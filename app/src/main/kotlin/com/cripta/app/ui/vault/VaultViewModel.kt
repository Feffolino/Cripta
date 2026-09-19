package com.cripta.app.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.cripta.app.data.DisplayPrefs
import com.cripta.app.data.FolderStat
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.ViewMode
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.computeFolderStats
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.media.ThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TypeFilter { ALL, IMAGE, VIDEO, OTHER }

data class Filters(
    val query: String = "",
    val tagIds: Set<Long> = emptySet(),
    /** Files carrying any of these tags are hidden from the results. */
    val excludedTagIds: Set<Long> = emptySet(),
    val type: TypeFilter = TypeFilter.ALL,
    val favoritesOnly: Boolean = false,
    val untaggedOnly: Boolean = false,
) {
    val active: Boolean
        get() = query.isNotBlank() || tagIds.isNotEmpty() || excludedTagIds.isNotEmpty() ||
            type != TypeFilter.ALL || favoritesOnly || untaggedOnly
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repo: VaultRepository,
    private val settings: SettingsStore,
    private val thumbs: ThumbnailLoader,
    private val viewerQueue: com.cripta.app.viewer.ViewerQueue,
) : ViewModel() {

    suspend fun thumb(file: FileEntity): Bitmap? = thumbs.load(file)

    /** Per-file cover version; a cover re-keyed on its entry reloads after [ThumbnailLoader.regenerate]
     *  (used by the Home/Favorites shelves). */
    val coverVersions: StateFlow<Map<String, Int>> = thumbs.versions

    /**
     * Freshly regenerated covers, by file id. Grid cells show the override for their id when
     * present, so a regenerated cover appears immediately without depending on a cache reload.
     * Cleared on folder navigation (by then the sealed disk cache already serves the new cover).
     */
    private val _coverOverrides = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val coverOverrides: StateFlow<Map<String, Bitmap>> = _coverOverrides

    /**
     * Regenerate the cover of every selected video using [cover] (ignoring non-video files) and
     * publish each new bitmap as an override so the visible thumbnail updates right away.
     */
    fun regenerateCovers(fileIds: List<String>, cover: ThumbnailLoader.VideoCover) = viewModelScope.launch {
        val ids = fileIds.toSet()
        val targets = files.value.map { it.file }
            .filter { it.id in ids && VaultRepository.isVideo(it.mimeType) }
        var applied = 0
        targets.forEach { f ->
            val bmp = thumbs.regenerateVideoCover(f, cover)
            if (bmp != null) { _coverOverrides.value = _coverOverrides.value + (f.id to bmp); applied++ }
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(
                appContext,
                "DIAG copertine: sel=${ids.size} video=${targets.size} aggiornate=$applied",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Publish the current display order so the viewer can swipe through it. */
    fun publishViewerQueue() { viewerQueue.set(files.value.map { it.file.id }) }

    private val _path = MutableStateFlow<List<FolderEntity>>(emptyList())
    val path: StateFlow<List<FolderEntity>> = _path

    private val currentFolderId = MutableStateFlow<Long?>(null)

    val filters = MutableStateFlow(Filters())

    /** Re-query trigger driven by the repository's global change signal (reliable across VMs). */
    private val refresh get() = repo.changes

    val folders: StateFlow<List<FolderEntity>> =
        combine(currentFolderId, refresh) { id, _ -> id }
            .flatMapLatest { repo.folders(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<TagEntity>> =
        refresh.flatMapLatest { repo.tags() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val viewMode: StateFlow<ViewMode> =
        settings.settings.map { it.viewMode }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewMode.GRID)

    val gridColumns: StateFlow<Int> =
        settings.settings.map { it.gridColumns }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val display: StateFlow<DisplayPrefs> =
        settings.settings.map { it.display }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DisplayPrefs())

    fun setViewMode(mode: ViewMode) = viewModelScope.launch { settings.setViewMode(mode) }
    fun setGridColumns(cols: Int) = viewModelScope.launch { settings.setGridColumns(cols) }

    val allFolders: StateFlow<List<FolderEntity>> =
        repo.allFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recursive per-folder stats (file count + total bytes, including all descendant folders). */
    val folderStats: StateFlow<Map<Long, FolderStat>> =
        combine(repo.allFolders(), repo.folderAggregates(), refresh) { folders, aggs, _ ->
            computeFolderStats(folders, aggs)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val sortFlow = settings.settings.map { it.sortKey to it.sortAscending }

    val sortKey: StateFlow<com.cripta.app.data.SortKey> =
        settings.settings.map { it.sortKey }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.cripta.app.data.SortKey.DATE)
    val sortAscending: StateFlow<Boolean> =
        settings.settings.map { it.sortAscending }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSort(key: com.cripta.app.data.SortKey, ascending: Boolean) =
        viewModelScope.launch { settings.setSort(key, ascending) }

    val files: StateFlow<List<FileWithTags>> =
        combine(currentFolderId, filters, sortFlow, refresh) { folder, f, sort, _ -> Triple(folder, f, sort) }
            .flatMapLatest { (folder, f, sort) ->
                val source = if (f.active) repo.allFiles() else repo.files(folder)
                source.map { list -> applySort(applyFilters(list, f), sort.first, sort.second) }
                    .flowOn(Dispatchers.Default)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Proactively generate covers for [items] so thumbnails are ready as cells scroll in instead
     * of each being generated on demand. Driven from the vault screen (not the ViewModel) so it
     * only runs while the grid is visible and stops when the viewer opens — otherwise the
     * background video decodes would contend with the player for the device's hardware codecs.
     */
    suspend fun prewarmCovers(items: List<FileWithTags>) = thumbs.prewarm(items.map { it.file })

    private fun applySort(
        list: List<FileWithTags>,
        key: com.cripta.app.data.SortKey,
        ascending: Boolean,
    ): List<FileWithTags> {
        val sorted = when (key) {
            com.cripta.app.data.SortKey.DATE -> list.sortedBy { it.file.importedAt }
            com.cripta.app.data.SortKey.NAME -> list.sortedBy { it.file.originalName.lowercase() }
            com.cripta.app.data.SortKey.SIZE -> list.sortedBy { it.file.sizeBytes }
            com.cripta.app.data.SortKey.MANUAL -> list.sortedBy { it.file.sortWeight }
        }
        // Manual order is intrinsically directional (weight ascending = top); ignore asc/desc.
        return if (key == com.cripta.app.data.SortKey.MANUAL || ascending) sorted else sorted.reversed()
    }

    private fun applyFilters(list: List<FileWithTags>, f: Filters): List<FileWithTags> {
        return list.filter { fwt ->
            val nameOk = f.query.isBlank() ||
                fwt.file.originalName.contains(f.query, ignoreCase = true) ||
                fwt.tags.any { it.name.contains(f.query, ignoreCase = true) }
            val tagsOk = f.tagIds.isEmpty() || fwt.tags.map { it.id }.containsAll(f.tagIds)
            val notExcludedOk = f.excludedTagIds.isEmpty() || fwt.tags.none { it.id in f.excludedTagIds }
            val untaggedOk = !f.untaggedOnly || fwt.tags.isEmpty()
            val typeOk = when (f.type) {
                TypeFilter.ALL -> true
                TypeFilter.IMAGE -> VaultRepository.isImage(fwt.file.mimeType)
                TypeFilter.VIDEO -> VaultRepository.isVideo(fwt.file.mimeType)
                TypeFilter.OTHER -> !VaultRepository.isImage(fwt.file.mimeType) &&
                    !VaultRepository.isVideo(fwt.file.mimeType)
            }
            val favOk = !f.favoritesOnly || fwt.file.isFavorite
            nameOk && tagsOk && notExcludedOk && typeOk && favOk && untaggedOk
        }
    }

    // --- Navigation ---
    fun enterFolder(folder: FolderEntity) {
        _coverOverrides.value = emptyMap()
        _path.value = _path.value + folder
        currentFolderId.value = folder.id
    }

    fun goUp() {
        if (_path.value.isNotEmpty()) {
            _coverOverrides.value = emptyMap()
            _path.value = _path.value.dropLast(1)
            currentFolderId.value = _path.value.lastOrNull()?.id
        }
    }

    val currentFolder: Long? get() = currentFolderId.value

    // --- Filters ---
    fun setQuery(q: String) { filters.value = filters.value.copy(query = q) }
    fun toggleTag(id: Long) {
        val cur = filters.value.tagIds
        // Including a specific tag turns off "untagged only" and drops it from the exclude set
        // (a tag can't be both required and forbidden).
        filters.value = filters.value.copy(
            tagIds = if (id in cur) cur - id else cur + id,
            excludedTagIds = filters.value.excludedTagIds - id,
            untaggedOnly = false,
        )
    }

    /** Toggle a tag in the *exclude* set: files with it are hidden. Mutually exclusive with include. */
    fun toggleExcludedTag(id: Long) {
        val cur = filters.value.excludedTagIds
        filters.value = filters.value.copy(
            excludedTagIds = if (id in cur) cur - id else cur + id,
            tagIds = filters.value.tagIds - id,
            untaggedOnly = false,
        )
    }
    fun setType(t: TypeFilter) { filters.value = filters.value.copy(type = t) }
    fun setFavoritesOnly(b: Boolean) { filters.value = filters.value.copy(favoritesOnly = b) }
    /** Show only media with no tags. Mutually exclusive with picking specific tags. */
    fun setUntaggedOnly(b: Boolean) {
        filters.value = filters.value.copy(untaggedOnly = b, tagIds = if (b) emptySet() else filters.value.tagIds)
    }
    fun clearFilters() { filters.value = Filters() }

    // --- Actions ---
    fun createFolder(name: String) = viewModelScope.launch {
        repo.createFolder(name, currentFolderId.value)    }

    fun deleteFolder(folder: FolderEntity) = viewModelScope.launch {
        repo.deleteFolderRecursive(folder.id).forEach { thumbs.evict(it) }    }

    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch {
        repo.renameFolder(folder, name)    }

    fun setFolderStyle(folderId: Long, color: Int?, emoji: String?) = viewModelScope.launch {
        repo.setFolderStyle(folderId, color, emoji)
    }

    fun importUris(uris: List<android.net.Uri>) {
        com.cripta.app.work.ConversionService.startImport(appContext, uris, currentFolderId.value)
    }

    fun toggleFavorite(fileId: String, fav: Boolean) = viewModelScope.launch {
        repo.toggleFavorite(fileId, fav)    }

    fun setFavorite(fileIds: List<String>, fav: Boolean) = viewModelScope.launch {
        fileIds.forEach { repo.toggleFavorite(it, fav) }    }

    /** Import picked files, then apply the delete-original policy. Returns nothing;
     *  when policy is ASK the screen collects the uris via [pendingOriginals]. */
    fun importThenHandleOriginals(uris: List<android.net.Uri>) {
        // Encryption now runs in a foreground service (background-safe, progress notification).
        // The delete-original policy (Elimina/Mantieni) is applied by the service; "Chiedi"
        // behaves as keep in the background since no dialog is available there.
        com.cripta.app.work.ConversionService.startImport(appContext, uris, currentFolderId.value)
    }

    private val _pendingOriginals = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val pendingOriginals: StateFlow<List<android.net.Uri>> = _pendingOriginals

    fun clearPendingOriginals() { _pendingOriginals.value = emptyList() }

    fun deleteOriginals(uris: List<android.net.Uri>) = viewModelScope.launch {
        repo.deleteOriginals(uris)
        _pendingOriginals.value = emptyList()
    }

    fun setTags(fileId: String, tagNames: List<String>) = viewModelScope.launch {
        repo.setTags(fileId, tagNames)    }

    /** Add the given tags to every selected file without touching their other tags. */
    fun addTagsToFiles(fileIds: List<String>, tagNames: List<String>) = viewModelScope.launch {
        fileIds.forEach { repo.addTags(it, tagNames) }    }

    /** Create a tag (optionally with an emoji/acronym alias) up front. */
    fun createTag(name: String, alias: String?) = viewModelScope.launch {
        repo.createTag(name, alias)    }

    fun renameFile(fileId: String, newName: String) = viewModelScope.launch {
        repo.renameFile(fileId, newName)    }

    fun setTagAlias(tagName: String, alias: String?) = viewModelScope.launch {
        repo.setTagAlias(tagName, alias)    }

    fun moveFiles(fileIds: List<String>, folderId: Long?) = viewModelScope.launch {
        fileIds.forEach { repo.moveFile(it, folderId) }    }

    fun deleteFiles(fileIds: List<String>) = viewModelScope.launch {
        fileIds.forEach { repo.secureDelete(it); thumbs.evict(it) }    }

    /** Persist a user drag-reorder (Manual sort). */
    fun reorder(orderedIds: List<String>) = viewModelScope.launch { repo.setSortWeights(orderedIds) }

    /**
     * Build a shuffled queue over the whole library (ids only, cheap for big libraries),
     * publish it to the viewer, and open the first item. Falls back to the current view
     * if the id query yields nothing.
     */
    fun randomShuffleOpen(open: (String) -> Unit) = viewModelScope.launch {
        val ids = repo.allFileIds()
        val order = if (ids.isNotEmpty()) ids.shuffled() else files.value.map { it.file.id }.shuffled()
        if (order.isEmpty()) return@launch
        viewerQueue.set(order)
        open(order.first())
    }
}

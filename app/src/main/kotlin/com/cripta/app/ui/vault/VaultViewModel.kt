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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.mapLatest
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
    /** Chosen tags: true = a file must have all of them, false = at least one. */
    val tagMatchAll: Boolean = true,
) {
    val active: Boolean
        get() = query.isNotBlank() || tagIds.isNotEmpty() || excludedTagIds.isNotEmpty() ||
            type != TypeFilter.ALL || favoritesOnly || untaggedOnly
}

/** True when [fwt] passes these filters (shared by the vault grid and the Home saved-filter counts). */
fun Filters.matches(fwt: FileWithTags): Boolean {
    val f = this
    val nameOk = f.query.isBlank() ||
        fwt.file.originalName.contains(f.query, ignoreCase = true) ||
        fwt.tags.any { it.name.contains(f.query, ignoreCase = true) }
    val tagsOk = f.tagIds.isEmpty() ||
        if (f.tagMatchAll) fwt.tags.map { it.id }.containsAll(f.tagIds) else fwt.tags.any { it.id in f.tagIds }
    val notExcludedOk = f.excludedTagIds.isEmpty() || fwt.tags.none { it.id in f.excludedTagIds }
    val untaggedOk = !f.untaggedOnly || fwt.tags.isEmpty()
    val typeOk = when (f.type) {
        TypeFilter.ALL -> true
        TypeFilter.IMAGE -> VaultRepository.isImage(fwt.file.mimeType)
        TypeFilter.VIDEO -> VaultRepository.isVideo(fwt.file.mimeType)
        TypeFilter.OTHER -> !VaultRepository.isImage(fwt.file.mimeType) && !VaultRepository.isVideo(fwt.file.mimeType)
    }
    val favOk = !f.favoritesOnly || fwt.file.isFavorite
    return nameOk && tagsOk && notExcludedOk && typeOk && favOk && untaggedOk
}

/** Per-type counts of a file list. */
data class TypeCounts(
    val total: Int = 0,
    val videos: Int = 0,
    val images: Int = 0,
    val others: Int = 0,
    val bytes: Long = 0,
    val videoDurationMs: Long = 0,
) {
    companion object {
        fun of(list: List<FileWithTags>): TypeCounts {
            var v = 0; var i = 0; var o = 0; var b = 0L; var d = 0L
            for (fwt in list) {
                val m = fwt.file.mimeType
                b += fwt.file.sizeBytes
                when {
                    VaultRepository.isVideo(m) -> { v++; d += fwt.file.durationMs ?: 0L }
                    VaultRepository.isImage(m) -> i++
                    else -> o++
                }
            }
            return TypeCounts(list.size, v, i, o, b, d)
        }
    }
}

/** What the grid shows ([shown]) against everything in the current scope ([scope]). */
data class VaultStats(val shown: TypeCounts = TypeCounts(), val scope: TypeCounts = TypeCounts()) {
    val filtered: Boolean get() = shown.total != scope.total
    companion object {
        fun of(shown: List<FileWithTags>, scope: List<FileWithTags>) =
            VaultStats(TypeCounts.of(shown), TypeCounts.of(scope))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repo: VaultRepository,
    private val settings: SettingsStore,
    private val thumbs: ThumbnailLoader,
    private val viewerQueue: com.cripta.app.viewer.ViewerQueue,
    private val navigator: VaultNavigator,
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
        targets.forEach { f ->
            val bmp = thumbs.regenerateVideoCover(f, cover)
            if (bmp != null) _coverOverrides.value = _coverOverrides.value + (f.id to bmp)
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

    /** Newest covers of each visible folder, for the folder mosaics. */
    val folderPreviews: StateFlow<Map<Long, List<FileEntity>>> =
        folders.mapLatest { list -> repo.folderPreviews(list.map { it.id }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    /** Unfiltered scope (current folder, or the whole vault while filtering) + the shown result. */
    private data class Scoped(val scope: List<FileWithTags>, val shown: List<FileWithTags>)

    private val scoped =
        combine(currentFolderId, filters, sortFlow, refresh) { folder, f, sort, _ -> Triple(folder, f, sort) }
            .flatMapLatest { (folder, f, sort) ->
                val source = if (f.active) repo.allFiles() else repo.files(folder)
                source.map { list -> Scoped(list, applySort(applyFilters(list, f), sort.first, sort.second)) }
                    .flowOn(Dispatchers.Default)
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val files: StateFlow<List<FileWithTags>> =
        scoped.map { it.shown }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Counts for the summary strip / sheet: what is shown vs. what the current scope holds. */
    val stats: StateFlow<VaultStats> =
        scoped.map { VaultStats.of(it.shown, it.scope) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultStats())

    /** Live state of file imports running in the foreground service. */
    val importState: StateFlow<VaultRepository.ImportState> = repo.importState
    fun dismissImportResult() = repo.dismissImportResult()

    /** Conversion queue status (banner). */
    val convertStatus: StateFlow<VaultRepository.ConvertStatus> = repo.convertStatus
    fun dismissConvertResult() = repo.dismissConvertResult()
    fun cancelConversion() = com.cripta.app.work.ConversionService.cancelConvert(appContext)

    val convertSettings: StateFlow<Pair<Boolean, Int>> =
        settings.settings.map { it.convertAfterChosen to it.trashDays }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false to 7)

    /** Batch conversion with the first-conversion choice (optionally remembered). */
    fun convertToMp4(fileIds: List<String>, after: com.cripta.app.data.ConvertAfter, rememberChoice: Boolean) {
        if (rememberChoice) viewModelScope.launch { settings.setConvertAfter(after) }
        val ids = fileIds.toSet()
        files.value.map { it.file }
            .filter { it.id in ids && VaultRepository.isVideo(it.mimeType) && it.mimeType != "video/mp4" }
            .forEach { com.cripta.app.work.ConversionService.startConvert(appContext, it.id, after) }
    }

    /** Queue the selected non-MP4 videos for conversion (they run one at a time). */
    fun convertToMp4(fileIds: List<String>) {
        val ids = fileIds.toSet()
        files.value.map { it.file }
            .filter { it.id in ids && VaultRepository.isVideo(it.mimeType) && it.mimeType != "video/mp4" }
            .forEach { com.cripta.app.work.ConversionService.startConvert(appContext, it.id) }
    }

    /** Drop the just-imported copies of files that were already in the vault (identical bytes). */
    fun removeImportDuplicates() = viewModelScope.launch {
        val dups = importState.value.duplicates.map { it.first }
        dups.forEach { repo.secureDelete(it); thumbs.evict(it) }
        repo.clearImportDuplicates()
        repo.dismissImportResult()
    }

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

    private fun applyFilters(list: List<FileWithTags>, f: Filters): List<FileWithTags> = list.filter { f.matches(it) }

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

    /** Jump back to a level of the breadcrumb (0 = root). */
    fun goToDepth(depth: Int) {
        val p = _path.value
        if (depth >= p.size) return
        _coverOverrides.value = emptyMap()
        _path.value = p.take(depth)
        currentFolderId.value = _path.value.lastOrNull()?.id
    }

    /** Open [folderId] directly (e.g. from Home), rebuilding its breadcrumb. */
    private fun openFolderById(folderId: Long) = viewModelScope.launch {
        val chain = repo.folderPath(folderId)
        if (chain.isEmpty()) return@launch
        filters.value = Filters()
        _coverOverrides.value = emptyMap()
        _path.value = chain
        currentFolderId.value = folderId
    }

    init {
        // Requests from other screens (Home folder card, saved-filter shortcut).
        viewModelScope.launch {
            navigator.pendingFolder.collect { id -> if (id != null) { openFolderById(id); navigator.consumeFolder() } }
        }
        viewModelScope.launch {
            navigator.pendingFilters.collect { f -> if (f != null) { filters.value = f; navigator.consumeFilters() } }
        }
        // Cartelle tab tapped again while already on it: back to the root.
        viewModelScope.launch {
            VaultTabReselect.events.collect { goToDepth(0) }
        }
    }

    // --- Saved filters ---
    val savedFilters: StateFlow<List<com.cripta.app.data.db.SavedFilterEntity>> =
        refresh.flatMapLatest { repo.savedFilters() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveCurrentFilter(name: String) = viewModelScope.launch {
        if (name.isNotBlank() && filters.value.active) repo.saveFilter(name, filters.value.toJson())
    }
    fun applySavedFilter(json: String) { filtersFromJson(json)?.let { filters.value = it } }
    fun deleteSavedFilter(id: Long) = viewModelScope.launch { repo.deleteSavedFilter(id) }

    /** Whether deleting moves to the trash (for the confirmation wording). */
    val trashEnabled: StateFlow<Boolean> =
        settings.settings.map { it.trashEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
    /** Tap on a count in the summary strip: filter to that type, or back to all when already on it. */
    fun toggleType(t: TypeFilter) { setType(if (filters.value.type == t) TypeFilter.ALL else t) }
    fun setTagMatchAll(all: Boolean) { filters.value = filters.value.copy(tagMatchAll = all) }
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

    /**
     * Import picked files in the foreground service (background-safe, progress notification and
     * in-app banner). The service applies the delete-original policy; with "Chiedi" it publishes
     * the imported originals to [pendingOriginals] and the screen asks the user.
     */
    fun importThenHandleOriginals(uris: List<android.net.Uri>) {
        com.cripta.app.work.ConversionService.startImport(appContext, uris, currentFolderId.value)
    }

    /** Originals of a finished import waiting for "Elimina originali / Mantieni" (policy "Chiedi"). */
    val pendingOriginals: StateFlow<List<android.net.Uri>> = repo.pendingOriginals

    fun clearPendingOriginals() = repo.clearPendingOriginals()

    fun deleteOriginals(uris: List<android.net.Uri>) = viewModelScope.launch {
        repo.deleteOriginals(uris)
        repo.clearPendingOriginals()
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

    fun setTagColor(tagName: String, color: Int?) = viewModelScope.launch { repo.setTagColorByName(tagName, color) }
    fun setTagPinned(tagName: String, pinned: Boolean) = viewModelScope.launch { repo.setTagPinned(tagName, pinned) }

    fun setTagAlias(tagName: String, alias: String?) = viewModelScope.launch {
        repo.setTagAlias(tagName, alias)    }

    fun moveFiles(fileIds: List<String>, folderId: Long?) = viewModelScope.launch {
        fileIds.forEach { repo.moveFile(it, folderId) }    }

    // --- Undo support (snackbar "Annulla") ---
    /** Put files back into the folders they were in before a move. */
    fun restoreFolders(previous: Map<String, Long?>) = viewModelScope.launch {
        previous.forEach { (id, folder) -> repo.moveFile(id, folder) }
    }

    /** Restore each file's previous favorite flag. */
    fun restoreFavorites(previous: Map<String, Boolean>) = viewModelScope.launch {
        previous.forEach { (id, fav) -> repo.toggleFavorite(id, fav) }
    }

    /** Restore each file's previous tag names. */
    fun restoreTags(previous: Map<String, List<String>>) = viewModelScope.launch {
        previous.forEach { (id, names) -> repo.setTags(id, names) }
    }

    /** Take [tagNames] off every file in [fileIds] (other tags kept). Files must be in the current view. */
    fun removeTagsFromFiles(fileIds: List<String>, tagNames: List<String>) = viewModelScope.launch {
        val ids = fileIds.toSet()
        files.value.filter { it.file.id in ids }.forEach { fwt ->
            val kept = fwt.tags.map { it.name }.filterNot { n -> tagNames.any { it.equals(n, ignoreCase = true) } }
            if (kept.size != fwt.tags.size) repo.setTags(fwt.file.id, kept)
        }
    }

    fun deleteFiles(fileIds: List<String>) = viewModelScope.launch {
        // To the trash when enabled (cover kept for a restore), otherwise shredded at once.
        fileIds.forEach { if (!repo.deleteOrTrash(it)) thumbs.evict(it) }    }

    /** Persist a user drag-reorder (Manual sort). */
    fun reorder(orderedIds: List<String>) = viewModelScope.launch { repo.setSortWeights(orderedIds) }

    /** True when "Casuale" shuffles only what is on screen (a folder or filtered results). */
    val randomScopedToView: Boolean get() = filters.value.active || currentFolderId.value != null

    /**
     * Build a shuffled queue and open its first item. Scope follows what the user is looking at:
     * the filtered results, or the open folder; at the root, the whole library (ids only, cheap
     * for big libraries). Falls back to the current view if the id query yields nothing.
     */
    fun randomShuffleOpen(open: (String) -> Unit) = viewModelScope.launch {
        val ids = if (randomScopedToView) files.value.map { it.file.id } else repo.allFileIds()
        val order = if (ids.isNotEmpty()) ids.shuffled() else files.value.map { it.file.id }.shuffled()
        if (order.isEmpty()) return@launch
        viewerQueue.set(order)
        open(order.first())
    }
}

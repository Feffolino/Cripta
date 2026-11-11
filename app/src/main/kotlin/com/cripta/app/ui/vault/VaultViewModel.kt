package com.cripta.app.ui.vault

import com.cripta.app.logException
import com.cripta.app.R
import com.cripta.app.str

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
import com.cripta.app.data.face.matching
import com.cripta.app.data.face.ordered
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** People who must all be in the file (faces found, or added by hand). */
    val personIds: Set<Long> = emptySet(),
    /**
     * Search the whole vault even with "Filtri solo nella cartella corrente" on: set by Home's
     * Foto / Video / Note figures, which count every file of the vault and must open all of them.
     */
    val wholeVault: Boolean = false,
    /** Videos started and not finished: what Home's "Continua a guardare" row shows. */
    val inProgress: Boolean = false,
) {
    val active: Boolean
        get() = query.isNotBlank() || tagIds.isNotEmpty() || excludedTagIds.isNotEmpty() ||
            type != TypeFilter.ALL || favoritesOnly || untaggedOnly || personIds.isNotEmpty() || inProgress
}

/**
 * Whether the filters run on the whole vault (true) or on the open folder only: active filters
 * search everywhere unless Settings keeps them in the current folder, and a request that counted
 * the whole vault ([Filters.wholeVault]) always does.
 */
fun searchesWholeVault(active: Boolean, wholeVault: Boolean, currentFolderOnly: Boolean): Boolean =
    active && (!currentFolderOnly || wholeVault)

/** True when [fwt] passes these filters (shared by the vault grid and the Home saved-filter counts). */
fun Filters.matches(fwt: FileWithTags): Boolean {
    val f = this
    val nameOk = f.query.isBlank() ||
        fwt.file.originalName.contains(f.query, ignoreCase = true) ||
        // A tag is found by its acronym too, as in the tag pickers and the filter's tag search.
        fwt.tags.any { it.name.contains(f.query, ignoreCase = true) || it.alias?.contains(f.query, ignoreCase = true) == true }
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
    val progressOk = !f.inProgress || (VaultRepository.isVideo(fwt.file.mimeType) && (fwt.file.playbackPosMs ?: 0L) > 0)
    return nameOk && tagsOk && notExcludedOk && typeOk && favOk && untaggedOk && progressOk
}

/**
 * The files of [list] these filters show: the filters themselves, plus the media of the people
 * chosen ([people], null = no person filter) and of the names typed ([byName]). One definition for
 * the vault grid and the counts of Home's saved filters, so both always agree.
 */
fun Filters.applyTo(list: List<FileWithTags>, people: Set<String>?, byName: Set<String>): List<FileWithTags> {
    val noQuery = copy(query = "")
    return list.filter { matches(it) || (it.file.id in byName && noQuery.matches(it)) }
        .let { s -> if (people == null) s else s.filter { it.file.id in people } }
}

/** Per-type counts of a file list. */
data class TypeCounts(
    val total: Int = 0,
    val videos: Int = 0,
    val images: Int = 0,
    val others: Int = 0,
    val bytes: Long = 0,
    val videoDurationMs: Long = 0,
    val notes: Int = 0,
) {
    companion object {
        fun of(list: List<FileWithTags>): TypeCounts {
            var v = 0; var i = 0; var o = 0; var b = 0L; var d = 0L; var n = 0
            for (fwt in list) {
                val m = fwt.file.mimeType
                b += fwt.file.sizeBytes
                when {
                    VaultRepository.isVideo(m) -> { v++; d += fwt.file.durationMs ?: 0L }
                    VaultRepository.isImage(m) -> i++
                    else -> { o++; if (VaultRepository.isNote(m)) n++ }
                }
            }
            return TypeCounts(list.size, v, i, o, b, d, n)
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

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repo: VaultRepository,
    private val settings: SettingsStore,
    private val thumbs: ThumbnailLoader,
    private val intro: com.cripta.app.data.intro.IntroRepository,
    private val faces: com.cripta.app.data.face.FaceRepository,
    private val viewerQueue: com.cripta.app.viewer.ViewerQueue,
    private val navigator: VaultNavigator,
    private val session: com.cripta.app.security.SessionManager,
) : ViewModel() {

    /**
     * Run a bulk vault job (loops over many files). The session is held for its duration, as the
     * foreground service does, so an auto-lock mid-way waits for it instead of pulling the keys
     * out; and a failure (e.g. the vault locked anyway, repository throws "Vault locked") is
     * logged and stops the loop instead of crashing the app from viewModelScope. It also runs to
     * the end when the screen goes away: the UI reports it done at once ("30 file eliminati"), and
     * Back during a slow shred used to stop it after a few files, leaving the rest in the vault.
     * [toEnd] = false for read-only work (covers), which may as well stop with the screen.
     */
    private fun launchBulk(what: String, toEnd: Boolean = true, block: suspend () -> Unit) = viewModelScope.launch {
        val held = session.beginWork()
        // Already locked: nothing can be read or written, don't even start.
        if (!held) return@launch
        try {
            if (toEnd) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { block() } else block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logException(android.util.Log.ERROR, "VaultViewModel", "$what failed", e)
        } finally {
            session.endWork()
        }
    }

    /** How many items of a bulk action were done, counted as it goes. */
    class BulkCount { var done = 0 }

    /**
     * Like [launchBulk], for actions the screen announces: [onDone] runs after the work with how
     * many items were really done (0 when the vault was locked or the first one failed), so the
     * announcement states the real result and never the number asked for.
     */
    private fun launchBulkCounted(what: String, onDone: (Int) -> Unit, block: suspend (BulkCount) -> Unit) = viewModelScope.launch {
        val count = BulkCount()
        val held = session.beginWork()
        if (held) {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { block(count) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logException(android.util.Log.ERROR, "VaultViewModel", "$what failed", e)
            } finally {
                session.endWork()
            }
        }
        onDone(count.done)
    }

    /** Like [launchBulkCounted] for a tag change: [onDone] gets what was really changed (empty if nothing). */
    private fun launchTagChange(what: String, onDone: (com.cripta.app.data.TagLinkDiff) -> Unit,
                                block: suspend () -> com.cripta.app.data.TagLinkDiff) = viewModelScope.launch {
        var diff = com.cripta.app.data.TagLinkDiff()
        if (session.beginWork()) {
            try {
                diff = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { block() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logException(android.util.Log.ERROR, "VaultViewModel", "$what failed", e)
            } finally {
                session.endWork()
            }
        }
        onDone(diff)
    }

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
    fun regenerateCovers(fileIds: List<String>, cover: ThumbnailLoader.VideoCover) = launchBulk("regenerateCovers", toEnd = false) {
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

    /** The folder list together with the level it belongs to (see [foldersLevel]). */
    private val levelFolders: StateFlow<Pair<Long?, List<FolderEntity>>?> =
        combine(currentFolderId, refresh) { id, _ -> id }
            .flatMapLatest { id -> repo.folders(id).map { id to it } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val folders: StateFlow<List<FolderEntity>> =
        levelFolders.map { it?.second.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Which level (folder id, null = root) [folders] currently shows; [LEVEL_UNKNOWN] before the
     * first load. The screen waits for this (and [filesLevel]) to match the new level before it
     * shows it, so the previous level's content never flashes in during a folder change.
     */
    val foldersLevel: StateFlow<Long?> =
        levelFolders.map { if (it == null) LEVEL_UNKNOWN else it.first }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LEVEL_UNKNOWN)

    /** Newest covers of each visible folder, for the folder mosaics. */
    val folderPreviews: StateFlow<Map<Long, List<FileEntity>>> =
        combine(folders, refresh) { list, _ -> list }
            .mapLatest { list -> repo.folderPreviews(list.map { it.id }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * For each media, the faces (cover track ids) of the people in it, starred people first: drawn
     * top-left on its cover.
     */
    val coverPeople: StateFlow<Map<String, List<Long>>> =
        faces.coverFaces.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tags: StateFlow<List<TagEntity>> =
        refresh.flatMapLatest { repo.tags() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val viewMode: StateFlow<ViewMode> =
        settings.settings.map { it.viewMode }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewMode.GRID)

    val gridColumns: StateFlow<Int> =
        settings.settings.map { it.gridColumns }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    val gridColumnsLandscape: StateFlow<Int> =
        settings.settings.map { it.gridColumnsLandscape }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val display: StateFlow<DisplayPrefs> =
        settings.settings.map { it.display }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DisplayPrefs())

    val foldersCollapsed: StateFlow<Boolean> =
        settings.settings.map { it.foldersCollapsed }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setFoldersCollapsed(collapsed: Boolean) = viewModelScope.launch { settings.setFoldersCollapsed(collapsed) }

    fun setViewMode(mode: ViewMode) = viewModelScope.launch { settings.setViewMode(mode) }
    fun setGridColumns(cols: Int, landscape: Boolean = false) = viewModelScope.launch { settings.setGridColumns(cols, landscape) }

    val allFolders: StateFlow<List<FolderEntity>> =
        repo.allFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recursive per-folder stats (file count + total bytes, including all descendant folders). */
    val folderStats: StateFlow<Map<Long, FolderStat>> =
        combine(repo.allFolders(), repo.folderAggregates(), refresh) { folders, aggs, _ ->
            computeFolderStats(folders, aggs)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Read the live folder, not the breadcrumb snapshot, so changes survive navigation/restarts.
    private val sortFlow = combine(currentFolderId, repo.allFolders(), settings.settings) { id, folders, s ->
        if (id == null) s.sortKey to s.sortAscending
        else folders.firstOrNull { it.id == id }?.let { folder ->
            (com.cripta.app.data.SortKey.entries.firstOrNull { it.name == folder.sortKey }
                ?: com.cripta.app.data.SortKey.DATE) to folder.sortAscending
        } ?: (com.cripta.app.data.SortKey.DATE to false)
    }.distinctUntilChanged()

    val sortKey: StateFlow<com.cripta.app.data.SortKey> =
        sortFlow.map { it.first }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.cripta.app.data.SortKey.DATE)
    val sortAscending: StateFlow<Boolean> =
        sortFlow.map { it.second }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSort(key: com.cripta.app.data.SortKey, ascending: Boolean) {
        val folderId = currentFolderId.value
        viewModelScope.launch {
            if (folderId == null) settings.setSort(key, ascending)
            else repo.setFolderSort(folderId, key, ascending)
        }
    }

    fun setInProgress(b: Boolean) { filters.value = filters.value.copy(inProgress = b) }

    fun setFolderFavorite(id: Long, favorite: Boolean) = viewModelScope.launch { repo.setFolderFavorite(id, favorite) }
    /** [onDone] gets whether the folder really changed (it may have gone meanwhile). */
    fun setFolderRandomExcluded(id: Long, excluded: Boolean, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onDone(runCatching { repo.setFolderRandomExcluded(id, excluded) }.getOrDefault(false))
    }

    /** Whether a parent folder keeps [id] out of "Casuale" (the menu says so instead of a switch that does nothing). */
    suspend fun randomExcludedByAncestor(id: Long): Boolean = repo.randomExcludedByAncestor(id)

    /** Current folder, or the whole vault while filtering when allowed by Settings. */
    private data class Scoped(val scope: List<FileWithTags>, val shown: List<FileWithTags>, val folder: Long? = null)

    /**
     * The files the filters run on: the current folder, or the whole vault while filtering when
     * allowed by Settings. Re-queried only when that scope changes, never on a filter or a keystroke.
     */
    private val source =
        combine(currentFolderId, filters.map { it.active to it.wholeVault }.distinctUntilChanged(),
            settings.settings.map { it.filterCurrentFolderOnly }.distinctUntilChanged(), refresh) { folder, (active, whole), localOnly, _ ->
            folder to searchesWholeVault(active, whole, localOnly)
        }
            .distinctUntilChanged()
            .flatMapLatest { (folder, wholeVault) ->
                (if (wholeVault) repo.allFiles() else repo.files(folder)).map { folder to it }
            }

    /** A filter with what the face index adds: the media of the people chosen and of the names typed. */
    private class Lookup(val f: Filters, val people: Set<String>?, val byName: Set<String>)

    /**
     * [filters] with their face lookups. While the search text is being typed (nothing else changed)
     * it waits for a pause, so a word isn't searched letter by letter; any other change is immediate.
     */
    private val lookups = run {
        var last: Filters? = null
        filters.debounce { f ->
            val prev = last
            last = f
            if (prev != null && f.query != prev.query && f.copy(query = "") == prev.copy(query = "")) 200L else 0L
        }
            .mapLatest { f ->
                // People come from the face index, not from the file rows.
                val people = if (f.personIds.isEmpty()) null else runCatching { faces.mediaWithAll(f.personIds) }.getOrDefault(emptySet())
                // The search also finds the media of the people whose name matches.
                val byName = if (f.query.isBlank()) emptySet() else runCatching { faces.mediaOfName(f.query) }.getOrDefault(emptySet())
                Lookup(f, people, byName)
            }
    }

    private val scoped =
        combine(source, lookups, sortFlow) { (folder, list), l, sort ->
            val shown = l.f.applyTo(list, l.people, l.byName)
            Scoped(list, applySort(shown, sort.first, sort.second), folder)
        }
            .flowOn(Dispatchers.Default)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val files: StateFlow<List<FileWithTags>> =
        scoped.map { it.shown }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Which level [files] belongs to (see [foldersLevel]). */
    val filesLevel: StateFlow<Long?> =
        scoped.map { it.folder }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LEVEL_UNKNOWN)

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
    fun resolveOriginal(originalId: String, delete: Boolean) =
        com.cripta.app.work.ConversionService.resolveOriginal(appContext, originalId, delete)

    val convertSettings: StateFlow<Pair<Boolean, Int>> =
        settings.settings.map { it.convertAfterChosen to it.trashDays }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false to 7)

    /**
     * Of [fileIds], the videos worth converting ([ThumbnailLoader.worthConvertingToMp4]): every
     * non-MP4 video, and MP4s holding a codec other than H.264/HEVC (AV1, VP9…).
     */
    suspend fun convertibleIds(fileIds: Set<String>): Set<String> =
        files.value.map { it.file }.filter { it.id in fileIds && thumbs.worthConvertingToMp4(it) }.map { it.id }.toSet()

    /** Batch conversion with the first-conversion choice (optionally remembered); [fileIds]
     *  already narrowed by [convertibleIds]. */
    fun convertToMp4(fileIds: List<String>, after: com.cripta.app.data.ConvertAfter, rememberChoice: Boolean) {
        if (rememberChoice) viewModelScope.launch { settings.setConvertAfter(after) }
        val ids = fileIds.toSet()
        files.value.map { it.file }
            .filter { it.id in ids && VaultRepository.isVideo(it.mimeType) }
            .forEach { com.cripta.app.work.ConversionService.startConvert(appContext, it.id, after) }
    }

    /**
     * "Scarica" on a selection: decrypt the files into [tree], a folder picked by the user, in the
     * background service (same pipeline as the viewer's "Esporta"). The grant is persisted so it
     * outlives the picker while the service writes; picking the same folder again reuses it.
     */
    fun downloadToFolder(fileIds: List<String>, tree: android.net.Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        com.cripta.app.work.ConversionService.startDownload(appContext, fileIds, tree)
    }

    /** Queue the selected convertible videos ([convertibleIds]) for conversion (they run one at a time). */
    /** "Confronta audio": what the starts of two analysed videos share, for [onResult]. */
    fun compareAudio(idA: String, idB: String, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(runCatching { intro.compare(idA, idB) }.getOrElse { str(R.string.compare_failed) })
    }


    fun convertToMp4(fileIds: List<String>) {
        val ids = fileIds.toSet()
        files.value.map { it.file }
            .filter { it.id in ids && VaultRepository.isVideo(it.mimeType) }
            .forEach { com.cripta.app.work.ConversionService.startConvert(appContext, it.id) }
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
        // A tag deleted while it is in the active filters leaves them (the saved ones are pruned by
        // the repository): an id of nothing would keep an AND filter at zero results.
        viewModelScope.launch {
            var known = emptySet<Long>()
            repo.tags().collect { list ->
                val ids = list.mapTo(HashSet()) { it.id }
                val gone = known - ids
                known = ids
                val f = filters.value
                if (gone.isNotEmpty() && (f.tagIds.any { it in gone } || f.excludedTagIds.any { it in gone }))
                    filters.value = f.copy(tagIds = f.tagIds - gone, excludedTagIds = f.excludedTagIds - gone)
            }
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
    /** Settings › "Filtri solo nella cartella corrente": the empty state names the scope actually searched. */
    val filterLocalOnly: StateFlow<Boolean> =
        settings.settings.map { it.filterCurrentFolderOnly }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
        val removing = id in cur
        filters.value = filters.value.copy(
            excludedTagIds = if (removing) cur - id else cur + id,
            tagIds = filters.value.tagIds - id,
            // Taking an exclusion away only does that: "Senza etichette" stays as it was.
            untaggedOnly = if (removing) filters.value.untaggedOnly else false,
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

    /** The people offered in the filter sheet: named, starred, or seen in two media or more, in the People page's order (starred first). */
    val filterPeople: StateFlow<List<com.cripta.app.data.face.FaceRepository.Person>> =
        combine(faces.people(), settings.settings.map { s -> s.peopleSort to s.peopleSortAscending }.distinctUntilChanged()) { o, (k, asc) ->
            val key = com.cripta.app.data.PeopleSort.entries.firstOrNull { it.name == k } ?: com.cripta.app.data.PeopleSort.RELEVANCE
            o.people.filter { it.main }.ordered(key, asc)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** People whose name matches the search text, shown under the search field (starred first). */
    val searchPeople: StateFlow<List<com.cripta.app.data.face.FaceRepository.Person>> =
        combine(faces.people(), filters.map { it.query.trim() }.distinctUntilChanged().debounce(200)) { o, q -> o.people.matching(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A person picked from the search results: their media, and the typed text goes. */
    fun pickSearchedPerson(id: Long) {
        filters.value = filters.value.copy(query = "", personIds = filters.value.personIds + id)
    }

    fun togglePerson(id: Long) {
        val cur = filters.value.personIds
        filters.value = filters.value.copy(personIds = if (id in cur) cur - id else cur + id)
    }

    suspend fun faceCrop(trackId: Long) = faces.crop(trackId)

    // --- Actions ---
    fun createFolder(name: String) = viewModelScope.launch {
        repo.createFolder(name, currentFolderId.value)    }

    /** [onDone] gets 1 when the folder was deleted, 0 when it was not. */
    fun deleteFolder(folder: FolderEntity, onDone: (Int) -> Unit = {}) = launchBulkCounted("deleteFolder", onDone) { c ->
        repo.deleteFolderRecursive(folder.id).forEach { thumbs.evict(it) }
        c.done = 1
    }

    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch {
        repo.renameFolder(folder, name)    }

    fun setFolderStyle(folderId: Long, color: Int?, icon: String?, iconChanged: Boolean, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onDone(runCatching { repo.setFolderStyle(folderId, color, icon, iconChanged) }.getOrDefault(false))
    }

    /** [onDone] gets whether the cover was really set (a file moved or deleted meanwhile is refused). */
    fun setFolderCover(folderId: Long, fileId: String?, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onDone(runCatching { repo.setFolderCover(folderId, fileId) }.getOrDefault(false))
    }

    suspend fun filesInFolder(folderId: Long, limit: Int): com.cripta.app.data.VaultRepository.CoverChoices = repo.filesInFolder(folderId, limit)

    fun importUris(uris: List<android.net.Uri>) {
        com.cripta.app.work.ConversionService.startImport(appContext, uris, currentFolderId.value)
    }

    fun toggleFavorite(fileId: String, fav: Boolean) = viewModelScope.launch {
        repo.toggleFavorite(fileId, fav)    }

    fun setFavorite(fileIds: List<String>, fav: Boolean) = launchBulk("setFavorite") {
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

    /** Imports named like a vault file, waiting for "Sostituisci / Tieni entrambi". */
    val pendingVersions: StateFlow<List<VaultRepository.VersionMatch>> = repo.pendingVersions

    /**
     * "Sostituisci" / "Rimuovi la copia": each of [matches] becomes the new version of its same-name
     * vault files, or (identical bytes) is dropped as a copy of the file already there.
     */
    fun replaceVersions(matches: List<VaultRepository.VersionMatch>) {
        // Answered at once: the dialog goes before a second tap can apply it again.
        repo.resolveNewVersions(matches.map { it.newId })
        launchBulk("replaceVersions") {
            matches.forEach { if (it.identical) removeCopy(it) else repo.applyNewVersion(it.newId, it.previousIds) }
        }
    }

    /**
     * Drop the new copy only while an identical live file still exists (the older one may have been
     * deleted or replaced since, leaving this the only one). It goes where any deletion goes (the
     * trash when it is on), so a wrong answer can be undone.
     */
    private suspend fun removeCopy(match: VaultRepository.VersionMatch) {
        val f = repo.fileById(match.newId)?.takeIf { it.deletedAt == null } ?: return
        val hash = f.contentHash ?: return
        if (repo.sameSizeAs(f).none { it.contentHash == hash }) return
        // A trashed copy keeps its cover: restoring it must not lose it.
        if (!repo.deleteOrTrash(f.id)) thumbs.evict(f.id)
        repo.clearImportDuplicates(listOf(f.id))
    }

    /**
     * "Elimina la nuova" for media that look like vault ones: the new files go where any deletion
     * goes (the trash when it is on), so a wrong answer can be undone.
     */
    /** A file by id (the import conflicts page shows the new copy's cover). */
    suspend fun fileFor(id: String): FileEntity? = repo.fileById(id)

    fun removeNewCopies(matches: List<VaultRepository.VersionMatch>) {
        repo.resolveNewVersions(matches.map { it.newId })
        launchBulk("removeNewCopies") {
            matches.forEach { m ->
                val f = repo.fileById(m.newId)?.takeIf { it.deletedAt == null } ?: return@forEach
                if (!repo.deleteOrTrash(f.id)) thumbs.evict(f.id)
            }
        }
    }

    /** "Tieni entrambi": nothing changes, the new files stay where they were imported. */
    fun keepVersionsSeparate(matches: List<VaultRepository.VersionMatch>) =
        repo.resolveNewVersions(matches.map { it.newId })

    // Answered here: the same choice in the notifications (Hyper Island) goes away too.
    fun clearPendingOriginals() {
        repo.clearPendingOriginals()
        com.cripta.app.work.ConversionService.dismissImportOriginalsChoice(appContext)
    }

    /** [onNotDeleted] gets how many originals the provider kept (0 = all gone). */
    fun deleteOriginals(uris: List<android.net.Uri>, onNotDeleted: (Int) -> Unit = {}) = viewModelScope.launch {
        com.cripta.app.work.ConversionService.dismissImportOriginalsChoice(appContext)
        val kept = runCatching { repo.deleteOriginals(uris) }.getOrDefault(uris.size)
        repo.clearPendingOriginals()
        onNotDeleted(kept)
    }

    /** [onDone] gets what really changed, after the write (an "Annulla" reverts exactly that). */
    fun setTags(fileId: String, tagNames: List<String>, onDone: (com.cripta.app.data.TagLinkDiff) -> Unit = {}) =
        launchTagChange("setTags", onDone) { repo.setTags(fileId, tagNames) }

    /** Add the given tags to every selected file without touching their other tags. */
    fun addTagsToFiles(fileIds: List<String>, tagNames: List<String>, onDone: (com.cripta.app.data.TagLinkDiff) -> Unit = {}) =
        launchTagChange("addTagsToFiles", onDone) { com.cripta.app.data.TagLinkDiff(added = repo.addTagsToFiles(fileIds, tagNames)) }

    /** Take [tags] off every file in [fileIds] (other tags kept), by id. */
    fun removeTagsFromFiles(fileIds: List<String>, tags: List<com.cripta.app.data.db.TagEntity>, onDone: (com.cripta.app.data.TagLinkDiff) -> Unit = {}) =
        launchTagChange("removeTagsFromFiles", onDone) {
            com.cripta.app.data.TagLinkDiff(removed = repo.removeTagsFromFiles(fileIds, tags.map { it.id }))
        }

    /** "Annulla" of a tag change: only the links it touched. */
    fun revertTagLinks(diff: com.cripta.app.data.TagLinkDiff) = launchBulk("revertTagLinks") { repo.revertTagLinks(diff) }

    /** Create a tag with its look; a name already in the library is said so, not overwritten. */
    fun createTag(draft: TagDraft) = viewModelScope.launch {
        val r = repo.createTag(draft.name, draft.alias, draft.icon, draft.color, draft.pinned, draft.onCovers)
        if (!r.created) android.widget.Toast.makeText(appContext, str(R.string.tag_exists, draft.name), android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Save a tag's editor in one go, by id. */
    /** Each tag's use (files, folders), for the tag editor's line under its title. */
    val tagUsage: StateFlow<Map<Long, com.cripta.app.data.db.TagUsage>> =
        repo.tagUsage().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun deleteTag(id: Long) = viewModelScope.launch { repo.deleteTag(id) }

    fun saveTag(id: Long, draft: TagDraft) = viewModelScope.launch {
        val r = repo.saveTag(id, draft.name, draft.alias, draft.color, draft.pinned, draft.icon, draft.onCovers)
        if (r == com.cripta.app.data.TagSave.NAME_TAKEN)
            android.widget.Toast.makeText(appContext, str(R.string.tag_exists, draft.name), android.widget.Toast.LENGTH_SHORT).show()
    }

    fun renameFile(fileId: String, newName: String) = viewModelScope.launch {
        repo.renameFile(fileId, newName)    }


    /** [onDone] gets how many files were really moved. */
    fun moveFiles(fileIds: List<String>, folderId: Long?, onDone: (Int) -> Unit = {}) = launchBulkCounted("moveFiles", onDone) { c ->
        // A destination gone (deleted, in the trash) since it was picked moves nothing: counted as not done.
        fileIds.forEach { if (repo.moveFile(it, folderId)) c.done++ }
    }

    // --- Undo support (snackbar "Annulla") ---
    /** Put files back into the folders they were in before a move. */
    fun restoreFolders(previous: Map<String, Long?>) = launchBulk("restoreFolders") {
        // A folder gone since: the file stays where it is (live, reachable) rather than under it.
        previous.forEach { (id, folder) -> repo.moveFile(id, folder) }
    }

    /** Restore each file's previous favorite flag. */
    fun restoreFavorites(previous: Map<String, Boolean>) = launchBulk("restoreFavorites") {
        previous.forEach { (id, fav) -> repo.toggleFavorite(id, fav) }
    }


    /** [onDone] gets how many files were really deleted (or trashed). */
    fun deleteFiles(fileIds: List<String>, onDone: (Int) -> Unit = {}) = launchBulkCounted("deleteFiles", onDone) { c ->
        // To the trash when enabled (cover kept for a restore), otherwise shredded at once.
        fileIds.forEach { if (!repo.deleteOrTrash(it)) thumbs.evict(it); c.done++ }
    }

    /** Persist a user drag-reorder (Manual sort). */
    fun reorder(orderedIds: List<String>) = launchBulk("reorder") {
        // Filtered results span the whole vault: renumbering that subset 0..k-1 would scramble
        // other folders' manual order (the screen also disables the drag while filtering).
        if (filters.value.active) return@launchBulk
        repo.setSortWeights(orderedIds)
    }

    /** True when "Casuale" shuffles only what is on screen (a folder or filtered results). */
    val randomScopedToView: Boolean get() = filters.value.active || currentFolderId.value != null

    /**
     * Build a shuffled queue and open its first item. Scope follows what the user is looking at:
     * the filtered results, or the open folder; at the root, the whole library (ids only).
     * Exclusions always apply, including when the folder itself is open. Never fall back to
     * excluded files when every candidate was excluded.
     */
    fun randomShuffleOpen(open: (String) -> Unit) = viewModelScope.launch {
        val eligible = repo.randomEligibleIds()
        val ids = if (randomScopedToView) {
            val allowed = eligible.toHashSet()
            files.value.map { it.file.id }.filter { it in allowed }
        } else eligible
        val order = ids.shuffled()
        if (order.isEmpty()) {
            android.widget.Toast.makeText(appContext, str(R.string.random_none), android.widget.Toast.LENGTH_SHORT).show()
            return@launch
        }
        viewerQueue.set(order)
        open(order.first())
    }
}

/** "Not loaded yet" for [VaultViewModel.foldersLevel] / [VaultViewModel.filesLevel]. */
const val LEVEL_UNKNOWN = Long.MIN_VALUE

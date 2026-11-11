package com.cripta.app.ui.home

import com.cripta.app.ui.safeScope
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.DisplayPrefs
import com.cripta.app.data.FolderStat
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.computeFolderStats
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.SavedFilterEntity
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.ui.vault.TypeCounts
import com.cripta.app.ui.vault.filtersFromJson
import com.cripta.app.ui.vault.applyTo
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repo: VaultRepository,
    private val downloadQueue: com.cripta.app.data.DownloadQueue,
    private val thumbs: ThumbnailLoader,
    private val viewerQueue: ViewerQueue,
    private val navigator: com.cripta.app.ui.vault.VaultNavigator,
    private val faces: com.cripta.app.data.face.FaceRepository,
    private val settings: com.cripta.app.data.SettingsStore,
    private val settingsNav: com.cripta.app.ui.settings.SettingsNav,
) : ViewModel() {

    /** Ask Settings to open its security page (the caller then switches tab). */
    fun openSecurity() = settingsNav.open(com.cripta.app.ui.settings.SettingsPage.SICUREZZA.name)

    /** Minutes before the vault locks itself (-1 never, 0 at once): shown in the first-run steps. */
    val autoLockMinutes: StateFlow<Int> = settings.settings.map { it.autoLockMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /** MP4 conversion progress / outcome, the same banner as in Cartelle. */
    val convertStatus: kotlinx.coroutines.flow.StateFlow<VaultRepository.ConvertStatus> = repo.convertStatus
    fun dismissConvertResult() = repo.dismissConvertResult()
    fun cancelConversion() = com.cripta.app.work.ConversionService.cancelConvert(appContext)
    fun resolveOriginal(originalId: String, delete: Boolean) =
        com.cripta.app.work.ConversionService.resolveOriginal(appContext, originalId, delete)

    init {
        // Shred trash entries past their retention (only when the trash is in use).
        safeScope.launch {
            runCatching {
                val s = settings.settingsOnce()
                repo.purgeExpiredTrash(s.trashDays).forEach { thumbs.evict(it) }
            }
            // Emojis of folders and tags (older vaults, restored backups) become icons.
            runCatching { repo.convertEmojisToIcons { e -> com.cripta.app.ui.tagicons.TagIcons.forEmoji(appContext, e) } }
            // Links and saved filters still naming tags deleted by older versions.
            runCatching { repo.repairTagReferences() }
            // Blobs of a restore the process was killed in: space no file in the app holds.
            runCatching { repo.reconcileInterruptedRestore() }
            // Resume downloads/conversions that were waiting when Android killed the app.
            if (repo.claimJobRestore()) {
                val complete = runCatching { restorePendingJobs() }.getOrDefault(false)
                // Not everything was resumed: the next time Home opens tries again.
                if (!complete) repo.releaseJobRestore()
            }
        }
    }

    /** Resume what was waiting; false when something could not be (unreadable list, start refused). */
    private suspend fun restorePendingJobs(): Boolean {
        var complete = true
        val queued = downloadQueue.downloads.value.filter { !it.finished }
        val queuedIds = queued.map { it.id }.toSet()
        val converting = repo.convertingIds.value
        for (job in repo.pendingJobs()) {
            val o = runCatching { org.json.JSONObject(job.payload) }.getOrNull() ?: continue
            when (job.kind) {
                "conversion_original" -> {
                    val id = o.optString("id")
                    val original = id.takeIf { it.isNotBlank() }?.let { repo.fileById(it) }
                    if (original != null && original.deletedAt == null) {
                        repo.restoreConvertedOriginal(id, o.optString("name"), o.optString("copyId"))
                    } else repo.deletePendingJob(job.id)
                }
                "download" -> {
                    val url = o.optString("url")
                    // A link queued in this process: this row is its own record (it survives the
                    // process being killed) and stays; deleting it lost the waiting link.
                    if (job.id in queuedIds) continue
                    val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getLong(it) } }.orEmpty()
                    val height = o.optInt("height", 0).takeIf { it > 0 }
                    val folder = o.optLong("folder", -1L).takeIf { it >= 0 }
                    // Blank, or an older record of the same link queued again meanwhile with the same
                    // quality, folder and tags. Queued again otherwise, it is another request: the
                    // record was dropped, and its own choices with it.
                    if (url.isBlank() || queued.any { it.url == url && it.maxHeight == height && it.folderId == folder && it.tagIds.toSet() == tags.toSet() }) {
                        repo.deletePendingJob(job.id); continue
                    }
                    // Resumed under its own id: the service keeps this record (and replaces it),
                    // so there is no moment with no record of the link. If the start throws (e.g.
                    // the app is in the background) the record stays for the next launch.
                    val started = runCatching {
                        com.cripta.app.work.ConversionService.startDownloadUrl(
                            appContext, url, height, folder, tags, jobId = job.id,
                        )
                    }.isSuccess
                    if (!started) complete = false
                }
                "convert" -> {
                    val id = o.optString("id")
                    if (id.isBlank() || id in converting) continue
                    if (repo.fileById(id) == null) { repo.deletePendingJob(job.id); continue }
                    val after = com.cripta.app.data.ConvertAfter.entries.getOrNull(o.optInt("after", -1))
                    if (runCatching { com.cripta.app.work.ConversionService.startConvert(appContext, id, after) }.isFailure) complete = false
                }
            }
        }
        return complete
    }

    suspend fun thumb(file: FileEntity): Bitmap? = thumbs.load(file)

    /** Per-file cover version; shelves re-key on it so covers refresh after a regeneration. */
    val coverVersions: StateFlow<Map<String, Int>> = thumbs.versions

    val display: StateFlow<DisplayPrefs> =
        settings.settings.map { it.display }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DisplayPrefs())

    /** Every live file with its tags, newest import first (one query shared by all shelves). */
    private val all = repo.changes.flatMapLatest { repo.allFiles() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** Header summary: how many videos / photos and how much space. */
    val summary: StateFlow<TypeCounts> = all.map { TypeCounts.of(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TypeCounts())

    /** Videos started and not finished, most recently watched first. */
    val continueWatching: StateFlow<List<FileWithTags>> = all.map { list ->
        list.filter { VaultRepository.isVideo(it.file.mimeType) && (it.file.playbackPosMs ?: 0L) > 0 }
            .sortedByDescending { it.file.lastPlayedAt ?: 0L }.take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recents: StateFlow<List<FileWithTags>> = all.map { it.take(24) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FileWithTags>> = all.map { list -> list.filter { it.file.isFavorite }.take(24) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteCount: StateFlow<Int> = all.map { list -> list.count { it.file.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Saved filters with how many files each one currently matches. */
    val savedFilters: StateFlow<List<Pair<SavedFilterEntity, Int>>> =
        // The face index too: a person given or taken a media changes their filter's count, and
        // neither the filters nor the files say so (the shortcut kept the count of before).
        // Only who is in which media (and their names, for a typed name): not every rebuild of the
        // people during a scan.
        combine(repo.changes.flatMapLatest { repo.savedFilters() }, all,
            faces.people().map { o -> o.people.map { Triple(it.id, it.entity.name to it.entity.ignored, it.media.mapTo(HashSet()) { f -> f.id }) } }
                .distinctUntilChanged()) { saved, files, _ -> saved to files }
            .mapLatest { (saved, files) ->
                saved.map { sf ->
                    val f = filtersFromJson(sf.json)
                    // Counted as the vault will show it: people and typed names included, whole vault.
                    val people = if (f == null || f.personIds.isEmpty()) null
                        else runCatching { faces.mediaWithAll(f.personIds) }.getOrDefault(emptySet())
                    val byName = if (f == null || f.query.isBlank()) emptySet()
                        else runCatching { faces.mediaOfName(f.query) }.getOrDefault(emptySet())
                    sf to (f?.applyTo(files, people, byName)?.size ?: 0)
                }
            }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        repo.changes.flatMapLatest { repo.folders(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Newest covers of each root folder, for the folder mosaics. */
    val folderPreviews: StateFlow<Map<Long, List<FileEntity>>> =
        combine(folders, repo.changes) { list, _ -> list }
            .mapLatest { list -> repo.folderPreviews(list.map { it.id }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** False until the vault content has been read once, so the empty state doesn't flash on the
     *  first frame after unlock while the DB queries are still loading. */
    val loaded: StateFlow<Boolean> =
        all.map { true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val folderStats: StateFlow<Map<Long, FolderStat>> =
        repo.changes.flatMapLatest { combine(repo.allFolders(), repo.folderAggregates()) { folders, aggs ->
            computeFolderStats(folders, aggs)
        } }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun publishQueue(ids: List<String>) = viewerQueue.set(ids)

    /** Encrypt picked files into the vault root (same foreground service as the Cartelle import). */
    fun importFiles(uris: List<android.net.Uri>) =
        com.cripta.app.work.ConversionService.startImport(appContext, uris, null)

    /** Ask the Cartelle screen to open this folder (the caller then switches tab). */
    fun openFolder(id: Long) = navigator.openFolder(id)

    /** Ask the Cartelle screen to apply a saved filter (the caller then switches tab). */
    /** The Home row of people: starred first, then the most seen; empty when Settings hides it. */
    val homePeople: StateFlow<List<com.cripta.app.data.face.FaceRepository.Person>> =
        combine(faces.people(), settings.settings.map { it.peopleOnHome }) { o, mode ->
            if (mode == "off") emptyList() else o.people.filter { it.main }.take(16)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "blur": faces blurred until the row is touched. */
    val peopleBlurred: StateFlow<Boolean> = settings.settings.map { it.peopleOnHome == "blur" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setPersonFavorite(id: Long, favorite: Boolean) = safeScope.launch { runCatching { faces.setFavorite(id, favorite) } }

    fun openPeople() = com.cripta.app.ui.people.PeopleNav.request()

    suspend fun faceCrop(trackId: Long) = faces.crop(trackId)

    /** Position of each tag in the library: covers draw their tags in that order. */
    val tagOrder: StateFlow<Map<Long, Int>> =
        repo.changes.flatMapLatest { repo.tags() }.map { list -> list.withIndex().associate { (i, t) -> t.id to i } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Faces on the covers of Home's shelves; none when "Persone in Home" is off. */
    val coverPeople: StateFlow<Map<String, List<Long>>> =
        combine(faces.coverFaces, settings.settings.map { it.peopleOnHome }) { m, mode -> if (mode == "off") emptyMap() else m }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The vault, filtered on [personId]. */
    fun openPerson(personId: Long) = navigator.applyFilters(com.cripta.app.ui.vault.Filters(personIds = setOf(personId), wholeVault = true))

    /** Counted over the whole vault on Home, so it opens over the whole vault (not the folder last open). */
    fun applySavedFilter(json: String) {
        filtersFromJson(json)?.let { navigator.applyFilters(it.copy(wholeVault = true)) }
    }

    /** Open Cartelle filtered to one type (from the summary card). */
    /** The Home search pill: the query opens in Cartelle, over the whole vault. */
    fun search(query: String) = navigator.applyFilters(com.cripta.app.ui.vault.Filters(query = query, wholeVault = true))

    /** The videos of "Continua a guardare" (started, not finished), wherever they are. */
    fun showContinueWatching() =
        navigator.applyFilters(com.cripta.app.ui.vault.Filters(type = com.cripta.app.ui.vault.TypeFilter.VIDEO, inProgress = true, wholeVault = true))

    /** Every file of [type] in the vault, wherever it is: the figure tapped counted them all. */
    fun showType(type: com.cripta.app.ui.vault.TypeFilter) =
        navigator.applyFilters(com.cripta.app.ui.vault.Filters(type = type, wholeVault = true))
}

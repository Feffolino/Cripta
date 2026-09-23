package com.cripta.app.ui.home

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
import com.cripta.app.ui.vault.matches
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val thumbs: ThumbnailLoader,
    private val viewerQueue: ViewerQueue,
    private val navigator: com.cripta.app.ui.vault.VaultNavigator,
    private val settings: com.cripta.app.data.SettingsStore,
    private val settingsNav: com.cripta.app.ui.settings.SettingsNav,
) : ViewModel() {

    /** Ask Settings to open its Strumenti page (the caller then switches tab). */
    fun openTools() = settingsNav.open(com.cripta.app.ui.settings.SettingsPage.STRUMENTI.name)

    init {
        // Shred trash entries past their retention (only when the trash is in use).
        viewModelScope.launch {
            runCatching {
                val s = settings.settingsOnce()
                repo.purgeExpiredTrash(s.trashDays).forEach { thumbs.evict(it) }
            }
            // Resume downloads/conversions that were waiting when Android killed the app.
            if (repo.claimJobRestore()) runCatching { restorePendingJobs() }
        }
    }

    private suspend fun restorePendingJobs() {
        val queuedUrls = repo.downloads.value.filter { !it.finished }.map { it.url }.toSet()
        val converting = repo.convertingIds.value
        for (job in repo.pendingJobs()) {
            val o = runCatching { org.json.JSONObject(job.payload) }.getOrNull() ?: continue
            when (job.kind) {
                "download" -> {
                    val url = o.optString("url")
                    repo.deletePendingJob(job.id)   // the service stores it again under a new id
                    if (url.isBlank() || url in queuedUrls) continue
                    val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getLong(it) } }.orEmpty()
                    com.cripta.app.work.ConversionService.startDownloadUrl(
                        appContext, url, o.optInt("height", 0).takeIf { it > 0 },
                        o.optLong("folder", -1L).takeIf { it >= 0 }, tags,
                    )
                }
                "convert" -> {
                    val id = o.optString("id")
                    if (id.isBlank() || id in converting) continue
                    if (repo.fileById(id) == null) { repo.deletePendingJob(job.id); continue }
                    val after = com.cripta.app.data.ConvertAfter.entries.getOrNull(o.optInt("after", -1))
                    com.cripta.app.work.ConversionService.startConvert(appContext, id, after)
                }
            }
        }
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
        combine(repo.changes.flatMapLatest { repo.savedFilters() }, all) { saved, files ->
            saved.map { sf -> sf to (filtersFromJson(sf.json)?.let { f -> files.count { f.matches(it) } } ?: 0) }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        repo.changes.flatMapLatest { repo.folders(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Newest covers of each root folder, for the folder mosaics. */
    val folderPreviews: StateFlow<Map<Long, List<FileEntity>>> =
        folders.mapLatest { list -> repo.folderPreviews(list.map { it.id }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** False until the vault content has been read once, so the empty state doesn't flash on the
     *  first frame after unlock while the DB queries are still loading. */
    val loaded: StateFlow<Boolean> =
        repo.changes.flatMapLatest { combine(repo.allFiles(), repo.folders(null)) { _, _ -> true } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val folderStats: StateFlow<Map<Long, FolderStat>> =
        repo.changes.flatMapLatest { combine(repo.allFolders(), repo.folderAggregates()) { folders, aggs ->
            computeFolderStats(folders, aggs)
        } }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun publishQueue(ids: List<String>) = viewerQueue.set(ids)

    /** Ask the Cartelle screen to open this folder (the caller then switches tab). */
    fun openFolder(id: Long) = navigator.openFolder(id)

    /** Ask the Cartelle screen to apply a saved filter (the caller then switches tab). */
    fun applySavedFilter(json: String) {
        filtersFromJson(json)?.let { navigator.applyFilters(it) }
    }

    /** Open Cartelle filtered to one type (from the summary card). */
    fun showType(type: com.cripta.app.ui.vault.TypeFilter) = navigator.applyFilters(com.cripta.app.ui.vault.Filters(type = type))
}

package com.cripta.app.ui.settings

import com.cripta.app.logException
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import androidx.compose.ui.graphics.toArgb

import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.Settings
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.dedup.DuplicateScanner
import com.cripta.app.data.db.TagEntity
import com.cripta.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val dupStore: com.cripta.app.data.dedup.DupScanStore,
    private val nav: SettingsNav,
    private val store: SettingsStore,
    private val session: SessionManager,
    private val repo: VaultRepository,
    private val scanner: DuplicateScanner,
    private val updater: com.cripta.app.update.AppUpdater,
    private val thumbs: com.cripta.app.media.ThumbnailLoader,
    private val keyVault: com.cripta.app.security.KeyVault,
    private val intro: com.cripta.app.data.intro.IntroRepository,
    private val faces: com.cripta.app.data.face.FaceRepository,
    private val viewerQueue: com.cripta.app.viewer.ViewerQueue,
    private val vaultNavigator: com.cripta.app.ui.vault.VaultNavigator,
    private val backups: com.cripta.app.data.BackupJobs,
) : ViewModel() {
    private companion object {
        /** Last tag count and space reading, kept across the screen's view models (see [tagCount]). */
        @Volatile var lastTagCount = 0
        @Volatile var lastSpace: com.cripta.app.data.SpaceReport? = null
    }


    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val release: com.cripta.app.update.AppUpdater.Release) : UpdateState
        data class Downloading(val pct: Int) : UpdateState
        /** APK downloaded: the installer was opened; if the user backed out it can be reopened. */
        data class ReadyToInstall(val release: com.cripta.app.update.AppUpdater.Release, val apk: java.io.File) : UpdateState
        data class Error(val message: String) : UpdateState
    }

    private val _update = kotlinx.coroutines.flow.MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    fun checkUpdate(ctx: android.content.Context) = viewModelScope.launch {
        _update.value = UpdateState.Checking
        val pre = store.settingsOnce().updatePrerelease
        val rel = try {
            updater.latest(includePrerelease = pre)
        } catch (e: java.io.IOException) {
            _update.value = UpdateState.Error(str(R.string.update_no_connection))
            return@launch
        }
        _update.value = when {
            rel == null -> UpdateState.Error(
                if (pre) str(R.string.update_none)
                else str(R.string.update_none_stable)
            )
            rel.buildNumber <= updater.currentBuild(ctx) -> UpdateState.UpToDate
            else -> UpdateState.Available(rel)
        }
    }

    /**
     * Download the update in the background service: it goes on with the app closed, with its own
     * notification; this screen follows it (updater.downloadState) and, when it is open as the
     * download ends, opens the installer as before.
     */
    fun downloadUpdate(ctx: android.content.Context) {
        val rel = (_update.value as? UpdateState.Available)?.release ?: return
        _update.value = UpdateState.Downloading(0)
        watchingDownload = true
        com.cripta.app.work.ConversionService.startUpdateDownload(ctx, rel)
    }

    fun cancelUpdateDownload(ctx: android.content.Context) = com.cripta.app.work.ConversionService.cancelUpdateDownload(ctx)

    /** Set when the download was started from this screen: its end opens the installer. */
    private var watchingDownload = false

    init {
        viewModelScope.launch {
            updater.downloadState.collect { d ->
                when (d) {
                    com.cripta.app.update.AppUpdater.Download.Idle -> Unit
                    is com.cripta.app.update.AppUpdater.Download.Running -> _update.value = UpdateState.Downloading(d.pct)
                    is com.cripta.app.update.AppUpdater.Download.Ready -> {
                        // Stay on "ready" rather than "100%": the system installer may be cancelled.
                        _update.value = UpdateState.ReadyToInstall(d.release, d.apk)
                        if (watchingDownload) { watchingDownload = false; openInstaller(appContext, d.apk) }
                    }
                    is com.cripta.app.update.AppUpdater.Download.Failed -> {
                        watchingDownload = false
                        _update.value = UpdateState.Error(networkError(d.error, str(R.string.update_download_failed)))
                    }
                    is com.cripta.app.update.AppUpdater.Download.Cancelled -> {
                        watchingDownload = false
                        _update.value = UpdateState.Available(d.release)
                    }
                }
            }
        }
    }

    /** Reopen the system installer for an APK already downloaded. */
    fun installUpdate(ctx: android.content.Context) {
        val ready = _update.value as? UpdateState.ReadyToInstall ?: return
        if (!ready.apk.exists()) { _update.value = UpdateState.Available(ready.release); return }
        openInstaller(ctx, ready.apk)
    }

    private fun openInstaller(ctx: android.content.Context, apk: java.io.File) {
        runCatching { updater.install(ctx, apk) }
            .onFailure { _message.value = str(R.string.installer_open_failed) }
    }

    /** Italian, user-facing text for a network/IO failure (never the raw exception message). */
    private fun networkError(t: Throwable, fallback: String): String = when (t) {
        is com.cripta.app.update.AppUpdater.Rejected -> t.message ?: fallback
        is java.net.UnknownHostException, is java.net.ConnectException ->
            str(R.string.no_internet_check)
        is java.net.SocketTimeoutException -> str(R.string.server_not_responding)
        is java.io.IOException -> str(R.string.fallback_check_connection, fallback)
        else -> str(R.string.fallback_retry, fallback)
    }

    val settings: StateFlow<Settings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    val tags: StateFlow<List<TagEntity>> =
        repo.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Tag count for the page list. The screen gets a new view model each time Impostazioni opens,
     * so the last count is kept for the process: the summary shows it at once, not "0" then a jump.
     */
    val tagCount: StateFlow<Int> = kotlinx.coroutines.flow.flow { repo.tags().collect { lastTagCount = it.size; emit(it.size) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), lastTagCount)

    /** Space used (Impostazioni list and Spazio page): the last reading at once, then a fresh one. */
    private val _space = kotlinx.coroutines.flow.MutableStateFlow(lastSpace)
    val space: StateFlow<com.cripta.app.data.SpaceReport?> = _space
    fun refreshSpace() = viewModelScope.launch {
        runCatching { repo.spaceReport() }.getOrNull()?.let { lastSpace = it; _space.value = it }
    }

    /** Every live file, for the cleanup list (heaviest first there). */
    val allFiles: StateFlow<List<com.cripta.app.data.db.FileEntity>> =
        repo.allFiles().map { l -> l.map { it.file } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cleanup: delete these files, through the trash when it is on. */
    fun deleteFiles(ids: List<String>) = launchHeld("deleteFiles") {
        ids.forEach { id -> if (!repo.deleteOrTrash(id)) thumbs.evict(id) }
        refreshSpace()
    }

    /** Cleanup: drop the covers cached on disk; says how much was freed. */
    fun clearCache() = launchHeld("clearCache") {
        val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { thumbs.clearDiskCache() }
        _message.value = str(R.string.cache_cleared, com.cripta.app.ui.components.formatBytes(freed))
        refreshSpace()
    }

    fun setAutoLock(minutes: Int) = viewModelScope.launch { store.setAutoLockMinutes(minutes) }
    fun setDeletePolicy(p: DeleteOriginalPolicy) = viewModelScope.launch { store.setDeleteOriginalPolicy(p) }
    fun setThemeMode(m: com.cripta.app.data.ThemeMode) = viewModelScope.launch { store.setThemeMode(m) }
    fun setDynamicColor(b: Boolean) = viewModelScope.launch { store.setDynamicColor(b) }
    fun setViewMode(m: com.cripta.app.data.ViewMode) = viewModelScope.launch { store.setViewMode(m) }
    fun setGridColumns(n: Int) = viewModelScope.launch { store.setGridColumns(n) }
    fun setSort(k: com.cripta.app.data.SortKey, asc: Boolean) = viewModelScope.launch { store.setSort(k, asc) }
    fun setFilterCurrentFolderOnly(v: Boolean) = viewModelScope.launch { store.setFilterCurrentFolderOnly(v) }
    fun setAllowScreenshots(b: Boolean) = viewModelScope.launch { store.setAllowScreenshots(b) }
    fun setShowFileInfo(v: Boolean) = viewModelScope.launch { store.setShowFileInfo(v) }
    fun setShowTagsOnCover(v: Boolean) = viewModelScope.launch { store.setShowTagsOnCover(v) }
    fun setShowDateHeaders(v: Boolean) = viewModelScope.launch { store.setShowDateHeaders(v) }
    fun setShowFolderInfo(v: Boolean) = viewModelScope.launch { store.setShowFolderInfo(v) }
    fun setFoldersHorizontal(v: Boolean) = viewModelScope.launch { store.setFoldersHorizontal(v) }
    fun setShowNoteFab(v: Boolean) = viewModelScope.launch { store.setShowNoteFab(v) }
    fun setShowRandomFab(v: Boolean) = viewModelScope.launch { store.setShowRandomFab(v) }
    fun setCoverTagRows(v: Int) = viewModelScope.launch { store.setCoverTagRows(v) }
    fun setCoverTagStyle(v: com.cripta.app.data.CoverTagStyle) = viewModelScope.launch { store.setCoverTagStyle(v) }
    fun setTagColors(v: Boolean) = viewModelScope.launch { store.setTagColors(v) }
    fun setDefaultTagColor(argb: Int?) = viewModelScope.launch { store.setDefaultTagColor(argb) }
    fun setShowDurationBadge(v: Boolean) = viewModelScope.launch { store.setShowDurationBadge(v) }
    fun setCoverPeople(v: Boolean) = viewModelScope.launch { store.setCoverPeople(v) }
    fun setCoverPeopleSize(v: Int) = viewModelScope.launch { store.setCoverPeopleSize(v) }
    fun setShowStatsStrip(v: Boolean) = viewModelScope.launch { store.setShowStatsStrip(v) }
    /** Page requested from another screen (Home "Strumenti", scan notification). */
    val requestedPage: StateFlow<String?> = nav.page
    fun consumeRequestedPage() = nav.consume()

    /** Reset one page's preferences to their defaults (files and tags are never touched). */
    fun resetPage(page: String) = viewModelScope.launch { store.resetPage(page) }

    fun setConvertAfter(v: com.cripta.app.data.ConvertAfter) = viewModelScope.launch { store.setConvertAfter(v) }
    fun setResumePlayback(v: Boolean) = viewModelScope.launch { store.setResumePlayback(v) }
    fun setTrashEnabled(v: Boolean) = viewModelScope.launch { store.setTrashEnabled(v) }
    fun setTrashDays(v: Int) = viewModelScope.launch { store.setTrashDays(v) }
    fun setSimilarOnImport(v: Boolean) = viewModelScope.launch { store.setSimilarOnImport(v) }

    /** (media with a fingerprint, photos and videos in the vault), or null while locked. */
    suspend fun printStats(): Pair<Int, Int>? = scanner.printStats()

    /** Files in the trash, newest first. */
    val trashed: StateFlow<List<com.cripta.app.data.db.FileWithTags>> =
        repo.changes.flatMapLatest { repo.trashed() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Folders in the trash (browsable: the trash dialog opens them). */
    val trashedFolders: StateFlow<List<com.cripta.app.data.db.FolderEntity>> =
        repo.changes.flatMapLatest { repo.trashedFolders() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /**
     * Runs [block] holding the session (an auto-lock waits for it instead of pulling the keys out
     * mid-way) and to the end even if Settings closes. Stopped halfway, a backup left a truncated
     * archive, a restore a half-merged vault, and an emptied trash files the user took for gone.
     */
    private suspend fun <T> heldToEnd(block: suspend () -> T): T {
        check(session.beginWork()) { "Vault locked" }
        try {
            return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { block() }
        } finally {
            session.endWork()
        }
    }

    /**
     * [heldToEnd] from the view model scope. A failure (the vault locked before the start: the
     * repository throws "Vault locked") is logged instead of crashing the app.
     */
    private fun launchHeld(what: String, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            heldToEnd(block)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logException(android.util.Log.ERROR, "SettingsViewModel", "$what failed", e)
        }
    }

    fun restore(id: String) = launchHeld("restore") { repo.restore(id) }
    /** Cover of a trashed file (its blob and key are kept until it is destroyed). */
    suspend fun thumbOf(f: com.cripta.app.data.db.FileEntity): android.graphics.Bitmap? = thumbs.load(f)
    fun restoreFolder(id: Long) = launchHeld("restoreFolder") { repo.restoreFolder(id) }
    fun deleteForever(id: String) = launchHeld("deleteForever") { if (repo.secureDelete(id, onlyIfTrashed = true)) thumbs.evict(id) }
    fun deleteFolderForever(id: Long) = launchHeld("deleteFolderForever") { repo.deleteFolderForever(id).forEach { thumbs.evict(it) } }
    fun emptyTrash() = launchHeld("emptyTrash") { repo.emptyTrash().forEach { thumbs.evict(it) }; refreshSpace() }
    /** Selection in the trash: restore these, one after the other (folders first, files then). */
    fun restoreMany(files: List<String>, folders: List<Long>) = launchHeld("restoreMany") {
        folders.forEach { repo.restoreFolder(it) }
        files.forEach { repo.restore(it) }
    }
    /** Selection in the trash: destroy these for good, one after the other. */
    fun deleteManyForever(files: List<String>, folders: List<Long>) = launchHeld("deleteManyForever") {
        folders.forEach { id -> repo.deleteFolderForever(id).forEach { thumbs.evict(it) } }
        files.forEach { if (repo.secureDelete(it, onlyIfTrashed = true)) thumbs.evict(it) }
    }
    fun setVideoLoop(v: Boolean) = viewModelScope.launch { store.setVideoLoop(v) }
    fun setSeekStep(v: Int) = viewModelScope.launch { store.setSeekStep(v) }
    fun setGestureControls(v: Boolean) = viewModelScope.launch { store.setGestureControls(v) }
    fun setAutoRotate(v: Boolean) = viewModelScope.launch { store.setAutoRotate(v) }
    fun setPictureInPicture(v: Boolean) = viewModelScope.launch { store.setPictureInPicture(v) }
    fun setGestureVolume(v: Boolean) = viewModelScope.launch { store.setGestureVolume(v) }
    fun setAutoNext(v: Boolean) = viewModelScope.launch { store.setAutoNext(v) }
    fun setAutoNextSec(v: Int) = viewModelScope.launch { store.setAutoNextSec(v) }
    fun setIntroAnalyzeNew(v: Boolean) = viewModelScope.launch { store.setIntroAnalyzeNew(v) }
    fun setIntroQuickScan(v: Boolean) = viewModelScope.launch { store.setIntroQuickScan(v) }
    fun setIntroAutoSkip(v: Boolean) = viewModelScope.launch { store.setIntroAutoSkip(v) }
    fun setOutroAutoSkip(v: Boolean) = viewModelScope.launch { store.setOutroAutoSkip(v) }
    fun startIntroScan() = com.cripta.app.work.ConversionService.startIntroScan(appContext, manual = true)
    fun restartIntroScan(everything: Boolean) = com.cripta.app.work.ConversionService.restartIntroScan(appContext, everything)
    val rejectedSegments: StateFlow<List<com.cripta.app.data.db.RejectedPrintEntity>> =
        intro.rejected().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun restoreRejected(id: Long) = viewModelScope.launch { intro.restoreRejected(id) }
    /** The analysis so far (status card and results page); null until first loaded. */
    val introOverview = kotlinx.coroutines.flow.MutableStateFlow<com.cripta.app.data.intro.IntroRepository.Overview?>(null)
    fun refreshIntro() = viewModelScope.launch { runCatching { intro.overview() }.getOrNull()?.let { introOverview.value = it } }
    /** A running scan, for the live status card. */
    val introProgress = com.cripta.app.work.ConversionService.introProgress
    fun cancelIntroScan() = com.cripta.app.work.ConversionService.cancelIntroScan(appContext)
    /** "Risultati dell'analisi" page open (it survives a trip to the viewer and back). */
    val introResultsOpen = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** "Firme": the signatures of one kind, null while loading. */
    val introGroups = kotlinx.coroutines.flow.MutableStateFlow<Pair<com.cripta.app.media.intro.SegmentKind,
        List<com.cripta.app.data.intro.IntroRepository.Signature>>?>(null)
    fun loadIntroGroups(kind: com.cripta.app.media.intro.SegmentKind) = viewModelScope.launch {
        if (introGroups.value?.first != kind) introGroups.value = null
        runCatching { intro.signatures(kind) }.getOrNull()?.let { introGroups.value = kind to it }
    }

    /** Outcome of a signature change, shown on the results page (it covers the Settings snackbar). */
    val introMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Runs a signature change, then reloads the signatures and the counts. */
    private fun signatureAction(kind: com.cripta.app.media.intro.SegmentKind, done: String, block: suspend () -> Unit) =
        viewModelScope.launch {
            val ok = runCatching { block() }.isSuccess
            introMessage.value = if (ok) done else str(R.string.operation_failed_short)
            runCatching { intro.signatures(kind) }.getOrNull()?.let { introGroups.value = kind to it }
            refreshIntro()
        }
    fun renameSignature(s: com.cripta.app.data.intro.IntroRepository.Signature, name: String) =
        signatureAction(s.kind, str(R.string.signature_renamed)) { intro.renameSignature(s, name) }
    fun mergeSignatures(sigs: List<com.cripta.app.data.intro.IntroRepository.Signature>, name: String?) =
        signatureAction(sigs.first().kind, str(R.string.signatures_merged, sigs.size)) { intro.mergeSignatures(sigs, name) }
    fun deleteSignatures(sigs: List<com.cripta.app.data.intro.IntroRepository.Signature>) =
        signatureAction(sigs.first().kind, plural(R.plurals.signatures_deleted, sigs.size)) { sigs.forEach { intro.deleteSignature(it) } }
    fun setSignatureDuration(s: com.cripta.app.data.intro.IntroRepository.Signature, startDelta: Long, endDelta: Long) =
        signatureAction(s.kind, str(R.string.duration_applied, s.entries.size)) { intro.setSignatureDuration(s, startDelta, endDelta) }
    fun removeFromSignature(s: com.cripta.app.data.intro.IntroRepository.Signature, fileId: String) =
        signatureAction(s.kind, str(R.string.video_removed_from_signature)) { intro.setNone(fileId, s.kind) }
    /** A video opened from the results: the viewer swipes through the same filtered list. */
    fun openFromResults(ids: List<String>) = viewerQueue.set(ids)

    // ---- People ----
    val people = faces.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val faceProgress = com.cripta.app.work.ConversionService.faceProgress
    /** The answer to "Confronta" / "Perché?", shown in a dialog. */
    val faceMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** The last change that can be taken back, with what the snackbar says. */
    val faceUndo = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, com.cripta.app.data.face.Undo>?>(null)
    fun setFacesAnalyzeNew(v: Boolean) = viewModelScope.launch { store.setFacesAnalyzeNew(v) }
    fun setFaceMoments(v: Boolean) = viewModelScope.launch { store.setFaceMoments(v) }
    fun setPeopleOnHome(v: String) = viewModelScope.launch { store.setPeopleOnHome(v) }
    fun setFacesMode(v: String) = viewModelScope.launch { store.setFacesMode(v); groupsVersion.value++ }
    /** Bumped when "Da sistemare" must be grouped again though the unassigned count did not change. */
    val groupsVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** "Dividi il gruppo": [a] and [b] are not one person. */
    fun keepFacesApart(a: Collection<Long>, b: Collection<Long>) = viewModelScope.launch {
        try {
            val u = faces.keepFacesApart(a, b)
            groupsVersion.value++
            faceUndo.value = str(R.string.group_split) to suspend { u(); groupsVersion.value += 1 }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            faceMessage.value = str(R.string.operation_failed_prefix) + com.cripta.app.work.UserErrors.of(e)
        }
    }
    fun openPeople() = com.cripta.app.ui.people.PeopleNav.request()
    fun startFaceScan() = com.cripta.app.work.ConversionService.startFaceScan(appContext, manual = true)
    /** [fileIds] analysed again, even those whose faces the user cleared. */
    fun analyseAgain(fileIds: List<String>, deep: Boolean = false) =
        com.cripta.app.work.ConversionService.startFaceScan(appContext, manual = true, fileIds = fileIds, deep = deep)
    /**
     * The last "Da sistemare" groups: shown at once when the page comes back from a media, so the
     * list is whole when its scroll position is restored (else it opened again at the top).
     */
    var lastGroups: List<com.cripta.app.data.face.FaceRepository.FaceGroup>? = null
    /**
     * "Forse è …" of a person kept while a suggestion is played: the whole list (answered faces
     * included: dropping them shifted the pages, and Back landed on the next face), the answers
     * and the page in view.
     */
    var lastSuggestions: Pair<Long, List<com.cripta.app.data.face.FaceRepository.Suggestion>>? = null
    var lastAnswers: Map<Long, Boolean> = emptyMap()
    var lastSuggestionPage = 0
    /** The "Da sistemare" page in view, for the way back from a media. */
    var lastTodoPage = 0
    fun restartFaces(everything: Boolean) =
        com.cripta.app.work.ConversionService.startFaceScan(appContext, manual = true, reset = if (everything) "all" else "auto")
    fun cancelFaceScan() = com.cripta.app.work.ConversionService.cancelFaceScan(appContext)
    suspend fun faceCrop(trackId: Long) = faces.crop(trackId)
    suspend fun faceStats() = faces.stats()
    private fun faceAction(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            faceMessage.value = str(R.string.operation_failed_prefix) + com.cripta.app.work.UserErrors.of(e)
        }
    }
    /** Run [block] and offer its undo as "[message] · Annulla". */
    private fun undoable(message: String, block: suspend () -> com.cripta.app.data.face.Undo) = faceAction { faceUndo.value = message to block() }
    fun undoFaceChange() { val u = faceUndo.value ?: return; faceUndo.value = null; faceAction { u.second() } }
    fun renamePerson(id: Long, name: String) = faceAction { faces.rename(id, name) }
    fun setPersonFavorite(id: Long, favorite: Boolean) = faceAction { faces.setFavorite(id, favorite) }
    fun mergePeople(into: Long, others: Collection<Long>) =
        undoable(plural(R.plurals.people_merged, others.size + 1)) { faces.merge(into, others) }
    fun keepApart(a: Long, b: Long) = faceAction { faces.keepApart(a, b) }
    fun ignorePerson(id: Long) = undoable(str(R.string.person_hidden)) { faces.ignore(id) }
    fun deleteEmptyPerson(id: Long) = faceAction { faces.deleteEmpty(id) }
    fun restorePerson(id: Long) = faceAction { faces.restore(id) }
    fun removeFromPerson(personId: Long, fileIds: Collection<String>) =
        undoable(plural(R.plurals.media_removed, fileIds.size)) { faces.removeMedia(personId, fileIds) }
    /** "Sposta a…" ([to] a person) or "Dividi" ([to] null: a new person named [newName]). */
    fun moveToPerson(from: Long, fileIds: Collection<String>, to: Long?, newName: String = "") = faceAction { faces.moveMedia(from, fileIds, to, newName) }
    fun confirmFace(trackId: Long, personId: Long) = undoable(str(R.string.confirmed)) { faces.assign(trackId, personId).also { lookCloser(listOf(trackId)) } }
    /** Faces confirmed: their videos are looked at closer in the background ([com.cripta.app.data.face.FaceRepository.refine]). */
    private suspend fun lookCloser(trackIds: Collection<Long>) =
        com.cripta.app.work.ConversionService.startFaceRefine(appContext, faces.videosOf(trackIds))
    /** Videos with a confirmed face not looked at closer as they are now. */
    suspend fun refineWanted() = faces.refineWanted()
    /** "Analizza meglio i suoi video" in a person's profile: [fileIds] looked at again, each result posted. */
    fun refineVideos(fileIds: List<String>) = com.cripta.app.work.ConversionService.startFaceRefine(appContext, fileIds, force = true)
    /** "Analizza meglio i video con persone confermate". */
    fun refineAll() = faceAction {
        com.cripta.app.work.ConversionService.startFaceRefine(appContext, faces.refineWanted(), manual = true)
    }
    fun rejectFace(trackId: Long, personId: Long) = undoable(str(R.string.rejected)) { faces.reject(trackId, personId) }
    fun setPersonCover(personId: Long, trackId: Long) = faceAction { faces.setCover(personId, trackId) }
    /** "Da sistemare": the unassigned faces grouped by likeness. */
    suspend fun unsortedGroups() = faces.unsortedGroups()
    fun deleteFaces(trackIds: Collection<Long>, everywhere: Boolean) =
        undoable(plural(R.plurals.faces_deleted, trackIds.size) + if (everywhere) str(R.string.everywhere_suffix) else "") {
            faces.deleteFaces(trackIds, everywhere)
        }
    /** A group of faces to [personId], or to a new person named [newName]. */
    fun assignFaces(trackIds: Collection<Long>, personId: Long?, newName: String) =
        undoable(plural(R.plurals.faces_assigned, trackIds.size)) {
            faces.assignAll(trackIds, personId ?: faces.newPerson(newName)).also { lookCloser(trackIds) }
        }
    /** Faces the user deleted, left out of new analyses. */
    val ignoredFaces: StateFlow<Int> = faces.ignoredCount()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0)
    fun forgetIgnoredFaces() = faceAction { faces.forgetIgnored() }
    fun setPersonProfile(personId: Long, notes: String, links: List<String>) = faceAction { faces.setProfile(personId, notes, links) }
    fun setPeopleSort(key: com.cripta.app.data.PeopleSort, ascending: Boolean) = viewModelScope.launch { store.setPeopleSort(key, ascending) }
    /** "Solo persone note": suggestions only from people with faces in at least two media. */
    suspend fun suggestionsFor(personId: Long) =
        faces.suggestionsFor(personId, minMedia = if (settings.value.facesMode == "known") 2 else 1)
    /** "Da confermare": the faces like a known person, across everyone. */
    suspend fun knownSuggestions() = faces.knownSuggestions().also { lastKnown = it }
    /** The last "Da confermare" list, shown at once when the page comes back from a media. */
    var lastKnown: List<com.cripta.app.data.face.FaceRepository.PersonSuggestion>? = null
    suspend fun frameOf(card: com.cripta.app.data.db.TrackCard) = faces.frameOf(card)
    /** "È lui/lei" from the suggestions: no snackbar, the list moves on to the next face. */
    fun acceptSuggestion(trackId: Long, personId: Long) = faceAction { faces.assign(trackId, personId); lookCloser(listOf(trackId)) }
    fun refuseSuggestion(trackId: Long, personId: Long) = faceAction { faces.reject(trackId, personId) }
    suspend fun detectedSince(since: Long) = faces.detectedSince(since)
    fun clearDetected(fileIds: Collection<String>) = faceAction {
        val n = faces.clearDetected(fileIds)
        faceMessage.value = plural(R.plurals.faces_deleted_n, n)
    }
    fun openPerson(personId: Long) = com.cripta.app.ui.people.PeopleNav.openPerson(personId)
    /** Cartelle filtered on [personId]. */
    fun showPersonInVault(personId: Long) = vaultNavigator.applyFilters(com.cripta.app.ui.vault.Filters(personIds = setOf(personId), wholeVault = true))
    fun describeMedia(fileId: String) = faceAction { faceMessage.value = faces.describe(fileId) }
    /** Open [id] among [ids] in the viewer, a video 2 s before the face at [timeMs]. */
    fun openPersonMedia(ids: List<String>, id: String, timeMs: Long?) {
        viewerQueue.set(ids)
        if (timeMs != null && timeMs > 0) viewerQueue.seekOnOpen(id, maxOf(0L, timeMs - 2_000))
    }
    fun setSwipeToClose(v: Boolean) = viewModelScope.launch { store.setSwipeToClose(v) }
    fun setSplitDetails(v: Boolean) = viewModelScope.launch { store.setSplitDetails(v) }
    fun setHoldForSpeed(v: Boolean) = viewModelScope.launch { store.setHoldForSpeed(v) }
    fun setHoldSpeed(x10: Int) = viewModelScope.launch { store.setHoldSpeed(x10) }
    fun setControlsTimeout(sec: Int) = viewModelScope.launch { store.setControlsTimeout(sec) }
    fun setViewerFilmstrip(v: Boolean) = viewModelScope.launch { store.setViewerFilmstrip(v) }
    fun setPauseOnLeave(v: Boolean) = viewModelScope.launch { store.setPauseOnLeave(v) }
    fun setFilmstripShape(v: com.cripta.app.data.StripShape) = viewModelScope.launch { store.setFilmstripShape(v) }
    fun setFilmstripSpan(v: Int) = viewModelScope.launch { store.setFilmstripSpan(v) }
    fun setFilmstripSize(v: Int) = viewModelScope.launch { store.setFilmstripSize(v) }
    fun setFilmstripTrueAspect(v: Boolean) = viewModelScope.launch { store.setFilmstripTrueAspect(v) }
    fun setUpdatePrerelease(v: Boolean) = viewModelScope.launch { store.setUpdatePrerelease(v); _update.value = UpdateState.Idle }
    fun setVideoStartMuted(v: Boolean) = viewModelScope.launch { store.setVideoStartMuted(v) }
    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** Backup/restore running now (label shown with a progress bar), or null. */
    val backupBusy: StateFlow<String?> = backups.running.map { it?.label }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, backups.running.value?.label)
    /** 0..1 while a backup / restore streams, null when idle or the size is unknown. */
    val backupProgress: StateFlow<Float?> = backups.running.map { it?.progress }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, backups.running.value?.progress)
    /** What a backup made now would contain: (files, bytes). Live, always shown in its section. */
    val backupEstimate: StateFlow<Pair<Int, Long>?> = repo.backupEstimate(
        store.settings.map { it.backupCompressDocs }.distinctUntilChanged(),
        store.settings.map { it.backupScope }.distinctUntilChanged(),
    ).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)
    /** Top-level folders, each one a choice of the backup (with everything under it). */
    val backupFolders: StateFlow<List<com.cripta.app.data.db.FolderEntity>> = repo.folders(null)
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    /** Files and size of each folder with everything under it: the weight of each backup choice. */
    val backupFolderStats: StateFlow<Map<Long, com.cripta.app.data.FolderStat>> =
        kotlinx.coroutines.flow.combine(repo.allFolders(), repo.folderAggregates()) { f, a -> com.cripta.app.data.computeFolderStats(f, a) }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyMap())
    /** The files in no folder, the other backup choice: count and size. */
    val backupRootStat: StateFlow<com.cripta.app.data.FolderStat> =
        repo.files(null).map { l -> com.cripta.app.data.FolderStat(l.size, l.sumOf { it.file.sizeBytes }) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), com.cripta.app.data.FolderStat(0, 0))
    fun setBackupScope(v: com.cripta.app.data.BackupScope) = viewModelScope.launch { store.setBackupScope(v) }

    init {
        // The outcome of a backup / restore run by the service, shown when Settings is open.
        viewModelScope.launch {
            backups.result.collect { r -> if (r != null) { _message.value = r; backups.consumeResult() } }
        }
    }

    /**
     * Backup and restore run in the background service, in the foreground: here, with the app in
     * the background, the process could be killed halfway (a truncated archive, a half restore).
     */
    fun exportBackup(uri: android.net.Uri, passphrase: String) = viewModelScope.launch {
        val s = runCatching { store.settingsOnce() }.getOrNull()
        startBackup(com.cripta.app.data.BackupJobs.Request(restore = false, uri = uri, passphrase = passphrase.toCharArray(),
            compressDocs = s?.backupCompressDocs ?: false, scope = s?.backupScope ?: com.cripta.app.data.BackupScope()))
    }

    fun importBackup(uri: android.net.Uri, passphrase: String) =
        startBackup(com.cripta.app.data.BackupJobs.Request(restore = true, uri = uri, passphrase = passphrase.toCharArray(), compressDocs = false))

    private fun startBackup(request: com.cripta.app.data.BackupJobs.Request) {
        if (!backups.offer(request)) return
        runCatching { com.cripta.app.work.ConversionService.startBackup(appContext) }.onFailure {
            logException(android.util.Log.ERROR, "SettingsViewModel", "backup service not started", it)
            backups.take()?.passphrase?.fill('\u0000')
            backups.finish(if (request.restore) str(R.string.restore_not_started) else str(R.string.backup_not_started))
        }
    }

    fun createTag(name: String, alias: String? = null, color: Int? = null, icon: String? = null) = viewModelScope.launch {
        repo.createTag(name, alias, icon)
        if (color != null) repo.setTagColorByName(name, color)
    }
    fun setTagColor(id: Long, color: Int?) = viewModelScope.launch { repo.setTagColor(id, color) }
    fun setTagIcon(name: String, icon: String?) = viewModelScope.launch { repo.setTagIcon(name, icon) }

    /**
     * Gives every tag a colour from the sober palette, at random but spread out (no two tags share
     * one until the palette runs out). Replaces the colours chosen so far.
     */
    fun randomizeTagColors() = viewModelScope.launch {
        var pool = emptyList<androidx.compose.ui.graphics.Color>()
        tags.value.forEach { t ->
            if (pool.isEmpty()) pool = com.cripta.app.ui.theme.TagPalette.shuffled()
            val c = pool.first(); pool = pool.drop(1)
            repo.setTagColor(t.id, c.toArgb())
        }
    }
    fun setTagPinned(name: String, pinned: Boolean) = viewModelScope.launch { repo.setTagPinned(name, pinned) }
    fun setShowRecentTags(v: Boolean) = viewModelScope.launch { store.setShowRecentTags(v) }
    fun setBackupCompressDocs(v: Boolean) = viewModelScope.launch { store.setBackupCompressDocs(v) }
    fun setRecentTagsCount(v: Int) = viewModelScope.launch { store.setRecentTagsCount(v) }
    fun setViewerQuickTags(v: Boolean) = viewModelScope.launch { store.setViewerQuickTags(v) }
    fun renameTag(id: Long, name: String) = editTagName(id, name)

    private fun editTagName(id: Long, name: String) = viewModelScope.launch {
        try {
            repo.renameTag(id, name)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _message.value = str(R.string.tag_exists, name.trim())
        }
    }
    fun deleteTag(id: Long) = viewModelScope.launch { repo.deleteTag(id) }
    fun setTagAlias(name: String, alias: String?) = viewModelScope.launch { repo.setTagAlias(name, alias) }

    /** Edit a label's name and alias together (used by the merged edit dialog). */
    fun editTag(id: Long, newName: String, alias: String?) = viewModelScope.launch {
        val n = newName.trim()
        if (n.isEmpty()) return@launch
        // Tag names are unique: renaming onto an existing one threw out of the coroutine (crash).
        try {
            repo.renameTag(id, n)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // SQLCipher's own constraint exception, not android.database.sqlite's: caught broadly.
            _message.value = str(R.string.tag_exists, n)
            return@launch
        }
        repo.setTagAlias(n, alias)
    }
    fun setTagSortMode(m: com.cripta.app.data.TagSortMode) = viewModelScope.launch { store.setTagSortMode(m) }

    /** Move a tag up/down in the custom order by swapping with its neighbor. */
    fun moveTag(id: Long, up: Boolean) = viewModelScope.launch {
        val ordered = tags.value.map { it.id }.toMutableList()
        val i = ordered.indexOf(id)
        if (i < 0) return@launch
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= ordered.size) return@launch
        ordered[i] = ordered[j].also { ordered[j] = ordered[i] }
        repo.reorderTags(ordered)
    }

    /** Puts tag [id] at [index] of the library (a drag in the custom order); the others close up. */
    fun moveTagTo(id: Long, index: Int) = viewModelScope.launch {
        val ordered = tags.value.map { it.id }.toMutableList()
        val i = ordered.indexOf(id)
        if (i < 0) return@launch
        ordered.add(index.coerceIn(0, ordered.size - 1), ordered.removeAt(i))
        repo.reorderTags(ordered)
    }

    fun lockNow() = session.lock()

    // --- Unlock mode (app PIN) ---

    private val _unlockMode = kotlinx.coroutines.flow.MutableStateFlow(keyVault.unlockMode)
    val unlockMode: StateFlow<com.cripta.app.security.UnlockMode> = _unlockMode

    /** Cipher the system prompt authorizes to confirm the change and re-wrap the vault key. */
    fun cipherForModeChange(): javax.crypto.Cipher? =
        runCatching { keyVault.cipherForModeChange() }
            .onFailure { logException(android.util.Log.ERROR, "SettingsVM", "mode-change cipher", it) }
            .getOrNull()

    /** Confirms the current app PIN before a change; [systemLayer] in SYSTEM_AND_PIN mode (see [unwrapSystemLayer]). */
    suspend fun verifyCurrentPin(pin: CharArray, systemLayer: ByteArray? = null): com.cripta.app.security.KeyVault.PinResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try { keyVault.verifyPin(pin, systemLayer) } finally { pin.fill('0') }
        }

    /** Decrypt cipher for the system step of confirming a change in SYSTEM_AND_PIN mode. */
    fun cipherForSystemStep(): javax.crypto.Cipher? =
        runCatching { keyVault.cipherForUnlock() }
            .onFailure { logException(android.util.Log.ERROR, "SettingsVM", "system-step cipher", it) }
            .getOrNull()

    /** The PIN-sealed layer the authorized [cipher] opens (SYSTEM_AND_PIN); wipe it after use. */
    fun unwrapSystemLayer(cipher: javax.crypto.Cipher): ByteArray? =
        runCatching { keyVault.unwrapSystemLayer(cipher) }
            .onFailure { logException(android.util.Log.ERROR, "SettingsVM", "system-step unwrap", it) }
            .getOrNull()

    fun pinWaitSeconds(): Long = keyVault.pinWaitSeconds()

    /** Applies [mode]; [pin] when it uses the app PIN, [cipher] (authorized) when it uses the prompt. */
    val secretKind: com.cripta.app.security.SecretKind get() = keyVault.secretKind

    fun applyUnlockMode(
        mode: com.cripta.app.security.UnlockMode,
        pin: CharArray?,
        kind: com.cripta.app.security.SecretKind,
        cipher: javax.crypto.Cipher?,
    ) =
        viewModelScope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                try { runCatching { keyVault.changeMode(mode, pin, kind, cipher) } } finally { pin?.fill('0') }
            }
            r.onSuccess {
                _unlockMode.value = keyVault.unlockMode
                _message.value = str(R.string.unlock_mode_set, mode.label.replaceFirstChar { it.lowercase() })
            }.onFailure {
                logException(android.util.Log.ERROR, "SettingsVM", "unlock mode change failed", it)
                _message.value = str(R.string.unlock_mode_failed)
            }
        }

    // --- Duplicate scan (delegates to the dedicated DuplicateScanner module) ---

    /** Which result set the UI should show, if any. */
    enum class DupMode { NONE, EXACT, SIMILAR }

    private val _dupMode = kotlinx.coroutines.flow.MutableStateFlow(DupMode.NONE)
    val dupMode: StateFlow<DupMode> = _dupMode
    /** The scan runs in the foreground service (continues in background); progress comes from the store. */
    val dupScanning: StateFlow<Boolean> =
        dupStore.state.map { it.running != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val dupProgress: StateFlow<Pair<Int, Int>> =
        dupStore.state.map { it.done to it.total }.stateIn(viewModelScope, SharingStarted.Eagerly, 0 to 0)
    /** A finished scan the user hasn't opened yet (e.g. it completed in the background). */
    /** The last scan's result, seen or not (it can be opened again until the app closes). */
    val dupLastResult: StateFlow<com.cripta.app.data.dedup.DupScanStore.Result?> =
        dupStore.state.map { it.result }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val dupResultWaiting: StateFlow<com.cripta.app.data.dedup.DupScanStore.Result?> =
        dupStore.state.map { it.result?.takeIf { r -> !r.seen } }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** Set when this screen started the scan: open the results as soon as it finishes. */
    private var openWhenDone = false
    private val _exactGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DuplicateScanner.ExactGroup>>(emptyList())
    val exactGroups: StateFlow<List<DuplicateScanner.ExactGroup>> = _exactGroups
    private val _similarGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DuplicateScanner.SimilarGroup>>(emptyList())
    val similarGroups: StateFlow<List<DuplicateScanner.SimilarGroup>> = _similarGroups
    /** How many files/images the last scan actually examined — shown so the user sees it ran. */
    private val _dupScannedCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val dupScannedCount: StateFlow<Int> = _dupScannedCount

    /** One file of a duplicate group, with what's needed to compare it against the others. */
    data class DupCandidate(
        val file: com.cripta.app.data.db.FileEntity,
        val tags: List<String>,
        /** Display width x height, when readable. */
        val resolution: Pair<Int, Int>?,
    ) {
        val pixels: Long get() = resolution?.let { it.first.toLong() * it.second } ?: 0L
    }

    /** A duplicate group ready for side-by-side comparison, with the suggested copy to keep. */
    data class DupGroup(
        val candidates: List<DupCandidate>,
        val bestId: String,
        /** Short human reasons why [bestId] is suggested. */
        val reasons: List<String>,
        /** True for byte-identical copies (quality is equal, only metadata differs). */
        val exact: Boolean,
    ) {
        val best: DupCandidate get() = candidates.first { it.file.id == bestId }
        /** Tags present on other copies but missing on the suggested one (moved when resolving). */
        val tagsToMove: List<String> get() =
            candidates.filter { it.file.id != bestId }.flatMap { it.tags }.distinct().filterNot { it in best.tags }
    }

    private val _dupGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DupGroup>>(emptyList())
    val dupGroups: StateFlow<List<DupGroup>> = _dupGroups
    /** Label of the last resolution, e.g. "Tenuto X · spostate 2 etichette". */
    private val _dupNotice = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val dupNotice: StateFlow<String?> = _dupNotice
    fun clearDupNotice() { _dupNotice.value = null; _dupUndo.value = emptyList(); beforeResolution = null }

    suspend fun thumb(file: com.cripta.app.data.db.FileEntity): android.graphics.Bitmap? = thumbs.load(file)
    /** Seekable decrypting channel for in-place video preview (nothing written to disk). */
    fun channelFor(file: com.cripta.app.data.db.FileEntity): java.nio.channels.SeekableByteChannel = repo.seekableChannel(file)
    /** Decrypted image bytes for the full-size preview, held in memory only. */
    suspend fun imageBytes(file: com.cripta.app.data.db.FileEntity): ByteArray = repo.decryptBytes(file)

    /**
     * Order candidates best-first: highest resolution, then (for videos) seekable MP4, then larger
     * file (higher bitrate), then the one already carrying the organisation (favorite, tags, link),
     * then the oldest import.
     */
    private fun rank(c: List<DupCandidate>): List<DupCandidate> = c.sortedWith(
        compareByDescending<DupCandidate> { it.pixels }
            .thenByDescending { if (com.cripta.app.data.VaultRepository.isVideo(it.file.mimeType)) it.file.mimeType == "video/mp4" else false }
            .thenByDescending { it.file.sizeBytes }
            .thenByDescending { it.file.isFavorite }
            .thenByDescending { it.tags.size }
            .thenByDescending { !it.file.sourceUrl.isNullOrBlank() }
            .thenBy { it.file.importedAt }
    )

    private fun reasonsFor(best: DupCandidate, others: List<DupCandidate>, exact: Boolean): List<String> {
        val r = mutableListOf<String>()
        if (!exact) {
            if (best.pixels > 0 && others.all { it.pixels < best.pixels }) {
                r += str(R.string.dup_reason_resolution, best.resolution!!.first, best.resolution.second)
            }
            if (best.file.mimeType == "video/mp4" && others.any { com.cripta.app.data.VaultRepository.isVideo(it.file.mimeType) && it.file.mimeType != "video/mp4" }) {
                r += str(R.string.dup_reason_mp4)
            }
            if (r.isEmpty() && others.all { it.file.sizeBytes < best.file.sizeBytes }) r += str(R.string.dup_reason_quality)
        } else {
            r += str(R.string.dup_reason_identical)
        }
        if (best.file.isFavorite && others.none { it.file.isFavorite }) r += str(R.string.dup_reason_favorite)
        if (best.tags.isNotEmpty() && others.all { it.tags.size < best.tags.size }) r += str(R.string.dup_reason_tags)
        if (!best.file.sourceUrl.isNullOrBlank() && others.all { it.file.sourceUrl.isNullOrBlank() }) r += str(R.string.dup_reason_link)
        if (others.all { it.file.importedAt > best.file.importedAt }) r += str(R.string.dup_reason_first)
        return r
    }

    private suspend fun buildGroups(sets: List<List<com.cripta.app.data.db.FileEntity>>, exact: Boolean): List<DupGroup> {
        val ids = sets.flatten().map { it.id }
        val tags = repo.tagNamesOf(ids)
        return sets.mapNotNull { files ->
            if (files.size < 2) return@mapNotNull null
            // Identical bytes have identical resolution: skip the probe for exact groups.
            val cands = files.map { f -> DupCandidate(f, tags[f.id].orEmpty(), if (exact) null else thumbs.mediaResolution(f)) }
            val ranked = rank(cands)
            val best = ranked.first()
            DupGroup(ranked, best.file.id, reasonsFor(best, ranked.drop(1), exact), exact)
        }
    }

    fun scanExact() {
        if (dupScanning.value) return
        openWhenDone = true
        com.cripta.app.work.ConversionService.startDupScan(appContext, similar = false)
    }

    fun scanSimilar() {
        if (dupScanning.value) return
        openWhenDone = true
        com.cripta.app.work.ConversionService.startDupScan(appContext, similar = true)
    }

    fun cancelScan() {
        openWhenDone = false
        com.cripta.app.work.ConversionService.cancelDupScan(appContext)
    }

    /** Open the stored scan result in the comparison screen. */
    fun openScanResult() = viewModelScope.launch {
        val r = dupStore.state.value.result ?: return@launch
        val exact = r.mode == com.cripta.app.data.dedup.DupScanStore.Mode.EXACT
        _exactGroups.value = if (exact) r.groups.map { DuplicateScanner.ExactGroup(it.first().sizeBytes, it) } else emptyList()
        _similarGroups.value = if (!exact) r.groups.map { DuplicateScanner.SimilarGroup(it, 0) } else emptyList()
        _dupGroups.value = buildGroups(r.groups, exact)
        _dupScannedCount.value = r.scanned
        _dupMode.value = if (exact) DupMode.EXACT else DupMode.SIMILAR
        _dupFreed.value = DupFreed()
        _dupApart.value = 0
        lastSimilarPage = 0
        dupStore.markSeen()
    }

    init {
        // Results produced by the background scan: open them if this screen asked for the scan,
        // or when the user tapped the "scan finished" notification.
        viewModelScope.launch {
            dupStore.state.collect { st ->
                val r = st.result
                if (r != null && !r.seen && openWhenDone && st.running == null) { openWhenDone = false; openScanResult() }
                if (st.error != null && openWhenDone) {
                    openWhenDone = false
                    android.util.Log.w("SettingsViewModel", "duplicate scan failed: ${st.error}")
                    _message.value = str(R.string.scan_failed)
                }
            }
        }
        viewModelScope.launch {
            dupStore.openRequested.collect { req ->
                if (req) {
                    dupStore.consumeOpen()
                    nav.open(SettingsPage.SPAZIO.name)
                    if (dupStore.state.value.result != null) openScanResult()
                }
            }
        }
    }

    /**
     * Keep [keepId] and delete every other copy of its group, moving their tags (plus favorite and
     * source link) onto the kept file so no organisation is lost.
     */
    fun keepOnly(keepId: String) = keepCopies(setOf(keepId))

    /**
     * Keep [keepIds] of their group and delete the other copies. The tags, favorite mark and link
     * of the deleted ones go to the kept copy ranked first (the suggested one when it is kept).
     */
    fun keepCopies(keepIds: Set<String>) = launchHeld("keepCopies") {
        val group = _dupGroups.value.firstOrNull { g -> g.candidates.any { it.file.id in keepIds } } ?: return@launchHeld
        val keepId = group.candidates.first { it.file.id in keepIds }.file.id
        // The kept copy deleted or trashed since the scan: removing the others too would leave
        // no live copy at all (and the trash is purged after a while).
        val kept = repo.fileById(keepId)
        if (kept == null || kept.deletedAt != null) {
            dropFromResults(setOf(keepId))
            _dupNotice.value = str(R.string.dup_keep_gone)
            return@launchHeld
        }
        val remove = unchangedSinceScan(group.candidates.filter { it.file.id !in keepIds })
        if (remove.isEmpty()) return@launchHeld
        val freedNow = group.candidates.filter { it.file.id in remove }.sumOf { it.file.sizeBytes }
        val outcome = repo.mergeDuplicates(keepId, remove)
        if (!outcome.merged) {
            // The kept copy went away between the check and the merge: nothing was deleted.
            dropFromResults(setOf(keepId))
            _dupNotice.value = str(R.string.dup_keep_gone)
            return@launchHeld
        }
        val moved = outcome.tagsMoved
        // Copies sent to the trash keep their cover (restoring them must not lose a chosen cover).
        val trash = trashOn()
        // With the trash on the blobs stay on the phone: the space is only freed when it is emptied.
        lastFreed = if (trash) DupFreed(trashedBytes = freedNow, groups = 1) else DupFreed(bytes = freedNow, groups = 1)
        _dupFreed.value = _dupFreed.value + lastFreed
        keptBefore = KeptBefore(keepId, moved, outcome.previousFavorite, outcome.previousSourceUrl)
        if (!trash) remove.forEach { thumbs.evict(it) }
        _dupUndo.value = if (trash) remove else emptyList()
        beforeResolution = if (trash) snapshotResults() else null
        dropFromResults(remove.toSet())
        val name = group.candidates.first { it.file.id == keepId }.file.originalName
        _dupNotice.value = buildString {
            append(if (keepIds.size > 1) str(R.string.dup_kept_n, keepIds.size) else str(R.string.dup_kept_name, name))
            append(plural(R.plurals.dup_deleted, remove.size))
            if (trash) append(str(R.string.in_trash_paren))
            if (moved.isNotEmpty()) append(str(R.string.dup_tags_moved_n, moved.size))
        }
    }

    /** Space freed and groups resolved since the results were opened, for "Tutto sistemato". */
    private val _dupFreed = kotlinx.coroutines.flow.MutableStateFlow(DupFreed())
    val dupFreed: StateFlow<DupFreed> = _dupFreed
    private var lastFreed = DupFreed()
    /** What the last resolution gave the kept copy, taken back by [undoDuplicates]. */
    private class KeptBefore(val keepId: String, val addedTags: List<String>, val favorite: Boolean, val sourceUrl: String?)
    private var keptBefore: KeptBefore? = null
    /** Groups the user said are not alike since the results were opened. */
    private val _dupApart = kotlinx.coroutines.flow.MutableStateFlow(0)
    val dupApart: StateFlow<Int> = _dupApart
    /** The similar-media page in view: the same one again after a media opened from it. */
    var lastSimilarPage = 0

    /** "Non sono simili": the group leaves the results and its media are never grouped together again. */
    fun notSimilar(fileIds: List<String>) = launchHeld("notSimilar") {
        scanner.keepApart(fileIds)
        _dupApart.value += 1
        _dupUndo.value = emptyList()
        keptBefore = null
        dropFromResults(fileIds.toSet())
        _dupNotice.value = str(R.string.dup_marked_not_similar)
    }

    /** The results as they were before the last resolution, put back by [undoDuplicates]. */
    private class ResultsSnapshot(
        val dup: List<DupGroup>,
        val exact: List<DuplicateScanner.ExactGroup>,
        val similar: List<DuplicateScanner.SimilarGroup>,
        val stored: List<List<com.cripta.app.data.db.FileEntity>>?,
    )
    private var beforeResolution: ResultsSnapshot? = null

    private fun snapshotResults() =
        ResultsSnapshot(_dupGroups.value, _exactGroups.value, _similarGroups.value, dupStore.state.value.result?.groups)

    /** Copies just sent to the trash by a resolution, restorable with [undoDuplicates]. */
    private val _dupUndo = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val dupUndo: StateFlow<List<String>> = _dupUndo

    /** Bring back the copies removed by the last resolution (only possible with the trash on). */
    fun undoDuplicates() = launchHeld("undoDuplicates") {
        val ids = _dupUndo.value
        if (ids.isEmpty()) return@launchHeld
        _dupUndo.value = emptyList()
        ids.forEach { repo.restore(it) }
        // The copies are back: the kept one gives back the tags, favorite mark and link it received.
        keptBefore?.let { repo.undoMergeOnKept(it.keepId, it.addedTags, it.favorite, it.sourceUrl) }
        keptBefore = null
        _dupFreed.value = _dupFreed.value - lastFreed
        lastFreed = DupFreed()
        // The group comes back too, re-read (the kept copy has the tags it was given meanwhile).
        beforeResolution?.let { r ->
            _dupGroups.value = r.dup
            _exactGroups.value = r.exact
            _similarGroups.value = r.similar
            r.stored?.let { dupStore.putBack(it) }
            dropFromResults(emptySet())
        }
        beforeResolution = null
        _dupNotice.value = plural(R.plurals.dup_restored, ids.size)
    }

    /**
     * Delete one copy. Its tags are not lost: they move to the copy that remains suggested in the
     * group (so deleting a tagged copy and keeping an untagged one keeps the tags).
     */
    fun deleteDuplicate(fileId: String) = launchHeld("deleteDuplicate") {
        val group = _dupGroups.value.firstOrNull { g -> g.candidates.any { it.file.id == fileId } }
        group?.candidates?.firstOrNull { it.file.id == fileId }?.let { c ->
            if (unchangedSinceScan(listOf(c)).isEmpty()) { dropFromResults(setOf(fileId)); return@launchHeld }
        }
        var moved = emptyList<String>()
        if (group != null) {
            // The heir must still be live: a copy trashed or deleted since the scan cannot take the
            // tags, and deleting this one would then leave no live copy at all.
            val others = group.candidates.filter { it.file.id != fileId }
            val live = others.filter { c -> repo.fileById(c.file.id)?.let { it.deletedAt == null } == true }
            val heir = (live.firstOrNull { it.file.id == group.bestId } ?: rank(live).firstOrNull())?.file?.id
            if (heir == null) {
                dropFromResults(others.map { it.file.id }.toSet())
                _dupNotice.value = str(R.string.dup_no_other_copy)
                return@launchHeld
            }
            val outcome = repo.mergeDuplicates(heir, listOf(fileId))
            if (!outcome.merged) {
                dropFromResults(setOf(heir))
                _dupNotice.value = str(R.string.dup_keep_gone)
                return@launchHeld
            }
            moved = outcome.tagsMoved
            keptBefore = KeptBefore(heir, moved, outcome.previousFavorite, outcome.previousSourceUrl)
        } else {
            repo.deleteOrTrash(fileId)
            keptBefore = null
        }
        val trash = trashOn()
        if (!trash) thumbs.evict(fileId)
        _dupUndo.value = if (trash) listOf(fileId) else emptyList()
        beforeResolution = if (trash) snapshotResults() else null
        dropFromResults(setOf(fileId))
        _dupNotice.value = buildString {
            append(if (trash) str(R.string.copy_to_trash) else str(R.string.copy_deleted))
            if (moved.isNotEmpty()) append(str(R.string.dup_tags_moved_list, moved.joinToString(", ") { "#$it" }))
        }
    }

    /**
     * The ids of [candidates] still as the scan saw them. Results are kept and can be opened
     * later (from the notification): a file edited (a note), deleted or moved to the trash since
     * then is no longer a proven copy and is not deleted as one.
     */
    private suspend fun unchangedSinceScan(candidates: List<DupCandidate>): List<String> = candidates.mapNotNull { c ->
        val now = runCatching { repo.fileById(c.file.id) }.getOrNull() ?: return@mapNotNull null
        // A hash gained since is fine (hashes are computed lazily); one lost means the content
        // was rewritten (a note saved again clears it).
        c.file.id.takeIf {
            now.deletedAt == null && now.sizeBytes == c.file.sizeBytes &&
                (c.file.contentHash == null || now.contentHash == c.file.contentHash)
        }
    }

    // Unreadable settings count as "trash on" (see VaultRepository.deleteOrTrash).
    private suspend fun trashOn(): Boolean = runCatching { store.settingsOnce().trashEnabled }.getOrDefault(true)

    /** Remove deleted files from every result set, dropping groups left with a single file. */
    private suspend fun dropFromResults(ids: Set<String>) {
        dupStore.dropFiles(ids)
        _exactGroups.value = _exactGroups.value
            .map { g -> g.copy(files = g.files.filterNot { it.id in ids }) }
            .filter { it.files.size > 1 }
        _similarGroups.value = _similarGroups.value
            .map { g -> g.copy(files = g.files.filterNot { it.id in ids }) }
            .filter { it.files.size > 1 }
        // Re-read the survivors (tags/favorite may have changed by the merge) and re-rank.
        val remaining = _dupGroups.value.map { g -> g.candidates.map { it.file }.filterNot { it.id in ids } }
            .filter { it.size > 1 }
            .map { files -> files.mapNotNull { repo.fileById(it.id) } }
        val old = _dupGroups.value.flatMap { it.candidates }.associateBy { it.file.id }
        val tags = repo.tagNamesOf(remaining.flatten().map { it.id })
        _dupGroups.value = remaining.filter { it.size > 1 }.map { files ->
            val exact = _dupGroups.value.firstOrNull { g -> g.candidates.any { it.file.id == files.first().id } }?.exact ?: false
            val cands = files.map { f -> DupCandidate(f, tags[f.id].orEmpty(), old[f.id]?.resolution) }
            val ranked = rank(cands)
            DupGroup(ranked, ranked.first().file.id, reasonsFor(ranked.first(), ranked.drop(1), exact), exact)
        }
    }

    fun closeDuplicates() {
        dupStore.markSeen()
        _dupMode.value = DupMode.NONE
        _dupGroups.value = emptyList()
        _dupNotice.value = null
        _dupUndo.value = emptyList()
        keptBefore = null
        beforeResolution = null
        _exactGroups.value = emptyList()
        _similarGroups.value = emptyList()
    }
}

/**
 * Space and groups settled by the duplicate resolutions of this session: [bytes] really freed,
 * [trashedBytes] only moved to the trash (still on the phone), [groups] resolved.
 */
data class DupFreed(val bytes: Long = 0L, val trashedBytes: Long = 0L, val groups: Int = 0) {
    operator fun plus(o: DupFreed) = DupFreed(bytes + o.bytes, trashedBytes + o.trashedBytes, groups + o.groups)
    operator fun minus(o: DupFreed) = DupFreed(
        (bytes - o.bytes).coerceAtLeast(0), (trashedBytes - o.trashedBytes).coerceAtLeast(0), (groups - o.groups).coerceAtLeast(0))
}

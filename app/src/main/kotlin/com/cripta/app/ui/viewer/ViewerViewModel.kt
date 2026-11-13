package com.cripta.app.ui.viewer

import com.cripta.app.ui.safeScope
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject

sealed interface ViewerState {
    data object Loading : ViewerState
    data class Photo(val file: FileEntity, val bytes: ByteArray) : ViewerState
    data class Video(val file: FileEntity) : ViewerState
    data class Note(val file: FileEntity, val text: String) : ViewerState
    data class Pdf(val file: FileEntity, val bytes: ByteArray) : ViewerState
    data class Other(val file: FileEntity) : ViewerState
    /** [missing]: the file no longer exists (deleted meanwhile) — the viewer drops the page. */
    data class Error(val message: String, val missing: Boolean = false) : ViewerState
}

/** Italian, user-facing text for a failure to open a file (never the raw exception message). */
internal fun friendlyOpenError(t: Throwable): String = when (t) {
    is OutOfMemoryError -> str(R.string.viewer_too_big)
    is java.security.GeneralSecurityException ->
        str(R.string.viewer_decrypt_failed)
    is java.io.FileNotFoundException -> str(R.string.viewer_content_missing)
    is java.io.IOException -> str(R.string.viewer_read_error)
    else -> str(R.string.viewer_open_failed)
}

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repo: VaultRepository,
    private val thumbs: com.cripta.app.media.ThumbnailLoader,
    private val settingsStore: com.cripta.app.data.SettingsStore,
    private val intro: com.cripta.app.data.intro.IntroRepository,
    private val faces: com.cripta.app.data.face.FaceRepository,
    session: com.cripta.app.security.SessionManager,
    private val queue: ViewerQueue,
    savedState: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    /**
     * True while the vault is locked. The player pauses on it whatever "Metti in pausa uscendo dall'app" says: a video
     * kept playing behind the lock screen (or in the background after a lock) otherwise. Collected as
     * a flow, not via composition, since composition is paused while the activity is stopped.
     */
    val vaultLocked: StateFlow<Boolean> = session.locked

    /**
     * The viewer comes back after Android killed the process: [ids] is then the part of the order
     * kept in the saved state, while the pager's own saved page counts in the whole order.
     */
    private var restoredFromSavedQueue = savedState.contains(SAVED_QUEUE)

    /** True once, for the screen first shown after a restore ([restoredFromSavedQueue]): it then goes to [startId]. */
    fun takeRestoredFromSavedQueue(): Boolean = restoredFromSavedQueue.also { restoredFromSavedQueue = false }

    /**
     * Snapshot of the browse order taken when the viewer opened. A file opened without a queue of
     * its own (a face's media from Persone) is not in the last one left behind: it is shown alone,
     * instead of the viewer landing on the first file of that old queue.
     */
    val ids: List<String> =
        // The browse order lives in memory only: after Android killed the process in the
        // background, the restored viewer had the opened file alone. Part of it is kept in the
        // saved state too (see [saveQueueAround]); a viewer restored from it shows that part.
        savedState.get<ArrayList<String>>(SAVED_QUEUE)?.toList()
            ?: savedState.get<String>("fileId").let { id ->
                val q = queue.ids
                if (id.isNullOrEmpty() || id in q) q else listOf(id)
            }

    /**
     * The page to start on: the file shown when the process was killed (swiped to after the
     * opened one), else the opened one. The route keeps naming the file first opened.
     */
    val startId: String? = savedState.get<String>(SAVED_CURRENT) ?: savedState.get<String>("fileId")
    private val saved = savedState

    /** Where the part of [ids] in the saved state starts, -1 before it is saved. */
    private var savedFrom = -1

    /**
     * Keep in the saved state the part of the browse order around [current], [MAX_SAVED_QUEUE] ids
     * at most: the whole order (thousands of ids) made the saved state too big to hand to Android
     * when the app went to the background, two viewers in the back stack above all.
     */
    private fun saveQueueAround(current: String?) {
        // A short order is saved whole, once.
        if (savedFrom >= 0 && ids.size <= MAX_SAVED_QUEUE) return
        val at = current?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        // Saved again only once [current] leaves the middle half of what is saved.
        if (savedFrom >= 0 && at - savedFrom in MAX_SAVED_QUEUE / 4 until MAX_SAVED_QUEUE * 3 / 4) return
        val window = queueWindow(ids.size, at, MAX_SAVED_QUEUE)
        if (window.isEmpty()) { savedFrom = 0; saved[SAVED_QUEUE] = ArrayList<String>(); return }
        if (window.first == savedFrom) return
        savedFrom = window.first
        saved[SAVED_QUEUE] = ArrayList(ids.subList(window.first, window.last + 1))
    }

    init { saveQueueAround(startId) }

    fun setCurrent(id: String) {
        saved[SAVED_CURRENT] = id
        saveQueueAround(id)
    }

    /**
     * The pages the viewer shows: the browse order, minus files deleted from the viewer (so a delete
     * moves on to the next file instead of closing). Lives here so it survives leaving the viewer
     * for the note editor and coming back. Falls back to the opened file alone.
     */
    val liveIds: androidx.compose.runtime.snapshots.SnapshotStateList<String> =
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            addAll(ids.ifEmpty { listOfNotNull(savedState.get<String>("fileId")) })
        }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** Bumped after favorite/tag changes so pages re-read the updated file. */
    private val _refresh = MutableStateFlow(0)
    val refresh: StateFlow<Int> = _refresh

    val allTags: StateFlow<List<TagEntity>> =
        repo.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun stateFor(id: String): ViewerState = try {
        val file = repo.fileById(id)
        when {
            // Trashed counts as gone (a note deleted from its editor stayed open here, editable).
            file == null || file.deletedAt != null -> ViewerState.Error(str(R.string.viewer_gone), missing = true)
            VaultRepository.isImage(file.mimeType) -> ViewerState.Photo(file, repo.decryptBytes(file))
            VaultRepository.isPlayable(file.mimeType) -> ViewerState.Video(file)
            VaultRepository.isNote(file.mimeType) -> ViewerState.Note(file, repo.noteText(file))
            VaultRepository.isPdf(file.mimeType) -> ViewerState.Pdf(file, repo.decryptBytes(file))
            else -> ViewerState.Other(file)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (t: Throwable) {
        ViewerState.Error(friendlyOpenError(t))
    }

    suspend fun fileById(id: String): FileEntity? = repo.fileById(id)

    data class PlaybackPrefs(
        val loop: Boolean = true,
        val muted: Boolean = false,
        val resume: Boolean = true,
        val seekStepSec: Int = 10,
        val gestures: Boolean = true,
        val volumeGesture: Boolean = true,
        val autoRotate: Boolean = true,
        val pip: Boolean = false,
        val filmstrip: Boolean = true,
        val autoNext: Boolean = true,
        val autoNextSec: Int = 8,
        val swipeToClose: Boolean = true,
        val holdForSpeed: Boolean = true,
        val holdSpeed: Float = 2f,
        val controlsTimeoutSec: Int = 4,
        val stripShape: com.cripta.app.data.StripShape = com.cripta.app.data.StripShape.RECT,
        val stripSpan: Int = 3,
        val stripSize: Int = 1,
        val stripTrueAspect: Boolean = true,
        val pauseOnLeave: Boolean = true,
        val splitDetails: Boolean = false,
        val introAutoSkip: Boolean = false,
        val outroAutoSkip: Boolean = false,
    )

    private fun com.cripta.app.data.Settings.toPlayback() = PlaybackPrefs(
        videoLoop, videoStartMuted, display.resumePlayback, seekStepSec,
        gestureControls, gestureVolume, autoRotate, pictureInPicture, viewerFilmstrip, autoNext,
        autoNextSec, swipeToClose, holdForSpeed, holdSpeedX10 / 10f, controlsTimeoutSec,
        stripShape = filmstripShape, stripSpan = filmstripSpan, stripSize = filmstripSize,
        stripTrueAspect = filmstripTrueAspect, pauseOnLeave = pauseOnLeave, splitDetails = splitDetails,
        introAutoSkip = introAutoSkip, outroAutoSkip = outroAutoSkip,
    )

    /** Player prefs as a live flow (seek step, gestures, rotation, PiP apply without reopening). */
    private val _playback = MutableStateFlow(PlaybackPrefs())
    val playback: StateFlow<PlaybackPrefs> = _playback

    /**
     * True once the stored preferences (follow the video's orientation, the remembered lock) are in
     * [playback] / [rotationLocked]. Until then they hold defaults, and turning the screen from
     * those made a video flash into landscape and back.
     */
    val settingsRead = MutableStateFlow(false)

    /** Orientation lock chosen in the player; kept while swiping and remembered for next time. */
    val rotationLocked = MutableStateFlow(false)

    /** The orientation the lock keeps (ActivityInfo value), valid while [rotationLocked]. */
    var lockedOrientation: Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        private set

    init {
        safeScope.launch {
            settingsStore.settings.collect { s ->
                _playback.value = s.toPlayback()
                if (!settingsRead.value) {
                    // The last choice of the lock button is a preference: the player opens locked again.
                    val o = s.viewerRotationLock
                    if (o >= 0) { lockedOrientation = o; rotationLocked.value = true }
                    settingsRead.value = true
                }
            }
        }
    }

    /** Lock to [orientation] (ActivityInfo value) or free the rotation (null); remembered. */
    fun setRotationLock(orientation: Int?) {
        if (orientation != null) lockedOrientation = orientation
        rotationLocked.value = orientation != null
        safeScope.launch { settingsStore.setViewerRotationLock(orientation ?: -1) }
    }

    /** Cover of any file of the browse list (filmstrip). */
    suspend fun thumbOf(id: String): android.graphics.Bitmap? = repo.fileById(id)?.let { thumbs.load(it) }

    /** Playback preferences read when a player is created. */
    // ---- People (details panel) ----
    fun facesOf(fileId: String) = faces.facesOf(fileId)
    fun personLinksOf(fileId: String) = faces.linksOf(fileId)
    /** Everyone not hidden, starred first then named then by name: the face labels and the "Chi è?" list. */
    val peopleList: kotlinx.coroutines.flow.StateFlow<List<com.cripta.app.data.face.FaceRepository.Person>> = faces.people()
        .map { o -> o.people.filter { !it.entity.ignored }.sortedWith(compareBy({ !it.entity.favorite }, { it.entity.name.isBlank() }, { it.title.lowercase() })) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())
    fun renamePerson(personId: Long, name: String) = faceAction { faces.rename(personId, name) }
    val faceMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val favoritePeople: kotlinx.coroutines.flow.StateFlow<Set<Long>> = faces.people()
        .map { o -> o.people.filter { it.entity.favorite }.map { it.id }.toSet() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptySet())
    fun setPersonFavorite(personId: Long, favorite: Boolean) = faceAction { faces.setFavorite(personId, favorite) }
    /** Where people show in [fileId] (ms), for the progress bar's marks; empty while "Momenti nel lettore" is off. */
    fun moments(fileId: String): kotlinx.coroutines.flow.Flow<LongArray> =
        kotlinx.coroutines.flow.combine(settingsStore.settings.map { it.faceMoments }, faces.facesOf(fileId)) { on, cards ->
            if (!on) LongArray(0)
            else cards.filter { it.personId != null && it.state != "suggested" }.flatMap { it.moments.ifEmpty { listOf(it.timeMs) } }
                .distinct().sorted().toLongArray()
        }
    suspend fun faceCrop(trackId: Long) = faces.crop(trackId)
    private fun faceAction(block: suspend () -> Unit) = safeScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            faceMessage.value = str(R.string.operation_failed, com.cripta.app.work.UserErrors.of(e))
        }
    }
    /**
     * [faceAction] for a change of the faces the "Annulla" still on offer would undo blindly: its
     * snapshot was taken before this change, and taking it back put the old people back over it.
     */
    private fun faceChange(block: suspend () -> Unit) = faceAction { faceUndo.value = null; block() }
    fun confirmFace(trackId: Long, personId: Long) = faceChange { faces.assign(trackId, personId); lookCloser(listOf(trackId)) }
    /**
     * A face confirmed in a video: the video is looked at closer in the background, to join its
     * other faces of that person and set aside those that are not faces
     * ([com.cripta.app.data.face.FaceRepository.refine]). Only that video, nothing given to anyone.
     */
    private suspend fun lookCloser(trackIds: Collection<Long>) =
        com.cripta.app.work.ConversionService.startFaceRefine(appContext, faces.videosOf(trackIds))
    /** "Analizza meglio" in the details of a video with a confirmed face. */
    fun refineFaces(fileId: String) = com.cripta.app.work.ConversionService.startFaceRefine(appContext, listOf(fileId), force = true)
    /** What the closer look did to [fileId], in words (null: it has not looked at it). */
    fun refineNote(fileId: String) = faces.refineNote(fileId)
    fun rejectFace(trackId: Long, personId: Long) = faceChange { faces.reject(trackId, personId) }
    fun describeFaces(fileId: String) = faceAction { faceMessage.value = faces.describe(fileId) }
    fun analyseFaces(fileId: String, deep: Boolean = false) =
        com.cripta.app.work.ConversionService.startFaceScan(appContext, manual = true, fileIds = listOf(fileId), deep = deep)
    /** The faces found here that nobody confirmed go (the analysis was on by mistake). */
    fun clearDetectedFaces(fileId: String) = faceChange { faces.clearDetected(listOf(fileId)) }

    /** The last change to the faces of [fileId], which "Annulla" in its details takes back. */
    class FaceUndo(val fileId: String, val message: String, val undo: com.cripta.app.data.face.Undo)
    val faceUndo = MutableStateFlow<FaceUndo?>(null)
    fun undoFaceChange() {
        val u = faceUndo.value ?: return
        faceUndo.value = null
        safeScope.launch {
            try { u.undo() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                faceMessage.value = str(R.string.operation_failed, com.cripta.app.work.UserErrors.of(e))
                // Not undone: "Annulla" stays on offer to try again (unless something newer took its place).
                if (faceUndo.value == null) faceUndo.value = u
            }
        }
    }
    /** Faces analysed before tracks were joined: those of one person become one when the details open. */
    fun tidyFaces(fileId: String) = faceAction { faces.tidy(fileId) }
    /** "Elimina": faces found by mistake, not found again here; [everywhere] ("Non è un volto"): nowhere. */
    fun deleteFaces(fileId: String, trackIds: Collection<Long>, everywhere: Boolean = false) = faceChange {
        val u = faces.deleteFaces(trackIds, everywhere)
        val what = plural(R.plurals.faces_deleted, trackIds.size)
        faceUndo.value = FaceUndo(fileId, if (everywhere) str(R.string.x_everywhere, what) else what, u)
    }
    /** "Mostra tutte" in the details' tags: kept for every media until "Mostra meno". */
    val detailsAllTags: StateFlow<Boolean> = settingsStore.settings.map { it.detailsAllTags }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
    fun setDetailsAllTags(v: Boolean) = safeScope.launch { settingsStore.setDetailsAllTags(v) }
    /** "Togli": [fileId], added to [personId] by hand, is not theirs any more. */
    fun unlinkPerson(fileId: String, personId: Long) = faceChange {
        faceUndo.value = FaceUndo(fileId, str(R.string.face_removed_from_person), faces.unlink(personId, fileId))
    }
    /** "Stessa persona": faces of [fileId] that are one person become one. */
    fun mergeFaces(fileId: String, trackIds: Collection<Long>) = faceChange {
        val u = faces.mergeFaces(trackIds)
        if (u == null) faceMessage.value = str(R.string.faces_different_people)
        else faceUndo.value = FaceUndo(fileId, str(R.string.faces_joined), u)
    }
    /** The face [trackId] (or, with none, the media itself) goes to [personId]. */
    fun putInPerson(fileId: String, trackId: Long?, personId: Long) = faceChange {
        if (trackId != null) { faces.assign(trackId, personId); offerAlike(fileId, trackId, personId) } else faces.link(personId, fileId)
    }

    /** After "Chi è?" on [trackId]: the other faces of its media that look like [personId], for "Anche questi?". */
    class AlsoFaces(val fileId: String, val trackId: Long, val personId: Long, val faces: List<com.cripta.app.data.face.FaceRepository.Suggestion>) {
        /** The faces left out with a tap: kept here, a rotation that rebuilt the dialog selected them again. */
        val off = MutableStateFlow(emptySet<Long>())
    }
    val alsoFaces = MutableStateFlow<AlsoFaces?>(null)
    /**
     * The closer look at the video starts once "Anche questi?" is answered (or when there is none
     * to ask): started before, it could join the very faces the dialog shows.
     */
    private suspend fun offerAlike(fileId: String, trackId: Long, personId: Long) {
        val list = faces.alikeInMedia(trackId)
        if (list.isNotEmpty()) alsoFaces.value = AlsoFaces(fileId, trackId, personId, list) else lookCloser(listOf(trackId))
    }
    /** "Anche questi?" › "Unisci": [picked] join the face just named. */
    fun joinAlso(a: AlsoFaces, picked: Collection<Long>) = faceChange {
        alsoFaces.value = null
        if (picked.isNotEmpty()) {
            val u = faces.mergeFaces(listOf(a.trackId) + picked)
            if (u == null) faceMessage.value = str(R.string.faces_different_people)
            else faceUndo.value = FaceUndo(a.fileId, str(R.string.faces_joined), u)
        }
        lookCloser(listOf(a.trackId))
    }
    /** "Anche questi?" › "No". */
    fun dismissAlso() {
        val a = alsoFaces.value ?: return
        alsoFaces.value = null
        faceAction { lookCloser(listOf(a.trackId)) }
    }
    /** The moments of a face that "Dividi" can move. */
    suspend fun splittable(trackId: Long) = faces.splittable(trackId)
    /** "Dividi": the faces of [trackId] at [moments] are someone else. */
    fun splitFace(fileId: String, trackId: Long, moments: Set<Long>) = faceChange {
        val u = faces.splitFace(trackId, moments)
        if (u == null) faceMessage.value = str(R.string.faces_pick_some)
        else faceUndo.value = FaceUndo(fileId, str(R.string.face_split), u)
    }
    /** "Aggiungi un volto": the faces at the player's spot (null: none asked, empty: still looking or none). */
    val foundFaces = MutableStateFlow<List<com.cripta.app.data.face.FaceRepository.FoundFace>?>(null)
    val findingFaces = MutableStateFlow(false)
    /** The media [foundFaces] belong to: the dialog is only shown on that one. */
    val foundFacesFile = MutableStateFlow<String?>(null)
    /** Bumped when a search starts or its dialog closes: an older search finishing later is dropped. */
    private var findToken = 0

    /** Close the "add a face" dialog and forget a search still running (it must not reopen it). */
    fun closeFoundFaces() {
        findToken++
        foundFaces.value = null; foundFacesFile.value = null; findingFaces.value = false
    }

    /** The dialog belongs to another media than [fileId] (the viewer moved on): close it. */
    fun closeFoundFacesUnless(fileId: String) {
        if (foundFacesFile.value != null && foundFacesFile.value != fileId) closeFoundFaces()
    }
    /** Where the last search started (ms): faces found later than this were found further on. */
    val findStart = MutableStateFlow(0L)
    /**
     * Look for faces at the player's spot (or [from]); in a video with none there, in the next
     * seconds, the player moving to where they were found.
     */
    fun findFaces(file: com.cripta.app.data.db.FileEntity, from: Long? = null) = faceAction {
        val at = from ?: playerSpots.value[file.id]?.positionMs ?: 0L
        val token = ++findToken
        findStart.value = at
        foundFacesFile.value = file.id
        findingFaces.value = true; foundFaces.value = emptyList()
        try {
            val list = faces.facesFrom(file, at)
            // Closed, or another media's search started, meanwhile: these faces are not for the dialog now.
            if (token != findToken) return@faceAction
            foundFaces.value = list
            list.firstOrNull()?.timeMs?.takeIf { it > at }?.let { seekPlayer(file.id, it) }
        } finally { if (token == findToken) findingFaces.value = false }
    }
    fun addFoundFace(file: com.cripta.app.data.db.FileEntity, f: com.cripta.app.data.face.FaceRepository.FoundFace, personId: Long?, newName: String) = faceChange {
        faces.addFace(file, f, personId ?: faces.newPerson(newName))
        closeFoundFaces()
        // A face added by hand teaches what the person looks like here: their other faces of the video too.
        if (com.cripta.app.data.VaultRepository.isVideo(file.mimeType)) com.cripta.app.work.ConversionService.startFaceRefine(appContext, listOf(file.id))
    }
    fun putInNewPerson(fileId: String, trackId: Long?, name: String) = faceChange {
        val id = faces.newPerson(name)
        if (trackId != null) { faces.assign(trackId, id); offerAlike(fileId, trackId, id) } else faces.link(id, fileId)
    }

    /** A start position asked for by the page that opened [id] (People: the face's moment), once. */
    fun takeSeek(id: String): Long? = queue.takeSeek(id)

    suspend fun playbackPrefs(): PlaybackPrefs =
        runCatching { settingsStore.settingsOnce() }.getOrNull()
            ?.toPlayback() ?: PlaybackPrefs()

    fun segments(fileId: String) = intro.segments(fileId)

    /**
     * Intro / outro starts marked with "Inizio qui" and not ended yet, by video: kept here, the
     * details panel moving to another place in the layout (a rotation) lost them.
     */
    private val introMarks = HashMap<String, androidx.compose.runtime.snapshots.SnapshotStateMap<com.cripta.app.media.intro.SegmentKind, Long>>()
    fun pendingMarks(fileId: String) = introMarks.getOrPut(fileId) { androidx.compose.runtime.mutableStateMapOf() }

    /** A player's place, for the details panel's intro section (which has no player of its own). */
    data class PlayerSpot(val positionMs: Long, val durationMs: Long, val playing: Boolean)

    /** Each open player's spot by file id (neighbouring pages may hold players too). */
    val playerSpots = MutableStateFlow<Map<String, PlayerSpot>>(emptyMap())

    /** A seek asked by the details panel for the player of [fileId]; [play] starts playback too. */
    data class SeekRequest(val fileId: String, val positionMs: Long, val play: Boolean)
    val seekRequests = kotlinx.coroutines.flow.MutableSharedFlow<SeekRequest>(extraBufferCapacity = 4)
    fun seekPlayer(fileId: String, positionMs: Long, play: Boolean = false) {
        seekRequests.tryEmit(SeekRequest(fileId, positionMs, play))
    }

    /** The name of the saved signature this video's intro / outro belongs to, if any. */
    suspend fun signatureName(fileId: String, kind: com.cripta.app.media.intro.SegmentKind): String? =
        runCatching { intro.signatureName(fileId, kind) }.getOrNull()

    /** "ok", the reason a video could not be analysed, or null when it was never analysed. */
    suspend fun introStatus(fileId: String): String? = runCatching { intro.printStatus(fileId) }.getOrNull()

    /** "Applica a tutti i video con la stessa sigla". */
    fun applySegmentToAll(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind) = safeScope.launch {
        val n = runCatching { intro.applyToAll(file, kind) }.getOrNull()
        _message.value = when {
            n == null -> str(R.string.segment_no_sound)
            n == 0 -> str(R.string.rule_saved)
            else -> str(R.string.rule_applied_n, n)
        }
    }

    /** "Analizza ora" from the details panel. */
    fun analyzeIntro(file: FileEntity) {
        com.cripta.app.work.ConversionService.startIntroScan(appContext, manual = true, fileIds = listOf(file.id))
        _message.value = str(R.string.analysis_started)
    }

    /**
     * Ritocco: move a segment's bounds by [startDelta] / [endDelta] ms, kept inside the video and
     * apart. From the bounds saved, not the page's: two quick taps both count.
     */
    fun nudgeSegment(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind, startDelta: Long, endDelta: Long, durationMs: Long) {
        safeScope.launch { intro.nudge(file.id, kind, startDelta, endDelta, durationMs, MIN_MARK_MS) }
    }

    /** "Fine qui". False when the end would not come after the start. */
    fun markEnd(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind, startMs: Long, posMs: Long): Boolean {
        if (posMs < startMs + MIN_MARK_MS) { _message.value = str(R.string.end_after_start); return false }
        safeScope.launch { intro.setManual(file.id, kind, startMs, posMs) }
        return true
    }

    fun removeSegment(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind) =
        safeScope.launch { intro.setNone(file.id, kind) }

    fun searchSegment(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind, seg: com.cripta.app.data.db.MediaSegmentEntity) =
        safeScope.launch {
            val n = intro.searchOthers(file.id, kind, seg.startMs, seg.endMs)
            _message.value = when {
                n == null -> str(R.string.stretch_too_short)
                n == 0 -> str(R.string.not_found_elsewhere)
                else -> str(R.string.found_in_n, n)
            }
        }

    fun rejectSegment(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind,
                      seg: com.cripta.app.data.db.MediaSegmentEntity, everywhere: Boolean) =
        safeScope.launch {
            if (!everywhere) { intro.setNone(file.id, kind); return@launch }
            val n = intro.rejectEverywhere(file, kind, seg.startMs, seg.endMs)
            _message.value = if (n > 0) str(R.string.discarded_here_and_n, n) else str(R.string.discarded_everywhere)
        }

    /**
     * Remember where playback stopped. Near the end counts as finished (next time starts over).
     * Saved on the repository's scope, not viewModelScope: the viewer may be closing, which would
     * cancel the save.
     */
    fun savePosition(fileId: String, posMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && posMs >= durationMs - 5_000
        repo.savePlaybackPos(fileId, if (finished || posMs < 3_000) null else posMs)
    }

    /** Detailed technical info for the viewer's Info dialog (video codec/size/crop/rotation). */
    suspend fun videoInfo(file: FileEntity): String = thumbs.videoDiagnostics(file)

    /** See [ThumbnailLoader.worthConvertingToMp4]. */
    suspend fun canConvertToMp4(file: FileEntity): Boolean = thumbs.worthConvertingToMp4(file)
    suspend fun tagNamesOf(id: String): List<String> = repo.tagNamesOf(id)

    fun channelFor(file: FileEntity): SeekableByteChannel = repo.seekableChannel(file)

    /** One save of a note at a time, in the order of the ticks. */
    private val noteSave = kotlinx.coroutines.sync.Mutex()

    /** A note's saves under way: what is on disk, what the latest tick wants, how many saves wait. */
    private class NoteSaves(var persisted: String, var wanted: String, var pending: Int = 0)
    /** By file id; touched on the main thread only (viewModelScope). */
    private val noteSaves = HashMap<String, NoteSaves>()

    /**
     * A note's text changed in the viewer (a check-list box ticked from [before] to [text]): saved
     * at once. Each save writes the latest wanted text, so quick ticks never put back a tick taken
     * by another; a save that fails brings the note back to what is on disk ([onReverted]) and
     * drops the ticks still waiting, so what is shown is always what is stored.
     */
    fun saveNoteText(file: FileEntity, before: String, text: String, onReverted: (String) -> Unit) {
        val s = noteSaves.getOrPut(file.id) { NoteSaves(before, before) }
        s.wanted = text; s.pending++
        safeScope.launch {
            try {
                noteSave.withLock {
                    val t = s.wanted
                    if (t == s.persisted) return@withLock
                    val saved = try { repo.updateNote(file.id, file.originalName, t); true }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
                    if (saved) {
                        s.persisted = t
                        // Its cover shows its first lines and their boxes: drawn again from the new text.
                        thumbs.evict(file.id)
                    } else {
                        s.wanted = s.persisted
                        _message.value = str(R.string.note_save_failed)
                        onReverted(s.persisted)
                    }
                }
            } finally {
                if (--s.pending == 0) noteSaves.remove(file.id)
            }
        }
    }

    fun toggleFavorite(file: FileEntity) = safeScope.launch {
        repo.toggleFavorite(file.id, !file.isFavorite)
        _refresh.value++
    }

    /** Add one tag by name (new names become tags) without touching the file's other tags. */
    fun addTag(fileId: String, name: String) = safeScope.launch {
        repo.addTags(fileId, listOf(name)); _refresh.value++
    }

    /** Store [url] as the file's source link (blank clears it). */
    fun setSourceUrl(fileId: String, url: String?) = safeScope.launch {
        repo.setSourceUrl(fileId, url); _refresh.value++
    }


    /** Quick-tag bar: toggle one tag on the current file. */
    fun toggleTag(fileId: String, tagId: Long) = safeScope.launch {
        repo.toggleFileTag(fileId, tagId); _refresh.value++
    }

    val display: StateFlow<com.cripta.app.data.DisplayPrefs> =
        settingsStore.settings.map { it.display }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.cripta.app.data.DisplayPrefs())

    /** Save a tag's editor in one go, by id. */
    /** Each tag's use (files, folders), for the tag editor's line under its title. */
    val tagUsage: StateFlow<Map<Long, com.cripta.app.data.db.TagUsage>> =
        repo.tagUsage().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun deleteTag(id: Long) = safeScope.launch { repo.deleteTag(id) }

    fun saveTag(id: Long, draft: com.cripta.app.ui.vault.TagDraft) = safeScope.launch {
        if (repo.saveTag(id, draft.name, draft.alias, draft.color, draft.pinned, draft.icon, draft.onCovers) == com.cripta.app.data.TagSave.NAME_TAKEN)
            _message.value = str(R.string.tag_exists, draft.name)
        _refresh.value++
    }

    /**
     * Create a tag and put it on the file, one after the other in one coroutine: the file's tags are
     * added to, never replaced with a list read before (a change made meanwhile is kept).
     */
    fun createTagOn(fileId: String, draft: com.cripta.app.ui.vault.TagDraft) = safeScope.launch {
        val r = repo.createTag(draft.name, draft.alias, draft.icon, draft.color, draft.pinned, draft.onCovers)
        if (r.id > 0) repo.addTagIds(fileId, listOf(r.id))
        _refresh.value++
    }

    fun download(file: FileEntity) {
        // Run decryption in the foreground service so it survives backgrounding and shows progress.
        com.cripta.app.work.ConversionService.startDownload(appContext, listOf(file.id))
        _message.value = str(R.string.export_started_in, exportFolder(file.mimeType))
    }

    fun delete(fileId: String, onDone: () -> Unit) = safeScope.launch {
        // To the trash when enabled (cover kept for a restore), otherwise shredded at once.
        try {
            val trashed = repo.deleteOrTrash(fileId)
            if (!trashed) thumbs.evict(fileId)
            _message.value = if (trashed) str(R.string.moved_to_trash) else str(R.string.file_deleted)
            onDone()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            _message.value = str(R.string.delete_failed)
        }
    }

    companion object {
        private const val SAVED_QUEUE = "viewer_queue"
        private const val SAVED_CURRENT = "viewer_current"
        /**
         * Ids of the browse order the saved state carries. A Parcel writes strings in UTF-16: about
         * 84 bytes a UUID, so about 25 KB, well under the 1 MB Android can hand over for all the
         * saved states of the app together.
         */
        internal const val MAX_SAVED_QUEUE = 300

        /**
         * The part of a list of [size] items to save, at most [max] long, with [at] in its middle
         * (pushed to the start or the end near them). Empty for an empty list.
         */
        internal fun queueWindow(size: Int, at: Int, max: Int): IntRange {
            if (size <= 0) return IntRange.EMPTY
            if (size <= max) return 0 until size
            val from = (at.coerceIn(0, size - 1) - max / 2).coerceIn(0, size - max)
            return from until from + max
        }

        /** The page of [ids] to start on: [startId], else the opened [fileId], else the first. */
        internal fun startPage(ids: List<String>, startId: String?, fileId: String): Int =
            ids.indexOf(startId ?: fileId).takeIf { it >= 0 } ?: ids.indexOf(fileId).coerceAtLeast(0)
        /** Shortest intro/outro a mark can make. */
        private const val MIN_MARK_MS = 1_000L

        /** Where an export lands on the device (mirrors VaultRepository.restoreToGallery). */
        fun exportFolder(mimeType: String): String = when {
            VaultRepository.isImage(mimeType) -> str(R.string.export_dest_pictures)
            VaultRepository.isVideo(mimeType) -> str(R.string.export_dest_movies)
            else -> str(R.string.export_dest_downloads)
        }
    }

    val trashEnabled: StateFlow<Boolean> =
        settingsStore.settings.map { it.trashEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True while any video conversion is running (drives a progress indicator). Backed by the
     *  foreground service, so it stays correct even if the viewer is closed and reopened. */
    val converting: StateFlow<Boolean> =
        repo.convertingIds.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 0-100 progress of the running transcode (for the in-app popup). */
    val convertingProgress: StateFlow<Int> = repo.conversionProgress

    /** Cancel the running transcode from the app. */
    fun cancelConversion() = com.cripta.app.work.ConversionService.cancelConvert(appContext)

    /** Set to the new MP4's id after a successful conversion; consumed by the UI. */
    private val _convertedId = MutableStateFlow<String?>(null)
    val convertedId: StateFlow<String?> = _convertedId
    /** Id of the ORIGINAL file that was converted (so the keep/delete choice targets the right one,
     *  even if the viewer has since swiped to another page). */
    private val _convertedOriginalId = MutableStateFlow<String?>(null)
    val convertedOriginalId: StateFlow<String?> = _convertedOriginalId
    /** The original's name, so the question says which video it is about (it may not be on screen). */
    private val _convertedOriginalName = MutableStateFlow<String?>(null)
    val convertedOriginalName: StateFlow<String?> = _convertedOriginalName
    fun clearConverted() { _convertedId.value = null; _convertedOriginalId.value = null; _convertedOriginalName.value = null }

    /**
     * Answer the completion prompt: delete (to the trash) or keep the just-converted original.
     * Through the service, like the notification's buttons and the vault banner, so all three
     * agree: the notification goes away and the banner stops asking.
     */
    fun resolveConvertedOriginal(delete: Boolean) {
        _convertedOriginalId.value?.let {
            com.cripta.app.work.ConversionService.resolveOriginal(appContext, it, delete)
            if (delete) thumbs.evict(it)
        }
        clearConverted()
    }

    init {
        // Surface completions from the service (which may outlive a single viewer instance).
        safeScope.launch {
            repo.convertEvents.collect { event ->
                if (event.ask) {
                    _convertedId.value = event.newId
                    _convertedOriginalId.value = event.originalId
                    _convertedOriginalName.value = runCatching { repo.fileById(event.originalId)?.originalName }.getOrNull()
                } else {
                    _message.value = str(R.string.converted_mp4)
                    // Replaced: the original is in the trash now (a missing page), its copy takes
                    // its place, so the viewer shows the MP4 instead of moving on or closing.
                    // Only when it was replaced: "keep both" leaves the original's page where it is.
                    val i = liveIds.indexOf(event.originalId)
                    if (event.replaced && i >= 0 && event.newId !in liveIds) liveIds[i] = event.newId
                }
                _refresh.value++
            }
        }
    }

    /**
     * Transcode a video (e.g. a non-seekable MPEG) into MP4 and encrypt it into the vault, keeping
     * the original. Runs in the foreground service so it survives leaving the viewer/backgrounding
     * and never leaves decrypted plaintext on disk.
     */
    /** Whether the user already chose what happens after a conversion (else the viewer asks). */
    val convertSettings: StateFlow<Pair<Boolean, Int>> =
        settingsStore.settings.map { it.convertAfterChosen to it.trashDays }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false to 7)

    /** Convert with the choice made in the first-conversion dialog, optionally remembering it. */
    fun convertToMp4(file: FileEntity, after: com.cripta.app.data.ConvertAfter, rememberChoice: Boolean) {
        if (rememberChoice) safeScope.launch { settingsStore.setConvertAfter(after) }
        com.cripta.app.work.ConversionService.startConvert(appContext, file.id, after)
        _message.value = str(R.string.conversion_started)
    }

    fun convertToMp4(file: FileEntity) {
        com.cripta.app.work.ConversionService.startConvert(appContext, file.id)
        _message.value = str(R.string.conversion_started)
    }
}

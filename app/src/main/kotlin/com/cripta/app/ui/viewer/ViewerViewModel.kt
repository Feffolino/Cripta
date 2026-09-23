package com.cripta.app.ui.viewer

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
    data class Error(val message: String) : ViewerState
}

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repo: VaultRepository,
    private val thumbs: com.cripta.app.media.ThumbnailLoader,
    private val settingsStore: com.cripta.app.data.SettingsStore,
    queue: ViewerQueue,
) : ViewModel() {

    /** Snapshot of the browse order taken when the viewer opened. */
    val ids: List<String> = queue.ids

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** Bumped after favorite/tag changes so pages re-read the updated file. */
    private val _refresh = MutableStateFlow(0)
    val refresh: StateFlow<Int> = _refresh

    val allTags: StateFlow<List<TagEntity>> =
        repo.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun stateFor(id: String): ViewerState = runCatching {
        val file = repo.fileById(id) ?: error("File non trovato")
        when {
            VaultRepository.isImage(file.mimeType) -> ViewerState.Photo(file, repo.decryptBytes(file))
            VaultRepository.isPlayable(file.mimeType) -> ViewerState.Video(file)
            VaultRepository.isNote(file.mimeType) -> ViewerState.Note(file, repo.noteText(file))
            VaultRepository.isPdf(file.mimeType) -> ViewerState.Pdf(file, repo.decryptBytes(file))
            else -> ViewerState.Other(file)
        }
    }.getOrElse { ViewerState.Error(it.message ?: "Errore") }

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
        val swipeForDetails: Boolean = true,
        val holdForSpeed: Boolean = true,
        val holdSpeed: Float = 2f,
        val controlsTimeoutSec: Int = 4,
    )

    private fun com.cripta.app.data.Settings.toPlayback() = PlaybackPrefs(
        videoLoop, videoStartMuted, display.resumePlayback, seekStepSec,
        gestureControls, gestureVolume, autoRotate, pictureInPicture, viewerFilmstrip, autoNext,
        autoNextSec, swipeToClose, swipeForDetails, holdForSpeed, holdSpeedX10 / 10f, controlsTimeoutSec,
    )

    /** Player prefs as a live flow (seek step, gestures, rotation, PiP apply without reopening). */
    val playback: StateFlow<PlaybackPrefs> = settingsStore.settings.map { it.toPlayback() }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackPrefs())

    /** Orientation lock chosen in the player; kept while swiping between videos. */
    val rotationLocked = MutableStateFlow(false)

    /** Cover of any file of the browse list (filmstrip). */
    suspend fun thumbOf(id: String): android.graphics.Bitmap? = repo.fileById(id)?.let { thumbs.load(it) }

    /** Playback preferences read when a player is created. */
    suspend fun playbackPrefs(): PlaybackPrefs =
        runCatching { settingsStore.settingsOnce() }.getOrNull()
            ?.toPlayback() ?: PlaybackPrefs()

    /**
     * Remember where playback stopped. Near the end counts as finished (next time starts over).
     * Runs outside viewModelScope: the viewer may be closing, which would cancel the save.
     */
    fun savePosition(fileId: String, posMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && posMs >= durationMs - 5_000
        val value = if (finished || posMs < 3_000) null else posMs
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { repo.setPlaybackPos(fileId, value) }
        }
    }

    /** Detailed technical info for the viewer's Info dialog (video codec/size/crop/rotation). */
    suspend fun videoInfo(file: FileEntity): String = thumbs.videoDiagnostics(file)
    suspend fun tagNamesOf(id: String): List<String> = repo.tagNamesOf(id)

    fun channelFor(file: FileEntity): SeekableByteChannel = repo.seekableChannel(file)

    fun toggleFavorite(file: FileEntity) = viewModelScope.launch {
        repo.toggleFavorite(file.id, !file.isFavorite)
        _refresh.value++
    }

    fun setTags(fileId: String, names: List<String>) = viewModelScope.launch {
        repo.setTags(fileId, names); _refresh.value++
    }

    /** Store [url] as the file's source link (blank clears it). */
    fun setSourceUrl(fileId: String, url: String?) = viewModelScope.launch {
        repo.setSourceUrl(fileId, url); _refresh.value++
    }

    fun setTagColor(name: String, color: Int?) = viewModelScope.launch { repo.setTagColorByName(name, color) }
    fun setTagPinned(name: String, pinned: Boolean) = viewModelScope.launch { repo.setTagPinned(name, pinned) }

    /** Quick-tag bar: toggle one tag on the current file. */
    fun toggleTag(fileId: String, tagId: Long) = viewModelScope.launch {
        repo.toggleFileTag(fileId, tagId); _refresh.value++
    }

    val display: StateFlow<com.cripta.app.data.DisplayPrefs> =
        settingsStore.settings.map { it.display }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.cripta.app.data.DisplayPrefs())

    fun setTagAlias(name: String, alias: String?) = viewModelScope.launch { repo.setTagAlias(name, alias) }

    fun createTag(name: String, alias: String?) = viewModelScope.launch { repo.createTag(name, alias); _refresh.value++ }

    fun download(file: FileEntity) {
        // Run decryption in the foreground service so it survives backgrounding and shows progress.
        com.cripta.app.work.ConversionService.startDownload(appContext, listOf(file.id))
        _message.value = "Download avviato"
    }

    fun delete(fileId: String, onDone: () -> Unit) = viewModelScope.launch {
        // To the trash when enabled (cover kept for a restore), otherwise shredded at once.
        if (!repo.deleteOrTrash(fileId)) thumbs.evict(fileId)
        onDone()
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
    fun clearConverted() { _convertedId.value = null; _convertedOriginalId.value = null }

    /** Delete the just-converted original (from the completion prompt). */
    fun deleteConvertedOriginal() = viewModelScope.launch {
        _convertedOriginalId.value?.let { if (!repo.deleteOrTrash(it)) thumbs.evict(it) }
        clearConverted()
    }

    init {
        // Surface completions from the service (which may outlive a single viewer instance).
        viewModelScope.launch {
            repo.convertEvents.collect { event ->
                if (event.ask) {
                    _convertedId.value = event.newId
                    _convertedOriginalId.value = event.originalId
                } else {
                    _message.value = "Convertito in MP4"
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
        if (rememberChoice) viewModelScope.launch { settingsStore.setConvertAfter(after) }
        com.cripta.app.work.ConversionService.startConvert(appContext, file.id, after)
        _message.value = "Conversione avviata: prosegue in background"
    }

    fun convertToMp4(file: FileEntity) {
        com.cripta.app.work.ConversionService.startConvert(appContext, file.id)
        _message.value = "Conversione avviata: prosegue in background"
    }
}

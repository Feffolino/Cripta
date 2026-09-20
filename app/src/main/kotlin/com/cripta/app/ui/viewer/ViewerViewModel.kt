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

    fun setTagAlias(name: String, alias: String?) = viewModelScope.launch { repo.setTagAlias(name, alias) }

    fun createTag(name: String, alias: String?) = viewModelScope.launch { repo.createTag(name, alias); _refresh.value++ }

    fun download(file: FileEntity) {
        // Run decryption in the foreground service so it survives backgrounding and shows progress.
        com.cripta.app.work.ConversionService.startDownload(appContext, listOf(file.id))
        _message.value = "Download avviato"
    }

    fun delete(fileId: String, onDone: () -> Unit) = viewModelScope.launch {
        repo.secureDelete(fileId)
        thumbs.evict(fileId)
        onDone()
    }

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
        _convertedOriginalId.value?.let { repo.secureDelete(it); thumbs.evict(it) }
        clearConverted()
    }

    init {
        // Surface completions from the service (which may outlive a single viewer instance).
        viewModelScope.launch {
            repo.convertEvents.collect { event ->
                _convertedId.value = event.newId
                _convertedOriginalId.value = event.originalId
                _refresh.value++
            }
        }
    }

    /**
     * Transcode a video (e.g. a non-seekable MPEG) into MP4 and encrypt it into the vault, keeping
     * the original. Runs in the foreground service so it survives leaving the viewer/backgrounding
     * and never leaves decrypted plaintext on disk.
     */
    fun convertToMp4(file: FileEntity) {
        com.cripta.app.work.ConversionService.startConvert(appContext, file.id)
        _message.value = "Conversione avviata"
    }
}

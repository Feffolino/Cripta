package com.cripta.app.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.media.VideoConverter
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@OptIn(UnstableApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val converter: VideoConverter,
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

    fun download(file: FileEntity) = viewModelScope.launch {
        val ok = runCatching { repo.restoreToGallery(file) != null }.getOrDefault(false)
        _message.value = if (ok) "Scaricato in galleria" else "Download non riuscito"
    }

    fun delete(fileId: String, onDone: () -> Unit) = viewModelScope.launch {
        repo.secureDelete(fileId)
        onDone()
    }

    /** True while a video conversion is running (drives a progress dialog). */
    val converting = MutableStateFlow(false)

    /** Set to the new MP4's id after a successful conversion; consumed by the UI. */
    private val _convertedId = MutableStateFlow<String?>(null)
    val convertedId: StateFlow<String?> = _convertedId
    fun clearConverted() { _convertedId.value = null }

    /**
     * Transcode a video (e.g. a non-seekable MPEG) into MP4, encrypt it into the vault, and keep
     * the original. Temp plaintext files live only in app-private cache and are shredded after.
     */
    fun convertToMp4(file: FileEntity) = viewModelScope.launch {
        if (converting.value) return@launch
        converting.value = true
        val srcSuffix = file.originalName.substringAfterLast('.', "mpg")
        var src: java.io.File? = null
        var out: java.io.File? = null
        val result = runCatching {
            src = repo.decryptToTempFile(file, srcSuffix)
            out = repo.newTempFile("mp4")
            // Transformer must run on the main thread.
            withContext(Dispatchers.Main) { converter.toMp4(src!!, out!!) }
            repo.importConvertedMp4(file, out!!)
        }
        withContext(Dispatchers.IO) {
            src?.let { repo.shredTempFile(it) }
            out?.let { repo.shredTempFile(it) }
        }
        converting.value = false
        result.onSuccess { newFile ->
            _convertedId.value = newFile.id
            _refresh.value++
            _message.value = "Convertito in MP4"
        }.onFailure {
            _message.value = "Conversione non riuscita: ${it.message ?: "errore"}"
        }
    }
}

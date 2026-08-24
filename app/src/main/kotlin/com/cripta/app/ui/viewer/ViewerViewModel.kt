package com.cripta.app.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repo: VaultRepository,
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
}

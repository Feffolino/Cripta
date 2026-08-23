package com.cripta.app.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TagEntity
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
    data class Other(val file: FileEntity) : ViewerState
    data class Error(val message: String) : ViewerState
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repo: VaultRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading)
    val state: StateFlow<ViewerState> = _state

    val allTags: StateFlow<List<TagEntity>> =
        repo.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun tagNamesOf(fileId: String): List<String> = repo.tagNamesOf(fileId)

    fun setTags(fileId: String, names: List<String>) = viewModelScope.launch {
        repo.setTags(fileId, names)
        load(fileId)
    }

    fun setTagAlias(name: String, alias: String?) = viewModelScope.launch { repo.setTagAlias(name, alias) }

    fun download(file: FileEntity) = viewModelScope.launch {
        val ok = runCatching { repo.restoreToGallery(file) != null }.getOrDefault(false)
        _message.value = if (ok) "Scaricato in galleria" else "Download non riuscito"
    }

    fun load(fileId: String) = viewModelScope.launch {
        runCatching {
            val file = repo.fileById(fileId) ?: error("File non trovato")
            when {
                VaultRepository.isImage(file.mimeType) ->
                    ViewerState.Photo(file, repo.decryptBytes(file))
                VaultRepository.isPlayable(file.mimeType) -> ViewerState.Video(file)
                else -> ViewerState.Other(file)
            }
        }.onSuccess { _state.value = it }
            .onFailure { _state.value = ViewerState.Error(it.message ?: "Errore") }
    }

    fun channelFor(file: FileEntity): SeekableByteChannel = repo.seekableChannel(file)

    fun toggleFavorite(file: FileEntity) = viewModelScope.launch {
        repo.toggleFavorite(file.id, !file.isFavorite)
        load(file.id)
    }

    fun export(file: FileEntity, dest: Uri) = viewModelScope.launch {
        runCatching { repo.export(file, dest) }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    fun restoreToGallery(file: FileEntity) = viewModelScope.launch {
        val ok = runCatching { repo.restoreToGallery(file) != null }.getOrDefault(false)
        _message.value = if (ok) "Ripristinato in galleria" else "Ripristino non riuscito"
    }

    fun delete(fileId: String, onDone: () -> Unit) = viewModelScope.launch {
        repo.secureDelete(fileId)
        onDone()
    }
}

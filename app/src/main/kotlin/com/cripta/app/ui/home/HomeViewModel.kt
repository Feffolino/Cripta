package com.cripta.app.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val thumbs: ThumbnailLoader,
    private val viewerQueue: ViewerQueue,
) : ViewModel() {

    suspend fun thumb(file: FileEntity): Bitmap? = thumbs.load(file)

    val recents: StateFlow<List<FileEntity>> =
        repo.allFiles().map { list -> list.map { it.file }.take(24) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FileEntity>> =
        repo.allFiles().map { list -> list.map { it.file }.filter { it.isFavorite }.take(24) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        repo.folders(null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun publishQueue(ids: List<String>) = viewerQueue.set(ids)
}

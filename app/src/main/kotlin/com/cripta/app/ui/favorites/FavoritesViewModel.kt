package com.cripta.app.ui.favorites

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val thumbs: ThumbnailLoader,
    private val viewerQueue: ViewerQueue,
) : ViewModel() {

    suspend fun thumb(file: FileEntity): Bitmap? = thumbs.load(file)

    /** Per-file cover version; the grid re-keys on it so covers refresh after a regeneration. */
    val coverVersions: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> = thumbs.versions

    /** Null until the first read completes, so the screen can tell "loading" from "no favorites"
     *  (an initial empty list used to flash "Nessun preferito" on every open). */
    val favorites: StateFlow<List<FileEntity>?> =
        repo.changes.flatMapLatest { repo.allFiles() }
            .map<_, List<FileEntity>?> { list -> list.map { it.file }.filter { it.isFavorite } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Remove from / put back into the favorites (the grid's star button and its "Annulla"). */
    fun setFavorite(fileId: String, fav: Boolean) = viewModelScope.launch {
        runCatching { repo.toggleFavorite(fileId, fav) }
    }

    fun publishQueue(ids: List<String>) = viewerQueue.set(ids)
}

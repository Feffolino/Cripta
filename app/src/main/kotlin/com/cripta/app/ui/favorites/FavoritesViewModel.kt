package com.cripta.app.ui.favorites

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.SortKey
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
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
    private val settings: SettingsStore,
    private val navigator: com.cripta.app.ui.vault.VaultNavigator,
) : ViewModel() {

    suspend fun thumb(file: FileEntity): Bitmap? = thumbs.load(file)

    /** Per-file cover version; the grid re-keys on it so covers refresh after a regeneration. */
    val coverVersions: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> = thumbs.versions

    /** Null until the first read completes, so the screen can tell "loading" from "no favorites"
     *  (an initial empty list used to flash "Nessun preferito" on every open). */
    val favorites: StateFlow<List<FileEntity>?> =
        combine(repo.changes.flatMapLatest { repo.allFiles() }, settings.screenSort("favorites")) { list, sort ->
            val files = list.map { it.file }.filter { it.isFavorite }
            val sorted = when (sort.first) {
                SortKey.DATE -> files.sortedBy { it.importedAt }
                SortKey.NAME -> files.sortedBy { it.originalName.lowercase() }
                SortKey.SIZE -> files.sortedBy { it.sizeBytes }
                SortKey.MANUAL -> files.sortedBy { it.sortWeight }
            }
            if (sort.second || sort.first == SortKey.MANUAL) sorted else sorted.reversed()
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sort: StateFlow<Pair<SortKey, Boolean>> = settings.screenSort("favorites")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortKey.DATE to false)
    fun setSort(key: SortKey, ascending: Boolean) = viewModelScope.launch { settings.setScreenSort("favorites", key, ascending) }

    val folders: StateFlow<List<FolderEntity>?> = repo.changes.flatMapLatest { repo.allFolders() }
        .map { list -> list.filter { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val folderPreviews: StateFlow<Map<Long, List<FileEntity>>> = folders
        .mapLatest { repo.folderPreviews(it.orEmpty().map { f -> f.id }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    fun openFolder(id: Long) = navigator.openFolder(id)
    fun setFolderFavorite(id: Long, favorite: Boolean) = viewModelScope.launch { repo.setFolderFavorite(id, favorite) }

    /** Remove from / put back into the favorites (the grid's star button and its "Annulla"). */
    fun setFavorite(fileId: String, fav: Boolean) = viewModelScope.launch {
        runCatching { repo.toggleFavorite(fileId, fav) }
    }

    fun publishQueue(ids: List<String>) = viewerQueue.set(ids)
}

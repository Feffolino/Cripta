package com.cripta.app.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.FolderStat
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.computeFolderStats
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.viewer.ViewerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val thumbs: ThumbnailLoader,
    private val viewerQueue: ViewerQueue,
) : ViewModel() {

    suspend fun thumb(file: FileEntity): Bitmap? = thumbs.load(file)

    /** Per-file cover version; shelves re-key on it so covers refresh after a regeneration. */
    val coverVersions: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> = thumbs.versions

    val recents: StateFlow<List<FileEntity>> =
        repo.changes.flatMapLatest { repo.allFiles() }
            .map { list -> list.map { it.file }.take(24) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FileEntity>> =
        repo.changes.flatMapLatest { repo.allFiles() }
            .map { list -> list.map { it.file }.filter { it.isFavorite }.take(24) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        repo.changes.flatMapLatest { repo.folders(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** False until the vault content has been read once, so the empty state doesn't flash on the
     *  first frame after unlock while the DB queries are still loading. */
    val loaded: StateFlow<Boolean> =
        repo.changes.flatMapLatest { combine(repo.allFiles(), repo.folders(null)) { _, _ -> true } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val folderStats: StateFlow<Map<Long, FolderStat>> =
        repo.changes.flatMapLatest { combine(repo.allFolders(), repo.folderAggregates()) { folders, aggs ->
            computeFolderStats(folders, aggs)
        } }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun publishQueue(ids: List<String>) = viewerQueue.set(ids)
}

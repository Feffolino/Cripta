package com.cripta.app.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TypeFilter { ALL, IMAGE, VIDEO, OTHER }

data class Filters(
    val query: String = "",
    val tagIds: Set<Long> = emptySet(),
    val type: TypeFilter = TypeFilter.ALL,
    val favoritesOnly: Boolean = false,
) {
    val active: Boolean
        get() = query.isNotBlank() || tagIds.isNotEmpty() || type != TypeFilter.ALL || favoritesOnly
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _path = MutableStateFlow<List<FolderEntity>>(emptyList())
    val path: StateFlow<List<FolderEntity>> = _path

    private val currentFolderId = MutableStateFlow<Long?>(null)

    val filters = MutableStateFlow(Filters())

    val folders: StateFlow<List<FolderEntity>> =
        currentFolderId.flatMapLatest { repo.folders(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<TagEntity>> =
        repo.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<FileWithTags>> =
        combine(currentFolderId, filters) { folder, f -> folder to f }
            .flatMapLatest { (folder, f) ->
                val source = if (f.active) repo.allFiles() else repo.files(folder)
                source.combine(MutableStateFlow(f)) { list, ff -> applyFilters(list, ff) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun applyFilters(list: List<FileWithTags>, f: Filters): List<FileWithTags> {
        return list.filter { fwt ->
            val nameOk = f.query.isBlank() ||
                fwt.file.originalName.contains(f.query, ignoreCase = true) ||
                fwt.tags.any { it.name.contains(f.query, ignoreCase = true) }
            val tagsOk = f.tagIds.isEmpty() || fwt.tags.map { it.id }.containsAll(f.tagIds)
            val typeOk = when (f.type) {
                TypeFilter.ALL -> true
                TypeFilter.IMAGE -> VaultRepository.isImage(fwt.file.mimeType)
                TypeFilter.VIDEO -> VaultRepository.isVideo(fwt.file.mimeType)
                TypeFilter.OTHER -> !VaultRepository.isImage(fwt.file.mimeType) &&
                    !VaultRepository.isVideo(fwt.file.mimeType)
            }
            val favOk = !f.favoritesOnly || fwt.file.isFavorite
            nameOk && tagsOk && typeOk && favOk
        }
    }

    // --- Navigation ---
    fun enterFolder(folder: FolderEntity) {
        _path.value = _path.value + folder
        currentFolderId.value = folder.id
    }

    fun goUp() {
        if (_path.value.isNotEmpty()) {
            _path.value = _path.value.dropLast(1)
            currentFolderId.value = _path.value.lastOrNull()?.id
        }
    }

    val currentFolder: Long? get() = currentFolderId.value

    // --- Filters ---
    fun setQuery(q: String) { filters.value = filters.value.copy(query = q) }
    fun toggleTag(id: Long) {
        val cur = filters.value.tagIds
        filters.value = filters.value.copy(tagIds = if (id in cur) cur - id else cur + id)
    }
    fun setType(t: TypeFilter) { filters.value = filters.value.copy(type = t) }
    fun setFavoritesOnly(b: Boolean) { filters.value = filters.value.copy(favoritesOnly = b) }
    fun clearFilters() { filters.value = Filters() }

    // --- Actions ---
    fun createFolder(name: String) = viewModelScope.launch {
        repo.createFolder(name, currentFolderId.value)
    }

    fun deleteFolder(folder: FolderEntity) = viewModelScope.launch {
        repo.deleteFolderRecursive(folder.id)
    }

    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch {
        repo.renameFolder(folder, name)
    }

    fun importUris(uris: List<android.net.Uri>) = viewModelScope.launch {
        val folder = currentFolderId.value
        uris.forEach { runCatching { repo.import(it, folder) } }
    }

    fun toggleFavorite(fileId: String, fav: Boolean) = viewModelScope.launch {
        repo.toggleFavorite(fileId, fav)
    }

    fun setTags(fileId: String, tagNames: List<String>) = viewModelScope.launch {
        repo.setTags(fileId, tagNames)
    }

    fun moveFiles(fileIds: List<String>, folderId: Long?) = viewModelScope.launch {
        fileIds.forEach { repo.moveFile(it, folderId) }
    }

    fun deleteFiles(fileIds: List<String>) = viewModelScope.launch {
        fileIds.forEach { repo.secureDelete(it) }
    }

    fun randomPick(): String? = files.value.randomOrNull()?.file?.id
}

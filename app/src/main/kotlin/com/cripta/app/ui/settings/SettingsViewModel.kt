package com.cripta.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.Settings
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.TagEntity
import com.cripta.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val session: SessionManager,
    private val repo: VaultRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    val tags: StateFlow<List<TagEntity>> =
        repo.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setAutoLock(minutes: Int) = viewModelScope.launch { store.setAutoLockMinutes(minutes) }
    fun setDeletePolicy(p: DeleteOriginalPolicy) = viewModelScope.launch { store.setDeleteOriginalPolicy(p) }
    fun setThemeMode(m: com.cripta.app.data.ThemeMode) = viewModelScope.launch { store.setThemeMode(m) }
    fun setDynamicColor(b: Boolean) = viewModelScope.launch { store.setDynamicColor(b) }
    fun setViewMode(m: com.cripta.app.data.ViewMode) = viewModelScope.launch { store.setViewMode(m) }
    fun setGridColumns(n: Int) = viewModelScope.launch { store.setGridColumns(n) }
    fun setSort(k: com.cripta.app.data.SortKey, asc: Boolean) = viewModelScope.launch { store.setSort(k, asc) }
    fun setAllowScreenshots(b: Boolean) = viewModelScope.launch { store.setAllowScreenshots(b) }
    fun setShowFileInfo(v: Boolean) = viewModelScope.launch { store.setShowFileInfo(v) }
    fun setShowTagsOnCover(v: Boolean) = viewModelScope.launch { store.setShowTagsOnCover(v) }
    fun setShowDateHeaders(v: Boolean) = viewModelScope.launch { store.setShowDateHeaders(v) }
    fun setShowFolderInfo(v: Boolean) = viewModelScope.launch { store.setShowFolderInfo(v) }
    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    fun exportBackup(uri: android.net.Uri, passphrase: String) = viewModelScope.launch {
        _message.value = runCatching { repo.exportBackup(uri, passphrase.toCharArray()) }
            .fold({ "Backup creato ($it file)" }, { "Export fallito: ${it.message}" })
    }

    fun importBackup(uri: android.net.Uri, passphrase: String) = viewModelScope.launch {
        _message.value = runCatching { repo.importBackup(uri, passphrase.toCharArray()) }
            .fold({ "Ripristinati $it file" }, { "Import fallito (passphrase errata?)" })
    }

    fun createTag(name: String, alias: String? = null) = viewModelScope.launch { repo.createTag(name, alias) }
    fun renameTag(id: Long, name: String) = viewModelScope.launch { repo.renameTag(id, name) }
    fun deleteTag(id: Long) = viewModelScope.launch { repo.deleteTag(id) }
    fun setTagAlias(name: String, alias: String?) = viewModelScope.launch { repo.setTagAlias(name, alias) }

    /** Edit a label's name and alias together (used by the merged edit dialog). */
    fun editTag(id: Long, newName: String, alias: String?) = viewModelScope.launch {
        val n = newName.trim()
        if (n.isEmpty()) return@launch
        repo.renameTag(id, n)
        repo.setTagAlias(n, alias)
    }
    fun lockNow() = session.lock()
}

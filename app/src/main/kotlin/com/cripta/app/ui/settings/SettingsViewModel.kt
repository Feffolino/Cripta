package com.cripta.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.Settings
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.dedup.DuplicateScanner
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
    private val scanner: DuplicateScanner,
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
    fun setShowNoteFab(v: Boolean) = viewModelScope.launch { store.setShowNoteFab(v) }
    fun setShowRandomFab(v: Boolean) = viewModelScope.launch { store.setShowRandomFab(v) }
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
    fun setTagSortMode(m: com.cripta.app.data.TagSortMode) = viewModelScope.launch { store.setTagSortMode(m) }

    /** Move a tag up/down in the custom order by swapping with its neighbor. */
    fun moveTag(id: Long, up: Boolean) = viewModelScope.launch {
        val ordered = tags.value.map { it.id }.toMutableList()
        val i = ordered.indexOf(id)
        if (i < 0) return@launch
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= ordered.size) return@launch
        ordered[i] = ordered[j].also { ordered[j] = ordered[i] }
        repo.reorderTags(ordered)
    }

    fun lockNow() = session.lock()

    // --- Duplicate scan (delegates to the dedicated DuplicateScanner module) ---

    /** Which result set the UI should show, if any. */
    enum class DupMode { NONE, EXACT, SIMILAR }

    private val _dupMode = kotlinx.coroutines.flow.MutableStateFlow(DupMode.NONE)
    val dupMode: StateFlow<DupMode> = _dupMode
    private val _dupScanning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val dupScanning: StateFlow<Boolean> = _dupScanning
    private val _dupProgress = kotlinx.coroutines.flow.MutableStateFlow(0 to 0)
    val dupProgress: StateFlow<Pair<Int, Int>> = _dupProgress
    private val _exactGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DuplicateScanner.ExactGroup>>(emptyList())
    val exactGroups: StateFlow<List<DuplicateScanner.ExactGroup>> = _exactGroups
    private val _similarGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DuplicateScanner.SimilarGroup>>(emptyList())
    val similarGroups: StateFlow<List<DuplicateScanner.SimilarGroup>> = _similarGroups
    private var scanJob: kotlinx.coroutines.Job? = null

    fun scanExact() {
        if (_dupScanning.value) return
        _dupScanning.value = true
        _dupProgress.value = 0 to 0
        scanJob = viewModelScope.launch {
            val result = runCatching {
                scanner.scanExact { done, total -> _dupProgress.value = done to total }
            }
            _dupScanning.value = false
            result.onSuccess { _exactGroups.value = it; _dupMode.value = DupMode.EXACT }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) _message.value = "Scansione fallita: ${it.message}" }
        }
    }

    fun scanSimilar() {
        if (_dupScanning.value) return
        _dupScanning.value = true
        _dupProgress.value = 0 to 0
        scanJob = viewModelScope.launch {
            val result = runCatching {
                scanner.scanSimilar { done, total -> _dupProgress.value = done to total }
            }
            _dupScanning.value = false
            result.onSuccess { _similarGroups.value = it; _dupMode.value = DupMode.SIMILAR }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) _message.value = "Scansione fallita: ${it.message}" }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _dupScanning.value = false
    }

    /** Crypto-shred one file and drop it from whichever result set is shown (removing singletons). */
    fun deleteDuplicate(fileId: String) = viewModelScope.launch {
        repo.secureDelete(fileId)
        _exactGroups.value = _exactGroups.value
            .map { g -> g.copy(files = g.files.filterNot { it.id == fileId }) }
            .filter { it.files.size > 1 }
        _similarGroups.value = _similarGroups.value
            .map { g -> g.copy(files = g.files.filterNot { it.id == fileId }) }
            .filter { it.files.size > 1 }
    }

    fun closeDuplicates() {
        _dupMode.value = DupMode.NONE
        _exactGroups.value = emptyList()
        _similarGroups.value = emptyList()
    }
}

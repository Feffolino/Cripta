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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val dupStore: com.cripta.app.data.dedup.DupScanStore,
    private val nav: SettingsNav,
    private val store: SettingsStore,
    private val session: SessionManager,
    private val repo: VaultRepository,
    private val scanner: DuplicateScanner,
    private val updater: com.cripta.app.update.AppUpdater,
    private val thumbs: com.cripta.app.media.ThumbnailLoader,
) : ViewModel() {

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val release: com.cripta.app.update.AppUpdater.Release) : UpdateState
        data class Downloading(val pct: Int) : UpdateState
        data class Error(val message: String) : UpdateState
    }

    private val _update = kotlinx.coroutines.flow.MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    fun checkUpdate(ctx: android.content.Context) = viewModelScope.launch {
        _update.value = UpdateState.Checking
        val pre = store.settingsOnce().updatePrerelease
        val rel = updater.latest(includePrerelease = pre)
        _update.value = when {
            rel == null -> UpdateState.Error(if (pre) "Nessuna release trovata" else "Nessuna release stabile trovata (attiva le pre-release)")
            rel.buildNumber <= updater.currentBuild(ctx) -> UpdateState.UpToDate
            else -> UpdateState.Available(rel)
        }
    }

    fun downloadUpdate(ctx: android.content.Context) {
        val rel = (_update.value as? UpdateState.Available)?.release ?: return
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(0)
            runCatching {
                val apk = updater.download(ctx, rel.apkUrl) { pct -> _update.value = UpdateState.Downloading(pct) }
                updater.install(ctx, apk)
            }.onFailure { _update.value = UpdateState.Error(it.message ?: "Download fallito") }
        }
    }

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
    fun setCoverTagRows(v: Int) = viewModelScope.launch { store.setCoverTagRows(v) }
    fun setCoverTagStyle(v: com.cripta.app.data.CoverTagStyle) = viewModelScope.launch { store.setCoverTagStyle(v) }
    fun setTagColors(v: Boolean) = viewModelScope.launch { store.setTagColors(v) }
    fun setShowDurationBadge(v: Boolean) = viewModelScope.launch { store.setShowDurationBadge(v) }
    fun setShowQualityBadge(v: Boolean) = viewModelScope.launch { store.setShowQualityBadge(v) }
    fun setShowStatsStrip(v: Boolean) = viewModelScope.launch { store.setShowStatsStrip(v) }
    /** Page requested from another screen (Home "Strumenti", scan notification). */
    val requestedPage: StateFlow<String?> = nav.page
    fun consumeRequestedPage() = nav.consume()

    /** Reset one page's preferences to their defaults (files and tags are never touched). */
    fun resetPage(page: String) = viewModelScope.launch { store.resetPage(page) }

    fun setConvertAfter(v: com.cripta.app.data.ConvertAfter) = viewModelScope.launch { store.setConvertAfter(v) }
    fun setResumePlayback(v: Boolean) = viewModelScope.launch { store.setResumePlayback(v) }
    fun setTrashEnabled(v: Boolean) = viewModelScope.launch { store.setTrashEnabled(v) }
    fun setTrashDays(v: Int) = viewModelScope.launch { store.setTrashDays(v) }

    /** Files in the trash, newest first. */
    val trashed: StateFlow<List<com.cripta.app.data.db.FileWithTags>> =
        repo.changes.flatMapLatest { repo.trashed() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun restore(id: String) = viewModelScope.launch { repo.restore(id) }
    fun deleteForever(id: String) = viewModelScope.launch { repo.secureDelete(id); thumbs.evict(id) }
    fun emptyTrash() = viewModelScope.launch {
        trashed.value.map { it.file.id }.forEach { repo.secureDelete(it); thumbs.evict(it) }
    }
    fun setVideoLoop(v: Boolean) = viewModelScope.launch { store.setVideoLoop(v) }
    fun setSeekStep(v: Int) = viewModelScope.launch { store.setSeekStep(v) }
    fun setGestureControls(v: Boolean) = viewModelScope.launch { store.setGestureControls(v) }
    fun setAutoRotate(v: Boolean) = viewModelScope.launch { store.setAutoRotate(v) }
    fun setPictureInPicture(v: Boolean) = viewModelScope.launch { store.setPictureInPicture(v) }
    fun setGestureVolume(v: Boolean) = viewModelScope.launch { store.setGestureVolume(v) }
    fun setAutoNext(v: Boolean) = viewModelScope.launch { store.setAutoNext(v) }
    fun setViewerFilmstrip(v: Boolean) = viewModelScope.launch { store.setViewerFilmstrip(v) }
    fun setUpdatePrerelease(v: Boolean) = viewModelScope.launch { store.setUpdatePrerelease(v); _update.value = UpdateState.Idle }
    fun setVideoStartMuted(v: Boolean) = viewModelScope.launch { store.setVideoStartMuted(v) }
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

    fun createTag(name: String, alias: String? = null, color: Int? = null) = viewModelScope.launch {
        repo.createTag(name, alias)
        if (color != null) repo.setTagColorByName(name, color)
    }
    fun setTagColor(id: Long, color: Int?) = viewModelScope.launch { repo.setTagColor(id, color) }
    fun setTagPinned(name: String, pinned: Boolean) = viewModelScope.launch { repo.setTagPinned(name, pinned) }
    fun setShowRecentTags(v: Boolean) = viewModelScope.launch { store.setShowRecentTags(v) }
    fun setViewerQuickTags(v: Boolean) = viewModelScope.launch { store.setViewerQuickTags(v) }
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
    /** The scan runs in the foreground service (continues in background); progress comes from the store. */
    val dupScanning: StateFlow<Boolean> =
        dupStore.state.map { it.running != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val dupProgress: StateFlow<Pair<Int, Int>> =
        dupStore.state.map { it.done to it.total }.stateIn(viewModelScope, SharingStarted.Eagerly, 0 to 0)
    /** A finished scan the user hasn't opened yet (e.g. it completed in the background). */
    val dupResultWaiting: StateFlow<com.cripta.app.data.dedup.DupScanStore.Result?> =
        dupStore.state.map { it.result?.takeIf { r -> !r.seen } }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** Set when this screen started the scan: open the results as soon as it finishes. */
    private var openWhenDone = false
    private val _exactGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DuplicateScanner.ExactGroup>>(emptyList())
    val exactGroups: StateFlow<List<DuplicateScanner.ExactGroup>> = _exactGroups
    private val _similarGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DuplicateScanner.SimilarGroup>>(emptyList())
    val similarGroups: StateFlow<List<DuplicateScanner.SimilarGroup>> = _similarGroups
    /** How many files/images the last scan actually examined — shown so the user sees it ran. */
    private val _dupScannedCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val dupScannedCount: StateFlow<Int> = _dupScannedCount

    /** One file of a duplicate group, with what's needed to compare it against the others. */
    data class DupCandidate(
        val file: com.cripta.app.data.db.FileEntity,
        val tags: List<String>,
        /** Display width x height, when readable. */
        val resolution: Pair<Int, Int>?,
    ) {
        val pixels: Long get() = resolution?.let { it.first.toLong() * it.second } ?: 0L
    }

    /** A duplicate group ready for side-by-side comparison, with the suggested copy to keep. */
    data class DupGroup(
        val candidates: List<DupCandidate>,
        val bestId: String,
        /** Short human reasons why [bestId] is suggested. */
        val reasons: List<String>,
        /** True for byte-identical copies (quality is equal, only metadata differs). */
        val exact: Boolean,
    ) {
        val best: DupCandidate get() = candidates.first { it.file.id == bestId }
        /** Tags present on other copies but missing on the suggested one (moved when resolving). */
        val tagsToMove: List<String> get() =
            candidates.filter { it.file.id != bestId }.flatMap { it.tags }.distinct().filterNot { it in best.tags }
    }

    private val _dupGroups = kotlinx.coroutines.flow.MutableStateFlow<List<DupGroup>>(emptyList())
    val dupGroups: StateFlow<List<DupGroup>> = _dupGroups
    /** Label of the last resolution, e.g. "Tenuto X · spostate 2 etichette". */
    private val _dupNotice = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val dupNotice: StateFlow<String?> = _dupNotice
    fun clearDupNotice() { _dupNotice.value = null }

    suspend fun thumb(file: com.cripta.app.data.db.FileEntity): android.graphics.Bitmap? = thumbs.load(file)
    /** Seekable decrypting channel for in-place video preview (nothing written to disk). */
    fun channelFor(file: com.cripta.app.data.db.FileEntity): java.nio.channels.SeekableByteChannel = repo.seekableChannel(file)
    /** Decrypted image bytes for the full-size preview, held in memory only. */
    suspend fun imageBytes(file: com.cripta.app.data.db.FileEntity): ByteArray = repo.decryptBytes(file)

    /**
     * Order candidates best-first: highest resolution, then (for videos) seekable MP4, then larger
     * file (higher bitrate), then the one already carrying the organisation (favorite, tags, link),
     * then the oldest import.
     */
    private fun rank(c: List<DupCandidate>): List<DupCandidate> = c.sortedWith(
        compareByDescending<DupCandidate> { it.pixels }
            .thenByDescending { if (com.cripta.app.data.VaultRepository.isVideo(it.file.mimeType)) it.file.mimeType == "video/mp4" else false }
            .thenByDescending { it.file.sizeBytes }
            .thenByDescending { it.file.isFavorite }
            .thenByDescending { it.tags.size }
            .thenByDescending { !it.file.sourceUrl.isNullOrBlank() }
            .thenBy { it.file.importedAt }
    )

    private fun reasonsFor(best: DupCandidate, others: List<DupCandidate>, exact: Boolean): List<String> {
        val r = mutableListOf<String>()
        if (!exact) {
            if (best.pixels > 0 && others.all { it.pixels < best.pixels }) {
                r += "risoluzione più alta (${best.resolution!!.first}×${best.resolution.second})"
            }
            if (best.file.mimeType == "video/mp4" && others.any { com.cripta.app.data.VaultRepository.isVideo(it.file.mimeType) && it.file.mimeType != "video/mp4" }) {
                r += "MP4 scorribile"
            }
            if (r.isEmpty() && others.all { it.file.sizeBytes < best.file.sizeBytes }) r += "qualità/bitrate maggiore"
        } else {
            r += "copie identiche"
        }
        if (best.file.isFavorite && others.none { it.file.isFavorite }) r += "è tra i preferiti"
        if (best.tags.isNotEmpty() && others.all { it.tags.size < best.tags.size }) r += "ha più etichette"
        if (!best.file.sourceUrl.isNullOrBlank() && others.all { it.file.sourceUrl.isNullOrBlank() }) r += "ha il link di origine"
        if (others.all { it.file.importedAt > best.file.importedAt }) r += "importato per primo"
        return r
    }

    private suspend fun buildGroups(sets: List<List<com.cripta.app.data.db.FileEntity>>, exact: Boolean): List<DupGroup> {
        val ids = sets.flatten().map { it.id }
        val tags = repo.tagNamesOf(ids)
        return sets.mapNotNull { files ->
            if (files.size < 2) return@mapNotNull null
            // Identical bytes have identical resolution: skip the probe for exact groups.
            val cands = files.map { f -> DupCandidate(f, tags[f.id].orEmpty(), if (exact) null else thumbs.mediaResolution(f)) }
            val ranked = rank(cands)
            val best = ranked.first()
            DupGroup(ranked, best.file.id, reasonsFor(best, ranked.drop(1), exact), exact)
        }
    }

    fun scanExact() {
        if (dupScanning.value) return
        openWhenDone = true
        com.cripta.app.work.ConversionService.startDupScan(appContext, similar = false)
    }

    fun scanSimilar() {
        if (dupScanning.value) return
        openWhenDone = true
        com.cripta.app.work.ConversionService.startDupScan(appContext, similar = true)
    }

    fun cancelScan() {
        openWhenDone = false
        com.cripta.app.work.ConversionService.cancelDupScan(appContext)
    }

    /** Open the stored scan result in the comparison screen. */
    fun openScanResult() = viewModelScope.launch {
        val r = dupStore.state.value.result ?: return@launch
        val exact = r.mode == com.cripta.app.data.dedup.DupScanStore.Mode.EXACT
        _exactGroups.value = if (exact) r.groups.map { DuplicateScanner.ExactGroup(it.first().sizeBytes, it) } else emptyList()
        _similarGroups.value = if (!exact) r.groups.map { DuplicateScanner.SimilarGroup(it, 0) } else emptyList()
        _dupGroups.value = buildGroups(r.groups, exact)
        _dupScannedCount.value = r.scanned
        _dupMode.value = if (exact) DupMode.EXACT else DupMode.SIMILAR
        dupStore.markSeen()
    }

    init {
        // Results produced by the background scan: open them if this screen asked for the scan,
        // or when the user tapped the "scan finished" notification.
        viewModelScope.launch {
            dupStore.state.collect { st ->
                val r = st.result
                if (r != null && !r.seen && openWhenDone && st.running == null) { openWhenDone = false; openScanResult() }
                if (st.error != null && openWhenDone) { openWhenDone = false; _message.value = "Scansione fallita: ${st.error}" }
            }
        }
        viewModelScope.launch {
            dupStore.openRequested.collect { req ->
                if (req) {
                    dupStore.consumeOpen()
                    nav.open(SettingsPage.STRUMENTI.name)
                    if (dupStore.state.value.result != null) openScanResult()
                }
            }
        }
    }

    /**
     * Keep [keepId] and delete every other copy of its group, moving their tags (plus favorite and
     * source link) onto the kept file so no organisation is lost.
     */
    fun keepOnly(keepId: String) = viewModelScope.launch {
        val group = _dupGroups.value.firstOrNull { g -> g.candidates.any { it.file.id == keepId } } ?: return@launch
        val remove = group.candidates.map { it.file.id }.filter { it != keepId }
        val moved = repo.mergeDuplicates(keepId, remove)
        // Copies sent to the trash keep their cover (restoring them must not lose a chosen cover).
        if (!trashOn()) remove.forEach { thumbs.evict(it) }
        dropFromResults(remove.toSet())
        val name = group.candidates.first { it.file.id == keepId }.file.originalName
        _dupNotice.value = buildString {
            append("Tenuto \"$name\", eliminate ${remove.size} copie")
            if (moved.isNotEmpty()) append(" · spostate ${moved.size} etichette")
        }
    }

    /**
     * Delete one copy. Its tags are not lost: they move to the copy that remains suggested in the
     * group (so deleting a tagged copy and keeping an untagged one keeps the tags).
     */
    fun deleteDuplicate(fileId: String) = viewModelScope.launch {
        val group = _dupGroups.value.firstOrNull { g -> g.candidates.any { it.file.id == fileId } }
        val heir = group?.let { g ->
            if (g.bestId != fileId) g.bestId
            else rank(g.candidates.filter { it.file.id != fileId }).firstOrNull()?.file?.id
        }
        val moved = if (heir != null) repo.mergeDuplicates(heir, listOf(fileId)) else { repo.deleteOrTrash(fileId); emptyList() }
        if (!trashOn()) thumbs.evict(fileId)
        dropFromResults(setOf(fileId))
        if (moved.isNotEmpty()) _dupNotice.value = "Etichette spostate: ${moved.joinToString(", ") { "#$it" }}"
    }

    private suspend fun trashOn(): Boolean = runCatching { store.settingsOnce().trashEnabled }.getOrDefault(false)

    /** Remove deleted files from every result set, dropping groups left with a single file. */
    private suspend fun dropFromResults(ids: Set<String>) {
        dupStore.dropFiles(ids)
        _exactGroups.value = _exactGroups.value
            .map { g -> g.copy(files = g.files.filterNot { it.id in ids }) }
            .filter { it.files.size > 1 }
        _similarGroups.value = _similarGroups.value
            .map { g -> g.copy(files = g.files.filterNot { it.id in ids }) }
            .filter { it.files.size > 1 }
        // Re-read the survivors (tags/favorite may have changed by the merge) and re-rank.
        val remaining = _dupGroups.value.map { g -> g.candidates.map { it.file }.filterNot { it.id in ids } }
            .filter { it.size > 1 }
            .map { files -> files.mapNotNull { repo.fileById(it.id) } }
        val old = _dupGroups.value.flatMap { it.candidates }.associateBy { it.file.id }
        val tags = repo.tagNamesOf(remaining.flatten().map { it.id })
        _dupGroups.value = remaining.filter { it.size > 1 }.map { files ->
            val exact = _dupGroups.value.firstOrNull { g -> g.candidates.any { it.file.id == files.first().id } }?.exact ?: false
            val cands = files.map { f -> DupCandidate(f, tags[f.id].orEmpty(), old[f.id]?.resolution) }
            val ranked = rank(cands)
            DupGroup(ranked, ranked.first().file.id, reasonsFor(ranked.first(), ranked.drop(1), exact), exact)
        }
    }

    fun closeDuplicates() {
        dupStore.markSeen()
        _dupMode.value = DupMode.NONE
        _dupGroups.value = emptyList()
        _dupNotice.value = null
        _exactGroups.value = emptyList()
        _similarGroups.value = emptyList()
    }
}

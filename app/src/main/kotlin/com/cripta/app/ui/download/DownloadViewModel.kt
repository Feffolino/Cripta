package com.cripta.app.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.DownloadDefaults
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.SharedLinkStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.media.YtdlpDownloader
import com.cripta.app.work.ConversionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val ytdlp: YtdlpDownloader,
    private val sharedLinks: SharedLinkStore,
    private val settings: SettingsStore,
    private val viewerQueue: com.cripta.app.viewer.ViewerQueue,
) : ViewModel() {

    /** The download queue: running, waiting and finished links. */
    val downloads: StateFlow<List<VaultRepository.DownloadJob>> = repo.downloads

    /** A link shared into the app, to pre-fill the field (null once consumed). */
    val pendingSharedLink: StateFlow<String?> = sharedLinks.pending
    fun consumeSharedLink() = sharedLinks.consume()

    val folders: StateFlow<List<FolderEntity>> =
        repo.allFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags: StateFlow<List<TagEntity>> =
        repo.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Last destination used (folder, tags, quality), so it doesn't have to be picked every time. */
    val defaults: StateFlow<DownloadDefaults?> =
        settings.settings.map<com.cripta.app.data.Settings, DownloadDefaults?> { it.downloadDefaults }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Estimated output size per quality bucket (px height, null = Auto). */
    sealed interface Estimate {
        data object Idle : Estimate
        data object Loading : Estimate
        data class Ready(val bytesByHeight: Map<Int?, Long?>) : Estimate
        data class Error(val message: String) : Estimate
    }

    private val _estimate = MutableStateFlow<Estimate>(Estimate.Idle)
    val estimate: StateFlow<Estimate> = _estimate

    /** A live vault file already downloaded from the link in the field, if any. */
    private val _existing = MutableStateFlow<FileEntity?>(null)
    val existing: StateFlow<FileEntity?> = _existing

    private var estimateJob: Job? = null
    private var estimatedUrl: String? = null

    /** Probe [url] and compute the size estimate for each of [heights]. No-op if already done for it. */
    fun estimate(url: String, heights: List<Int?>) {
        val u = url.trim()
        if (u.isBlank()) { resetEstimate(); return }
        viewModelScope.launch { _existing.value = repo.fileBySourceUrl(u) }
        if (u == estimatedUrl && _estimate.value is Estimate.Ready) return
        estimatedUrl = u
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            _estimate.value = Estimate.Loading
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val info = ytdlp.info(u)
                    heights.associateWith { h -> ytdlp.estimateBytes(info, h) }
                }
            }
            result.onSuccess { _estimate.value = Estimate.Ready(it) }
                .onFailure { _estimate.value = Estimate.Error(it.message ?: "Impossibile stimare") }
        }
    }

    fun resetEstimate() {
        estimateJob?.cancel(); estimatedUrl = null; _estimate.value = Estimate.Idle; _existing.value = null
    }

    /** True when [url] is already waiting or running in the queue. */
    fun isQueued(url: String): Boolean =
        downloads.value.any { it.url == url.trim() && !it.finished }

    /** Queue [url] with its destination, and remember that destination for next time. */
    fun enqueue(ctx: Context, url: String, height: Int?, folderId: Long?, tagIds: List<Long>) {
        ConversionService.startDownloadUrl(ctx, url.trim(), height, folderId, tagIds)
        viewModelScope.launch { settings.setDownloadDefaults(DownloadDefaults(folderId, tagIds, height)) }
    }

    /** Cancel the link being downloaded now (the rest of the queue continues). */
    fun cancel(ctx: Context) = ConversionService.cancelDownload(ctx)
    fun remove(id: String) = repo.removeDownload(id)
    fun clearFinished() = repo.clearFinishedDownloads()

    /** Prepare the viewer to open a single file (e.g. a finished or already-present download). */
    fun prepareOpen(fileId: String) = viewerQueue.set(listOf(fileId))
}

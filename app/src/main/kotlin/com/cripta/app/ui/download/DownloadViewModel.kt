package com.cripta.app.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.SharedLinkStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.media.YtdlpDownloader
import com.cripta.app.work.ConversionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val ytdlp: YtdlpDownloader,
    private val sharedLinks: SharedLinkStore,
) : ViewModel() {

    /** Live progress/result of the in-app URL downloader. */
    val state: StateFlow<VaultRepository.DownloadState> = repo.downloadState

    /** A link shared into the app, to pre-fill the field (null once consumed). */
    val pendingSharedLink: StateFlow<String?> = sharedLinks.pending
    fun consumeSharedLink() = sharedLinks.consume()

    /** Estimated output size per quality bucket (px height, null = Auto). */
    sealed interface Estimate {
        data object Idle : Estimate
        data object Loading : Estimate
        data class Ready(val bytesByHeight: Map<Int?, Long?>) : Estimate
        data class Error(val message: String) : Estimate
    }

    private val _estimate = MutableStateFlow<Estimate>(Estimate.Idle)
    val estimate: StateFlow<Estimate> = _estimate

    private var estimateJob: Job? = null
    private var estimatedUrl: String? = null

    /** Probe [url] and compute the size estimate for each of [heights]. No-op if already done for it. */
    fun estimate(url: String, heights: List<Int?>) {
        val u = url.trim()
        if (u.isBlank()) { resetEstimate(); return }
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
        estimateJob?.cancel(); estimatedUrl = null; _estimate.value = Estimate.Idle
    }

    fun cancel(ctx: Context) = ConversionService.cancelConvert(ctx)

    /** Clear a terminal (done/failed/cancelled) state so the UI returns to the idle form. */
    fun dismissResult() {
        if (!state.value.active) repo.setDownloadState(VaultRepository.DownloadState())
    }
}

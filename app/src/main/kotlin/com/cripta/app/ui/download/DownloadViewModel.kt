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
        /** Sizes per quality, plus what the link is (for a preview card before downloading). */
        data class Ready(
            val bytesByHeight: Map<Int?, Long?>,
            val title: String? = null,
            val thumbnailUrl: String? = null,
            val durationSec: Int = 0,
        ) : Estimate
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
                    Estimate.Ready(
                        heights.associateWith { h -> ytdlp.estimateBytes(info, h) },
                        title = info.title?.takeIf { it.isNotBlank() },
                        thumbnailUrl = info.thumbnail?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                        durationSec = info.duration,
                    )
                }
            }
            result.onSuccess { _estimate.value = it }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    android.util.Log.w("DownloadViewModel", "estimate failed: $u", it)
                    _estimate.value = Estimate.Error(friendlyDownloadError(it.message))
                }
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

    /** Queue a failed/cancelled link again with the same quality and destination. */
    fun retry(ctx: Context, job: VaultRepository.DownloadJob) {
        repo.removeDownload(job.id)
        ConversionService.startDownloadUrl(ctx, job.url, job.maxHeight, job.folderId, job.tagIds)
    }
    fun clearFinished() = repo.clearFinishedDownloads()

    /** Prepare the viewer to open a single file (e.g. a finished or already-present download). */
    fun prepareOpen(fileId: String) = viewerQueue.set(listOf(fileId))
}

/** The first http(s) link inside shared/pasted text (apps often share "Title https://..."), else the text. */
fun extractUrl(text: String): String =
    Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'') ?: text.trim()

/** True for something that looks like a web link (what yt-dlp can fetch). */
fun looksLikeUrl(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return false
    val uri = runCatching { java.net.URI(t) }.getOrNull() ?: return false
    return (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
}

/**
 * Plain Italian message for a download/estimate failure. yt-dlp and the network stack report in
 * English (often with stack-like detail), which must not reach the user as is.
 */
fun friendlyDownloadError(raw: String?): String {
    val m = raw.orEmpty()
    val l = m.lowercase()
    return when {
        m.isBlank() -> "Download non riuscito. Controlla il link e riprova."
        // Messages already written for the user (in Italian) by the app itself.
        m.startsWith("Nessun") || m.startsWith("Impossibile") || m.startsWith("Link") -> m
        "unsupported url" in l || "no video formats" in l || "not a valid url" in l ->
            "Questo link non contiene un video scaricabile."
        "drm" in l -> "Il video è protetto da DRM e non si può scaricare."
        "private video" in l || "sign in" in l || "login" in l || "members-only" in l || "age" in l && "confirm" in l ->
            "Il video è privato o richiede l'accesso a un account."
        "not available in your country" in l || "geo" in l && "restrict" in l ->
            "Il video non è disponibile nel tuo Paese."
        "video unavailable" in l || "has been removed" in l || "404" in l || "not found" in l ->
            "Il video non è disponibile o è stato rimosso."
        "403" in l || "forbidden" in l -> "Il sito ha rifiutato il download. Riprova più tardi."
        "429" in l || "too many requests" in l -> "Troppe richieste al sito. Riprova tra qualche minuto."
        "unable to resolve host" in l || "unknownhost" in l || "failed to connect" in l || "network is unreachable" in l ||
            "connection" in l && ("refused" in l || "reset" in l) -> "Nessuna connessione a Internet. Controlla la rete e riprova."
        "timed out" in l || "timeout" in l -> "Il sito non risponde. Riprova più tardi."
        "no space" in l || "enospc" in l -> "Spazio di archiviazione esaurito."
        else -> "Download non riuscito. Controlla il link e riprova."
    }
}

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
import kotlinx.coroutines.runInterruptible
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val queue: com.cripta.app.data.DownloadQueue,
    private val ytdlp: YtdlpDownloader,
    private val sharedLinks: SharedLinkStore,
    private val settings: SettingsStore,
    private val viewerQueue: com.cripta.app.viewer.ViewerQueue,
) : ViewModel() {

    /** The download queue: running, waiting and finished links. */
    val downloads: StateFlow<List<com.cripta.app.data.DownloadJob>> = queue.downloads

    /** A link shared into the app, to pre-fill the field (null once consumed). */
    val pendingSharedLink: StateFlow<String?> = sharedLinks.pending
    fun consumeSharedLink() = sharedLinks.consume()

    val folders: StateFlow<List<FolderEntity>> =
        repo.allFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** A new folder made from the destination picker; its id, to choose it right away. */
    suspend fun createFolder(name: String, parentId: Long?): Long = repo.createFolder(name, parentId)
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
    private var existingJob: Job? = null
    private var estimatedUrl: String? = null

    /** Probe [url] and compute the size estimate for each of [heights]. No-op if already done for it. */
    fun estimate(url: String, heights: List<Int?>) {
        val u = url.trim()
        if (u.isBlank()) { resetEstimate(); return }
        // Tracked, so a lookup still running when the field is cleared or submitted can't bring
        // "Già nel vault" back for an empty field.
        existingJob?.cancel()
        existingJob = viewModelScope.launch { _existing.value = repo.fileBySourceUrl(u) }
        if (u == estimatedUrl && _estimate.value is Estimate.Ready) return
        estimatedUrl = u
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            _estimate.value = Estimate.Loading
            val result = runInterruptible(Dispatchers.IO) {
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
                    android.util.Log.w("DownloadViewModel", "Estimate failed (${it.javaClass.simpleName})")
                    _estimate.value = Estimate.Error(friendlyDownloadError(it.message))
                }
        }
    }

    /**
     * The field now holds [u]: what was shown for another link (the "already in the vault" notice
     * and its Open button, the size and preview) goes at once, not when the new check ends.
     */
    fun onLinkEdited(u: String) {
        if (_existing.value?.sourceUrl?.trim() != u) { existingJob?.cancel(); _existing.value = null }
        if (u != estimatedUrl && _estimate.value !is Estimate.Idle) { estimateJob?.cancel(); estimatedUrl = null; _estimate.value = Estimate.Idle }
    }

    fun resetEstimate() {
        estimateJob?.cancel(); existingJob?.cancel(); estimatedUrl = null; _estimate.value = Estimate.Idle; _existing.value = null
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
    fun remove(id: String) = queue.removeDownload(id)

    /** Queue a failed/cancelled link again with the same quality and destination. */
    fun retry(ctx: Context, job: com.cripta.app.data.DownloadJob) {
        queue.removeDownload(job.id)
        ConversionService.startDownloadUrl(ctx, job.url, job.maxHeight, job.folderId, job.tagIds)
    }
    fun clearFinished() = queue.clearFinishedDownloads()

    /** Prepare the viewer to open a single file (e.g. a finished or already-present download). */
    fun prepareOpen(fileId: String) = viewerQueue.set(listOf(fileId))
}

/**
 * The first http(s) link inside shared/pasted text (apps often share "Title https://..."), else the
 * text. Punctuation after the link goes; a closing bracket only when nothing in the link opened it,
 * so ".../video_(final)" keeps its ")". An apostrophe inside the path stays.
 */
fun extractUrl(text: String): String {
    var url = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE).find(text)?.value ?: return text.trim()
    while (url.isNotEmpty()) {
        val last = url.last()
        url = when {
            last in ".,;:!?\"'" -> url.dropLast(1)
            last == ')' && url.count { it == ')' } > url.count { it == '(' } -> url.dropLast(1)
            last == ']' && url.count { it == ']' } > url.count { it == '[' } -> url.dropLast(1)
            else -> break
        }
    }
    return url
}

/**
 * True for something that looks like a web link (what yt-dlp can fetch). A domain in its Unicode
 * form ("https://例子.中国/…") counts as its ASCII (IDN) form does: java.net.URI gives it no host.
 */
fun looksLikeUrl(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return false
    fun ok(s: String): Boolean {
        val uri = runCatching { java.net.URI(s) }.getOrNull() ?: return false
        return (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
    }
    if (ok(t)) return true
    return asciiHost(t)?.let { it != t && ok(it) } == true
}

/** [url] with its host in the ASCII (IDN) form, or null when it has none to convert. */
internal fun asciiHost(url: String): String? {
    val m = Regex("^(https?://)([^/?#]*)(.*)$", RegexOption.IGNORE_CASE).find(url) ?: return null
    val (scheme, authority, rest) = m.destructured
    val at = authority.lastIndexOf('@')
    val user = if (at >= 0) authority.substring(0, at + 1) else ""
    val hostPort = authority.substring(at + 1)
    if (hostPort.startsWith("[")) return null   // an IPv6 literal: nothing to convert
    val colon = hostPort.lastIndexOf(':')
    val host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
    val port = if (colon >= 0) hostPort.substring(colon) else ""
    if (host.isEmpty() || host.all { it.code < 128 }) return null
    val ascii = runCatching { java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED) }.getOrNull() ?: return null
    return scheme + user + ascii + port + rest
}

/**
 * Plain Italian message for a download/estimate failure. yt-dlp and the network stack report in
 * English (often with stack-like detail), which must not reach the user as is.
 */
fun friendlyDownloadError(raw: String?): String =
    com.cripta.app.work.UserErrors.ofDownload(IllegalStateException(raw.orEmpty()))

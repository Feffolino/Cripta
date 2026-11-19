package com.cripta.app.ui.download

import com.cripta.app.ui.safeScope
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    /**
     * A new folder made from the destination picker; its id, to choose it right away. Null when it
     * could not be made (the vault locked as it was tapped, a full phone): the picker runs this on
     * the screen's own scope, where an error closed the app.
     */
    suspend fun createFolder(name: String, parentId: Long?): Long? = try {
        repo.createFolder(name, parentId)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        com.cripta.app.logException(android.util.Log.ERROR, "DownloadViewModel", "folder not created", e)
        null
    }
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

    /**
     * The stream chosen in the page of a link (the link, then the stream): analysed and downloaded
     * in the page's place, until the field holds another link.
     */
    private var stream: Pair<String, com.cripta.app.data.StreamSource>? = null
    private fun streamOf(u: String) = stream?.takeIf { it.first == u }?.second

    /** [s] was picked in the page [page] with the in-app browser: analyse it in the page's place. */
    fun useStream(page: String, s: com.cripta.app.data.StreamSource, heights: List<Int?>) {
        val u = page.trim()
        stream = u to s
        estimateJob?.cancel(); estimatedUrl = null; _estimate.value = Estimate.Idle
        estimate(u, heights)
    }

    /** One stream is read at a time: each reading starts a yt-dlp process, heavy for a phone. */
    private val reading = Semaphore(1)

    /**
     * What [s], a stream the page search found, is: its tallest picture, its length and the size of
     * its best quality (what "Auto" would download), for its row.
     * Null when it cannot be read; the row stays tappable all the same.
     */
    internal suspend fun briefOf(s: com.cripta.app.data.StreamSource): StreamBrief? = reading.withPermit {
        runInterruptible(Dispatchers.IO) {
            runCatching {
                val info = ytdlp.info(s.url, s.headers)
                StreamBrief(info.formats?.maxOfOrNull { it.height } ?: 0, info.duration, ytdlp.estimateBytes(info, null))
            }.getOrNull()
        }
    }

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
        existingJob = safeScope.launch { _existing.value = repo.fileBySourceUrl(u) }
        if (u == estimatedUrl && _estimate.value is Estimate.Ready) return
        estimatedUrl = u
        val src = streamOf(u)
        estimateJob?.cancel()
        estimateJob = safeScope.launch {
            _estimate.value = Estimate.Loading
            val result = runInterruptible(Dispatchers.IO) {
                runCatching {
                    val info = ytdlp.info(src?.url ?: u, src?.headers.orEmpty())
                    Estimate.Ready(
                        heights.associateWith { h -> ytdlp.estimateBytes(info, h) },
                        // A stream is called "master" or "index": its page's title says what it is.
                        title = src?.title ?: info.title?.takeIf { it.isNotBlank() },
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
        if (stream?.first != u) stream = null
        if (_existing.value?.sourceUrl?.trim() != u) { existingJob?.cancel(); _existing.value = null }
        if (u != estimatedUrl && _estimate.value !is Estimate.Idle) { estimateJob?.cancel(); estimatedUrl = null; _estimate.value = Estimate.Idle }
    }

    fun resetEstimate() {
        estimateJob?.cancel(); existingJob?.cancel(); estimatedUrl = null; _estimate.value = Estimate.Idle; _existing.value = null
        stream = null
    }

    /** True when [url] is already waiting or running in the queue. */
    fun isQueued(url: String): Boolean =
        downloads.value.any { it.url == url.trim() && !it.finished }

    /** Queue [url] with its destination, and remember that destination for next time. */
    fun enqueue(ctx: Context, url: String, height: Int?, folderId: Long?, tagIds: List<Long>) {
        ConversionService.startDownloadUrl(ctx, url.trim(), height, folderId, tagIds, stream = streamOf(url.trim()))
        safeScope.launch { settings.setDownloadDefaults(DownloadDefaults(folderId, tagIds, height)) }
    }

    /** Cancel the link being downloaded now (the rest of the queue continues). */
    fun cancel(ctx: Context) = ConversionService.cancelDownload(ctx)
    fun remove(id: String) = queue.removeDownload(id)

    /** Queue a failed/cancelled link again with the same quality and destination. */
    fun retry(ctx: Context, job: com.cripta.app.data.DownloadJob) {
        // Refused to start: the failed link stays in the list, to try again.
        if (ConversionService.startDownloadUrl(ctx, job.url, job.maxHeight, job.folderId, job.tagIds, stream = job.stream)) queue.removeDownload(job.id)
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
fun extractUrl(text: String): String = LINK_IN_TEXT.find(text)?.value?.let(::trimLinkEnd) ?: text.trim()

/**
 * Every http(s) link inside [text], cut like [extractUrl], each once in the order found, one per
 * line; else [extractUrl] of it. Several links (a selection of a note, a pasted list) are queued
 * together by the downloader.
 */
fun extractUrls(text: String): String =
    LINK_IN_TEXT.findAll(text).map { trimLinkEnd(it.value) }.distinct().joinToString("\n").ifEmpty { text.trim() }

/** The links of the downloader's field when it holds more than one (one per line, all links), else empty. */
fun linksOf(field: String): List<String> {
    val lines = field.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return if (lines.size > 1 && lines.all(::looksLikeUrl)) lines.distinct() else emptyList()
}

private val LINK_IN_TEXT = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)

private fun trimLinkEnd(link: String): String {
    var url = link
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
 * True for something that looks like a web link (what yt-dlp can fetch): http(s), a host, then
 * anything. Not java.net.URI: it refused links yt-dlp fetches ("|", "{", "^", a stray "%" in the
 * query, "_" in a host). A domain in its Unicode form ("https://例子.中国/…") counts too.
 */
fun looksLikeUrl(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return false
    return WEB_LINK.matches(t)
}

/** http(s)://, an optional "user@", a host name or an [IPv6] literal, an optional port, the rest. */
private val WEB_LINK = Regex(
    """^https?://(?:[^/?#@]*@)?(?:[^/?#@:\[\]]+|\[[0-9a-f:.]+])(?::\d*)?(?:[/?#].*)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Plain Italian message for a download/estimate failure. yt-dlp and the network stack report in
 * English (often with stack-like detail), which must not reach the user as is.
 */
fun friendlyDownloadError(raw: String?): String =
    com.cripta.app.work.UserErrors.ofDownload(IllegalStateException(raw.orEmpty()))

package com.cripta.app.media

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads videos from a page/stream URL with yt-dlp (via youtubedl-android). Unlike media3
 * Transformer — which needs a direct media URL — yt-dlp extracts the real stream from ~1000 sites
 * (YouTube, Vimeo, HLS, ...) and, together with the bundled FFmpeg, merges video+audio into a single
 * video. The caller then encrypts that file into the vault, preserving its real container.
 *
 * The native Python/yt-dlp payload is extracted on first use, so [ensureInit] is lazy and cheap on
 * later calls. All methods here block; call them off the main thread.
 */
@Singleton
class YtdlpDownloader @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    @Volatile private var initialized = false
    private val engineLock = ReentrantReadWriteLock()
    private val updater = YtdlpUpdater(appContext)

    /**
     * Initialize once and, when [update] (a download about to start), check for extractor updates
     * daily, with bounded network timeouts. Offline/failed updates retain the installed engine and
     * are retried after five minutes. The update needs the engine to itself: while another link
     * is downloading or being analysed it waits for a later start instead of holding this one (and
     * the analysis of a pasted link) up until that download ends. Blocking; call on IO.
     */
    fun ensureInit(update: Boolean = false) {
        if (!initialized) {
            val lock = engineLock.writeLock()
            lock.lockInterruptibly()
            try {
                if (!initialized) {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                    initialized = true
                }
            } finally { lock.unlock() }
        }
        if (!update || !updater.isDue()) return
        val lock = engineLock.writeLock()
        if (!lock.tryLock()) return
        try {
            runCatching { updater.updateIfDue() }
                .onFailure { android.util.Log.w("YtdlpDownloader", "Engine update failed (${it.javaClass.simpleName}); keeping installed version") }
        } finally { lock.unlock() }
    }

    /**
     * Download [url] into a fresh private temp dir and return the produced file. [maxHeight] caps the
     * resolution (null = best). [processId] lets the caller cancel via [cancel]. Progress callback
     * gives percent (0..100) and ETA seconds. Blocking; call on IO. Throws on failure.
     *
     * Caller owns the returned file: import it, then shred it and delete its parent dir.
     */
    fun download(
        url: String,
        maxHeight: Int?,
        processId: String,
        /** Annulla pressed: checked before starting and on every progress update. */
        isCancelled: () -> Boolean = { false },
        onProgress: (Int, Long) -> Unit,
    ): File {
        ensureInit(update = true)
        // Annulla during the first-use setup (payload extraction, self-update): there was no
        // process to kill yet, so the download started anyway.
        if (isCancelled()) throw java.io.InterruptedIOException("cancelled")
        val dir = File(appContext.cacheDir, "ytdl-${UUID.randomUUID()}").apply { mkdirs() }
        val request = DownloadRequests.download(url, maxHeight, dir)
        val progress = DownloadProgress()
        try {
            withEngine {
                if (isCancelled()) throw java.io.InterruptedIOException("cancelled")
                YoutubeDL.getInstance().execute(request, processId) { _, etaInSeconds, line ->
                    // A cancel that came before the process was registered is honoured here.
                    if (isCancelled()) cancel(processId)
                    onProgress(progress.update(line), etaInSeconds)
                }
            }
            if (isCancelled()) throw java.io.InterruptedIOException("cancelled")
            return DownloadRequests.completedFile(dir)
        } catch (e: Throwable) {
            // Failed or cancelled: the partial video (plaintext, named after the title) goes now.
            wipe(dir)
            throw e
        }
    }

    /** Overwrite then delete every file under [dir], then the folder. Best effort. */
    private fun wipe(dir: File) {
        dir.walkBottomUp().forEach { f ->
            if (f.isFile) runCatching {
                java.io.RandomAccessFile(f, "rw").use { raf ->
                    val chunk = ByteArray(64 * 1024)
                    var left = raf.length()
                    while (left > 0) {
                        val n = minOf(chunk.size.toLong(), left).toInt()
                        raf.write(chunk, 0, n); left -= n
                    }
                }
            }
            runCatching { f.delete() }
        }
    }

    /** Abort a running download started with [processId]. Safe to call after it has finished. */
    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** Probe [url] for its available formats (no download). Blocking; call on IO. */
    fun info(url: String): VideoInfo {
        ensureInit()
        return withEngine { YoutubeDL.getInstance().getInfo(DownloadRequests.common(url)) }
    }

    private inline fun <T> withEngine(block: () -> T): T {
        val lock = engineLock.readLock()
        lock.lockInterruptibly()
        return try { block() } finally { lock.unlock() }
    }

    /**
     * Estimated output size in bytes for a download of [info] capped at [maxHeight] (null = best).
     * Picks the streams the download itself prefers within the cap (MP4 video with M4A audio first,
     * then any container) and adds the separate audio track when the video has none.
     *
     * Sites frequently report a concrete size (or bitrate) for only *some* renditions — often just
     * the top one, which is what "Auto" selects — so every other quality used to come back with no
     * estimate. When a rendition carries no size/bitrate of its own we therefore approximate it by
     * scaling a rendition that does by pixel area (bitrate roughly tracks height², width being
     * proportional for a fixed aspect ratio). That way each quality shows an estimate whenever any
     * rendition does, while exact per-rendition sizes are still used when the site provides them.
     * Returns null only when nothing at all can be estimated.
     */
    fun estimateBytes(info: VideoInfo, maxHeight: Int?): Long? = DownloadEstimate.bytes(info, maxHeight)
}

/** The size estimate of a download, apart from the downloader so it can be tested. */
internal object DownloadEstimate {
    fun bytes(info: VideoInfo, maxHeight: Int?): Long? {
        val formats = info.formats ?: return null
        val dur = info.duration.toLong().coerceAtLeast(0)
        fun declaredSize(f: VideoFormat): Long = when {
            f.fileSize > 0 -> f.fileSize
            f.fileSizeApproximate > 0 -> f.fileSizeApproximate
            else -> 0L
        }
        fun bytesFromKbps(kbps: Int): Long = if (kbps > 0 && dur > 0) kbps.toLong() * 1000L / 8L * dur else 0L
        // A rendition's own size: declared first, then derived from its total bitrate. Null = unknown.
        fun ownBytes(f: VideoFormat): Long? =
            declaredSize(f).takeIf { it > 0 } ?: bytesFromKbps(f.tbr).takeIf { it > 0 }

        fun hasVideo(f: VideoFormat) = f.vcodec != null && f.vcodec != "none"
        fun hasAudio(f: VideoFormat) = f.acodec != null && f.acodec != "none"
        val videos = formats.filter { hasVideo(it) && it.height > 0 }
        if (videos.isEmpty()) return null
        // The download never goes above the cap: a source with only larger renditions cannot be
        // fetched at this quality, so there is no estimate (falling back to the smallest one showed
        // a size for a download that would then fail).
        val fitting = videos.filter { maxHeight == null || it.height <= maxHeight }
        val audios = formats.filter { !hasVideo(it) && hasAudio(it) }
        // The streams the download takes, in its order ([DownloadRequests.download]): an MP4 video
        // with M4A audio, an MP4 with both, then the best video and audio of any container.
        fun tallest(list: List<VideoFormat>) = list.maxByOrNull { it.height }
        val m4a = audios.filter { it.ext == "m4a" }.maxByOrNull { it.abr }
        val mp4 = tallest(fitting.filter { it.ext == "mp4" })
        val mp4Muxed = tallest(fitting.filter { it.ext == "mp4" && hasAudio(it) })
        val (video, audio) = when {
            mp4 != null && m4a != null -> mp4 to m4a
            mp4Muxed != null -> mp4Muxed to null
            else -> (tallest(fitting) ?: return null).let { v -> v to (if (hasAudio(v)) null else audios.maxByOrNull { it.abr }) }
        }

        // Video part: the rendition's own size when known, otherwise scale the highest rendition that
        // does have a size down (or up) to this one's pixel area.
        val videoBytes = ownBytes(video) ?: run {
            val ref = videos.mapNotNull { f -> ownBytes(f)?.let { f to it } }.maxByOrNull { it.first.height }
                ?: return null
            val h = video.height.toDouble()
            val rh = ref.first.height.toDouble()
            (ref.second * (h * h) / (rh * rh)).toLong()
        }

        var total = videoBytes
        // Video-only stream: add the audio track that will be muxed in.
        audio?.let { a ->
            total += (declaredSize(a).takeIf { it > 0 } ?: bytesFromKbps(if (a.abr > 0) a.abr else a.tbr))
        }
        return total.takeIf { it > 0 }
    }
}

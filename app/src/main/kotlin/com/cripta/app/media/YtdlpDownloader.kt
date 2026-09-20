package com.cripta.app.media

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads videos from a page/stream URL with yt-dlp (via youtubedl-android). Unlike media3
 * Transformer — which needs a direct media URL — yt-dlp extracts the real stream from ~1000 sites
 * (YouTube, Vimeo, HLS, ...) and, together with the bundled FFmpeg, merges video+audio into a single
 * MP4. The caller then encrypts that file into the vault.
 *
 * The native Python/yt-dlp payload is extracted on first use, so [ensureInit] is lazy and cheap on
 * later calls. All methods here block; call them off the main thread.
 */
@Singleton
class YtdlpDownloader @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    @Volatile private var initialized = false
    private val initLock = Any()

    /** Extract and initialize yt-dlp + FFmpeg once per process, then pull the latest yt-dlp so
     *  extractors keep up with site changes (YouTube breaks the bundled binary within weeks).
     *  Blocking; call on IO. The update is best-effort — offline just keeps the bundled version. */
    fun ensureInit() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
            runCatching { YoutubeDL.getInstance().updateYoutubeDL(appContext) }
                .onFailure { android.util.Log.w("YtdlpDownloader", "yt-dlp self-update failed", it) }
            initialized = true
        }
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
        onProgress: (Int, Long) -> Unit,
    ): File {
        ensureInit()
        val dir = File(appContext.cacheDir, "ytdl-${UUID.randomUUID()}").apply { mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            addOption("-o", File(dir, "%(title).80s.%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--no-mtime")
            // Prefer the best video+audio within the height cap, then merge to a single MP4.
            val fmt = if (maxHeight != null) {
                "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b"
            } else {
                "bv*+ba/b"
            }
            addOption("-f", fmt)
            addOption("--merge-output-format", "mp4")
        }
        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
            onProgress(progress.toInt().coerceIn(0, 100), etaInSeconds)
        }
        return dir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("yt-dlp non ha prodotto alcun file")
    }

    /** Abort a running download started with [processId]. Safe to call after it has finished. */
    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** Probe [url] for its available formats (no download). Blocking; call on IO. */
    fun info(url: String): VideoInfo {
        ensureInit()
        return YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url).apply { addOption("--no-playlist") })
    }

    /**
     * Estimated output size in bytes for a download of [info] capped at [maxHeight] (null = best).
     * Picks the best video format within the cap, adds a separate audio track when the video is
     * muxed video-only, and falls back to bitrate*duration when a format has no declared size.
     * Returns null when nothing can be estimated.
     */
    fun estimateBytes(info: VideoInfo, maxHeight: Int?): Long? {
        val formats = info.formats ?: return null
        val dur = info.duration.toLong().coerceAtLeast(0)
        fun sizeOf(f: com.yausername.youtubedl_android.mapper.VideoFormat, kbps: Int): Long = when {
            f.fileSize > 0 -> f.fileSize
            f.fileSizeApproximate > 0 -> f.fileSizeApproximate
            kbps > 0 && dur > 0 -> kbps.toLong() * 1000L / 8L * dur
            else -> 0L
        }
        val videos = formats.filter { it.vcodec != null && it.vcodec != "none" && it.height > 0 }
        if (videos.isEmpty()) return null
        val video = if (maxHeight == null) videos.maxByOrNull { it.height }
        else (videos.filter { it.height <= maxHeight }.maxByOrNull { it.height } ?: videos.minByOrNull { it.height })
        video ?: return null
        var total = sizeOf(video, video.tbr)
        // Video-only stream: add the best audio track that will be muxed in.
        if (video.acodec == null || video.acodec == "none") {
            val audios = formats.filter { (it.vcodec == null || it.vcodec == "none") && it.acodec != null && it.acodec != "none" }
            audios.maxByOrNull { it.abr }?.let { total += sizeOf(it, if (it.abr > 0) it.abr else it.tbr) }
        }
        return total.takeIf { it > 0 }
    }
}

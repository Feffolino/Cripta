package com.cripta.app.media

import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

internal object DownloadRequests {
    const val RESULT_FILE = "completed-path.txt"

    fun common(url: String) = YoutubeDLRequest(url).apply {
        addOption("--ignore-config")
        addOption("--no-playlist")
        addOption("--socket-timeout", 20)
        addOption("--extractor-retries", 2)
        addOption("--retries", 3)
        addOption("--fragment-retries", 5)
        // Do not silently accept a video with missing HLS/DASH segments.
        addOption("--abort-on-unavailable-fragments")
        addOption("--retry-sleep", "exp=1:8")
    }

    fun download(url: String, height: Int?, dir: File) = common(url).apply {
        require(height == null || height > 0)
        val cap = height?.let { "[height<=?$it]" }.orEmpty()
        // Prefer MP4 for Android, but keep working when a site only offers WebM/Matroska.
        // Unknown heights are allowed; known heights never fall back above the requested cap.
        addOption("-f", "bv*[ext=mp4]$cap+ba[ext=m4a]/b[ext=mp4]$cap/bv*$cap+ba/b$cap/bv*$cap")
        addOption("--merge-output-format", "mp4/mkv")
        addOption("-o", File(dir, "%(title).80s.%(ext)s").absolutePath)
        addOption("--no-mtime")
        addOption("--newline")
        addOption("--no-simulate")
        // Only the file reported after merging/postprocessing is importable, never a .part or
        // a larger intermediate video-only stream left in the working directory.
        addCommands(listOf("--print-to-file", "after_move:filepath", File(dir, RESULT_FILE).absolutePath))
    }

    fun completedFile(dir: File): File {
        val manifest = File(dir, RESULT_FILE)
        val path = manifest.takeIf { it.isFile }?.readLines()?.lastOrNull { it.isNotBlank() }
            ?: throw IllegalStateException("Nessun file completo prodotto dal downloader")
        val file = File(path).canonicalFile
        check(file.parentFile == dir.canonicalFile && file.isFile && file.length() > 0) {
            "Nessun file completo prodotto dal downloader"
        }
        downloadedVideoMime(file) // Reject fragments, subtitles and other non-video outputs.
        return file
    }
}

internal fun downloadedVideoMime(file: File): String = when (file.extension.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    "ts", "m2ts" -> "video/mp2t"
    "mpg", "mpeg" -> "video/mpeg"
    "flv" -> "video/x-flv"
    "avi" -> "video/x-msvideo"
    "3gp" -> "video/3gpp"
    else -> throw IllegalStateException("Nessun video completo prodotto dal downloader")
}

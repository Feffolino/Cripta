package com.cripta.app.media

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The gallery-dl command and what it leaves behind, apart from the process so they can be tested. */
internal object GalleryRequests {
    /** The most files taken from one link: a whole profile or board is thousands. */
    const val MAX_FILES = 200

    /**
     * gallery-dl for [url] into [dir] (flat, its own file names), run by [python]. Its settings
     * files are not read, it never asks on the terminal and it keeps no cache between links. Videos
     * that would need yt-dlp are left out: that one already tried the link.
     */
    fun command(python: File, url: String, dir: File): List<String> = listOf(
        python.absolutePath, "-m", "gallery_dl",
        "--config-ignore", "--no-input", "--no-colors",
        "-D", dir.absolutePath,
        "--range", "1-$MAX_FILES",
        "--retries", "3", "--http-timeout", "20", "--no-mtime",
        "-o", "cache.file=null",
        "-o", "extractor.*.ytdl.enabled=false",
        "-o", "downloader.ytdl.enabled=false",
        // Only a web link, and last: never read as an option (see DownloadRequests.checkedUrl).
        DownloadRequests.checkedUrl(url),
    )

    /** Type of a file gallery-dl left, by its extension; null for what the vault does not take from a link. */
    fun mime(file: File): String? = when (file.extension.lowercase()) {
        "jpg", "jpeg", "jfif" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        "bmp" -> "image/bmp"
        else -> runCatching { downloadedVideoMime(file) }.getOrNull()
    }

    /** The complete images and videos in [dir], in name order (gallery-dl numbers a post's files). */
    fun mediaFiles(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { it.isFile && it.length() > 0 && mime(it) != null }.sortedBy { it.name }
}

/**
 * Downloads the images (and plain video files) of a post or gallery with gallery-dl, for the links
 * yt-dlp does not take: posts with only pictures, carousels, image boards. It runs on the Python
 * that youtubedl-android brings, with its own packages (the asset `gallery-dl.zip`, see
 * scripts/build_gallery_dl.py) unpacked once per app version. Blocking; call on IO.
 */
@Singleton
class GalleryDownloader @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val ytdlp: YtdlpDownloader,
) {
    private val lock = Any()

    /**
     * Download what [url] shows into a fresh private temp dir and return it; null when gallery-dl
     * does not know the site or found nothing. [onFile] gets the number of files so far. The
     * caller owns the folder: import its [GalleryRequests.mediaFiles], then shred them and delete it.
     */
    fun download(url: String, isCancelled: () -> Boolean, onFile: (Int) -> Unit = {}): File? {
        ytdlp.ensureInit() // unpacks the Python this runs on
        val packages = packages()
        if (isCancelled()) throw java.io.InterruptedIOException("cancelled")
        val work = File(appContext.cacheDir, "gdl-${UUID.randomUUID()}").apply { mkdirs() }
        val home = File(work, "home").apply { mkdirs() }
        val out = File(work, "out").apply { mkdirs() }
        var done = false
        try {
            val python = File(appContext.applicationInfo.nativeLibraryDir, "libpython.so")
            val usr = File(File(File(appContext.noBackupFilesDir, YoutubeDL.baseName), "packages"), "python/usr")
            val process = ProcessBuilder(GalleryRequests.command(python, url, out)).apply {
                environment().apply {
                    put("LD_LIBRARY_PATH", File(usr, "lib").absolutePath)
                    put("SSL_CERT_FILE", File(usr, "etc/tls/cert.pem").absolutePath)
                    put("PYTHONHOME", usr.absolutePath)
                    put("PYTHONPATH", packages.absolutePath)
                    put("HOME", home.absolutePath)
                    put("TMPDIR", work.absolutePath)
                }
                redirectErrorStream(true)
            }.start()
            // Annulla while a file is coming down: no line is printed then, so it is watched apart.
            val watcher = Thread {
                try {
                    while (process.isAlive) { if (isCancelled()) process.destroy(); Thread.sleep(300) }
                } catch (_: InterruptedException) {}
            }.apply { isDaemon = true; start() }
            try {
                var files = 0
                // One line per file saved (its path); messages start with "[". Neither is kept:
                // they name what is being downloaded.
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (line.isNotBlank() && !line.startsWith("[") && !line.startsWith("#")) onFile(++files)
                }
                val exit = process.waitFor()
                if (isCancelled()) throw java.io.InterruptedIOException("cancelled")
                android.util.Log.i("GalleryDownloader", "gallery-dl ended ($exit), $files files")
            } finally {
                watcher.interrupt()
                process.destroy()
            }
            // Some files and an error (one picture of a post gone): what came down is kept.
            if (GalleryRequests.mediaFiles(out).isEmpty()) return null
            done = true
            return out
        } finally {
            if (!done) wipe(work) else runCatching { home.deleteRecursively() }
        }
    }

    /** Overwrite then delete what [download] left in [out]'s working folder. Best effort. */
    fun wipe(out: File) {
        val work = if (out.name == "out") out.parentFile ?: out else out
        work.walkBottomUp().forEach { f ->
            if (f.isFile) runCatching {
                java.io.RandomAccessFile(f, "rw").use { raf ->
                    val chunk = ByteArray(64 * 1024)
                    var left = raf.length()
                    while (left > 0) { val n = minOf(chunk.size.toLong(), left).toInt(); raf.write(chunk, 0, n); left -= n }
                }
            }
            runCatching { f.delete() }
        }
    }

    /**
     * gallery-dl's packages, unpacked from the asset the first time and again after each app
     * update (the asset may have changed): into a folder of their own, moved into place whole.
     */
    private fun packages(): File = synchronized(lock) {
        val root = File(appContext.noBackupFilesDir, "gallery-dl")
        val stamp = appContext.packageManager.getPackageInfo(appContext.packageName, 0).lastUpdateTime.toString()
        val dir = File(root, stamp)
        if (File(dir, "gallery_dl/__main__.py").isFile) return dir
        root.deleteRecursively() // an older version's, or one left half unpacked
        val staged = File(root, "$stamp.tmp").apply { mkdirs() }
        val base = staged.canonicalPath + File.separator
        ZipInputStream(appContext.assets.open("gallery-dl.zip")).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(staged, entry.name)
                check(target.canonicalPath.startsWith(base)) { "Entry outside the package folder" }
                if (entry.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
        }
        check(staged.renameTo(dir)) { "gallery-dl packages not installed" }
        dir
    }
}

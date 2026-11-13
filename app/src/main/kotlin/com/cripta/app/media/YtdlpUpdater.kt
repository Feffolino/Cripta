package com.cripta.app.media

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Bounded update requests, staged and atomically installed without losing the working engine. */
internal class YtdlpUpdater(
    context: Context,
    private val fetch: (String) -> ByteArray = ::fetchUpdate,
) {
    private val prefs = context.getSharedPreferences("download_engine", Context.MODE_PRIVATE)
    private val engineDir = File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName)

    fun isDue(now: Long = System.currentTimeMillis()): Boolean =
        now - prefs.getLong("attempt", 0) >= 5 * 60_000L &&
            now - prefs.getLong("success", 0) >= 24 * 60 * 60_000L

    /** Called under the downloader's write lock, so probes/downloads cannot use a changing ZIP. */
    fun updateIfDue(now: Long = System.currentTimeMillis()) {
        if (!isDue(now)) return
        prefs.edit().putLong("attempt", now).apply()
        val release = JSONObject(String(fetch(RELEASE_URL), Charsets.UTF_8))
        val tag = release.getString("tag_name")
        val binary = File(engineDir, YoutubeDL.ytdlpBin)
        if (tag != prefs.getString("version", null) || !binary.isFile) {
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .first { it.getString("name") == YoutubeDL.ytdlpBin }
            val url = asset.getString("browser_download_url")
            require(url.startsWith("https://github.com/yt-dlp/yt-dlp/releases/download/"))
            // This is code the app runs: never installed without the release's own checksum. A
            // release listed without one keeps the engine already installed.
            val digest = asset.optString("digest")
            check(digest.startsWith("sha256:") && digest.length == 7 + 64) { "Engine update without checksum" }
            val bytes = fetch(url)
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(digest.removePrefix("sha256:").equals(actual, ignoreCase = true)) { "Engine update checksum mismatch" }
            engineDir.mkdirs()
            val staged = File.createTempFile("yt-dlp-update-", ".zip", engineDir)
            try {
                check(isEngineArchive(bytes)) { "Engine update is not a yt-dlp archive" }
                staged.writeBytes(bytes)
                Files.move(staged.toPath(), binary.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { staged.delete() }
        }
        prefs.edit().putString("version", tag).putLong("success", now).apply()
    }

    companion object {
        const val RELEASE_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"

        /**
         * Whether [bytes] is yt-dlp's zipapp: a `#!` line, then a zip holding `__main__.py` and the
         * `yt_dlp` package. Android's ZipFile refuses a file that does not start with a zip header
         * (ZipException), unlike the JVM's, so every update failed on the phone and the engine stayed
         * the one bundled with the library. The entries are read from where the zip starts instead.
         */
        internal fun isEngineArchive(bytes: ByteArray): Boolean {
            val start = zipStart(bytes)
            if (start < 0 || (start > 0 && !(bytes[0] == '#'.code.toByte() && bytes[1] == '!'.code.toByte()))) return false
            val names = HashSet<String>()
            return try {
                ZipInputStream(java.io.ByteArrayInputStream(bytes, start, bytes.size - start)).use { zip ->
                    while (true) names += (zip.nextEntry ?: break).name
                }
                "__main__.py" in names && "yt_dlp/__init__.py" in names
            } catch (_: ZipException) { false }
        }

        /** Offset of the first local file header ("PK\u0003\u0004"), or -1. */
        private fun zipStart(b: ByteArray): Int {
            for (i in 0..b.size - 4) {
                if (b[i] == 0x50.toByte() && b[i + 1] == 0x4b.toByte() && b[i + 2] == 3.toByte() && b[i + 3] == 4.toByte()) return i
            }
            return -1
        }
    }
}

/** At most this long for one request, however slowly the bytes trickle in (each read has its own timeout). */
private const val FETCH_LIMIT_MS = 90_000L

private fun fetchUpdate(url: String): ByteArray {
    val deadline = System.nanoTime() + FETCH_LIMIT_MS * 1_000_000
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 15_000
    connection.setRequestProperty("User-Agent", "Cripta-downloader")
    return try {
        check(connection.responseCode in 200..299) { "Engine update HTTP ${connection.responseCode}" }
        connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= 32 * 1024 * 1024) { "Engine update too large" }
                check(System.nanoTime() < deadline) { "Engine update too slow" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } finally { connection.disconnect() }
}

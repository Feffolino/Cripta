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
import java.util.zip.ZipFile

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
            val bytes = fetch(url)
            val digest = asset.optString("digest")
            if (digest.startsWith("sha256:")) {
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                check(digest.removePrefix("sha256:").equals(actual, ignoreCase = true))
            }
            engineDir.mkdirs()
            val staged = File.createTempFile("yt-dlp-update-", ".zip", engineDir)
            try {
                staged.writeBytes(bytes)
                ZipFile(staged).use { check(it.getEntry("__main__.py") != null && it.getEntry("yt_dlp/__init__.py") != null) }
                Files.move(staged.toPath(), binary.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { staged.delete() }
        }
        prefs.edit().putString("version", tag).putLong("success", now).apply()
    }

    companion object {
        const val RELEASE_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    }
}

private fun fetchUpdate(url: String): ByteArray {
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
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } finally { connection.disconnect() }
}

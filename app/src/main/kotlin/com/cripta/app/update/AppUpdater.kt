package com.cripta.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app updater. Checks the PUBLIC releases repo (no auth needed) for the latest APK, compares it
 * to the installed build, and installs the downloaded APK via the system package installer. The
 * signing key stays private (only the built APK is public), so an update installs in place.
 */
@Singleton
class AppUpdater @Inject constructor() {

    data class Release(val versionName: String, val buildNumber: Int, val apkUrl: String, val sizeBytes: Long)

    /** Installed build number (CI run number == versionCode). */
    fun currentBuild(ctx: Context): Int =
        runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
        }.getOrDefault(0)

    /** Fetch the newest public release with an APK, or null. Uses the releases LIST (not
     *  /releases/latest, which skips prereleases — our CI publishes prereleases) and picks the
     *  highest build number that has an .apk asset. */
    suspend fun latest(includePrerelease: Boolean = true): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val json = httpGet("https://api.github.com/repos/$REPO/releases?per_page=20")
            val arr = JSONArray(json)
            // Candidates newest build first; the APK is looked up only until one is found.
            val candidates = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { !it.optBoolean("draft") && (includePrerelease || !it.optBoolean("prerelease")) }
                .mapNotNull { o ->
                    val tag = o.optString("tag_name")            // e.g. v0.1.0-b123
                    tag.substringAfterLast("-b", "").toIntOrNull()?.let { build -> Triple(o, tag, build) }
                }
                .sortedByDescending { it.third }
            for ((obj, tag, build) in candidates) {
                // The list sometimes comes back with an empty "assets" array even when the APK is
                // attached, so fall back to the release's own assets endpoint.
                val embedded = obj.optJSONArray("assets")
                val assets = if (embedded != null && embedded.length() > 0) embedded
                    else obj.optString("assets_url").takeIf { it.isNotBlank() }
                        ?.let { runCatching { JSONArray(httpGet(it)) }.getOrNull() } ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk")) {
                        return@runCatching Release(tag.removePrefix("v"), build, a.optString("browser_download_url"), a.optLong("size"))
                    }
                }
            }
            null
        }.getOrNull()
    }

    /** Download [url] to app cache as update.apk, reporting 0..100 progress. Call off the main thread. */
    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, "update.apk").apply { if (exists()) delete() }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 20000; readTimeout = 20000
        }
        conn.inputStream.use { input ->
            val total = conn.contentLengthLong.takeIf { it > 0 }
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024); var read: Int; var done = 0L
                while (input.read(buf).also { read = it } >= 0) {
                    output.write(buf, 0, read); done += read
                    if (total != null) onProgress((done * 100 / total).toInt().coerceIn(0, 100))
                }
            }
        }
        conn.disconnect()
        out
    }

    /** Hand the downloaded APK to the system installer. */
    fun install(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    companion object {
        // The app checks this repo's public GitHub releases (the repo is public; the signing key is
        // kept out of it via CI secrets, so making it public never exposes the key).
        const val REPO = "Feffolino/Cripta"
    }
}

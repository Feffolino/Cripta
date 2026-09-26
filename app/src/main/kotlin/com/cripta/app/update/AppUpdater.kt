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

    /** [sha256Url] = the release's "<apk>.sha256" asset, when CI published one (older releases: null). */
    data class Release(
        val versionName: String,
        val buildNumber: Int,
        val apkUrl: String,
        val sizeBytes: Long,
        val sha256Url: String? = null,
    )

    /**
     * The update download, which runs in the background service (it goes on with the app closed):
     * the settings screen follows it from here, whenever it is open.
     */
    sealed interface Download {
        data object Idle : Download
        data class Running(val release: Release, val pct: Int) : Download
        data class Ready(val release: Release, val apk: File) : Download
        data class Failed(val release: Release, val error: Throwable) : Download
        data class Cancelled(val release: Release) : Download
    }
    private val _download = kotlinx.coroutines.flow.MutableStateFlow<Download>(Download.Idle)
    val downloadState: kotlinx.coroutines.flow.StateFlow<Download> = _download
    fun publish(d: Download) { _download.value = d }

    /** The system installer for [apk], as an intent. */
    fun installIntent(ctx: Context, apk: File): Intent = installerIntent(ctx, apk)

    /** Checksum asset per APK url, remembered from [latest] so [download] can verify it. */
    private val checksumFor = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Installed build number (CI run number == versionCode). */
    fun currentBuild(ctx: Context): Int =
        runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
        }.getOrDefault(0)

    /** Fetch the newest public release with an APK, or null when none exists. Throws
     *  [java.io.IOException] when GitHub can't be reached, so callers can tell "offline" from
     *  "no release". Uses the releases LIST (not
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
                val all = (0 until assets.length()).map { assets.getJSONObject(it) }
                val apk = all.firstOrNull { it.optString("name").endsWith(".apk") } ?: continue
                val apkName = apk.optString("name")
                // Prefer "<apk>.sha256"; accept a lone .sha256 asset as well.
                val sha = all.firstOrNull { it.optString("name") == "$apkName.sha256" }
                    ?: all.filter { it.optString("name").endsWith(".sha256") }.singleOrNull()
                val apkUrl = apk.optString("browser_download_url")
                val shaUrl = sha?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
                if (shaUrl != null) checksumFor[apkUrl] = shaUrl
                return@runCatching Release(tag.removePrefix("v"), build, apkUrl, apk.optLong("size"), shaUrl)
            }
            null
        }.getOrElse { e ->
            // Network failures propagate; a malformed response just means "nothing usable".
            if (e is java.io.IOException || e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Download [url] to app cache as update.apk, reporting 0..100 progress. Call off the main thread.
     *
     * When the release publishes a SHA-256 checksum ([sha256Url], or the one found by [latest]) the
     * file is verified before it is handed to the installer; a mismatch deletes it and throws. A
     * release without a checksum asset (older builds) is accepted as before.
     */
    suspend fun download(
        ctx: Context,
        url: String,
        sha256Url: String? = null,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, APK_NAME).apply { if (exists()) delete() }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 20000; readTimeout = 20000
        }
        try {
            conn.inputStream.use { input ->
                val total = conn.contentLengthLong.takeIf { it > 0 }
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var read: Int; var done = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        output.write(buf, 0, read); md.update(buf, 0, read); done += read
                        if (total != null) onProgress((done * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } catch (e: Exception) {
            out.delete(); throw e
        } finally {
            conn.disconnect()
        }
        val checksumUrl = sha256Url ?: checksumFor[url]
        if (checksumUrl != null) {
            val expected = try {
                // "sha256sum" format: "<64 hex>  <file name>"; take the first hex token.
                Regex("[0-9a-fA-F]{64}").find(httpGet(checksumUrl, accept = null))?.value?.lowercase()
            } catch (e: Exception) {
                out.delete()
                throw java.io.IOException("Impossibile verificare l'aggiornamento: checksum non scaricabile", e)
            } ?: run {
                out.delete()
                throw java.io.IOException("Impossibile verificare l'aggiornamento: checksum non valido")
            }
            val actual = md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            if (actual != expected) {
                out.delete()
                throw SecurityException("L'aggiornamento scaricato non corrisponde a quello pubblicato (SHA-256 diverso): installazione annullata")
            }
        }
        out
    }

    /** Hand the downloaded APK to the system installer. */
    fun install(ctx: Context, apk: File) {
        ctx.startActivity(installIntent(ctx, apk))
    }

    private fun httpGet(url: String, accept: String? = "application/vnd.github+json"): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            instanceFollowRedirects = true
            accept?.let { setRequestProperty("Accept", it) }
        }
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        // The app checks this repo's public GitHub releases (the repo is public; the signing key is
        // kept out of it via CI secrets, so making it public never exposes the key).
        const val REPO = "Feffolino/Cripta"
        /** The downloaded update, in the app cache (shared with the FileProvider as "updates"). */
        const val APK_NAME = "update.apk"

        /** The system installer for [apk] (see also [InstallUpdateActivity], the notification's way to it). */
        fun installerIntent(ctx: Context, apk: File): Intent {
            val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}

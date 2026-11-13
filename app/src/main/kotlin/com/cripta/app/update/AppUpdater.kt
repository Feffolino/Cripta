package com.cripta.app.update

import com.cripta.app.R
import com.cripta.app.str

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    class Rejected(message: String) : SecurityException(message)

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

    /** Reads a GitHub API url (tests stand in for GitHub). */
    internal var get: (String) -> String = { httpGet(it) }

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
     *  "no release". With [includePrerelease] off it asks /releases/latest first (GitHub's latest
     *  stable release, which may sit behind more CI prereleases than the list pages below reach);
     *  with prereleases on, or when that has no APK or answers 404, it reads the releases LIST page
     *  by page and picks the highest build number that has an .apk asset. */
    suspend fun latest(includePrerelease: Boolean = true): Release? = withContext(Dispatchers.IO) {
        runCatching {
            // Prereleases off: GitHub's own latest stable release first. The CI publishes a
            // prerelease per build, and behind more of them than the pages below look through the
            // last stable release was never found. None (404): the list decides.
            if (!includePrerelease) {
                val stable = try {
                    org.json.JSONObject(get("https://api.github.com/repos/$REPO/releases/latest"))
                } catch (e: java.io.FileNotFoundException) {
                    null
                }
                stable?.let { pickFrom(JSONArray().put(it), includePrerelease = false) }?.let { return@runCatching it }
            }
            // Page by page (newest first) until one has a usable release.
            for (page in 1..MAX_PAGES) {
                val arr = JSONArray(get("https://api.github.com/repos/$REPO/releases?per_page=$PAGE_SIZE&page=$page"))
                pickFrom(arr, includePrerelease)?.let { return@runCatching it }
                if (arr.length() < PAGE_SIZE) break
            }
            null
        }.getOrElse { e ->
            // Network failures propagate; a malformed response just means "nothing usable".
            if (e is java.io.IOException || e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** The newest release of one page of the list that has an APK, or null. */
    private fun pickFrom(arr: JSONArray, includePrerelease: Boolean): Release? {
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
                    ?.let { runCatching { JSONArray(get(it)) }.getOrNull() } ?: continue
            val all = (0 until assets.length()).map { assets.getJSONObject(it) }
            val apk = all.firstOrNull { it.optString("name").endsWith(".apk") } ?: continue
            val apkName = apk.optString("name")
            // Prefer "<apk>.sha256"; accept a lone .sha256 asset as well.
            val sha = all.firstOrNull { it.optString("name") == "$apkName.sha256" }
                ?: all.filter { it.optString("name").endsWith(".sha256") }.singleOrNull()
            val apkUrl = apk.optString("browser_download_url")
            val shaUrl = sha?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
            if (shaUrl != null) checksumFor[apkUrl] = shaUrl
            return Release(tag.removePrefix("v"), build, apkUrl, apk.optLong("size"), shaUrl)
        }
        return null
    }

    /**
     * Download [url] to app cache as update.apk, reporting 0..100 progress. Call off the main thread.
     *
     * A published SHA-256 checksum ([sha256Url], or the one found by [latest]) is required.
     * Verify the checksum and package before handing the APK to the system installer.
     */
    suspend fun download(
        ctx: Context,
        url: String,
        sha256Url: String? = null,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val checksumUrl = (sha256Url ?: checksumFor[url])?.takeIf { it.isNotBlank() }
            ?: throw Rejected(str(R.string.update_checksum_required))
        // Written beside update.apk and moved over it only once verified: an earlier "Installa"
        // (its notification still there) opened the file this download was rewriting, and the
        // installer got half an APK.
        val target = apkFile(ctx)
        val out = File(target.parentFile, PART_NAME).apply { parentFile?.mkdirs(); if (exists()) delete() }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 20000; readTimeout = 20000
        }
        try {
            conn.inputStream.use { input ->
                val total = conn.contentLengthLong.takeIf { it > 0 }
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var read: Int; var done = 0L; var lastPct = -1
                    while (input.read(buf).also { read = it } >= 0) {
                        // Annulla: the blocking read loop never looked at the cancellation, so the
                        // whole APK kept downloading and "annullato" only came at the end.
                        ensureActive()
                        output.write(buf, 0, read); md.update(buf, 0, read); done += read
                        // Only when the percentage moves: a read returns ~8 KB, and the callback
                        // rebuilds the notification (binder calls) on this thread, which capped
                        // the download speed far below the browser's.
                        if (total != null) {
                            val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            out.delete(); throw e
        } finally {
            conn.disconnect()
        }
        try {
            val expected = try {
                // "sha256sum" format: "<64 hex>  <file name>"; take the first hex token.
                Regex("\\b[0-9a-fA-F]{64}\\b").find(httpGet(checksumUrl, accept = null))?.value
            } catch (e: java.io.IOException) {
                throw java.io.IOException(str(R.string.update_checksum_missing), e)
            }
            val actual = md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            // Verify integrity before asking Android to parse the downloaded APK.
            refusal(expected, actual, ctx.packageName, ctx.packageName)?.let { throw Rejected(str(it)) }
            @Suppress("DEPRECATION")
            val apkPackage = ctx.packageManager.getPackageArchiveInfo(out.path, 0)?.packageName
            refusal(expected, actual, apkPackage, ctx.packageName)?.let { throw Rejected(str(it)) }
        } catch (e: Throwable) {
            out.delete(); throw e
        }
        if (!out.renameTo(target)) { out.delete(); throw java.io.IOException(str(R.string.update_missing)) }
        target
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
        /** Releases per page of the list, and how many pages are looked through at most. */
        private const val PAGE_SIZE = 30
        private const val MAX_PAGES = 5

        internal fun refusal(expectedSha: String?, actualSha: String, apkPackage: String?, ownPackage: String): Int? = when {
            expectedSha == null || !expectedSha.matches(Regex("[0-9a-fA-F]{64}")) -> R.string.update_checksum_invalid
            !expectedSha.equals(actualSha, ignoreCase = true) -> R.string.update_checksum_mismatch
            apkPackage != ownPackage -> R.string.update_wrong_package
            else -> null
        }

        // The app checks this repo's public GitHub releases (the repo is public; the signing key is
        // kept out of it via CI secrets, so making it public never exposes the key).
        const val REPO = "Feffolino/Cripta"
        /** The downloaded update, in the app cache (shared with the FileProvider as "updates"). */
        const val APK_NAME = "update.apk"

        /** Where the update is downloaded: its own cache folder, the only one the FileProvider shares. */
        fun apkFile(ctx: Context): File = File(File(ctx.cacheDir, UPDATES_DIR), APK_NAME)
        private const val UPDATES_DIR = "updates"
        /** The download in progress, until verified (see [download]). */
        private const val PART_NAME = "update.part.apk"

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

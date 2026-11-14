package com.cripta.app.update

import android.app.Application
import com.cripta.app.R
import com.cripta.app.Strings
import com.cripta.app.str
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class AppUpdaterTest {
    private val sha = "ab".repeat(32)
    private val ownPackage = "com.cripta.app"

    @Before
    fun texts() = Strings.init(RuntimeEnvironment.getApplication())

    @Test
    fun matchingChecksumAndPackageAreAccepted() {
        assertNull(AppUpdater.refusal(sha, sha, ownPackage, ownPackage))
        assertNull(AppUpdater.refusal(sha.uppercase(), sha, ownPackage, ownPackage))
    }

    @Test
    fun missingChecksumIsRefusedBeforePackageValidation() {
        assertEquals(R.string.update_checksum_invalid, AppUpdater.refusal(null, sha, null, ownPackage))
    }

    @Test
    fun malformedChecksumIsRefused() {
        for (expected in listOf("", "not a checksum", "a".repeat(63), "a".repeat(65), "g".repeat(64))) {
            assertEquals(R.string.update_checksum_invalid, AppUpdater.refusal(expected, sha, ownPackage, ownPackage))
        }
    }

    @Test
    fun differentChecksumIsRefusedBeforePackageValidation() {
        assertEquals(R.string.update_checksum_mismatch, AppUpdater.refusal("cd".repeat(32), sha, null, ownPackage))
    }

    @Test
    fun differentPackageIsRefused() {
        assertEquals(R.string.update_wrong_package, AppUpdater.refusal(sha, sha, "other.app", ownPackage))
    }

    @Test
    fun unreadablePackageIsRefused() {
        assertEquals(R.string.update_wrong_package, AppUpdater.refusal(sha, sha, null, ownPackage))
    }

    @Test
    fun missingChecksumUrlFailsBeforeOpeningAConnection() {
        val app = RuntimeEnvironment.getApplication()
        val error = assertThrows(AppUpdater.Rejected::class.java) {
            runBlocking {
                AppUpdater().download(app, "https://example.invalid/x.apk", null) {
                    throw AssertionError("Download must not start without a checksum")
                }
            }
        }
        assertEquals(str(R.string.update_checksum_required), error.message)
        assertFalse(AppUpdater.apkFile(app).exists())
    }

    @Test
    fun blankChecksumUrlAlsoFailsBeforeOpeningAConnection() {
        val error = assertThrows(AppUpdater.Rejected::class.java) {
            runBlocking {
                AppUpdater().download(RuntimeEnvironment.getApplication(), "https://example.invalid/x.apk", " ") {}
            }
        }
        assertEquals(str(R.string.update_checksum_required), error.message)
    }

    /** A release as the GitHub list gives it, with its APK. */
    private fun release(build: Int, prerelease: Boolean) = org.json.JSONObject()
        .put("tag_name", "v0.1.0-b$build").put("prerelease", prerelease).put("draft", false)
        .put("assets", org.json.JSONArray().put(org.json.JSONObject()
            .put("name", "cripta.apk").put("browser_download_url", "https://example.invalid/b$build.apk").put("size", 1)))

    /**
     * GitHub with [stable] (null: none) behind [prereleases] newer prereleases, 30 a page.
     * [latestEndpoint] off: /releases/latest answers 404; [latestApk] off: it gives the release
     * without its APK.
     */
    private fun github(stable: Int?, latestEndpoint: Boolean = true, prereleases: Int = 200,
                       latestApk: Boolean = true): (String) -> String {
        val all = (300 downTo 301 - prereleases).map { release(it, prerelease = true) } +
            listOfNotNull(stable?.let { release(it, false) })
        return { url ->
            when {
                url.endsWith("/releases/latest") ->
                    if (stable != null && latestEndpoint)
                        release(stable, false).apply { if (!latestApk) put("assets", org.json.JSONArray()) }.toString()
                    else throw java.io.FileNotFoundException(url)
                else -> {
                    val page = Regex("page=(\\d+)$").find(url)!!.groupValues[1].toInt()
                    org.json.JSONArray(all.drop((page - 1) * 30).take(30)).toString()
                }
            }
        }
    }

    @Test
    fun theLastStableReleaseIsFoundBehindManyPrereleases() = runBlocking {
        val updater = AppUpdater().apply { get = github(stable = 90) }
        assertEquals(90, updater.latest(includePrerelease = false)?.buildNumber)
        assertEquals(300, updater.latest(includePrerelease = true)?.buildNumber)
    }

    @Test
    fun withNoLatestReleaseTheListIsRead() = runBlocking {
        // /releases/latest answers 404 (no release marked latest): the list still finds the stable one.
        val updater = AppUpdater().apply { get = github(stable = 5, latestEndpoint = false, prereleases = 3) }
        assertEquals(5, updater.latest(includePrerelease = false)?.buildNumber)
    }

    @Test
    fun aLatestReleaseWithoutItsApkFallsBackToTheList() = runBlocking {
        val updater = AppUpdater().apply { get = github(stable = 5, prereleases = 3, latestApk = false) }
        assertEquals(5, updater.latest(includePrerelease = false)?.buildNumber)
    }

    @Test
    fun noStableReleaseIsNoUpdateNotAnError() = runBlocking {
        assertNull(AppUpdater().apply { get = github(stable = null) }.latest(includePrerelease = false))
    }

    // --- download(): the bytes, their checksum and the package, before anything is handed to the installer ---

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val apkBytes = ByteArray(200_000) { (it % 97).toByte() }
    private val apkSha = java.security.MessageDigest.getInstance("SHA-256").digest(apkBytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    private val part get() = java.io.File(AppUpdater.apkFile(app).parentFile, "update.part.apk")

    private fun updater(checksum: () -> String = { "$apkSha  cripta.apk" }, apkPackage: String? = app.packageName,
                        input: () -> java.io.InputStream = { apkBytes.inputStream() }) = AppUpdater().apply {
        open = { AppUpdater.Body(input(), apkBytes.size.toLong()) {} }
        getChecksum = { checksum() }
        archivePackage = { _, _ -> apkPackage }
    }

    @org.junit.After
    fun noDownloadLeft() {
        AppUpdater.apkFile(app).delete(); part.delete()
    }

    @Test
    fun aVerifiedDownloadBecomesUpdateApk() = runBlocking {
        val progress = mutableListOf<Int>()
        val apk = updater().download(app, "https://example.invalid/x.apk", "https://example.invalid/x.sha256") { progress += it }
        assertEquals(AppUpdater.apkFile(app), apk)
        org.junit.Assert.assertArrayEquals(apkBytes, apk.readBytes())
        assertFalse(part.exists())
        assertEquals(100, progress.last())
        // Only when the percentage moves.
        assertEquals(progress.distinct(), progress)
    }

    private fun assertRefused(updater: AppUpdater, error: Class<out Throwable>, message: String) {
        // An earlier verified APK stays until a new one is verified: its notification may still open it.
        AppUpdater.apkFile(app).apply { parentFile?.mkdirs(); writeText("earlier") }
        val thrown = assertThrows(error) {
            runBlocking { updater.download(app, "https://example.invalid/x.apk", "https://example.invalid/x.sha256") {} }
        }
        assertEquals(message, thrown.message)
        assertFalse("the unverified download is removed", part.exists())
        assertEquals("earlier", AppUpdater.apkFile(app).readText())
    }

    @Test
    fun aDifferentChecksumIsRefusedAndLeavesNothing() =
        assertRefused(updater(checksum = { "cd".repeat(32) }), AppUpdater.Rejected::class.java, str(R.string.update_checksum_mismatch))

    @Test
    fun anApkOfAnotherAppIsRefused() =
        assertRefused(updater(apkPackage = "other.app"), AppUpdater.Rejected::class.java, str(R.string.update_wrong_package))

    @Test
    fun anUnreadableChecksumFileSaysSo() =
        assertRefused(updater(checksum = { throw java.io.FileNotFoundException("x.sha256") }),
            java.io.IOException::class.java, str(R.string.update_checksum_missing))

    @Test
    fun aChecksumFileWithoutAChecksumIsRefused() =
        assertRefused(updater(checksum = { "<html>not found</html>" }), AppUpdater.Rejected::class.java, str(R.string.update_checksum_invalid))

    @Test
    fun aDownloadCutOffMidwayLeavesNothing() {
        val failing = object : java.io.InputStream() {
            var left = 100_000
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) throw java.net.SocketTimeoutException("read timed out")
                return minOf(len, left).also { left -= it }
            }
        }
        assertRefused(updater(input = { failing }), java.net.SocketTimeoutException::class.java, "read timed out")
    }

    @Test
    fun cancellingStopsTheDownloadAndLeavesNothing() = runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val reading = kotlinx.coroutines.CompletableDeferred<Unit>()
        val endless = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reading.complete(Unit); Thread.sleep(1); return len
            }
        }
        val job = scope.async { updater(input = { endless }).download(app, "https://example.invalid/x.apk", "https://example.invalid/x.sha256") {} }
        reading.await()
        job.cancel()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking { job.await() } }
        assertFalse(part.exists())
        assertFalse(AppUpdater.apkFile(app).exists())
    }

    @Test
    fun anInstalledUpdateLeavesTheCacheAndANewerOneStays() {
        val apk = AppUpdater.apkFile(app).apply { parentFile?.mkdirs(); writeBytes(ByteArray(16)) }
        val updater = AppUpdater()
        val installed = updater.currentBuild(app).toLong()
        updater.archiveBuild = { _, _ -> installed + 1 }
        updater.dropInstalled(app)
        assertTrue(apk.exists())
        updater.archiveBuild = { _, _ -> installed }
        updater.dropInstalled(app)
        assertFalse(apk.exists())
        // Unreadable: of no use to the installer either.
        apk.writeBytes(ByteArray(16))
        updater.archiveBuild = { _, _ -> null }
        updater.dropInstalled(app)
        assertFalse(apk.exists())
        updater.dropInstalled(app)
    }
}

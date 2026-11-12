package com.cripta.app.update

import android.app.Application
import com.cripta.app.R
import com.cripta.app.Strings
import com.cripta.app.str
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    /** GitHub with [stable] (null: none) behind 200 newer prereleases, 30 a page. */
    private fun github(stable: Int?, latestEndpoint: Boolean = true): (String) -> String {
        val all = (300 downTo 101).map { release(it, prerelease = true) } + listOfNotNull(stable?.let { release(it, false) })
        return { url ->
            when {
                url.endsWith("/releases/latest") ->
                    if (stable != null && latestEndpoint) release(stable, false).toString() else throw java.io.FileNotFoundException(url)
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
    fun noStableReleaseIsNoUpdateNotAnError() = runBlocking {
        assertNull(AppUpdater().apply { get = github(stable = null) }.latest(includePrerelease = false))
    }
}

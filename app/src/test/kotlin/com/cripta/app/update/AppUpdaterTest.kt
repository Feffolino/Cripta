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
}

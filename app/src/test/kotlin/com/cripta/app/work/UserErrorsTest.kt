package com.cripta.app.work

import android.app.Application
import com.cripta.app.R
import com.cripta.app.Strings
import com.cripta.app.str
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class UserErrorsTest {
    @Before
    fun texts() = Strings.init(RuntimeEnvironment.getApplication())

    @Test
    fun vaultOperationsRecogniseTypedAndPlatformErrorsThroughWrappers() {
        val cases = listOf(
            OutOfMemoryError("allocation detail") to R.string.err_out_of_memory,
            IOException("open failed: ENOSPC") to R.string.err_no_space,
            IOException("No space left on device") to R.string.err_no_space,
            IllegalStateException("Vault locked during work") to R.string.err_vault_locked_during,
            FileNotFoundException("/private/document.pdf") to R.string.err_file_not_found,
            SecurityException("private provider detail") to R.string.err_permission_denied,
            IOException("Permission denial by provider") to R.string.err_permission_denied,
            UnknownHostException("private.example") to R.string.err_no_internet,
            ConnectException("private.example:443") to R.string.err_no_internet,
            SocketTimeoutException("private.example") to R.string.err_timeout,
            AEADBadTagException("cipher detail") to R.string.err_bad_encrypted,
            IOException("TAG MISMATCH") to R.string.err_bad_encrypted,
            GeneralSecurityException("key detail") to R.string.err_crypto,
            IOException("raw filesystem detail") to R.string.err_io,
            IllegalArgumentException("raw parser detail") to R.string.err_unexpected,
        )
        for ((cause, expected) in cases) {
            val wrapped = IllegalStateException("service wrapper", RuntimeException("worker wrapper", cause))
            assertEquals(cause.javaClass.simpleName, str(expected), UserErrors.of(wrapped))
        }
    }

    @Test
    fun urgentCauseWinsOverGenericWrappersAndOwnMessages() {
        assertEquals(str(R.string.err_out_of_memory),
            UserErrors.of(IOException("ENOSPC", OutOfMemoryError())))
        assertEquals(str(R.string.err_no_space),
            UserErrors.of(FileNotFoundException("No space left on device")))
        assertEquals(str(R.string.err_file_not_found),
            UserErrors.of(SecurityException("provider", FileNotFoundException())))
        assertEquals(str(R.string.err_bad_encrypted),
            UserErrors.of(GeneralSecurityException("outer", AEADBadTagException())))
        assertEquals(str(R.string.err_no_space),
            UserErrors.of(IOException(str(R.string.err_write_backup), IOException("ENOSPC"))))
    }

    @Test
    fun translatedOwnMessagesSurviveButUnrecognisedCauseTextDoesNotLeak() {
        val own = str(R.string.err_read_incomplete, "example.pdf")
        assertEquals(own, UserErrors.of(IOException(own)))
        val result = UserErrors.of(IllegalStateException("wrapper",
            IOException("/private/customer-document.pdf: unexpected errno 17")))
        assertEquals(str(R.string.err_io), result)
        assertFalse(result.contains("customer-document.pdf"))
        assertEquals(str(R.string.err_unexpected), UserErrors.of(Throwable()))
    }

    @Test
    fun downloadsClassifyExtractorHttpAndIncompleteOutputErrors() {
        val cases = listOf(
            "ENOSPC" to R.string.err_no_space,
            "No space left on device" to R.string.err_no_space,
            "SSL handshake failed" to R.string.dl_err_certificate,
            "certificate verify failed" to R.string.dl_err_certificate,
            "Requested format is not available" to R.string.dl_err_no_format,
            "No video formats found" to R.string.dl_err_no_format,
            "No supported JavaScript runtime" to R.string.dl_err_extractor,
            "JavaScript challenge" to R.string.dl_err_extractor,
            "nsig extraction failed" to R.string.dl_err_extractor,
            "Unsupported URL" to R.string.dl_err_unsupported,
            "DRM protected" to R.string.dl_err_drm,
            "Private video" to R.string.dl_err_private,
            "Sign in to continue" to R.string.dl_err_private,
            "Login required" to R.string.dl_err_private,
            "Members-only video" to R.string.dl_err_private,
            "Video not available" to R.string.dl_err_unavailable,
            "Video unavailable" to R.string.dl_err_unavailable,
            "Video removed" to R.string.dl_err_unavailable,
            "Geographic restriction" to R.string.dl_err_geo,
            "HTTP 429" to R.string.dl_err_too_many,
            "Too many requests" to R.string.dl_err_too_many,
            "HTTP 403" to R.string.dl_err_forbidden,
            "Forbidden" to R.string.dl_err_forbidden,
            "HTTP 404" to R.string.dl_err_not_found,
            "Not found" to R.string.dl_err_not_found,
            "Unable to download webpage" to R.string.dl_err_connection,
            "Name or service not known" to R.string.dl_err_connection,
            "Failed to resolve hostname" to R.string.dl_err_connection,
            "Network is unreachable" to R.string.dl_err_connection,
            "Timed out" to R.string.dl_err_connection,
            "Connection reset" to R.string.dl_err_connection,
            "Il download non ha prodotto un file" to R.string.dl_err_incomplete,
            "Nessun file completo" to R.string.dl_err_incomplete,
            "Nessun video completo" to R.string.dl_err_incomplete,
            "opaque downloader diagnostic" to R.string.dl_err_failed,
        )
        for ((diagnostic, expected) in cases) {
            assertEquals(diagnostic, str(expected), UserErrors.ofDownload(
                IllegalStateException("worker", RuntimeException(diagnostic))))
        }
    }

    @Test
    fun downloadCanonicalErrorsAreIdempotentAndTypedFallbackStaysUseful() {
        val ids = listOf(
            R.string.dl_err_unsupported, R.string.dl_err_drm, R.string.dl_err_private,
            R.string.dl_err_unavailable, R.string.dl_err_geo, R.string.dl_err_too_many,
            R.string.dl_err_forbidden, R.string.dl_err_not_found, R.string.dl_err_connection,
            R.string.dl_err_no_format, R.string.dl_err_incomplete, R.string.dl_err_extractor,
            R.string.err_no_space, R.string.dl_err_certificate, R.string.dl_err_failed,
        )
        for (id in ids) {
            val message = str(id)
            assertEquals(message, UserErrors.ofDownload(IllegalStateException(message)))
        }
        assertEquals(str(R.string.err_out_of_memory), UserErrors.ofDownload(OutOfMemoryError()))
        assertEquals(str(R.string.err_io), UserErrors.ofDownload(IOException("raw disk diagnostic")))
        assertEquals(str(R.string.err_no_space),
            UserErrors.ofDownload(IllegalStateException("SSL: ENOSPC")))
    }

    // Regression: a word-based heuristic once let raw diagnostics containing "vault" through.
    @Test
    fun rawDiagnosticContainingVaultMustNotReachTheUi() {
        assertEquals(str(R.string.err_io),
            UserErrors.of(IOException("vault /private/customer-document.pdf: errno 17")))
    }
}

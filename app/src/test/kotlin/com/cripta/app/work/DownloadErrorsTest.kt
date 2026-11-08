package com.cripta.app.work

import com.cripta.app.ui.download.friendlyDownloadError
import org.junit.Assert.*
import org.junit.Test
import android.app.Application
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// The texts compared here are the Italian ones (res/values-it).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "it")
class DownloadErrorsTest {
    @Before
    fun texts() = com.cripta.app.Strings.init(RuntimeEnvironment.getApplication())

    @Test fun queuePreservesTheActualErrorInsteadOfReplacingItWithGenericFailure() {
        for (raw in listOf("HTTP Error 403: Forbidden", "Requested format is not available", "No space left on device")) {
            val message = UserErrors.ofDownload(IllegalStateException(raw))
            assertEquals(message, friendlyDownloadError(message))
            assertNotEquals("Download non riuscito", message)
        }
    }

    @Test fun unavailableRequestedFormatIsNotMisreportedAsRemovedVideo() {
        assertEquals("Nessun formato video disponibile alla qualità scelta",
            UserErrors.ofDownload(IllegalStateException("Requested format is not available")))
    }
}

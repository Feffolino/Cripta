package com.cripta.app.work

import com.cripta.app.ui.download.friendlyDownloadError
import org.junit.Assert.*
import org.junit.Test

class DownloadErrorsTest {
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

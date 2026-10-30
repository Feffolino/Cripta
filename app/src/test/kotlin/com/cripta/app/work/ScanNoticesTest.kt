package com.cripta.app.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanNoticesTest {
    private val notes = ScanNotices<String> { it }

    @Test
    fun twoScansShareOneNotification() {
        assertEquals("faces", notes.update("faces", 10, otherBusy = false))
        // The intro scan never posts its own: the faces notification shows it.
        assertEquals("faces", notes.update("intro", 30, otherBusy = false))
        assertEquals("intro 30%", notes.others("faces"))
        notes.end("faces")
        assertEquals("intro", notes.update("intro", 40, otherBusy = false))
        assertNull(notes.others("intro"))
    }

    @Test
    fun scansStaySilentWhileAnotherOperationShows() {
        assertNull(notes.update("faces", 10, otherBusy = true))
        assertNull(notes.update("intro", 10, otherBusy = true))
        assertEquals("faces", notes.update("faces", 20, otherBusy = false))
        assertEquals("intro 10%", notes.others("faces"))
    }
}

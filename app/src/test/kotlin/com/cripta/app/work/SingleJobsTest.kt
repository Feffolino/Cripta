package com.cripta.app.work

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A duplicate scan or an update download asked while one is running is not started again: two
 * scans shared one result and progress, and the first one ending dropped the second from "Annulla".
 */
class SingleJobsTest {
    private val running = CompletableDeferred<Unit>()
    private val ended = Job().apply { complete() }

    @Test
    fun aSecondScanWaitsForTheRunningOne() {
        assertTrue(ConversionService.alreadyRunning("dup_scan", scan = running, update = null))
        assertFalse(ConversionService.alreadyRunning("dup_scan", scan = ended, update = null))
        assertFalse(ConversionService.alreadyRunning("dup_scan", scan = null, update = running))
    }

    @Test
    fun aSecondUpdateDownloadWaitsForTheRunningOne() {
        assertTrue(ConversionService.alreadyRunning("update", scan = null, update = running))
        assertFalse(ConversionService.alreadyRunning("update", scan = running, update = null))
    }

    @Test
    fun otherJobsAreNotHeldBack() {
        assertFalse(ConversionService.alreadyRunning("import", scan = running, update = running))
    }
}

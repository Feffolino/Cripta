package com.cripta.app.work

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Regression: a job being cancelled is no longer active, yet it still runs its last lines.
    @Test
    fun aJobStillStoppingCountsAsRunning() {
        val gate = CompletableDeferred<Unit>()
        val stopping = CoroutineScope(Dispatchers.Unconfined).launch { withContext(NonCancellable) { gate.await() } }
        stopping.cancel()
        try {
            assertTrue(ConversionService.alreadyRunning("dup_scan", scan = stopping, update = null))
            assertTrue(ConversionService.alreadyRunning("update", scan = null, update = stopping))
        } finally {
            gate.complete(Unit)
        }
        assertFalse(ConversionService.alreadyRunning("update", scan = null, update = stopping))
    }

    @Test
    fun otherJobsAreNotHeldBack() {
        assertFalse(ConversionService.alreadyRunning("import", scan = running, update = running))
    }
}

package com.cripta.app.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Home resumes the waiting jobs once; what did not get through is tried again at the next unlock. */
class ResumeJobsTest {
    private var claims = 0
    private var releases = 0
    private var restores = 0

    private fun run(claimed: Boolean = true, restore: () -> Boolean) = runBlocking {
        resumeOnce({ claims++; claimed }, { releases++ }, { restores++; restore() })
    }

    @Test
    fun aCompleteResumeKeepsTheTurn() {
        run { true }
        assertEquals(listOf(1, 1, 0), listOf(claims, restores, releases))
    }

    @Test
    fun anIncompleteResumeGivesItBack() {
        run { false }
        assertEquals(1, releases)
    }

    @Test
    fun aFailureGivesItBackWithoutThrowing() {
        run { throw IllegalStateException("vault locked") }
        assertEquals(1, releases)
    }

    @Test
    fun aCancelGivesItBackAndStillCancels() {
        assertThrows(CancellationException::class.java) { run { throw CancellationException("lock") } }
        assertEquals(1, releases)
    }

    @Test
    fun withoutTheTurnNothingRuns() {
        run(claimed = false) { true }
        assertEquals(listOf(1, 0, 0), listOf(claims, restores, releases))
    }
}

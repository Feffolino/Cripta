package com.cripta.app.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TimelineSeekTest {
    @Test
    fun tapAndAccessibilityPositionsAreClampedToTheSameMediaBounds() {
        val sought = mutableListOf<Long>()
        for (position in listOf(-50.0, 0.0, 123.75, 2_000.0)) {
            assertTrue(seekTimeline(position, 1_000L) { sought += it })
        }
        assertEquals(listOf(0L, 0L, 123L, 1_000L), sought)
    }

    @Test
    fun unknownDurationNeverSeeks() {
        for (duration in listOf(0L, -1L)) {
            assertFalse(seekTimeline(100.0, duration) { fail("Unknown duration must not seek") })
        }
    }

    @Test
    fun nonFiniteAccessibilityValuesNeverReachThePlayer() {
        for (position in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(seekTimeline(position, 1_000L) { fail("Non-finite position must not seek") })
        }
    }
}

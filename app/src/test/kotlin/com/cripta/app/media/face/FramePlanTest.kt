package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePlanTest {
    /** The same moment found by the usual and the deep plan: frame indices differ, times are within [FramePlan.spacing]. */
    @Test
    fun theTwoPlansMeetWithinOneSpacing() {
        for (duration in listOf(30_000L, 80_000L, 300_000L, 7_200_000L)) {
            val usual = FramePlan.times(duration)
            val deep = FramePlan.times(duration, deep = true)
            val slack = FramePlan.spacing(duration)
            for (t in usual) assertTrue("$duration ms, $t", deep.any { kotlin.math.abs(it - t) <= slack })
        }
    }

    @Test
    fun anEightySecondVideoSamplesOtherFramesInTheDeepPlan() {
        // The report's case: 14–18 s is frame 1 (15 s) in the usual plan, frame 3 (17.5 s) in the deep one.
        val usual = FramePlan.times(80_000)
        val deep = FramePlan.times(80_000, deep = true)
        assertEquals(15_000L, usual[1])
        assertEquals(17_500L, deep[3])
        assertEquals(10_000L, FramePlan.spacing(80_000))
        assertEquals(FramePlan.STEP_MS, FramePlan.spacing(0))
    }
}

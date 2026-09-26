package com.cripta.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaEstimatorTest {

    /** Feeds [e] a job advancing [perSec] of progress per second, reported every [everyMs]. */
    private fun run(e: EtaEstimator, from: Float, perSec: Float, fromT: Long, untilT: Long, everyMs: Long = 500): Long? {
        var last: Long? = null
        var t = fromT
        while (t <= untilT) {
            last = e.update(from + perSec * (t - fromT) / 1000f, t)
            t += everyMs
        }
        return last
    }

    @Test
    fun steadyJobIsAccurate() {
        val e = EtaEstimator()
        // 1 % a second: at 30 s (30 %) there are 70 s left.
        val eta = run(e, 0f, 0.01f, 0, 30_000)
        assertNotNull(eta)
        assertTrue("eta $eta", eta!! in 63_000L..77_000L)
    }

    @Test
    fun silentWhileWarmingUp() {
        val e = EtaEstimator()
        assertNull(run(e, 0f, 0.01f, 0, 2_000))
    }

    @Test
    fun timeAtZeroBeforeStartingIsNotCounted() {
        val e = EtaEstimator()
        // 20 s at 0 (analysing the link), then 1 % a second.
        run(e, 0f, 0f, 0, 20_000)
        val eta = run(e, 0f, 0.01f, 20_500, 50_000)
        assertTrue("eta $eta", eta!! in 60_000L..80_000L)
    }

    @Test
    fun wholePercentStepsGiveAStableEstimate() {
        val e = EtaEstimator()
        // A 10-minute job reporting whole percents every 500 ms.
        val seen = mutableListOf<Long>()
        var t = 0L
        while (t <= 300_000) {
            val p = (t / 6_000).toInt() / 100f
            e.update(p, t)?.let { if (t > 60_000) seen += it + t }
            t += 500
        }
        // Predicted end time stays near 600 s the whole way.
        assertTrue(seen.isNotEmpty())
        assertTrue("ends ${seen.min()}..${seen.max()}", seen.all { it in 560_000L..650_000L })
    }

    @Test
    fun followsAChangeOfPace() {
        val e = EtaEstimator()
        // Fast to 50 % in 50 s, then 4x slower: 0.25 % a second, so 200 s for the rest.
        run(e, 0f, 0.01f, 0, 50_000)
        val eta = run(e, 0.5f, 0.0025f, 50_500, 110_000)
        // At 110 s: 65 % done, 140 s left.
        assertTrue("eta $eta", eta!! in 115_000L..165_000L)
    }

    @Test
    fun stallMakesTheEstimateGrow() {
        val e = EtaEstimator()
        val before = run(e, 0f, 0.01f, 0, 30_000)!!
        // Stuck at 30 % for a minute, still reporting.
        val after = run(e, 0.3f, 0f, 30_500, 90_000)!!
        assertTrue("before $before after $after", after > before)
    }

    @Test
    fun countsDownSteadily() {
        val e = EtaEstimator()
        run(e, 0f, 0.01f, 0, 30_000)
        val a = e.update(0.31f, 31_000)!!
        val b = e.update(0.32f, 32_000)!!
        // Nearly the same estimate: the shown value just goes down by the second.
        assertEquals(a - 1_000, b)
    }

    @Test
    fun progressGoingBackStartsOver() {
        val e = EtaEstimator()
        run(e, 0f, 0.01f, 0, 30_000)
        assertNull(e.update(0.05f, 31_000))
    }

    @Test
    fun formats() {
        assertEquals("quasi finito", formatEtaShort(9_000))
        assertEquals("ancora 15 s", formatEtaShort(11_000))
        assertEquals("ancora 1 min", formatEtaShort(57_000))
        assertEquals("ancora 2 min", formatEtaShort(61_000))
        assertEquals("ancora 1 h", formatEtaShort(3_599_000))
        assertEquals("ancora 1 h 21 min", formatEtaShort(4_830_000))
        assertEquals("circa 3 min rimanenti", formatEta(170_000))
        assertEquals("pochi secondi rimanenti", formatEta(2_000))
    }
}

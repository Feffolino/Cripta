package com.cripta.app.media.intro

import org.junit.Assert.assertTrue
import org.junit.Test

/** Two episodes whose audio is shifted by any fraction of a hash step must still match. */
class AlignmentTest {
    private fun noise(samples: Int, seed: Long): FloatArray {
        val r = java.util.Random(seed)
        return FloatArray(samples) { r.nextFloat() * 2 - 1 }
    }

    @Test
    fun introAtTheStartMatchesAtEveryShift() {
        val sr = MonoResampler.OUT_RATE
        val intro = noise(30 * sr, 9)
        for (shift in listOf(0, Fingerprint.HOP / 4, Fingerprint.HOP / 2, 3 * Fingerprint.HOP / 4)) {
            val a = Print("a", 0, Fingerprint.hashes(intro + noise(30 * sr, 1)))
            // b: the same intro, delayed by `shift` samples (encoder delay), then other content.
            val b = Print("b", 0, Fingerprint.hashes(FloatArray(shift) + intro + noise(30 * sr, 2)))
            val found = IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), emptyList())
            assertTrue("shift $shift", found.any { it.fileId == "a" && it.endMs - it.startMs > 25_000 })
        }
    }
}

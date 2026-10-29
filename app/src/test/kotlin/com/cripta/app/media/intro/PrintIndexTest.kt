package com.cripta.app.media.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintIndexTest {
    private fun noise(seconds: Double, seed: Long): FloatArray {
        val r = java.util.Random(seed)
        return FloatArray((seconds * MonoResampler.OUT_RATE).toInt()) { r.nextFloat() * 2 - 1 }
    }

    private val intro = noise(20.0, 9)

    private fun episode(id: String, coldOpen: Double, seed: Long, withIntro: Boolean = true): Print {
        val pcm = noise(coldOpen, seed) + (if (withIntro) intro else noise(20.0, seed + 50)) + noise(40.0, seed + 100)
        return Print(id, 0, Fingerprint.hashes(pcm))
    }

    @Test
    fun proposesOnlyVideosSharingAudio() {
        val a = episode("a", 5.0, 1)
        val b = episode("b", 7.3, 2)
        val others = (3L..8L).map { episode("x$it", 6.0, it, withIntro = false) }
        val index = PrintIndex()
        (listOf(a, b) + others).forEach { index.add(it.fileId, it.hashes) }

        val c = index.candidates(a.hashes)
        assertEquals(setOf("a", "b"), c.keys)
        // b's intro starts 2.3 s later than a's.
        val expected = (2_300 / Fingerprint.HOP_MS)
        assertEquals(expected, c.getValue("b").first().toDouble(), 2.0)
    }

    @Test
    fun detectAtFindsWhatDetectFinds() {
        val a = episode("a", 5.0, 1)
        val b = episode("b", 7.3, 2)
        val index = PrintIndex()
        listOf(a, b).forEach { index.add(it.fileId, it.hashes) }
        val cands = index.candidates(a.hashes).filterKeys { it != "a" }.map { (id, offs) -> (if (id == "b") b else a) to offs }

        val viaIndex = IntroDetector.detectAt(SegmentKind.INTRO, a, cands, emptyList())
        val direct = IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), emptyList())
        assertEquals(2, viaIndex.size)
        for (f in direct) {
            val g = viaIndex.single { it.fileId == f.fileId }
            assertTrue(kotlin.math.abs(g.startMs - f.startMs) <= 300 && kotlin.math.abs(g.endMs - f.endMs) <= 300)
        }
    }
}

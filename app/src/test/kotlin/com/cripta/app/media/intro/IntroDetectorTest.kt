package com.cripta.app.media.intro

import com.cripta.app.data.db.MediaSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroDetectorTest {
    private fun noise(seconds: Double, seed: Long): FloatArray {
        val r = java.util.Random(seed)
        return FloatArray((seconds * MonoResampler.OUT_RATE).toInt()) { r.nextFloat() * 2 - 1 }
    }

    private fun episode(id: String, coldOpen: Double, seed: Long, intro: FloatArray): Print {
        val pcm = noise(coldOpen, seed) + intro + noise(40.0, seed + 100)
        return Print(id, 0, Fingerprint.hashes(pcm))
    }

    private val intro = noise(20.0, 9)

    @Test
    fun findsTheSameIntroAtDifferentTimes() {
        val a = episode("a", 5.0, 1, intro)
        val b = episode("b", 7.3, 2, intro)
        val found = IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), rejected = emptyList())
        val fa = found.single { it.fileId == "a" }
        val fb = found.single { it.fileId == "b" }
        assertEquals(5_000.0, fa.startMs.toDouble(), 700.0)
        assertEquals(25_000.0, fa.endMs.toDouble(), 700.0)
        assertEquals(7_300.0, fb.startMs.toDouble(), 700.0)
        assertEquals(27_300.0, fb.endMs.toDouble(), 700.0)
    }

    @Test
    fun aStretchCutByTheWindowIsNoIntro() {
        // The same video twice (or an intro longer than the analysed minutes): shared up to the edge.
        val a = episode("a", 5.0, 1, intro)
        val copy = Print("copy", 0, a.hashes.copyOf())
        assertTrue(IntroDetector.detect(SegmentKind.INTRO, a, listOf(copy), emptyList()).isEmpty())
        val index = PrintIndex().apply { add(a.fileId, a.hashes); add(copy.fileId, copy.hashes) }
        val d = IntroDetector.detectAt(SegmentKind.INTRO, a, listOf(copy to index.candidates(a.hashes).getValue("copy")), emptyList())
        assertTrue(d.found.isEmpty())
        assertEquals(setOf("a", "copy"), d.cut)
    }

    @Test
    fun nothingWithoutSharedAudio() {
        val a = episode("a", 5.0, 1, noise(20.0, 10))
        val b = episode("b", 5.0, 2, noise(20.0, 11))
        assertTrue(IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), emptyList()).isEmpty())
    }

    @Test
    fun rejectedAudioIsIgnored() {
        val a = episode("a", 5.0, 1, intro)
        val b = episode("b", 7.3, 2, intro)
        val sample = a.slice(5_000, 25_000)
        assertTrue(IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), listOf(sample)).isEmpty())
    }

    @Test
    fun searchFindsAMarkedStretch() {
        val a = episode("a", 5.0, 1, intro)
        val b = episode("b", 7.3, 2, intro)
        val f = IntroDetector.search(a.slice(6_000, 22_000), listOf(b)).single()
        assertEquals("b", f.fileId)
        assertEquals(8_300.0, f.startMs.toDouble(), 700.0)
    }

    @Test
    fun storeRules() {
        val f = Found("x", 1_000, 21_000)
        fun seg(source: String, len: Long) = MediaSegmentEntity("x", "intro", 0, len, source)
        assertTrue(IntroDetector.shouldStore(null, f))
        assertTrue(IntroDetector.shouldStore(seg("auto", 10_000), f))
        assertFalse(IntroDetector.shouldStore(seg("auto", 30_000), f))
        assertFalse(IntroDetector.shouldStore(seg("manual", 5_000), f))
        assertFalse(IntroDetector.shouldStore(seg("none", 0), f))
        // Extended by a rule to 40 s, but its own audio is 10 s: a longer match still replaces it.
        val extended = MediaSegmentEntity("x", "intro", 0, 40_000, "auto", autoStartMs = 0, autoEndMs = 10_000)
        assertTrue(IntroDetector.shouldStore(extended, f))
    }

    @Test
    fun aRuleMovesTheBoundsAndKeepsTheSound() {
        val s = IntroDetector.segmentFor(SegmentKind.INTRO, Found("x", 1_000, 4_000), -500, 12_000, 60_000)
        assertEquals(500L, s.startMs)
        assertEquals(16_000L, s.endMs)
        assertEquals(1_000L, s.autoStartMs)
        assertEquals(4_000L, s.autoEndMs)
        // Inside the video, and without a rule nothing extra is stored.
        assertEquals(0L, IntroDetector.segmentFor(SegmentKind.INTRO, Found("x", 200, 4_000), -1_000, 0, 60_000).startMs)
        assertEquals(null, IntroDetector.segmentFor(SegmentKind.INTRO, Found("x", 1_000, 4_000), 0, 0, 60_000).autoStartMs)
    }

    private fun hashes(n: Int, seed: Long) = java.util.Random(seed).let { r -> IntArray(n) { r.nextInt() or 1 } }

    /** [h] with digital silence (hash 0) over [from] until [to]. */
    private fun silenced(h: IntArray, from: Int, to: Int) = h.copyOf().also { it.fill(0, from, to) }

    @Test
    fun twoCopiesWithDigitalSilenceInsideAreNoIntro() {
        // The same video twice, a few seconds of digital silence 6 minutes in: the shared stretch
        // stops before the window's edge, but the same alignment goes on to it after the silence.
        val head = silenced(hashes(9_000, 1), 5_700, 5_760)
        val a = Print("a", 0, head)
        val copy = Print("copy", 0, head.copyOf())
        assertTrue(IntroDetector.detect(SegmentKind.INTRO, a, listOf(copy), emptyList()).isEmpty())
        val tail = silenced(hashes(4_000, 2), 1_000, 1_060)
        assertTrue(IntroDetector.detect(SegmentKind.OUTRO, Print("a", 0, tail), listOf(Print("copy", 0, tail.copyOf())), emptyList()).isEmpty())
    }

    @Test
    fun anIntroWithSilenceInsideIsStillFound() {
        // An intro with a silent beat in it, then each video's own audio: not a copy.
        val intro = silenced(hashes(400, 3), 200, 230)
        val a = Print("a", 0, hashes(100, 4) + intro + hashes(2_000, 5))
        val b = Print("b", 0, hashes(150, 6) + intro + hashes(2_000, 7))
        val found = IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), emptyList())
        assertEquals(setOf("a", "b"), found.map { it.fileId }.toSet())
    }

    @Test
    fun manualMarksFollowANewVersionOnlyWithTheSameLength() {
        assertTrue(IntroDetector.carriesManual(1_200_000, 1_201_500))
        assertFalse(IntroDetector.carriesManual(1_200_000, 1_203_000))
        assertFalse(IntroDetector.carriesManual(null, 1_200_000))
    }
}

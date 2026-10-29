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
    }

    @Test
    fun manualMarksFollowANewVersionOnlyWithTheSameLength() {
        assertTrue(IntroDetector.carriesManual(1_200_000, 1_201_500))
        assertFalse(IntroDetector.carriesManual(1_200_000, 1_203_000))
        assertFalse(IntroDetector.carriesManual(null, 1_200_000))
    }
}

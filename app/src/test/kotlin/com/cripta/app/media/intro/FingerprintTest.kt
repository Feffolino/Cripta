package com.cripta.app.media.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
    private fun noise(seconds: Double, seed: Long, amp: Float = 1f): FloatArray {
        val r = java.util.Random(seed)
        return FloatArray((seconds * MonoResampler.OUT_RATE).toInt()) { (r.nextFloat() * 2 - 1) * amp }
    }

    /** Mean share of differing bits between two hash sequences (over the shorter one). */
    private fun ber(a: IntArray, b: IntArray): Double {
        val n = minOf(a.size, b.size)
        return (0 until n).sumOf { Integer.bitCount(a[it] xor b[it]) } / (32.0 * n)
    }

    @Test
    fun sameAudioSameHashes() {
        val a = Fingerprint.hashes(noise(20.0, 1))
        val b = Fingerprint.hashes(noise(20.0, 1))
        assertTrue(a.contentEquals(b))
        assertEquals((20 * MonoResampler.OUT_RATE - Fingerprint.FRAME) / Fingerprint.HOP, a.size)
    }

    @Test
    fun lightNoiseFlipsFewBits() {
        val clean = noise(20.0, 1)
        val extra = noise(20.0, 99, amp = 0.02f)
        val noisy = FloatArray(clean.size) { clean[it] + extra[it] }
        assertTrue(ber(Fingerprint.hashes(clean), Fingerprint.hashes(noisy)) < 0.2)
    }

    @Test
    fun offsetOfAQuarterHopStaysClose() {
        val src = noise(20.0, 1)
        val shifted = src.copyOfRange(Fingerprint.HOP / 4, src.size)
        assertTrue(ber(Fingerprint.hashes(src), Fingerprint.hashes(shifted)) < 0.3)
    }

    @Test
    fun differentAudioFlipsAboutHalf() {
        assertTrue(ber(Fingerprint.hashes(noise(20.0, 1)), Fingerprint.hashes(noise(20.0, 2))) > 0.35)
    }

    @Test
    fun silenceGivesZeroHashes() {
        assertTrue(Fingerprint.hashes(FloatArray(5 * MonoResampler.OUT_RATE)).all { it == 0 })
    }
}

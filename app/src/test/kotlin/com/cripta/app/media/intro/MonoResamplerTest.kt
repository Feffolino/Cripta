package com.cripta.app.media.intro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonoResamplerTest {
    @Test
    fun halvesTheRateByAveragingPairs() {
        val r = MonoResampler(MonoResampler.OUT_RATE * 2, channels = 1)
        r.push(floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f))
        assertArrayEquals(floatArrayOf(0.5f, 2.5f, 4.5f), r.result(), 1e-6f)
    }

    @Test
    fun averagesChannels() {
        val r = MonoResampler(MonoResampler.OUT_RATE, channels = 2)
        r.push(floatArrayOf(1f, 3f, -1f, -3f))
        assertArrayEquals(floatArrayOf(2f, -2f), r.result(), 1e-6f)
    }

    @Test
    fun oneSecondAt48kGivesOneSecondOut() {
        val r = MonoResampler(48_000, channels = 1)
        val chunk = FloatArray(4_800)
        repeat(10) { r.push(chunk) }
        assertTrue(kotlin.math.abs(r.size - MonoResampler.OUT_RATE) <= 1)
    }

    @Test
    fun codecRoundTrips() {
        val h = intArrayOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE, 123456789)
        assertArrayEquals(h, PrintCodec.decode(PrintCodec.encode(h)))
        assertEquals(24, PrintCodec.encode(h).size)
    }
}

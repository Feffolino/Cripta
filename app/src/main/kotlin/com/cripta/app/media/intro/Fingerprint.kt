package com.cripta.app.media.intro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Chromaprint-style audio fingerprint: one 32-bit hash every [HOP] samples of mono
 * [MonoResampler.OUT_RATE] Hz audio (about 16 per second: a coarser step misses the same audio
 * when it starts between two hashes). Bit b says whether the energy difference
 * between bands b and b+1 grew since the previous frame (Haitsma–Kalker). A re-encode of the same
 * audio flips few bits, different audio about half. Digital silence gives 0, which the matcher
 * never counts as a match.
 */
object Fingerprint {
    const val FRAME = 2048
    const val HOP = 345

    /** Milliseconds between consecutive hashes. */
    const val HOP_MS = HOP * 1000.0 / MonoResampler.OUT_RATE

    private const val BANDS = 33
    private const val MIN_HZ = 300.0
    private const val MAX_HZ = 2000.0

    private val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1)) }

    /** FFT bin edges of the log-spaced bands. */
    private val edges = IntArray(BANDS + 1) { b ->
        val hz = MIN_HZ * (MAX_HZ / MIN_HZ).pow(b.toDouble() / BANDS)
        (hz * FRAME / MonoResampler.OUT_RATE).roundToInt()
    }

    /** Hashes of the first [size] samples of [pcm]. */
    fun hashes(pcm: FloatArray, size: Int = pcm.size): IntArray {
        if (size < FRAME) return IntArray(0)
        val frames = (size - FRAME) / HOP + 1
        val out = IntArray(frames - 1)
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        var prev: DoubleArray? = null
        for (f in 0 until frames) {
            val off = f * HOP
            for (i in 0 until FRAME) { re[i] = pcm[off + i] * window[i]; im[i] = 0.0 }
            fft(re, im)
            val e = DoubleArray(BANDS) { b ->
                var s = 0.0
                for (k in edges[b] until maxOf(edges[b + 1], edges[b] + 1)) s += re[k] * re[k] + im[k] * im[k]
                s
            }
            val p = prev
            if (p != null) {
                var h = 0
                for (b in 0 until 32) if ((e[b] - e[b + 1]) - (p[b] - p[b + 1]) > 0) h = h or (1 shl b)
                out[f - 1] = h
            }
            prev = e
        }
        return out
    }

    /** In-place iterative radix-2 FFT; the size must be a power of two. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

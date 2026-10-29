package com.cripta.app.media.intro

import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Episodes built like real ones: music (notes with harmonics, drums), the same intro encoded two
 * ways (48 kHz vs 44.1 kHz, other gain, codec noise, encoder delay), through the app's own chain.
 * Prints what each step keeps, to see where a real match would get lost.
 */
class RealisticAudioTest {
    private fun music(seconds: Double, seed: Long, sr: Int = 48_000): FloatArray {
        val r = java.util.Random(seed)
        val out = FloatArray((seconds * sr).toInt())
        val noteLen = sr / 4
        var i = 0
        while (i < out.size) {
            val notes = List(3) { 440.0 * 2.0.pow((r.nextInt(36) - 21) / 12.0) }
            val drum = r.nextInt(2) == 0
            for (k in 0 until noteLen) {
                if (i + k >= out.size) break
                val t = k.toDouble() / sr
                var v = 0.0
                for (f in notes) for (h in 1..5) v += sin(2 * PI * f * h * (i + k) / sr) / h * exp(-3 * t)
                if (drum && t < 0.05) v += (r.nextDouble() * 2 - 1) * 2 * exp(-60 * t)
                v += (r.nextDouble() * 2 - 1) * 0.05
                out[i + k] = (v * 0.15).toFloat()
            }
            i += noteLen
        }
        return out
    }

    /** Another encode of [src] (48 kHz): resampled to [rate], gain, added noise, encoder delay. */
    private fun reencode(src: FloatArray, rate: Int, gain: Float, noiseDb: Double, delay: Int, seed: Long): FloatArray {
        val n = (src.size.toLong() * rate / 48_000).toInt()
        val r = java.util.Random(seed)
        val rms = sqrt(src.sumOf { (it * it).toDouble() } / src.size)
        val noise = rms * 10.0.pow(noiseDb / 20)
        val out = FloatArray(n + delay)
        for (j in 0 until n) {
            val x = j * 48_000.0 / rate
            val a = x.toInt().coerceAtMost(src.size - 2)
            val v = src[a] + (src[a + 1] - src[a]) * (x - a)
            out[j + delay] = (v * gain + (r.nextGaussian() * noise)).toFloat()
        }
        return out
    }

    private fun print(id: String, pcm: FloatArray, rate: Int): Print {
        val m = MonoResampler(rate, 1).apply { push(pcm) }
        return Print(id, 0, Fingerprint.hashes(m.buffer(), m.size))
    }

    @Test
    fun report() {
        for (introSec in listOf(20.0, 2.5)) {
            val intro = music(introSec, 9)
            for ((name, coldOpen) in listOf("all'inizio" to 0.0, "dopo 7,3 s" to 7.3)) {
                for ((label, enc) in listOf(
                    "stessa codifica" to Triple(48_000, 1.0f, -60.0),
                    "44,1 kHz, vol 0,7, -25 dB" to Triple(44_100, 0.7f, -25.0),
                    "44,1 kHz, vol 0,5, -15 dB" to Triple(44_100, 0.5f, -15.0),
                )) {
                    val a = print("a", intro + music(40.0, 1), 48_000)
                    val b = print("b", reencode(music(coldOpen, 3) + intro + music(40.0, 2), enc.first, enc.second, enc.third, 1024, 5), enc.first)
                    val m = SegmentMatcher.best(a.hashes, b.hashes, 1)
                    val off = m?.let { it.bStart - it.aStart } ?: 0
                    val introHashes = (introSec * 1000 / Fingerprint.HOP_MS).toInt()
                    val aligned = (0 until introHashes).filter { it + off in b.hashes.indices }
                    val ber = aligned.sumOf { Integer.bitCount(a.hashes[it] xor b.hashes[it + off]) } / (32.0 * aligned.size)
                    val index = PrintIndex().apply { add(a.fileId, a.hashes); add(b.fileId, b.hashes) }
                    val cands = index.candidates(a.hashes)
                    val found = cands["b"]?.let { IntroDetector.detectAt(SegmentKind.INTRO, a, listOf(b to it), emptyList()).found }
                        ?.firstOrNull { it.fileId == "a" }
                    println("[sigla ${introSec}s $name | $label] bit diversi %.0f%%, indice: %s, trovata: %s".format(
                        ber * 100, if ("b" in cands) "sì" else "NO", found?.let { "${it.startMs}-${it.endMs} ms" } ?: "NO"))
                    org.junit.Assert.assertTrue("sigla $introSec $name | $label",
                        found != null && found.endMs - found.startMs > introSec * 1000 * 0.8)
                }
            }
        }
    }

    /** The looser thresholds must not make different videos (same style of music) look alike. */
    @Test
    fun differentVideosShareNothing() {
        val prints = (1L..12L).map { s -> print("v$s", music(60.0, s * 31), 48_000) }
        val index = PrintIndex().apply { prints.forEach { add(it.fileId, it.hashes) } }
        for (p in prints) {
            val cands = index.candidates(p.hashes).filterKeys { it != p.fileId }
                .map { (id, offs) -> prints.first { it.fileId == id } to offs }
            val found = IntroDetector.detectAt(SegmentKind.INTRO, p, cands, emptyList()).found
            org.junit.Assert.assertTrue("${p.fileId}: $found", found.isEmpty())
        }
    }
}

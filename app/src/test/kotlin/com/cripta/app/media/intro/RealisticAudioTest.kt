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
        val intro = music(20.0, 9)
        for ((name, coldOpen) in listOf("intro all'inizio" to 0.0, "intro dopo 7,3 s" to 7.3)) {
            for ((label, enc) in listOf(
                "stessa codifica" to Triple(48_000, 1.0f, -60.0),
                "44,1 kHz, volume 0,7, rumore -35 dB" to Triple(44_100, 0.7f, -35.0),
                "44,1 kHz, volume 0,7, rumore -25 dB" to Triple(44_100, 0.7f, -25.0),
            )) {
                val a48 = intro + music(40.0, 1)
                val b48 = music(coldOpen, 3) + intro + music(40.0, 2)
                val a = print("a", a48, 48_000)
                val b = print("b", reencode(b48, enc.first, enc.second, enc.third, 1024, 5), enc.first)
                val m = SegmentMatcher.best(a.hashes, b.hashes, 1)
                val off = m?.let { it.bStart - it.aStart } ?: 0
                val introHashes = (20_000 / Fingerprint.HOP_MS).toInt()
                val aligned = (0 until introHashes).filter { it + off in b.hashes.indices }
                val ber = aligned.sumOf { Integer.bitCount(a.hashes[it] xor b.hashes[it + off]) } / (32.0 * aligned.size)
                val exact20 = aligned.count { (a.hashes[it] ushr 12) == (b.hashes[it + off] ushr 12) } / aligned.size.toDouble()
                val index = PrintIndex().apply { add(a.fileId, a.hashes); add(b.fileId, b.hashes) }
                val cands = index.candidates(a.hashes)
                val viaIndex = cands["b"]?.let { IntroDetector.detectAt(SegmentKind.INTRO, a, listOf(b to it), emptyList()).found } ?: emptyList()
                println("[$name | $label] bit diversi %.0f%%, top20 uguali %.0f%%, comune %.1f s, indice: %s, trovata: %s".format(
                    ber * 100, exact20 * 100, (m?.length ?: 0) * Fingerprint.HOP_MS / 1000,
                    if ("b" in cands) "sì" else "NO",
                    viaIndex.firstOrNull { it.fileId == "a" }?.let { "${it.startMs}-${it.endMs} ms" } ?: "NO"))
                org.junit.Assert.assertTrue("$name | $label", viaIndex.any { it.fileId == "a" && it.endMs - it.startMs > 18_000 })
            }
        }
    }
}

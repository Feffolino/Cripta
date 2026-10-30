package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

class FaceClustersTest {
    private val rnd = Random(7)

    private fun unit(v: FloatArray): FloatArray { val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat(); return FloatArray(v.size) { v[it] / n } }
    private fun random() = unit(FloatArray(128) { rnd.nextGaussian().toFloat() })
    /** A face of the same person as [base]: similarity about [s]. */
    private fun near(base: FloatArray, s: Float): FloatArray {
        val r = random()
        val d = similarity(r, base)
        val ortho = unit(FloatArray(128) { r[it] - d * base[it] })
        val k = sqrt(1 - s * s)
        return unit(FloatArray(128) { s * base[it] + k * ortho[it] })
    }
    private fun track(id: Long, file: String, vararg e: FloatArray, frames: Long = 1L) = TrackInfo(id, file, frames, e.toList(), 0.5f)

    @Test
    fun framePlanSpreadsTheFrames() {
        assertEquals(8, FramePlan.times(30_000).size)
        assertEquals(30, FramePlan.times(300_000).size)
        assertEquals(40, FramePlan.times(7_200_000).size)
        val t = FramePlan.times(80_000)
        assertEquals(5_000L, t.first()); assertEquals(75_000L, t.last())
    }

    @Test
    fun codecKeepsTheSimilarity() {
        val a = random(); val b = near(a, 0.5f)
        val (da, db) = EmbeddingCodec.decode(EmbeddingCodec.encode(listOf(a, b)))
        assertEquals(similarity(a, b), similarity(da, db), 0.005f)
    }

    @Test
    fun trackKeepsTheMeanOfItsFacesAndEveryMoment() {
        val a = random()
        val d = TrackDraft(listOf(Sighting(0, 1_000, near(a, 0.8f), 0.9f), Sighting(1, 11_000, near(a, 0.8f), 0.5f), Sighting(2, 21_000, near(a, 0.8f), 0.4f)))
        assertEquals(listOf(1_000L, 11_000L, 21_000L), d.times)
        assertEquals(3, d.embeddings.size)
        // The mean is closer to the person than any single noisy face.
        assertTrue(similarity(d.embeddings[0], a) > d.sightings.maxOf { similarity(it.embedding, a) })
    }

    @Test
    fun repeatedKeyframesAreRecognised() {
        val px = IntArray(64 * 48) { if (it % 7 == 0) 0xFFFFFFFF.toInt() else 0xFF202020.toInt() }
        val img = FaceImage(64, 48, px)
        val other = FaceImage(64, 48, IntArray(64 * 48) { if (it < 1500) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() })
        assertTrue(FaceStats.sameFrame(FaceStats.frameHash(img), FaceStats.frameHash(FaceImage(64, 48, px.copyOf()))))
        assertTrue(!FaceStats.sameFrame(FaceStats.frameHash(img), FaceStats.frameHash(other)))
        assertEquals(FaceStats.SHARP_BANDS.size, FaceStats.band(500.0))
        assertEquals(1, FaceStats.parse(FaceStats.encode(intArrayOf(0, 1, 2, 3, 4, 5), 2, 3)).sharp[1])
    }

    @Test
    fun trackerSplitsFacesOfTheSameFrame() {
        val a = random(); val b = near(a, 0.6f)
        val tracks = FaceTracker.build(listOf(Sighting(0, 0, a, 0.9f), Sighting(0, 0, b, 0.8f), Sighting(1, 10_000, near(a, 0.8f), 0.7f)))
        assertEquals(2, tracks.size)
        assertEquals(0b11L, tracks.first { it.sightings.size == 2 }.frames)
    }
}

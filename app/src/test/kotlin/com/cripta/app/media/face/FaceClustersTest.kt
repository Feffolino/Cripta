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

    @Test
    fun tracksOfOnePersonThatShareNoFrameAreJoined() {
        val a = random(); val other = random()
        // Alike means, no frame in common: one person; the stranger stays apart.
        assertEquals(listOf(listOf(0, 1), listOf(2)),
            FaceTracker.groups(listOf(0b001L, 0b010L, 0b100L), listOf(a, near(a, 0.5f), other), listOf(3, 2, 1)))
        // The same frame: two people, however alike.
        assertEquals(listOf(listOf(0), listOf(1)), FaceTracker.groups(listOf(1L, 1L), listOf(a, near(a, 0.9f)), listOf(1, 1)))
        // Below the bar: kept apart.
        assertEquals(2, FaceTracker.groups(listOf(1L, 2L), listOf(a, near(a, FaceTracker.JOIN - 0.1f)), listOf(1, 1)).size)
    }

    @Test
    fun aPersonSplitByTheLightBecomesOneTrack() {
        val a = random()
        val dark = near(a, 0.7f); val light = near(a, 0.7f)
        // Each look alike within itself but less than SAME to the other's faces.
        val faces = (0 until 4).map { Sighting(it, it * 10_000L, near(dark, 0.85f), 0.9f - it * 0.01f) } +
            (4 until 8).map { Sighting(it, it * 10_000L, near(light, 0.85f), 0.8f - it * 0.01f) }
        val tracks = FaceTracker.build(faces)
        assertEquals(1, tracks.size)
        assertEquals((0 until 8).map { it * 10_000L }, tracks.single().times)
    }

    @Test
    fun combineWeighsEachMeanByItsFaces() {
        val a = random(); val b = random()
        val joined = FaceTracker.combine(listOf(FaceTracker.Part(listOf(a, a, a), 9, 0.5f), FaceTracker.Part(listOf(b), 1, 0.9f)))
        assertEquals(FaceTracker.KEPT, joined.size)
        assertTrue(similarity(joined[0], a) > similarity(joined[0], b))
        assertEquals(1f, similarity(joined[0], joined[0]), 1e-4f)
        // The best face first after the mean: the single face of the sharper track.
        assertTrue(joined[1].contentEquals(b))
    }

    @Test
    fun trackerNeverJoinsTwoPeopleOfTheTestPhotos() {
        val who = HashMap<Sighting, String>()
        TestFaces.names().forEachIndexed { i, name ->
            for (f in TestFaces.engine.analyze(TestFaces.image(name))) {
                val id = TestFaces.who(name, f.det) ?: continue
                who[Sighting(i, i * 1_000L, f.embedding ?: continue, f.quality)] = id
            }
        }
        val list = who.keys.toList()
        val same = ArrayList<Float>(); val diff = ArrayList<Float>()
        for (x in list.indices) for (y in x + 1 until list.size)
            (if (who[list[x]] == who[list[y]]) same else diff) += similarity(list[x].embedding, list[y].embedding)
        println("faces ${list.size}; same person, lowest: ${same.minOrNull()}; different people, highest: ${diff.maxOrNull()}")
        for (t in FaceTracker.build(list)) assertEquals(1, t.sightings.map { who[it] }.distinct().size)
    }

    @Test
    fun storedFacesKeepWhatASplitNeeds() {
        val a = random()
        val faces = listOf(StoredFace(3, 12_500L, 0.7f, 88, floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), a),
            StoredFace(41, 3_600_000L, 0.2f, 40, floatArrayOf(0f, 0.5f, 1f, 0.25f), near(a, 0.3f)))
        val back = FaceCodec.decode(FaceCodec.encode(faces))
        assertEquals(2, back.size)
        assertEquals(41, back[1].frame); assertEquals(3_600_000L, back[1].timeMs); assertEquals(40, back[1].width)
        assertEquals(0.7f, back[0].quality, 1e-6f)
        assertEquals(0.3f, back[0].box[2], 1e-4f)
        assertEquals(1f, similarity(back[0].embedding, a), 0.005f)
        assertTrue(FaceCodec.decode(ByteArray(0)).isEmpty())
        assertTrue(FaceCodec.decode(byteArrayOf(9, 1, 2)).isEmpty())
    }

    @Test
    fun clusterGroupsAlikeFacesOfDifferentMedia() {
        val a = random(); val b = random()
        val means = listOf(near(a, 0.8f), near(b, 0.8f), near(a, 0.8f), near(a, 0.8f), random())
        val groups = FaceTracker.cluster(means, 0.45f)
        assertEquals(listOf(0, 2, 3), groups.first { 0 in it })
        assertEquals(3, groups.size)
    }
}

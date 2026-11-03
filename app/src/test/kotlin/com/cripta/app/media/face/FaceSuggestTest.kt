package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

class FaceSuggestTest {
    private val rnd = Random(7)

    private fun unit(v: FloatArray): FloatArray { val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat(); return FloatArray(v.size) { v[it] / n } }
    private fun random() = unit(FloatArray(128) { rnd.nextGaussian().toFloat() })
    /** A face whose similarity with [base] is [s]. */
    private fun near(base: FloatArray, s: Float): FloatArray {
        val r = random()
        val d = similarity(r, base)
        val ortho = unit(FloatArray(128) { r[it] - d * base[it] })
        val k = sqrt(1 - s * s)
        return unit(FloatArray(128) { s * base[it] + k * ortho[it] })
    }

    private fun candidate(id: Long, e: FloatArray, media: String = "m$id", frames: Long = 1L shl (id % 60).toInt(), uncertain: Boolean = false) =
        FaceSuggest.Candidate(id, media, frames, listOf(e), uncertain)

    /** The old score: the mean of the three best matches among all the person's faces (or the best, a little less). */
    private fun oldScore(own: List<FloatArray>, e: FloatArray): Float {
        val sims = own.map { similarity(it, e) }.sortedDescending()
        return maxOf(sims.take(3).average().toFloat(), sims.first() - 0.04f)
    }

    @Test
    fun aStrangerWhoMatchesAFewMediaByLuckIsNotOfferedOnceThePersonHasMany() {
        val me = random()
        val stranger = random()
        // The person in 40 media; in three of them the face happens to look like the stranger
        // (light, angle): the old score offered the stranger, and more media only meant more such luck.
        val media = (0 until 40).associate { i ->
            "p$i" to listOf(if (i < 3) near(stranger, 0.45f) else near(me, 0.7f))
        }
        val own = media.values.flatten()
        assertTrue(oldScore(own, stranger) >= 0.36f)
        val picked = FaceSuggest.pick(media, listOf(candidate(1, stranger)))
        assertTrue(picked.isEmpty())
        // The person themself still is.
        assertEquals(listOf(2L), FaceSuggest.pick(media, listOf(candidate(2, near(me, 0.7f)))).map { it.first.id })
    }

    @Test
    fun theBarRisesWithThePersonsMedia() {
        assertEquals(FaceSuggest.BASE, FaceSuggest.threshold(1), 1e-6f)
        assertTrue(FaceSuggest.threshold(4) > FaceSuggest.threshold(2))
        assertTrue(FaceSuggest.threshold(16) >= FaceSuggest.threshold(4))
        assertTrue(FaceSuggest.threshold(1000) <= FaceSuggest.BASE + 0.05f + 1e-6f)
        assertEquals(1, FaceSuggest.agreeing(1))
        assertEquals(1, FaceSuggest.agreeing(7))
        assertEquals(2, FaceSuggest.agreeing(8))
        assertEquals(38, FaceSuggest.agreeing(300))
    }

    @Test
    fun moreConfirmedMediaNeverOfferMoreStrangers() {
        val me = random()
        // Strangers a little like the person (same age, same light): each of the person's faces gives them a chance.
        val strangers = (0 until 300).map { near(me, 0.3f) }
        val media = (0 until 60).associate { i -> "p$i" to listOf(near(me, 0.55f)) }
        fun offered(n: Int) = FaceSuggest.pick(media.entries.take(n).associate { it.key to it.value },
            strangers.mapIndexed { i, s -> candidate(i.toLong(), s) }).size
        fun offeredBefore(n: Int) = strangers.count { s -> oldScore(media.values.take(n).flatten(), s) >= 0.36f }
        // Before: the more media, the more strangers offered.
        assertTrue(offeredBefore(60) > offeredBefore(2))
        val few = offered(2)
        val many = offered(60)
        assertTrue("$many > $few", many <= few)
    }

    @Test
    fun facesTheCloserLookJoinsInOneMediaDoNotWidenTheSuggestions() {
        val me = random()
        val stranger = random()
        val before = mapOf("v1" to listOf(near(me, 0.7f)), "v2" to listOf(near(me, 0.7f)))
        // The closer look of v1 joined five stray faces into the person's there, one a little like the stranger.
        val after = mapOf("v1" to before.getValue("v1") + List(4) { near(me, 0.6f) } + near(stranger, 0.3f), "v2" to before.getValue("v2"))
        val c = candidate(1, stranger)
        assertEquals(FaceSuggest.pick(before, listOf(c)).size, FaceSuggest.pick(after, listOf(c)).size)
        assertTrue(FaceSuggest.score(after.values.toList(), listOf(stranger)) < FaceSuggest.threshold(2))
    }

    @Test
    fun facesOfThePersonsOwnMediaAreNeverOffered() {
        // Why the closer look changed nothing in the suggestions: the faces it judges are those of
        // the very videos where the person is confirmed, which the suggestions never offer.
        val me = random()
        val media = mapOf("v1" to listOf(me))
        val inOwnVideo = candidate(1, near(me, 0.9f), media = "v1")
        assertTrue(FaceSuggest.pick(media, listOf(inOwnVideo)).isEmpty())
        // A face linked by hand ("own media" without a face) is left out too.
        val linked = candidate(2, near(me, 0.9f), media = "photo")
        assertTrue(FaceSuggest.pick(media, listOf(linked), ownMedia = setOf("v1", "photo")).isEmpty())
    }

    @Test
    fun aFaceLikeOneTheUserRefusedIsNotOffered() {
        val me = random()
        val lookalike = random()
        val media = mapOf("p1" to listOf(near(me, 0.8f)), "p2" to listOf(near(lookalike, 0.65f)))
        val again = near(lookalike, 0.7f)
        assertEquals(1, FaceSuggest.pick(media, listOf(candidate(1, again))).size)
        assertTrue(FaceSuggest.pick(media, listOf(candidate(1, again)), negatives = listOf(lookalike)).isEmpty())
        // The person themself, a little like the refused face, still is.
        val mine = near(me, 0.75f)
        assertEquals(1, FaceSuggest.pick(media, listOf(candidate(2, mine)), negatives = listOf(lookalike)).size)
    }

    @Test
    fun twoFacesOfOneFrameOrPhotoAreNotBothOffered() {
        val me = random()
        val media = mapOf("p1" to listOf(me))
        val a = candidate(1, near(me, 0.8f), media = "v", frames = 0b011)
        val b = candidate(2, near(me, 0.6f), media = "v", frames = 0b110)
        val c = candidate(3, near(me, 0.6f), media = "v", frames = 0b1000)
        assertEquals(listOf(1L, 3L), FaceSuggest.pick(media, listOf(b, c, a)).map { it.first.id })
        val photo = listOf(candidate(4, near(me, 0.7f), media = "ph", frames = 0), candidate(5, near(me, 0.8f), media = "ph", frames = 0))
        assertEquals(listOf(5L), FaceSuggest.pick(media, photo).map { it.first.id })
    }

    @Test
    fun aBlurryFaceNeedsMore() {
        val me = random()
        val media = mapOf("p1" to listOf(me))
        val e = near(me, 0.39f)
        assertEquals(1, FaceSuggest.pick(media, listOf(candidate(1, e))).size)
        assertTrue(FaceSuggest.pick(media, listOf(candidate(1, e, uncertain = true))).isEmpty())
    }
}

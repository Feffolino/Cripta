package com.cripta.app.media.face

import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.face.FaceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

class FaceFollowerTest {
    private val rnd = Random(11)

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

    private val left = floatArrayOf(0.1f, 0.2f, 0.2f, 0.3f)
    private val right = floatArrayOf(0.6f, 0.2f, 0.2f, 0.3f)
    private fun seen(box: FloatArray, e: FloatArray?, usable: Boolean = true) = SeenFace(box, e, usable)
    private fun frame(t: Long, vararg faces: SeenFace, real: Long? = t) = SeenFrame(t, real, faces.toList())
    private fun stored(t: Long, box: FloatArray, e: FloatArray, frame: Int = 0) = StoredFace(frame, t, 0.8f, 80, box, e)

    @Test
    fun aFaceAtThePersonsPlaceInEveryMomentJoinsThem() {
        val me = random()
        val known = listOf(KnownHere(1, listOf(me), frames = 0b1))
        // The stray face looks only fairly like the confirmed one (another angle): at its moment the
        // closer reading sees the person clearly at the same place.
        val loose = LooseFace(9, 0b10, listOf(stored(5_000, left, near(me, 0.3f))))
        val frames = listOf(frame(4_000, seen(left, near(me, 0.6f))), frame(5_000, seen(left, near(me, 0.55f))))
        val labels = FaceFollower.label(frames, known)
        assertEquals(FaceFollower.Verdict.Join(1), FaceFollower.judge(loose, frames, labels, known))
    }

    @Test
    fun aFaceFollowedOnlyByPlaceIsNotSureEnough() {
        val me = random()
        val known = listOf(KnownHere(1, listOf(me), frames = 0b1))
        // Found by likeness at 4 s, followed by place to 5 s (a blurred turn of the head): never by likeness at its own moment.
        val loose = LooseFace(9, 0b10, listOf(stored(5_000, left, near(me, 0.3f))))
        val frames = listOf(frame(4_000, seen(left, near(me, 0.6f))), frame(5_000, seen(left, near(me, 0.32f))))
        val labels = FaceFollower.label(frames, known)
        assertEquals(0, labels.who[1][0])
        assertEquals(FaceFollower.Verdict.Keep, FaceFollower.judge(loose, frames, labels, known))
    }

    @Test
    fun anotherFaceBesideThePersonIsSomeoneElse() {
        val me = random(); val other = random()
        val known = listOf(KnownHere(1, listOf(me), frames = 0b1))
        val loose = LooseFace(9, 0b10, listOf(stored(5_000, right, other)))
        val frames = listOf(frame(5_000, seen(left, near(me, 0.7f)), seen(right, near(other, 0.8f))))
        val labels = FaceFollower.label(frames, known)
        assertEquals(FaceFollower.Verdict.Keep, FaceFollower.judge(loose, frames, labels, known))
    }

    @Test
    fun neverTwoFacesOfOneFrame() {
        val me = random()
        // The stray shares a sampled frame with the confirmed face: two faces at once, never one person.
        val known = listOf(KnownHere(1, listOf(me), frames = 0b11))
        val loose = LooseFace(9, 0b10, listOf(stored(5_000, left, near(me, 0.6f))))
        val frames = listOf(frame(5_000, seen(left, near(me, 0.7f))))
        assertEquals(FaceFollower.Verdict.Keep, FaceFollower.judge(loose, frames, FaceFollower.label(frames, known), known))
    }

    @Test
    fun oneMomentOfSomeoneElseKeepsTheFaceApart() {
        val me = random(); val other = random()
        val known = listOf(KnownHere(1, listOf(me), frames = 0b1))
        val loose = LooseFace(9, 0b110, listOf(stored(5_000, left, near(me, 0.5f), 1), stored(9_000, right, other, 2)))
        val frames = listOf(frame(5_000, seen(left, near(me, 0.7f))),
            frame(9_000, seen(left, near(me, 0.7f)), seen(right, near(other, 0.8f))))
        assertEquals(FaceFollower.Verdict.Keep, FaceFollower.judge(loose, frames, FaceFollower.label(frames, known), known))
    }

    @Test
    fun twoConfirmedPeopleAreNeverMixed() {
        val a = random(); val b = random()
        val known = listOf(KnownHere(1, listOf(a), 0b1), KnownHere(2, listOf(b), 0b1))
        val frames = listOf(frame(5_000, seen(left, near(a, 0.7f)), seen(right, near(b, 0.7f))))
        val labels = FaceFollower.label(frames, known)
        assertEquals(0, labels.who[0][0]); assertEquals(1, labels.who[0][1])
        val loose = LooseFace(9, 0b10, listOf(stored(5_000, right, near(b, 0.4f))))
        assertEquals(FaceFollower.Verdict.Join(2), FaceFollower.judge(loose, frames, labels, known))
    }

    @Test
    fun theKeyFrameTheAnalysisSawCountsToo() {
        val me = random()
        val known = listOf(KnownHere(1, listOf(me), frames = 0b1))
        // Asked at 5 s, the analysis got the key frame at 4 s (face on the left); the exact frame at
        // 5 s shows the face moved to the right. Both are read: the key frame matches.
        val loose = LooseFace(9, 0b10, listOf(stored(5_000, left, near(me, 0.3f))))
        val frames = listOf(frame(5_000, seen(left, near(me, 0.6f)), real = 4_000), frame(5_000, seen(right, near(me, 0.6f))))
        val labels = FaceFollower.label(frames, known)
        assertEquals(FaceFollower.Verdict.Join(1), FaceFollower.judge(loose, frames, labels, known))
    }

    @Test
    fun faceNotFoundAgainIsSetAside() {
        val me = random(); val ghost = random()
        val known = listOf(KnownHere(1, listOf(me), frames = 0b1))
        val loose = LooseFace(9, 0b10, listOf(stored(5_000, right, ghost)))
        val frames = listOf(frame(5_000, seen(left, null, usable = false)))
        assertEquals(FaceFollower.Verdict.Aside, FaceFollower.judge(loose, frames, FaceFollower.label(frames, known), known))
        // Blurred where it was: set aside too. A clear unknown face alone: left as it is.
        val blurred = listOf(frame(5_000, seen(right, ghost, usable = false)))
        assertEquals(FaceFollower.Verdict.Aside, FaceFollower.judge(loose, blurred, FaceFollower.label(blurred, known), known))
        val clear = listOf(frame(5_000, seen(right, ghost)))
        assertEquals(FaceFollower.Verdict.Keep, FaceFollower.judge(loose, clear, FaceFollower.label(clear, known), known))
        // Not read at all: nothing is concluded.
        assertEquals(FaceFollower.Verdict.Keep, FaceFollower.judge(loose, emptyList(), FaceFollower.label(emptyList(), known), known))
    }

    @Test
    fun planKeepsTheMomentsToJudgeAndSpreadsTheRest() {
        val p = FaceFollower.plan(60_000, listOf(10_000L, 30_000L))
        assertTrue(10_000L in p && 30_000L in p && 9_000L in p && 31_000L in p)
        assertEquals(p.sorted(), p)
        assertTrue(p.size in 60..70)
        assertTrue(FaceFollower.plan(7_200_000, (0 until 400).map { it * 10_000L }).size <= 200)
        assertTrue(FaceFollower.plan(0, listOf(5L)).isEmpty())
    }

    @Test
    fun refineResultIsKeptWithTheScanStats() {
        val s = FaceStats.encode(IntArray(FaceStats.SHARP_BANDS.size + 1), 2, 3)
        assertNull(FaceStats.refineOf(s))
        val r = FaceStats.withRefine(s, FaceStats.Refine("4:1,7:2", 2, 3, 118))
        val back = FaceStats.refineOf(r)!!
        assertEquals("4:1,7:2", back.signature); assertEquals(2, back.joined); assertEquals(3, back.aside); assertEquals(118, back.frames)
        assertEquals(2, FaceStats.parse(r).duplicates)
        // Looked at again: replaced, not added.
        assertEquals(1, FaceStats.withRefine(r, FaceStats.Refine("4:1", 0, 0, 5)).split(';').count { it.startsWith("refined=") })
    }

    @Test
    fun facesSetAsideFoldWithTheUnclearOnes() {
        val card = TrackCard(1, "f", 0, 80, 6, null, FaceRepository.ASIDE, 0f, quality = 0.9f)
        assertTrue(FaceRepository.uncertain(card, video = true))
    }

    @Test
    fun aFaceAddedByHandHasNoSampledFrame() {
        val e = random()
        val d = TrackDraft(listOf(Sighting(-1, 3_000, e, 0.7f), Sighting(4, 5_000, e, 0.6f)))
        assertEquals(1L shl 4, d.frames)
        assertEquals(0L, TrackDraft(listOf(Sighting(-1, 3_000, e, 0.7f))).frames)
    }

    @Test
    fun sampledFramesFitTheTrackMask() {
        // Bit n of a track's frames is frame n: the plan and the frames added near faces stay below 64.
        assertTrue(FramePlan.MAX + FaceRepository.MAX_EXTRA <= 64)
    }
}

package com.cripta.app.data.intro

import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.media.face.FaceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroLogicTest {
    private val head = 300_000L

    @Test fun unknownDurationWithAShortQuickPrintIsNotEnough() {
        // Quick scan covered 60 s of a video whose length was never stored: not "nothing to cover".
        assertTrue(coverageIsShort(null, 60_000, head))
        assertTrue(coverageIsShort(0, 60_000, head))
    }

    @Test fun unknownDurationWithAFullHeadIsEnough() {
        assertFalse(coverageIsShort(null, head, head))
    }

    @Test fun knownDurationKeepsItsOldBehaviour() {
        assertFalse(coverageIsShort(100_000, 80_000, head))   // short video, mostly covered
        assertTrue(coverageIsShort(100_000, 20_000, head))
        assertFalse(coverageIsShort(2_000_000, 280_000, head))
        assertTrue(coverageIsShort(2_000_000, 100_000, head))
    }

    private fun seg(kind: String, source: String, start: Long = 0, end: Long = 0) =
        MediaSegmentEntity("f", kind, start, end, source)

    @Test fun aRejectedMarkerIsNotAnIntro() {
        val none = seg("intro", "none")
        assertNull(activeSegment(listOf(none), "intro"))
    }

    @Test fun anAutoSegmentIsFoundAndOtherKindsAreIgnored() {
        val auto = seg("intro", "auto", 1_000, 30_000)
        val outro = seg("outro", "auto", 90_000, 100_000)
        assertEquals(auto, activeSegment(listOf(seg("intro", "none"), auto, outro).filter { it === auto || it === outro }, "intro"))
        assertEquals(outro, activeSegment(listOf(auto, outro), "outro"))
    }

    @Test fun anAlikeFrameFarInTimeIsNotADuplicate() {
        val h = 0x0F0F0F0F0F0F0F0FL
        // Same picture, key frame returned twice at the same real time: a duplicate.
        assertTrue(FaceStats.isSameMoment(h, h, 10_000, 10_000))
        // Same picture hash 8 seconds later (fixed camera, a small face came in): a different frame.
        assertFalse(FaceStats.isSameMoment(h, h, 18_000, 10_000))
        // The first frame has nothing before it.
        assertFalse(FaceStats.isSameMoment(h, h, 10_000, null))
        // A different picture is never a duplicate.
        assertFalse(FaceStats.isSameMoment(h, h.inv(), 10_000, 10_000))
    }
}

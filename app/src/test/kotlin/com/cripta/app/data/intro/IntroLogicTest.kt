package com.cripta.app.data.intro

import com.cripta.app.data.db.MediaSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroLogicTest {
    private val head = 300_000L
    private val quick = 60_000L

    @Test fun unknownDurationWithAShortQuickPrintIsNotEnough() {
        // Quick scan covered 60 s of a video whose length was never stored: not "nothing to cover".
        assertTrue(coverageIsShort(null, 60_000, head, quick))
        assertTrue(coverageIsShort(0, 60_000, head, quick))
    }

    @Test fun unknownDurationWithAFullHeadIsEnough() {
        assertFalse(coverageIsShort(null, head, head, quick))
    }

    @Test fun knownDurationKeepsItsOldBehaviour() {
        assertFalse(coverageIsShort(100_000, 80_000, head, quick))   // short video, mostly covered
        assertTrue(coverageIsShort(100_000, 20_000, head, quick))
        assertFalse(coverageIsShort(2_000_000, 280_000, head, quick))
        assertTrue(coverageIsShort(2_000_000, 60_000, head, quick))
    }

    @Test fun audioShorterThanTheVideoIsNotAnalysedAgain() {
        // 30 minutes of video, 2 of audio: the complete scan read all there was to read.
        assertFalse(coverageIsShort(1_800_000, 120_000, head, quick))
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
        // The "none" mark comes first: it must be passed over, not returned.
        assertEquals(auto, activeSegment(listOf(seg("intro", "none"), auto, outro), "intro"))
        assertEquals(outro, activeSegment(listOf(auto, outro), "outro"))
    }
}

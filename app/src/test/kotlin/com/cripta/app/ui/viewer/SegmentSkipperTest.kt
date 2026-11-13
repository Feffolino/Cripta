package com.cripta.app.ui.viewer

import com.cripta.app.ui.viewer.SegmentSkipper.Action
import com.cripta.app.ui.viewer.SegmentSkipper.Kind
import com.cripta.app.ui.viewer.SegmentSkipper.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentSkipperTest {
    private val intro = Segment(Kind.INTRO, 10_000, 40_000)
    private val outro = Segment(Kind.OUTRO, 1_000_000, 1_060_000)
    private val segs = listOf(intro, outro)

    @Test
    fun showsTheButtonInsideASegmentOnly() {
        val s = SegmentSkipper()
        assertEquals(Action.None, s.at(5_000, segs, false, false, false))
        assertEquals(Action.Show(intro), s.at(12_000, segs, false, false, false))
        assertEquals(Action.Show(outro), s.at(1_010_000, segs, false, false, false))
        assertEquals(Action.None, s.at(39_800, segs, false, false, false))
    }

    @Test
    fun dismissHidesForThisViewing() {
        val s = SegmentSkipper()
        s.dismiss(Kind.INTRO)
        assertEquals(Action.None, s.at(12_000, segs, false, false, false))
        assertEquals(Action.Show(outro), s.at(1_010_000, segs, false, false, false))
    }

    @Test
    fun autoSkipsOnceThenShows() {
        val s = SegmentSkipper()
        assertEquals(Action.AutoSkip(intro), s.at(12_000, segs, false, true, false))
        assertEquals(Action.Show(intro), s.at(13_000, segs, false, true, false))
    }

    @Test
    fun undoStopsTheAutoSkip() {
        val s = SegmentSkipper()
        s.undo(Kind.INTRO)
        assertEquals(Action.Show(intro), s.at(12_000, segs, false, true, false))
    }

    @Test
    fun nothingWhileScrubbing() {
        assertEquals(Action.None, SegmentSkipper().at(12_000, segs, true, true, true))
    }

    @Test
    fun goingIntoAnIntroOnPurposeStopsItsAutoSkip() {
        val s = SegmentSkipper()
        s.seekedTo(20_000, segs)
        // Auto-skip on: the spot chosen in the intro is kept, the button still offers the skip.
        assertEquals(Action.Show(intro), s.at(20_500, segs, false, true, true))
        // The outro is untouched and still skips by itself.
        assertEquals(Action.AutoSkip(outro), s.at(1_010_000, segs, false, true, true))
    }

    @Test
    fun goingOutsideEverySegmentChangesNothing() {
        val s = SegmentSkipper()
        s.seekedTo(5_000, segs)
        s.seekedTo(40_000, segs)   // the end is outside the segment
        assertEquals(Action.AutoSkip(intro), s.at(12_000, segs, false, true, false))
    }
}

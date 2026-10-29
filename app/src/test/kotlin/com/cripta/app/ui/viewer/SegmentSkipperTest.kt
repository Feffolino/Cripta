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
}

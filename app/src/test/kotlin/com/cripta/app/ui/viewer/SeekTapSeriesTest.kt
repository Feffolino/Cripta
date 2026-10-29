package com.cripta.app.ui.viewer

import com.cripta.app.ui.viewer.SeekTapSeries.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekTapSeriesTest {
    /** Mirrors the side zone: presses, then the detector's double tap / single tap callbacks. */
    private class Zone(val series: SeekTapSeries = SeekTapSeries()) {
        var seeks = 0
        var toggles = 0
        fun press(side: Side, now: Long) { if (series.onPress(side, now)) seeks++ }
        fun doubleTap(side: Side, now: Long) { if (series.onDoubleTap(side, now)) seeks++ }
        fun singleTap() { if (!series.lastPressSeeked) toggles++ }
    }

    @Test
    fun doubleTapSeeksOnceWithoutControls() {
        val z = Zone()
        z.press(Side.Forward, 0); z.press(Side.Forward, 150); z.doubleTap(Side.Forward, 200)
        assertEquals(1, z.seeks)
        assertEquals(0, z.toggles)
        assertEquals(1, z.series.steps)
    }

    @Test
    fun thirdTapSeeksAgainAndDoesNotShowControls() {
        val z = Zone()
        z.press(Side.Forward, 0); z.press(Side.Forward, 150); z.doubleTap(Side.Forward, 200)
        z.press(Side.Forward, 400)
        // No fourth tap: the detector reports the third one as a single tap.
        z.singleTap()
        assertEquals(2, z.seeks)
        assertEquals(0, z.toggles)
        assertEquals(2, z.series.steps)
    }

    @Test
    fun fourthTapSeeksAndItsDoubleTapIsIgnored() {
        val z = Zone()
        z.press(Side.Back, 0); z.press(Side.Back, 150); z.doubleTap(Side.Back, 200)
        z.press(Side.Back, 400); z.press(Side.Back, 550); z.doubleTap(Side.Back, 600)
        assertEquals(3, z.seeks)
        assertEquals(0, z.toggles)
        assertEquals(3, z.series.steps)
    }

    @Test
    fun tapAfterTheWindowTogglesControls() {
        val z = Zone()
        z.press(Side.Forward, 0); z.press(Side.Forward, 150); z.doubleTap(Side.Forward, 200)
        z.press(Side.Forward, 1_100)
        z.singleTap()
        assertEquals(1, z.seeks)
        assertEquals(1, z.toggles)
        assertEquals(0, z.series.steps)
    }

    @Test
    fun eachStepRenewsTheWindow() {
        val z = Zone()
        z.press(Side.Forward, 0); z.press(Side.Forward, 150); z.doubleTap(Side.Forward, 200)
        z.press(Side.Forward, 900)
        z.press(Side.Forward, 1_600)
        assertEquals(3, z.seeks)
    }

    @Test
    fun otherSideEndsTheSeries() {
        val z = Zone()
        z.press(Side.Forward, 0); z.press(Side.Forward, 150); z.doubleTap(Side.Forward, 200)
        z.press(Side.Back, 400)
        assertFalse(z.series.lastPressSeeked)
        z.singleTap()
        assertEquals(1, z.seeks)
        assertEquals(1, z.toggles)
        // A later press back on the first side is a fresh tap too.
        z.press(Side.Forward, 500)
        assertFalse(z.series.lastPressSeeked)
    }

    @Test
    fun doubleTapOnOtherSideStartsNewSeries() {
        val z = Zone()
        z.press(Side.Forward, 0); z.press(Side.Forward, 150); z.doubleTap(Side.Forward, 200)
        z.press(Side.Back, 400); z.press(Side.Back, 550); z.doubleTap(Side.Back, 600)
        assertEquals(2, z.seeks)
        assertEquals(1, z.series.steps)
        z.press(Side.Back, 800)
        assertTrue(z.series.lastPressSeeked)
    }
}

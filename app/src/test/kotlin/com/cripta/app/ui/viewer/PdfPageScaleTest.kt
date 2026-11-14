package com.cripta.app.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * A PDF page is drawn as one bitmap: one larger than Android can draw (100 MB) closed the app as
 * soon as it showed ("Canvas: trying to draw too large bitmap"). An A0 drawing at 150 dpi was
 * 4967 x 7022 pixels, about 139 MB.
 */
class PdfPageScaleTest {

    private fun pixels(widthPt: Float, heightPt: Float): Pair<Int, Int> {
        val s = pdfRenderScale(widthPt, heightPt)
        return (widthPt * s).roundToInt() to (heightPt * s).roundToInt()
    }

    /** Bytes of the page's ARGB_8888 bitmap. */
    private fun bytes(p: Pair<Int, Int>) = p.first.toLong() * p.second * 4

    @Test
    fun anA4PageIsDrawnAt150Dpi() {
        // 595 x 842 points (1/72 inch).
        assertEquals(1240 to 1754, pixels(595f, 842f))
    }

    @Test
    fun anA0PageIsDrawnSmallerThanAndroidCanDraw() {
        val p = pixels(2384f, 3370f)
        assertEquals(4096, maxOf(p.first, p.second))
        assertTrue("${bytes(p)} bytes", bytes(p) < 100L * 1024 * 1024)
    }

    @Test
    fun aVeryLongPageKeepsItsShapeWithinTheLimit() {
        // A 10 m roll, 1 m wide.
        val p = pixels(2835f, 28346f)
        assertEquals(4096, p.second)
        assertEquals(410, p.first)
    }

    @Test
    fun aPageWithNoSizeGetsTheNormalScale() {
        assertEquals(150f / 72f, pdfRenderScale(0f, 0f), 0.0001f)
    }
}

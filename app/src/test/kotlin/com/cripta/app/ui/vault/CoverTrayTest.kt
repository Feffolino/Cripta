package com.cripta.app.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTrayTest {
    private val gap = 4f
    private val pad = 3f
    private val slot = 22f
    private val overflow: (Int) -> Float = { slot }

    /** Even-odd ray cast: is ([x], [y]) inside the polygon of [pts]? */
    private fun inside(pts: List<TrayPoint>, x: Float, y: Float): Boolean {
        var hit = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) hit = !hit
            j = i
        }
        return hit
    }

    @Test
    fun fullRowsSitAtTheBottom() {
        val r = packCoverRows(List(7) { slot }, avail = 100f, gap = gap, maxRows = 3, overflowWidth = overflow)
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6)), r.rows)
        assertEquals(0, r.extra)
        val w = r.rowWidths(gap)
        assertTrue("bottom row is the widest", w[0] > w[1])
    }

    @Test
    fun noEmptyAreaBesideTheShortRow() {
        val rowWidths = listOf(100f, 48f) // bottom row first
        val rects = trayRects(rowWidths, slot, gap, pad)
        assertEquals(48f + 2 * pad, rects[0].right) // top row
        assertEquals(100f + 2 * pad, rects[1].right)
        assertEquals("rows touch", rects[0].bottom, rects[1].top)
        val outline = trayOutline(rects)
        val topMid = pad + slot / 2
        val bottomMid = pad + slot + gap + slot / 2
        assertTrue(inside(outline, 20f, topMid))
        assertFalse("nothing beside the short row", inside(outline, 80f, topMid))
        assertTrue(inside(outline, 80f, bottomMid))
        // Each mark keeps its pad all round: the wider row's exposed top is pad above its marks.
        assertEquals(pad + slot + gap - pad, rects[0].bottom)
    }

    @Test
    fun shorterRowBelowStillKeepsItsPad() {
        val rects = trayRects(listOf(48f, 100f), slot, gap, pad) // bottom narrower
        assertEquals(pad + slot + pad, rects[0].bottom)
        assertEquals(6, trayOutline(rects).size)
    }

    @Test
    fun equalRowsMakeOneRectangle() {
        val outline = trayOutline(trayRects(listOf(74f, 74f, 74f), slot, gap, pad))
        assertEquals(4, outline.size)
        assertEquals(TrayPoint(80f, 0f), outline[1])
        assertEquals(3 * slot + 2 * gap + 2 * pad, outline[2].y)
    }

    @Test
    fun oneRowIsAStadium() {
        val ops = trayPath(listOf(100f), slot, gap, pad, radius = 14f)
        // 4 corners, each a curve of radius 14 = half of the 28 tall row: no straight left or right side.
        val curves = ops.filterIsInstance<TrayPathOp.CubicTo>()
        assertEquals(4, curves.size)
        val start = ops.first() as TrayPathOp.MoveTo
        assertEquals(TrayPoint(0f, 14f), TrayPoint(start.x, start.y))
        assertEquals(TrayPathOp.Close, ops.last())
    }

    @Test
    fun shortStepsClampTheirRadius() {
        // A 4px step: its two corners share it, 2px each.
        val ops = trayPath(listOf(100f, 96f), slot, gap, pad, radius = 14f)
        val ends = ops.filterIsInstance<TrayPathOp.CubicTo>().map { TrayPoint(it.x, it.y) }
        assertTrue(ends.any { it.x == 100f + 2 * pad - 2f })
    }

    @Test
    fun smallerSizeFitsMoreMarksPerRow() {
        val avail = 150f - 16f
        fun perRow(step: Int): Int {
            val s = coverTagScale(step)
            // The bottom row: a full one, with no room kept for "+N".
            return packCoverRows(List(20) { slot * s }, avail, gap * s, maxRows = 3) { slot * s }.rows[0].size
        }
        assertEquals(6, perRow(0))
        assertEquals(5, perRow(1))
        assertEquals(4, perRow(2))
        assertTrue(perRow(0) > perRow(1) && perRow(1) > perRow(2))
    }

    @Test
    fun rowLimitEndsWithPlusN() {
        val avail = 134f
        val r = packCoverRows(List(20) { slot }, avail, gap, maxRows = 2, overflowWidth = overflow)
        assertEquals(2, r.rows.size)
        assertEquals(5, r.rows[0].size)
        assertEquals(4, r.rows[1].size) // room kept for "+N"
        assertEquals(11, r.extra)
        r.rowWidths(gap).forEach { assertTrue(it <= avail) }
        assertEquals(4 * slot + 4 * gap + slot, r.rowWidths(gap)[1])
    }

    @Test
    fun widePillBesidePlusNIsNarrowed() {
        val r = packCoverRows(listOf(120f, 120f, 120f), avail = 134f, gap = gap, maxRows = 2) { 30f }
        assertEquals(listOf(listOf(0), listOf(1)), r.rows)
        assertEquals(1, r.extra)
        assertEquals(134f - 30f - gap, r.widths[1][0])
        assertEquals(134f, r.rowWidths(gap)[1])
    }

    @Test
    fun scaleSteps() {
        assertEquals(0.8f, coverTagScale(0))
        assertEquals(1f, coverTagScale(1))
        assertEquals(1.2f, coverTagScale(2))
    }
}

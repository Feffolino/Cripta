package com.cripta.app.ui.vault

import kotlin.math.hypot

/*
 * Geometry of the tags on a cover, kept free of Compose so it can be unit-tested: how the marks
 * fall into rows, and the outline of the one dark tray behind them. Every length is in pixels.
 */

/** Scale of the marks for the "Dimensione" setting (0 small, 1 medium, 2 large). */
internal fun coverTagScale(step: Int): Float = when (step) {
    0 -> 0.8f
    2 -> 1.2f
    else -> 1f
}

/**
 * The marks of a cover in rows. [rows] holds the tag indices of each row, bottom row first (the
 * full rows sit on the cover's edge, the shorter one goes on top); [widths] the width each of
 * those marks is drawn at. [extra] tags found no room: a "+N" mark of [overflowWidth] ends the
 * top row (0 when there is none).
 */
internal data class CoverRows(
    val rows: List<List<Int>>,
    val widths: List<List<Float>>,
    val extra: Int,
    val overflowWidth: Float,
) {
    /** Width of each row's content, bottom row first, "+N" included. */
    fun rowWidths(gap: Float): List<Float> = widths.mapIndexed { r, w ->
        val marks = w.sum() + gap * (w.size - 1).coerceAtLeast(0)
        if (r == widths.lastIndex && extra > 0) marks + gap + overflowWidth else marks
    }
}

/**
 * Greedy row packing of marks of [widths] (each already capped to [avail]) into at most [maxRows]
 * rows of [avail] width, [gap] apart. When not every mark fits, the top row keeps room for a "+N"
 * mark ([overflowWidth] of the count left out) and its marks are narrowed to what is left. A row
 * always takes its first mark, so a pill too wide beside "+N" is narrowed instead of leaving the
 * "+N" with no row to sit in.
 */
internal fun packCoverRows(
    widths: List<Float>,
    avail: Float,
    gap: Float,
    maxRows: Int,
    overflowWidth: (Int) -> Float,
): CoverRows {
    if (widths.isEmpty() || maxRows <= 0) return CoverRows(emptyList(), emptyList(), widths.size, 0f)
    fun pack(reserveLast: Float): List<List<Int>> {
        val rows = mutableListOf<List<Int>>()
        var i = 0
        while (i < widths.size && rows.size < maxRows) {
            val row = mutableListOf<Int>()
            val budget = if (rows.size == maxRows - 1) avail - reserveLast else avail
            var used = 0f
            while (i < widths.size) {
                val add = widths[i] + if (row.isNotEmpty()) gap else 0f
                if (used + add <= budget || row.isEmpty()) { row += i; used += add; i++ } else break
            }
            rows += row
        }
        return rows
    }
    var rows = pack(0f)
    if (rows.sumOf { it.size } < widths.size) rows = pack(overflowWidth(widths.size) + gap)
    val extra = widths.size - rows.sumOf { it.size }
    val overflow = if (extra > 0) overflowWidth(extra) else 0f
    // The top row shares its width with "+N": its marks are kept to what is left.
    val topMax = if (extra > 0) (avail - overflow - gap).coerceAtLeast(0f) else avail
    val drawn = rows.mapIndexed { r, row ->
        row.map { i -> if (r == rows.lastIndex) minOf(widths[i], topMax) else widths[i] }
    }
    return CoverRows(rows, drawn, extra, overflow)
}

/** A rectangle of the tray, in the tray's own frame (0,0 at its top-left). */
internal data class TrayRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The tray as one rectangle per row, TOP row first, stacked with no gap between them: each is as
 * wide as its row ([rowWidths], bottom row first, as [CoverRows.rowWidths] gives them) plus [pad]
 * on each side, so nothing empty sits beside a shorter row. Rows are [rowHeight] tall, [gap]
 * apart. Where two rows meet, the line falls [pad] past the narrower row's marks: the wider row
 * keeps its [pad] all round, and so does every mark.
 */
internal fun trayRects(rowWidths: List<Float>, rowHeight: Float, gap: Float, pad: Float): List<TrayRect> {
    val top = rowWidths.asReversed()
    val n = top.size
    if (n == 0) return emptyList()
    val markTop = { i: Int -> pad + i * (rowHeight + gap) }
    // Boundary between row i (upper) and i + 1 (lower).
    val seam = { i: Int ->
        if (top[i + 1] >= top[i]) markTop(i + 1) - pad else markTop(i) + rowHeight + pad
    }
    return List(n) { i ->
        TrayRect(
            left = 0f,
            top = if (i == 0) 0f else seam(i - 1),
            right = top[i] + 2 * pad,
            bottom = if (i == n - 1) markTop(i) + rowHeight + pad else seam(i),
        )
    }
}

/** A corner of the tray's outline. */
internal data class TrayPoint(val x: Float, val y: Float)

/**
 * The outline of the stacked [rects] (left-aligned, touching) as the corners of one polygon,
 * clockwise from the top-left: a straight left side and a staircase on the right. Rows of the same
 * width add no corner.
 */
internal fun trayOutline(rects: List<TrayRect>): List<TrayPoint> {
    if (rects.isEmpty()) return emptyList()
    val pts = mutableListOf(TrayPoint(0f, rects[0].top), TrayPoint(rects[0].right, rects[0].top))
    for (i in 0 until rects.lastIndex) {
        if (rects[i].right != rects[i + 1].right) {
            pts += TrayPoint(rects[i].right, rects[i].bottom)
            pts += TrayPoint(rects[i + 1].right, rects[i].bottom)
        }
    }
    val last = rects.last()
    pts += TrayPoint(last.right, last.bottom)
    pts += TrayPoint(0f, last.bottom)
    return pts
}

/** A drawing step of the tray's rounded outline. */
internal sealed interface TrayPathOp {
    data class MoveTo(val x: Float, val y: Float) : TrayPathOp
    data class LineTo(val x: Float, val y: Float) : TrayPathOp
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : TrayPathOp
    data object Close : TrayPathOp
}

/** Control-point distance of a cubic that draws a quarter circle. */
private const val QUARTER = 0.5523f

/**
 * [corners] with every corner rounded, outer ones bulging and inner (concave) ones filleted: a
 * radius of [radius], less where an edge is too short to give each of its two corners that much.
 */
internal fun roundedOutline(corners: List<TrayPoint>, radius: Float): List<TrayPathOp> {
    val n = corners.size
    if (n < 3) return emptyList()
    fun len(a: TrayPoint, b: TrayPoint) = hypot(b.x - a.x, b.y - a.y)
    fun toward(from: TrayPoint, to: TrayPoint, d: Float): TrayPoint {
        val l = len(from, to)
        return if (l == 0f) from else TrayPoint(from.x + (to.x - from.x) * d / l, from.y + (to.y - from.y) * d / l)
    }
    // Each corner: where its curve starts (on the edge coming in) and ends (on the edge going out).
    val arcs = List(n) { i ->
        val c = corners[i]; val prev = corners[(i + n - 1) % n]; val next = corners[(i + 1) % n]
        val d = minOf(radius, len(prev, c) / 2, len(c, next) / 2).coerceAtLeast(0f)
        Triple(toward(c, prev, d), c, toward(c, next, d))
    }
    val ops = mutableListOf<TrayPathOp>(TrayPathOp.MoveTo(arcs[0].first.x, arcs[0].first.y))
    arcs.forEachIndexed { i, (a, c, b) ->
        if (i > 0) ops += TrayPathOp.LineTo(a.x, a.y)
        ops += TrayPathOp.CubicTo(
            a.x + (c.x - a.x) * QUARTER, a.y + (c.y - a.y) * QUARTER,
            b.x + (c.x - b.x) * QUARTER, b.y + (c.y - b.y) * QUARTER,
            b.x, b.y,
        )
    }
    ops += TrayPathOp.Close
    return ops
}

/** The tray behind rows of [rowWidths] (bottom row first): its rounded outline in its own frame. */
internal fun trayPath(rowWidths: List<Float>, rowHeight: Float, gap: Float, pad: Float, radius: Float): List<TrayPathOp> =
    roundedOutline(trayOutline(trayRects(rowWidths, rowHeight, gap, pad)), radius)

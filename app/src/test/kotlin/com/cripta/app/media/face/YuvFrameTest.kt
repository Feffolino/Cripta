package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuvFrameTest {
    /** A [w] x [h] NV12 picture (padded rows) painted by [color] (Y, U, V per pixel). */
    private fun nv12(w: Int, h: Int, pad: Int = 0, color: (Int, Int) -> Triple<Int, Int, Int>): YuvFrame.Planes {
        val row = w + pad
        val y = ByteArray(row * h)
        val uv = ByteArray(row * (h / 2))
        for (j in 0 until h) for (i in 0 until w) {
            val (yy, u, v) = color(i, j)
            y[j * row + i] = yy.toByte()
            if (i % 2 == 0 && j % 2 == 0) { uv[j / 2 * row + i] = u.toByte(); uv[j / 2 * row + i + 1] = v.toByte() }
        }
        // As decoders hand NV12: U and V are the same buffer, V one byte later, pixel stride 2.
        return YuvFrame.Planes(y, row, uv, uv.copyOfRange(1, uv.size), row, 2)
    }

    private fun rgb(c: Int) = Triple(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)

    @Test
    fun videoRangeGreyAndColours() {
        val p = nv12(4, 4) { i, _ -> if (i < 2) Triple(16, 128, 128) else Triple(235, 128, 128) }
        val img = YuvFrame.toFace(p, 0, 0, 4, 4, 1280)
        assertEquals(Triple(0, 0, 0), rgb(img.argb[0]))
        assertEquals(Triple(255, 255, 255), rgb(img.argb[3]))
        // Pure red in BT.601 video range.
        val red = YuvFrame.toFace(nv12(2, 2) { _, _ -> Triple(81, 90, 240) }, 0, 0, 2, 2, 1280)
        val (r, g, b) = rgb(red.argb[0])
        assertTrue("red $r $g $b", r > 245 && g < 10 && b < 10)
    }

    @Test
    fun cropScaleAndRotate() {
        // Left half dark, right half light, 2 pixels of padding per row, a 2-pixel border cropped away.
        val p = nv12(20, 12, pad = 2) { i, j ->
            if (i < 2 || j < 2 || i >= 18 || j >= 10) Triple(128, 0, 255) else if (i < 10) Triple(16, 128, 128) else Triple(235, 128, 128)
        }
        val img = YuvFrame.toFace(p, 2, 2, 16, 8, 8)
        assertEquals(8, img.width); assertEquals(4, img.height)
        assertEquals(Triple(0, 0, 0), rgb(img.argb[0]))
        assertEquals(Triple(255, 255, 255), rgb(img.argb[7]))
        // Turned 90 degrees clockwise: the dark half goes on top.
        val up = YuvFrame.rotate(img, 90)
        assertEquals(4, up.width); assertEquals(8, up.height)
        assertEquals(Triple(0, 0, 0), rgb(up.argb[0]))
        assertEquals(Triple(255, 255, 255), rgb(up.argb[up.argb.size - 1]))
        val back = YuvFrame.rotate(YuvFrame.rotate(up, 180), 90)
        assertTrue(back.argb.contentEquals(img.argb))
    }
}

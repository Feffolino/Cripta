package com.cripta.app.media.face

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A decoder's YUV 4:2:0 picture (any row and pixel strides: I420, NV12, NV21 all fit) turned
 * straight into a [FaceImage]: cropped, scaled down so its longest side is at most a given size,
 * then turned upright. No JPEG and no Bitmap on the way, and free of Android types (unit tests).
 */
object YuvFrame {
    /** The three planes as copied out of the decoder; offsets are from each plane's start. */
    class Planes(
        val y: ByteArray, val yRow: Int,
        val u: ByteArray, val v: ByteArray, val uvRow: Int, val uvPixel: Int,
    )

    /**
     * The [width] x [height] region at ([left], [top]) of [p], scaled (bilinear on the luma, as
     * [FaceImage.fit]) so its longest side is at most [side], then turned [rotation] degrees
     * clockwise (the container's rotation). [full]: full-range samples (else video range 16-235);
     * [bt709]: HD colours (else BT.601).
     */
    fun toFace(p: Planes, left: Int, top: Int, width: Int, height: Int, side: Int, rotation: Int = 0,
               full: Boolean = false, bt709: Boolean = false): FaceImage {
        val k = minOf(1.0, side.toDouble() / maxOf(width, height))
        val w = if (k < 1.0) maxOf(1, (width * k).roundToInt()) else width
        val h = if (k < 1.0) maxOf(1, (height * k).roundToInt()) else height
        // Fixed point (x256): luma scale and offset, then V->R, U->G, V->G, U->B.
        val yMul = if (full) 256 else 298
        val yOff = if (full) 0 else 16
        val rv: Int; val gu: Int; val gv: Int; val bu: Int
        when {
            full && bt709 -> { rv = 403; gu = 48; gv = 120; bu = 475 }
            full -> { rv = 359; gu = 88; gv = 183; bu = 454 }
            bt709 -> { rv = 459; gu = 55; gv = 136; bu = 541 }
            else -> { rv = 409; gu = 100; gv = 208; bu = 516 }
        }
        val sx = width.toDouble() / w
        val sy = height.toDouble() / h
        // Per column: the two luma samples, their weight and the chroma sample (nearest).
        val x0s = IntArray(w); val x1s = IntArray(w); val wxs = IntArray(w); val cxs = IntArray(w)
        for (x in 0 until w) {
            val fx = ((x + 0.5) * sx - 0.5).coerceIn(0.0, (width - 1).toDouble())
            val x0 = floor(fx).toInt()
            x0s[x] = left + x0; x1s[x] = left + minOf(x0 + 1, width - 1)
            wxs[x] = ((fx - x0) * 256).toInt()
            cxs[x] = (left + minOf((fx + 0.5).toInt(), width - 1)) / 2 * p.uvPixel
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val fy = ((y + 0.5) * sy - 0.5).coerceIn(0.0, (height - 1).toDouble())
            val y0 = floor(fy).toInt()
            val r0 = (top + y0) * p.yRow
            val r1 = (top + minOf(y0 + 1, height - 1)) * p.yRow
            val wy = ((fy - y0) * 256).toInt()
            val cr = (top + minOf((fy + 0.5).toInt(), height - 1)) / 2 * p.uvRow
            val row = y * w
            for (x in 0 until w) {
                val wx = wxs[x]
                val a = (p.y[r0 + x0s[x]].toInt() and 0xFF) * (256 - wx) + (p.y[r0 + x1s[x]].toInt() and 0xFF) * wx
                val b = (p.y[r1 + x0s[x]].toInt() and 0xFF) * (256 - wx) + (p.y[r1 + x1s[x]].toInt() and 0xFF) * wx
                val lum = (a * (256 - wy) + b * wy + 32768) shr 16
                val d = (p.u[cr + cxs[x]].toInt() and 0xFF) - 128
                val e = (p.v[cr + cxs[x]].toInt() and 0xFF) - 128
                val c = (lum - yOff) * yMul
                val r = ((c + rv * e + 128) shr 8).coerceIn(0, 255)
                val g = ((c - gu * d - gv * e + 128) shr 8).coerceIn(0, 255)
                val bl = ((c + bu * d + 128) shr 8).coerceIn(0, 255)
                out[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return rotate(FaceImage(w, h, out), rotation)
    }

    /** [img] turned [degrees] clockwise (multiples of 90; anything else counts as 0). */
    fun rotate(img: FaceImage, degrees: Int): FaceImage {
        val d = ((degrees % 360) + 360) % 360
        if (d != 90 && d != 180 && d != 270) return img
        val w = img.width; val h = img.height; val src = img.argb
        val ow = if (d == 180) w else h
        val oh = if (d == 180) h else w
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val at = when (d) {
                90 -> x * ow + (h - 1 - y)
                180 -> (h - 1 - y) * ow + (w - 1 - x)
                else -> (w - 1 - x) * ow + y
            }
            out[at] = src[y * w + x]
        }
        return FaceImage(ow, oh, out)
    }
}

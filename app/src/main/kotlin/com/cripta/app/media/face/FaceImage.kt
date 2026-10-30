package com.cripta.app.media.face

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A decoded picture as packed ARGB pixels (what [android.graphics.Bitmap.getPixels] returns), kept
 * free of Android types so the whole face pipeline also runs in the JVM unit tests.
 */
class FaceImage(val width: Int, val height: Int, val argb: IntArray) {
    init { require(argb.size == width * height) { "pixels ${argb.size} != $width x $height" } }

    /** This picture scaled down (bilinear, as OpenCV's INTER_LINEAR) so its longest side is at most [maxSide]. */
    fun fit(maxSide: Int): FaceImage {
        val k = minOf(1.0, maxSide.toDouble() / maxOf(width, height))
        if (k >= 1.0) return this
        return resized(maxOf(1, (width * k).roundToInt()), maxOf(1, (height * k).roundToInt()))
    }

    fun resized(w: Int, h: Int): FaceImage {
        val out = IntArray(w * h)
        val sx = width.toDouble() / w
        val sy = height.toDouble() / h
        for (y in 0 until h) {
            val fy = ((y + 0.5) * sy - 0.5).coerceIn(0.0, (height - 1).toDouble())
            val y0 = floor(fy).toInt(); val y1 = minOf(y0 + 1, height - 1); val wy = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5) * sx - 0.5).coerceIn(0.0, (width - 1).toDouble())
                val x0 = floor(fx).toInt(); val x1 = minOf(x0 + 1, width - 1); val wx = fx - x0
                out[y * w + x] = blend(argb[y0 * width + x0], argb[y0 * width + x1], argb[y1 * width + x0], argb[y1 * width + x1], wx, wy)
            }
        }
        return FaceImage(w, h, out)
    }

    /** Bilinear sample at ([x], [y]); outside the picture is black (OpenCV's constant border). */
    internal fun sample(x: Double, y: Double, rgb: FloatArray) {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val wx = (x - x0).toFloat(); val wy = (y - y0).toFloat()
        rgb[0] = 0f; rgb[1] = 0f; rgb[2] = 0f
        for (dy in 0..1) for (dx in 0..1) {
            val px = x0 + dx; val py = y0 + dy
            if (px < 0 || py < 0 || px >= width || py >= height) continue
            val w = (if (dx == 0) 1 - wx else wx) * (if (dy == 0) 1 - wy else wy)
            val c = argb[py * width + px]
            rgb[0] += w * (c shr 16 and 0xFF); rgb[1] += w * (c shr 8 and 0xFF); rgb[2] += w * (c and 0xFF)
        }
    }

    private companion object {
        fun blend(a: Int, b: Int, c: Int, d: Int, wx: Double, wy: Double): Int {
            fun ch(s: Int): Int {
                val top = (a shr s and 0xFF) * (1 - wx) + (b shr s and 0xFF) * wx
                val bottom = (c shr s and 0xFF) * (1 - wx) + (d shr s and 0xFF) * wx
                return (top * (1 - wy) + bottom * wy).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

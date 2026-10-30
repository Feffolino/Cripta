package com.cripta.app.media.face

import kotlin.math.abs
import kotlin.math.hypot

/**
 * The 112 x 112 face SFace expects: the five points moved onto the ArcFace template by the
 * least-squares similarity (rotation, scale, shift), as OpenCV's FaceRecognizerSF.alignCrop does.
 */
object FaceAligner {
    const val SIZE = 112
    private val TEMPLATE = floatArrayOf(38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f)

    /** [a, b, tx, ty] of the map (x, y) -> (a·x − b·y + tx, b·x + a·y + ty) taking [src] onto the template. */
    fun similarity(src: FloatArray): DoubleArray {
        var mx = 0.0; var my = 0.0; var mu = 0.0; var mv = 0.0
        for (i in 0 until 5) { mx += src[2 * i]; my += src[2 * i + 1]; mu += TEMPLATE[2 * i]; mv += TEMPLATE[2 * i + 1] }
        mx /= 5; my /= 5; mu /= 5; mv /= 5
        var num1 = 0.0; var num2 = 0.0; var den = 0.0
        for (i in 0 until 5) {
            val x = src[2 * i] - mx; val y = src[2 * i + 1] - my
            val u = TEMPLATE[2 * i] - mu; val v = TEMPLATE[2 * i + 1] - mv
            num1 += x * u + y * v; num2 += x * v - y * u; den += x * x + y * y
        }
        val a = num1 / den; val b = num2 / den
        return doubleArrayOf(a, b, mu - (a * mx - b * my), mv - (b * mx + a * my))
    }

    /** The aligned face of [img] whose five points are [landmarks]. */
    fun align(img: FaceImage, landmarks: FloatArray): FaceImage {
        val (a, b, tx, ty) = similarity(landmarks)
        // Inverse map: each output pixel samples the picture.
        val det = a * a + b * b
        val out = IntArray(SIZE * SIZE)
        val rgb = FloatArray(3)
        for (v in 0 until SIZE) for (u in 0 until SIZE) {
            val du = u - tx; val dv = v - ty
            img.sample((a * du + b * dv) / det, (-b * du + a * dv) / det, rgb)
            out[v * SIZE + u] = (0xFF shl 24) or (rgb[0].toInt().coerceIn(0, 255) shl 16) or
                (rgb[1].toInt().coerceIn(0, 255) shl 8) or rgb[2].toInt().coerceIn(0, 255)
        }
        return FaceImage(SIZE, SIZE, out)
    }

    /** SFace's input: RGB, 0..255, planar (OpenCV blobFromImage with swapRB). */
    fun input(face: FaceImage): FloatArray {
        val plane = SIZE * SIZE
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = face.argb[i]
            out[i] = (c shr 16 and 0xFF).toFloat(); out[plane + i] = (c shr 8 and 0xFF).toFloat(); out[2 * plane + i] = (c and 0xFF).toFloat()
        }
        return out
    }

    /** How far the nose sits from between the eyes, in eye distances: 0 = frontal, ≳0.6 = profile. */
    fun yaw(lm: FloatArray): Float {
        val eyes = hypot(lm[2] - lm[0], lm[3] - lm[1]).coerceAtLeast(1e-3f)
        return abs(lm[4] - (lm[0] + lm[2]) / 2) / eyes
    }

    /** Sharpness: variance of the Laplacian of the aligned face's grey levels (low = blurred). */
    fun sharpness(face: FaceImage): Double {
        val n = face.width
        val g = DoubleArray(n * n) { val c = face.argb[it]; 0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF) }
        var sum = 0.0; var sq = 0.0; var count = 0
        // The middle of the face only: the corners are background or black border.
        for (y in 20 until n - 20) for (x in 20 until n - 20) {
            val l = g[(y - 1) * n + x] + g[(y + 1) * n + x] + g[y * n + x - 1] + g[y * n + x + 1] - 4 * g[y * n + x]
            sum += l; sq += l * l; count++
        }
        val mean = sum / count
        return sq / count - mean * mean
    }
}

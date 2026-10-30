package com.cripta.app.media.face

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A detected face: box and five points (right eye, left eye, nose, mouth corners) in picture pixels. */
class Detection(val x: Float, val y: Float, val w: Float, val h: Float, val landmarks: FloatArray, val score: Float) {
    fun scaled(kx: Float, ky: Float) = Detection(x * kx, y * ky, w * kx, h * ky, FloatArray(10) { landmarks[it] * if (it % 2 == 0) kx else ky }, score)
}

/** YuNet's input and output, as OpenCV's FaceDetectorYN prepares and reads them. */
object YuNet {
    val STRIDES = intArrayOf(8, 16, 32)

    /** Input size: the picture padded right and bottom to a multiple of 32. */
    fun padded(n: Int) = (n + 31) / 32 * 32

    /** BGR, 0..255, planar (NCHW), zero padding: what the model was trained on. */
    fun input(img: FaceImage): FloatArray {
        val pw = padded(img.width); val ph = padded(img.height)
        val plane = pw * ph
        val out = FloatArray(3 * plane)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val c = img.argb[y * img.width + x]
            val i = y * pw + x
            out[i] = (c and 0xFF).toFloat()
            out[plane + i] = (c shr 8 and 0xFF).toFloat()
            out[2 * plane + i] = (c shr 16 and 0xFF).toFloat()
        }
        return out
    }

    /**
     * Faces from the model's outputs (`cls_8`, `obj_8`, `bbox_8`, `kps_8`, … per stride) for a padded
     * input of [padW] x [padH]: every anchor scoring at least [minScore], then non-maximum suppression.
     */
    fun decode(outputs: Map<String, FloatArray>, padW: Int, padH: Int, minScore: Float, nms: Float = 0.3f): List<Detection> {
        val found = ArrayList<Detection>()
        for (s in STRIDES) {
            val cls = outputs.getValue("cls_$s"); val obj = outputs.getValue("obj_$s")
            val bbox = outputs.getValue("bbox_$s"); val kps = outputs.getValue("kps_$s")
            val cols = padW / s; val rows = padH / s
            for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                val score = sqrt(cls[i].coerceIn(0f, 1f) * obj[i].coerceIn(0f, 1f))
                if (score < minScore) continue
                val cx = (c + bbox[i * 4]) * s; val cy = (r + bbox[i * 4 + 1]) * s
                val w = exp(bbox[i * 4 + 2]) * s; val h = exp(bbox[i * 4 + 3]) * s
                val lm = FloatArray(10) { k -> (kps[i * 10 + k] + if (k % 2 == 0) c else r) * s }
                found += Detection(cx - w / 2, cy - h / 2, w, h, lm, score)
            }
        }
        return suppress(found, nms)
    }

    /** Greedy non-maximum suppression: the best face of each overlapping cluster stays. */
    fun suppress(faces: List<Detection>, iouMax: Float): List<Detection> {
        val kept = ArrayList<Detection>()
        for (f in faces.sortedByDescending { it.score }) if (kept.none { iou(it, f) > iouMax }) kept += f
        return kept
    }

    fun iou(a: Detection, b: Detection): Float {
        val ix = max(0f, min(a.x + a.w, b.x + b.w) - max(a.x, b.x))
        val iy = max(0f, min(a.y + a.h, b.y + b.h) - max(a.y, b.y))
        val inter = ix * iy
        return inter / (a.w * a.h + b.w * b.h - inter).coerceAtLeast(1e-6f)
    }
}

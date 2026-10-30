package com.cripta.app.media.face

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Embeddings stored compactly: each one as its largest magnitude (float) and 128 signed bytes.
 * The similarity moves by less than 0.005, far below the gap between the thresholds.
 */
object EmbeddingCodec {
    private const val DIM = 128

    fun encode(list: List<FloatArray>): ByteArray {
        val b = ByteBuffer.allocate(list.size * (4 + DIM))
        for (e in list) {
            val m = e.maxOf { abs(it) }.coerceAtLeast(1e-6f)
            b.putFloat(m)
            for (v in e) b.put((v / m * 127).roundToInt().coerceIn(-127, 127).toByte())
        }
        return b.array()
    }

    /** Back to normalised embeddings. */
    fun decode(bytes: ByteArray): List<FloatArray> {
        val b = ByteBuffer.wrap(bytes)
        return List(bytes.size / (4 + DIM)) {
            val m = b.float / 127
            val e = FloatArray(DIM) { b.get() * m }
            val n = sqrt(e.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
            for (i in e.indices) e[i] /= n
            e
        }
    }
}

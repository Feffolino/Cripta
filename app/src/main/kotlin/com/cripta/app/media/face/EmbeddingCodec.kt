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

/** One face of a track as stored: its frame and moment, quality, width (px), place ("x,y,w,h" fractions) and embedding. */
class StoredFace(val frame: Int, val timeMs: Long, val quality: Float, val width: Int, val box: FloatArray, val embedding: FloatArray)

/**
 * The faces of a track, kept so that the user can split it: about 150 bytes each (the embedding
 * as [EmbeddingCodec] stores it). A leading version byte; anything else reads as no faces.
 */
object FaceCodec {
    private const val V1: Byte = 1
    private const val SIZE = 2 + 8 + 4 + 2 + 8 + 4 + 128

    fun encode(list: List<StoredFace>): ByteArray {
        if (list.isEmpty()) return ByteArray(0)
        val b = ByteBuffer.allocate(1 + list.size * SIZE)
        b.put(V1)
        for (f in list) {
            b.putShort(f.frame.toShort()); b.putLong(f.timeMs); b.putFloat(f.quality)
            b.putShort(f.width.coerceIn(0, Short.MAX_VALUE.toInt()).toShort())
            for (i in 0 until 4) b.putShort(((f.box.getOrNull(i) ?: 0f) * 10_000).roundToInt().coerceIn(0, 10_000).toShort())
            b.put(EmbeddingCodec.encode(listOf(f.embedding)))
        }
        return b.array()
    }

    fun decode(bytes: ByteArray): List<StoredFace> {
        if (bytes.isEmpty() || bytes[0] != V1 || (bytes.size - 1) % SIZE != 0) return emptyList()
        val b = ByteBuffer.wrap(bytes, 1, bytes.size - 1)
        return List((bytes.size - 1) / SIZE) {
            val frame = b.short.toInt(); val time = b.long; val q = b.float; val w = b.short.toInt()
            val box = FloatArray(4) { b.short / 10_000f }
            val e = ByteArray(4 + 128).also { b.get(it) }
            StoredFace(frame, time, q, w, box, EmbeddingCodec.decode(e).first())
        }
    }
}

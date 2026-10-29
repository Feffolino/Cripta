package com.cripta.app.media.intro

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fingerprint hashes as little-endian bytes, the form stored in the database. */
object PrintCodec {
    fun encode(hashes: IntArray): ByteArray {
        val bb = ByteBuffer.allocate(hashes.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asIntBuffer().put(hashes)
        return bb.array()
    }

    fun decode(bytes: ByteArray): IntArray {
        val ib = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        return IntArray(ib.remaining()).also { ib.get(it) }
    }
}

package com.cripta.crypto

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * Read-only SeekableByteChannel over a file, for Tink's seekable decrypting channel.
 * Backed by a RandomAccessFile rather than a FileChannel on purpose: a FileChannel closes itself
 * when the reading thread is interrupted, and the player interrupts its loader threads on seek.
 */
class SeekableInputByteChannel(file: File) : SeekableByteChannel {
    private val raf = RandomAccessFile(file, "r")
    private var open = true

    override fun read(dst: ByteBuffer): Int {
        if (dst.hasArray()) {
            // Straight into the caller's buffer: Tink reads whole 1 MB segments into heap buffers.
            val n = raf.read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining())
            if (n > 0) dst.position(dst.position() + n)
            return n
        }
        val tmp = ByteArray(dst.remaining())
        val n = raf.read(tmp)
        if (n > 0) dst.put(tmp, 0, n)
        return n
    }

    override fun write(src: ByteBuffer): Int =
        throw java.nio.channels.NonWritableChannelException()

    override fun position(): Long = raf.filePointer
    override fun position(newPosition: Long): SeekableByteChannel {
        raf.seek(newPosition); return this
    }
    override fun size(): Long = raf.length()
    override fun truncate(size: Long): SeekableByteChannel =
        throw java.nio.channels.NonWritableChannelException()
    override fun isOpen(): Boolean = open
    override fun close() { open = false; raf.close() }
}

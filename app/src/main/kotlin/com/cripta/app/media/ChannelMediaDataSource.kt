package com.cripta.app.media

import android.media.MediaDataSource
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** A decrypting channel seen as a [MediaDataSource], for MediaExtractor (no plaintext on disk). */
internal class ChannelMediaDataSource(
    private val channel: SeekableByteChannel,
    private val length: Long,
) : MediaDataSource() {
    /** One read at a time: a read is a seek then a read on the one channel, which two threads interleaved. */
    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= length) return -1
        // Nothing asked: nothing read, which is not the end of the stream.
        if (size == 0) return 0
        channel.position(position)
        val bb = ByteBuffer.wrap(buffer, offset, size)
        var read = 0
        while (bb.hasRemaining()) {
            val n = channel.read(bb)
            if (n <= 0) break
            read += n
        }
        return if (read == 0) -1 else read
    }
    override fun getSize(): Long = length
    @Synchronized
    override fun close() { runCatching { channel.close() } }
}

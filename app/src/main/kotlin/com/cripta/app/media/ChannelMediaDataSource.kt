package com.cripta.app.media

import android.media.MediaDataSource
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** A decrypting channel seen as a [MediaDataSource], for MediaExtractor (no plaintext on disk). */
internal class ChannelMediaDataSource(
    private val channel: SeekableByteChannel,
    private val length: Long,
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= length) return -1
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
    override fun close() { runCatching { channel.close() } }
}

package com.cripta.app.media

import android.app.Application
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChannelMediaDataSourceTest {
    /** An in-memory channel over [bytes]. */
    private class Bytes(private val bytes: ByteArray) : SeekableByteChannel {
        private var pos = 0L
        override fun read(dst: ByteBuffer): Int {
            if (pos >= bytes.size) return -1
            val n = minOf(dst.remaining().toLong(), bytes.size - pos).toInt()
            dst.put(bytes, pos.toInt(), n); pos += n
            return n
        }
        override fun position(): Long = pos
        override fun position(newPosition: Long): SeekableByteChannel = apply { pos = newPosition }
        override fun size(): Long = bytes.size.toLong()
        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException()
        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException()
        override fun isOpen(): Boolean = true
        override fun close() {}
    }

    // Regression: asked for no bytes, it answered -1 (the end of the stream) inside the file.
    @Test
    fun askedForNoBytesItReadsNoneRatherThanEndingTheStream() {
        val source = ChannelMediaDataSource(Bytes(ByteArray(10) { it.toByte() }), 10)
        assertEquals(0, source.readAt(2, ByteArray(4), 0, 0))
        val out = ByteArray(4)
        assertEquals(4, source.readAt(2, out, 0, 4))
        assertEquals(2.toByte(), out[0])
        assertEquals(-1, source.readAt(10, out, 0, 4))
    }
}

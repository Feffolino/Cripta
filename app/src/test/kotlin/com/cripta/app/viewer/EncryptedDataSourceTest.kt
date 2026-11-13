package com.cripta.app.viewer

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EncryptedDataSourceTest {
    /** An in-memory channel over [bytes] that remembers being closed. */
    private class Bytes(private val bytes: ByteArray) : SeekableByteChannel {
        private var pos = 0L
        var open = true
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
        override fun isOpen(): Boolean = open
        override fun close() { open = false }
    }

    private val data = ByteArray(100) { it.toByte() }
    private fun spec(position: Long, length: Long = C.LENGTH_UNSET.toLong()) =
        DataSpec.Builder().setUri(Uri.parse("cripta://v")).setPosition(position).setLength(length).build()

    @Test
    fun readsFromThePositionAskedToTheEnd() {
        val channel = Bytes(data)
        // The size given at import is wrong: the channel's own length counts.
        val source = EncryptedDataSource({ channel }, plaintextLength = 10)
        assertEquals(60L, source.open(spec(40)))
        val out = ByteArray(100)
        var total = 0
        while (true) {
            val n = source.read(out, total, out.size - total)
            if (n == C.RESULT_END_OF_INPUT) break
            total += n
        }
        assertEquals(60, total)
        assertEquals(40.toByte(), out[0])
        assertEquals(99.toByte(), out[59])
        source.close()
        assertFalse(channel.open)
    }

    @Test
    fun aPositionPastTheEndFailsTheOpenAndTheCloseStillReleasesTheChannel() {
        val channel = Bytes(data)
        val source = EncryptedDataSource({ channel }, plaintextLength = 100)
        assertThrows(DataSourceException::class.java) { source.open(spec(101)) }
        source.close()
        assertFalse(channel.open)
    }

    @Test
    fun aBoundedReadStopsAtItsLength() {
        val source = EncryptedDataSource({ Bytes(data) }, plaintextLength = 100)
        assertEquals(5L, source.open(spec(10, 5)))
        val out = ByteArray(20)
        assertEquals(5, source.read(out, 0, 20))
        assertEquals(C.RESULT_END_OF_INPUT, source.read(out, 0, 20))
        assertEquals(0, source.read(out, 0, 0))
        source.close()
    }
}

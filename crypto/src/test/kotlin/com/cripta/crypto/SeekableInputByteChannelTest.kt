package com.cripta.crypto

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer

class SeekableInputByteChannelTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun readsFromPosition() {
        val f = tmp.newFile("data.bin")
        f.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        SeekableInputByteChannel(f).use { ch ->
            assertEquals(10L, ch.size())
            ch.position(4L)
            val buf = ByteBuffer.allocate(3)
            val n = ch.read(buf)
            assertEquals(3, n)
            assertEquals(4.toByte(), buf.get(0))
            assertEquals(6.toByte(), buf.get(2))
        }
    }
}

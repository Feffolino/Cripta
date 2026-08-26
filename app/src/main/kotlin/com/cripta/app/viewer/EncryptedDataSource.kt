package com.cripta.app.viewer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * Media3 DataSource that streams plaintext from a Tink seekable decrypting channel.
 * Nothing is ever written to disk in the clear — ExoPlayer reads decrypted bytes on demand.
 *
 * A fresh channel is opened on every [open] (and closed on [close]) so ExoPlayer can
 * re-open the same DataSource instance for seeks without hitting a closed channel.
 */
@UnstableApi
class EncryptedDataSource(
    private val channelProvider: () -> SeekableByteChannel,
    private val plaintextLength: Long,
) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var channel: SeekableByteChannel? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val ch = channelProvider()
        // Use the channel's true decrypted plaintext length as the authoritative total. The
        // caller-supplied plaintextLength comes from OpenableColumns.SIZE at import time, which
        // some content providers report inaccurately. When it's too small, ExoPlayer can't reach
        // an MP4 whose moov (seek table) sits at the end of the file, so the video plays but is
        // not seekable. Deriving the length from the channel fixes seeking for those files.
        val total = runCatching { ch.size() }.getOrNull()?.takeIf { it > 0 } ?: plaintextLength
        ch.position(dataSpec.position)
        channel = ch
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            total - dataSpec.position
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val ch = channel ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val bb = ByteBuffer.wrap(buffer, offset, toRead)
        var read = 0
        while (bb.hasRemaining()) {
            val n = ch.read(bb)
            if (n <= 0) break
            read += n
        }
        if (read <= 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { channel?.close() }
        channel = null
        transferEnded()
    }

    @UnstableApi
    class Factory(
        private val channelProvider: () -> SeekableByteChannel,
        private val plaintextLength: Long,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            EncryptedDataSource(channelProvider, plaintextLength)
    }
}

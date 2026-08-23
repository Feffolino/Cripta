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
 */
@UnstableApi
class EncryptedDataSource(
    private val channel: SeekableByteChannel,
    private val plaintextLength: Long,
) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        channel.position(dataSpec.position)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET) {
            dataSpec.length
        } else {
            plaintextLength - dataSpec.position
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val bb = ByteBuffer.wrap(buffer, offset, toRead)
        var read = 0
        while (bb.hasRemaining()) {
            val n = channel.read(bb)
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
        runCatching { channel.close() }
        transferEnded()
    }

    @UnstableApi
    class Factory(
        private val channelProvider: () -> SeekableByteChannel,
        private val plaintextLength: Long,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            EncryptedDataSource(channelProvider(), plaintextLength)
    }
}

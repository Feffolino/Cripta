package com.cripta.app.media.intro

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.media.ChannelMediaDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Decodes a stretch of a video's audio track to mono [MonoResampler.OUT_RATE] Hz, from the encrypted file. */
@Singleton
class AudioDecoder @Inject constructor(private val repo: VaultRepository) {
    /** The first [size] of [samples] start at [startMs] in the video. */
    class Pcm(val startMs: Long, val samples: FloatArray, val size: Int)

    /** Why a video's audio cannot be fingerprinted, in Italian for the user. */
    class Undecodable(reason: String) : Exception(reason)

    /** The video's length from its container (for files whose length was never recorded). */
    suspend fun durationMs(file: FileEntity): Long? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var channel: SeekableByteChannel? = null
        try {
            channel = repo.seekableChannel(file)
            extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .filter { it.containsKey(MediaFormat.KEY_DURATION) }
                .maxOfOrNull { it.getLong(MediaFormat.KEY_DURATION) / 1000 }?.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
            runCatching { channel?.close() }
        }
    }

    /**
     * Audio of [file] from [fromMs] to [toMs]; null when nothing could be decoded.
     * Throws [Undecodable] without an audio track or a decoder for it.
     */
    suspend fun decode(file: FileEntity, fromMs: Long, toMs: Long): Pcm? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var channel: SeekableByteChannel? = null
        try {
            channel = repo.seekableChannel(file)
            extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw Undecodable("nessuna traccia audio")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val c = try {
                MediaCodec.createDecoderByType(mime).also { codec = it; it.configure(format, null, null, 0) }
            } catch (e: Exception) {
                throw Undecodable("audio ${mime.removePrefix("audio/")} non supportato dal telefono")
            }
            c.start()
            val info = MediaCodec.BufferInfo()
            var resampler: MonoResampler? = null
            var floatPcm = false
            var startUs = -1L
            var inputDone = false
            var buf = FloatArray(0)
            var shorts = ShortArray(0)
            val endUs = toMs * 1000
            // Sized for the whole stretch up front: growing it by doubling meant copies of minutes of audio.
            val capacity = ((toMs - fromMs) / 1000.0 * MonoResampler.OUT_RATE).toInt() + MonoResampler.OUT_RATE
            var outputDone = false
            // Feed every free input buffer and drain every ready output without waiting; block (briefly)
            // only when neither side moved. Waiting 10 ms for each output frame, one frame at a time,
            // made the decoding of 15 minutes of audio take minutes.
            while (!outputDone) {
                coroutineContext.ensureActive()
                var moved = false
                while (!inputDone) {
                    val i = c.dequeueInputBuffer(0)
                    if (i < 0) break
                    moved = true
                    val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                    if (n < 0 || extractor.sampleTime > endUs) {
                        c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                while (true) {
                    val o = c.dequeueOutputBuffer(info, if (moved) 0L else WAIT_US)
                    if (o == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    moved = true
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = c.outputFormat
                        resampler = MonoResampler(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT), capacity)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        continue
                    }
                    if (o < 0) continue
                    val r = resampler ?: MonoResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), capacity).also { resampler = it }
                    if (info.size > 0) {
                        if (startUs < 0) startUs = info.presentationTimeUs
                        val out = c.getOutputBuffer(o)!!
                        out.position(info.offset); out.limit(info.offset + info.size)
                        out.order(ByteOrder.nativeOrder())
                        val n = if (floatPcm) info.size / 4 else info.size / 2
                        if (buf.size < n) buf = FloatArray(n)
                        if (floatPcm) out.asFloatBuffer().get(buf, 0, n)
                        else {
                            // One bulk copy, then a plain array loop (a ShortBuffer.get per sample is slow).
                            if (shorts.size < n) shorts = ShortArray(n)
                            out.asShortBuffer().get(shorts, 0, n)
                            for (k in 0 until n) buf[k] = shorts[k] / 32768f
                        }
                        r.push(buf, n)
                    }
                    c.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { outputDone = true; break }
                }
            }
            val r = resampler
            if (r == null || startUs < 0 || r.size == 0) null else Pcm(startUs / 1000, r.buffer(), r.size)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
            runCatching { channel?.close() }
        }
    }

    private companion object {
        /** Wait for the codec only when nothing moved, and briefly. */
        const val WAIT_US = 2_000L
    }
}

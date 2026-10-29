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
    /** [samples] start at [startMs] in the video. */
    class Pcm(val startMs: Long, val samples: FloatArray)

    /** Audio of [file] from [fromMs] to [toMs]; null when it has no audio track or nothing decoded. */
    suspend fun decode(file: FileEntity, fromMs: Long, toMs: Long): Pcm? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var channel: SeekableByteChannel? = null
        try {
            channel = repo.seekableChannel(file)
            extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val info = MediaCodec.BufferInfo()
            var resampler: MonoResampler? = null
            var floatPcm = false
            var startUs = -1L
            var inputDone = false
            var buf = FloatArray(0)
            val endUs = toMs * 1000
            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                        if (n < 0 || extractor.sampleTime > endUs) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    resampler = MonoResampler(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val r = resampler ?: MonoResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)).also { resampler = it }
                    if (info.size > 0) {
                        if (startUs < 0) startUs = info.presentationTimeUs
                        val out = c.getOutputBuffer(o)!!
                        out.position(info.offset); out.limit(info.offset + info.size)
                        out.order(ByteOrder.nativeOrder())
                        val n = if (floatPcm) info.size / 4 else info.size / 2
                        if (buf.size < n) buf = FloatArray(n)
                        if (floatPcm) out.asFloatBuffer().get(buf, 0, n)
                        else { val sb = out.asShortBuffer(); for (k in 0 until n) buf[k] = sb.get(k) / 32768f }
                        r.push(buf, n)
                    }
                    c.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            val r = resampler
            if (r == null || startUs < 0 || r.size == 0) null else Pcm(startUs / 1000, r.result())
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
            runCatching { channel?.close() }
        }
    }
}

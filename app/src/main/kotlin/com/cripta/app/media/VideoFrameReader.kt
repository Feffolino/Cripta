package com.cripta.app.media

import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.cripta.app.media.face.FaceImage
import com.cripta.app.media.face.YuvFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import kotlin.coroutines.coroutineContext

/**
 * The frames of one video read in the app's own process, for the face scan: a MediaExtractor over
 * the decrypting channel and one MediaCodec, opened once and kept for every frame asked.
 * MediaMetadataRetriever runs in the media server and pulls the stream back through binder for
 * each frame (2 to 6 s a frame on a POCO X8 Pro); here a frame is a seek and a few decodes. The
 * decoder's YUV planes go straight to a [FaceImage] ([YuvFrame]): no JPEG, no Bitmap, nothing on disk.
 *
 * Not thread safe: one caller at a time.
 */
internal class VideoFrameReader private constructor(
    private val extractor: MediaExtractor,
    private val format: MediaFormat,
    private val mime: String,
    private val decoders: ArrayDeque<String>,
    /** The track's duration in ms (0 when the container does not say). */
    val durationMs: Long,
    private val rotation: Int,
) : Closeable {
    /** No decoder here gives a picture that can be read: the caller falls back to the retriever. */
    class Unreadable : Exception("no decoder gives a readable picture")

    /** The decoder in use gave a picture that cannot be read (a private layout) or broke. */
    private class BadDecoder : Exception()

    private var codec: MediaCodec? = null
    private var defaultTried = false
    /** Decoders already tried (the default one is also among the software ones at times). */
    private val tried = HashSet<String>()
    /** The last moment asked lies past the end of the stream: no decoder's fault. */
    private var pastEnd = false
    /** The decoder in use has given a picture: a frame it misses is that frame's fault, not its. */
    private var proven = false
    /** Presentation time of the last frame decoded since the last seek (-1: none). */
    private var lastUs = -1L
    /** The end of the stream was queued: the decoder takes nothing more until the next seek. */
    private var inputDone = false
    /**
     * Seeking lands far before the time asked (no seek index, or a single key frame): frames are
     * reached by decoding on from where the reading is, not from the start again for each one.
     */
    private var noIndex = false
    private val info = MediaCodec.BufferInfo()
    private var yBytes = ByteArray(0)
    private var uBytes = ByteArray(0)
    private var vBytes = ByteArray(0)

    /**
     * The frame at [tMs], longest side at most [side], turned upright. [exact]: the frame at that
     * very moment (decoded on from the key frame before it); else the nearest key frame, as
     * MediaMetadataRetriever.OPTION_CLOSEST_SYNC. Null: this moment cannot be read (past the end,
     * or not reached in time). Throws [Unreadable] when no decoder can read this video.
     */
    suspend fun frame(tMs: Long, exact: Boolean, side: Int): FaceImage? {
        while (true) {
            val c = codec ?: nextDecoder() ?: throw Unreadable()
            try {
                pastEnd = false
                val img = read(c, tMs * 1000, exact, side)
                if (img != null) { proven = true; return img }
                // A decoder that never gave a picture and misses its first frame too is not trusted:
                // the next one tries the same moment.
                if (proven || pastEnd) return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BadDecoder) {
                // Next decoder.
            } catch (e: IllegalStateException) {
                // MediaCodec.CodecException and friends: the decoder broke; the next one goes on.
            } catch (e: IllegalArgumentException) {
                // The same, from a decoder refusing a sample.
            }
            dropDecoder()
        }
    }

    private suspend fun read(c: MediaCodec, tUs: Long, exact: Boolean, side: Int): FaceImage? {
        val target: Long
        if (noIndex && !inputDone && lastUs in 0..tUs) {
            // Ahead of where the reading is, in a file that cannot seek: decode on from here.
            target = tUs
        } else {
            extractor.seekTo(tUs, if (exact || noIndex) MediaExtractor.SEEK_TO_PREVIOUS_SYNC else MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val landed = extractor.sampleTime
            if (landed < 0) { pastEnd = true; return null }
            val far = tUs - landed > FAR_US
            if (far) noIndex = true
            c.flush()
            lastUs = -1L
            inputDone = false
            // The key frame itself, unless the exact moment is asked or the key frame is far behind it.
            target = if (exact || far) tUs else landed
        }
        val started = SystemClock.elapsedRealtime()
        var decoded = 0
        while (true) {
            coroutineContext.ensureActive()
            // A file without a seek index may need minutes of decoding to reach a late frame: it
            // is given up after a while (the next frame goes on from where this one stopped).
            if (decoded > MAX_DECODED || SystemClock.elapsedRealtime() - started > BUDGET_MS) return null
            if (!inputDone) {
                val inIdx = c.dequeueInputBuffer(TICK_US)
                if (inIdx >= 0) {
                    val buf = c.getInputBuffer(inIdx) ?: throw BadDecoder()
                    // A channel closed by a cancelled scan reads as the end: the loop then stops above.
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        c.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = c.dequeueOutputBuffer(info, TICK_US)
            if (outIdx < 0) continue
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            val picture = info.size > 0 || !eos
            if (picture) { decoded++; lastUs = info.presentationTimeUs }
            if (picture && info.presentationTimeUs >= target) {
                val img = try {
                    c.getOutputImage(outIdx)?.use { toFace(c, it, side) }
                } catch (e: IllegalStateException) { null }
                c.releaseOutputBuffer(outIdx, false)
                // There but not readable as an image: this decoder will not do better on the next ones.
                return img ?: throw BadDecoder()
            }
            c.releaseOutputBuffer(outIdx, false)
            if (eos) return null
        }
    }

    private fun toFace(c: MediaCodec, image: Image, side: Int): FaceImage? {
        if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) return null
        val crop = image.cropRect.takeIf { !it.isEmpty } ?: android.graphics.Rect(0, 0, image.width, image.height)
        if (!crop.intersect(0, 0, image.width, image.height)) return null
        val (yp, up, vp) = image.planes
        yBytes = copy(yp.buffer, yBytes)
        uBytes = copy(up.buffer, uBytes)
        vBytes = copy(vp.buffer, vBytes)
        if (up.rowStride != vp.rowStride || up.pixelStride != vp.pixelStride) return null
        val out = runCatching { c.outputFormat }.getOrNull()
        fun key(k: String) = out?.takeIf { it.containsKey(k) }?.let { runCatching { it.getInteger(k) }.getOrNull() }
        val full = key(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_FULL
        val bt709 = key(MediaFormat.KEY_COLOR_STANDARD) == MediaFormat.COLOR_STANDARD_BT709
        val planes = YuvFrame.Planes(yBytes, yp.rowStride, uBytes, vBytes, up.rowStride, up.pixelStride)
        return YuvFrame.toFace(planes, crop.left, crop.top, crop.width(), crop.height(), side, rotation, full, bt709)
    }

    /** [buf]'s bytes into [into] (grown when too small: reused from frame to frame). */
    private fun copy(buf: java.nio.ByteBuffer, into: ByteArray): ByteArray {
        val b = buf.duplicate().apply { rewind() }
        val dest = if (into.size >= b.remaining()) into else ByteArray(b.remaining())
        b.get(dest, 0, b.remaining())
        return dest
    }

    /** The next decoder to try (the default one first, then the software ones), started. */
    private fun nextDecoder(): MediaCodec? {
        while (true) {
            val name = if (!defaultTried) null else decoders.removeFirstOrNull() ?: return null
            if (name == null) defaultTried = true else if (name in tried) continue
            val c = runCatching { if (name == null) MediaCodec.createDecoderByType(mime) else MediaCodec.createByCodecName(name) }.getOrNull()
                ?: continue
            tried += c.name
            val ok = runCatching {
                // A readable YUV layout, not the decoder's own tiled one. No surface: Image output.
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                c.configure(format, null, null, 0)
                c.start()
            }.isSuccess
            if (!ok) { runCatching { c.release() }; continue }
            codec = c
            proven = false
            // A new decoder starts with a seek.
            lastUs = -1L
            inputDone = false
            return c
        }
    }

    private fun dropDecoder() {
        codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
        codec = null
        proven = false
        lastUs = -1L
    }

    override fun close() {
        dropDecoder()
        runCatching { extractor.release() }
    }

    companion object {
        /** Polling step of the decoder's queues. */
        private const val TICK_US = 10_000L
        /** A seek landing this far before the time asked: the file cannot really seek. */
        private const val FAR_US = 20_000_000L
        /** At most this many frames decoded, and this long, to reach one frame. */
        private const val MAX_DECODED = 1_800
        private const val BUDGET_MS = 20_000L

        /** A reader for the video track of [source], or null when it has none or cannot be opened. */
        fun open(source: MediaDataSource): VideoFrameReader? {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(source)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                } ?: run { extractor.release(); return null }
                val format = extractor.getTrackFormat(track)
                extractor.selectTrack(track)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                val durUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                VideoFrameReader(extractor, format, mime, ArrayDeque(softwareDecoders(mime)), durUs / 1000, rotation)
            } catch (e: Exception) {
                runCatching { extractor.release() }
                null
            }
        }

        /**
         * Software decoders for [mime] (e.g. c2.android.av1.decoder / dav1d), in platform order: for
         * when the default (hardware) one only gives frames in a private layout, as AV1 ones often do.
         */
        fun softwareDecoders(mime: String): List<String> =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.isSoftwareOnly && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
                .map { it.name }
    }
}

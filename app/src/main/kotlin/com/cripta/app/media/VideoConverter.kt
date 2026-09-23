package com.cripta.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Transcodes a plaintext video file to a standard MP4 (H.264 + AAC) using media3 Transformer.
 *
 * Container formats without a seek index (e.g. MPEG program/transport streams) play but can't be
 * seeked; re-wrapping them as MP4 gives a proper seek table and broad compatibility. Transformer
 * runs its decode -> encode -> mux pipeline internally, so we avoid hand-rolling MediaCodec/EGL.
 *
 * Works on plaintext temp files only (the caller decrypts in and encrypts out), which keeps the
 * Transformer setup simple and reliable. Must be invoked on the main thread (Transformer requires
 * a Looper); callers use Dispatchers.Main.
 */
@Singleton
@UnstableApi
class VideoConverter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Transcode [src] into [out] as MP4 (H.264/AAC). Suspends until done; throws on failure.
     * Call on the main thread.
     */
    suspend fun toMp4(src: File, out: File, videoBitrate: Int? = null, onProgress: (Int) -> Unit = {}): Unit =
        export(MediaItem.fromUri(Uri.fromFile(src)), out, maxHeight = null, videoBitrate = videoBitrate, onProgress = onProgress)

    /**
     * Download and remux/transcode a remote video (direct link or HLS .m3u8) into [out] as MP4.
     * [maxHeight] caps the output resolution (quality); null keeps the source resolution.
     * Call on the main thread.
     */
    suspend fun downloadToMp4(url: String, out: File, maxHeight: Int?, onProgress: (Int) -> Unit = {}): Unit =
        export(MediaItem.fromUri(url), out, maxHeight, null, onProgress)

    private suspend fun export(
        source: MediaItem,
        out: File,
        maxHeight: Int?,
        videoBitrate: Int?,
        onProgress: (Int) -> Unit,
    ): Unit = suspendCancellableCoroutine { cont ->
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var transformer: Transformer
        val holder = androidx.media3.transformer.ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                if (!cont.isActive) return
                val state = runCatching { transformer.getProgress(holder) }.getOrNull()
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                handler.postDelayed(this, 500)
            }
        }
        // Encoder: fall back to a supported configuration instead of failing (or producing garbage)
        // when the device encoder rejects the source resolution/profile; request a bitrate that fits
        // the resolution so the output isn't blocky.
        val encoderSettings = androidx.media3.transformer.VideoEncoderSettings.Builder()
            .apply { if (videoBitrate != null) setBitrate(videoBitrate) }
            .build()
        val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .build()
        // Tolerant extractors for MPEG-PS/TS sources (no seek index, timestamp discontinuities).
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractors))
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacks(poll)
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    handler.removeCallbacks(poll)
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            .build()

        val effects = if (maxHeight != null) {
            Effects(emptyList(), listOf(Presentation.createForHeight(maxHeight)))
        } else {
            Effects.EMPTY
        }
        val edited = EditedMediaItem.Builder(source).setEffects(effects).build()
        // HDR (10-bit HLG/PQ) sources: tone-map to SDR. Without this, H.264 SDR output from an HDR
        // phone clip comes out with washed/green/garbled frames.
        val composition = Composition.Builder(androidx.media3.transformer.EditedMediaItemSequence(edited))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        cont.invokeOnCancellation { handler.removeCallbacks(poll); runCatching { transformer.cancel() } }
        transformer.start(composition, out.absolutePath)
        handler.post(poll)
    }
}

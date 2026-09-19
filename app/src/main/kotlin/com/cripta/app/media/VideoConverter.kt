package com.cripta.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
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
    suspend fun toMp4(src: File, out: File, onProgress: (Int) -> Unit = {}): Unit =
        suspendCancellableCoroutine { cont ->
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
            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
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

            cont.invokeOnCancellation { handler.removeCallbacks(poll); runCatching { transformer.cancel() } }
            transformer.start(MediaItem.fromUri(Uri.fromFile(src)), out.absolutePath)
            handler.post(poll)
        }
}

package com.cripta.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces small thumbnails for images/videos by decrypting in memory only.
 * Two-level cache: in-memory LRU + a persistent on-disk cache of thumbnails encrypted
 * with the session DEK, so covers load fast after the app is closed/reopened without
 * re-decrypting the full media. Plaintext thumbnails are never written to disk.
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext context: Context,
    private val repo: VaultRepository,
) {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val diskDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val target = 320

    /** How many covers to generate at once during a background [prewarm] pass. Kept at 1 so the
     *  background video decodes never pile up on the device's few hardware codec instances. */
    private val PREWARM_CONCURRENCY = 1

    suspend fun load(file: FileEntity): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(file.id)?.let { return@withContext it }
        // Persistent sealed cache.
        readDisk(file.id)?.let { cache.put(file.id, it); return@withContext it }
        val bmp = runCatching {
            when {
                VaultRepository.isImage(file.mimeType) -> imageThumb(file)
                VaultRepository.isVideo(file.mimeType) -> videoThumb(file)
                else -> null
            }
        }.getOrNull()
        if (bmp != null) {
            cache.put(file.id, bmp)
            runCatching { writeDisk(file.id, bmp) }
        }
        bmp
    }

    /**
     * Warm the cover cache for [files] in the background so thumbnails are ready before their cell
     * scrolls into view (otherwise each cover is only generated on demand — a slow decrypt+decode
     * for videos — so covers "appear only after lingering" on the screen). Processed a few at a
     * time to avoid saturating the codec/CPU; already-cached items return immediately inside load().
     */
    suspend fun prewarm(files: List<FileEntity>) = withContext(Dispatchers.IO) {
        files.chunked(PREWARM_CONCURRENCY).forEach { chunk ->
            supervisorScope { chunk.forEach { f -> launch { runCatching { load(f) } } } }
        }
    }

    private fun readDisk(id: String): Bitmap? {
        if (!repo.hasKey) return null
        val f = File(diskDir, id)
        if (!f.exists()) return null
        val plain = repo.openThumb(f.readBytes()) ?: return null
        return BitmapFactory.decodeByteArray(plain, 0, plain.size)
    }

    private fun writeDisk(id: String, bmp: Bitmap) {
        if (!repo.hasKey) return
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        File(diskDir, id).writeBytes(repo.sealThumb(bos.toByteArray()))
    }

    /** Remove a cached thumbnail (call after a file is deleted). */
    fun evict(id: String) {
        cache.remove(id)
        runCatching { File(diskDir, id).delete() }
    }

    private fun imageThumb(file: FileEntity): Bitmap? {
        // Two decrypt passes over a stream instead of holding the whole file in a ByteArray:
        // first reads only the bounds, second decodes downsampled to ~target.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        repo.decryptingStream(file).use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = calcSample(bounds.outWidth, bounds.outHeight, target)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return repo.decryptingStream(file).use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Which frame of a video to use as its cover, chosen by the user when regenerating. */
    enum class VideoCover { AUTO, START, MIDDLE, END, RANDOM }

    /**
     * Regenerate the cover of a video using [cover] and replace the cached thumbnail (memory +
     * sealed disk cache) with it, so the new cover survives app restarts. Returns the new bitmap,
     * or the existing cover unchanged if extraction produced nothing (so a failed pick never wipes
     * a good cover). No-op for non-video files.
     */
    suspend fun regenerateVideoCover(file: FileEntity, cover: VideoCover): Bitmap? =
        withContext(Dispatchers.IO) {
            if (!VaultRepository.isVideo(file.mimeType)) return@withContext cache.get(file.id)
            val bmp = withRetriever(file) { coverFrame(it, cover) }
                ?: return@withContext cache.get(file.id)
            cache.put(file.id, bmp)
            runCatching { writeDisk(file.id, bmp) }
            bmp
        }

    private fun videoThumb(file: FileEntity): Bitmap? = withRetriever(file) { extractFrame(it) }

    /** Open a [MediaMetadataRetriever] over the decrypted video and run [block], releasing after. */
    private inline fun <T> withRetriever(file: FileEntity, block: (MediaMetadataRetriever) -> T): T? {
        val retriever = MediaMetadataRetriever()
        var channel: SeekableByteChannel? = null
        return try {
            channel = repo.seekableChannel(file)
            retriever.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            block(retriever)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { channel?.close() }
        }
    }

    /** Pick a frame according to [cover]; falls back to the automatic best frame when the chosen
     *  position yields nothing (e.g. unknown duration or an undecodable spot). */
    private fun coverFrame(r: MediaMetadataRetriever, cover: VideoCover): Bitmap? {
        if (cover == VideoCover.AUTO) return extractFrame(r)
        val durationUs =
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
        val timeUs = when (cover) {
            VideoCover.START -> 0L
            VideoCover.MIDDLE -> durationUs / 2
            VideoCover.END -> durationUs * 9 / 10
            VideoCover.RANDOM -> if (durationUs > 0) (Math.random() * durationUs).toLong() else 0L
            VideoCover.AUTO -> 0L // handled above
        }
        return frameAt(r, timeUs) ?: extractFrame(r)
    }

    /** Frame closest to [timeUs], trying the scaled paths first and a hand-scaled full decode last. */
    private fun frameAt(r: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        runCatching {
            r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, target, target)
        }.getOrNull()?.let { return it }
        runCatching {
            r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, target, target)
        }.getOrNull()?.let { return it }
        val full = runCatching {
            r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull() ?: return null
        return scaleDown(full, target)
    }

    /**
     * Grab a representative frame, trying progressively looser strategies. A single call to
     * [MediaMetadataRetriever.getScaledFrameAtTime] returns null for some containers/codecs
     * (no sync frame near the requested time, or the scaled decode path unsupported), which is
     * what left those videos with a gray cover. Falling back to a non-sync frame, then to an
     * unscaled decode downscaled by hand, recovers a thumbnail in those cases.
     */
    private fun extractFrame(r: MediaMetadataRetriever): Bitmap? {
        // 1. Representative frame, scaled by the framework (fast, works for most videos).
        runCatching {
            r.getScaledFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, target, target)
        }.getOrNull()?.let { return it }
        // 2. First sync frame from the start.
        runCatching {
            r.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, target, target)
        }.getOrNull()?.let { return it }
        // 3. Closest frame (not necessarily a keyframe) — handles clips whose only sync frame
        //    sits well past the start.
        runCatching {
            r.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST, target, target)
        }.getOrNull()?.let { return it }
        // 4. Last resort: full-size decode, downscaled here. Some codecs fail the scaled path
        //    above but decode a full frame fine.
        val full = runCatching { r.getFrameAtTime(-1L) }.getOrNull()
            ?: runCatching { r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST) }.getOrNull()
            ?: return null
        return scaleDown(full, target)
    }

    /** Scale [src] down so its longest side is at most [target] px, preserving aspect ratio. */
    private fun scaleDown(src: Bitmap, target: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0 || (w <= target && h <= target)) return src
        val ratio = minOf(target.toFloat() / w, target.toFloat() / h)
        val dst = Bitmap.createScaledBitmap(
            src, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true
        )
        if (dst != src) src.recycle()
        return dst
    }

    private fun calcSample(w: Int, h: Int, target: Int): Int {
        var sample = 1
        var halfW = w / 2
        var halfH = h / 2
        while (halfW / sample >= target && halfH / sample >= target) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private class ChannelMediaDataSource(
        private val channel: SeekableByteChannel,
        private val length: Long,
    ) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= length) return -1
            channel.position(position)
            val bb = ByteBuffer.wrap(buffer, offset, size)
            var read = 0
            while (bb.hasRemaining()) {
                val n = channel.read(bb)
                if (n <= 0) break
                read += n
            }
            return if (read == 0) -1 else read
        }
        override fun getSize(): Long = length
        override fun close() { runCatching { channel.close() } }
    }
}

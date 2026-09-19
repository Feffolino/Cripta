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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Per-file cover version. Bumped by [regenerate] so on-screen thumbnails whose Compose
     * `produceState` keys include this version re-run their loader after a manual regeneration
     * (eviction alone can't re-trigger a producer keyed on the file id).
     */
    private val _versions = MutableStateFlow<Map<String, Int>>(emptyMap())
    val versions: StateFlow<Map<String, Int>> = _versions

    /**
     * Ids whose extraction already failed this session. Without this, a file that can't produce a
     * thumbnail (corrupt/unsupported) is re-decrypted in full and re-run through
     * MediaMetadataRetriever on every scroll/recomposition — a needless CPU/battery drain that also
     * janks large grids. Cleared per id by [evict]/[regenerate] so a manual retry still runs.
     */
    private val failed = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    suspend fun load(file: FileEntity): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(file.id)?.let { return@withContext it }
        // Persistent sealed cache.
        readDisk(file.id)?.let { cache.put(file.id, it); return@withContext it }
        // Skip files already known to yield nothing this session.
        if (file.id in failed) return@withContext null
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
        } else {
            failed.add(file.id)
        }
        bmp
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
        failed.remove(id)
        runCatching { File(diskDir, id).delete() }
    }

    /**
     * Force-rebuild a cover: drop both cache levels, recompute from the source, and bump the
     * file's [versions] entry so any on-screen thumbnail re-keyed on it reloads the fresh bitmap.
     * Returns the new bitmap (null if extraction still fails, e.g. an unreadable file).
     */
    suspend fun regenerate(file: FileEntity): Bitmap? {
        evict(file.id)
        val bmp = load(file)
        _versions.value = _versions.value + (file.id to ((_versions.value[file.id] ?: 0) + 1))
        return bmp
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

    private fun videoThumb(file: FileEntity): Bitmap? {
        val retriever = MediaMetadataRetriever()
        var channel: SeekableByteChannel? = null
        return try {
            channel = repo.seekableChannel(file)
            retriever.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            extractFrame(retriever, file)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { channel?.close() }
        }
    }

    /**
     * Robust video-frame extraction. The single `getScaledFrameAtTime(-1, CLOSEST_SYNC)` call
     * returns null for many videos (sparse keyframes, VBR, some HEVC/MPEG containers), which left
     * their cover permanently grey. Try progressively looser strategies — matching the fallback
     * chain proven in DuplicateScanner — and downscale manually when only a full-size frame is
     * available. Returns the first non-null frame, or null only when nothing can be decoded.
     */
    private fun extractFrame(retriever: MediaMetadataRetriever, file: FileEntity): Bitmap? {
        // Fast path: representative scaled sync frame.
        retriever.getScaledFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, target, target)
            ?.let { return it }

        val durMs = file.durationMs
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        // Candidate timestamps (µs): an interior point (avoids black lead-in), then the very start.
        val candidates = buildList {
            if (durMs != null && durMs > 0) add((durMs * 1000L) / 3)
            add(0L)
        }
        for (us in candidates) {
            val frame = retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
            if (frame != null) return downscale(frame)
        }
        // Last resort: any frame at all.
        return retriever.getFrameAtTime()?.let { downscale(it) }
    }

    /** Downscale a decoded frame to roughly [target] on its long edge, recycling the source. */
    private fun downscale(bmp: Bitmap): Bitmap {
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge <= target) return bmp
        val scale = target.toFloat() / longEdge
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
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

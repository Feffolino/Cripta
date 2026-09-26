package com.cripta.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
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
    /**
     * Covers the user picked explicitly (Rigenera copertina). Kept under filesDir, NOT cacheDir:
     * the system may purge the cache at any time, which silently brought back the automatic cover.
     * Sealed with the DEK exactly like the cache entries.
     */
    private val customDir = File(context.filesDir, "covers").apply { mkdirs() }
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

    /** How many covers to generate at once during a background [prewarm] pass. Kept at 1 so the
     *  background video decodes never pile up on the device's few hardware codec instances. */
    private val PREWARM_CONCURRENCY = 1

    suspend fun load(file: FileEntity): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(file.id)?.let { return@withContext it }
        // A user-chosen cover always wins over the automatic one.
        readSealed(File(customDir, file.id))?.let { cache.put(file.id, it); return@withContext it }
        // Persistent sealed cache.
        readDisk(file.id)?.let { cache.put(file.id, it); return@withContext it }
        // Skip files already known to yield nothing this session.
        if (file.id in failed) return@withContext null
        val bmp = runCatching {
            when {
                VaultRepository.isImage(file.mimeType) -> imageThumb(file)
                VaultRepository.isVideo(file.mimeType) -> videoFrame(file, null)
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
        backfillResolutions(files)
    }

    /** Ids already probed for a resolution this session (hit or miss), so each is read once. */
    private val resolutionTried = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * Record the resolution of media imported before it was stored (drives the HD/4K cover badge).
     * Saved in small batches so the grid refreshes a few times, not once per file.
     */
    private suspend fun backfillResolutions(files: List<FileEntity>) {
        val todo = files.filter {
            it.width == null && it.id !in resolutionTried &&
                (VaultRepository.isVideo(it.mimeType) || VaultRepository.isImage(it.mimeType))
        }
        val found = HashMap<String, Pair<Int, Int>>()
        try {
            for (f in todo) {
                val res = mediaResolution(f)
                // Marked only once the probe actually finished: a pass cancelled mid-file (the grid
                // restarts prewarm after each saved batch) retries that file on the next pass.
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                resolutionTried += f.id
                res?.let { found[f.id] = it }
                if (found.size >= 24) { runCatching { repo.setResolutions(HashMap(found)) }; found.clear() }
            }
        } finally {
            // Never drop results already read, even when this pass is cancelled.
            if (found.isNotEmpty()) {
                withContext(kotlinx.coroutines.NonCancellable) { runCatching { repo.setResolutions(found) } }
            }
        }
    }

    private fun readDisk(id: String): Bitmap? = readSealed(File(diskDir, id))

    private fun readSealed(f: File): Bitmap? {
        if (!repo.hasKey) return null
        if (!f.exists()) return null
        val plain = repo.openThumb(f.readBytes()) ?: return null
        return BitmapFactory.decodeByteArray(plain, 0, plain.size)
    }

    private fun writeDisk(id: String, bmp: Bitmap) = writeSealed(File(diskDir, id), bmp)

    /**
     * Seal [bmp] into [dest] atomically (temp file + rename), so a crash or a concurrent reader
     * never sees a half-written cover. Returns true once the file is on disk.
     */
    private fun writeSealed(dest: File, bmp: Bitmap): Boolean {
        if (!repo.hasKey) return false
        val bos = ByteArrayOutputStream()
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)) return false
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        tmp.writeBytes(repo.sealThumb(bos.toByteArray()))
        if (!tmp.renameTo(dest)) {
            dest.delete()
            if (!tmp.renameTo(dest)) { tmp.delete(); return false }
        }
        return true
    }

    /** Remove a cached thumbnail (call after a file is deleted). */
    fun evict(id: String) {
        cache.remove(id)
        failed.remove(id)
        runCatching { File(diskDir, id).delete() }
        runCatching { File(customDir, id).delete() }
    }

    /** Give [toId] the same user-chosen cover as [fromId], if it has one (e.g. after an MP4 conversion). */
    fun copyCustomCover(fromId: String, toId: String) {
        runCatching {
            val src = File(customDir, fromId)
            if (src.exists()) {
                src.copyTo(File(customDir, toId), overwrite = true)
                cache.remove(toId)
                runCatching { File(diskDir, toId).delete() }
                bumpVersion(toId)
            }
        }
    }

    /**
     * Display resolution (width x height, rotation applied) of an image or video, or null when it
     * can't be read. Used to suggest which duplicate is the best quality copy to keep.
     */
    suspend fun mediaResolution(file: FileEntity): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            when {
                VaultRepository.isImage(file.mimeType) -> {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    repo.decryptingStream(file).use { BitmapFactory.decodeStream(it, null, o) }
                    if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
                }
                VaultRepository.isVideo(file.mimeType) -> withRetriever(file) { r ->
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    when {
                        w <= 0 || h <= 0 -> null
                        rot == 90 || rot == 270 -> h to w
                        else -> w to h
                    }
                }
                else -> null
            }
        }.getOrNull()
    }

    /** Forget this session's failed-extraction ids so those covers are retried (pull-to-refresh). */
    fun clearFailed() = failed.clear()

    /** Bump a file's version so any cover keyed on it reloads (re-runs the full extraction chain). */
    fun invalidate(id: String) = bumpVersion(id)

    /**
     * Detailed diagnostics for a video (codec, coded size, crop rect, pixel aspect, rotation,
     * color info) — used by the viewer's Info dialog so the user can share why a cover looks wrong.
     * Reads over the in-memory channel; returns a human-readable multi-line string.
     */
    suspend fun videoDiagnostics(file: FileEntity): String = withContext(Dispatchers.IO) {
        if (!VaultRepository.isVideo(file.mimeType)) return@withContext ""
        val sb = StringBuilder()
        // MediaExtractor track format (has crop/sar/coded size keys via toString()).
        run {
            val extractor = MediaExtractor()
            var channel: SeekableByteChannel? = null
            try {
                channel = repo.seekableChannel(file)
                extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: "?"
                    if (mime.startsWith("video/")) {
                        val w = if (f.containsKey(MediaFormat.KEY_WIDTH)) f.getInteger(MediaFormat.KEY_WIDTH) else -1
                        val h = if (f.containsKey(MediaFormat.KEY_HEIGHT)) f.getInteger(MediaFormat.KEY_HEIGHT) else -1
                        val rot = if (f.containsKey(MediaFormat.KEY_ROTATION)) f.getInteger(MediaFormat.KEY_ROTATION) else 0
                        sb.append("Codec: ").append(mime).append('\n')
                        sb.append("Risoluzione: ").append(w).append("x").append(h).append('\n')
                        sb.append("Rotazione: ").append(rot).append("°\n")
                        sb.append("Track format:\n").append(f.toString()).append('\n')
                        break
                    }
                }
            } catch (e: Exception) {
                sb.append("Probe extractor fallito: ").append(e.javaClass.simpleName).append('\n')
            } finally {
                runCatching { extractor.release() }
                runCatching { channel?.close() }
            }
        }
        // MediaMetadataRetriever metadata (rotation/dimensions as MMR sees them).
        withRetriever(file) { r ->
            fun meta(key: Int, label: String) {
                r.extractMetadata(key)?.let { sb.append(label).append(": ").append(it).append('\n') }
            }
            meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "MMR width")
            meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "MMR height")
            meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, "MMR rotation")
            meta(MediaMetadataRetriever.METADATA_KEY_BITRATE, "Bitrate")
            meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE, "Capture fps")
        }
        sb.toString().trim()
    }

    /**
     * Force-rebuild a cover: drop both cache levels, recompute from the source, and bump the
     * file's [versions] entry so any on-screen thumbnail re-keyed on it reloads the fresh bitmap.
     * Returns the new bitmap (null if extraction still fails, e.g. an unreadable file).
     */
    suspend fun regenerate(file: FileEntity): Bitmap? {
        evict(file.id)
        val bmp = load(file)
        bumpVersion(file.id)
        return bmp
    }

    /**
     * Bump a file's cover version so every surface keyed on [versions] (grid, Home/Favorites
     * shelves, the viewer) reloads it. Most-recent last; the map is capped so it can't grow
     * without bound.
     */
    private fun bumpVersion(id: String) {
        val next = LinkedHashMap(_versions.value)
        val v = (next.remove(id) ?: 0) + 1
        next[id] = v
        while (next.size > 256) next.remove(next.keys.first())
        _versions.value = next
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
            if (!VaultRepository.isVideo(file.mimeType)) return@withContext null
            val bmp = videoFrame(file, cover)
                ?: return@withContext null
            cache.put(file.id, bmp)
            failed.remove(file.id)                 // a good cover now exists: allow reload paths
            // Persist as a user-chosen cover (survives cache purges / restarts) and verify it reads
            // back; the automatic cache entry is dropped so it can never shadow the chosen one.
            val saved = runCatching { writeSealed(File(customDir, file.id), bmp) }.getOrDefault(false) &&
                readSealed(File(customDir, file.id)) != null
            if (saved) {
                runCatching { File(diskDir, file.id).delete() }
            } else {
                android.util.Log.w("ThumbnailLoader", "custom cover not persisted for ${file.id}")
                runCatching { writeDisk(file.id, bmp) }
            }
            bumpVersion(file.id)                    // refresh shelves/viewer keyed on versions
            bmp
        }

    /**
     * Extract a cover frame for a video. Tries the fast in-memory path (channel-backed data source,
     * no plaintext on disk) first; if that yields nothing — some containers (MPEG PS/TS, certain
     * MKV/HEVC) fail [MediaMetadataRetriever] over a custom MediaDataSource but decode fine from a
     * real file — it falls back to decrypting to a temporary file (shredded afterwards, like the
     * duplicate scanner and transcoder already do). [cover] null = automatic best frame.
     */
    private fun videoFrame(file: FileEntity, cover: VideoCover?): Bitmap? {
        val raw = videoFrameRaw(file, cover) ?: return null
        // Correct non-square pixels (SAR): some clips are coded e.g. 620x348 but meant to display
        // 197x348. Decoders/MMR return the coded frame, which looks horizontally squished until the
        // pixel aspect ratio is applied. Read at generation time only (covers are cached after).
        return centerSquare(applyPixelAspect(raw, readSampleAspect(file)))
    }

    /** Sample aspect ratio (display pixel width / height) for the video, or 1.0 when square. */
    private fun readSampleAspect(file: FileEntity): Double {
        val extractor = MediaExtractor()
        var channel: SeekableByteChannel? = null
        try {
            channel = repo.seekableChannel(file)
            extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                fun key(k: String) = if (f.containsKey(k)) runCatching { f.getInteger(k) }.getOrNull() else null
                val sarW = key("sar-width"); val sarH = key("sar-height")
                if (sarW != null && sarH != null && sarW > 0 && sarH > 0) return sarW.toDouble() / sarH
                val dispW = key("display-width"); val dispH = key("display-height")
                val codW = key(MediaFormat.KEY_WIDTH); val codH = key(MediaFormat.KEY_HEIGHT)
                if (dispW != null && dispH != null && codW != null && codH != null &&
                    dispW > 0 && dispH > 0 && codW > 0 && codH > 0
                ) return (dispW.toDouble() / dispH) / (codW.toDouble() / codH)
                return 1.0
            }
            return 1.0
        } catch (e: Exception) {
            return 1.0
        } finally {
            runCatching { extractor.release() }
            runCatching { channel?.close() }
        }
    }

    /** Rescale a frame's width by [sar] so its pixels become square, fixing anamorphic videos. */
    private fun applyPixelAspect(bmp: Bitmap, sar: Double): Bitmap {
        if (sar <= 0.0 || kotlin.math.abs(sar - 1.0) < 0.02) return bmp
        val newW = (bmp.width * sar).toInt().coerceAtLeast(1)
        if (newW == bmp.width) return bmp
        val out = Bitmap.createScaledBitmap(bmp, newW, bmp.height, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    /** Crop [src] to a centered square and scale it to [target]px, so covers are uniform 1:1 and
     *  can't appear stretched regardless of the source frame's aspect ratio. */
    private fun centerSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        if (side <= 0) return src
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = if (src.width == side && src.height == side) src
        else Bitmap.createBitmap(src, x, y, side, side)
        val scaled = if (side <= target) cropped else Bitmap.createScaledBitmap(cropped, target, target, true)
        if (cropped !== src) src.recycle()
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    private fun videoFrameRaw(file: FileEntity, cover: VideoCover?): Bitmap? {
        // Passive cover load (cover == null): fast hardware thumbnail via the in-memory channel.
        if (cover == null) {
            withRetriever(file) { extractFrame(it) }?.let { return it }
        }
        // Everything else — explicit regeneration (any frame position) and passive loads that the
        // MediaMetadataRetriever path couldn't handle — goes through MediaExtractor + MediaCodec
        // over the SAME on-demand channel data source. This seeks correctly (unlike MMR over a
        // custom MediaDataSource) and decodes codecs MMR refuses (10-bit HEVC / AV1), all WITHOUT
        // decrypting the whole file to disk (the old temp-file path made large videos very slow and
        // briefly wrote plaintext to disk).
        return decodeFrameWithCodec(file, cover)
    }

    /**
     * Decode a single frame with MediaCodec/MediaExtractor and return it as a [Bitmap]. This is the
     * last-resort path for videos that MediaMetadataRetriever refuses to thumbnail; it drives the
     * platform decoders directly (ByteBuffer/Image output, converted YUV_420_888 -> NV21 -> JPEG ->
     * Bitmap). Returns null on any failure.
     */
    private fun decodeFrameWithCodec(file: FileEntity, cover: VideoCover?): Bitmap? {
        // The default decoder first (usually the hardware one); then the software ones. Some
        // hardware decoders (AV1 in particular) only output frames in a private layout that cannot
        // be read back as an image, so every cover attempt failed with them.
        decodeFrameWith(file, cover, null)?.let { return it }
        val mime = runCatching { videoMime(file) }.getOrNull() ?: return null
        for (name in softwareDecoders(mime)) {
            decodeFrameWith(file, cover, name)?.let { return it }
        }
        return null
    }

    private fun videoMime(file: FileEntity): String? {
        val extractor = MediaExtractor()
        var channel: SeekableByteChannel? = null
        try {
            channel = repo.seekableChannel(file)
            extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            for (i in 0 until extractor.trackCount) {
                val m = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (m?.startsWith("video/") == true) return m
            }
            return null
        } finally {
            runCatching { extractor.release() }
            runCatching { channel?.close() }
        }
    }

    /** Software decoders for [mime] (e.g. c2.android.av1.decoder / dav1d), in platform order. */
    private fun softwareDecoders(mime: String): List<String> =
        android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.isSoftwareOnly && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
            .map { it.name }

    /** One decode attempt with the decoder [codecName] (null = the platform's default for the type). */
    private fun decodeFrameWith(file: FileEntity, cover: VideoCover?, codecName: String?): Bitmap? {
        val extractor = MediaExtractor()
        var channel: SeekableByteChannel? = null
        var codec: MediaCodec? = null
        try {
            channel = repo.seekableChannel(file)
            extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    track = i; format = f; break
                }
            }
            val fmt = format ?: return null
            if (track < 0) return null
            extractor.selectTrack(track)
            // MediaMetadataRetriever auto-applies the container's rotation; a raw MediaCodec decode
            // does not, so a portrait clip stored landscape + rotation=90 came out sideways and
            // looked stretched in the square cell. Read it and rotate the decoded frame to match.
            val rotation = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            val seekUs = when (cover) {
                null, VideoCover.AUTO, VideoCover.START -> 0L
                VideoCover.MIDDLE -> durUs / 2
                VideoCover.END -> durUs * 9 / 10
                VideoCover.RANDOM -> if (durUs > 0) (Math.random() * durUs).toLong() else 0L
            }
            var targetUs = seekUs
            if (seekUs > 0) {
                extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                // A file without a seek index (some WebM/MKV) lands back at the start: decoding
                // minutes of video to reach the frame gave up and the cover failed. Take the first
                // frame from where it landed instead.
                val landed = extractor.sampleTime
                if (landed >= 0 && seekUs - landed > 20_000_000L) targetUs = 0L
            }

            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            codec = if (codecName != null) MediaCodec.createByCodecName(codecName) else MediaCodec.createDecoderByType(mime)
            // Ask for a readable YUV layout (the default can be a private tiled one).
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec.configure(fmt, null, null, 0)   // null surface -> Image/ByteBuffer output
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var guard = 0
            // seekTo only lands on a sync frame, so Start/Middle/End/Random could resolve to the
            // same keyframe and produce identical covers. Decode forward and keep the first frame
            // at/after the requested time so distinct positions give distinct frames.
            while (guard++ < 2000) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val reachedTarget = targetUs <= 0L || info.presentationTimeUs >= targetUs || isEos
                    if (reachedTarget) {
                        val bmp = runCatching {
                            val image = codec.getOutputImage(outIdx)
                            image?.let { val b = imageToBitmap(it); it.close(); b }
                        }.getOrNull()
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bmp != null) return applyRotation(scaleDown(bmp, target), rotation)
                        // The frame is there but cannot be read as an image: this decoder will not do
                        // better on the next ones, let the caller try another decoder.
                        return null
                    } else {
                        // Not yet at the requested position: drop this frame and keep decoding.
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                } else if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    return null
                }
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { channel?.close() }
        }
    }

    /** Rotate [bmp] by [degrees] (0/90/180/270); returns the source unchanged when no rotation. */
    private fun applyRotation(bmp: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return bmp
        val m = Matrix().apply { postRotate(d.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    /** Convert a decoder [Image] (YUV_420_888) to a Bitmap via NV21 + JPEG (robust across devices). */
    private fun imageToBitmap(image: Image): Bitmap? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val w = image.width
        val h = image.height
        val nv21 = yuv420ToNv21(image, w, h)
        val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val out = ByteArrayOutputStream()
        if (!yuv.compressToJpeg(Rect(0, 0, w, h), 90, out)) return null
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** Pack a YUV_420_888 [Image] into an NV21 byte array, honoring row/pixel strides. */
    private fun yuv420ToNv21(image: Image, w: Int, h: Int): ByteArray {
        val ySize = w * h
        val nv21 = ByteArray(ySize + ySize / 2)
        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        var pos = 0
        if (yRowStride == w) {
            yBuf.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until h) {
                yBuf.position(row * yRowStride)
                yBuf.get(nv21, pos, w)
                pos += w
            }
        }
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixStride = uPlane.pixelStride
        val vPixStride = vPlane.pixelStride
        val cw = w / 2
        val ch = h / 2
        var offset = ySize
        for (row in 0 until ch) {
            val uRow = row * uRowStride
            val vRow = row * vRowStride
            for (col in 0 until cw) {
                nv21[offset++] = vBuf.get(vRow + col * vPixStride)   // NV21 = Y then V,U interleaved
                nv21[offset++] = uBuf.get(uRow + col * uPixStride)
            }
        }
        return nv21
    }

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
        // Full-resolution frames only (getFrameAtTime preserves aspect + applies rotation).
        // getScaledFrameAtTime(w,h) forces the frame into w x h, squishing non-square videos into a
        // square — the caller crops to a square itself via centerSquare().
        return runCatching { r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) }.getOrNull()
            ?: runCatching { r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
    }

    /**
     * Grab a representative frame, trying progressively looser strategies. A single call to
     * [MediaMetadataRetriever.getScaledFrameAtTime] returns null for some containers/codecs
     * (no sync frame near the requested time, or the scaled decode path unsupported), which is
     * what left those videos with a gray cover. Falling back to a non-sync frame, then to an
     * unscaled decode downscaled by hand, recovers a thumbnail in those cases.
     */
    private fun extractFrame(r: MediaMetadataRetriever): Bitmap? {
        // Full-resolution frames only (preserve aspect ratio + auto-rotation); the caller squares
        // them via centerSquare(). getScaledFrameAtTime(w,h) would distort non-square videos.
        return runCatching { r.getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
            ?: runCatching { r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
            ?: runCatching { r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST) }.getOrNull()
            ?: runCatching { r.getFrameAtTime() }.getOrNull()
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

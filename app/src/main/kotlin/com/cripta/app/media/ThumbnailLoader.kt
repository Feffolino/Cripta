package com.cripta.app.media

import com.cripta.app.R
import com.cripta.app.str

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces small thumbnails by decrypting in memory only: images, video frames, the first page of
 * a PDF, the first lines of a note, a text or a document.
 * Two-level cache: in-memory LRU + a persistent on-disk cache of thumbnails encrypted
 * with the session DEK, so covers load fast after the app is closed/reopened without
 * re-decrypting the full media. Plaintext thumbnails are never written to disk.
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext context: Context,
    private val repo: VaultRepository,
    session: com.cripta.app.security.SessionManager,
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

    /** Bumped at every lock, before the memory cache is emptied (see [remember]). */
    private val lockEpoch = java.util.concurrent.atomic.AtomicInteger()

    /** One generation per file at a time: a cover shown twice (grid + shelf) or asked for again
     *  while it is generated is decrypted and decoded once, the second caller then reads the cache. */
    private val generating = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** Video frames decoded at once on demand: a fast scroll can't pile up decoders on the few
     *  hardware codec instances (each extra one fails or stalls the others). */
    private val videoDecoders = Semaphore(2)

    /** One PDF rendered at a time (the renderer's engine is shared by the whole process). */
    private val pdfPages = Semaphore(1)

    init {
        // Locked: the decrypted covers held in memory go (they are the vault's content), and so
        // do the failures seen meanwhile (a load while locked fails for want of the key, and the
        // file then kept a grey cover for the rest of the session).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default).launch {
            session.locked.collect { locked -> if (locked) { lockEpoch.incrementAndGet(); cache.evictAll(); failed.clear() } }
        }
        // A restore gave files already here a hand-picked cover: the one drawn before goes, and
        // every surface showing it loads it again (the new file on disk is kept).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default).launch {
            repo.coversReplaced.collect { ids ->
                ids.forEach { id -> cache.remove(id); failed.remove(id); bumpVersion(id) }
            }
        }
    }

    /**
     * Put a decrypted cover in memory, unless the vault locked since [epoch]: a cover decoded while
     * it was locking would otherwise outlive the eviction and stay in RAM while locked.
     */
    private fun remember(id: String, bmp: Bitmap, epoch: Int): Boolean {
        cache.put(id, bmp)
        if (lockEpoch.get() == epoch && repo.hasKey) return true
        cache.remove(id)
        return false
    }

    /** How many covers to generate at once during a background [prewarm] pass. Kept at 1 so the
     *  background video decodes never pile up on the device's few hardware codec instances. */
    private val PREWARM_CONCURRENCY = 1

    suspend fun load(file: FileEntity): Bitmap? = withContext(Dispatchers.IO) {
        // Nothing while locked: no key to decrypt with, and no cached plaintext to hand out.
        if (!repo.hasKey) return@withContext null
        cache.get(file.id)?.let { return@withContext it }
        generating.getOrPut(file.id) { Mutex() }.withLock { loadOnce(file) }
    }

    private suspend fun loadOnce(file: FileEntity): Bitmap? {
        val epoch = lockEpoch.get()
        if (!repo.hasKey) return null
        // Made by another caller while this one waited.
        cache.get(file.id)?.let { return it }
        // A user-chosen cover always wins over the automatic one.
        readSealed(File(customDir, file.id))?.let { return it.takeIf { b -> remember(file.id, b, epoch) } }
        // Persistent sealed cache. Not for notes: their cover is cheap to draw and must follow
        // every edit (and a cover from an older drawing style must not stay).
        val note = VaultRepository.isNote(file.mimeType)
        if (note) runCatching { File(diskDir, file.id).delete() }
        else readDisk(file.id)?.let { return it.takeIf { b -> remember(file.id, b, epoch) } }
        // Skip files already known to yield nothing this session.
        if (file.id in failed) return null
        // A cell scrolled away cancels its request, even while it waits for a decoder: that is not a
        // failure of the file, and must neither be remembered as one nor be swallowed.
        val bmp = try {
            when {
                VaultRepository.isImage(file.mimeType) -> imageThumb(file)
                VaultRepository.isVideo(file.mimeType) -> videoDecoders.withPermit { videoFrame(file, null) }
                VaultRepository.isPdf(file.mimeType) -> pdfPages.withPermit { pdfCover(file) }
                note -> noteCover(file)
                else -> documentText(file)?.let { textCover(it) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        if (bmp != null) {
            if (!remember(file.id, bmp, epoch)) return null
            if (!note) runCatching { writeDisk(file.id, bmp) }
        } else if (repo.hasKey) {
            // Only a real failure: one caused by the vault locking meanwhile would stick.
            failed.add(file.id)
        }
        return bmp
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

    /**
     * Empty the covers cached on disk (they are rebuilt when next shown); the ones chosen by hand
     * are kept. Returns the bytes freed.
     */
    fun clearDiskCache(): Long {
        var freed = 0L
        diskDir.listFiles()?.forEach { f -> val n = f.length(); if (runCatching { f.delete() }.getOrDefault(false)) freed += n }
        return freed
    }

    /** Bump a file's version so any cover keyed on it reloads (re-runs the full extraction chain). */
    fun invalidate(id: String) = bumpVersion(id)

    /**
     * Detailed diagnostics for a video (codec, coded size, crop rect, pixel aspect, rotation,
     * color info) — used by the viewer's Info dialog so the user can share why a cover looks wrong.
     * Reads over the in-memory channel; returns a human-readable multi-line string.
     */
    /** The video track's codec mime type (e.g. video/avc, video/av01), or null if unreadable. */
    suspend fun videoCodec(file: FileEntity): String? = withContext(Dispatchers.IO) {
        if (!VaultRepository.isVideo(file.mimeType)) null else runCatching { videoMime(file) }.getOrNull()
    }

    /**
     * Worth converting to MP4: any video that is not already an H.264/HEVC MP4. An MP4 can hold
     * other codecs (AV1, VP9…) that some devices can neither seek nor thumbnail; by the file type
     * alone those looked "already MP4" and the conversion was never offered.
     */
    suspend fun worthConvertingToMp4(file: FileEntity): Boolean {
        if (!VaultRepository.isVideo(file.mimeType)) return false
        if (file.mimeType != "video/mp4") return true
        val codec = videoCodec(file) ?: return false
        return codec != "video/avc" && codec != "video/hevc"
    }

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
                        sb.append(str(R.string.diag_resolution)).append(w).append("x").append(h).append('\n')
                        sb.append(str(R.string.diag_rotation)).append(rot).append("°\n")
                        sb.append("Track format:\n").append(f.toString()).append('\n')
                        break
                    }
                }
            } catch (e: Exception) {
                sb.append(str(R.string.diag_probe_failed)).append(e.javaClass.simpleName).append('\n')
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

    /**
     * The first page of a PDF, its top as a square: what the document is, at a glance. The renderer
     * needs a seekable descriptor, so the decrypted PDF goes into an anonymous memory file (memfd):
     * in RAM only, gone when closed, never on disk.
     */
    private fun pdfCover(file: FileEntity): Bitmap? {
        if (file.sizeBytes > MAX_PDF_BYTES) return null
        val fd = android.system.Os.memfd_create("cover", 0)
        try {
            // Not closed: closing the stream would close [fd] before the renderer reads it.
            val out = java.io.FileOutputStream(fd)
            repo.decryptingStream(file).use { it.copyTo(out, 64 * 1024) }
            android.system.Os.lseek(fd, 0, android.system.OsConstants.SEEK_SET)
            val pfd = android.os.ParcelFileDescriptor.dup(fd)
            val renderer = try { android.graphics.pdf.PdfRenderer(pfd) } catch (e: Exception) { pfd.close(); throw e }
            return renderer.use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { page ->
                    val h = (page.height * target.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceIn(1, target * 3)
                    val full = Bitmap.createBitmap(target, h, Bitmap.Config.ARGB_8888)
                    full.eraseColor(android.graphics.Color.WHITE)
                    page.render(full, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    if (h <= target) full else Bitmap.createBitmap(full, 0, 0, target, target).also { full.recycle() }
                }
            }
        } finally {
            runCatching { android.system.Os.close(fd) }
        }
    }

    /**
     * The opening text of a note, a text file or a document (Word, OpenDocument), for its cover;
     * null for any other file (its type's icon stays).
     */
    private fun documentText(file: FileEntity): String? {
        val mime = file.mimeType
        val text = when {
            mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" ->
                repo.decryptingStream(file).use { String(readUpTo(it, TEXT_COVER_BYTES), Charsets.UTF_8) }
            mime == DOCX -> zipText(file, "word/document.xml")
            mime == ODT -> zipText(file, "content.xml")
            else -> null
        }
        return text?.takeIf { it.isNotBlank() }
    }

    /** The paragraphs of a zipped document's [entry] (its XML), read in memory, tags left out. */
    private fun zipText(file: FileEntity, entry: String): String? {
        java.util.zip.ZipInputStream(repo.decryptingStream(file)).use { zip ->
            var e = zip.nextEntry
            while (e != null && e.name != entry) e = zip.nextEntry
            if (e == null) return null
            val xml = String(readUpTo(zip, XML_COVER_BYTES), Charsets.UTF_8)
            return xml.replace(Regex("</(w:p|text:p|text:h)>"), "\n").replace(Regex("<[^>]*>?"), "")
                .replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
                    m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
                        ?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: ""
                }
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
                .take(TEXT_COVER_BYTES)
        }
    }

    /** Up to [n] bytes of [input] (fewer at its end). */
    private fun readUpTo(input: java.io.InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val k = input.read(buf, read, n - read)
            if (k < 0) break
            read += k
        }
        return buf.copyOf(read)
    }

    /**
     * A page with the first lines of [text], drawn the same in either theme (it is a sheet of
     * paper, like a PDF's page): the first line in bold when it reads as a title, then as much of
     * the rest as fits.
     */
    private fun textCover(text: String): Bitmap {
        val side = target
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(PAPER)
        val pad = side * 0.085f
        val width = (side - 2 * pad).toInt()
        val lines = text.replace("\r", "").replace('\t', ' ').lines().dropWhile { it.isBlank() }
        val first = lines.firstOrNull()?.trim().orEmpty()
        val rest = lines.drop(1).joinToString("\n").trim()
        val titled = rest.isNotEmpty() && first.length <= 60
        val bold = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = side * 0.074f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val plain = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = INK_SOFT; textSize = side * 0.06f }
        var y = pad
        fun block(s: String, paint: android.text.TextPaint, maxLines: Int) {
            if (s.isEmpty() || maxLines <= 0) return
            val layout = android.text.StaticLayout.Builder.obtain(s, 0, s.length, paint, width)
                .setLineSpacing(0f, 1.2f).setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            canvas.save(); canvas.translate(pad, y); layout.draw(canvas); canvas.restore()
            y += layout.height
        }
        val body = if (titled) { block(first, bold, 2); y += side * 0.03f; rest } else lines.joinToString("\n").trim()
        block(body.take(TEXT_COVER_BYTES), plain, ((side - pad - y) / (plain.fontSpacing * 1.2f)).toInt())
        return bmp
    }

    /** Manrope, as in the app; the platform font if it can't be read. */
    private val coverFont: android.graphics.Typeface by lazy {
        runCatching { context.resources.getFont(com.cripta.app.R.font.manrope_medium) }.getOrDefault(android.graphics.Typeface.DEFAULT)
    }
    private val coverFontBold: android.graphics.Typeface by lazy { android.graphics.Typeface.create(coverFont, 700, false) }

    /**
     * A note's cover: its title, then its text as it reads (headings, bold, lists, check boxes),
     * not the Markdown markers. An empty note shows its title alone.
     */
    private fun noteCover(file: FileEntity): Bitmap {
        val text = repo.decryptingStream(file).use { String(readUpTo(it, TEXT_COVER_BYTES), Charsets.UTF_8) }
        val title = file.originalName.replace(Regex("\\.(md|markdown|txt)$", RegexOption.IGNORE_CASE), "").trim()
        val side = target
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(PAPER)
        val pad = side * 0.085f
        val width = (side - 2 * pad).toInt()
        var y = pad
        fun block(s: CharSequence, paint: android.text.TextPaint, maxLines: Int, spacing: Float) {
            if (s.isEmpty() || maxLines <= 0) return
            val layout = android.text.StaticLayout.Builder.obtain(s, 0, s.length, paint, width)
                .setLineSpacing(0f, spacing).setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            canvas.save(); canvas.translate(pad, y); layout.draw(canvas); canvas.restore()
            y += layout.height
        }
        if (title.isNotEmpty()) {
            val head = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = INK; textSize = side * 0.078f; typeface = coverFontBold
            }
            block(title, head, 2, 1.1f)
            y += side * 0.035f
        }
        val plain = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = side * 0.058f; typeface = coverFont
        }
        val body = noteSpans(text.replace("\r", "").trim('\n', ' '))
        block(body, plain, ((side - pad - y) / (plain.fontSpacing * 1.25f)).toInt(), 1.25f)
        return bmp
    }

    /** The note's Markdown in its reading form (the viewer's), as platform spans for a [android.text.StaticLayout]. */
    private fun noteSpans(text: String): CharSequence {
        val palette = com.cripta.app.ui.note.NoteMarkdown.Palette(
            muted = androidx.compose.ui.graphics.Color(INK_MUTED),
            accent = androidx.compose.ui.graphics.Color(INK_ACCENT),
            codeBackground = androidx.compose.ui.graphics.Color(CODE_PAPER),
        )
        val a = com.cripta.app.ui.note.NoteMarkdown.render(text, palette, keepMarkers = false)
        val out = android.text.SpannableString(a.text)
        for (r in a.spanStyles) {
            val st = r.item
            fun put(span: Any) = out.setSpan(span, r.start, r.end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val bold = (st.fontWeight?.weight ?: 0) >= 600
            if (bold) put(android.text.style.TypefaceSpan(coverFontBold))
            if (bold) put(android.text.style.ForegroundColorSpan(INK))
            if (st.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic) put(android.text.style.StyleSpan(android.graphics.Typeface.ITALIC))
            if (st.fontSize.isEm) put(android.text.style.RelativeSizeSpan(st.fontSize.value))
            if (st.color != androidx.compose.ui.graphics.Color.Unspecified) put(android.text.style.ForegroundColorSpan(st.color.toArgb()))
            if (st.background != androidx.compose.ui.graphics.Color.Unspecified) put(android.text.style.BackgroundColorSpan(st.background.toArgb()))
            if (st.textDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.LineThrough) == true) put(android.text.style.StrikethroughSpan())
            if (st.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace) put(android.text.style.TypefaceSpan("monospace"))
        }
        return out
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
                val cached = runCatching { writeDisk(file.id, bmp) }.isSuccess
                // Nothing reached the disk: a cover shown now would be gone after a restart. Not offered.
                if (!cached) { cache.remove(file.id); return@withContext null }
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

    private fun softwareDecoders(mime: String): List<String> = VideoFrameReader.softwareDecoders(mime)

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

    private companion object {
        /** Larger PDFs keep their icon: the whole file is held in memory to draw its page. */
        const val MAX_PDF_BYTES = 64L * 1024 * 1024
        /** How much of a text is read for its cover: far more than a cover shows. */
        const val TEXT_COVER_BYTES = 4 * 1024
        /** How much of a document's XML is read: its markup takes most of it. */
        const val XML_COVER_BYTES = 256 * 1024
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val ODT = "application/vnd.oasis.opendocument.text"
        /** The sheet and its ink: the app's light page and text colours. */
        const val PAPER = 0xFFF7F8FA.toInt()
        const val INK = 0xFF0F172A.toInt()
        const val INK_SOFT = 0xFF475467.toInt()
        /** Note covers: markers and done items, list bullets and boxes, code. */
        const val INK_MUTED = 0xFF98A2B3.toInt()
        const val INK_ACCENT = 0xFF2563EB.toInt()
        const val CODE_PAPER = 0xFFE9ECF1.toInt()
    }
}

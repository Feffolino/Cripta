package com.cripta.app.data.dedup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.DupApartEntity
import com.cripta.app.data.db.DupPrintEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.media.ChannelMediaDataSource
import com.cripta.app.media.VideoFrameReader
import com.cripta.app.security.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Dedicated scanning module for finding duplicate files in the vault.
 *
 * Kept separate from [VaultRepository] on purpose: scanning is a self-contained, CPU/IO-heavy
 * concern with its own algorithms, and isolating it keeps the vault repository focused on CRUD.
 *
 * Two independent scans are offered:
 *  - [scanExact]   : byte-identical plaintext, found with a cheap multi-phase hash.
 *  - [scanSimilar] : visually near-identical images, found with a perceptual dHash.
 *
 * Both decrypt in memory only. No plaintext is ever written to disk, honouring the vault's
 * security model, and neither needs any network access. Both cooperate with coroutine
 * cancellation (checked between files) so the UI can abort a long scan.
 */
@Singleton
class DuplicateScanner @Inject constructor(
    private val repo: VaultRepository,
    private val session: SessionManager,
) {
    /** A set of files whose decrypted plaintext is byte-identical. */
    data class ExactGroup(val sizeBytes: Long, val files: List<FileEntity>)

    /** A cluster of images that look the same (re-encodes, resizes, minor edits). */
    data class SimilarGroup(val files: List<FileEntity>, val maxDistance: Int)

    /** Exact-scan outcome plus how many files were examined (for user feedback). */
    data class ExactResult(val groups: List<ExactGroup>, val filesScanned: Int)

    /** Similar-scan outcome plus how many media items were examined (for user feedback). */
    data class SimilarResult(val groups: List<SimilarGroup>, val mediaScanned: Int)

    // --- Exact duplicates ------------------------------------------------------------------

    /**
     * Find groups of byte-identical files without hashing more than necessary:
     *  1. group by exact size (no decryption at all — a size mismatch can't be a duplicate);
     *  2. within each size collision, split by a **partial** hash of the head + tail windows
     *     (read via the seekable decrypting channel, so a large video that differs early is
     *     rejected without decrypting the whole file);
     *  3. only the files that still collide get a **full** streaming SHA-256.
     * A size whose files all carry the SHA-256 recorded at import skips 2 and 3: the stored hashes
     * split it, and only the matches are read (head and tail, to leave out an unreadable copy).
     *
     * Groups of 2+ are returned, each sorted oldest-import-first (the natural "keep" candidate),
     * ordered by reclaimable space descending. [onProgress] reports (processed, total) files; the
     * total grows by the files a full hash is read for, so it does not show 100% before them.
     */
    suspend fun scanExact(onProgress: (Int, Int) -> Unit = { _, _ -> }): ExactResult =
        withContext(Dispatchers.IO) {
            val started = SystemClock.elapsedRealtime()
            val all = repo.allFilesSnapshot()
            val bySize = all.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
            var total = bySize.values.sumOf { it.size }
            var done = 0
            onProgress(0, total)
            val confirmed = mutableListOf<ExactGroup>()
            for ((size, sameSize) in bySize) {
                coroutineContext.ensureActive()
                if (sameSize.all { it.contentHash != null }) {
                    // Every file hashed at import: the hashes split the bucket with nothing to
                    // decrypt. Only real matches are read, head and tail, so a copy whose blob
                    // cannot be read is left out as before.
                    for (dupes in sameSize.groupBy { it.contentHash }.values) {
                        coroutineContext.ensureActive()
                        val readable = if (dupes.size > 1) dupes.filter { runCatching { partialHash(it) }.isSuccess } else emptyList()
                        if (readable.size > 1) confirmed += ExactGroup(size, readable.sortedBy { it.importedAt })
                    }
                    done += sameSize.size; onProgress(done, total)
                    continue
                }
                // Phase 1: partial (head+tail) hash to cheaply split the same-size bucket.
                val byPartial = HashMap<String, MutableList<FileEntity>>()
                for (f in sameSize) {
                    coroutineContext.ensureActive()
                    val p = runCatching { partialHash(f) }.getOrNull()
                    done++; onProgress(done, total)
                    if (p != null) byPartial.getOrPut(p) { mutableListOf() }.add(f)
                }
                // Phase 2: full hash only where the partial hash already collided.
                val collided = byPartial.values.filter { it.size > 1 }
                val toRead = collided.sumOf { c -> c.count { it.contentHash == null } }
                if (toRead > 0) { total += toRead; onProgress(done, total) }
                for (candidates in collided) {
                    val byFull = HashMap<String, MutableList<FileEntity>>()
                    for (f in candidates) {
                        coroutineContext.ensureActive()
                        // The hash recorded at import: no need to decrypt the whole file again.
                        val h = f.contentHash ?: fullHashOrNull(f).also { done++; onProgress(done, total) }?.also { repo.setContentHash(f.id, it) }
                        // The group carries the hash it was proved with: without it, a copy edited
                        // later to the same size still passed for the copy the scan had seen.
                        if (h != null) byFull.getOrPut(h) { mutableListOf() }.add(if (f.contentHash == null) f.copy(contentHash = h) else f)
                    }
                    byFull.values.filter { it.size > 1 }.forEach { dupes ->
                        confirmed += ExactGroup(size, dupes.sortedBy { it.importedAt })
                    }
                }
            }
            android.util.Log.i(TAG, "exact scan: ${all.size} files, $total of equal size, ${confirmed.size} groups " +
                "in ${SystemClock.elapsedRealtime() - started} ms")
            ExactResult(confirmed.sortedByDescending { it.sizeBytes * (it.files.size - 1) }, all.size)
        }

    /**
     * Existing live files byte-identical to [file] (e.g. just imported). Only same-size files are
     * candidates, and each is confirmed with the partial then the full hash, so the usual case (no
     * same-size file) costs nothing. Used for the "già presente" warning after an import.
     */
    suspend fun copiesOf(file: FileEntity): List<FileEntity> = withContext(Dispatchers.IO) {
        val candidates = repo.sameSizeAs(file)
        if (candidates.isEmpty()) return@withContext emptyList()
        // Fast path: hashes recorded at import time are compared directly, no decryption at all.
        val own = file.contentHash ?: fullHashOrNull(file)?.also { repo.setContentHash(file.id, it) }
            ?: return@withContext emptyList()
        val (hashed, unhashed) = candidates.partition { it.contentHash != null }
        val matches = hashed.filter { it.contentHash == own }.toMutableList()
        // Older files without a stored hash: cheap head/tail check first, full hash only if needed
        // (and remembered, so this file is instant next time).
        if (unhashed.isNotEmpty()) {
            val p = runCatching { partialHash(file) }.getOrNull()
            if (p != null) {
                unhashed.filter { runCatching { partialHash(it) }.getOrNull() == p }.forEach { c ->
                    val h = fullHashOrNull(c) ?: return@forEach
                    repo.setContentHash(c.id, h)
                    if (h == own) matches += c
                }
            }
        }
        matches
    }

    /** SHA-256 over size + the first and last [WINDOW] bytes, read via the seekable channel. */
    private fun partialHash(f: FileEntity): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(longBytes(f.sizeBytes))
        val size = f.sizeBytes
        repo.seekableChannel(f).use { ch ->
            hashRange(ch, 0, minOf(WINDOW, size), md)
            if (size > WINDOW) {
                val tailStart = maxOf(WINDOW, size - WINDOW)
                hashRange(ch, tailStart, size - tailStart, md)
            }
        }
        return md.hex()
    }

    /**
     * Full streaming SHA-256 of the decrypted plaintext (constant memory, no temp file), or null when
     * it cannot be read. A cancel stops it between blocks: a big video is not read to its end first.
     */
    private suspend fun fullHashOrNull(f: FileEntity): String? = try {
        fullHash(f)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun fullHash(f: FileEntity): String {
        val md = MessageDigest.getInstance("SHA-256")
        repo.decryptingStream(f).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.hex()
    }

    private fun hashRange(ch: java.nio.channels.SeekableByteChannel, from: Long, count: Long, md: MessageDigest) {
        ch.position(from)
        val buf = ByteBuffer.allocate(64 * 1024)
        var remaining = count
        while (remaining > 0) {
            buf.clear()
            if (remaining < buf.capacity()) buf.limit(remaining.toInt())
            val n = ch.read(buf)
            if (n <= 0) break
            md.update(buf.array(), 0, n)
            remaining -= n
        }
    }

    // --- Similar images --------------------------------------------------------------------

    /**
     * Cluster images that look alike using a 64-bit perceptual difference hash (dHash): each image
     * is decoded (downscaled, in memory) to a 9x8 greyscale grid and encoded as the sign of every
     * horizontal neighbour difference. Two hashes within [threshold] bits (Hamming distance) are
     * treated as the same picture, so re-encodes, quality changes and resizes still match.
     *
     * Fingerprints are kept in the encrypted database ([DupPrintEntity]), so a later scan decodes
     * only the media added since; those are read [WORKERS] at once. Clusters of 2+ are returned.
     * The pairwise Hamming compare is O(images^2) but only over 64-bit longs, which is cheap next
     * to decryption. [onProgress] reports media done (those fingerprinted before count at once).
     */
    suspend fun scanSimilar(
        imageThreshold: Int = DEFAULT_SIMILARITY_THRESHOLD,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): SimilarResult = withContext(Dispatchers.IO) {
        val media = repo.allFilesSnapshot().filter {
            VaultRepository.isImage(it.mimeType) || VaultRepository.isVideo(it.mimeType)
        }
        val total = media.size
        onProgress(0, total)
        val started = SystemClock.elapsedRealtime()

        // Fingerprint each item: an image is one dHash; a video is a dHash per sampled frame.
        val dao = session.requireDb().dupPrintDao()
        orNull { dao.purgeOrphans() }
        val stored = orNull { dao.all() }.orEmpty().associateBy { it.fileId }
        val hashes = arrayOfNulls<LongArray>(total)
        val todo = ArrayList<Int>()
        media.forEachIndexed { i, f ->
            val c = SimilarityPrints.cached(stored[f.id], f.id, f.sizeBytes, versionOf(f))
            if (c != null) hashes[i] = c.hashes else todo += i
        }
        val done = AtomicInteger(total - todo.size)
        onProgress(done.get(), total)
        val imagesMs = AtomicLong()
        val videosMs = AtomicLong()
        val cursor = AtomicInteger(0)
        coroutineScope {
            // Progress goes out from here, not from the workers: the service posts a notification
            // for it, and the workers would wait on each other to report in order.
            val reporter = launch {
                var last = -1
                while (true) {
                    val d = done.get()
                    if (d != last) { last = d; onProgress(d, total) }
                    delay(PROGRESS_MS)
                }
            }
            coroutineScope {
                repeat(minOf(WORKERS, todo.size)) {
                    launch {
                        while (true) {
                            coroutineContext.ensureActive()
                            val i = todo.getOrNull(cursor.getAndIncrement()) ?: break
                            val f = media[i]
                            val video = VaultRepository.isVideo(f.mimeType)
                            val t0 = SystemClock.elapsedRealtime()
                            // Unreadable items are skipped, but a cancellation must still stop the scan.
                            val print = try {
                                if (video) videoFingerprint(f) else Print(imageDHash(f)?.let { longArrayOf(it) }, definite = true)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                Print(null, definite = false)
                            }
                            (if (video) videosMs else imagesMs).addAndGet(SystemClock.elapsedRealtime() - t0)
                            hashes[i] = print.hashes
                            if (print.definite) orNull {
                                dao.put(DupPrintEntity(f.id, f.sizeBytes, versionOf(f), SimilarityPrints.encode(print.hashes)))
                            }
                            done.incrementAndGet()
                        }
                    }
                }
            }
            reporter.cancel()
        }
        onProgress(done.get(), total)
        // A file destroyed while it was being fingerprinted got its row after secureDelete dropped it.
        orNull { dao.purgeOrphans() }
        val printedMs = SystemClock.elapsedRealtime() - started

        // A (nearly) flat picture has almost no gradient: its hash is ~all zeros, the same as every
        // other flat picture (black frames, blank pages, plain screenshots). Such items say nothing
        // about similarity and are left out, rather than grouped as "simili".
        val usable = media.indices.mapNotNull { i ->
            hashes[i]?.takeIf { SimilarityPrints.informative(it, MIN_BITS) }?.let { i to it }
        }
        val kept = usable.map { it.first }
        val prints = usable.map { it.second }
        val isVideo = BooleanArray(kept.size) { VaultRepository.isVideo(media[kept[it]].mimeType) }
        // Pairs the user said are not alike ("Non sono simili") never share a group.
        orNull { dao.purgeApartOrphans() }
        val apartPairs = orNull { dao.apartPairs() }.orEmpty().mapTo(HashSet()) { it.a + "|" + it.b }
        val ids = kept.map { media[it].id }
        val apart = { x: Int, y: Int -> apartPairs.isNotEmpty() && SimilarityPrints.pairKey(ids[x], ids[y]) in apartPairs }
        val clusters = SimilarityPrints.group(prints, isVideo, imageThreshold, VIDEO_FRAMES * VIDEO_PER_FRAME_THRESHOLD, apart) {
            coroutineContext.ensureActive()
        }
        val groups = clusters.map { idxs ->
            SimilarGroup(idxs.map { media[kept[it]] }.sortedBy { it.importedAt }, SimilarityPrints.maxDistance(prints, isVideo, idxs))
        }.sortedByDescending { g -> g.files.sumOf { it.sizeBytes } }
        android.util.Log.i(TAG, "similar scan: $total media, ${todo.size} fingerprinted in $printedMs ms " +
            "(work: images ${imagesMs.get()} ms, videos ${videosMs.get()} ms), " +
            "grouped in ${SystemClock.elapsedRealtime() - started - printedMs} ms")
        SimilarResult(groups, total)
    }

    /**
     * A media's fingerprint ([hashes] null: unreadable) and whether it is [definite], so stored:
     * a failure that may pass (memory, a frame not reached in time) is tried again next scan.
     */
    private class Print(val hashes: LongArray?, val definite: Boolean)

    /** [block]'s result, or null when it fails; a cancellation still stops the scan. */
    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * Media already in the vault that look like [file] (just imported), most alike first, with the
     * thresholds of [scanSimilar]. [file] is fingerprinted now and kept, so the next scan has it;
     * the vault side is the stored fingerprints only, nothing else is decrypted. Media never
     * fingerprinted yet are not found. A video the app's decoder cannot read is left to the scan
     * (the retriever takes far too long for an import).
     */
    suspend fun similarTo(file: FileEntity): List<FileEntity> = withContext(Dispatchers.IO) {
        val video = VaultRepository.isVideo(file.mimeType)
        if (!video && !VaultRepository.isImage(file.mimeType)) return@withContext emptyList()
        val dao = session.requireDb().dupPrintDao()
        val rows = orNull { dao.all() }.orEmpty()
        val version = versionOf(file)
        val own = SimilarityPrints.cached(rows.firstOrNull { it.fileId == file.id }, file.id, file.sizeBytes, version)?.hashes
            ?: run {
                val print = try {
                    if (video) videoFingerprint(file, retriever = false) else Print(imageDHash(file)?.let { longArrayOf(it) }, definite = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Print(null, definite = false)
                }
                if (print.definite) orNull { dao.put(DupPrintEntity(file.id, file.sizeBytes, version, SimilarityPrints.encode(print.hashes))) }
                print.hashes
            }
        if (own == null || !SimilarityPrints.informative(own, MIN_BITS)) return@withContext emptyList()
        val others = rows.filter { it.fileId != file.id && it.version == version }.mapNotNull { r ->
            SimilarityPrints.decode(r.hashes)?.takeIf { it.size == own.size && SimilarityPrints.informative(it, MIN_BITS) }?.let { r to it }
        }
        val limit = if (video) VIDEO_FRAMES * VIDEO_PER_FRAME_THRESHOLD else DEFAULT_SIMILARITY_THRESHOLD
        val found = ArrayList<FileEntity>()
        for (i in SimilarityPrints.closest(own, others.map { it.second }, limit)) {
            if (found.size >= MAX_SIMILAR) break
            val r = others[i].first
            // Still that content, live, the same kind; byte-identical copies are the exact check's.
            repo.fileById(r.fileId)?.takeIf {
                it.deletedAt == null && it.sizeBytes == r.sizeBytes && VaultRepository.isVideo(it.mimeType) == video &&
                    (it.contentHash == null || it.contentHash != file.contentHash)
            }?.let { found += it }
        }
        found
    }

    /**
     * "Non sono simili": no two of [fileIds] are grouped together again. Returns the pairs it
     * added (not those already apart), which [bringTogether] takes back.
     */
    suspend fun keepApart(fileIds: List<String>): List<DupApartEntity> = withContext(Dispatchers.IO) {
        val ids = fileIds.distinct()
        val pairs = ids.indices.flatMap { i -> (i + 1 until ids.size).map { j -> DupApartEntity(minOf(ids[i], ids[j]), maxOf(ids[i], ids[j])) } }
        if (pairs.isEmpty()) return@withContext emptyList()
        val rows = session.requireDb().dupPrintDao().putApart(pairs)
        pairs.filterIndexed { i, _ -> rows[i] != -1L }
    }

    /** Undo of [keepApart]: [pairs] may be grouped together again. */
    suspend fun bringTogether(pairs: List<DupApartEntity>) = withContext(Dispatchers.IO) {
        if (pairs.isNotEmpty()) session.requireDb().dupPrintDao().deleteApart(pairs)
    }

    /** Live photos and videos and how many have a fingerprint already (null while locked). */
    suspend fun printStats(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val dao = session.dbOrNull()?.dupPrintDao() ?: return@withContext null
        orNull { dao.printedCount(IMAGE_PRINT_VERSION, VIDEO_PRINT_VERSION) to dao.mediaCount() }
    }

    /** The version of the fingerprint [f] gets: a stored one made another way is made again. */
    private fun versionOf(f: FileEntity) = if (VaultRepository.isVideo(f.mimeType)) VIDEO_PRINT_VERSION else IMAGE_PRINT_VERSION

    /** Decode an image (downscaled, in memory) and return its 64-bit dHash, or null if undecodable. */
    private fun imageDHash(f: FileEntity): Long? {
        // Decode straight from the decrypting stream (twice: bounds, then a subsampled bitmap)
        // instead of reading the whole image into RAM first — a big photo no longer costs its full
        // size in heap for a 9x8 fingerprint.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        repo.decryptingStream(f).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Decode small: no need for full resolution just to fingerprint.
        val minSide = minOf(bounds.outWidth, bounds.outHeight)
        val sample = Integer.highestOneBit(maxOf(1, minSide / 16))
        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, sample)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = repo.decryptingStream(f).use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return bitmapDHash(bmp).also { bmp.recycle() }
    }

    /**
     * Video fingerprint: [VIDEO_FRAMES] frames sampled at even interior time points (the key frame
     * nearest each, else the exact frame), read straight from the decrypting channel (no plaintext
     * copy on disk). The app's own decoder ([VideoFrameReader]) reads them in milliseconds; a video
     * it cannot read (or not all of it) goes through MediaMetadataRetriever, seconds a frame in the
     * media server, as before, within [RETRIEVER_LIMIT_MS]. Unreadable (null hashes) if the video
     * can't be read or any frame is missing; not [Print.definite] when the time ran out.
     */
    private suspend fun videoFingerprint(f: FileEntity, retriever: Boolean = true): Print = coroutineScope {
        val channels = CopyOnWriteArrayList<SeekableByteChannel>()
        fun source() = ChannelMediaDataSource(repo.seekableChannel(f).also { channels += it }, f.sizeBytes)
        fun closeAll() = channels.forEach { runCatching { it.close() } }
        // A cancelled scan closes the channels at once: a frame being read fails now, instead of the
        // scan waiting for it.
        val closer = launch { try { awaitCancellation() } finally { closeAll() } }
        try {
            readerFingerprint(f, source())?.let { return@coroutineScope Print(it, definite = true) }
            coroutineContext.ensureActive()
            if (!retriever) return@coroutineScope Print(null, definite = false)
            // The retriever blocks in the media server and may never give up on a broken file: past
            // the limit its channel is closed, so its reads fail and it returns.
            val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
            val timer = launch { delay(RETRIEVER_LIMIT_MS); timedOut.set(true); closeAll() }
            val hashes = try { retrieverFingerprint(f, source()) } finally { timer.cancel() }
            // Closed channels read as a broken file: a cancelled scan or the limit is no answer.
            coroutineContext.ensureActive()
            Print(hashes, definite = !timedOut.get())
        } finally {
            closer.cancel()
            channels.forEach { runCatching { it.close() } }
        }
    }

    /** [videoFingerprint] through the app's own decoder; null when it cannot read every frame. */
    private suspend fun readerFingerprint(f: FileEntity, source: ChannelMediaDataSource): LongArray? {
        val reader = VideoFrameReader.open(source) ?: return null
        try {
            val durMs = f.durationMs?.takeIf { it > 0 } ?: reader.durationMs.takeIf { it > 0 } ?: return null
            val out = LongArray(VIDEO_FRAMES)
            for (i in 0 until VIDEO_FRAMES) {
                val t = frameAt(durMs, i) / 1000
                val img = reader.frame(t, exact = false, side = VIDEO_FRAME_SIDE)
                    ?: reader.frame(t, exact = true, side = VIDEO_FRAME_SIDE)
                    ?: return null
                out[i] = SimilarityPrints.dHash(img)
            }
            return out
        } catch (e: VideoFrameReader.Unreadable) {
            return null
        } finally {
            reader.close()
        }
    }

    /** [videoFingerprint] through MediaMetadataRetriever, for videos the app's decoder cannot read. */
    private suspend fun retrieverFingerprint(f: FileEntity, source: ChannelMediaDataSource): LongArray? {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            // A file the retriever cannot open is unreadable, as one it opens but cannot decode.
            try { retriever.setDataSource(source) } catch (e: RuntimeException) { return null }
            val durMs = f.durationMs
                ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return null
            if (durMs <= 0) return null
            val out = LongArray(VIDEO_FRAMES)
            for (i in 0 until VIDEO_FRAMES) {
                coroutineContext.ensureActive()
                val us = frameAt(durMs, i)
                val frame = retriever.getFrameAtTime(us, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(us, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: return null
                // Scaled as the app's decoder gives its frames, so a video reads the same either way.
                val fitted = fit(frame, VIDEO_FRAME_SIDE)
                out[i] = bitmapDHash(fitted)
                if (fitted !== frame) fitted.recycle()
                frame.recycle()
            }
            return out
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** [bmp] scaled down (filtered) so its longest side is at most [side]; [bmp] itself when it fits. */
    private fun fit(bmp: Bitmap, side: Int): Bitmap {
        val k = side.toDouble() / maxOf(bmp.width, bmp.height)
        if (k >= 1.0) return bmp
        return Bitmap.createScaledBitmap(bmp, maxOf(1, Math.round(bmp.width * k).toInt()), maxOf(1, Math.round(bmp.height * k).toInt()), true)
    }

    /** The moment (us) of the [i]th sampled frame of a [durMs] long video. */
    private fun frameAt(durMs: Long, i: Int): Long = (durMs * ((i + 1.0) / (VIDEO_FRAMES + 1)) * 1000).toLong()

    /** 64-bit difference hash of a bitmap (scaled to 9x8 greyscale, sign of horizontal deltas). */
    private fun bitmapDHash(bmp: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = SimilarityPrints.luminance(small.getPixel(x, y))
                val right = SimilarityPrints.luminance(small.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (small !== bmp) small.recycle()
        return hash
    }

    private fun longBytes(v: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) out[i] = (v shr (i * 8)).toByte()
        return out
    }

    private fun MessageDigest.hex(): String =
        digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    companion object {
        private const val WINDOW = 64 * 1024L
        /** ~18% of 64 bits: tolerant enough for re-encodes, tight enough to avoid false matches. */
        const val DEFAULT_SIMILARITY_THRESHOLD = 12
        /** Frames sampled per video for its fingerprint. */
        private const val VIDEO_FRAMES = 5
        /** Per-frame Hamming tolerance; a video pair matches within VIDEO_FRAMES * this bits total. */
        private const val VIDEO_PER_FRAME_THRESHOLD = 10
        private const val MIN_BITS = SimilarityPrints.MIN_BITS
        /** At most this many look-alikes named for one imported media. */
        private const val MAX_SIMILAR = 5
        /** Media fingerprinted at once (each holds one small decoded picture or one video decoder). */
        private const val WORKERS = 3
        /** Longest side of a video frame read for its dHash (plenty for a 9x8 grid). */
        private const val VIDEO_FRAME_SIDE = 512
        /** Bumped when a fingerprint is made another way: stored ones are then made again. */
        private const val IMAGE_PRINT_VERSION = 1
        private const val VIDEO_PRINT_VERSION = 2
        /** Time given to MediaMetadataRetriever for one video's frames. */
        private const val RETRIEVER_LIMIT_MS = 90_000L
        /** How often the progress of the similar scan is reported. */
        private const val PROGRESS_MS = 250L
        private const val TAG = "DuplicateScanner"
    }
}

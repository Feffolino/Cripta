package com.cripta.app.data.dedup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest
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
     *
     * Groups of 2+ are returned, each sorted oldest-import-first (the natural "keep" candidate),
     * ordered by reclaimable space descending. [onProgress] reports (processed, total) files.
     */
    suspend fun scanExact(onProgress: (Int, Int) -> Unit = { _, _ -> }): ExactResult =
        withContext(Dispatchers.IO) {
            val all = repo.allFilesSnapshot()
            val bySize = all.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
            val total = bySize.values.sumOf { it.size }
            var done = 0
            onProgress(0, total)
            val confirmed = mutableListOf<ExactGroup>()
            for ((size, sameSize) in bySize) {
                coroutineContext.ensureActive()
                // Phase 1: partial (head+tail) hash to cheaply split the same-size bucket.
                val byPartial = HashMap<String, MutableList<FileEntity>>()
                for (f in sameSize) {
                    coroutineContext.ensureActive()
                    val p = runCatching { partialHash(f) }.getOrNull()
                    done++; onProgress(done, total)
                    if (p != null) byPartial.getOrPut(p) { mutableListOf() }.add(f)
                }
                // Phase 2: full hash only where the partial hash already collided.
                for (candidates in byPartial.values.filter { it.size > 1 }) {
                    val byFull = HashMap<String, MutableList<FileEntity>>()
                    for (f in candidates) {
                        coroutineContext.ensureActive()
                        val h = runCatching { fullHash(f) }.getOrNull()
                        if (h != null) byFull.getOrPut(h) { mutableListOf() }.add(f)
                    }
                    byFull.values.filter { it.size > 1 }.forEach { dupes ->
                        confirmed += ExactGroup(size, dupes.sortedBy { it.importedAt })
                    }
                }
            }
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
        val own = file.contentHash ?: runCatching { fullHash(file) }.getOrNull()?.also { repo.setContentHash(file.id, it) }
            ?: return@withContext emptyList()
        val (hashed, unhashed) = candidates.partition { it.contentHash != null }
        val matches = hashed.filter { it.contentHash == own }.toMutableList()
        // Older files without a stored hash: cheap head/tail check first, full hash only if needed
        // (and remembered, so this file is instant next time).
        if (unhashed.isNotEmpty()) {
            val p = runCatching { partialHash(file) }.getOrNull()
            if (p != null) {
                unhashed.filter { runCatching { partialHash(it) }.getOrNull() == p }.forEach { c ->
                    val h = runCatching { fullHash(c) }.getOrNull() ?: return@forEach
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

    /** Full streaming SHA-256 of the decrypted plaintext (constant memory, no temp file). */
    private fun fullHash(f: FileEntity): String {
        val md = MessageDigest.getInstance("SHA-256")
        repo.decryptingStream(f).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
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
     * Clusters of 2+ are returned. Hashing is O(files); the pairwise Hamming compare is O(images^2)
     * but only over 64-bit longs, which is cheap next to decryption. [onProgress] reports images.
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

        // Fingerprint each item: an image is one dHash; a video is a dHash per sampled frame.
        val fps = ArrayList<Fingerprint>(total)
        media.forEachIndexed { i, f ->
            coroutineContext.ensureActive()
            val video = VaultRepository.isVideo(f.mimeType)
            // Unreadable items are skipped, but a cancellation must still stop the scan.
            val hashes = try {
                if (video) videoFingerprint(f) else imageDHash(f)?.let { longArrayOf(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            onProgress(i + 1, total)
            if (hashes != null && hashes.isNotEmpty()) fps += Fingerprint(f, hashes, video)
        }

        // Union-find clustering. Only same-modality fingerprints of equal length are comparable,
        // so images never merge with videos.
        val n = fps.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != cur) { val next = parent[cur]; parent[cur] = root; cur = next }
            return root
        }
        for (a in 0 until n) {
            coroutineContext.ensureActive()
            for (b in a + 1 until n) {
                val d = distance(fps[a], fps[b]) ?: continue
                val limit = if (fps[a].isVideo) VIDEO_FRAMES * VIDEO_PER_FRAME_THRESHOLD else imageThreshold
                if (d <= limit) parent[find(a)] = find(b)
            }
        }
        val clusters = HashMap<Int, MutableList<Int>>()
        for (i in 0 until n) clusters.getOrPut(find(i)) { mutableListOf() }.add(i)

        val groups = clusters.values.filter { it.size > 1 }.map { idxs ->
            var maxD = 0
            for (a in idxs.indices) for (b in a + 1 until idxs.size) {
                (distance(fps[idxs[a]], fps[idxs[b]]) ?: 0).let { if (it > maxD) maxD = it }
            }
            SimilarGroup(idxs.map { fps[it].file }.sortedBy { it.importedAt }, maxD)
        }.sortedByDescending { g -> g.files.sumOf { it.sizeBytes } }
        SimilarResult(groups, total)
    }

    private data class Fingerprint(val file: FileEntity, val hashes: LongArray, val isVideo: Boolean)

    /** Total Hamming distance between two fingerprints, or null if they aren't comparable. */
    private fun distance(a: Fingerprint, b: Fingerprint): Int? {
        if (a.isVideo != b.isVideo || a.hashes.size != b.hashes.size) return null
        var d = 0
        for (i in a.hashes.indices) d += java.lang.Long.bitCount(a.hashes[i] xor b.hashes[i])
        return d
    }

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
     * Video fingerprint: decrypt to a temp file (shredded afterwards, like the transcoder), then
     * dHash [VIDEO_FRAMES] frames sampled at even interior time points. Returns null if the video
     * can't be read or any frame is missing.
     */
    private suspend fun videoFingerprint(f: FileEntity): LongArray? {
        val tmp = repo.decryptToTempFile(f, "vid")
        try {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(tmp.absolutePath)
                val durMs = f.durationMs
                    ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: return null
                if (durMs <= 0) return null
                val out = LongArray(VIDEO_FRAMES)
                for (i in 0 until VIDEO_FRAMES) {
                    coroutineContext.ensureActive()
                    val frac = (i + 1.0) / (VIDEO_FRAMES + 1)
                    val us = (durMs * frac * 1000).toLong()
                    val frame = retriever.getFrameAtTime(us, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(us, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: return null
                    out[i] = bitmapDHash(frame)
                    frame.recycle()
                }
                return out
            } finally {
                runCatching { retriever.release() }
            }
        } finally {
            repo.shredTempFile(tmp)
        }
    }

    /** 64-bit difference hash of a bitmap (scaled to 9x8 greyscale, sign of horizontal deltas). */
    private fun bitmapDHash(bmp: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(small.getPixel(x, y))
                val right = luminance(small.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (small !== bmp) small.recycle()
        return hash
    }

    private fun luminance(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
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
    }
}

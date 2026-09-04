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
    suspend fun scanExact(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ExactGroup> =
        withContext(Dispatchers.IO) {
            val bySize = repo.allFilesSnapshot().groupBy { it.sizeBytes }.filterValues { it.size > 1 }
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
            confirmed.sortedByDescending { it.sizeBytes * (it.files.size - 1) }
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
        threshold: Int = DEFAULT_SIMILARITY_THRESHOLD,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<SimilarGroup> = withContext(Dispatchers.IO) {
        val images = repo.allFilesSnapshot().filter { it.mimeType.startsWith("image/") }
        val total = images.size
        onProgress(0, total)
        val entries = ArrayList<Pair<FileEntity, Long>>(total)
        images.forEachIndexed { i, f ->
            coroutineContext.ensureActive()
            val h = runCatching { imageDHash(f) }.getOrNull()
            onProgress(i + 1, total)
            if (h != null) entries += f to h
        }

        // Union-find clustering by Hamming distance.
        val n = entries.size
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
                val d = java.lang.Long.bitCount(entries[a].second xor entries[b].second)
                if (d <= threshold) parent[find(a)] = find(b)
            }
        }
        val clusters = HashMap<Int, MutableList<Int>>()
        for (i in 0 until n) clusters.getOrPut(find(i)) { mutableListOf() }.add(i)

        clusters.values.filter { it.size > 1 }.map { idxs ->
            var maxD = 0
            for (a in idxs.indices) for (b in a + 1 until idxs.size) {
                val d = java.lang.Long.bitCount(entries[idxs[a]].second xor entries[idxs[b]].second)
                if (d > maxD) maxD = d
            }
            SimilarGroup(idxs.map { entries[it].first }.sortedBy { it.importedAt }, maxD)
        }.sortedByDescending { g -> g.files.sumOf { it.sizeBytes } }
    }

    /** Decode an image (downscaled, in memory) and return its 64-bit dHash, or null if undecodable. */
    private fun imageDHash(f: FileEntity): Long? {
        val bytes = repo.decryptingStream(f).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Decode small: no need for full resolution just to fingerprint.
        val minSide = minOf(bounds.outWidth, bounds.outHeight)
        val sample = Integer.highestOneBit(maxOf(1, minSide / 16))
        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, sample)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        if (small != bmp) bmp.recycle()
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
        small.recycle()
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
    }
}

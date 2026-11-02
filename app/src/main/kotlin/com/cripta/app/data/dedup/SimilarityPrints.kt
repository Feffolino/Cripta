package com.cripta.app.data.dedup

import com.cripta.app.data.db.DupPrintEntity
import com.cripta.app.media.face.FaceImage

/**
 * The pure parts of the "simili" scan, free of Android types so they are unit tested: the stored
 * fingerprint format, when a stored one still holds, the dHash of a decoded frame and the
 * complete-linkage grouping.
 */
internal object SimilarityPrints {
    /** A stored fingerprint that still holds: [hashes] null means the media could not be read. */
    class Cached(val hashes: LongArray?)

    /** [hashes] as little-endian longs (empty for null: unreadable). */
    fun encode(hashes: LongArray?): ByteArray {
        if (hashes == null) return ByteArray(0)
        val out = ByteArray(hashes.size * 8)
        for (i in hashes.indices) for (b in 0 until 8) out[i * 8 + b] = (hashes[i] shr (b * 8)).toByte()
        return out
    }

    /** The inverse of [encode]; null for an empty (unreadable) or malformed blob. */
    fun decode(bytes: ByteArray): LongArray? {
        if (bytes.isEmpty() || bytes.size % 8 != 0) return null
        return LongArray(bytes.size / 8) { i ->
            var v = 0L
            for (b in 0 until 8) v = v or ((bytes[i * 8 + b].toLong() and 0xFF) shl (b * 8))
            v
        }
    }

    /**
     * [row] when it is still [fileId]'s fingerprint: same size and made the current way ([version]).
     * Null: fingerprint the media again.
     */
    fun cached(row: DupPrintEntity?, fileId: String, sizeBytes: Long, version: Int): Cached? {
        if (row == null || row.fileId != fileId || row.sizeBytes != sizeBytes || row.version != version) return null
        if (row.hashes.isEmpty()) return Cached(null)
        return decode(row.hashes)?.let { Cached(it) }
    }

    /**
     * 64-bit difference hash of [img]: scaled (bilinear) to 9x8, then the sign of every horizontal
     * neighbour's luminance difference, as the scan's Bitmap dHash.
     */
    fun dHash(img: FaceImage): Long {
        val small = if (img.width == 9 && img.height == 8) img else img.resized(9, 8)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luminance(small.argb[y * 9 + x]) > luminance(small.argb[y * 9 + x + 1])) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun luminance(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /**
     * False for a (nearly) flat picture: its hash is ~all zeros, the same as every other flat
     * picture (black frames, blank pages, plain screenshots), so it says nothing about similarity.
     * A video needs most of its frames informative.
     */
    fun informative(hashes: LongArray, minBits: Int = MIN_BITS): Boolean {
        val n = hashes.count { java.lang.Long.bitCount(it) in minBits..(64 - minBits) }
        return hashes.isNotEmpty() && n * 2 > hashes.size
    }

    /**
     * The indices of [candidates] within [limit] of [print] (same length only, so same modality),
     * nearest first; ties keep their order.
     */
    fun closest(print: LongArray, candidates: List<LongArray>, limit: Int): List<Int> =
        candidates.indices
            .map { it to distance(print, false, candidates[it], false) }
            .filter { (_, d) -> d in 0..limit }
            .sortedBy { it.second }
            .map { it.first }

    /** A hash with fewer set (or unset) bits than this comes from a nearly flat picture. */
    const val MIN_BITS = 4

    /** Total Hamming distance between [a] and [b], or -1 when they are not comparable. */
    fun distance(a: LongArray, aVideo: Boolean, b: LongArray, bVideo: Boolean): Int {
        if (aVideo != bVideo || a.size != b.size) return -1
        var d = 0
        for (i in a.indices) d += java.lang.Long.bitCount(a[i] xor b[i])
        return d
    }

    /**
     * Complete-linkage grouping, greedy in input order: each item not grouped yet seeds a group, and
     * every later free item joins when it is within the limit (of the seed's modality) of EVERY
     * member so far. Chaining (A~B and B~C put A with C, however far apart) grouped pictures that
     * looked nothing alike. Only same-modality fingerprints of equal length are comparable, so
     * images never mix with videos, and never two items the user kept [apart]. Returns the groups
     * of 2+, members in input order. [check] runs once per seed (cancellation).
     */
    fun group(
        prints: List<LongArray>, video: BooleanArray, imageLimit: Int, videoLimit: Int,
        apart: (Int, Int) -> Boolean = { _, _ -> false }, check: () -> Unit = {},
    ): List<IntArray> {
        val n = prints.size
        val assigned = BooleanArray(n)
        val members = IntArray(n)
        val out = ArrayList<IntArray>()
        for (a in 0 until n) {
            check()
            if (assigned[a]) continue
            val limit = if (video[a]) videoLimit else imageLimit
            val pa = prints[a]
            var size = 1
            members[0] = a
            for (b in a + 1 until n) {
                // Cheap rejections first: grouped already, the other modality, too far from the seed.
                if (assigned[b] || video[b] != video[a]) continue
                val pb = prints[b]
                val d = distance(pa, video[a], pb, video[b])
                if (d < 0 || d > limit || apart(a, b)) continue
                var ok = true
                for (m in 1 until size) {
                    val dm = distance(prints[members[m]], video[members[m]], pb, video[b])
                    if (dm < 0 || dm > limit || apart(members[m], b)) { ok = false; break }
                }
                if (ok) members[size++] = b
            }
            if (size > 1) {
                for (m in 0 until size) assigned[members[m]] = true
                out += members.copyOf(size)
            }
        }
        return out
    }

    /** The stored key of a "not alike" pair, whatever the order of [x] and [y]. */
    fun pairKey(x: String, y: String) = if (x < y) "$x|$y" else "$y|$x"

    /** The largest pairwise distance inside [group]. */
    fun maxDistance(prints: List<LongArray>, video: BooleanArray, group: IntArray): Int {
        var maxD = 0
        for (a in group.indices) for (b in a + 1 until group.size) {
            val d = distance(prints[group[a]], video[group[a]], prints[group[b]], video[group[b]])
            if (d > maxD) maxD = d
        }
        return maxD
    }
}

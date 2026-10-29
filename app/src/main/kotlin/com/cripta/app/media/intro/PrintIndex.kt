package com.cripta.app.media.intro

/**
 * Inverted index over the fingerprints of many videos: which ones share audio with a given stretch,
 * and at which alignment, without comparing every pair (thousands of videos made that hours).
 * With very many hashes only "landmark" ones are indexed, one in [sample] chosen by value, so the
 * same audio gives the same landmarks in every video; quick prints are all indexed (short intros
 * need every hit). Entries are packed in one sorted LongArray:
 * key (16 bits) · video number (24 bits) · position (16 bits).
 */
class PrintIndex(expectedEntries: Int = 1 shl 16, private val sample: Int = 1) {
    init { require(sample > 0 && sample and (sample - 1) == 0) { "sample must be a power of two" } }
    private var entries = LongArray(expectedEntries.coerceAtLeast(16))
    private var size = 0
    private var sorted = true
    private val ids = ArrayList<String>()

    /** Add a video's hashes. */
    fun add(fileId: String, hashes: IntArray) {
        val v = ids.size
        ids += fileId
        for (pos in 0 until minOf(hashes.size, 0x10000)) {
            val h = hashes[pos]
            if (!isLandmark(h)) continue
            if (size == entries.size) entries = entries.copyOf(size * 2)
            entries[size++] = ((h ushr KEY_SHIFT).toLong() shl 40) or (v.toLong() shl 16) or pos.toLong()
        }
        sorted = false
    }

    /**
     * Videos (by file id) whose audio lines up with [hashes], each with its alignments (its index −
     * query index), best first, each backed by at least [minVotes] landmarks.
     */
    fun candidates(hashes: IntArray, minVotes: Int = MIN_VOTES): Map<String, List<Int>> {
        if (!sorted) { java.util.Arrays.sort(entries, 0, size); sorted = true }
        val votes = HashMap<Long, Int>()
        for (pos in hashes.indices) {
            val h = hashes[pos]
            if (!isLandmark(h)) continue
            val key = (h ushr KEY_SHIFT).toLong()
            var i = lowerBound(key shl 40)
            while (i < size && entries[i] ushr 40 == key) {
                val e = entries[i++]
                val v = ((e ushr 16) and 0xFFFFFF).toInt()
                val p = (e and 0xFFFF).toInt()
                votes.merge((v.toLong() shl 32) or ((p - pos).toLong() and 0xFFFFFFFFL), 1, Int::plus)
            }
        }
        return votes.entries.filter { it.value >= minVotes }
            .groupBy({ ids[(it.key ushr 32).toInt()] }, { it.key.toInt() to it.value })
            .mapValues { (_, l) -> l.sortedByDescending { it.second }.take(MAX_OFFSETS).map { it.first } }
    }

    private fun lowerBound(x: Long): Int {
        var lo = 0; var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid] < x) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun isLandmark(h: Int) = h != 0 && (h ushr KEY_SHIFT) and (sample - 1) == 0

    companion object {
        /** Same key as [SegmentMatcher]. */
        private const val KEY_SHIFT = SegmentMatcher.KEY_SHIFT
        /** Above this many hashes in all, only landmarks are indexed (memory: 8 bytes each). */
        const val MAX_ENTRIES = 4_000_000L
        private const val MIN_VOTES = 2
        private const val MAX_OFFSETS = 3

        /** The landmark rate for [totalHashes]: 1 (all) up to the memory cap, then powers of two. */
        fun sampleFor(totalHashes: Long): Int {
            var s = 1
            while (totalHashes / s > MAX_ENTRIES) s *= 2
            return s
        }
    }
}

package com.cripta.app.media.intro

/** A stretch of audio two hash sequences share, as hash indices (ends exclusive). */
data class Match(val aStart: Int, val aEnd: Int, val bStart: Int, val bEnd: Int) {
    val length: Int get() = aEnd - aStart
}

/**
 * Finds the longest stretch two fingerprints share. Hashes whose top bits agree vote for an
 * alignment (b index − a index); the best few alignments are then walked hash by hash.
 */
object SegmentMatcher {
    /** Votes use the top 20 bits: the low bits flip most under re-encoding. */
    private const val KEY_SHIFT = 12
    /** Mean differing bits (of 32) over [WINDOW] hashes still taken as the same audio. */
    private const val MAX_BITS = 10
    private const val WINDOW = 8
    private const val MAX_OFFSETS = 5
    private const val MIN_VOTES = 3

    fun best(a: IntArray, b: IntArray, minLength: Int): Match? {
        if (a.isEmpty() || b.isEmpty()) return null
        val index = HashMap<Int, MutableList<Int>>()
        for (j in b.indices) if (b[j] != 0) index.getOrPut(b[j] ushr KEY_SHIFT) { mutableListOf() }.add(j)
        val votes = HashMap<Int, Int>()
        for (i in a.indices) {
            if (a[i] == 0) continue
            index[a[i] ushr KEY_SHIFT]?.forEach { j -> votes.merge(j - i, 1, Int::plus) }
        }
        return votes.entries.filter { it.value >= MIN_VOTES }
            .sortedByDescending { it.value }.take(MAX_OFFSETS)
            .mapNotNull { runAt(a, b, it.key, minLength) }
            .maxByOrNull { it.length }
    }

    /** Longest shared stretch at an alignment already known (from a [PrintIndex]). */
    fun at(a: IntArray, b: IntArray, offset: Int, minLength: Int): Match? = runAt(a, b, offset, minLength)

    /** Longest run of [a] against [b] shifted by [offset] (b index = a index + offset). */
    private fun runAt(a: IntArray, b: IntArray, offset: Int, minLength: Int): Match? {
        val from = maxOf(0, -offset)
        val to = minOf(a.size, b.size - offset)
        if (to - from < maxOf(minLength, WINDOW)) return null
        val err = IntArray(to - from) { k ->
            val x = a[from + k]; val y = b[from + k + offset]
            if (x == 0 || y == 0) 32 else Integer.bitCount(x xor y)
        }
        // A window is good when its hashes average at most MAX_BITS; a run of good windows covers
        // from its first window's start to its last window's end.
        var bestStart = 0; var bestLen = 0; var runStart = -1
        var sum = 0
        for (k in 0 until WINDOW) sum += err[k]
        val n = err.size - WINDOW + 1
        for (k in 0 until n) {
            if (k > 0) sum += err[k + WINDOW - 1] - err[k - 1]
            val good = sum <= MAX_BITS * WINDOW
            if (good && runStart < 0) runStart = k
            if ((!good || k == n - 1) && runStart >= 0) {
                val end = (if (good) k else k - 1) + WINDOW
                if (end - runStart > bestLen) { bestLen = end - runStart; bestStart = runStart }
                runStart = -1
            }
        }
        // Windows reach a few hashes past the shared audio on each side: trim those.
        while (bestLen > 0 && err[bestStart] > MAX_BITS) { bestStart++; bestLen-- }
        while (bestLen > 0 && err[bestStart + bestLen - 1] > MAX_BITS) bestLen--
        if (bestLen < minLength || bestLen == 0) return null
        val s = from + bestStart
        return Match(s, s + bestLen, s + offset, s + offset + bestLen)
    }
}

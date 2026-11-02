package com.cripta.app.data.dedup

import com.cripta.app.data.db.DupPrintEntity
import com.cripta.app.media.face.FaceImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SimilarityPrintsTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val h = longArrayOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x0123456789ABCDEFL)
        assertArrayEquals(h, SimilarityPrints.decode(SimilarityPrints.encode(h)))
        assertEquals(0, SimilarityPrints.encode(null).size)
        assertNull(SimilarityPrints.decode(ByteArray(0)))
        assertNull(SimilarityPrints.decode(ByteArray(7)))
    }

    @Test
    fun storedPrintHoldsOnlyForSameFileSizeAndVersion() {
        val h = longArrayOf(42L, 7L)
        val row = DupPrintEntity("a", 100, 1, SimilarityPrints.encode(h))
        assertArrayEquals(h, SimilarityPrints.cached(row, "a", 100, 1)!!.hashes)
        assertNull(SimilarityPrints.cached(null, "a", 100, 1))
        assertNull(SimilarityPrints.cached(row, "b", 100, 1))
        assertNull("other content under the same id", SimilarityPrints.cached(row, "a", 101, 1))
        assertNull("made another way", SimilarityPrints.cached(row, "a", 100, 2))
        // An unreadable media is remembered as such, a damaged row is made again.
        val unreadable = SimilarityPrints.cached(DupPrintEntity("a", 100, 1, ByteArray(0)), "a", 100, 1)
        assertNotNull(unreadable)
        assertNull(unreadable!!.hashes)
        assertNull(SimilarityPrints.cached(DupPrintEntity("a", 100, 1, ByteArray(5)), "a", 100, 1))
    }

    @Test
    fun dHashOfHorizontalGradients() {
        // Brighter on the left: every left > right comparison holds, all 64 bits set.
        val falling = FaceImage(9, 8, IntArray(72) { grey(255 - (it % 9) * 20) })
        assertEquals(-1L, SimilarityPrints.dHash(falling))
        val rising = FaceImage(9, 8, IntArray(72) { grey((it % 9) * 20) })
        assertEquals(0L, SimilarityPrints.dHash(rising))
        // A larger picture is scaled to 9x8 first; the gradient survives.
        val big = FaceImage(90, 80, IntArray(90 * 80) { grey(255 - (it % 90) * 2) })
        assertEquals(-1L, SimilarityPrints.dHash(big))
    }

    @Test
    fun groupingMatchesTheOriginalCompleteLinkage() {
        val rnd = Random(7)
        repeat(30) { round ->
            val n = 40 + rnd.nextInt(80)
            // Few base pictures, each copied with a few flipped bits, so groups and near misses form.
            val bases = LongArray(6) { rnd.nextLong() }
            val video = BooleanArray(n) { rnd.nextInt(5) == 0 }
            val prints = List(n) { i ->
                val frames = if (video[i]) 5 else 1
                LongArray(frames) { f ->
                    var h = bases[(i + f) % bases.size].let { if (video[i]) it xor (f.toLong() shl 3) else it }
                    repeat(rnd.nextInt(10)) { h = h xor (1L shl rnd.nextInt(64)) }
                    h
                }
            }
            val expected = referenceGroups(prints, video, 12, 50)
            val actual = SimilarityPrints.group(prints, video, 12, 50).map { it.toList() }
            assertEquals("round $round", expected, actual)
        }
    }

    @Test
    fun noChainingAndNoMixedModalities() {
        // a~b and b~c, but a and c too far apart: only a and b group.
        val a = 0L
        val b = (1L shl 8) - 1            // 8 bits from a
        val c = (1L shl 16) - 1           // 8 bits from b, 16 from a
        val groups = SimilarityPrints.group(listOf(longArrayOf(a), longArrayOf(b), longArrayOf(c)), BooleanArray(3), 12, 50)
        assertEquals(listOf(listOf(0, 1)), groups.map { it.toList() })
        // An image and a one-frame "video" with the same hash never group.
        val mixed = SimilarityPrints.group(listOf(longArrayOf(a), longArrayOf(a)), booleanArrayOf(false, true), 12, 50)
        assertEquals(0, mixed.size)
    }

    @Test
    fun maxDistanceIsTheWidestPair() {
        val prints = listOf(longArrayOf(0L), longArrayOf(1L), longArrayOf(7L))
        assertEquals(3, SimilarityPrints.maxDistance(prints, BooleanArray(3), intArrayOf(0, 1, 2)))
    }

    @Test
    fun flatPicturesAreNotInformative() {
        assertEquals(false, SimilarityPrints.informative(longArrayOf(0L)))
        assertEquals(false, SimilarityPrints.informative(longArrayOf(-1L)))
        assertEquals(true, SimilarityPrints.informative(longArrayOf(0x0F0F0F0F0F0F0F0FL)))
        // A video: most frames must say something.
        val busy = 0x0123456789ABCDEFL
        assertEquals(true, SimilarityPrints.informative(longArrayOf(busy, busy, busy, 0L, 0L)))
        assertEquals(false, SimilarityPrints.informative(longArrayOf(busy, busy, 0L, 0L, 0L)))
        assertEquals(false, SimilarityPrints.informative(LongArray(0)))
    }

    @Test
    fun closestKeepsOnlyNearOnesNearestFirst() {
        val print = longArrayOf(0L)
        val candidates = listOf(
            longArrayOf((1L shl 10) - 1),   // 10 bits away
            longArrayOf(0xFFFFFFL),          // 24: too far
            longArrayOf(1L),                 // 1
            longArrayOf(0L, 0L),             // another length (a video): not comparable
            longArrayOf(3L),                 // 2
        )
        assertEquals(listOf(2, 4, 0), SimilarityPrints.closest(print, candidates, 12))
    }

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    /** The grouping as the scan did it before (lists, nullable distances), for comparison. */
    private fun referenceGroups(prints: List<LongArray>, video: BooleanArray, imageLimit: Int, videoLimit: Int): List<List<Int>> {
        fun distance(x: Int, y: Int): Int? {
            if (video[x] != video[y] || prints[x].size != prints[y].size) return null
            var d = 0
            for (i in prints[x].indices) d += java.lang.Long.bitCount(prints[x][i] xor prints[y][i])
            return d
        }
        val n = prints.size
        val assigned = BooleanArray(n)
        val clusters = mutableListOf<MutableList<Int>>()
        for (a in 0 until n) {
            if (assigned[a]) continue
            val limit = if (video[a]) videoLimit else imageLimit
            val group = mutableListOf(a)
            for (b in a + 1 until n) {
                if (assigned[b]) continue
                if (group.all { m -> distance(m, b)?.let { it <= limit } == true }) group += b
            }
            if (group.size > 1) group.forEach { assigned[it] = true }
            clusters += group
        }
        return clusters.filter { it.size > 1 }
    }
}

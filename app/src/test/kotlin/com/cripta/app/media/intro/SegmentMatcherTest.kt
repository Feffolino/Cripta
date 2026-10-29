package com.cripta.app.media.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random

class SegmentMatcherTest {
    private fun random(n: Int, seed: Long) = Random(seed).let { r -> IntArray(n) { r.nextInt() or 1 } }

    /** [src] with [bits] random bits flipped in each hash (a re-encode). */
    private fun noisy(src: IntArray, seed: Long, bits: Int = 2): IntArray {
        val r = Random(seed)
        return IntArray(src.size) { i -> var x = src[i]; repeat(bits) { x = x xor (1 shl r.nextInt(32)) }; x }
    }

    @Test
    fun findsSharedStretchAtAnyOffset() {
        val a = random(400, 1)
        val b = random(300, 2)
        noisy(a.copyOfRange(100, 250), 3).copyInto(b, destinationOffset = 40)
        val m = SegmentMatcher.best(a, b, minLength = 120)
        assertNotNull(m); m!!
        // Within a quarter of a second (4 hashes) of the shared stretch's real bounds.
        assertEquals(100.0, m.aStart.toDouble(), 5.0)
        assertEquals(250.0, m.aEnd.toDouble(), 5.0)
        assertEquals(m.aStart - 60, m.bStart)
        assertEquals(m.length, m.bEnd - m.bStart)
    }

    @Test
    fun nothingShared() {
        assertNull(SegmentMatcher.best(random(400, 1), random(400, 2), minLength = 120))
    }

    @Test
    fun sharedStretchShorterThanTheMinimum() {
        val a = random(400, 1)
        val b = random(300, 2)
        a.copyOfRange(100, 150).copyInto(b, destinationOffset = 40)
        assertNull(SegmentMatcher.best(a, b, minLength = 120))
    }

    @Test
    fun sharedSilenceIsNotAMatch() {
        val a = random(400, 1).also { IntArray(200).copyInto(it, 100) }
        val b = random(400, 2).also { IntArray(200).copyInto(it, 50) }
        assertNull(SegmentMatcher.best(a, b, minLength = 120))
    }
}

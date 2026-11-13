package com.cripta.app.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerQueueTest {
    @Test
    fun aSeekIsTakenOnceByItsOwnFile() {
        val queue = ViewerQueue()
        queue.set(listOf("a", "b"))
        queue.seekOnOpen("b", 5_000)
        assertNull(queue.takeSeek("a"))
        assertEquals(5_000L, queue.takeSeek("b"))
        assertNull(queue.takeSeek("b"))
    }

    // Regression: a seek armed for a viewer that never opened stayed, and the next opening of that
    // video from anywhere started at the face instead of where it was left.
    @Test
    fun aNewQueueDropsASeekNotTaken() {
        val queue = ViewerQueue()
        queue.set(listOf("a", "b"))
        queue.seekOnOpen("b", 5_000)
        queue.set(listOf("b"))
        assertNull(queue.takeSeek("b"))
    }
}

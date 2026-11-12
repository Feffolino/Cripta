package com.cripta.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OnceTest {
    @Test
    fun aFailedRunIsTriedAgain() {
        var calls = 0
        val once = Once { if (++calls == 1) throw IllegalStateException("first") }
        try {
            once.run()
            fail("the failure must reach the caller")
        } catch (e: IllegalStateException) {
            assertEquals("first", e.message)
        }
        once.run()
        once.run()
        assertEquals(2, calls)
    }

    @Test(timeout = 10_000)
    fun aCallerDuringTheRunWaitsForItToEnd() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val runs = AtomicInteger()
        val finished = AtomicInteger()
        val once = Once { runs.incrementAndGet(); started.countDown(); release.await() }
        val first = Thread { once.run() }.apply { start() }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        // Arrives while the first run is still going: it must not return before that run ends.
        val second = Thread { once.run(); finished.incrementAndGet() }.apply { start() }
        second.join(300)
        assertEquals(0, finished.get())
        release.countDown()
        first.join(); second.join()
        assertEquals(1, finished.get())
        assertEquals(1, runs.get())
    }
}

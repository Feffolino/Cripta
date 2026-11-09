package com.cripta.app

import android.app.Application
import android.util.Log
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExceptionLogTest {
    @Test
    fun exceptionLoggingUsesThePolicyOfTheBuildVariant() {
        val failure = IllegalStateException("URI is sensitive", IOException("/private/customer-document.pdf"))

        val tag = "ExceptionLogTest"
        logException(Log.ERROR, tag, "Import failed", failure)

        val entry = ShadowLog.getLogsForTag(tag).single()
        if (BuildConfig.DEBUG) {
            assertEquals("Import failed", entry.msg)
            assertSame(failure, entry.throwable)
        } else {
            assertEquals("Import failed (IllegalStateException caused by IOException)", entry.msg)
            assertNull(entry.throwable)
        }
    }

    @Test(timeout = 3_000)
    fun releaseMessageStopsAtACyclicCause() {
        val failure = IllegalStateException()
        val cause = IOException()
        failure.initCause(cause)
        cause.initCause(failure)

        assertEquals(
            "Import failed (IllegalStateException caused by IOException)",
            releaseExceptionMessage("Import failed", failure),
        )
    }
}

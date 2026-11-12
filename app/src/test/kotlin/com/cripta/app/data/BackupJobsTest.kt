package com.cripta.app.data

import android.app.Application
import android.net.Uri
import com.cripta.app.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Settings tells a running restore (the vault's other changes wait for it) from a backup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupJobsTest {
    @Before
    fun texts() = Strings.init(RuntimeEnvironment.getApplication())

    private fun request(restore: Boolean) =
        BackupJobs.Request(restore = restore, uri = Uri.parse("content://x/y"), passphrase = "p".toCharArray(), compressDocs = false)

    @Test
    fun aRunningRestoreIsMarkedAsOne() {
        val jobs = BackupJobs()
        jobs.offer(request(restore = true))
        assertEquals(true, jobs.running.value?.restore)
        jobs.take()   // the service takes it
        jobs.progress(0.5f)
        assertEquals(true, jobs.running.value?.restore)
        jobs.finish("ok")
        assertNull(jobs.running.value)
        jobs.offer(request(restore = false))
        jobs.take()
        assertEquals(false, jobs.running.value?.restore)
    }
}

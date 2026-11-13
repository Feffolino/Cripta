package com.cripta.app.data

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Resuming the waiting downloads and conversions failed at start (the vault locked while their
 * list was read): the next unlock tries again, in the same process. Home stays alive across the
 * lock, so its start-up try was the only one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class JobRestoreRetryTest {
    private lateinit var db: CriptaDatabase
    private lateinit var session: SessionManager
    private lateinit var repo: VaultRepository
    private val dek = DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes())

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        session = SessionManager()
        session.activate(dek, db)
        repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
    }

    private val reopened = mutableListOf<CriptaDatabase>()

    @After
    fun close() {
        db.close()
        reopened.forEach { it.close() }
    }

    /**
     * Lock, then unlock with a database of its own, as the app does. Reopening the same instance
     * would leave it to the lock's delayed close, which shuts it after a few seconds even in use.
     */
    private suspend fun relockAndOpen() {
        session.lock(); yield()
        val next = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CriptaDatabase::class.java)
            .allowMainThreadQueries().build().also { reopened += it }
        session.activate(dek, next); yield()
    }

    @Test
    fun aFailedResumeIsTriedAgainAtTheNextUnlock() = runBlocking {
        var retries = 0
        val watch = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { repo.jobRestoreRetries.collect { retries++ } }
        try {
            check(repo.claimJobRestore())
            repo.releaseJobRestore()   // the list could not be read: the vault locked meanwhile
            relockAndOpen()
            assertEquals(1, retries)
        } finally {
            watch.cancel()
        }
    }

    @Test
    fun aResumeThatWentThroughIsNotDoneAgain() = runBlocking {
        var retries = 0
        val watch = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { repo.jobRestoreRetries.collect { retries++ } }
        try {
            check(repo.claimJobRestore())
            relockAndOpen()
            assertEquals(0, retries)
        } finally {
            watch.cancel()
        }
    }
}

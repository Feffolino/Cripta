package com.cripta.app.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.cripta.app.Strings
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** A backup restored into the vault it came from adds nothing that is already there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestoreKnownFilesTest {
    private lateinit var app: Application
    private lateinit var db: CriptaDatabase
    private lateinit var repo: VaultRepository

    @Before
    fun open() {
        app = RuntimeEnvironment.getApplication()
        Strings.init(app)
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
    }

    @After
    fun close() = db.close()

    private suspend fun roundTrip(): RestoreReport {
        val file = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(file), "a passphrase".toCharArray())
        return repo.importBackup(Uri.fromFile(file), "a passphrase".toCharArray())
    }

    private suspend fun liveNotes() = db.fileDao().everyFile().filter { it.deletedAt == null }

    @Test
    fun aNoteRestoredIntoItsVaultIsNotAddedAgain() = runBlocking {
        repo.createNote(null, "alpha", "alpha")
        val report = roundTrip()
        assertEquals(1, liveNotes().size)
        assertEquals(1, report.duplicates)
    }

    @Test
    fun aNoteWithoutARecordedHashIsFoundToo() = runBlocking {
        // Notes written by earlier versions have no hash of their text.
        repo.createNote(null, "alpha", "alpha")
        db.openHelper.writableDatabase.execSQL("UPDATE files SET contentHash = NULL")
        val report = roundTrip()
        assertEquals(1, liveNotes().size)
        assertEquals(1, report.duplicates)
    }
}

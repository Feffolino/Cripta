package com.cripta.app.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FileEntity
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * A blob written for an import, a download or a conversion whose process was killed before its row
 * was committed is found and deleted at the next start; nothing listed, or still being written, is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InterruptedWritesTest {
    private lateinit var app: Application
    private lateinit var db: CriptaDatabase
    private lateinit var session: SessionManager
    private lateinit var settings: SettingsStore

    @Before
    fun open() {
        app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        settings = SettingsStore(app)
    }

    @After
    fun close() = db.close()

    /** What a new process finds: its own blob store (nothing being written) and repository. */
    private fun nextStart() = BlobStore(app).let { it to VaultRepository(app, session, it, settings) }

    @Test
    fun aBlobLeftByAKilledWriteGoesAtTheNextStart() = runBlocking {
        val killed = BlobStore(app)
        val orphan = UUID.randomUUID().toString()
        killed.beginWrite(orphan)
        killed.blob(orphan).writeBytes(ByteArray(1000))
        // The process dies here: no row, no cleanup.
        val (blobs, repo) = nextStart()
        repo.reconcileInterruptedWrites()
        assertFalse(blobs.blob(orphan).exists())
        assertTrue(blobs.unfinishedWrites().isEmpty())
    }

    @Test
    fun aBlobWhoseRowWasCommittedStays() = runBlocking {
        val killed = BlobStore(app)
        val id = UUID.randomUUID().toString()
        killed.beginWrite(id)
        killed.blob(id).writeBytes(ByteArray(10))
        db.fileDao().insert(FileEntity(id = id, originalName = "a.jpg", mimeType = "image/jpeg", sizeBytes = 10,
            createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0)))
        val (blobs, repo) = nextStart()
        repo.reconcileInterruptedWrites()
        assertTrue(blobs.blob(id).exists())
        assertTrue(blobs.unfinishedWrites().isEmpty())
    }

    @Test
    fun aWriteOfThisProcessIsLeftAlone() = runBlocking {
        val blobs = BlobStore(app)
        val repo = VaultRepository(app, session, blobs, settings)
        val running = UUID.randomUUID().toString()
        blobs.beginWrite(running)
        blobs.blob(running).writeBytes(ByteArray(10))
        repo.reconcileInterruptedWrites()
        assertTrue(blobs.blob(running).exists())
        blobs.endWrite(running)
        assertTrue(blobs.unfinishedWrites().isEmpty())
    }

    @Test
    fun anImportLeavesNothingToReconcile() = runBlocking {
        val blobs = BlobStore(app)
        val repo = VaultRepository(app, session, blobs, settings)
        val src = File.createTempFile("photo", ".jpg", app.cacheDir).apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        val f = repo.import(Uri.fromFile(src), null)
        repo.createNote(null, "n", "text")
        assertTrue(blobs.unfinishedWrites().isEmpty())
        assertEquals(2, db.fileDao().everyFile().size)
        assertTrue(blobs.blob(f.id).exists())
    }
}

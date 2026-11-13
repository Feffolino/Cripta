package com.cripta.app.data

import android.app.Application
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
 * Blobs no row lists (a deletion killed between its commit and the blob's removal, or a removal
 * that failed) are found and deleted; blobs with a row, being written, or of a restore are not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OrphanBlobsTest {
    private lateinit var app: Application
    private lateinit var db: CriptaDatabase
    private lateinit var session: SessionManager
    private lateinit var settings: SettingsStore
    private lateinit var blobs: BlobStore
    private lateinit var repo: VaultRepository

    @Before
    fun open() {
        app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        settings = SettingsStore(app)
        blobs = BlobStore(app)
        repo = VaultRepository(app, session, blobs, settings)
    }

    @After
    fun close() {
        db.close()
        File(app.filesDir, "blob-writes").let { if (it.isFile) it.delete() else it.deleteRecursively() }
    }

    private fun blobOf(id: String = UUID.randomUUID().toString()) = id.also { blobs.blob(it).writeBytes(ByteArray(100)) }

    private suspend fun row(id: String, trashed: Boolean = false) = db.fileDao().insert(FileEntity(
        id = id, originalName = "a.jpg", mimeType = "image/jpeg", sizeBytes = 100,
        createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0), deletedAt = if (trashed) 1L else null))

    @Test
    fun aBlobWithNoRowGoes() = runBlocking {
        val orphan = blobOf()
        repo.sweepOrphanBlobs()
        assertFalse(blobs.blob(orphan).exists())
    }

    @Test
    fun blobsWithTheirRowStayTrashedIncluded() = runBlocking {
        val live = blobOf().also { row(it) }
        val trashed = blobOf().also { row(it, trashed = true) }
        repo.sweepOrphanBlobs()
        assertTrue(blobs.blob(live).exists())
        assertTrue(blobs.blob(trashed).exists())
    }

    @Test
    fun aBlobBeingWrittenStays() = runBlocking {
        val id = UUID.randomUUID().toString()
        blobs.beginWrite(id)
        blobOf(id)
        repo.sweepOrphanBlobs()
        assertTrue(blobs.blob(id).exists())
        blobs.endWrite(id)
    }

    @Test
    fun aBlobOfARestoreStays() = runBlocking {
        val id = UUID.randomUUID().toString()
        blobs.journalRestored(id)
        blobOf(id)
        try {
            repo.sweepOrphanBlobs()
            assertTrue(blobs.blob(id).exists())
        } finally { blobs.clearRestoreJournal() }
    }

    @Test
    fun filesNotNamedAsBlobsStay() = runBlocking {
        val tmp = blobOf("${UUID.randomUUID()}.${UUID.randomUUID()}.tmp")
        val other = blobOf("something-else")
        repo.sweepOrphanBlobs()
        assertTrue(blobs.blob(tmp).exists())
        assertTrue(blobs.blob(other).exists())
    }

    @Test
    fun nothingGoesWhenTheDatabaseCannotBeRead() = runBlocking {
        val id = blobOf()
        db.close()
        runCatching { repo.sweepOrphanBlobs() }
        assertTrue(blobs.blob(id).exists())
    }

    @Test
    fun aWriteThatCouldNotBeNotedIsNotLeftAsRunning() {
        // The notes' folder cannot be made: the write fails before any blob is written.
        File(app.filesDir, "blob-writes").apply { deleteRecursively(); writeText("x") }
        val id = UUID.randomUUID().toString()
        assertTrue(runCatching { blobs.beginWrite(id) }.isFailure)
        assertFalse(id in blobs.writesInProgress())
    }

    @Test
    fun aRewrittenFolderDeleteJournalIsWholeAndLeavesNoTemporaryFile() {
        blobs.journalFolderDelete(listOf(1L), 5L)
        blobs.journalFolderDelete(listOf(1L, 2L, 3L), 5L)
        assertEquals(5L to listOf(1L, 2L, 3L), blobs.folderDeleteJournal())
        assertTrue(app.filesDir.listFiles()!!.none { it.name.startsWith("folder-delete-journal") && it.name != "folder-delete-journal" })
        blobs.clearFolderDeleteJournal()
    }
}

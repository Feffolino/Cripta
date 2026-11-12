package com.cripta.app.data

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A folder deleted without the trash, stopped half way: its folders must not stay closed (they
 * looked trashed with the trash off, and hid the files not destroyed yet).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FolderDeleteRecoveryTest {
    private lateinit var app: Application
    private lateinit var db: CriptaDatabase
    private lateinit var session: SessionManager
    private lateinit var blobs: BlobStore
    private lateinit var settings: SettingsStore
    private lateinit var repo: VaultRepository

    @Before
    fun open() {
        app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        blobs = BlobStore(app)
        blobs.clearFolderDeleteJournal()
        settings = SettingsStore(app)   // the trash is off by default
        repo = VaultRepository(app, session, blobs, settings)
    }

    @After
    fun close() = db.close()

    private fun file(id: String, folderId: Long?) = FileEntity(
        id = id, originalName = "$id.jpg", mimeType = "image/jpeg", sizeBytes = 1, folderId = folderId,
        createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0),
    )

    /** Folder "A" with "B" inside, one file in each. */
    private suspend fun tree(): Pair<Long, Long> {
        val a = db.folderDao().insert(FolderEntity(name = "A", createdAt = 0))
        val b = db.folderDao().insert(FolderEntity(name = "B", parentId = a, createdAt = 0))
        db.fileDao().insert(file("fa", a))
        db.fileDao().insert(file("fb", b))
        return a to b
    }

    @Test
    fun anErrorHalfWayReopensTheFoldersWithWhatIsLeft() = runBlocking {
        val (a, b) = tree()
        var calls = 0
        try {
            repo.deleteFolderRecursive(a) { id ->
                if (++calls == 2) throw java.io.IOException("disk")
                repo.secureDelete(id)
            }
            fail("the error must reach the caller")
        } catch (e: java.io.IOException) {
            assertEquals("disk", e.message)
        }
        assertNull(db.folderDao().byId(a)!!.deletedAt)
        assertNull(db.folderDao().byId(b)!!.deletedAt)
        // The first file was destroyed, the second is still there, live, in its folder.
        assertEquals(1, db.fileDao().byIds(listOf("fa", "fb")).count { it.deletedAt == null })
        assertNull(blobs.folderDeleteJournal())
    }

    @Test
    fun aKilledDeletionIsReopenedAtTheNextStartNotWhileItRuns() = runBlocking {
        val (a, b) = tree()
        var closedWhileRunning: Long? = null
        var afterOwnRepair: Long? = null
        var afterNextStart: Pair<Long?, Long?>? = null
        try {
            repo.deleteFolderRecursive(a) { _ ->
                closedWhileRunning = db.folderDao().byId(a)!!.deletedAt
                // The same process: the deletion is running, nothing to repair.
                repo.repairInterruptedFolderDeletes()
                afterOwnRepair = db.folderDao().byId(a)!!.deletedAt
                // What a new process finds (the journal and the closed folders): it reopens them.
                VaultRepository(app, session, blobs, settings).repairInterruptedFolderDeletes()
                afterNextStart = db.folderDao().byId(a)!!.deletedAt to db.folderDao().byId(b)!!.deletedAt
                throw IllegalStateException("killed")
            }
            fail("the error must reach the caller")
        } catch (e: IllegalStateException) {
            assertEquals("killed", e.message)
        }
        assertNotNull(closedWhileRunning)
        assertEquals(closedWhileRunning, afterOwnRepair)
        assertEquals(null to null, afterNextStart)
        assertNull(blobs.folderDeleteJournal())
        assertEquals(2, db.fileDao().byIds(listOf("fa", "fb")).count { it.deletedAt == null })
    }

    @Test
    fun theJournalOnlyReopensFoldersStillClosedWithItsTime() = runBlocking {
        val (a, b) = tree()
        db.folderDao().setDeletedAt(listOf(a), 100)
        db.folderDao().setDeletedAt(listOf(b), 200)   // trashed later, by something else
        blobs.journalFolderDelete(listOf(a, b), 100)
        repo.repairInterruptedFolderDeletes()
        assertNull(db.folderDao().byId(a)!!.deletedAt)
        assertEquals(200L, db.folderDao().byId(b)!!.deletedAt)
        assertNull(blobs.folderDeleteJournal())
    }

    @Test
    fun aCompleteDeletionLeavesNothingBehind() = runBlocking {
        val (a, b) = tree()
        val deleted = repo.deleteFolderRecursive(a)
        assertEquals(setOf("fa", "fb"), deleted.toSet())
        assertNull(db.folderDao().byId(a))
        assertNull(db.folderDao().byId(b))
        assertTrue(db.fileDao().byIds(listOf("fa", "fb")).isEmpty())
        assertNull(blobs.folderDeleteJournal())
    }
}

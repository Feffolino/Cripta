package com.cripta.app.data

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The trash cleanup decides a trashed folder is empty, then deletes it: a folder restored and
 * given a note in between must stay (deleted, the note was left in a folder that no longer exists).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TrashFolderRaceTest {
    private lateinit var db: CriptaDatabase
    private lateinit var repo: VaultRepository
    /** Run once, on the cleanup's thread, right after its last check of a folder's content. */
    @Volatile private var onLastCheck: (() -> Unit)? = null
    private val fired = AtomicBoolean(false)

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(RoomDatabase.QueryCallback { sql, _ ->
                if (sql.contains("SELECT COUNT(*) FROM folders WHERE parentId")) {
                    onLastCheck?.let { if (fired.compareAndSet(false, true)) it() }
                }
            }, Executor { it.run() })
            .build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
    }

    @After
    fun close() = db.close()

    @Test
    fun aFolderRestoredDuringTheCleanupStays() = runBlocking {
        val old = System.currentTimeMillis() - 40L * 86_400_000
        val folder = db.folderDao().insert(FolderEntity(name = "A", createdAt = 0, deletedAt = old))
        var noteId: String? = null
        onLastCheck = {
            // The user restores the folder and writes a note in it, between the check and the delete.
            val t = Thread {
                runBlocking {
                    repo.restoreFolder(folder)
                    assertNull(db.folderDao().byId(folder)!!.deletedAt)
                    noteId = repo.createNote(folder, "n", "text").id
                }
            }
            t.start(); t.join(10_000)
        }
        repo.purgeExpiredTrash(30)
        assertTrue(fired.get())
        val note = db.fileDao().byId(noteId!!)!!
        assertEquals(folder, note.folderId)
        val kept = db.folderDao().byId(folder)
        assertNotNull(kept)
        assertNull(kept!!.deletedAt)
    }

    @Test
    fun anEmptyExpiredFolderStillGoes() = runBlocking {
        val old = System.currentTimeMillis() - 40L * 86_400_000
        val folder = db.folderDao().insert(FolderEntity(name = "A", createdAt = 0, deletedAt = old))
        repo.purgeExpiredTrash(30)
        assertNull(db.folderDao().byId(folder))
    }
}

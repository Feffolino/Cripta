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
 * A folder created inside one being deleted, after the deletion listed what to delete: it must go
 * with it (or land at the root), never stay under a folder that no longer exists or is trashed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FolderCreateRaceTest {
    private lateinit var app: Application
    private lateinit var db: CriptaDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: VaultRepository
    /** Run once on the deletion's thread, at its first folder read after listing the subtree. */
    @Volatile private var afterListing: (() -> Unit)? = null
    private val listed = AtomicBoolean(false)
    private val fired = AtomicBoolean(false)

    @Before
    fun open() {
        app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(RoomDatabase.QueryCallback { sql, _ ->
                if (sql.startsWith("SELECT id, parentId FROM folders")) listed.set(true)
                else if (listed.get() && sql.startsWith("SELECT * FROM folders WHERE id")) {
                    afterListing?.let { if (fired.compareAndSet(false, true)) it() }
                }
            }, Executor { it.run() })
            .build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        settings = SettingsStore(app)
        repo = VaultRepository(app, session, BlobStore(app), settings)
    }

    @After
    fun close() {
        // The settings outlive the test (one store per process): the trash is off by default.
        runBlocking { settings.setTrashEnabled(false) }
        db.close()
    }

    private fun createChildMeanwhile(parent: Long): () -> Long? {
        var child: Long? = null
        afterListing = {
            val t = Thread { runBlocking { child = repo.createFolder("new", parent) } }
            t.start(); t.join(10_000)
        }
        return { child }
    }

    /** Every folder is at the root or under a folder that exists. */
    private suspend fun noOrphans() {
        val all = db.folderDao().liveNow() + db.folderDao().trashedNow()
        val ids = all.map { it.id }.toSet()
        all.forEach { f -> assertTrue("folder ${f.id} under a missing ${f.parentId}", f.parentId == null || f.parentId in ids) }
    }

    @Test
    fun aDeletionWithoutTheTrashTakesAFolderCreatedMeanwhile() = runBlocking {
        val a = db.folderDao().insert(FolderEntity(name = "A", createdAt = 0))
        val child = createChildMeanwhile(a)
        repo.deleteFolderRecursive(a)
        assertTrue(fired.get())
        assertNotNull(child())
        assertNull(db.folderDao().byId(a))
        noOrphans()
    }

    @Test
    fun aFolderCreatedMeanwhileGoesToTheTrashWithItsParent() = runBlocking {
        settings.setTrashEnabled(true)
        val a = db.folderDao().insert(FolderEntity(name = "A", createdAt = 0))
        val child = createChildMeanwhile(a)
        repo.deleteFolderRecursive(a)
        assertTrue(fired.get())
        val parent = db.folderDao().byId(a)!!
        assertNotNull(parent.deletedAt)
        // Trashed with the parent, restorable with it, not left live under a trashed folder.
        assertEquals(parent.deletedAt, db.folderDao().byId(child()!!)!!.deletedAt)
        repo.restoreFolder(a)
        assertNull(db.folderDao().byId(child()!!)!!.deletedAt)
    }

    @Test
    fun aFolderCreatedInATrashedOneGoesToTheRoot() = runBlocking {
        val a = db.folderDao().insert(FolderEntity(name = "A", createdAt = 0, deletedAt = 5))
        val id = repo.createFolder("new", a)
        assertNull(db.folderDao().byId(id)!!.parentId)
    }
}

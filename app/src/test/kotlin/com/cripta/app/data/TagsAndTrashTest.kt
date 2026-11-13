package com.cripta.app.data

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The everyday tag and trash operations of the repository, on a real (in-memory) vault. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TagsAndTrashTest {
    private lateinit var db: CriptaDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: VaultRepository

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        settings = SettingsStore(app)
        repo = VaultRepository(app, session, BlobStore(app), settings)
    }

    @After
    fun close() {
        runBlocking { settings.clearAll() }
        db.close()
    }

    private suspend fun tags(id: String) = repo.tagNamesOf(listOf(id)).getValue(id).toSet()
    private suspend fun tagId(name: String) = db.tagDao().byName(name)!!.id

    @Test
    fun setTagsReportsWhatItAddedAndRemovedAndKeepsTheLibrary() = runBlocking {
        val a = repo.createNote(null, "a", "x")
        repo.setTags(a.id, listOf("sea", "trip"))
        val diff = repo.setTags(a.id, listOf("trip", "  mountain "))
        assertEquals(setOf("trip", "mountain"), tags(a.id))
        assertEquals(listOf(tagId("mountain")), diff.added.map { it.tagId })
        assertEquals(listOf(tagId("sea")), diff.removed.map { it.tagId })
        // A tag on no file stays in the library until deleted.
        assertNotNull(db.tagDao().byName("sea"))
    }

    @Test
    fun aTagIsCreatedOnceWhateverItsCase() = runBlocking {
        val first = repo.createTag("Sea")
        assertTrue(first.created)
        assertEquals(repo.createTag(" Sea ").id, first.id)
        val clash = repo.createTag("sea")
        assertFalse(clash.created)
        assertEquals(first.id, clash.id)
        assertFalse(repo.createTag("   ").created)
    }

    @Test
    fun mergingMovesEveryFileToTheOtherTagAndRemembersWhereItWent() = runBlocking {
        val a = repo.createNote(null, "a", "x")
        val b = repo.createNote(null, "b", "y")
        repo.addTags(a.id, listOf("sea"))
        repo.addTags(b.id, listOf("sea", "beach"))
        val sea = tagId("sea"); val beach = tagId("beach")
        assertTrue(repo.mergeTags(sea, beach))
        assertEquals(setOf("beach"), tags(a.id))
        assertEquals(setOf("beach"), tags(b.id))
        assertNull(db.tagDao().byId(sea))
        assertEquals(beach, repo.tagMergedInto(sea))
        // Gone or the same tag: nothing happens.
        assertFalse(repo.mergeTags(sea, beach))
        assertFalse(repo.mergeTags(beach, beach))
    }

    @Test
    fun deletingATagTakesItOffItsFiles() = runBlocking {
        val a = repo.createNote(null, "a", "x")
        repo.addTags(a.id, listOf("sea", "trip"))
        repo.deleteTag(tagId("sea"))
        assertEquals(setOf("trip"), tags(a.id))
        assertNull(db.tagDao().byName("sea"))
        assertNull(repo.tagMergedInto(tagId("trip")))
    }

    @Test
    fun onlyTagsOnNoFileGoAsUnused() = runBlocking {
        val a = repo.createNote(null, "a", "x")
        repo.addTags(a.id, listOf("used"))
        val unused = repo.createTag("unused").id
        assertEquals(1, repo.deleteUnusedTags(listOf(unused, tagId("used"))))
        assertNull(db.tagDao().byId(unused))
        assertNotNull(db.tagDao().byName("used"))
        // A tag on a file in the trash is still used.
        repo.trash(a.id)
        assertEquals(0, repo.deleteUnusedTags(listOf(tagId("used"))))
    }

    @Test
    fun aTrashedFileComesBackWhereItWasOrAtTheRootIfItsFolderIsGone() = runBlocking {
        val folder = repo.createFolder("Trips", null)
        val a = repo.createNote(folder, "a", "x")
        val b = repo.createNote(folder, "b", "y")
        repo.trash(a.id)
        assertNotNull(db.fileDao().byId(a.id)!!.deletedAt)
        repo.restore(a.id)
        assertNull(db.fileDao().byId(a.id)!!.deletedAt)
        assertEquals(folder, db.fileDao().byId(a.id)!!.folderId)

        repo.trash(b.id)
        db.openHelper.writableDatabase.execSQL("DELETE FROM folders WHERE id = $folder")
        repo.restore(b.id)
        assertNull(db.fileDao().byId(b.id)!!.folderId)
        assertNull(db.fileDao().byId(b.id)!!.deletedAt)
    }

    @Test
    fun emptyingTheTrashDestroysOnlyWhatIsInIt() = runBlocking {
        val a = repo.createNote(null, "a", "x")
        val b = repo.createNote(null, "b", "y")
        repo.trash(a.id)
        assertEquals(listOf(a.id), repo.emptyTrash())
        assertNull(db.fileDao().byId(a.id))
        assertNotNull(db.fileDao().byId(b.id))
    }

    @Test
    fun deleteOrTrashFollowsTheTrashSetting() = runBlocking {
        val a = repo.createNote(null, "a", "x")
        val b = repo.createNote(null, "b", "y")
        settings.setTrashEnabled(true)
        assertTrue(repo.deleteOrTrash(a.id))
        assertNotNull(db.fileDao().byId(a.id)!!.deletedAt)
        settings.setTrashEnabled(false)
        assertFalse(repo.deleteOrTrash(b.id))
        assertNull(db.fileDao().byId(b.id))
    }
}

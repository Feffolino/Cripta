package com.cripta.app.data.db

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The queries behind the tag and folder fixes, on a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TagFolderQueriesTest {
    private lateinit var db: CriptaDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CriptaDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private fun file(id: String, folderId: Long?, hash: String? = null, deletedAt: Long? = null) = FileEntity(
        id = id, originalName = "$id.jpg", mimeType = "image/jpeg", sizeBytes = 1, folderId = folderId,
        createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0), contentHash = hash, deletedAt = deletedAt,
    )

    @Test
    fun namesThatDifferOnlyInCaseAreTwoTags() = runBlocking {
        val a = db.tagDao().insert(TagEntity(name = "Viaggi"))
        val b = db.tagDao().insert(TagEntity(name = "viaggi"))
        assertTrue(a > 0 && b > 0 && a != b)
        assertEquals(a, db.tagDao().byName("Viaggi")!!.id)
        assertEquals(b, db.tagDao().byName("viaggi")!!.id)
    }

    @Test
    fun theEditorUpdatesEveryFieldByIdAtOnce() = runBlocking {
        val id = db.tagDao().insert(TagEntity(name = "Mare"))
        assertEquals(1, db.tagDao().update(id, "Spiaggia", "SP", 0xFF112233.toInt(), true, "water-wave"))
        val t = db.tagDao().byId(id)!!
        assertEquals("Spiaggia", t.name); assertEquals("SP", t.alias); assertEquals(0xFF112233.toInt(), t.color)
        assertTrue(t.pinned); assertEquals("water-wave", t.icon)
        assertEquals(0, db.tagDao().update(9999, "X", null, null, false, null))
    }

    @Test
    fun linkSaysWhetherTheFileAlreadyHadTheTag() = runBlocking {
        db.fileDao().insert(file("f", null))
        val t = db.tagDao().insert(TagEntity(name = "A"))
        assertTrue(db.tagDao().link(FileTagCrossRef("f", t)) != -1L)
        assertEquals(-1L, db.tagDao().link(FileTagCrossRef("f", t)))
        assertEquals(listOf(FileTagCrossRef("f", t)), db.tagDao().linksAmong(listOf("f", "g"), listOf(t)))
        assertEquals(1, db.tagDao().unlink("f", t))
        assertEquals(0, db.tagDao().unlink("f", t))
    }

    @Test
    fun linksToNothingAreRemoved() = runBlocking {
        db.fileDao().insert(file("f", null))
        val t = db.tagDao().insert(TagEntity(name = "A"))
        db.tagDao().link(FileTagCrossRef("f", t))
        db.tagDao().link(FileTagCrossRef("f", 777))      // a tag deleted while a download waited
        db.tagDao().link(FileTagCrossRef("gone", t))     // a file no longer there
        assertEquals(2, db.tagDao().deleteOrphanLinks())
        assertEquals(listOf(t), db.tagDao().tagIdsOf("f"))
        assertEquals(listOf(t), db.tagDao().existingIds(listOf(t, 777)))
    }

    @Test
    fun aMovedFileStopsBeingTheCoverOfItsOldFolder() = runBlocking {
        val a = db.folderDao().insert(FolderEntity(name = "A", parentId = null, createdAt = 0))
        val b = db.folderDao().insert(FolderEntity(name = "B", parentId = null, createdAt = 0))
        db.fileDao().insert(file("f", a))
        db.folderDao().setCover(a, "f")
        assertEquals(1, db.fileDao().move("f", b))
        db.folderDao().dropCoverOf("f", keepFolderId = b)
        assertNull(db.folderDao().byId(a)!!.coverFileId)
        // Its new folder can still pick it.
        db.folderDao().setCover(b, "f")
        db.folderDao().dropCoverOf("f", keepFolderId = b)
        assertEquals("f", db.folderDao().byId(b)!!.coverFileId)
    }

    @Test
    fun anAncestorExcludedFromRandomIsSeen() = runBlocking {
        val top = db.folderDao().insert(FolderEntity(name = "Top", parentId = null, createdAt = 0))
        val mid = db.folderDao().insert(FolderEntity(name = "Mid", parentId = top, createdAt = 0))
        val leaf = db.folderDao().insert(FolderEntity(name = "Leaf", parentId = mid, createdAt = 0))
        assertFalse(db.folderDao().ancestorExcludedFromRandom(leaf))
        db.folderDao().setRandomExcluded(top, true)
        assertTrue(db.folderDao().ancestorExcludedFromRandom(leaf))
        // Its own switch is not "an ancestor".
        assertFalse(db.folderDao().ancestorExcludedFromRandom(top))
        // A trashed folder can't be switched (the menu then says nothing changed).
        db.folderDao().setDeletedAt(listOf(mid), 1)
        assertEquals(0, db.folderDao().setRandomExcluded(mid, true))
    }

    @Test
    fun theColourAloneKeepsAnOldEmoji() = runBlocking {
        val id = db.folderDao().insert(FolderEntity(name = "A", parentId = null, createdAt = 0))
        db.folderDao().setEmoji(id, "🦄")
        assertEquals(1, db.folderDao().setColor(id, 0xFF00FF00.toInt()))
        assertEquals("🦄", db.folderDao().byId(id)!!.emoji)
        db.folderDao().setStyle(id, null, "star")
        assertNull(db.folderDao().byId(id)!!.emoji)
    }

    @Test
    fun theSameContentIsFoundOnlyInItsFolderAndAlive() = runBlocking {
        val a = db.folderDao().insert(FolderEntity(name = "A", parentId = null, createdAt = 0))
        db.fileDao().insert(file("f", a, hash = "h"))
        db.fileDao().insert(file("r", null, hash = "h"))
        db.fileDao().insert(file("t", a, hash = "x", deletedAt = 5))
        assertEquals(listOf("f"), db.fileDao().sameContentIn("h", a))
        assertEquals(listOf("r"), db.fileDao().sameContentIn("h", null))
        assertTrue(db.fileDao().sameContentIn("x", a).isEmpty())
    }
}

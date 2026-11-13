package com.cripta.app.data.dedup

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
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
import java.io.RandomAccessFile

/**
 * Resolving duplicates deletes for good (the trash is off by default): what the copies carried
 * must reach the copy kept, an undo must take it back, and the scan must offer only copies that
 * can be read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DupMergeTest {
    private lateinit var db: CriptaDatabase
    private lateinit var blobs: BlobStore
    private lateinit var repo: VaultRepository
    private lateinit var scanner: DuplicateScanner

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        blobs = BlobStore(app)
        repo = VaultRepository(app, session, blobs, SettingsStore(app))
        scanner = DuplicateScanner(repo, session)
    }

    @After
    fun close() = db.close()

    private suspend fun tags(id: String) = repo.tagNamesOf(listOf(id)).getValue(id).toSet()

    @Test
    fun theKeptCopyGetsTheTagsFavoriteAndLinkOfTheOthersAndTheUndoTakesThemBack() = runBlocking {
        val keep = repo.createNote(null, "keep", "alpha")
        val b = repo.createNote(null, "b", "alpha")
        val c = repo.createNote(null, "c", "alpha")
        repo.addTags(keep.id, listOf("home"))
        repo.addTags(b.id, listOf("home", "trip"))
        repo.addTags(c.id, listOf("summer"))
        db.fileDao().setFavorite(c.id, true)
        repo.setSourceUrl(b.id, "https://example.org/b")
        repo.setSourceUrl(c.id, "https://example.org/c")

        val out = repo.mergeDuplicates(keep.id, listOf(b.id, c.id), exact = true, provedHash = db.fileDao().byId(keep.id)!!.contentHash)
        assertTrue(out.merged)
        assertEquals(setOf("trip", "summer"), out.tagsMoved.toSet())
        assertEquals(setOf("home", "trip", "summer"), tags(keep.id))
        val kept = db.fileDao().byId(keep.id)!!
        assertTrue(kept.isFavorite)
        // The first copy's link, in the order given.
        assertEquals("https://example.org/b", kept.sourceUrl)
        assertNull(db.fileDao().byId(b.id))
        assertNull(db.fileDao().byId(c.id))

        repo.undoMergeOnKept(keep.id, out.tagsMoved, out.previousFavorite, out.previousSourceUrl)
        assertEquals(setOf("home"), tags(keep.id))
        val undone = db.fileDao().byId(keep.id)!!
        assertFalse(undone.isFavorite)
        assertNull(undone.sourceUrl)
    }

    @Test
    fun theKeptCopysOwnLinkAndFavoriteStay() = runBlocking {
        val keep = repo.createNote(null, "keep", "alpha")
        val b = repo.createNote(null, "b", "alpha")
        db.fileDao().setFavorite(keep.id, true)
        repo.setSourceUrl(keep.id, "https://example.org/keep")
        repo.setSourceUrl(b.id, "https://example.org/b")
        val out = repo.mergeDuplicates(keep.id, listOf(b.id))
        assertTrue(out.merged)
        assertTrue(out.previousFavorite)
        assertEquals("https://example.org/keep", out.previousSourceUrl)
        assertEquals("https://example.org/keep", db.fileDao().byId(keep.id)!!.sourceUrl)
    }

    @Test
    fun groupsComeLargestSavingFirstOldestCopyFirstWithoutTheUnreadableCopy() = runBlocking {
        val small1 = repo.createNote(null, "s1", "alpha")
        val small2 = repo.createNote(null, "s2", "alpha")
        val big = "a much longer text"
        val big1 = repo.createNote(null, "b1", big)
        val big2 = repo.createNote(null, "b2", big)
        val big3 = repo.createNote(null, "b3", big)
        val broken = repo.createNote(null, "b4", big)
        repo.createNote(null, "other", "alphb")   // same size as alpha, other content
        // Every one of these carries its import hash: the scan reads only the matches, and this one fails.
        RandomAccessFile(blobs.blob(broken.id), "rw").use { it.setLength(3) }

        val groups = scanner.scanExact().groups
        assertEquals(2, groups.size)
        assertEquals(listOf(big1.id, big2.id, big3.id), groups[0].files.map { it.id })
        assertEquals(listOf(small1.id, small2.id), groups[1].files.map { it.id })
    }

    @Test
    fun copiesOfFindsOnlyIdenticalLiveFiles() = runBlocking {
        val a = repo.createNote(null, "a", "alpha")
        val b = repo.createNote(null, "b", "alpha")
        repo.createNote(null, "c", "alphb")
        val d = repo.createNote(null, "d", "alpha")
        repo.mergeDuplicates(b.id, listOf(d.id))   // d deleted
        assertEquals(listOf(b.id), scanner.copiesOf(db.fileDao().byId(a.id)!!).map { it.id })
        // Older files without a recorded hash are read and compared too.
        db.openHelper.writableDatabase.execSQL("UPDATE files SET contentHash = NULL")
        assertEquals(listOf(b.id), scanner.copiesOf(db.fileDao().byId(a.id)!!).map { it.id })
    }
}

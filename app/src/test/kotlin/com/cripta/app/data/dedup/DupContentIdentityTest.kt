package com.cripta.app.data.dedup

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FileEntity
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
import java.io.RandomAccessFile

/**
 * A duplicate result can be opened again later: nothing may be deleted as a copy unless every copy
 * still holds the content the scan proved, and the copy kept can be read to its end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DupContentIdentityTest {
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
        repo = VaultRepository(app, session, blobs, SettingsStore(app))   // the trash is off by default
        scanner = DuplicateScanner(repo, session)
    }

    @After
    fun close() = db.close()

    private suspend fun text(id: String) = repo.noteText(db.fileDao().byId(id)!!)

    /** Notes from before hashes were recorded for them: the scan computes them. */
    private fun forgetHashes() = db.openHelper.writableDatabase.execSQL("UPDATE files SET contentHash = NULL")

    private suspend fun onlyGroup(): List<FileEntity> = scanner.scanExact().groups.single().files

    @Test
    fun theScanResultCarriesTheHashItComputed() = runBlocking {
        repo.createNote(null, "a", "alpha")
        repo.createNote(null, "b", "alpha")
        forgetHashes()
        val group = onlyGroup()
        assertEquals(2, group.size)
        assertTrue(group.all { it.contentHash != null })
        assertEquals(1, group.map { it.contentHash }.distinct().size)
    }

    @Test
    fun theProgressCountsTheFilesReadWhole() = runBlocking {
        repo.createNote(null, "a", "alpha")
        repo.createNote(null, "b", "alpha")
        forgetHashes()
        val seen = mutableListOf<Pair<Int, Int>>()
        scanner.scanExact { done, total -> seen += done to total }
        // Two files read head and tail, then both read whole: 100% only at the very end.
        assertEquals(4 to 4, seen.last())
        assertTrue(seen.dropLast(1).none { (done, total) -> done == total && total == 4 })
    }

    @Test
    fun aCopyEditedToTheSameSizeIsNotDeleted() = runBlocking {
        val a = repo.createNote(null, "a", "alpha")
        val b = repo.createNote(null, "b", "alpha")
        forgetHashes()
        val group = onlyGroup()
        repo.updateNote(b.id, "b", "bravo")   // same length, other text
        val out = repo.mergeDuplicates(a.id, listOf(b.id), exact = true, provedHash = group.first().contentHash)
        assertFalse(out.merged)
        assertEquals(setOf(b.id), out.stale)
        assertEquals("bravo", text(b.id))
    }

    @Test
    fun aKeptCopyEditedSinceTheScanDeletesNothing() = runBlocking {
        val a = repo.createNote(null, "a", "alpha")
        val b = repo.createNote(null, "b", "alpha")
        val group = onlyGroup()
        repo.updateNote(a.id, "a", "new text")
        val out = repo.mergeDuplicates(a.id, listOf(b.id), exact = true, provedHash = group.first().contentHash)
        assertFalse(out.merged)
        assertEquals(setOf(a.id), out.stale)
        assertEquals("alpha", text(b.id))
    }

    @Test
    fun aKeptCopyDamagedInTheMiddleDeletesNothing() = runBlocking {
        // Four encrypted segments of 1 MB: the scan of files hashed at import reads the first and
        // the last, the damage is in between.
        val big = "x".repeat(3_500_000)
        val a = repo.createNote(null, "a", big)
        val b = repo.createNote(null, "b", big)
        assertEquals(2, onlyGroup().size)
        RandomAccessFile(blobs.blob(a.id), "rw").use { f ->
            f.seek(f.length() / 2)
            val v = f.read()
            f.seek(f.length() / 2)
            f.write(v xor 0xFF)
        }
        val group = onlyGroup()   // still found: only head and tail are read
        assertEquals(2, group.size)
        val out = repo.mergeDuplicates(a.id, listOf(b.id), exact = true, provedHash = group.first().contentHash)
        assertFalse(out.merged)
        assertEquals(setOf(a.id), out.stale)
        assertEquals(big, text(b.id))
    }

    @Test
    fun copiesStillAsScannedAreMerged() = runBlocking {
        val a = repo.createNote(null, "a", "alpha")
        val b = repo.createNote(null, "b", "alpha")
        val group = onlyGroup()
        val out = repo.mergeDuplicates(a.id, listOf(b.id), exact = true, provedHash = group.first().contentHash)
        assertTrue(out.merged)
        assertNull(db.fileDao().byId(b.id))
        assertEquals("alpha", text(a.id))
    }

    @Test
    fun aRefreshNeverTakesTheNewStateAsTheProof() {
        fun f(size: Long, hash: String?, deletedAt: Long? = null) = FileEntity(
            id = "b", originalName = "b.md", mimeType = "text/markdown", sizeBytes = size, createdAt = 0,
            importedAt = 0, wrappedKeyset = ByteArray(0), contentHash = hash, deletedAt = deletedAt,
        )
        val scanned = f(5, "h1")
        // Edited since (another hash, even another size): out of the group.
        assertNull(DupIdentity.stillScanned(scanned, f(5, "h2")))
        assertNull(DupIdentity.stillScanned(scanned, f(9, null)))
        assertNull(DupIdentity.stillScanned(scanned, f(5, "h1", deletedAt = 1)))
        assertNull(DupIdentity.stillScanned(scanned, null))
        assertEquals("h1", DupIdentity.stillScanned(scanned, f(5, "h1"))!!.contentHash)
        // A similar group saw no hash: one computed since is fine, and is not taken as the proof.
        val similar = DupIdentity.stillScanned(f(5, null), f(5, "h3"))
        assertNotNull(similar)
        assertNull(similar!!.contentHash)
    }
}

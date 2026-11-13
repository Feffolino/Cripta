package com.cripta.app.data.db

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A hash computed late (a duplicate scan) only fills a missing one: never over a note saved meanwhile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ContentHashTest {
    private lateinit var db: CriptaDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CriptaDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private suspend fun note(hash: String?) = db.fileDao().insert(FileEntity(
        id = "n", originalName = "n.md", mimeType = "text/markdown", sizeBytes = 2,
        createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0), contentHash = hash))

    @Test
    fun aMissingHashIsFilled() = runBlocking {
        note(null)
        db.fileDao().setContentHash("n", "old")
        assertEquals("old", db.fileDao().byId("n")!!.contentHash)
    }

    @Test
    fun aScanThatReadTheOldTextDoesNotOverwriteTheNewHash() = runBlocking {
        note(null)
        // The scan read T1; the user saved T2 before the scan wrote its hash.
        db.fileDao().setNoteContent("n", "n.md", "text/markdown", 2, "new")
        db.fileDao().setContentHash("n", "old")
        assertEquals("new", db.fileDao().byId("n")!!.contentHash)
    }
}

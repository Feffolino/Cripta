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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * With the trash on, "Tieni" runs without a confirmation: a second tap on the same group, queued
 * behind the first, must find its copies already gone and touch nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DupResolveTwiceTest {
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
        runBlocking { settings.setTrashEnabled(true) }
    }

    @After
    fun close() {
        // The settings outlive the test (one store per process): the trash is off by default.
        runBlocking { settings.setTrashEnabled(false) }
        db.close()
    }

    @Test
    fun aSecondResolutionOfTheSameGroupChangesNothing() = runBlocking {
        val a = repo.createNote(null, "a", "alpha")
        val b = repo.createNote(null, "b", "alpha")
        repo.addTags(b.id, listOf("trip"))
        val first = repo.mergeDuplicates(a.id, listOf(b.id))
        assertTrue(first.merged)
        assertEquals(listOf("trip"), first.tagsMoved)
        val trashedAt = db.fileDao().byId(b.id)!!.deletedAt!!
        Thread.sleep(5)

        val second = repo.mergeDuplicates(a.id, listOf(b.id))
        assertFalse(second.merged)
        // The trash date is the first one (the copy is not purged later than it should be) and the
        // undo of the first resolution still knows the tag it added.
        assertEquals(trashedAt, db.fileDao().byId(b.id)!!.deletedAt)
        assertTrue(second.tagsMoved.isEmpty())
    }
}

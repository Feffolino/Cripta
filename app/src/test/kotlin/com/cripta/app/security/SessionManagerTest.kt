package com.cripta.app.security

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.crypto.DekManager
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Locking hides the vault at once and drops the keys, except under background work that still needs them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionManagerTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val opened = mutableListOf<CriptaDatabase>()
    private val session = SessionManager()
    private val dekBytes = DekManager.newDekKeysetBytes()

    private fun newDb() = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        .also { opened += it }

    private fun unlock(db: CriptaDatabase = newDb()) = db.also { session.activate(DekManager.dekAeadFromBytes(dekBytes), it, dekBytes) }

    @Before
    fun startsLocked() {
        assertTrue(session.locked.value)
        assertFalse(session.isUnlocked)
        assertFalse("no keys, no work", session.beginWork())
    }

    @After
    fun close() = opened.forEach { runCatching { it.close() } }

    @Test
    fun lockingWithNoWorkDropsTheKeysAtOnce() {
        val db = unlock()
        assertTrue(session.isUnlocked)
        assertSame(db, session.database.value)
        assertArrayEquals(dekBytes, session.dekBytesCopy())
        session.lock()
        assertTrue(session.locked.value)
        assertNull(session.database.value)
        assertNull(session.dbOrNull())
        assertNull(session.dekBytesCopy())
        assertThrows(IllegalStateException::class.java) { session.requireDek() }
        assertFalse(session.beginWork())
    }

    @Test
    fun runningWorkKeepsTheKeysUntilItEnds() {
        val db = unlock()
        assertTrue(session.beginWork())
        assertTrue(session.beginWork())
        session.lock()
        // The vault is hidden at once…
        assertTrue(session.locked.value)
        assertNull(session.database.value)
        assertNull(session.dekBytesCopy())
        // …but the jobs keep their keys until the last one ends.
        assertSame(db, session.dbOrNull())
        session.requireDek()
        session.endWork()
        assertTrue(session.hasActiveWork)
        assertSame(db, session.dbOrNull())
        session.endWork()
        assertFalse(session.hasActiveWork)
        assertNull(session.dbOrNull())
        assertThrows(IllegalStateException::class.java) { session.requireDek() }
    }

    @Test
    fun unlockingAgainUnderRunningWorkKeepsItsDatabase() {
        val db = unlock()
        assertTrue(session.beginWork())
        session.lock()
        // A second Room instance over the same file is not opened: the retained one goes on.
        unlock(newDb())
        assertSame(db, session.database.value)
        assertTrue(session.isUnlocked)
        session.endWork()
        // The pending release was cancelled by the unlock: the keys stay.
        assertSame(db, session.dbOrNull())
        assertTrue(session.isUnlocked)
    }

    @Test
    fun anUnbalancedEndWorkDoesNotGoBelowZero() {
        unlock()
        session.endWork()
        assertFalse(session.hasActiveWork)
        assertTrue(session.beginWork())
        assertTrue(session.hasActiveWork)
        session.endWork()
        assertFalse(session.hasActiveWork)
    }

    @Test
    fun lockAndCloseNowIgnoresTheWork() {
        val db = unlock()
        assertTrue(session.beginWork())
        session.lockAndCloseNow()
        assertNull(session.dbOrNull())
        assertFalse(db.isOpen)
        assertEquals(true, session.locked.value)
    }

    @Test
    fun anUnlockResetsTheBackgroundTime() {
        unlock()
        session.backgroundedAt = 123L
        session.lock()
        unlock()
        assertEquals(0L, session.backgroundedAt)
    }
}

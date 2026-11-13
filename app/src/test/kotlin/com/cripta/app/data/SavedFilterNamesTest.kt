package com.cripta.app.data

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.security.SessionManager
import com.cripta.app.ui.vault.limitName
import com.cripta.app.ui.vault.MAX_NAME_LENGTH
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

/** Names typed in the vault's prompts: a saved filter's is its own, and no name runs on forever. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavedFilterNamesTest {
    private lateinit var db: CriptaDatabase
    private lateinit var repo: VaultRepository

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
    }

    @After
    fun close() = db.close()

    @Test
    fun aSecondFilterWithTheSameNameIsRefused() = runBlocking {
        assertTrue(repo.saveFilter("Mare", "{}"))
        // Case and spaces aside, as for the tags.
        assertFalse(repo.saveFilter("  mare ", """{"q":"x"}"""))
        assertEquals(listOf("{}"), db.savedFilterDao().allNow().map { it.json })
    }

    @Test
    fun aLongNameIsCutWithoutSplittingACharacter() {
        assertEquals("abc", limitName("abc"))
        assertEquals(MAX_NAME_LENGTH, limitName("a".repeat(1000)).length)
        // An emoji (two chars) across the limit goes whole, not half.
        val cut = limitName("a".repeat(MAX_NAME_LENGTH - 1) + "😀")
        assertEquals("a".repeat(MAX_NAME_LENGTH - 1), cut)
    }
}

package com.cripta.app.ui.note

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.cripta.app.Strings
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.SharedLinkStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

/** The note being written is kept in the vault when Cripta goes to the background, once. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoteEditorAutoSaveTest {
    private lateinit var db: CriptaDatabase
    private lateinit var repo: VaultRepository
    private lateinit var vm: NoteEditorViewModel

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        Strings.init(app)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
        vm = NoteEditorViewModel(repo, ThumbnailLoader(app, repo, session), SharedLinkStore(), SavedStateHandle())
    }

    @After
    fun close() {
        if (db.isOpen) db.close()
        Dispatchers.resetMain()
    }

    private suspend fun settled() = withTimeout(10_000) { while (vm.saving) delay(10) }

    private suspend fun notes() = repo.allFilesSnapshot()

    /** The app goes to the background (ON_STOP) with the editor of [id] open. */
    private suspend fun stop(id: String?) { vm.saveInBackground(id, null); settled() }

    @Test
    fun aNewNoteGoesToTheVaultOnceWhenTheAppStopsTwice() = runBlocking {
        vm.load(null)
        vm.name = "Spesa"; vm.text = "latte"
        stop(null)
        stop(null)
        val saved = notes().single()
        assertEquals("latte", repo.noteText(saved))
        assertFalse(vm.dirty)
    }

    @Test
    fun aNoteSavedInTheBackgroundIsUpdatedNotCreatedAgain() = runBlocking {
        vm.load(null)
        vm.name = "Spesa"; vm.text = "latte"
        stop(null)
        vm.text = "latte, pane"
        stop(null)
        // "Fatto" afterwards writes the same note.
        vm.text = "latte, pane, uova"
        vm.save(null, null) {}
        settled()
        val saved = notes().single()
        assertEquals("latte, pane, uova", repo.noteText(saved))
    }

    @Test
    fun anEmptyNewNoteIsNotWritten() = runBlocking {
        vm.load(null)
        stop(null)
        assertEquals(0, notes().size)
    }

    @Test
    fun aNoteThatCannotBeSavedInTheBackgroundIsSaidInANotification() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        vm.load(null)
        vm.name = "Diario"; vm.text = "testo privato"
        db.close()   // the write fails, as with a full disk
        var failed = false
        vm.saveInBackground(null, null) { failed = true; NoteSaveNotice.post(app) }
        settled()
        assertTrue(failed)
        assertTrue(vm.dirty)
        val mgr = app.getSystemService(android.app.NotificationManager::class.java)
        val posted = org.robolectric.Shadows.shadowOf(mgr).getNotification(NoteSaveNotice.ID)
        val shown = listOf(android.app.Notification.EXTRA_TITLE, android.app.Notification.EXTRA_TEXT, android.app.Notification.EXTRA_BIG_TEXT)
            .joinToString(" ") { posted.extras.getCharSequence(it)?.toString().orEmpty() }
        assertFalse(shown, shown.contains("Diario") || shown.contains("testo privato"))
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, posted.visibility)
        NoteSaveNotice.cancel(app)
        assertNull(org.robolectric.Shadows.shadowOf(mgr).getNotification(NoteSaveNotice.ID))
    }

    @Test
    fun anExistingNoteChangedIsWrittenInTheBackground() = runBlocking {
        val note = repo.createNote(null, "Lista", "uno")
        vm.load(note.id)
        stop(note.id)
        assertEquals("uno", repo.noteText(repo.fileById(note.id)!!))
        vm.text = "uno, due"
        stop(note.id)
        assertEquals("uno, due", repo.noteText(repo.fileById(note.id)!!))
        assertEquals(1, notes().size)
    }
}

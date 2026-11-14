package com.cripta.app.ui.download

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.DownloadQueue
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.SharedLinkStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.media.YtdlpDownloader
import com.cripta.app.security.SessionManager
import com.cripta.app.viewer.ViewerQueue
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "Nuova cartella" in the download destination picker runs on the screen's own coroutine scope,
 * which has no error handler: a database error (the vault locked as it was tapped, a full phone)
 * closed the app. It is now a folder not made, as in Cartelle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadNewFolderTest {
    private fun open(): Pair<SessionManager, DownloadViewModel> {
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        val settings = SettingsStore(app)
        val repo = VaultRepository(app, session, BlobStore(app), settings)
        val vm = DownloadViewModel(repo, DownloadQueue(repo), YtdlpDownloader(app), SharedLinkStore(), settings, ViewerQueue())
        return session to vm
    }

    @Test fun aNewFolderIsMade() = runBlocking {
        val (_, vm) = open()
        assertNotNull(vm.createFolder("Video", null))
    }

    @Test fun aNewFolderAfterTheLockIsNotMadeAndNothingCrashes() = runBlocking {
        val (session, vm) = open()
        session.lock()
        assertNull(vm.createFolder("Video", null))
    }
}

package com.cripta.app.data.face

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The face on a cover is asked for by the grid on its own: when the vault locks first, there is no
 * face to show, not a crash. Seen on 0.1.0-b414: "IllegalStateException: Vault locked" from
 * FaceRepository.crop, called by VaultGrid's CoverFaces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FaceCropLockTest {
    @Test fun aFaceAskedForAfterTheLockIsNothingToShow() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        val faces = FaceRepository(app, session, VaultRepository(app, session, BlobStore(app), SettingsStore(app)))
        session.lock()
        assertNull(faces.crop(1L))
    }

    @Test fun aFaceAskedForWhileTheDatabaseClosesNeverCrashes() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        val faces = FaceRepository(app, session, VaultRepository(app, session, BlobStore(app), SettingsStore(app)))
        db.close()
        // Room may report the closed database as a cancellation, which ends the screen's
        // produceState quietly; anything else would reach the main thread and crash the app.
        val face = try { faces.crop(1L) } catch (e: kotlinx.coroutines.CancellationException) { null }
        assertNull(face)
    }
}

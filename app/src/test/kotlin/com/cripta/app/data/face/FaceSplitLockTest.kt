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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "Dividi" in the viewer reads the moments of a face on its own (a produceState): when the vault
 * locks first, there is nothing to split, not a crash ("IllegalStateException: Vault locked"), as
 * for the faces on the covers ([FaceCropLockTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FaceSplitLockTest {
    private fun open(): Triple<CriptaDatabase, SessionManager, FaceRepository> {
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        return Triple(db, session, FaceRepository(app, session, VaultRepository(app, session, BlobStore(app), SettingsStore(app))))
    }

    @Test fun aFaceToSplitAskedForAfterTheLockHasNoMoments() = runBlocking {
        val (_, session, faces) = open()
        session.lock()
        assertEquals(emptyList<Long>(), faces.splittable(1L))
    }

    @Test fun aFaceToSplitAskedForWhileTheDatabaseClosesNeverCrashes() = runBlocking {
        val (db, _, faces) = open()
        db.close()
        // Room may report the closed database as a cancellation, which ends the produceState
        // quietly; anything else would reach the main thread and crash the app.
        val moments = try { faces.splittable(1L) } catch (e: kotlinx.coroutines.CancellationException) { emptyList() }
        assertEquals(emptyList<Long>(), moments)
    }
}

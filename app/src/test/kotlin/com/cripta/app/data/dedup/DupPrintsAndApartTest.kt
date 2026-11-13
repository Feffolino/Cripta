package com.cripta.app.data.dedup

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.DupApartEntity
import com.cripta.app.data.db.DupPrintEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "N di M analizzati" and "Non sono simili" with its "Annulla". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DupPrintsAndApartTest {
    private lateinit var db: CriptaDatabase
    private lateinit var scanner: DuplicateScanner

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        scanner = DuplicateScanner(VaultRepository(app, session, BlobStore(app), SettingsStore(app)), session)
    }

    @After
    fun close() = db.close()

    private suspend fun media(id: String, mime: String) = db.fileDao().insert(FileEntity(
        id = id, originalName = id, mimeType = mime, sizeBytes = 10, createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0),
    ))

    @Test
    fun aFingerprintMadeTheOldWayIsNotCountedAsDone() = runBlocking {
        media("photo", "image/jpeg")
        media("old-video", "video/mp4")
        media("new-video", "video/mp4")
        db.dupPrintDao().put(DupPrintEntity("photo", 10, 1, ByteArray(8)))
        db.dupPrintDao().put(DupPrintEntity("old-video", 10, 1, ByteArray(8)))   // remade by the next scan
        db.dupPrintDao().put(DupPrintEntity("new-video", 10, 2, ByteArray(8)))
        assertEquals(2 to 3, scanner.printStats())
    }

    @Test
    fun undoingNotSimilarTakesBackOnlyThePairsItAdded() = runBlocking {
        listOf("a", "b", "c").forEach { media(it, "image/jpeg") }
        assertEquals(listOf(DupApartEntity("a", "b")), scanner.keepApart(listOf("a", "b")))
        val added = scanner.keepApart(listOf("c", "b", "a"))
        assertEquals(setOf(DupApartEntity("a", "c"), DupApartEntity("b", "c")), added.toSet())
        scanner.bringTogether(added)
        assertEquals(listOf(DupApartEntity("a", "b")), db.dupPrintDao().apartPairs())
    }
}

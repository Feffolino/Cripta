package com.cripta.app.data.face

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FaceApartEntity
import com.cripta.app.data.db.FaceTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FaceApartTest {
    private fun pair(a: Long, b: Long) = FaceApartEntity(minOf(a, b), maxOf(a, b))

    @Test
    fun pairsFollowTheFacesFoundAgain() {
        // 1 and 2 were analysed again as 11 and 12; 3 belongs to another media, untouched.
        val out = FaceApart.remap(listOf(pair(1, 2), pair(1, 3)), mapOf(1L to 11L, 2L to 12L))
        assertEquals(setOf(pair(11, 12), pair(3, 11)), out.toSet())
    }

    @Test
    fun aSideNotFoundAgainDropsThePair() {
        assertEquals(emptyList<FaceApartEntity>(), FaceApart.remap(listOf(pair(1, 3)), mapOf(1L to null)))
    }

    @Test
    fun facesKeptByAnIncompleteReadingKeepTheirPairs() {
        // A stalled reading found 1 again as 11 and did not reach 2: 2 stays, and so does its pair.
        val out = FaceApart.remap(listOf(pair(1, 2), pair(2, 3)), mapOf(1L to 11L, 2L to 2L))
        assertEquals(setOf(pair(2, 11), pair(2, 3)), out.toSet())
    }

    @Test
    fun twoSidesBecomingOneFaceDropThePair() {
        val out = FaceApart.remap(listOf(pair(1, 2), pair(1, 5), pair(2, 5)), mapOf(1L to 9L, 2L to 9L))
        // (1,2) is now one face; (1,5) and (2,5) both become (5,9), kept once.
        assertEquals(listOf(pair(5, 9)), out)
    }

    @Test
    fun ricominciaKeepsSplitFacesAndTheirPairs() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CriptaDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.faceDao()
        fun track(file: String) = FaceTrackEntity(fileId = file, frames = 1, timeMs = 0, faceCount = 1, width = 10,
            quality = 1f, embeddings = ByteArray(0), crop = ByteArray(0))
        val a = dao.addTrack(track("f1"))
        val b = dao.addTrack(track("f2"))
        dao.addTrack(track("f3"))
        dao.addApart(listOf(pair(a, b)))
        dao.deleteUnconfirmedTracks()
        // The split faces stay for the new analysis to carry their pair; the others go.
        assertEquals(setOf(a, b), dao.trackRowsOf(listOf("f1", "f2", "f3")).map { it.id }.toSet())
        assertEquals(0, dao.purgeApartOrphans())
        dao.deleteUnconfirmedTracksOf("f1").also { assertEquals(1, dao.trackRowsOf(listOf("f1")).size) }
        // A face gone for good takes its pairs with it.
        dao.deleteScannedTracksOf("f1")
        assertEquals(1, dao.purgeApartOrphans())
        assertEquals(emptyList<FaceApartEntity>(), dao.apart())
        db.close()
    }
}

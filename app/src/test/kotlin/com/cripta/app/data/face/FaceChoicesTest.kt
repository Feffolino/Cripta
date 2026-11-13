package com.cripta.app.data.face

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FaceApartEntity
import com.cripta.app.data.db.FaceScanEntity
import com.cripta.app.data.db.FaceTrackEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.media.face.EmbeddingCodec
import com.cripta.app.media.face.FaceCodec
import com.cripta.app.media.face.SeenFace
import com.cripta.app.media.face.SeenFrame
import com.cripta.app.media.face.StoredFace
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.sqrt

/** The user's answers about faces hold against the closer look, the tidying and later assignments. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FaceChoicesTest {
    private lateinit var db: CriptaDatabase
    private lateinit var faces: FaceRepository
    private val dao get() = db.faceDao()

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        com.cripta.app.Strings.init(app)
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        val repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
        faces = FaceRepository(app, session, repo)
    }

    @After
    fun close() = db.close()

    private val look = FloatArray(128) { 1f / sqrt(128f) }
    private val box = floatArrayOf(0.2f, 0.2f, 0.2f, 0.2f)

    private suspend fun video(version: Int = FaceRepository.VERSION) {
        db.fileDao().insert(FileEntity(id = "v", originalName = "v.mp4", mimeType = "video/mp4", sizeBytes = 1, createdAt = 0,
            importedAt = 0, wrappedKeyset = ByteArray(0), durationMs = 60_000))
        dao.putScan(FaceScanEntity("v", "ok", 10, 0, 2, "", version, 1))
    }

    /** A face of the analysis in the video at [t], alone on its sampled frame [frame] (bit mask). */
    private suspend fun videoFace(frame: Long, t: Long, personId: Long? = null) = dao.addTrack(FaceTrackEntity(
        fileId = "v", frames = frame, timeMs = t, faceCount = 1, width = 100, quality = 1f,
        embeddings = EmbeddingCodec.encode(listOf(look)), crop = ByteArray(0), personId = personId,
        state = if (personId != null) "manual" else "auto", times = "$t",
        sightings = FaceCodec.encode(listOf(StoredFace(frame.countTrailingZeroBits(), t, 1f, 100, box, look)))))

    /** The closer reading of the video: the same face, at its place, at every moment asked. */
    private suspend fun lookCloser() = faces.refine(db.fileDao().byId("v")!!, true) { free, anchors ->
        (free.flatMap { f -> f.faces.map { it.timeMs } } + anchors.flatMap { a -> FaceCodec.decode(a.sightings).map { it.timeMs } })
            .distinct().map { t -> SeenFrame(t, t, listOf(SeenFace(box, look, true))) }
    }

    private suspend fun ids() = dao.tracksOfFile("v").map { it.id }.toSet()

    // ---- "Dividi": two faces kept apart are never joined again by the app itself ----

    @Test
    fun theCloserLookNeverJoinsAFaceSplitFromAConfirmedOne() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val c = videoFace(1, 1_000, p)
        val b = videoFace(2, 9_000)
        faces.keepFacesApart(listOf(c), listOf(b))
        lookCloser()
        assertEquals(setOf(c, b), ids())
        assertEquals(listOf(FaceApartEntity(minOf(b, c), maxOf(b, c))), dao.apart())
    }

    @Test
    fun theCloserLookStillJoinsAFaceNotKeptApart() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val c = videoFace(1, 1_000, p)
        videoFace(2, 9_000)
        assertEquals(1, lookCloser()!!.joined)
        assertEquals(setOf(c), ids())
    }

    @Test
    fun tidyingOldMediaNeverJoinsFacesKeptApart() = runBlocking {
        video(version = 3)
        val a = videoFace(1, 1_000)
        val b = videoFace(2, 9_000)
        faces.keepFacesApart(listOf(a), listOf(b))
        faces.tidy("v")
        assertEquals(setOf(a, b), ids())
    }

    // ---- "Non è lui/lei" taken back by giving the face to that person ----

    @Test
    fun givingAFaceToThePersonItWasRefusedForForgetsTheNo() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val a = videoFace(1, 1_000, p)
        faces.reject(a, p)
        assertTrue(dao.rejectionsOfTracks(listOf(a)).any { it.personId == p })
        val undo = faces.assign(a, p)
        assertFalse(dao.rejectionsOfTracks(listOf(a)).any { it.personId == p })
        undo()
        assertTrue(dao.rejectionsOfTracks(listOf(a)).any { it.personId == p })
    }

    @Test
    fun givingFacesAtOnceToThePersonTheyWereRefusedForForgetsTheNo() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val a = videoFace(1, 1_000, p)
        faces.reject(a, p)
        faces.assignAll(listOf(a), p)
        assertFalse(dao.rejectionsOfTracks(listOf(a)).any { it.personId == p })
    }

    // ---- "Annulla" of two assignments of one face ----

    @Test
    fun theFirstUndoOfAFaceGivenTwiceLeavesTheSecondUndoWorking() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val c = videoFace(1, 1_000, p)
        val a = videoFace(2, 5_000)
        val first = faces.assign(a, p)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(5))
        val second = faces.assign(a, p)
        first()
        // The second "Annulla" is still on offer: the face is not joined into another meanwhile.
        lookCloser()
        assertEquals(setOf(c, a), ids())
        second()
        assertEquals(p, dao.tracks(listOf(a)).single().personId)
    }

    // ---- "Nascondi" and "Elimina la persona" taken back ----

    @Test
    fun hidingSeveralPeopleIsTakenBackAtOnce() = runBlocking {
        val a = faces.newPerson("A")
        val b = faces.newPerson("B")
        val undo = faces.ignore(listOf(a, b))
        assertTrue(dao.persons().filter { it.id in setOf(a, b) }.all { it.ignored })
        undo()
        assertFalse(dao.persons().filter { it.id in setOf(a, b) }.any { it.ignored })
    }

    @Test
    fun deletingAPersonIsTakenBackWithTheirProfile() = runBlocking {
        val p = faces.newPerson("P")
        faces.setProfile(p, "private notes", listOf("https://p.example"))
        faces.setFavorite(p, true)
        val undo = faces.deleteEmpty(p)
        assertTrue(dao.persons().none { it.id == p })
        undo()
        val back = dao.persons().single { it.id == p }
        assertEquals("P", back.name)
        assertEquals("private notes", back.notes)
        assertEquals("https://p.example", back.links)
        assertTrue(back.favorite)
    }

    // ---- A person with only a profile ----

    @Test
    fun aNamelessPersonWithNotesAndNoMediaIsStillListed() = runBlocking {
        val p = faces.newPerson("")
        dao.rename(p, "")
        faces.setProfile(p, "notes", emptyList())
        dao.deleteEmptyPersons()
        assertNotNull(dao.persons().firstOrNull { it.id == p })
        assertTrue(faces.people().first().people.any { it.id == p })
    }
}

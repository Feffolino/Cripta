package com.cripta.app.data.face

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FaceApartEntity
import com.cripta.app.data.db.FaceRejectionEntity
import com.cripta.app.data.db.FaceScanEntity
import com.cripta.app.data.db.FaceTrackEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.media.face.EmbeddingCodec
import com.cripta.app.media.face.FaceCodec
import com.cripta.app.media.face.StoredFace
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.sqrt

/** The user's corrections on people and faces outlive the operations that follow them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PersonEditsTest {
    private lateinit var db: CriptaDatabase
    private lateinit var faces: FaceRepository
    private val dao get() = db.faceDao()

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        val repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
        faces = FaceRepository(app, session, repo)
    }

    @After
    fun close() = db.close()

    private suspend fun person(id: Long) = dao.persons().firstOrNull { it.id == id }

    private suspend fun file(id: String) = db.fileDao().insert(FileEntity(
        id = id, originalName = "$id.jpg", mimeType = "image/jpeg", sizeBytes = 1, createdAt = 0, importedAt = 0,
        wrappedKeyset = ByteArray(0),
    ))

    /** One look for every face: all alike, so they would all share a group. */
    private val look = FloatArray(128) { 1f / sqrt(128f) }

    private suspend fun track(fileId: String, quality: Float = 1f, personId: Long? = null, moments: List<Long> = emptyList()): Long =
        dao.addTrack(FaceTrackEntity(
            fileId = fileId, frames = 1, timeMs = moments.firstOrNull() ?: 0, faceCount = maxOf(1, moments.size), width = 100,
            quality = quality, embeddings = EmbeddingCodec.encode(listOf(look)), crop = ByteArray(0), personId = personId,
            state = if (personId != null) "manual" else "auto", times = moments.joinToString(","),
            sightings = FaceCodec.encode(moments.mapIndexed { i, t -> StoredFace(i, t, quality, 100, FloatArray(4), look) }),
        ))

    private suspend fun groupOf(trackId: Long) = faces.unsortedGroups().indexOfFirst { g -> g.cards.any { it.id == trackId } }

    // ---- "Annulla" on a merge of people ----

    @Test
    fun undoingAMergeKeepsNotesAndLinksSavedAfterIt() = runBlocking {
        val a = faces.newPerson("A")
        val b = faces.newPerson("B")
        faces.setProfile(a, "about A", listOf("https://a.example"))
        faces.setProfile(b, "about B", listOf("https://b.example"))
        val undo = faces.merge(a, listOf(b))
        val merged = person(a)!!
        faces.setProfile(a, merged.notes + "\n\nwritten after", merged.links.lines() + "https://after.example")
        undo()
        val kept = person(a)!!
        assertTrue(kept.notes, kept.notes.contains("written after"))
        assertTrue(kept.links, kept.links.contains("https://after.example"))
        assertEquals("about B", person(b)!!.notes)
    }

    @Test
    fun undoingAMergeKeepsAStarGivenAfterIt() = runBlocking {
        val a = faces.newPerson("A")
        val b = faces.newPerson("B")
        val undo = faces.merge(a, listOf(b))
        faces.setFavorite(a, true)
        undo()
        assertTrue(person(a)!!.favorite)
    }

    @Test
    fun undoingAMergeWithNothingChangedPutsBothProfilesBack() = runBlocking {
        val a = faces.newPerson("A")
        val b = faces.newPerson("B")
        faces.setProfile(a, "about A", listOf("https://a.example"))
        faces.setProfile(b, "about B", emptyList())
        faces.setFavorite(b, true)
        val undo = faces.merge(a, listOf(b))
        assertTrue(person(a)!!.favorite)
        undo()
        assertEquals("about A", person(a)!!.notes)
        assertEquals("https://a.example", person(a)!!.links)
        assertFalse(person(a)!!.favorite)
        assertEquals("about B", person(b)!!.notes)
    }

    @Test
    fun undoingAMergePutsTheMergedPersonsNoAnswersBack() = runBlocking {
        file("f1"); file("f2")
        val a = faces.newPerson("A")
        val b = faces.newPerson("B")
        val x = track("f1")   // a free face the user said is not B
        val y = track("f2")   // and one they said is not A, before the merge
        dao.addRejection(FaceRejectionEntity(x, b, 0.5f))
        dao.addRejection(FaceRejectionEntity(y, a, 0.5f))
        val undo = faces.merge(a, listOf(b))
        undo()
        val rejections = dao.rejections().map { it.trackId to it.personId }.toSet()
        assertTrue((x to b) in rejections)
        assertFalse((x to a) in rejections)
        assertTrue((y to a) in rejections)
    }

    // ---- Faces kept apart ----

    @Test
    fun joiningFacesKeepsWhatTheyWereKeptApartFrom() = runBlocking {
        file("f1"); file("f2")
        val a = track("f1", quality = 0.5f)
        val c = track("f1", quality = 0.9f)   // better: it stays, a goes into it
        val b = track("f2")
        faces.keepFacesApart(listOf(a), listOf(b))
        assertTrue(groupOf(a) != groupOf(b))
        val undo = faces.mergeFaces(listOf(a, c))
        assertNotNull(undo)
        assertNull(dao.tracks(listOf(a)).firstOrNull())
        assertEquals(listOf(FaceApartEntity(minOf(b, c), maxOf(b, c))), dao.apart())
        assertTrue(groupOf(c) != groupOf(b))
        undo!!()
        assertEquals(listOf(FaceApartEntity(minOf(a, b), maxOf(a, b))), dao.apart())
    }

    @Test
    fun splittingAFaceKeepsItsPartsApart() = runBlocking {
        file("f1")
        val t = track("f1", moments = listOf(1_000, 9_000))
        val undo = faces.splitFace(t, setOf(9_000))
        assertNotNull(undo)
        val parts = dao.trackRowsOf(listOf("f1")).map { it.id }
        assertEquals(2, parts.size)
        assertTrue(groupOf(parts[0]) != groupOf(parts[1]))
        undo!!()
        assertEquals(1, dao.trackRowsOf(listOf("f1")).size)
        assertEquals(emptyList<FaceApartEntity>(), dao.apart())
    }

    // ---- "Elimina persona" ----

    @Test
    fun deletingAPersonDeletesTheirProfile() = runBlocking {
        val p = faces.newPerson("P")
        faces.setProfile(p, "private notes", listOf("https://p.example"))
        faces.setFavorite(p, true)
        faces.deleteEmpty(p)
        assertNull(person(p))
    }

    // ---- "Annulla" on faces given to a person ----

    @Test
    fun aCloserLookRightAfterAnAssignmentLeavesItsUndoWorking() = runBlocking {
        db.fileDao().insert(FileEntity(id = "v", originalName = "v.mp4", mimeType = "video/mp4", sizeBytes = 1, createdAt = 0,
            importedAt = 0, wrappedKeyset = ByteArray(0), durationMs = 60_000))
        dao.putScan(FaceScanEntity("v", "ok", 10, 0, 2, "", 1, 1))
        val p = faces.newPerson("P")
        // A: confirmed before, in moments of its own; B: the same person elsewhere in the video.
        val a = dao.addTrack(FaceTrackEntity(fileId = "v", frames = 1, timeMs = 1_000, faceCount = 2, width = 100, quality = 1f,
            embeddings = EmbeddingCodec.encode(listOf(look)), crop = ByteArray(0), personId = p, state = "manual", times = "1000",
            sightings = FaceCodec.encode(listOf(StoredFace(0, 1_000, 1f, 100, FloatArray(4), look)))))
        val b = dao.addTrack(FaceTrackEntity(fileId = "v", frames = 2, timeMs = 9_000, faceCount = 1, width = 100, quality = 1f,
            embeddings = EmbeddingCodec.encode(listOf(look)), crop = ByteArray(0), times = "9000",
            sightings = FaceCodec.encode(listOf(StoredFace(1, 9_000, 1f, 100, FloatArray(4), look)))))
        val undo = faces.assignAll(listOf(b), p)
        // The closer look the assignment starts runs before the user taps "Annulla".
        faces.refine(db.fileDao().byId("v")!!, force = true)
        undo()
        val back = dao.tracks(listOf(b)).single()
        assertNull(back.personId)
        assertEquals(p, dao.tracks(listOf(a)).single().personId)
    }
}

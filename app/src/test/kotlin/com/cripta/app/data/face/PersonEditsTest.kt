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
import com.cripta.app.media.face.FaceStats
import com.cripta.app.media.face.SeenFace
import com.cripta.app.media.face.SeenFrame
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

    @Test
    fun undoingAMergePutsBackANoSaidToBothPeople() = runBlocking {
        file("f1")
        val a = faces.newPerson("A")
        val b = faces.newPerson("B")
        val x = track("f1")   // the user said it is neither A nor B
        dao.addRejection(FaceRejectionEntity(x, a, 0.5f))
        dao.addRejection(FaceRejectionEntity(x, b, 0.5f))
        val undo = faces.merge(a, listOf(b))
        assertEquals(listOf(x to a), dao.rejections().map { it.trackId to it.personId })
        undo()
        assertEquals(setOf(x to a, x to b), dao.rejections().map { it.trackId to it.personId }.toSet())
    }

    @Test
    fun undoingAMergeOfTwoPeopleAtOnceBringsBothBack() = runBlocking {
        file("f1"); file("f2")
        val a = faces.newPerson("A")
        val b = faces.newPerson("B")
        val c = faces.newPerson("C")
        faces.setProfile(b, "about B", emptyList())
        faces.setProfile(c, "about C", emptyList())
        val tb = track("f1", personId = b)
        val tc = track("f2", personId = c)
        val undo = faces.merge(a, listOf(b, c))
        assertEquals(listOf(a, a), dao.tracks(listOf(tb, tc)).map { it.personId })
        undo()
        assertEquals("about B", person(b)!!.notes)
        assertEquals("about C", person(c)!!.notes)
        val back = dao.tracks(listOf(tb, tc)).associate { it.id to it.personId }
        assertEquals(b, back[tb])
        assertEquals(c, back[tc])
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

    // ---- "Perché" on the faces of a media ----

    @Test
    fun aPersonWithAPercentSignInTheNameIsNamedInTheExplanation() = runBlocking {
        com.cripta.app.Strings.init(RuntimeEnvironment.getApplication())
        file("x")
        dao.putScan(FaceScanEntity("x", "ok", 10, 0, 2, "", 1, 1))
        for (name in listOf("Mario 100%", "50%d %s", "Anna")) {
            val p = faces.newPerson(name)
            dao.place(track("x"), p, "suggested", 0.8f)
        }
        val answer = faces.describe("x")
        for (name in listOf("Mario 100%", "50%d %s", "Anna")) assertTrue("$name missing from: $answer", answer.contains(name))
        assertTrue(answer, answer.contains("0.80"))
    }

    private val box = floatArrayOf(0.2f, 0.2f, 0.2f, 0.2f)

    private suspend fun video() {
        db.fileDao().insert(FileEntity(id = "v", originalName = "v.mp4", mimeType = "video/mp4", sizeBytes = 1, createdAt = 0,
            importedAt = 0, wrappedKeyset = ByteArray(0), durationMs = 60_000))
        dao.putScan(FaceScanEntity("v", "ok", 10, 0, 2, "", 1, 1))
    }

    /** A face of the analysis in the video at [t], alone on its sampled frame [frame] (bit mask). */
    private suspend fun videoFace(frame: Long, t: Long, personId: Long? = null) = dao.addTrack(FaceTrackEntity(
        fileId = "v", frames = frame, timeMs = t, faceCount = 1, width = 100, quality = 1f,
        embeddings = EmbeddingCodec.encode(listOf(look)), crop = ByteArray(0), personId = personId,
        state = if (personId != null) "manual" else "auto", times = "$t",
        sightings = FaceCodec.encode(listOf(StoredFace(frame.countTrailingZeroBits(), t, 1f, 100, box, look)))))

    /** The closer reading of the video: the same face, at its place, at every moment asked. */
    private suspend fun lookCloser() = faces.refine(db.fileDao().byId("v")!!, false) { free, anchors ->
        (free.flatMap { f -> f.faces.map { it.timeMs } } + anchors.flatMap { a -> FaceCodec.decode(a.sightings).map { it.timeMs } })
            .distinct().map { t -> SeenFrame(t, t, listOf(SeenFace(box, look, true))) }
    }

    @Test
    fun aCloserLookRightAfterAnAssignmentJoinsTheOtherFacesOfThatPerson() = runBlocking {
        video()
        val p = faces.newPerson("P")
        // Nothing else is confirmed for P in this video: the face just given is the only one to join into.
        val a = videoFace(1, 1_000)
        val b = videoFace(2, 9_000)
        faces.assign(a, p)
        val r = lookCloser()
        assertEquals(1, r!!.joined)
        assertEquals(listOf(a), dao.tracksOfFile("v").map { it.id })
        assertEquals(p, dao.tracks(listOf(a)).single().personId)
        assertNull(dao.tracks(listOf(b)).singleOrNull())
        assertNotNull(FaceStats.refineOf(dao.scanOf("v")!!.stats))
    }

    @Test
    fun undoingAnAssignmentAfterACloserLookTakesBackTheFacesJoinedIntoIt() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val a = videoFace(1, 1_000)
        val b = videoFace(2, 9_000)
        dao.addRejection(FaceRejectionEntity(b, faces.newPerson("Q"), 0.5f))
        val undo = faces.assign(a, p)
        assertEquals(1, lookCloser()!!.joined)
        undo()
        val rows = dao.tracksOfFile("v").associateBy { it.id }
        assertEquals(setOf(a, b), rows.keys)
        assertNull(rows.getValue(a).personId)
        assertEquals(1L, rows.getValue(a).frames)
        assertEquals(1, FaceCodec.decode(rows.getValue(a).sightings).size)
        assertEquals(2L, rows.getValue(b).frames)
        assertEquals(listOf(9_000L), FaceCodec.decode(rows.getValue(b).sightings).map { it.timeMs })
        assertEquals(listOf(b), dao.rejectionsOfTracks(listOf(a, b)).map { it.trackId })
    }

    @Test
    fun aFaceLeftApartForItsUndoSaysUntilWhenAndIsJoinedAfter() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val c = videoFace(1, 1_000, p)
        val a = videoFace(2, 5_000)
        faces.assign(a, p)
        val first = lookCloser()!!
        // Left apart while its "Annulla" is on offer: the closer look says until when.
        val until = first.heldUntil!!
        assertTrue(until > android.os.SystemClock.elapsedRealtime())
        assertEquals(setOf(c, a), dao.tracksOfFile("v").map { it.id }.toSet())
        // Held back, the look is not recorded as done: the next one is not skipped as already made.
        assertNull(FaceStats.refineOf(dao.scanOf("v")!!.stats))
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(until - android.os.SystemClock.elapsedRealtime() + 1))
        val again = lookCloser()!!
        assertNull(again.heldUntil)
        assertEquals(listOf(c), dao.tracksOfFile("v").map { it.id })
        assertNotNull(FaceStats.refineOf(dao.scanOf("v")!!.stats))
    }

    @Test
    fun aCloserLookAfterTheUndoNoLongerHoldsTheFaceBack() = runBlocking {
        video()
        val p = faces.newPerson("P")
        val c = videoFace(1, 1_000, p)
        val a = videoFace(2, 5_000)
        val undo = faces.assign(a, p)
        assertNotNull(lookCloser()!!.heldUntil)
        assertNull(FaceStats.refineOf(dao.scanOf("v")!!.stats))
        // "Annulla" releases the hold at once, long before it would run out.
        undo()
        val again = lookCloser()!!
        assertNull(again.heldUntil)
        // Free again and no longer held, it is joined like any other free face of P.
        assertEquals(1, again.joined)
        assertEquals(listOf(c), dao.tracksOfFile("v").map { it.id })
        assertNotNull(FaceStats.refineOf(dao.scanOf("v")!!.stats))
        assertEquals(p, dao.tracks(listOf(c)).single().personId)
    }

    @Test
    fun aCloserLookNeverJoinsAFaceWhoseAssignmentCanStillBeUndone() = runBlocking {
        video()
        val p = faces.newPerson("P")
        // C: confirmed before; A: just given to P, its "Annulla" still on offer; B: free, P again.
        val c = videoFace(1, 1_000, p)
        val a = videoFace(2, 5_000)
        val b = videoFace(4, 9_000)
        val undo = faces.assign(a, p)
        lookCloser()
        assertEquals(p, dao.tracks(listOf(a)).single().personId)
        assertNull(dao.tracks(listOf(b)).singleOrNull())
        undo()
        assertNull(dao.tracks(listOf(a)).single().personId)
        assertEquals(p, dao.tracks(listOf(c)).single().personId)
    }
}

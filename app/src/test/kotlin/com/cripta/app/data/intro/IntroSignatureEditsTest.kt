package com.cripta.app.data.intro

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.media.intro.AudioDecoder
import com.cripta.app.media.intro.Fingerprint
import com.cripta.app.media.intro.PrintCodec
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToLong

/**
 * Actions on a signature page loaded earlier ("Durata della sigla", "Non è una sigla") and on a
 * rejected sample never undo what the user did meanwhile, nor move a segment twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IntroSignatureEditsTest {
    private lateinit var db: CriptaDatabase
    private lateinit var intro: IntroRepository
    private val dao get() = db.introDao()
    private val kind = SegmentKind.INTRO

    /** The shared sound: [SOUND] hashes, after [BEFORE] hashes of each video's own audio. */
    private val sound = java.util.Random(7).let { r -> IntArray(SOUND) { r.nextInt() or 1 } }
    /** Another shared sound, at the same place: a second signature. */
    private val otherSound = java.util.Random(11).let { r -> IntArray(SOUND) { r.nextInt() or 1 } }
    private fun at(index: Int) = (index * Fingerprint.HOP_MS).roundToLong()
    private val soundStart get() = at(BEFORE)
    private val soundEnd get() = at(BEFORE + SOUND)

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        val repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
        intro = IntroRepository(session, repo, AudioDecoder(repo))
    }

    @After
    fun close() = db.close()

    /** A video whose head has the shared sound, detected (an automatic segment on it). */
    private suspend fun video(id: String, seed: Long, detected: Boolean = true, shared: IntArray = sound) {
        db.fileDao().insert(FileEntity(id = id, originalName = "$id.mp4", mimeType = "video/mp4", sizeBytes = 1,
            createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0), durationMs = 600_000))
        val own = java.util.Random(seed)
        val head = IntArray(BEFORE) { own.nextInt() or 1 } + shared + IntArray(AFTER) { own.nextInt() or 1 }
        dao.putPrint(AudioPrintEntity(id, kind.part, 0, PrintCodec.encode(head), "ok"))
        if (detected) dao.putSegment(MediaSegmentEntity(id, kind.id, soundStart, soundEnd, "auto"))
    }

    private suspend fun seg(id: String) = dao.segmentsOf(id).firstOrNull { it.kind == kind.id }

    private suspend fun page() = intro.signatures(kind).single()

    @Test
    fun aDurationChangeLeavesAMarkMadeSinceThePageLoaded() = runBlocking {
        video("a", 1); video("b", 2)
        val sig = page()
        intro.setManual("a", kind, soundStart + 2_000, soundEnd - 1_000)
        intro.setSignatureDuration(sig, -1_000, 1_000)
        val a = seg("a")!!
        assertEquals("manual", a.source)
        assertEquals(soundStart + 2_000, a.startMs)
        assertEquals(soundStart - 1_000, seg("b")!!.startMs)
    }

    @Test
    fun aDurationChangeLeavesASegmentRemovedSinceThePageLoaded() = runBlocking {
        video("a", 1); video("b", 2)
        val sig = page()
        intro.setNone("a", kind)
        intro.setSignatureDuration(sig, -1_000, 1_000)
        assertEquals("none", seg("a")!!.source)
    }

    @Test
    fun notASignatureLeavesAMarkMadeSinceThePageLoaded() = runBlocking {
        video("a", 1); video("b", 2)
        val sig = page()
        intro.setManual("a", kind, soundStart + 2_000, soundEnd - 1_000)
        intro.deleteSignature(sig)
        assertEquals("manual", seg("a")!!.source)
    }

    @Test
    fun aDurationChangeReachesAVideoFoundSinceThePageLoaded() = runBlocking {
        video("a", 1); video("b", 2)
        val sig = page()
        video("c", 3)
        intro.setSignatureDuration(sig, -1_000, 1_000)
        assertEquals(soundStart - 1_000, seg("c")!!.startMs)
        assertEquals(soundStart - 1_000, seg("a")!!.startMs)
    }

    @Test
    fun aRejectedSampleTakenBackGivesTheSameSegmentAsBefore() = runBlocking {
        video("a", 1); video("b", 2)
        intro.setSignatureDuration(page(), -1_000, 1_000)
        val before = seg("a")!!
        assertEquals(soundStart - 1_000, before.startMs)
        val file = db.fileDao().byId("a")!!
        intro.rejectEverywhere(file, kind, before.startMs, before.endMs)
        val rejected = dao.rejectedOfKind(kind.id).single()
        intro.restoreRejected(rejected.id)
        val after = seg("a")
        assertNotNull(after)
        assertEquals(before.startMs, after!!.startMs)
        assertEquals(before.endMs, after.endMs)
    }

    @Test
    fun twoActionsOnASignatureNotYetReloadedSaveItOnce() = runBlocking {
        video("a", 1); video("b", 2)
        val sig = page()
        intro.renameSignature(sig, "Uno")
        // The page still shows the signature unsaved: a second action on it before the reload.
        intro.renameSignature(sig, "Due")
        intro.setSignatureDuration(sig, -1_000, 1_000)
        val saved = dao.signaturesOfKind(kind.id).single()
        assertEquals("Due", saved.name)
        assertEquals(-1_000L, saved.startDeltaMs)
    }

    @Test
    fun twoQuickNudgesBothCount() = runBlocking {
        video("a", 1)
        intro.nudge("a", kind, 1_000, 0, 600_000, 1_000)
        intro.nudge("a", kind, 0, 1_000, 600_000, 1_000)
        val a = seg("a")!!
        assertEquals("manual", a.source)
        assertEquals(soundStart + 1_000, a.startMs)
        assertEquals(soundEnd + 1_000, a.endMs)
        assertEquals(soundStart, a.autoStartMs)
    }

    @Test
    fun rejectedEverywhereReachesSegmentsExtendedPastTheirSound() = runBlocking {
        video("a", 1); video("b", 2); video("c", 3)
        // Extended by more than the sound itself on every video.
        intro.setSignatureDuration(page(), -soundStart, 10_000)
        val shown = seg("a")!!
        val n = intro.rejectEverywhere(db.fileDao().byId("a")!!, kind, shown.startMs, shown.endMs)
        assertEquals(2, n)
        assertNull(seg("b"))
        assertNull(seg("c"))
    }

    @Test
    fun searchingAnExtendedSegmentSearchesItsSound() = runBlocking {
        video("a", 1); video("b", 2)
        intro.setSignatureDuration(page(), -soundStart, 10_000)
        video("c", 3, detected = false)
        val shown = seg("a")!!
        assertEquals(1, intro.searchOthers("a", kind, shown.startMs, shown.endMs))
        assertNotNull(seg("c"))
    }

    // ---- the happy paths ----

    @Test
    fun notASignatureRemovesItsDetectedSegmentsAndRejectsEachSoundWhereItCameFrom() = runBlocking {
        video("a", 1); video("b", 2)
        intro.deleteSignature(page())
        assertNull(seg("a"))
        assertNull(seg("b"))
        val rejected = dao.rejectedOfKind(kind.id)
        assertEquals(setOf("a", "b"), rejected.map { it.originFileId }.toSet())
        for (r in rejected) {
            assertEquals(soundStart, r.startMs)
            assertEquals(soundEnd, r.endMs)
        }
        // Never detected again: a new video with that sound finds nothing.
        video("c", 3, detected = false)
        intro.matchNew(listOf("c"))
        assertNull(seg("c"))
    }

    @Test
    fun notASignatureOnASavedOneForgetsItAndKeepsItsName() = runBlocking {
        video("a", 1); video("b", 2)
        intro.renameSignature(page(), "Sigla")
        intro.deleteSignature(page())
        assertNull(seg("a"))
        assertTrue(dao.signaturesOfKind(kind.id).isEmpty())
        assertTrue(dao.signatureSoundsOfKind(kind.id).isEmpty())
        val rejected = dao.rejectedOfKind(kind.id)
        assertEquals(2, rejected.size)
        // A saved signature's sounds have no origin recorded: none is invented.
        assertTrue(rejected.all { it.originFileId.isEmpty() && it.originName == "Sigla" })
    }

    @Test
    fun mergingIntoAnExtendedSignatureGivesItsDurationToTheOtherVideos() = runBlocking {
        video("a", 1); video("b", 2)
        video("c", 3, shared = otherSound); video("d", 4, shared = otherSound)
        assertEquals(2, intro.signatures(kind).size)
        val first = intro.signatures(kind).first { s -> s.entries.any { it.first.id == "a" } }
        intro.setSignatureDuration(first, -1_000, 1_000)
        intro.mergeSignatures(intro.signatures(kind).sortedByDescending { it.id != null }, "Insieme")
        val saved = dao.signaturesOfKind(kind.id).single()
        assertEquals("Insieme", saved.name)
        assertEquals(-1_000L, saved.startDeltaMs)
        for (id in listOf("a", "b", "c", "d")) {
            assertEquals(id, soundStart - 1_000, seg(id)!!.startMs)
            assertEquals(id, soundEnd + 1_000, seg(id)!!.endMs)
        }
        assertEquals(4, page().entries.size)
    }

    @Test
    fun mergingIntoANaturalSignatureTakesBackTheOthersExtraSeconds() = runBlocking {
        video("a", 1); video("b", 2)
        video("c", 3, shared = otherSound); video("d", 4, shared = otherSound)
        val extended = intro.signatures(kind).first { s -> s.entries.any { it.first.id == "a" } }
        intro.setSignatureDuration(extended, -1_000, 1_000)
        intro.renameSignature(intro.signatures(kind).first { s -> s.entries.any { it.first.id == "c" } }, "Nome")
        // The named one wins, whatever the order.
        intro.mergeSignatures(intro.signatures(kind).sortedBy { it.name != null }, null)
        val saved = dao.signaturesOfKind(kind.id).single()
        assertEquals("Nome", saved.name)
        assertEquals(0L, saved.startDeltaMs)
        for (id in listOf("a", "b", "c", "d")) {
            assertEquals(id, soundStart, seg(id)!!.startMs)
            assertEquals(id, soundEnd, seg(id)!!.endMs)
        }
    }

    @Test
    fun applyToAllMovesTheOtherVideosAndGivesTheSavedSignatureThatDuration() = runBlocking {
        video("a", 1); video("b", 2); video("c", 3)
        intro.renameSignature(page(), "Sigla")
        intro.setManual("a", kind, soundStart - 1_000, soundEnd + 2_000)
        assertEquals(2, intro.applyToAll(db.fileDao().byId("a")!!, kind))
        for (id in listOf("b", "c")) {
            val s = seg(id)!!
            assertEquals("auto", s.source)
            assertEquals(soundStart - 1_000, s.startMs)
            assertEquals(soundEnd + 2_000, s.endMs)
            assertEquals(soundStart, s.autoStartMs)
        }
        val saved = dao.signaturesOfKind(kind.id).single()
        assertEquals(-1_000L, saved.startDeltaMs)
        assertEquals(2_000L, saved.endDeltaMs)
        assertEquals(1, dao.rulesOfKind(kind.id).size)
        // A video analysed later gets the same move.
        video("d", 4, detected = false)
        intro.matchNew(listOf("d"))
        val d = seg("d")!!
        assertEquals(d.autoStartMs!! - 1_000, d.startMs)
        assertEquals(d.autoEndMs!! + 2_000, d.endMs)
    }

    @Test
    fun renamingASavedSignatureKeepsItOneAndTrimsTheName() = runBlocking {
        video("a", 1); video("b", 2)
        intro.renameSignature(page(), "  Sigla  ")
        assertEquals("Sigla", page().name)
        val sounds = dao.signatureSoundsOfKind(kind.id).size
        intro.renameSignature(page(), "Altra")
        val saved = dao.signaturesOfKind(kind.id).single()
        assertEquals("Altra", saved.name)
        assertEquals(sounds, dao.signatureSoundsOfKind(kind.id).size)
    }

    @Test
    fun startingOverKeepsTheUsersChoicesUnlessEverythingGoes() = runBlocking {
        video("a", 1); video("b", 2); video("c", 3)
        intro.setManual("a", kind, soundStart + 1_000, soundEnd)
        intro.renameSignature(page(), "Sigla")
        intro.applyToAll(db.fileDao().byId("a")!!, kind)
        intro.rejectEverywhere(db.fileDao().byId("b")!!, kind, seg("b")!!.startMs, seg("b")!!.endMs)
        video("d", 4)
        intro.resetAll(everything = false)
        assertTrue(dao.printsOf("a").isEmpty())
        assertNull(seg("d"))
        assertEquals("manual", seg("a")!!.source)
        assertEquals(1, dao.signaturesOfKind(kind.id).size)
        assertTrue(dao.rejectedOfKind(kind.id).isNotEmpty())
        assertTrue(dao.rulesOfKind(kind.id).isNotEmpty())
        intro.resetAll(everything = true)
        assertNull(seg("a"))
        assertTrue(dao.signaturesOfKind(kind.id).isEmpty())
        assertTrue(dao.signatureSoundsOfKind(kind.id).isEmpty())
        assertTrue(dao.rejectedOfKind(kind.id).isEmpty())
        assertTrue(dao.rulesOfKind(kind.id).isEmpty())
    }

    private companion object {
        const val BEFORE = 160
        const val SOUND = 240
        const val AFTER = 160
    }
}

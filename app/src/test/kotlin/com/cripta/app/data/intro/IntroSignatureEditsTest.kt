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
    private suspend fun video(id: String, seed: Long) {
        db.fileDao().insert(FileEntity(id = id, originalName = "$id.mp4", mimeType = "video/mp4", sizeBytes = 1,
            createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0), durationMs = 600_000))
        val own = java.util.Random(seed)
        val head = IntArray(BEFORE) { own.nextInt() or 1 } + sound + IntArray(AFTER) { own.nextInt() or 1 }
        dao.putPrint(AudioPrintEntity(id, kind.part, 0, PrintCodec.encode(head), "ok"))
        dao.putSegment(MediaSegmentEntity(id, kind.id, soundStart, soundEnd, "auto"))
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

    private companion object {
        const val BEFORE = 160
        const val SOUND = 240
        const val AFTER = 160
    }
}

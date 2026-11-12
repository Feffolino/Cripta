package com.cripta.app.data.intro

import android.app.Application
import androidx.room.Room
import com.cripta.app.Strings
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FileEntity
import com.cripta.app.media.intro.AudioDecoder
import com.cripta.app.media.intro.PrintCodec
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "Analizza" explains why a video has no intro also when the video it shares sound with has a "%" in its name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IntroNameWithPercentTest {
    private lateinit var db: CriptaDatabase
    private lateinit var intro: IntroRepository
    private val kind = SegmentKind.INTRO

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        Strings.init(app)
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        val repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
        intro = IntroRepository(session, repo, AudioDecoder(repo))
    }

    @After
    fun close() = db.close()

    private fun hashes(seed: Long, n: Int) = java.util.Random(seed).let { r -> IntArray(n) { r.nextInt() or 1 } }

    private suspend fun video(id: String, name: String, head: IntArray) {
        db.fileDao().insert(FileEntity(id = id, originalName = name, mimeType = "video/mp4", sizeBytes = 1,
            createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0), durationMs = 600_000))
        db.introDao().putPrint(AudioPrintEntity(id, kind.part, 0, PrintCodec.encode(head), "ok"))
    }

    /** "a.mp4" and [other] share [shared] hashes after their own audio, followed by [after] hashes of their own. */
    private fun describe(other: String, shared: Int, after: Int): String = runBlocking {
        val sound = hashes(7, shared)
        video("a", "a.mp4", hashes(1, 160) + sound + hashes(11, after))
        video("b", other, hashes(2, 160) + sound + hashes(12, after))
        intro.describe(listOf("a", "b"))
    }

    /** [answer] names [other] and gives the reason it is expected to ([why], part of the English text). */
    private fun assertNamed(other: String, why: String, answer: String) {
        assertTrue("$other missing from: $answer", answer.contains("“$other”"))
        assertTrue("\"$why\" missing from: $answer", answer.contains(why))
    }

    @Test
    fun tooShortAStretchNamesTheOtherVideo() {
        for (name in listOf("100%.mp4", "Sconto 20% off.mp4", "50%d %s.mp4", "Normale.mp4")) {
            assertNamed(name, "only 1.3 s", describe(name, 20, 200))
            close(); open()
        }
    }

    @Test
    fun aStretchUpToTheEdgeNamesTheOtherVideo() {
        for (name in listOf("100%.mp4", "Sconto 20% off.mp4", "Normale.mp4")) {
            assertNamed(name, "up to the edge", describe(name, 240, 0))
            close(); open()
        }
    }

    @Test
    fun aStretchNotSavedNamesTheOtherVideo() {
        for (name in listOf("100%.mp4", "Sconto 20% off.mp4", "Normale.mp4")) {
            assertNamed(name, "but not saved", describe(name, 240, 200))
            close(); open()
        }
    }
}

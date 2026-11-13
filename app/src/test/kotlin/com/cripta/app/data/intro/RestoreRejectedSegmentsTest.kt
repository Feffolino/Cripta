package com.cripta.app.data.intro

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.BackupExtras
import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.db.RejectedPrintEntity
import com.cripta.app.media.intro.Fingerprint
import com.cripta.app.media.intro.PrintCodec
import com.cripta.app.media.intro.SegmentKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.roundToLong

/**
 * An automatic segment a restore puts back on a file is dropped again when its sound is rejected,
 * by the vault ("Non è un'intro · In tutti i video" after the backup) or by the archive itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestoreRejectedSegmentsTest {
    private val kind = SegmentKind.INTRO
    private val sound = java.util.Random(7).let { r -> IntArray(SOUND) { r.nextInt() or 1 } }
    private fun at(index: Int) = (index * Fingerprint.HOP_MS).roundToLong()

    private fun db() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CriptaDatabase::class.java)
        .allowMainThreadQueries().build()

    /** A video already in the vault, with the sound in its head and the automatic segment the restore put back. */
    private suspend fun knownVideo(db: CriptaDatabase, id: String) {
        db.fileDao().insert(FileEntity(id = id, originalName = "$id.mp4", mimeType = "video/mp4", sizeBytes = 1,
            createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0), durationMs = 600_000))
        val own = java.util.Random(1)
        val head = IntArray(BEFORE) { own.nextInt() or 1 } + sound + IntArray(AFTER) { own.nextInt() or 1 }
        db.introDao().putPrint(AudioPrintEntity(id, kind.part, 0, PrintCodec.encode(head), "ok"))
        db.introDao().putSegment(MediaSegmentEntity(id, kind.id, at(BEFORE), at(BEFORE + SOUND), "auto"))
    }

    private fun rejectedSound() = RejectedPrintEntity(kind = kind.id, originFileId = "x", originName = "x.mp4",
        startMs = 0, endMs = at(SOUND), hashes = PrintCodec.encode(sound), createdAt = 1L)

    private suspend fun restore(from: CriptaDatabase, to: CriptaDatabase, known: Array<String?>) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { BackupExtras.write(from, emptyMap(), it) }
        DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { BackupExtras.read(to, arrayOf(), it, known = known) }
    }

    @Test
    fun aSoundTheVaultRejectsDoesNotComeBack() = runBlocking {
        val from = db(); val to = db()
        knownVideo(to, "a")
        to.introDao().addRejected(rejectedSound())
        restore(from, to, arrayOf("a"))
        assertNull(to.introDao().segmentsOf("a").firstOrNull { it.kind == kind.id })
        from.close(); to.close()
    }

    @Test
    fun aSoundTheArchiveRejectsDoesNotStayEither() = runBlocking {
        val from = db(); val to = db()
        from.introDao().addRejected(rejectedSound())
        knownVideo(to, "a")
        restore(from, to, arrayOf("a"))
        assertNull(to.introDao().segmentsOf("a").firstOrNull { it.kind == kind.id })
        from.close(); to.close()
    }

    @Test
    fun anotherSoundStays() = runBlocking {
        val from = db(); val to = db()
        knownVideo(to, "a")
        val other = java.util.Random(99).let { r -> IntArray(SOUND) { r.nextInt() or 1 } }
        to.introDao().addRejected(rejectedSound().copy(hashes = PrintCodec.encode(other)))
        restore(from, to, arrayOf("a"))
        assertEquals(1, to.introDao().segmentsOf("a").count { it.kind == kind.id })
        from.close(); to.close()
    }

    private companion object {
        const val BEFORE = 160
        const val SOUND = 240
        const val AFTER = 160
    }
}

package com.cripta.app.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.cripta.app.Strings
import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.DupPrintEntity
import com.cripta.app.data.db.FaceScanEntity
import com.cripta.app.data.db.FaceTrackEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileTagCrossRef
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.db.PersonEntity
import com.cripta.app.data.db.RejectedPrintEntity
import com.cripta.app.data.db.SegmentRuleEntity
import com.cripta.app.data.db.SignatureEntity
import com.cripta.app.data.db.SignatureSoundEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.security.SessionManager
import com.cripta.crypto.DekManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** A backup restored into the vault it came from adds nothing that is already there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestoreKnownFilesTest {
    private lateinit var app: Application
    private lateinit var db: CriptaDatabase
    private lateinit var repo: VaultRepository

    @Before
    fun open() {
        app = RuntimeEnvironment.getApplication()
        Strings.init(app)
        db = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val session = SessionManager()
        session.activate(DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes()), db)
        repo = VaultRepository(app, session, BlobStore(app), SettingsStore(app))
    }

    @After
    fun close() = db.close()

    private suspend fun roundTrip(): RestoreReport {
        val file = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(file), "a passphrase".toCharArray())
        return repo.importBackup(Uri.fromFile(file), "a passphrase".toCharArray())
    }

    private suspend fun liveNotes() = db.fileDao().everyFile().filter { it.deletedAt == null }

    @Test
    fun aNoteRestoredIntoItsVaultIsNotAddedAgain() = runBlocking {
        val note = repo.createNote(null, "alpha", "alpha")
        val hash = db.fileDao().byId(note.id)!!.contentHash
        assertNotNull(hash)
        val report = roundTrip()
        assertEquals(1, liveNotes().size)
        assertEquals(1, report.duplicates)
        // The note here keeps the hash it was found by.
        assertEquals(hash, liveNotes().single().contentHash)
    }

    @Test
    fun aNoteWithoutARecordedHashIsFoundToo() = runBlocking {
        // Notes written by earlier versions have no hash of their text.
        repo.createNote(null, "alpha", "alpha")
        db.openHelper.writableDatabase.execSQL("UPDATE files SET contentHash = NULL")
        val report = roundTrip()
        assertEquals(1, liveNotes().size)
        assertEquals(1, report.duplicates)
        // Computed to find it, the hash is kept: the next restore does not read the note again.
        assertNotNull(liveNotes().single().contentHash)
    }

    private var made = 0

    /** A video of its own content (each one another). */
    private suspend fun video(): FileEntity {
        val seed = ++made
        val src = File.createTempFile("clip", ".mp4", app.cacheDir).apply { writeBytes(ByteArray(4096) { (it * 7 + seed).toByte() }) }
        return repo.import(Uri.fromFile(src), null)
    }

    private fun sql(q: String) = db.openHelper.writableDatabase.execSQL(q)

    @Test
    fun aFileAlreadyHereGetsTheIntroItLacksAndKeepsItsOwn() = runBlocking {
        val a = video()
        val b = video().let { db.fileDao().byId(it.id)!! }
        db.introDao().putSegment(MediaSegmentEntity(a.id, "intro", 1_000, 5_000, "manual"))
        db.introDao().putSegment(MediaSegmentEntity(b.id, "intro", 1_000, 5_000, "manual"))
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        // Here: a lost its intro, b was marked otherwise since.
        db.introDao().deleteSegment(a.id, "intro")
        db.introDao().putSegment(MediaSegmentEntity(b.id, "intro", 2_000, 3_000, "manual"))
        repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        assertEquals(2, liveNotes().size)
        val sa = db.introDao().segmentsOf(a.id).single()
        assertEquals(1_000L to 5_000L, sa.startMs to sa.endMs)
        val sb = db.introDao().segmentsOf(b.id).single()
        assertEquals(2_000L to 3_000L, sb.startMs to sb.endMs)
    }

    @Test
    fun aFileAlreadyHereGetsOnlyTheEndingItLacks() = runBlocking {
        val a = video()
        db.introDao().putSegment(MediaSegmentEntity(a.id, "intro", 1_000, 5_000, "manual"))
        db.introDao().putSegment(MediaSegmentEntity(a.id, "outro", 50_000, 60_000, "manual"))
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        // Here: the ending lost, the intro marked otherwise since.
        db.introDao().deleteSegment(a.id, "outro")
        db.introDao().putSegment(MediaSegmentEntity(a.id, "intro", 2_000, 3_000, "manual"))
        repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        val segs = db.introDao().segmentsOf(a.id).associateBy { it.kind }
        assertEquals(2_000L to 3_000L, segs.getValue("intro").let { it.startMs to it.endMs })
        assertEquals(50_000L to 60_000L, segs.getValue("outro").let { it.startMs to it.endMs })
    }

    @Test
    fun aFileAlreadyHereGetsTheAudioPrintItLacksAndIsComparedAgain() = runBlocking {
        val a = video()
        val hashes = ByteArray(64) { it.toByte() }
        db.introDao().putPrint(AudioPrintEntity(a.id, "head", 0, hashes, "ok", matched = true))
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        db.introDao().clearPrints(a.id)
        repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        val print = db.introDao().printsOf(a.id).single()
        assertTrue(print.hashes.contentEquals(hashes))
        // Compared with this vault's videos at the next analysis, not taken as done.
        assertEquals(listOf(a.id), db.introDao().unmatchedIds())
    }

    @Test
    fun aFileAlreadyHereGetsTheSimilarityPrintItLacksAndKeepsItsOwn() = runBlocking {
        val a = video(); val b = video()
        db.dupPrintDao().put(DupPrintEntity(a.id, 1, 1, ByteArray(8) { 1 }))
        db.dupPrintDao().put(DupPrintEntity(b.id, 1, 1, ByteArray(8) { 2 }))
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        // Here: a lost its print, b has another one since.
        sql("DELETE FROM dup_prints WHERE fileId = '${a.id}'")
        db.dupPrintDao().put(DupPrintEntity(b.id, 1, 1, ByteArray(8) { 3 }))
        repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        val prints = db.dupPrintDao().all().associateBy { it.fileId }
        assertTrue(prints.getValue(a.id).hashes.contentEquals(ByteArray(8) { 1 }))
        assertTrue(prints.getValue(b.id).hashes.contentEquals(ByteArray(8) { 3 }))
    }

    @Test
    fun aFileAlreadyHereGetsTheFaceAnalysisItLacks() = runBlocking {
        val a = video()
        val anna = db.faceDao().addPerson(PersonEntity(name = "Anna", createdAt = 42))
        db.faceDao().putScan(FaceScanEntity(a.id, "ok", 5, 0, 1, "", 1, 0))
        db.faceDao().addTrack(FaceTrackEntity(fileId = a.id, frames = 1, timeMs = 0, faceCount = 1, width = 10, quality = 1f,
            embeddings = ByteArray(0), crop = ByteArray(0), personId = anna, state = "manual"))
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        sql("DELETE FROM face_tracks"); sql("DELETE FROM face_scans")
        repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        assertNotNull(db.faceDao().scanOf(a.id))
        val tracks = db.faceDao().trackRowsOf(listOf(a.id))
        assertEquals(1, tracks.size)
        assertEquals(anna, tracks.single().personId)
        assertEquals(1, db.faceDao().persons().size)
    }

    @Test
    fun aFileAlreadyAnalysedKeepsItsOwnFaces() = runBlocking {
        val a = video()
        db.faceDao().putScan(FaceScanEntity(a.id, "ok", 5, 0, 1, "", 1, 0))
        db.faceDao().addTrack(FaceTrackEntity(fileId = a.id, frames = 1, timeMs = 0, faceCount = 1, width = 10, quality = 1f,
            embeddings = ByteArray(0), crop = ByteArray(0)))
        roundTrip()
        assertEquals(1, db.faceDao().trackRowsOf(listOf(a.id)).size)
    }

    @Test
    fun restoringTheSameBackupAgainAddsNoAnalysisTwice() = runBlocking {
        val a = video()
        val hashes = ByteArray(64) { it.toByte() }
        db.introDao().putPrint(AudioPrintEntity(a.id, "head", 0, hashes, "ok"))
        val sig = db.introDao().addSignature(SignatureEntity(kind = "intro", name = "Sigla", createdAt = 1))
        db.introDao().addSignatureSound(SignatureSoundEntity(signatureId = sig, hashes = hashes))
        db.introDao().addRule(SegmentRuleEntity(kind = "intro", originFileId = a.id, originName = "clip", hashes = hashes,
            startDeltaMs = -1_000, endDeltaMs = 1_000, createdAt = 1))
        db.introDao().addRejected(RejectedPrintEntity(kind = "outro", originFileId = a.id, originName = "clip",
            startMs = 0, endMs = 4_000, hashes = hashes, createdAt = 1))
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        repeat(2) { repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray()) }
        val intro = db.introDao()
        assertEquals(1, intro.backupSignatures().size)
        assertEquals(1, intro.backupSignatureSounds().size)
        assertEquals(1, intro.backupRules().size)
        assertEquals(1, intro.backupRejected().size)
    }

    private suspend fun tag(fileId: String, name: String): Long {
        val id = db.tagDao().byName(name)?.id ?: db.tagDao().insert(TagEntity(name = name))
        db.tagDao().link(FileTagCrossRef(fileId = fileId, tagId = id))
        return id
    }

    private suspend fun tagNames(fileId: String) = db.fileDao().withTagsById(fileId)!!.tags.map { it.name }.toSet()

    @Test
    fun aTagDifferingOnlyInCaseJoinsTheOneHere() = runBlocking {
        val a = video()
        val t = tag(a.id, "vacanze")
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        // Here the tag is written "Vacanze" (another phone's archive writes it "vacanze").
        db.tagDao().rename(t, "Vacanze")
        repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        assertEquals(listOf("Vacanze"), db.tagDao().allNow().map { it.name })
        assertEquals(setOf("Vacanze"), tagNames(a.id))
    }

    @Test
    fun twoTagsOfAnArchiveDifferingOnlyInCaseStayTwo() = runBlocking {
        val a = video(); val b = video()
        tag(a.id, "Ab"); tag(b.id, "ab")
        val backup = File.createTempFile("vault", ".cripta", app.cacheDir)
        repo.exportBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        // Here they became one tag, "AB".
        db.tagDao().byName("ab")!!.let { db.tagDao().unlinkAll(it.id); db.tagDao().deleteById(it.id) }
        db.tagDao().rename(db.tagDao().byName("Ab")!!.id, "AB")
        tag(b.id, "AB")
        repo.importBackup(Uri.fromFile(backup), "a passphrase".toCharArray())
        // The archive told them apart: its two tags are not both made "AB".
        assertEquals(1, (tagNames(a.id) intersect tagNames(b.id)).size)
        assertEquals(setOf("AB", "Ab", "ab"), db.tagDao().allNow().map { it.name }.toSet())
    }
}

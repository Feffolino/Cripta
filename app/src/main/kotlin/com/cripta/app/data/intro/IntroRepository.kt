package com.cripta.app.data.intro

import androidx.room.withTransaction
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.db.RejectedPrintEntity
import com.cripta.app.media.intro.AudioDecoder
import com.cripta.app.media.intro.Fingerprint
import com.cripta.app.media.intro.Found
import com.cripta.app.media.intro.IntroDetector
import com.cripta.app.media.intro.Print
import com.cripta.app.media.intro.PrintCodec
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.security.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intros and outros: fingerprints the videos, matches them across the whole vault, stores the
 * segments and the user's own marks and rejections.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class IntroRepository @Inject constructor(
    private val session: SessionManager,
    private val repo: VaultRepository,
    private val decoder: AudioDecoder,
) {
    private val db get() = session.requireDb()
    private val dao get() = db.introDao()

    fun segments(fileId: String): Flow<List<MediaSegmentEntity>> =
        session.database.flatMapLatest { d -> if (d == null) emptyFlow() else d.introDao().observeSegments(fileId) }

    fun rejected(): Flow<List<RejectedPrintEntity>> =
        session.database.flatMapLatest { d -> if (d == null) emptyFlow() else d.introDao().observeRejected() }

    /** Live videos never analysed. */
    suspend fun pending(): List<FileEntity> {
        val done = dao.printedIds().toHashSet()
        return repo.allFilesSnapshot().filter { VaultRepository.isVideo(it.mimeType) && it.id !in done }
    }

    /** Fingerprint [file]'s head and tail; a video that cannot be analysed gets a "skip" row. True when analysed. */
    suspend fun fingerprint(file: FileEntity): Boolean {
        val dur = file.durationMs ?: 0L
        val parts = if (dur < MIN_DURATION_MS) null else try {
            val head = decoder.decode(file, 0, minOf(dur, HEAD_MS))
            val tail = decoder.decode(file, maxOf(0L, dur - TAIL_MS), dur)
            if (head == null || tail == null) null else head to tail
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("IntroRepository", "audio not decodable (${e.javaClass.simpleName})")
            null
        }
        if (parts == null) {
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.INTRO.part, 0, ByteArray(0), "skip"))
            return false
        }
        val (head, tail) = parts
        val headHashes = withContext(Dispatchers.Default) { Fingerprint.hashes(head.samples) }
        val tailHashes = withContext(Dispatchers.Default) { Fingerprint.hashes(tail.samples) }
        db.withTransaction {
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.INTRO.part, head.startMs, PrintCodec.encode(headHashes), "ok"))
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.OUTRO.part, tail.startMs, PrintCodec.encode(tailHashes), "ok"))
        }
        return true
    }

    /** Match the videos [ids] (just fingerprinted) against every other video. Returns how many videos got a segment. */
    suspend fun matchNew(ids: Collection<String>): Int = withContext(Dispatchers.Default) {
        val touched = HashSet<String>()
        for (kind in SegmentKind.entries) {
            val prints = livePrints(kind)
            val byId = prints.associateBy { it.fileId }
            val rejected = dao.rejectedOfKind(kind.id).map { PrintCodec.decode(it.hashes) }
            val done = HashSet<String>()
            for (id in ids) {
                ensureActive()
                val target = byId[id] ?: continue
                // A pair of two new videos is compared once: detect() reports both sides.
                val others = prints.filter { it.fileId != id && it.fileId !in done }
                done += id
                for (f in IntroDetector.detect(kind, target, others, rejected)) if (store(kind, f)) touched += f.fileId
            }
        }
        touched.size
    }

    suspend fun setManual(fileId: String, kind: SegmentKind, startMs: Long, endMs: Long) =
        dao.putSegment(MediaSegmentEntity(fileId, kind.id, startMs, endMs, "manual"))

    /** "Rimuovi" or "Non è un'intro · Solo qui": no segment of [kind] here, kept across scans. */
    suspend fun setNone(fileId: String, kind: SegmentKind) =
        dao.putSegment(MediaSegmentEntity(fileId, kind.id, 0, 0, "none"))

    /** Search [fileId]'s stretch in every other video; null when too short or the video has no fingerprint. */
    suspend fun searchOthers(fileId: String, kind: SegmentKind, startMs: Long, endMs: Long): Int? = withContext(Dispatchers.Default) {
        val sample = printOf(fileId, kind)?.slice(startMs, endMs) ?: return@withContext null
        if (sample.size < SegmentKind.OUTRO.minHashes / 2) return@withContext null
        IntroDetector.search(sample, livePrints(kind).filter { it.fileId != fileId }).count { store(kind, it) }
    }

    /** "Non è un'intro · In tutti i video". Returns how many other videos lost the segment. */
    suspend fun rejectEverywhere(file: FileEntity, kind: SegmentKind, startMs: Long, endMs: Long): Int = withContext(Dispatchers.Default) {
        dao.deleteSegment(file.id, kind.id)
        val sample = printOf(file.id, kind)?.slice(startMs, endMs)?.takeIf { it.isNotEmpty() } ?: return@withContext 0
        dao.addRejected(RejectedPrintEntity(kind = kind.id, originFileId = file.id, originName = file.originalName,
            startMs = startMs, endMs = endMs, hashes = PrintCodec.encode(sample), createdAt = System.currentTimeMillis()))
        var removed = 0
        for (seg in dao.segmentsOfKind(kind.id)) {
            if (seg.source != "auto" || seg.fileId == file.id) continue
            val stretch = printOf(seg.fileId, kind)?.slice(seg.startMs, seg.endMs) ?: continue
            if (IntroDetector.isRejected(stretch, listOf(sample))) { dao.deleteSegment(seg.fileId, kind.id); removed++ }
        }
        removed
    }

    /** Delete a rejected sample and put back at once the segments it was blocking. */
    suspend fun restoreRejected(id: Long): Int = withContext(Dispatchers.Default) {
        val r = dao.rejectedById(id) ?: return@withContext 0
        dao.deleteRejected(id)
        val kind = SegmentKind.of(r.kind)
        var restored = 0
        val origin = repo.fileById(r.originFileId)
        if (origin != null && origin.deletedAt == null && store(kind, Found(origin.id, r.startMs, r.endMs))) restored++
        val sample = PrintCodec.decode(r.hashes)
        restored + IntroDetector.search(sample, livePrints(kind).filter { it.fileId != r.originFileId }).count { store(kind, it) }
    }

    private suspend fun store(kind: SegmentKind, f: Found): Boolean {
        val existing = dao.segmentsOf(f.fileId).firstOrNull { it.kind == kind.id }
        if (!IntroDetector.shouldStore(existing, f)) return false
        dao.putSegment(MediaSegmentEntity(f.fileId, kind.id, f.startMs, f.endMs, "auto"))
        return true
    }

    private suspend fun printOf(fileId: String, kind: SegmentKind): Print? =
        dao.printsOf(fileId).firstOrNull { it.part == kind.part && it.status == "ok" }
            ?.let { Print(it.fileId, it.startMs, PrintCodec.decode(it.hashes)) }

    private suspend fun livePrints(kind: SegmentKind): List<Print> {
        val live = repo.allFilesSnapshot().mapTo(HashSet()) { it.id }
        return dao.allPrints(kind.part).filter { it.fileId in live }.map { Print(it.fileId, it.startMs, PrintCodec.decode(it.hashes)) }
    }

    private companion object {
        const val MIN_DURATION_MS = 120_000L
        const val HEAD_MS = 600_000L
        const val TAIL_MS = 300_000L
    }
}

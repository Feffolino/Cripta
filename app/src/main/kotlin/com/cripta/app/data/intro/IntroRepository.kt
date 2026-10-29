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
import com.cripta.app.media.intro.PrintIndex
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
import kotlin.coroutines.coroutineContext

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

    /**
     * Live videos to analyse: never analysed, and in the complete mode ([quick] off) also those a
     * quick scan covered only partly.
     */
    suspend fun pending(quick: Boolean): List<FileEntity> {
        val sizes = dao.headSizes().associateBy { it.fileId }
        return repo.allFilesSnapshot().filter { f ->
            if (!VaultRepository.isVideo(f.mimeType)) return@filter false
            val s = sizes[f.id] ?: return@filter true
            if (quick || s.status != "ok") return@filter false
            val covered = s.bytes / 4 * Fingerprint.HOP_MS
            covered < minOf(f.durationMs ?: 0L, HEAD_MS) - 30_000
        }
    }

    /** What the analysis found so far, for Settings: counts and the videos with an intro or outro. */
    class Overview(val videos: Int, val analysed: Int, val skipped: Int, val items: List<Item>) {
        val withIntro get() = items.count { it.intro != null }
        val withOutro get() = items.count { it.outro != null }
    }
    class Item(val name: String, val intro: MediaSegmentEntity?, val outro: MediaSegmentEntity?)

    suspend fun overview(): Overview {
        val live = repo.allFilesSnapshot().filter { VaultRepository.isVideo(it.mimeType) }
        val byId = live.associateBy { it.id }
        val sizes = dao.headSizes().filter { it.fileId in byId }
        val items = dao.allSegments().filter { it.fileId in byId }.groupBy { it.fileId }.map { (id, segs) ->
            Item(byId.getValue(id).originalName, segs.firstOrNull { it.kind == SegmentKind.INTRO.id }, segs.firstOrNull { it.kind == SegmentKind.OUTRO.id })
        }.sortedBy { it.name.lowercase() }
        return Overview(live.size, sizes.count { it.status == "ok" }, sizes.count { it.status != "ok" }, items)
    }

    /** Forget [fileId]'s fingerprint and detected segments (its own marks stay) before analysing it again. */
    suspend fun forget(fileId: String): FileEntity? {
        val f = repo.fileById(fileId)?.takeIf { it.deletedAt == null && VaultRepository.isVideo(it.mimeType) } ?: return null
        db.withTransaction {
            dao.clearPrints(fileId)
            dao.segmentsOf(fileId).filter { it.source == "auto" }.forEach { dao.deleteSegment(fileId, it.kind) }
        }
        return f
    }

    /**
     * Fingerprint [file]'s head and tail ([quick]: only its first and last minute); a video that
     * cannot be analysed gets a "skip" row. True when analysed.
     */
    suspend fun fingerprint(file: FileEntity, quick: Boolean): Boolean {
        val dur = file.durationMs ?: 0L
        val headMs = if (quick) QUICK_MS else HEAD_MS
        val tailMs = if (quick) QUICK_MS else TAIL_MS
        val started = android.os.SystemClock.elapsedRealtime()
        // Each part is hashed as soon as it is decoded, so only one part's audio is held at a time.
        suspend fun part(fromMs: Long, toMs: Long): Pair<Long, IntArray>? {
            val pcm = decoder.decode(file, fromMs, toMs) ?: return null
            return pcm.startMs to withContext(Dispatchers.Default) { Fingerprint.hashes(pcm.samples, pcm.size) }
        }
        val parts = if (dur < MIN_DURATION_MS) null else try {
            val head = part(0, minOf(dur, headMs))
            val tail = head?.let { part(maxOf(0L, dur - tailMs), dur) }
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
        db.withTransaction {
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.INTRO.part, head.first, PrintCodec.encode(head.second), "ok"))
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.OUTRO.part, tail.first, PrintCodec.encode(tail.second), "ok"))
        }
        android.util.Log.i("IntroRepository", "fingerprint ${android.os.SystemClock.elapsedRealtime() - started} ms (${dur / 1000} s video)")
        return true
    }

    /** Match the videos [ids] (just fingerprinted) against every other video. Returns how many videos got a segment. */
    suspend fun matchNew(ids: Collection<String>): Int = withContext(Dispatchers.Default) {
        val touched = HashSet<String>()
        for (kind in SegmentKind.entries) {
            val index = indexFor(kind)
            val rejected = dao.rejectedOfKind(kind.id).map { PrintCodec.decode(it.hashes) }
            val done = HashSet<String>()
            for (id in ids) {
                ensureActive()
                val target = printOf(id, kind) ?: continue
                // A pair of two new videos is compared once: detectAt() reports both sides.
                done += id
                val candidates = candidatesFor(index, target.hashes, kind) { it in done }
                for (f in IntroDetector.detectAt(kind, target, candidates, rejected)) if (store(kind, f)) touched += f.fileId
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
        IntroDetector.searchAt(sample, candidatesFor(indexFor(kind), sample, kind) { it == fileId }).count { store(kind, it) }
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
        restored + IntroDetector.searchAt(sample, candidatesFor(indexFor(kind), sample, kind) { it == r.originFileId })
            .count { store(kind, it) }
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

    /** Landmark index of every live video's print of [kind], read one print at a time. */
    private suspend fun indexFor(kind: SegmentKind): PrintIndex {
        val live = repo.allFilesSnapshot().mapTo(HashSet()) { it.id }
        val ids = dao.printIds(kind.part).filter { it in live }
        // About 16 hashes a second, a quarter of them landmarks; sized for quick prints, grows for complete ones.
        val perVideo = (QUICK_MS / Fingerprint.HOP_MS / PrintIndex.SAMPLE).toInt()
        val index = PrintIndex(ids.size * perVideo + 1024)
        for (id in ids) {
            coroutineContext.ensureActive()
            printOf(id, kind)?.let { index.add(it.fileId, it.hashes) }
        }
        return index
    }

    /** The videos the index proposes for [hashes], read from the database, minus [skip]. */
    private suspend fun candidatesFor(index: PrintIndex, hashes: IntArray, kind: SegmentKind,
                                      skip: (String) -> Boolean): List<Pair<Print, List<Int>>> =
        index.candidates(hashes).mapNotNull { (id, offsets) -> if (skip(id)) null else printOf(id, kind)?.let { it to offsets } }

    private companion object {
        const val MIN_DURATION_MS = 120_000L
        const val HEAD_MS = 600_000L
        const val TAIL_MS = 300_000L
        /** Quick scan: the first and the last minute only. */
        const val QUICK_MS = 60_000L
    }
}

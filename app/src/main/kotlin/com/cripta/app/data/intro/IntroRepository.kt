package com.cripta.app.data.intro

import androidx.room.withTransaction
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.db.RejectedPrintEntity
import com.cripta.app.data.db.SegmentRuleEntity
import com.cripta.app.data.db.SignatureEntity
import com.cripta.app.data.db.SignatureSoundEntity
import com.cripta.app.media.intro.AudioDecoder
import com.cripta.app.media.intro.Fingerprint
import com.cripta.app.media.intro.Found
import com.cripta.app.media.intro.IntroDetector
import com.cripta.app.media.intro.Print
import com.cripta.app.media.intro.PrintCodec
import com.cripta.app.media.intro.PrintIndex
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.media.intro.SegmentMatcher
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
            // Skipped by an older version (any video under 2 minutes, unknown length): try once more.
            if (s.status == "skip") return@filter true
            if (quick || s.status != "ok") return@filter false
            val covered = s.bytes / 4 * Fingerprint.HOP_MS
            covered < minOf(f.durationMs ?: 0L, HEAD_MS) - 30_000
        }
    }

    /**
     * "Ricomincia da capo": throw away every fingerprint and detected segment ([everything]: also the
     * user's own marks and rejected samples), so the next scan analyses every video anew.
     */
    suspend fun resetAll(everything: Boolean) = db.withTransaction {
        dao.deleteAllPrints()
        if (everything) {
            dao.deleteAllSegments(); dao.deleteAllRejected(); dao.deleteAllRules()
            dao.deleteAllSignatureSounds(); dao.deleteAllSignatures()
        } else dao.deleteAutoSegments()
    }

    /** What the analysis found so far: one [Entry] per video analysed or skipped, for Settings. */
    class Overview(val videos: Int, val entries: List<Entry>) {
        val analysed get() = entries.count { it.skipReason == null }
        val skipped get() = entries.count { it.skipReason != null }
        val withIntro get() = entries.count { it.intro != null }
        val withOutro get() = entries.count { it.outro != null }
    }

    /** A video in the results; [skipReason] is set when it could not be analysed. */
    class Entry(val file: FileEntity, val intro: MediaSegmentEntity?, val outro: MediaSegmentEntity?, val skipReason: String?)

    suspend fun overview(): Overview = withContext(Dispatchers.Default) {
        val live = repo.allFilesSnapshot().filter { VaultRepository.isVideo(it.mimeType) }
        val byId = live.associateBy { it.id }
        val segs = dao.allSegments().filter { it.fileId in byId }.groupBy { it.fileId }
        val entries = dao.headSizes().filter { it.fileId in byId }.map { s ->
            val own = segs[s.fileId].orEmpty()
            Entry(byId.getValue(s.fileId), own.firstOrNull { it.kind == SegmentKind.INTRO.id }, own.firstOrNull { it.kind == SegmentKind.OUTRO.id },
                if (s.status == "ok") null else s.status.removePrefix("skip").removePrefix(":").ifBlank { null } ?: "versione precedente: verrà riprovato")
        }.sortedBy { it.file.originalName.lowercase() }
        Overview(live.size, entries)
    }

    private fun clock(ms: Long) = (ms / 1000).let { s -> if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    /**
     * "Confronta audio" on two selected videos: how much audio their starts share and where, whatever
     * the thresholds, to tell a recognition problem from videos that simply differ.
     */
    suspend fun compare(idA: String, idB: String): String = withContext(Dispatchers.Default) {
        val a = printOf(idA, SegmentKind.INTRO) ?: return@withContext "Il primo video non è ancora analizzato (o non è analizzabile)."
        val b = printOf(idB, SegmentKind.INTRO) ?: return@withContext "Il secondo video non è ancora analizzato (o non è analizzabile)."
        fun secs(p: Print) = (p.hashes.size * Fingerprint.HOP_MS / 1000).toInt()
        val m = SegmentMatcher.best(a.hashes, b.hashes, 1)
            ?: return@withContext "Nessun audio in comune nei primi ${secs(a)} s e ${secs(b)} s analizzati."
        val len = m.length * Fingerprint.HOP_MS / 1000
        val bits = (m.aStart until m.aEnd).sumOf { Integer.bitCount(a.hashes[it] xor b.hashes[it + m.bStart - m.aStart]) } / m.length.toDouble()
        "In comune %.1f s: %s–%s nel primo, %s–%s nel secondo. Somiglianza %d%% (analizzati %d s e %d s).".format(
            len, clock(a.timeOf(m.aStart)), clock(a.timeOf(m.aEnd)), clock(b.timeOf(m.bStart)), clock(b.timeOf(m.bEnd)),
            ((1 - bits / 32) * 100).toInt(), secs(a), secs(b))
    }

    /**
     * The answer to "Analizza": for each video its intro, or the precise reason there is none (so a
     * failure can be told apart from videos that simply share nothing).
     */
    suspend fun describe(ids: List<String>): String = withContext(Dispatchers.Default) {
        val names = ids.associateWith { repo.fileById(it)?.originalName ?: "Video" }
        val intros = ids.associateWith { id -> dao.segmentsOf(id).firstOrNull { it.kind == SegmentKind.INTRO.id } }
        // Chosen together: compare with each other directly; alone: with the vault's candidates.
        val chosen = ids.mapNotNull { printOf(it, SegmentKind.INTRO) }
        val index = if (ids.size == 1) indexFor(SegmentKind.INTRO) else null
        val lines = ids.map { id ->
            val seg = intros[id]
            val what = when {
                seg != null && seg.source != "none" -> "intro ${clock(seg.startMs)}–${clock(seg.endMs)}"
                else -> "nessuna intro: " + whyNoIntro(id, seg, chosen, index)
            }
            "• ${names.getValue(id)}: $what"
        }
        val found = intros.values.count { it != null && it.source != "none" }
        (if (ids.size > 1) "Intro trovata in $found di ${ids.size}\n" else "") + lines.take(12).joinToString("\n")
    }

    /** Why [id] has no intro: the user's own choice, its audio, or what its best shared stretch lacks. */
    private suspend fun whyNoIntro(id: String, seg: MediaSegmentEntity?, chosen: List<Print>, index: PrintIndex?): String {
        if (seg?.source == "none") return "segnato da te come «nessuna intro» (riattivalo con «Inizio qui» / «Fine qui», o «Ricomincia da capo» cancellando anche i segni a mano)"
        dao.printsOf(id).takeIf { p -> p.none { it.status == "ok" } }?.let { p ->
            return "non analizzabile (" + (p.firstOrNull()?.status?.removePrefix("skip")?.removePrefix(":")?.ifBlank { null } ?: "motivo sconosciuto") + ")"
        }
        val kind = SegmentKind.INTRO
        val p = printOf(id, kind) ?: return "non ancora analizzato"
        val others = if (index != null) candidatesFor(index, p.hashes, kind) { it == id }.map { it.first } else chosen.filter { it.fileId != id }
        val best = others.mapNotNull { o -> SegmentMatcher.best(p.hashes, o.hashes, 1)?.let { o to it } }.maxByOrNull { it.second.length }
            ?: return if (index != null) "nessun altro video nel vault ha audio in comune" else "nessun audio in comune con gli altri video scelti"
        val (o, m) = best
        val secs = m.length * Fingerprint.HOP_MS / 1000
        val other = repo.fileById(o.fileId)?.originalName ?: "un altro video"
        return when {
            m.length < kind.minHashes -> "in comune con «$other» solo %.1f s (ne servono almeno 2)".format(secs)
            IntroDetector.isCut(kind, p, o, m) -> "in comune con «$other» %.0f s fino al bordo della parte analizzata: sembra una copia dello stesso video".format(secs)
            IntroDetector.isRejected(p.hashes.copyOfRange(m.aStart, m.aEnd), dao.rejectedOfKind(kind.id).map { PrintCodec.decode(it.hashes) }) ->
                "il tratto in comune con «$other» è tra i segmenti scartati (Impostazioni › Persone e intro › Intro e finale)"
            else -> "in comune con «$other» %.0f s (%s–%s), ma non salvato: segnalamelo".format(secs, clock(p.timeOf(m.aStart)), clock(p.timeOf(m.aEnd)))
        }
    }

    /**
     * A signature: videos whose segment of [kind] has the same sound. [id] null = found by grouping,
     * not saved yet (it gets saved the first time the user renames, merges or times it).
     */
    class Signature(
        val id: Long?, val name: String?, val kind: SegmentKind,
        val entries: List<Pair<FileEntity, MediaSegmentEntity>>,
        val soundMs: Long, val startDeltaMs: Long, val endDeltaMs: Long,
        /** The members' sounds, longest first: what saving it keeps as reference sounds. */
        internal val cores: List<IntArray>,
    )

    /** A video's segment and the sound it was found on (its core, before any extension). */
    private class Member(val seg: MediaSegmentEntity, val core: IntArray)

    private suspend fun members(kind: SegmentKind, live: Set<String>): List<Member> =
        dao.segmentsOfKind(kind.id).filter { it.source != "none" && it.endMs > it.startMs && it.fileId in live }.mapNotNull { s ->
            val core = printOf(s.fileId, kind)?.slice(s.autoStartMs ?: s.startMs, s.autoEndMs ?: s.endMs) ?: return@mapNotNull null
            if (core.size < kind.minHashes) null else Member(s, core)
        }

    /** Whether two sounds are the same: at least half of the shorter one in common, at [offsets]. */
    private fun sameSound(kind: SegmentKind, a: IntArray, b: IntArray, offsets: List<Int>): Boolean {
        val min = maxOf(kind.minHashes, minOf(a.size, b.size) / 2)
        return offsets.any { SegmentMatcher.at(a, b, it, min) != null }
    }

    /** The saved signature (oldest first) one of whose sounds is [core], or null. */
    private suspend fun savedSignatureOf(kind: SegmentKind, core: IntArray): SignatureEntity? {
        val sounds = dao.signatureSoundsOfKind(kind.id)
        if (sounds.isEmpty()) return null
        val decoded = sounds.map { PrintCodec.decode(it.hashes) }
        val index = PrintIndex(decoded.sumOf { it.size } + 1024).apply { decoded.forEachIndexed { i, h -> add("$i", h) } }
        val hits = index.candidates(core).mapNotNull { (k, offs) -> k.toInt().takeIf { sameSound(kind, core, decoded[it], offs) } }
            .map { sounds[it].signatureId }.toSet()
        return dao.signaturesOfKind(kind.id).firstOrNull { it.id in hits }
    }

    /**
     * Every signature of [kind]: the saved ones (each video goes to the oldest saved signature knowing
     * its sound), then the rest grouped by sound as unsaved ones. Unnamed and small ones first: the
     * likeliest false positives.
     */
    suspend fun signatures(kind: SegmentKind): List<Signature> = withContext(Dispatchers.Default) {
        val files = repo.allFilesSnapshot().associateBy { it.id }
        val members = members(kind, files.keys)
        val saved = dao.signaturesOfKind(kind.id)
        val sounds = dao.signatureSoundsOfKind(kind.id)
        val decoded = sounds.map { PrintCodec.decode(it.hashes) }
        val soundIndex = PrintIndex(decoded.sumOf { it.size } + 1024).apply { decoded.forEachIndexed { i, h -> add("$i", h) } }
        val order = saved.withIndex().associate { it.value.id to it.index }
        val toSaved = HashMap<Long, MutableList<Member>>()
        val loose = ArrayList<Member>()
        for (m in members) {
            ensureActive()
            val owner = soundIndex.candidates(m.core).mapNotNull { (k, offs) ->
                k.toInt().takeIf { sameSound(kind, m.core, decoded[it], offs) }?.let { sounds[it].signatureId }
            }.minByOrNull { order[it] ?: Int.MAX_VALUE }
            if (owner != null) toSaved.getOrPut(owner) { ArrayList() } += m else loose += m
        }
        // The rest: grouped by sound, directly or through other videos.
        val index = PrintIndex(loose.sumOf { it.core.size } + 1024).apply { loose.forEachIndexed { i, m -> add("$i", m.core) } }
        val parent = IntArray(loose.size) { it }
        fun root(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; return r }
        for ((i, m) in loose.withIndex()) {
            ensureActive()
            for ((k, offs) in index.candidates(m.core)) {
                val j = k.toInt().takeIf { it != i } ?: continue
                if (sameSound(kind, m.core, loose[j].core, offs)) parent[root(i)] = root(j)
            }
        }
        fun build(id: Long?, name: String?, ds: Long, de: Long, ms: List<Member>): Signature {
            val lengths = ms.map { it.core.size }.sorted()
            return Signature(id, name, kind,
                ms.map { files.getValue(it.seg.fileId) to it.seg }.sortedBy { it.first.originalName.lowercase() },
                (lengths[lengths.size / 2] * Fingerprint.HOP_MS).toLong(), ds, de,
                ms.map { it.core }.sortedByDescending { it.size })
        }
        val named = saved.mapNotNull { s -> toSaved[s.id]?.let { build(s.id, s.name.ifBlank { null }, s.startDeltaMs, s.endDeltaMs, it) } }
        val unnamed = loose.indices.groupBy { root(it) }.values.map { g -> build(null, null, 0, 0, g.map { loose[it] }) }
        (named + unnamed).sortedWith(compareBy<Signature>({ it.name != null }, { it.entries.size }))
    }

    /** Save an unsaved signature (its longest sounds become its reference sounds); returns its id. */
    private suspend fun persist(sig: Signature, name: String = ""): Long {
        sig.id?.let { return it }
        val id = dao.addSignature(SignatureEntity(kind = sig.kind.id, name = name, createdAt = System.currentTimeMillis()))
        sig.cores.take(MAX_SIGNATURE_SOUNDS).forEach { dao.addSignatureSound(SignatureSoundEntity(signatureId = id, hashes = PrintCodec.encode(it))) }
        return id
    }

    suspend fun renameSignature(sig: Signature, name: String) {
        val id = persist(sig, name.trim())
        dao.renameSignature(id, name.trim())
    }

    /** "Unisci": one signature knowing all their sounds; its name and duration are the first named one's. */
    suspend fun mergeSignatures(sigs: List<Signature>, name: String?) = db.withTransaction {
        if (sigs.size < 2) return@withTransaction
        val target = sigs.firstOrNull { it.id != null && it.name != null } ?: sigs.firstOrNull { it.id != null } ?: sigs.first()
        val targetId = persist(target)
        for (s in sigs) {
            if (s === target) continue
            if (s.id != null) { dao.moveSignatureSounds(s.id, targetId); dao.deleteSignature(s.id) }
            else s.cores.take(MAX_SIGNATURE_SOUNDS).forEach { dao.addSignatureSound(SignatureSoundEntity(signatureId = targetId, hashes = PrintCodec.encode(it))) }
        }
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { dao.renameSignature(targetId, it) }
        val deltas = dao.signaturesOfKind(target.kind.id).first { it.id == targetId }
        if (deltas.startDeltaMs != 0L || deltas.endDeltaMs != 0L) {
            for (s in sigs) applyDeltas(s, deltas.startDeltaMs, deltas.endDeltaMs)
        }
    }

    /**
     * "Non è una sigla": the signature's videos lose that segment (the user's own marks stay) and its
     * sounds become rejected samples, so they are never detected again (undo in "Segmenti scartati").
     */
    suspend fun deleteSignature(sig: Signature) = db.withTransaction {
        for ((_, seg) in sig.entries) if (seg.source == "auto") dao.deleteSegment(seg.fileId, sig.kind.id)
        val origin = sig.entries.firstOrNull()
        val sounds = if (sig.id != null) dao.signatureSoundsOfKind(sig.kind.id).filter { it.signatureId == sig.id }.map { PrintCodec.decode(it.hashes) }
            else sig.cores.take(MAX_SIGNATURE_SOUNDS)
        for (h in sounds) {
            dao.addRejected(RejectedPrintEntity(kind = sig.kind.id, originFileId = origin?.first?.id.orEmpty(),
                originName = sig.name ?: origin?.first?.originalName.orEmpty(), startMs = origin?.second?.startMs ?: 0,
                endMs = (origin?.second?.startMs ?: 0) + (h.size * Fingerprint.HOP_MS).toLong(),
                hashes = PrintCodec.encode(h), createdAt = System.currentTimeMillis()))
        }
        sig.id?.let { dao.deleteSignatureSounds(it); dao.deleteSignature(it) }
    }

    /** "Durata della sigla": move every detected segment of the signature by these deltas from its sound. */
    suspend fun setSignatureDuration(sig: Signature, startDelta: Long, endDelta: Long) {
        val id = persist(sig)
        dao.setSignatureDeltas(id, startDelta, endDelta)
        applyDeltas(sig, startDelta, endDelta)
    }

    private suspend fun applyDeltas(sig: Signature, startDelta: Long, endDelta: Long) {
        for ((file, seg) in sig.entries) {
            if (seg.source != "auto") continue   // the user's own marks stay
            val core = Found(file.id, seg.autoStartMs ?: seg.startMs, seg.autoEndMs ?: seg.endMs)
            dao.putSegment(IntroDetector.segmentFor(sig.kind, core, startDelta, endDelta, file.durationMs))
        }
    }

    /** The name of the saved signature [fileId]'s segment of [kind] belongs to, if any. */
    suspend fun signatureName(fileId: String, kind: SegmentKind): String? = withContext(Dispatchers.Default) {
        val s = dao.segmentsOf(fileId).firstOrNull { it.kind == kind.id && it.source != "none" } ?: return@withContext null
        val core = printOf(fileId, kind)?.slice(s.autoStartMs ?: s.startMs, s.autoEndMs ?: s.endMs)
            ?.takeIf { it.size >= kind.minHashes } ?: return@withContext null
        savedSignatureOf(kind, core)?.name?.ifBlank { null }
    }

    /** "ok", the reason [fileId] could not be analysed, or null when it was never analysed. */
    suspend fun printStatus(fileId: String): String? {
        val p = dao.printsOf(fileId)
        if (p.isEmpty()) return null
        if (p.any { it.status == "ok" }) return "ok"
        return p.first().status.removePrefix("skip").removePrefix(":").ifBlank { "motivo sconosciuto" }
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
        val dur = file.durationMs?.takeIf { it > 0 } ?: decoder.durationMs(file) ?: 0L
        val headMs = if (quick) QUICK_MS else HEAD_MS
        val tailMs = if (quick) QUICK_MS else TAIL_MS
        val started = android.os.SystemClock.elapsedRealtime()
        // Each part is hashed as soon as it is decoded, so only one part's audio is held at a time.
        suspend fun part(fromMs: Long, toMs: Long): Pair<Long, IntArray>? {
            val pcm = decoder.decode(file, fromMs, toMs) ?: return null
            return pcm.startMs to withContext(Dispatchers.Default) { Fingerprint.hashes(pcm.samples, pcm.size) }
        }
        var reason = "audio non decodificabile"
        val parts = when {
            dur <= 0 -> { reason = "durata non leggibile"; null }
            dur < MIN_DURATION_MS -> { reason = "video più corto di 20 secondi"; null }
            else -> try {
                val head = part(0, minOf(dur, headMs))
                val tail = head?.let { part(maxOf(0L, dur - tailMs), dur) }
                if (head == null || tail == null) null else head to tail
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioDecoder.Undecodable) {
                reason = e.message ?: reason; null
            } catch (e: Exception) {
                android.util.Log.w("IntroRepository", "audio not decodable (${e.javaClass.simpleName})")
                reason = "errore di lettura (${e.javaClass.simpleName})"; null
            }
        }
        if (parts == null) {
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.INTRO.part, 0, ByteArray(0), "skip:$reason"))
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
                for (f in IntroDetector.detectAt(kind, target, candidates, rejected).found) if (store(kind, f)) touched += f.fileId
            }
        }
        ids.chunked(200).forEach { dao.markMatched(it) }
        touched.size
    }

    /** Videos fingerprinted but never compared with the rest (a scan stopped before its matching). */
    suspend fun unmatched(): List<String> {
        val live = repo.allFilesSnapshot().mapTo(HashSet()) { it.id }
        return dao.unmatchedIds().filter { it in live }
    }

    /** A mark or a trim by the user; the detected sound stays remembered for "Applica a tutti". */
    suspend fun setManual(fileId: String, kind: SegmentKind, startMs: Long, endMs: Long) {
        val old = dao.segmentsOf(fileId).firstOrNull { it.kind == kind.id }
        val coreStart = old?.autoStartMs ?: old?.takeIf { it.source == "auto" }?.startMs
        val coreEnd = old?.autoEndMs ?: old?.takeIf { it.source == "auto" }?.endMs
        dao.putSegment(MediaSegmentEntity(fileId, kind.id, startMs, endMs, "manual", coreStart, coreEnd))
    }

    /**
     * "Applica a tutti i video con la stessa sigla": the user moved this segment's bounds away from the
     * detected sound; every video with that sound gets the same move, now and when analysed later.
     * Returns how many videos changed, or null when the segment has no detected sound to search.
     */
    suspend fun applyToAll(file: FileEntity, kind: SegmentKind): Int? = withContext(Dispatchers.Default) {
        val seg = dao.segmentsOf(file.id).firstOrNull { it.kind == kind.id } ?: return@withContext null
        val coreStart = seg.autoStartMs ?: return@withContext null
        val coreEnd = seg.autoEndMs ?: return@withContext null
        val sample = printOf(file.id, kind)?.slice(coreStart, coreEnd)?.takeIf { it.size >= kind.minHashes } ?: return@withContext null
        val ds = seg.startMs - coreStart
        val de = seg.endMs - coreEnd
        dao.addRule(SegmentRuleEntity(kind = kind.id, originFileId = file.id, originName = file.originalName,
            hashes = PrintCodec.encode(sample), startDeltaMs = ds, endDeltaMs = de, createdAt = System.currentTimeMillis()))
        val durations = repo.allFilesSnapshot().associate { it.id to it.durationMs }
        var changed = 0
        for (f in IntroDetector.searchAt(sample, candidatesFor(indexFor(kind), sample, kind) { it == file.id })) {
            val existing = dao.segmentsOf(f.fileId).firstOrNull { it.kind == kind.id }
            if (existing != null && existing.source != "auto") continue   // the user's own marks stay
            dao.putSegment(IntroDetector.segmentFor(kind, f, ds, de, durations[f.fileId]))
            changed++
        }
        changed
    }

    /** "Rimuovi" or "Non è un'intro · Solo qui": no segment of [kind] here, kept across scans. */
    suspend fun setNone(fileId: String, kind: SegmentKind) =
        dao.putSegment(MediaSegmentEntity(fileId, kind.id, 0, 0, "none"))

    /** Search [fileId]'s stretch in every other video; null when too short or the video has no fingerprint. */
    suspend fun searchOthers(fileId: String, kind: SegmentKind, startMs: Long, endMs: Long): Int? = withContext(Dispatchers.Default) {
        val sample = printOf(fileId, kind)?.slice(startMs, endMs) ?: return@withContext null
        if (sample.size < SegmentKind.OUTRO.minHashes) return@withContext null
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
        // A sound with a duration of its own (its signature's, or "Applica a tutti") gets the same move here.
        val rules = dao.rulesOfKind(kind.id)
        val timed = dao.signaturesOfKind(kind.id).any { it.startDeltaMs != 0L || it.endDeltaMs != 0L }
        var ds = 0L; var de = 0L
        if (timed || rules.isNotEmpty()) printOf(f.fileId, kind)?.slice(f.startMs, f.endMs)?.let { stretch ->
            val sig = if (timed && stretch.size >= kind.minHashes) savedSignatureOf(kind, stretch) else null
            val rule = rules.firstOrNull { IntroDetector.isRejected(stretch, listOf(PrintCodec.decode(it.hashes))) }
            when {
                sig != null && (sig.startDeltaMs != 0L || sig.endDeltaMs != 0L) -> { ds = sig.startDeltaMs; de = sig.endDeltaMs }
                rule != null -> { ds = rule.startDeltaMs; de = rule.endDeltaMs }
            }
        }
        val duration = if (ds == 0L && de == 0L) null else repo.fileById(f.fileId)?.durationMs
        dao.putSegment(IntroDetector.segmentFor(kind, f, ds, de, duration))
        return true
    }

    private suspend fun printOf(fileId: String, kind: SegmentKind): Print? =
        dao.printsOf(fileId).firstOrNull { it.part == kind.part && it.status == "ok" }
            ?.let { Print(it.fileId, it.startMs, PrintCodec.decode(it.hashes)) }

    /** Landmark index of every live video's print of [kind], read one print at a time. */
    private suspend fun indexFor(kind: SegmentKind): PrintIndex {
        val live = repo.allFilesSnapshot().mapTo(HashSet()) { it.id }
        val ids = dao.printIds(kind.part).filter { it in live }
        // Every hash while memory allows (quick prints), landmarks only beyond that.
        val total = dao.printBytes(kind.part) / 4
        val sample = PrintIndex.sampleFor(total)
        val index = PrintIndex((total / sample).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + 1024, sample)
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
        /** Just over the shortest intro (15 s). */
        const val MIN_DURATION_MS = 20_000L
        const val HEAD_MS = 600_000L
        const val TAIL_MS = 300_000L
        /** Quick scan: the first and the last minute only. */
        const val QUICK_MS = 60_000L
        /** Reference sounds kept when a signature is saved: a few versions are enough to recognise it. */
        const val MAX_SIGNATURE_SOUNDS = 3
    }
}

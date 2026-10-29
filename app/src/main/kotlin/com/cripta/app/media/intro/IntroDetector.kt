package com.cripta.app.media.intro

import com.cripta.app.data.db.MediaSegmentEntity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Intro or outro, the print part it is searched in and its minimum length (in hashes). */
enum class SegmentKind(val id: String, val part: String, val minHashes: Int) {
    // 5 s: short intros (a few seconds of jingle before the video) were missed with 15 s.
    INTRO("intro", "head", (5_000 / Fingerprint.HOP_MS).toInt()),
    OUTRO("outro", "tail", (5_000 / Fingerprint.HOP_MS).toInt());

    companion object {
        fun of(id: String): SegmentKind = entries.first { it.id == id }
    }
}

/** One part of a video's fingerprint, decoded. */
class Print(val fileId: String, val startMs: Long, val hashes: IntArray) {
    fun timeOf(index: Int): Long = startMs + (index * Fingerprint.HOP_MS).roundToLong()
    fun indexOf(ms: Long): Int = ((ms - startMs) / Fingerprint.HOP_MS).roundToInt().coerceIn(0, hashes.size)
    fun slice(fromMs: Long, toMs: Long): IntArray = hashes.copyOfRange(indexOf(fromMs), maxOf(indexOf(fromMs), indexOf(toMs)))
}

/** A segment found in a video. */
data class Found(val fileId: String, val startMs: Long, val endMs: Long)

object IntroDetector {
    /** A new version of a video keeps the old one's manual marks only within this length difference. */
    private const val CARRY_TOLERANCE_MS = 2_000L

    /**
     * Stretches [target] shares with each of [others]: for the target the longest, for each other
     * video the stretch that matched it. Stretches covered by a [rejected] sample are dropped.
     */
    fun detect(kind: SegmentKind, target: Print, others: List<Print>, rejected: List<IntArray>): List<Found> =
        collect(kind, target, others.filter { it.fileId != target.fileId }
            .mapNotNull { o -> SegmentMatcher.best(target.hashes, o.hashes, kind.minHashes)?.let { o to it } }, rejected).found

    /** What [detectAt] found, plus the videos whose shared stretch was cut by the analysed window. */
    class Detection(val found: List<Found>, val cut: Set<String>)

    /** [detect] only at the alignments a [PrintIndex] proposed for each candidate video. */
    fun detectAt(kind: SegmentKind, target: Print, candidates: List<Pair<Print, List<Int>>>, rejected: List<IntArray>): Detection =
        collect(kind, target, candidates.filter { it.first.fileId != target.fileId }
            .mapNotNull { (o, offsets) -> bestAt(target.hashes, o.hashes, offsets, kind.minHashes)?.let { o to it } }, rejected)

    private fun collect(kind: SegmentKind, target: Print, matches: List<Pair<Print, Match>>, rejected: List<IntArray>): Detection {
        var best: Found? = null
        val found = mutableListOf<Found>()
        val cut = HashSet<String>()
        for ((o, m) in matches) {
            if (isCut(kind, target, o, m)) { cut += target.fileId; cut += o.fileId; continue }
            if (isRejected(target.hashes.copyOfRange(m.aStart, m.aEnd), rejected)) continue
            val t = Found(target.fileId, target.timeOf(m.aStart), target.timeOf(m.aEnd))
            if (best == null || t.endMs - t.startMs > best.endMs - best.startMs) best = t
            found += Found(o.fileId, o.timeOf(m.bStart), o.timeOf(m.bEnd))
        }
        return Detection(listOfNotNull(best) + found, cut)
    }

    /** Hashes from a window's edge within which a shared stretch counts as cut by the window. */
    private val EDGE = (2_000 / Fingerprint.HOP_MS).toInt()

    /**
     * Whether a shared stretch runs into the open side of the analysed window: the end of the start
     * minutes (intro) or the beginning of the last ones (outro). That is two copies of the same video
     * (an original and its MP4 copy, a file imported twice), not an intro: as the longest match it won,
     * and skipping to the window's edge jumped a whole minute.
     */
    fun isCut(kind: SegmentKind, a: Print, b: Print, m: Match): Boolean = when (kind) {
        SegmentKind.INTRO -> m.aEnd >= a.hashes.size - EDGE || m.bEnd >= b.hashes.size - EDGE
        SegmentKind.OUTRO -> m.aStart <= EDGE || m.bStart <= EDGE
    }

    /** Where a stretch the user marked ([sample]) appears in each of [others]. */
    fun search(sample: IntArray, others: List<Print>): List<Found> = others.mapNotNull { o ->
        SegmentMatcher.best(sample, o.hashes, searchMin(sample))?.let { Found(o.fileId, o.timeOf(it.bStart), o.timeOf(it.bEnd)) }
    }

    /** [search] only at the alignments a [PrintIndex] proposed. */
    fun searchAt(sample: IntArray, candidates: List<Pair<Print, List<Int>>>): List<Found> = candidates.mapNotNull { (o, offsets) ->
        bestAt(sample, o.hashes, offsets, searchMin(sample))?.let { Found(o.fileId, o.timeOf(it.bStart), o.timeOf(it.bEnd)) }
    }

    private fun searchMin(sample: IntArray) = maxOf((sample.size * 0.8).toInt(), SegmentKind.OUTRO.minHashes / 2)

    private fun bestAt(a: IntArray, b: IntArray, offsets: List<Int>, minLength: Int): Match? =
        offsets.mapNotNull { SegmentMatcher.at(a, b, it, minLength) }.maxByOrNull { it.length }

    /** Whether at least half of [stretch] is audio of one of the [rejected] samples. */
    fun isRejected(stretch: IntArray, rejected: List<IntArray>): Boolean = rejected.any { r ->
        val m = SegmentMatcher.best(stretch, r, 1) ?: return@any false
        m.length * 2 >= stretch.size
    }

    /** Never over the user's own choice ("manual" or "none"); over an automatic one only when longer. */
    fun shouldStore(existing: MediaSegmentEntity?, found: Found): Boolean = when (existing?.source) {
        null -> true
        "auto" -> found.endMs - found.startMs > existing.endMs - existing.startMs
        else -> false
    }

    fun carriesManual(fromDurationMs: Long?, toDurationMs: Long?): Boolean =
        fromDurationMs != null && toDurationMs != null && abs(fromDurationMs - toDurationMs) <= CARRY_TOLERANCE_MS
}

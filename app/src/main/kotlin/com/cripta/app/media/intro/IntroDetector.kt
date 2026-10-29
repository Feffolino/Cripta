package com.cripta.app.media.intro

import com.cripta.app.data.db.MediaSegmentEntity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Intro or outro, the print part it is searched in and its minimum length (in hashes). */
enum class SegmentKind(val id: String, val part: String, val minHashes: Int) {
    INTRO("intro", "head", (15_000 / Fingerprint.HOP_MS).toInt()),
    OUTRO("outro", "tail", (10_000 / Fingerprint.HOP_MS).toInt());

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
    fun detect(kind: SegmentKind, target: Print, others: List<Print>, rejected: List<IntArray>): List<Found> {
        var best: Found? = null
        val found = mutableListOf<Found>()
        for (o in others) {
            if (o.fileId == target.fileId) continue
            val m = SegmentMatcher.best(target.hashes, o.hashes, kind.minHashes) ?: continue
            if (isRejected(target.hashes.copyOfRange(m.aStart, m.aEnd), rejected)) continue
            val t = Found(target.fileId, target.timeOf(m.aStart), target.timeOf(m.aEnd))
            if (best == null || t.endMs - t.startMs > best.endMs - best.startMs) best = t
            found += Found(o.fileId, o.timeOf(m.bStart), o.timeOf(m.bEnd))
        }
        return listOfNotNull(best) + found
    }

    /** Where a stretch the user marked ([sample]) appears in each of [others]. */
    fun search(sample: IntArray, others: List<Print>): List<Found> {
        val min = maxOf((sample.size * 0.8).toInt(), SegmentKind.OUTRO.minHashes / 2)
        return others.mapNotNull { o ->
            SegmentMatcher.best(sample, o.hashes, min)?.let { Found(o.fileId, o.timeOf(it.bStart), o.timeOf(it.bEnd)) }
        }
    }

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

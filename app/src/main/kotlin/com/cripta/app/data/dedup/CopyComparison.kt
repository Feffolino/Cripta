package com.cripta.app.data.dedup

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Why one copy of a group of similar media beats another, in plain words, for "Perché è
 * consigliata". Free of Android types (unit tested). The decisive reasons ([Reason.major]) are
 * what the recommendation rests on: sharper, less compressed, longer; the rest only add to it.
 * Tags never decide: they go to the copy kept whichever it is (the screen says so itself).
 */
internal object CopyComparison {
    /** What is known of one copy. [width]/[height] null when unreadable. */
    class Facts(
        val width: Int?, val height: Int?, val bytes: Long, val durationMs: Long?, val importedAt: Long,
        val video: Boolean, val mp4: Boolean, val favorite: Boolean, val hasLink: Boolean,
    ) {
        val pixels: Long get() = if (width != null && height != null) width.toLong() * height else 0L
    }

    class Reason(val major: Boolean, val title: String, val detail: String)

    /** [best] against [other]; [date] and [duration] format a moment and a length for the text. */
    fun reasons(best: Facts, other: Facts, date: (Long) -> String, duration: (Long) -> String): List<Reason> {
        val out = ArrayList<Reason>()
        val sharper = best.pixels > 0 && other.pixels > 0 && best.pixels > other.pixels * 1.05
        if (sharper) {
            out += Reason(true, "Più nitida", "${label(best)} contro ${label(other)}: ${times(best.pixels.toDouble() / other.pixels, "i pixel")}.")
        } else if (best.pixels > 0 && best.pixels == other.pixels) {
            out += Reason(false, "Stessa risoluzione", "${label(best)}, come l'altra.")
        }
        // Data per pixel (and per second for a video): more of it for the same picture is less compression.
        val a = density(best)
        val b = density(other)
        if (a != null && b != null && a > b * 1.3) {
            val sameLength = !best.video || sameLength(best, other)
            out += Reason(true, "Meno compressa",
                (if (best.video && sameLength) "Stessa durata, " else "") + "${times(a / b, "dati per pixel")}: più dettagli.")
        } else if (a == null && best.bytes > other.bytes * 1.3) {
            out += Reason(false, "Più pesante", "${mb(best.bytes)} contro ${mb(other.bytes)}: di solito vuol dire meno compressa.")
        }
        if (best.video && best.durationMs != null && other.durationMs != null) {
            val d = best.durationMs - other.durationMs
            out += when {
                d > 1_000 -> Reason(true, "Più lunga", "${duration(best.durationMs)} contro ${duration(other.durationMs)}: l'altra è tagliata.")
                d < -1_000 -> Reason(false, "Più corta", "${duration(best.durationMs)} contro ${duration(other.durationMs)}.")
                else -> Reason(false, "Durata intera", "${duration(best.durationMs)}, come l'altra.")
            }
        }
        if (best.video && best.mp4 && other.video && !other.mp4) out += Reason(false, "MP4", "Si scorre e si riproduce ovunque.")
        if (best.favorite && !other.favorite) out += Reason(false, "Tra i preferiti", "L'altra non lo è.")
        if (best.hasLink && !other.hasLink) out += Reason(false, "Link di origine", "Sai da dove viene; l'altra no.")
        if (best.importedAt < other.importedAt) {
            out += Reason(false, "Importata prima", "${date(best.importedAt)}; l'altra il ${date(other.importedAt)}.")
        }
        return out.sortedByDescending { it.major }
    }

    /** "Full HD", "4K", "480p" for a video; "12,2 MP" for a photo. */
    fun label(f: Facts): String {
        if (f.pixels <= 0) return "?"
        if (!f.video) return String.format(Locale.ITALY, "%.1f MP", f.pixels / 1_000_000.0)
        val short = minOf(f.width!!, f.height!!)
        return when {
            short >= 2160 -> "4K"
            short >= 1440 -> "1440p"
            short >= 1080 -> "Full HD"
            short >= 720 -> "HD"
            else -> "${short}p"
        }
    }

    private fun sameLength(x: Facts, y: Facts): Boolean {
        val a = x.durationMs ?: return false
        val b = y.durationMs ?: return false
        return kotlin.math.abs(a - b) <= 1_000
    }

    private fun density(f: Facts): Double? {
        if (f.pixels <= 0 || f.bytes <= 0) return null
        val seconds = if (f.video) ((f.durationMs ?: return null) / 1000.0).coerceAtLeast(1.0) else 1.0
        return f.bytes / (f.pixels * seconds)
    }

    /** "5 volte i pixel" from 2x on, "+40% di pixel" below. */
    private fun times(ratio: Double, what: String): String =
        if (ratio >= 1.95) "${ratio.roundToInt()} volte $what"
        else "+${((ratio - 1) * 100).roundToInt()}% di ${what.removePrefix("i ")}"

    private fun mb(bytes: Long) = String.format(Locale.ITALY, "%.1f MB", bytes / 1_048_576.0)
}

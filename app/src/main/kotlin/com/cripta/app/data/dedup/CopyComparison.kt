package com.cripta.app.data.dedup

import com.cripta.app.R
import com.cripta.app.str

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
            out += Reason(true, str(R.string.cmp_sharper), str(R.string.cmp_sharper_detail, label(best), label(other), times(best.pixels.toDouble() / other.pixels, pixels = true)))
        } else if (best.pixels > 0 && best.pixels == other.pixels) {
            out += Reason(false, str(R.string.cmp_same_resolution), str(R.string.cmp_same_as_other, label(best)))
        }
        // Data per pixel (and per second for a video): more of it for the same picture is less compression.
        val a = density(best)
        val b = density(other)
        if (a != null && b != null && a > b * 1.3) {
            val sameLength = !best.video || sameLength(best, other)
            out += Reason(true, str(R.string.cmp_less_compressed),
                str(if (best.video && sameLength) R.string.cmp_less_compressed_same_length else R.string.cmp_less_compressed_detail, times(a / b, pixels = false)))
        } else if (a == null && best.bytes > other.bytes * 1.3) {
            out += Reason(false, str(R.string.cmp_heavier), str(R.string.cmp_heavier_detail, mb(best.bytes), mb(other.bytes)))
        }
        if (best.video && best.durationMs != null && other.durationMs != null) {
            val d = best.durationMs - other.durationMs
            out += when {
                d > 1_000 -> Reason(true, str(R.string.cmp_longer), str(R.string.cmp_longer_detail, duration(best.durationMs), duration(other.durationMs)))
                d < -1_000 -> Reason(false, str(R.string.cmp_shorter), str(R.string.cmp_shorter_detail, duration(best.durationMs), duration(other.durationMs)))
                else -> Reason(false, str(R.string.cmp_full_length), str(R.string.cmp_same_as_other, duration(best.durationMs)))
            }
        }
        if (best.video && best.mp4 && other.video && !other.mp4) out += Reason(false, "MP4", str(R.string.cmp_mp4_detail))
        if (best.favorite && !other.favorite) out += Reason(false, str(R.string.cmp_favorite), str(R.string.cmp_favorite_detail))
        if (best.hasLink && !other.hasLink) out += Reason(false, str(R.string.cmp_link), str(R.string.cmp_link_detail))
        if (best.importedAt < other.importedAt) {
            out += Reason(false, str(R.string.cmp_imported_first), str(R.string.cmp_imported_first_detail, date(best.importedAt), date(other.importedAt)))
        }
        return out.sortedByDescending { it.major }
    }

    /** "Full HD", "4K", "480p" for a video; "12,2 MP" for a photo. */
    fun label(f: Facts): String {
        if (f.pixels <= 0) return "?"
        if (!f.video) return String.format(com.cripta.app.Strings.locale, "%.1f MP", f.pixels / 1_000_000.0)
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
    private fun times(ratio: Double, pixels: Boolean): String =
        if (ratio >= 1.95) str(if (pixels) R.string.cmp_times_pixels else R.string.cmp_times_data, ratio.roundToInt())
        else str(if (pixels) R.string.cmp_more_pixels else R.string.cmp_more_data, ((ratio - 1) * 100).roundToInt())

    private fun mb(bytes: Long) = String.format(com.cripta.app.Strings.locale, "%.1f MB", bytes / 1_048_576.0)
}

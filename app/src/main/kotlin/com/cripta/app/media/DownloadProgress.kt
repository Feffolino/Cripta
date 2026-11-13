package com.cripta.app.media

/**
 * The overall progress of one yt-dlp download, read from its output lines.
 *
 * youtubedl-android hands every line to the callback, but its own percentage comes only from the
 * lines that also carry a numeric ETA: with fragments fetched in parallel yt-dlp prints
 * "ETA Unknown" for a while, so the bar stood still and then jumped. Its percentage is also
 * computed on a total that is re-estimated as fragments arrive, so it moves back and forth
 * (6.6 % → 6.1 %), and a video with separate audio restarts from 0 % for the audio track.
 *
 * Here every `[download] NN.N%` line counts, the streams of the chosen format ("137+140") share
 * one bar (the video takes most of it), and the value shown never goes back.
 */
internal class DownloadProgress {
    private var streams = 1
    private var current = -1
    private var shown = 0f

    /** The overall percentage (0..100) after [line]. */
    fun update(line: String): Int {
        FORMATS.find(line)?.let { m -> streams = m.groupValues[1].split('+').size.coerceAtLeast(1) }
        when {
            line.startsWith(DESTINATION) -> current = (current + 1).coerceAtMost(streams - 1)
            else -> PERCENT.find(line)?.let { m ->
                val pct = m.groupValues[1].toFloatOrNull()?.coerceIn(0f, 100f) ?: return@let
                val stream = current.coerceAtLeast(0)
                val done = (0 until stream).sumOf { weight(it).toDouble() }.toFloat()
                shown = maxOf(shown, (done + weight(stream) * pct / 100f) * 100f)
            }
        }
        return shown.toInt().coerceIn(0, 100)
    }

    /** Share of the bar of stream [i]: the video (first) takes most of it, the audio the rest. */
    private fun weight(i: Int): Float = when {
        streams == 1 -> 1f
        i == 0 -> VIDEO_SHARE
        else -> (1f - VIDEO_SHARE) / (streams - 1)
    }

    private companion object {
        const val VIDEO_SHARE = 0.9f
        const val DESTINATION = "[download] Destination:"
        val FORMATS = Regex("""^\[info] .*Downloading \d+ format\(s\): (\S+)""")
        val PERCENT = Regex("""^\[download]\s+(\d+(?:\.\d+)?)%""")
    }
}

package com.cripta.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressTest {
    private fun line(pct: String, eta: String = "00:09", frag: Int = 6) =
        "[download]  $pct% of ~  72.76MiB at    6.88MiB/s ETA $eta (frag $frag/64)"

    @Test fun linesWithoutANumericEtaMoveTheBarToo() {
        val p = DownloadProgress()
        p.update("[info] x36xhzz: Downloading 1 format(s): 480")
        p.update("[download] Destination: /cache/ytdl-1/v.mp4")
        // youtubedl-android ignores these: its pattern wants "ETA mm:ss".
        assertEquals(1, p.update(line("1.1", eta = "Unknown", frag = 1)))
        assertEquals(4, p.update(line("4.1", eta = "Unknown", frag = 2)))
        assertEquals(10, p.update(line("10.0")))
    }

    @Test fun aReEstimatedTotalNeverMovesTheBarBack() {
        val p = DownloadProgress()
        p.update("[download] Destination: /cache/ytdl-1/v.mp4")
        assertEquals(6, p.update(line("6.6")))
        assertEquals(6, p.update(line("6.1")))
        assertEquals(6, p.update(line("6.3")))
        assertEquals(7, p.update(line("7.4")))
    }

    @Test fun otherLinesKeepTheValue() {
        val p = DownloadProgress()
        p.update("[download] Destination: /cache/ytdl-1/v.mp4")
        p.update(line("42.0"))
        assertEquals(42, p.update("[hlsnative] Total fragments: 64"))
        assertEquals(42, p.update("WARNING: something"))
        assertEquals(100, p.update("[download] 100% of   72.80MiB in 00:00:12 at 5.95MiB/s"))
        assertEquals(100, p.update("[Merger] Merging formats into \"/cache/ytdl-1/v.mp4\""))
    }

    @Test fun videoAndAudioShareOneBarInsteadOfStartingAgain() {
        val p = DownloadProgress()
        p.update("[info] abc: Downloading 1 format(s): 137+140")
        p.update("[download] Destination: /cache/ytdl-1/v.f137.mp4")
        assertEquals(45, p.update("[download]  50.0% of   80.00MiB at  5.00MiB/s ETA 00:08"))
        assertEquals(90, p.update("[download] 100% of   80.00MiB in 00:00:16 at 5.00MiB/s"))
        p.update("[download] Destination: /cache/ytdl-1/v.f140.m4a")
        assertEquals(90, p.update("[download]   0.0% of    4.00MiB at  1.00MiB/s ETA 00:04"))
        assertEquals(95, p.update("[download]  50.0% of    4.00MiB at  1.00MiB/s ETA 00:02"))
        assertEquals(100, p.update("[download] 100% of    4.00MiB in 00:00:01 at 4.00MiB/s"))
    }

    @Test fun aLongReplayOnlyGoesUpAndEndsFull() {
        val p = DownloadProgress()
        p.update("[info] x: Downloading 1 format(s): 480")
        p.update("[download] Destination: /cache/ytdl-1/v.mp4")
        var last = 0
        // A noisy estimate, as yt-dlp prints it with fragments in parallel.
        for (i in 0..640) {
            val noisy = (i / 6.4 + if (i % 3 == 0) -0.8 else 0.4).coerceIn(0.0, 99.9)
            val now = p.update(line("%.1f".format(java.util.Locale.ROOT, noisy), frag = i / 10))
            assertTrue("went back at $i: $now < $last", now >= last)
            last = now
        }
        assertEquals(100, p.update("[download] 100% of   72.80MiB in 00:00:12 at 5.95MiB/s"))
    }
}

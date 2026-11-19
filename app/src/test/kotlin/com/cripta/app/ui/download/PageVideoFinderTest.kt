package com.cripta.app.ui.download

import com.cripta.app.data.DownloadJob
import com.cripta.app.data.DownloadPhase
import com.cripta.app.data.StreamSource
import com.cripta.app.media.DownloadRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageVideoFinderTest {
    @Test
    fun `a request for a playlist or a video file is a stream, whatever follows the name`() {
        assertEquals("HLS", streamKind("https://cdn.example.com/v/master.m3u8?token=abc.mp3"))
        assertEquals("HLS", streamKind("https://cdn.example.com/v/INDEX.M3U8#t=1"))
        assertEquals("DASH", streamKind("https://cdn.example.com/v/manifest.mpd"))
        assertEquals("MP4", streamKind("https://cdn.example.com/v/film.mp4?x=1"))
    }

    @Test
    fun `pieces of a stream, pages and scripts are not streams`() {
        assertNull(streamKind("https://cdn.example.com/v/seg-12.ts"))
        assertNull(streamKind("https://cdn.example.com/v/seg-12.m4s"))
        assertNull(streamKind("https://example.com/watch?file=video.m3u8"))
        assertNull(streamKind("https://example.com/player.js"))
        assertNull(streamKind("https://example.m3u8/"))
    }

    @Test
    fun `playlists are listed before loose video files`() {
        assertTrue(streamRank("HLS") < streamRank("DASH"))
        assertTrue(streamRank("DASH") < streamRank("MP4"))
    }

    @Test
    fun `only the headers a site checks reach the downloader, on one line`() {
        val h = StreamSource.cleanHeaders(mapOf(
            "referer" to "https://example.com/watch\r\n--exec: rm",
            "USER-AGENT" to " Mozilla/5.0 ",
            "Accept" to "*/*",
            "X-Requested-With" to "com.cripta.app",
            "Cookie" to "",
        ))
        assertEquals(mapOf("Referer" to "https://example.com/watch--exec: rm", "User-Agent" to "Mozilla/5.0"), h)
    }

    @Test
    fun `a link the downloader failed on can be searched in its page, a stream or a running link cannot`() {
        val failed = DownloadJob("1", "https://example.com/watch", phase = DownloadPhase.FAILED)
        assertTrue(canFindInPage(failed))
        // Already a stream from a page: its page was searched, retrying is all there is.
        assertFalse(canFindInPage(failed.copy(stream = StreamSource("https://cdn.example.com/master.m3u8"))))
        assertFalse(canFindInPage(failed.copy(phase = DownloadPhase.DOWNLOADING)))
        assertFalse(canFindInPage(failed.copy(phase = DownloadPhase.CANCELLED)))
        assertFalse(canFindInPage(failed.copy(phase = DownloadPhase.DONE)))
    }

    @Test
    fun `a stream's row says its picture height and length, whichever is known`() {
        assertEquals("1080p · 42:10", streamBrief(1080, "42:10"))
        assertEquals("720p", streamBrief(720, null))
        assertEquals("1:05:00", streamBrief(0, "1:05:00"))
        // A bare media playlist or a live stream: no picture size and no length to tell.
        assertNull(streamBrief(0, null))
    }

    @Test
    fun `the page sees a browser, not a WebView`() {
        val webView = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36"
        val browser = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        assertEquals(browser, browserUserAgent(webView))
        // A browser's own agent stays as it is.
        assertEquals(browser, browserUserAgent(browser))
    }

    @Test
    fun `a stream's headers are each the value of their own downloader option`() {
        val cmd = DownloadRequests.common("https://cdn.example.com/master.m3u8", mapOf("Referer" to "https://example.com/")).buildCommand()
        val i = cmd.indexOf("--add-header")
        assertTrue(i >= 0)
        assertEquals("Referer:https://example.com/", cmd[i + 1])
    }
}

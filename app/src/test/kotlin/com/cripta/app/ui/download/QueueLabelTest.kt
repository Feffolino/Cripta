package com.cripta.app.ui.download

import com.cripta.app.data.DownloadJob
import com.cripta.app.data.DownloadPhase
import com.cripta.app.data.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueLabelTest {
    private fun label(url: String, vararg others: String) = linkLabel(url, listOf(url) + others)

    @Test fun linksOfOneSiteShowTheirOwnSlugFirst() {
        val a = "https://www.site.com/video/123/first-title"
        val b = "https://www.site.com/video/456/second-title"
        assertEquals("first-title · site.com", label(a, b))
        assertEquals("second-title · site.com", label(b, a))
    }

    @Test fun aGenericStreamNameGivesWayToTheSegmentBeforeIt() {
        assertEquals("abc123 · cdn.x.com", label("https://cdn.x.com/hls/abc123/master.m3u8", "https://cdn.x.com/hls/def456/master.m3u8"))
        assertEquals("my-clip · s.com", label("https://s.com/videos/my-clip.html"))
    }

    @Test fun aPlayerPageIsToldByItsQuery() {
        val a = "https://www.youtube.com/watch?v=AAA111&utm_source=share"
        val b = "https://www.youtube.com/watch?v=BBB222"
        assertEquals("v=AAA111 · youtube.com", label(a, b))
        assertEquals("v=BBB222 · youtube.com", label(b, a))
        assertEquals("id=7 · s.com", label("https://s.com/watch.php?id=7", "https://s.com/watch.php?id=8"))
    }

    @Test fun theSameSlugTakesTheQueryThenThePath() {
        assertEquals("trailer?part=1 · s.com", label("https://s.com/trailer?part=1", "https://s.com/trailer?part=2"))
        assertEquals("user1/clip-final · s.com", label("https://s.com/user1/clip-final", "https://s.com/user2/clip-final"))
    }

    @Test fun theSameSlugOnAnotherSiteNeedsNoMore() {
        assertEquals("trailer · a.com", label("https://a.com/x/trailer", "https://b.com/y/trailer"))
    }

    @Test fun trackingAloneDoesNotTellLinksApartUntilNothingElseDoes() {
        assertEquals("my-video · s.com", label("https://s.com/my-video?utm_source=x"))
        assertEquals("my-video?fbclid=1 · s.com", label("https://s.com/my-video?fbclid=1", "https://s.com/my-video?fbclid=2"))
    }

    @Test fun aLinkWithNothingAfterItsSiteIsTheSite() {
        assertEquals("site.com", label("https://www.site.com/"))
    }

    @Test fun escapesAreDecodedAndTextThatIsNotALinkStays() {
        assertEquals("caffè-video · s.com", label("https://s.com/caff%C3%A8-video"))
        assertEquals("a%zz|b · s.com", label("https://s.com/a%zz|b"))
        assertEquals("not a link", label("  not a link "))
    }

    @Test fun theSameLinkTwiceIsNotItsOwnRival() {
        assertEquals("trailer · s.com", label("https://s.com/trailer", "https://s.com/trailer"))
    }

    private fun job(url: String, phase: DownloadPhase = DownloadPhase.QUEUED, title: String? = null, message: String? = null, stream: StreamSource? = null) =
        DownloadJob(id = url, url = url, phase = phase, title = title, message = message, stream = stream)

    @Test fun aKnownTitleNamesTheEntry() {
        val a = job("https://s.com/v/1", title = "Il mio video")
        assertEquals("Il mio video", queueLabel(a, listOf(a)))
        val b = job("https://s.com/v/2", stream = StreamSource("https://cdn.s.com/master.m3u8", title = "Titolo della pagina"))
        assertEquals("Titolo della pagina", queueLabel(b, listOf(a, b)))
    }

    @Test fun aSavedFileNamesADoneEntryButAFailureDoesNot() {
        val done = job("https://s.com/v/1", DownloadPhase.DONE, title = "Titolo", message = "Nome file")
        assertEquals("Nome file", queueLabel(done, listOf(done)))
        val failed = job("https://s.com/v/2", DownloadPhase.FAILED, message = "Link non supportato")
        assertEquals("2 · s.com", queueLabel(failed, listOf(done, failed)))
    }

    @Test fun aGenericTitleIsNoTitle() {
        val a = job("https://cdn.s.com/abc/master.m3u8", title = "master")
        assertEquals("abc · cdn.s.com", queueLabel(a, listOf(a)))
    }
}

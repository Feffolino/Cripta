package com.cripta.app.ui.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUrlTest {
    @Test
    fun aClosingBracketOfThePathStays() {
        assertEquals("https://example.com/video_(final)", extractUrl("https://example.com/video_(final)"))
        assertEquals("https://example.com/video_(final)", extractUrl("look (https://example.com/video_(final))."))
        assertEquals("https://example.com/a", extractUrl("[link](https://example.com/a)"))
    }

    @Test
    fun punctuationAfterTheLinkGoes() {
        assertEquals("https://example.com/a", extractUrl("Title https://example.com/a, more"))
        assertEquals("https://example.com/a", extractUrl("'https://example.com/a'"))
        assertEquals("https://example.com/a", extractUrl("see https://example.com/a!"))
    }

    @Test
    fun anApostropheInsideThePathStays() {
        assertEquals("https://example.com/l'amico", extractUrl("https://example.com/l'amico"))
    }

    @Test
    fun textWithoutALinkIsReturnedTrimmed() {
        assertEquals("not a link", extractUrl("  not a link "))
    }

    @Test
    fun aUnicodeDomainIsAValidLink() {
        assertTrue(looksLikeUrl("https://例子.中国/video.mp4"))
        assertTrue(looksLikeUrl("https://xn--fsqu00a.xn--fiqs8s/video.mp4"))
        assertTrue(looksLikeUrl("https://user@例子.中国:8080/v"))
    }

    // Regression: java.net.URI refuses these, yet yt-dlp fetches such links.
    @Test
    fun aLinkWithCharactersJavaUriRefusesIsStillALink() {
        assertTrue(looksLikeUrl("https://example.com/watch?v=a|b"))
        assertTrue(looksLikeUrl("https://example.com/p?q={x}&r=^"))
        assertTrue(looksLikeUrl("https://example.com/a%zz"))
        assertTrue(looksLikeUrl("https://my_host.example.com/v"))
        assertTrue(looksLikeUrl("https://[2001:db8::1]:8080/v"))
    }

    @Test
    fun notALinkStaysInvalid() {
        assertFalse(looksLikeUrl("ftp://example.com/a"))
        assertFalse(looksLikeUrl("https://"))
        assertFalse(looksLikeUrl("hello world"))
        assertFalse(looksLikeUrl("https:///path"))
        assertFalse(looksLikeUrl("https://user@/path"))
        assertFalse(looksLikeUrl("-o https://example.com"))
    }

    @Test
    fun everyLinkOfATextGoesOnALineOfItsOwn() {
        assertEquals("https://a.com/1\nhttps://b.com/2",
            extractUrls("vedi https://a.com/1, poi (https://b.com/2). E di nuovo https://a.com/1!"))
        assertEquals("https://a.com/1", extractUrls("Title https://a.com/1"))
        assertEquals("not a link", extractUrls("  not a link "))
    }

    @Test
    fun severalLinksAreAListOnlyWhenEveryLineIsALink() {
        assertEquals(listOf("https://a.com/1", "https://b.com/2"), linksOf("https://a.com/1\n\n https://b.com/2 \n"))
        assertEquals(emptyList<String>(), linksOf("https://a.com/1"))
        assertEquals(emptyList<String>(), linksOf("https://a.com/1\nnot a link"))
    }
}

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
        assertEquals("https://xn--fsqu00a.xn--fiqs8s/video.mp4", asciiHost("https://例子.中国/video.mp4"))
    }

    @Test
    fun notALinkStaysInvalid() {
        assertFalse(looksLikeUrl("ftp://example.com/a"))
        assertFalse(looksLikeUrl("https://"))
        assertFalse(looksLikeUrl("hello world"))
    }
}

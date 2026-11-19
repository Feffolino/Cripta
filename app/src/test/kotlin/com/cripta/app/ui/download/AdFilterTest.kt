package com.cripta.app.ui.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFilterTest {
    private fun filter(vararg rules: String) = AdFilter.parse(rules.asSequence())

    @Test
    fun `a domain rule blocks that domain and its subdomains, not a name that only contains it`() {
        val f = filter("||ads.example.com^")
        assertTrue(f.blocks("https://ads.example.com/vast?zone=1"))
        assertTrue(f.blocks("https://cdn.ads.example.com/clip.mp4"))
        assertFalse(f.blocks("https://notads.example.com/clip.mp4"))
        assertFalse(f.blocks("https://example.com/?next=ads.example.com"))
        assertFalse(f.blocks("https://ads.example.community/"))
    }

    @Test
    fun `path rules, wildcards and separators match anywhere in the address`() {
        val f = filter("/banner/*/img^", "ad*server")
        assertTrue(f.blocks("https://site.com/banner/top/img?x=1"))
        assertTrue(f.blocks("https://site.com/banner/top/img"))
        assertFalse(f.blocks("https://site.com/banner/top/imgs"))
        assertTrue(f.blocks("https://site.com/adXYZserver.js"))
    }

    @Test
    fun `a request of the site itself is blocked when its path is an advertisement's`() {
        val f = filter("/_xa/ads_batch")
        assertTrue(f.blocks("https://www.tube.example/_xa/ads_batch?data=1"))
        assertFalse(f.blocks("https://www.tube.example/video/get_media?id=1"))
    }

    @Test
    fun `an exception lets a blocked address through`() {
        val f = filter("||ads.example.com^", "@@||ads.example.com/player/core.js")
        assertTrue(f.blocks("https://ads.example.com/track.gif"))
        assertFalse(f.blocks("https://ads.example.com/player/core.js"))
    }

    @Test
    fun `cosmetic rules, comments and rules for a few sites block nothing`() {
        val f = filter("! comment", "[Adblock Plus 2.0]", "example.com##.ad-box", "||cdn.example.com^\$domain=other.com",
            "||popup.example.com^\$popup", "ab")
        assertFalse(f.blocks("https://example.com/ad-box"))
        assertFalse(f.blocks("https://cdn.example.com/video.m3u8"))
        assertFalse(f.blocks("https://popup.example.com/"))
        assertFalse(f.blocks("https://ab.example.com/ab"))
    }

    @Test
    fun `other options do not stop a rule, and case does not matter`() {
        val f = filter("||tracker.example.net^\$third-party,xmlhttprequest", "|https://Pixel.")
        assertTrue(f.blocks("https://TRACKER.example.net/collect"))
        assertTrue(f.blocks("https://pixel.example.org/p.gif"))
        assertFalse(f.blocks("http://pixel.example.org/p.gif"))
    }

    @Test
    fun `regular expression rules keep their own dollar sign`() {
        val f = filter("/\\/ad[0-9]+\\.js\$/", "/\\/promo-[a-z]+\\//\$script")
        assertTrue(f.blocks("https://site.com/ad123.js"))
        assertFalse(f.blocks("https://site.com/ad123.json"))
        assertTrue(f.blocks("https://site.com/promo-summer/a.js"))
    }

    @Test
    fun `an ordinary page loads untouched`() {
        val f = filter("||ads.example.com^", "/banner/*/img^", "-ad-unit-")
        assertFalse(f.blocks("https://www.example.org/watch?v=abc"))
        assertFalse(f.blocks("https://cdn.example.org/hls/master.m3u8?token=x"))
    }

    @Test
    fun `a saved list is good for four days, then fetched again`() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(EasyList.fresh(null, 10 * day))
        assertTrue(EasyList.fresh(10 * day, 10 * day))
        assertTrue(EasyList.fresh(10 * day, 13 * day))
        assertFalse(EasyList.fresh(10 * day, 14 * day))
        assertFalse(EasyList.fresh(10 * day, 9 * day)) // a clock moved back: fetch again
    }
}

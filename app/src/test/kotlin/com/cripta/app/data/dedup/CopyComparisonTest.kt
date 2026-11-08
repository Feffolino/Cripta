package com.cripta.app.data.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.app.Application
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// The texts compared here are the Italian ones (res/values-it).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "it")
class CopyComparisonTest {
    @Before
    fun texts() = com.cripta.app.Strings.init(RuntimeEnvironment.getApplication())

    private fun video(w: Int, h: Int, mb: Double, sec: Int, at: Long = 0, mp4: Boolean = true) =
        CopyComparison.Facts(w, h, (mb * 1_048_576).toLong(), sec * 1000L, at, video = true, mp4 = mp4, favorite = false, hasLink = false)

    private fun photo(w: Int?, h: Int?, mb: Double, at: Long = 0) =
        CopyComparison.Facts(w, h, (mb * 1_048_576).toLong(), null, at, video = false, mp4 = false, favorite = false, hasLink = false)

    private fun reasons(a: CopyComparison.Facts, b: CopyComparison.Facts) =
        CopyComparison.reasons(a, b, { "d$it" }, { "${it / 1000}s" })

    @Test
    fun sharperAndLessCompressedVideoComeFirst() {
        // A WhatsApp copy: 480p and far fewer bytes per pixel and second, imported later.
        val r = reasons(video(1920, 1080, 43.0, 42, at = 1), video(848, 480, 4.0, 42, at = 2))
        assertEquals(listOf("Più nitida", "Meno compressa"), r.filter { it.major }.map { it.title })
        assertEquals("Full HD contro 480p: 5 volte i pixel.", r.first().detail)
        assertTrue(r.any { it.title == "Durata intera" && !it.major })
        assertTrue(r.any { it.title == "Importata prima" && it.detail == "d1; l'altra il d2." })
    }

    @Test
    fun aCutCopyLosesOnLength() {
        val r = reasons(video(1280, 720, 20.0, 60), video(1280, 720, 10.0, 30))
        assertTrue(r.any { it.major && it.title == "Più lunga" && it.detail == "60s contro 30s: l'altra è tagliata." })
        assertTrue(r.any { it.title == "Stessa risoluzione" && !it.major })
        // Same bytes per pixel and second: no compression reason.
        assertFalse(r.any { it.title == "Meno compressa" })
    }

    @Test
    fun photosSpeakInMegapixelsAndSmallGainsInPercent() {
        val r = reasons(photo(4032, 3024, 4.1), photo(3400, 2550, 3.0))
        assertEquals("12,2 MP contro 8,7 MP: +41% di pixel.", r.first { it.title == "Più nitida" }.detail)
    }

    @Test
    fun withoutSizesOnlyTheWeightIsSaid() {
        val r = reasons(photo(null, null, 4.0), photo(null, null, 1.0))
        assertEquals(listOf("Più pesante"), r.map { it.title })
        assertFalse(r.single().major)
    }

    @Test
    fun videoLabels() {
        assertEquals("4K", CopyComparison.label(video(3840, 2160, 1.0, 1)))
        assertEquals("Full HD", CopyComparison.label(video(1080, 1920, 1.0, 1)))
        assertEquals("HD", CopyComparison.label(video(1280, 720, 1.0, 1)))
        assertEquals("360p", CopyComparison.label(video(640, 360, 1.0, 1)))
    }
}

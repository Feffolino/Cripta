package com.cripta.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPaletteTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    private val darkPage = Color(0xFF12151C)
    private val lightPage = Color.White

    @Test
    fun `the palette has twelve distinct colours, the accent hue first`() {
        assertEquals(12, TagPalette.size)
        assertEquals(12, TagPalette.map { it.toArgb() }.toSet().size)
        val blue = Hsv.of(TagPalette.first().toArgb()).h
        assertTrue("first is blue: $blue", blue in 210f..240f)
    }

    @Test
    fun `black reads on every preset at 13 to 1`() {
        for (c in TagPalette) assertTrue("$c black", contrast(c, Color.Black) >= 13.0)
    }

    @Test
    fun `readableOn gives 4_5 to 1 on both pages for presets and for extreme own colours`() {
        val own = listOf(Color.Black, Color.White, Color(0xFFFFFF00), Color(0xFF000080), Color(0xFF808080))
        for (c in TagPalette + own) {
            for (page in listOf(darkPage, lightPage)) {
                val tint = c.copy(alpha = 0.35f).compositeOverOpaque(page)
                assertTrue("$c on $page", contrast(readableOn(c, tint), tint) >= 4.5)
            }
            for (fill in listOf(c)) assertTrue("$c ink", contrast(readableOn(Color.White, fill), fill) >= 4.5)
        }
    }

    @Test
    fun `on the light theme a pastel takes a fuller tone that stands out and keeps black text`() {
        val lightPageBg = Color(0xFFF7F8FA)
        for (c in TagPalette) {
            val shown = shownOnLightTheme(c)
            assertTrue("$c page", contrast(shown, lightPageBg) >= 2.3)
            assertTrue("$c black", contrast(shown, Color.Black) >= 7.0)
            assertEquals("$c hue", Hsv.of(c.toArgb()).h, Hsv.of(shown.toArgb()).h, 12f)
        }
        val dark = Color(0xFF1E3A8A)
        assertEquals(dark, shownOnLightTheme(dark))
        val grey = Hsv.of(shownOnLightTheme(Color(0xFFE5E7EB)).toArgb())
        assertTrue("grey stays grey: ${grey.s}", grey.s < 0.12f)
    }

    @Test
    fun `hex codes round trip and bad ones are refused`() {
        assertEquals(0xFF6D9DF5.toInt(), parseColorHex("#6D9DF5"))
        assertEquals(0xFF6D9DF5.toInt(), parseColorHex(" 6d9df5 "))
        assertEquals(0xFFAABBCC.toInt(), parseColorHex("#abc"))
        assertEquals("#6D9DF5", colorHex(0xFF6D9DF5.toInt()))
        assertEquals("#0000FF", colorHex(0x800000FF.toInt()))
        for (bad in listOf("", "#", "12345", "#12345G", "1234567", "#ff")) assertNull(bad, parseColorHex(bad))
        assertNotNull(parseColorHex("#000000"))
    }

    @Test
    fun `HSV conversion is stable for every preset and the primaries`() {
        for (argb in TagPalette.map { it.toArgb() } + listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())) {
            assertEquals(colorHex(argb), colorHex(Hsv.of(argb).toArgb()))
        }
        assertEquals(120f, Hsv.of(0xFF00FF00.toInt()).h, 0.01f)
        assertEquals(1f, Hsv.of(0xFF00FF00.toInt()).s, 0.001f)
    }

    private fun Color.compositeOverOpaque(bg: Color): Color =
        Color(red * alpha + bg.red * (1 - alpha), green * alpha + bg.green * (1 - alpha), blue * alpha + bg.blue * (1 - alpha))
}

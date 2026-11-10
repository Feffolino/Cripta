package com.cripta.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class TagTonesTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    private fun hueGap(a: Double, b: Double): Double {
        val d = abs(a - b) % (2 * PI)
        return minOf(d, 2 * PI - d)
    }

    private val darkPage = Color(0xFF12151C)
    private val darkBg = Color(0xFF0A0C10)
    private val lightPage = Color(0xFFFFFFFF)
    private val lightBg = Color(0xFFF7F8FA)

    /** Colours of the user's own, from a pure hue to a grey. */
    private val own = listOf(Color(0xFFFF0000), Color(0xFF00C853), Color(0xFF0000FF), Color(0xFFFFEB3B),
        Color(0xFF6D4C41), Color(0xFF808080), Color.Black, Color.White)

    @Test
    fun `OKLCH round trips the presets`() {
        for (c in TagPalette) {
            val (l, ch, h) = oklch(c).toList()
            val back = oklchColor(l, ch, h).toArgb()
            // One step of rounding per channel at most.
            for (s in listOf(16, 8, 0)) assertTrue("$c", abs(((back shr s) and 0xFF) - ((c.toArgb() shr s) and 0xFF)) <= 1)
            assertEquals("$c is a pastel", 0.86, l, 0.01)
        }
    }

    @Test
    fun `tones sit at the agreed lightness`() {
        val blue = TagPalette.first()
        val dark = tagTones(blue, dark = true)
        assertEquals(0.32, oklch(dark.container)[0], 0.005)
        assertEquals(0.88, oklch(dark.onContainer)[0], 0.005)
        assertEquals(0.80, oklch(dark.mark)[0], 0.005)
        assertEquals(MarkInkDark, dark.onMark)
        val light = tagTones(blue, dark = false)
        assertEquals(0.90, oklch(light.container)[0], 0.005)
        assertEquals(0.40, oklch(light.onContainer)[0], 0.005)
        assertEquals(0.55, oklch(light.mark)[0], 0.005)
        assertEquals(Color.White, light.onMark)
        assertEquals(blue, dark.pastel)
        assertEquals(blue, light.pastel)
    }

    @Test
    fun `text on the container reads well on both themes`() {
        for (c in TagPalette + own) {
            val d = tagTones(c, dark = true)
            val l = tagTones(c, dark = false)
            assertTrue("$c dark ${contrast(d.onContainer, d.container)}", contrast(d.onContainer, d.container) >= 7.0)
            assertTrue("$c light ${contrast(l.onContainer, l.container)}", contrast(l.onContainer, l.container) >= 6.0)
        }
    }

    @Test
    fun `a tick on a mark reads at 4_5 to 1 and the mark stands out from the page`() {
        for (c in TagPalette + own) {
            val d = tagTones(c, dark = true)
            val l = tagTones(c, dark = false)
            assertTrue("$c dark ink", contrast(d.onMark, d.mark) >= 4.5)
            assertTrue("$c light ink ${contrast(l.onMark, l.mark)}", contrast(l.onMark, l.mark) >= 4.5)
            assertTrue("$c dark page", contrast(d.mark, darkPage) >= 3.0)
            assertTrue("$c light page", contrast(l.mark, lightPage) >= 3.0)
        }
    }

    @Test
    fun `the container is a quiet ground, not a pastel on the dark page`() {
        for (c in TagPalette) {
            val d = tagTones(c, dark = true)
            assertTrue("$c", contrast(d.container, darkBg) < 2.0)
            val l = tagTones(c, dark = false)
            assertTrue("$c", contrast(l.container, lightBg) < 1.4)
        }
    }

    @Test
    fun `every tone keeps the hue of the colour`() {
        for (c in TagPalette.dropLast(1)) {
            val h = oklch(c)[2]
            val t = tagTones(c, dark = true)
            for (tone in listOf(t.container, t.onContainer, t.mark)) assertTrue("$c $tone", hueGap(h, oklch(tone)[2]) < 0.12)
        }
    }

    @Test
    fun `the slate keeps a third of the chroma and a grey stays grey`() {
        val slate = TagPalette.last()
        val blue = TagPalette.first()
        val sMark = oklch(tagTones(slate, dark = true).mark)[1]
        val bMark = oklch(tagTones(blue, dark = true).mark)[1]
        assertEquals(0.11 * 0.35, sMark, 0.005)
        // The blue at L 0.80 is trimmed a little to stay in sRGB.
        assertTrue("$bMark", bMark in 0.09..0.111)
        val pink = oklch(tagTones(TagPalette[3], dark = true).mark)[1]
        assertEquals(0.11, pink, 0.005)
        for (grey in listOf(Color(0xFF808080), Color.Black, Color.White)) {
            val t = tagTones(grey, dark = false)
            assertTrue("$grey", oklch(t.mark)[1] < 0.01)
            assertTrue("$grey", oklch(t.container)[1] < 0.01)
        }
    }

    @Test
    fun `a colour of its own takes its share of chroma, at most a preset's`() {
        // A faint custom colour: tones as faint as it is against a preset.
        val faint = oklchColor(0.6, 0.05, 1.0)
        assertEquals(0.5, toneChromaShare(faint), 0.03)
        assertEquals(1.0, toneChromaShare(Color(0xFFFF0000)), 0.0)
        // A former preset counts as the preset that replaced it.
        assertEquals(0.35, toneChromaShare(Color(0xFF8A939F)), 0.0)
    }

    @Test
    fun `covers get the pastel, black text on it`() {
        for (c in TagPalette) assertEquals(c, tagPastel(c))
        for (c in own) {
            val p = tagPastel(c)
            assertEquals("$c", 0.86, oklch(p)[0], 0.01)
            assertTrue("$c", contrast(p, Color.Black) >= 12.0)
        }
        assertEquals(Color(0xFFB9D2FF), tagPastel(Color(0xFF90B8FE)))
    }

    @Test
    fun `tag colours off take the accent's quiet tones`() {
        val t = tagOffTones(dark = true, primary = Primary, onPrimary = OnPrimary)
        assertEquals(Color(0xFF1E2A3D), t.container)
        assertEquals(Primary, t.onContainer)
        assertEquals(TagOffPastel, t.pastel)
        assertTrue(contrast(t.onContainer, t.container) >= 4.5)
        val l = tagOffTones(dark = false, primary = LightPrimary, onPrimary = Color.White)
        assertEquals(Color(0xFFD6E4FF), l.container)
        assertTrue(contrast(l.onContainer, l.container) >= 4.5)
    }
}

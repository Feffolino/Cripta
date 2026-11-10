package com.cripta.app.ui.vault

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.cripta.app.ui.theme.TagPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMarkFillTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    @Test
    fun `a mark keeps the tag's own colour`() {
        val blue = Color(0xFF3F6FD8)
        assertEquals(blue, coverMarkFill(blue))
        assertEquals(1f, coverMarkFill(Color(0x80FF0000)).alpha, 0f)
    }

    @Test
    fun `text on a mark reads at 4_5 to 1 on presets and on any colour of the user's own`() {
        val own = listOf(Color.Black, Color.White, Color(0xFFFFF59D), Color(0xFF000080), Color(0xFF808080), Color(0xFF3F6FD8))
        for (c in TagPalette + own) {
            val fill = coverMarkFill(c)
            assertTrue("$c", contrast(coverMarkInk(fill), fill) >= 4.5)
        }
        for (c in TagPalette) assertEquals(Color.Black, coverMarkInk(coverMarkFill(c)))
    }
}

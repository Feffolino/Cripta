package com.cripta.app.ui.vault

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMarkFillTest {
    private fun whiteContrast(bg: Color) = 1.05 / (bg.luminance() + 0.05)

    @Test
    fun `a mid colour gets exactly 28 percent black`() {
        val blue = Color(0xFF3F6FD8)
        val fill = coverMarkFill(blue)
        val expected = lerp(blue, Color.Black, 0.28f)
        assertEquals(expected.red, fill.red, 1e-3f)
        assertEquals(expected.green, fill.green, 1e-3f)
        assertEquals(expected.blue, fill.blue, 1e-3f)
        assertEquals(1f, fill.alpha, 0f)
    }

    @Test
    fun `a light colour is darkened until white reads at 3 to 1`() {
        for (light in listOf(Color(0xFFFFF59D), Color(0xFFFFFFFF), Color(0xFFB2EBF2), Color(0xFFFFCC80))) {
            assertTrue("$light", whiteContrast(coverMarkFill(light)) >= 3.0)
        }
    }

    @Test
    fun `a see-through colour comes out opaque`() {
        assertEquals(1f, coverMarkFill(Color(0x80FF0000)).alpha, 0f)
    }
}

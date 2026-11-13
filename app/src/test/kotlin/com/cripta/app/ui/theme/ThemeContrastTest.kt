package com.cripta.app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The light theme's accent text and favourite star stay readable where they are drawn. */
class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    @Test
    fun lightAccentTextReadsOnDialogsAndSheets() {
        // White, surfaceContainerLow, surfaceVariant, surfaceContainerHigh, surfaceContainerHighest.
        for (bg in listOf(0xFFFFFFFF, 0xFFEFF2F6, 0xFFE9EDF3, 0xFFE2E7EF, 0xFFDBE1EA)) {
            val c = contrast(LightPrimary, Color(bg))
            assertTrue("accent on ${bg.toString(16)}: $c", c >= 4.5)
        }
    }

    @Test
    fun theFavouriteStarIsSeenOnLightPages() {
        val light = lightColorScheme(background = LightBg)
        for (bg in listOf(Color.White, LightBg, Color(0xFFE2E7EF))) {
            val c = contrast(light.favorite, bg)
            assertTrue("star on $bg: $c", c >= 3.0)
        }
        // The dark theme and the pictures keep the gold.
        assertEquals(Favorite, darkColorScheme(background = Bg).favorite)
    }
}

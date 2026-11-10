package com.cripta.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A colour as hue (0..360), saturation and brightness (0..1): what the custom colour picker's sliders edit. */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    /** Opaque ARGB of this colour. */
    fun toArgb(): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun toColor(): Color = Color(toArgb())

    companion object {
        fun of(argb: Int): Hsv {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val d = mx - mn
            val h = when {
                d == 0f -> 0f
                mx == r -> 60f * (((g - b) / d) % 6f)
                mx == g -> 60f * ((b - r) / d + 2f)
                else -> 60f * ((r - g) / d + 4f)
            }
            return Hsv((h + 360f) % 360f, if (mx == 0f) 0f else d / mx, mx)
        }
    }
}

/** "#RRGGBB" of [argb] (alpha dropped), upper case. */
fun colorHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/**
 * The opaque ARGB of "RRGGBB" or "#RRGGBB" (also the short "RGB"), or null when [text] is not a
 * colour (yet): the picker's code field says so while the user is still typing.
 */
fun parseColorHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    val full = when (t.length) {
        3 -> t.map { "$it$it" }.joinToString("")
        6 -> t
        else -> return null
    }
    if (!full.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return (0xFF shl 24) or full.toInt(16)
}

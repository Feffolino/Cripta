package com.cripta.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * The tones of one tag or folder colour. A tag keeps the one colour the user saved (a plain ARGB
 * value); the app takes its OKLCH hue and draws the tone that suits the place:
 * - [container]: the ground of a tag pill, of an empty folder and the veil on the pictures that
 *   peek out behind a folder's cover. Dark L 0.32 / light L 0.90, chroma 0.06.
 * - [onContainer]: text and icons on [container]. Dark L 0.88 / light L 0.40 (about 8.8:1 / 6.8:1).
 * - [mark]: a small sign of the colour on a page (the dot of an Off pill, a colour swatch, the dot
 *   of a folder without an emoji). Dark L 0.80 / light L 0.55, with [onMark] (near black / white)
 *   for a tick or a letter on it.
 * - [pastel]: the saved preset itself (OKLCH L 0.86), used only on covers (the marks with an
 *   acronym, the full pills) with black [onPastel] text.
 */
@Immutable
data class TagTones(
    val container: Color,
    val onContainer: Color,
    val mark: Color,
    val onMark: Color,
    val pastel: Color,
    val onPastel: Color = Color.Black,
)

/** Ink on a [TagTones.mark] of the dark theme: the app's own near black. */
val MarkInkDark = Color(0xFF0A0C10)

/** Fill of every cover mark while "Etichette colorate" is off: the blue preset. */
val TagOffPastel = Color(0xFFB9D2FF)

private const val PRESET_L = 0.86
private const val PRESET_C = 0.10
/** The slate preset is a tinted grey: its tones keep only this share of the chroma. */
private const val SLATE_CHROMA = 0.35

/** OKLCH of [color]: lightness 0..1, chroma, hue in radians. */
internal fun oklch(color: Color): DoubleArray {
    fun lin(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    val r = lin(color.red); val g = lin(color.green); val b = lin(color.blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return doubleArrayOf(okL, hypot(okA, okB), atan2(okB, okA))
}

/** The sRGB colour of OKLCH ([lightness], [chroma], [hue] in radians), chroma trimmed until sRGB holds it. */
internal fun oklchColor(lightness: Double, chroma: Double, hue: Double): Color {
    fun enc(c: Double): Float = (if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055).toFloat().coerceIn(0f, 1f)
    var c = chroma.coerceAtLeast(0.0)
    while (true) {
        val a = c * cos(hue); val b = c * sin(hue)
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val lr = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        if (listOf(lr, lg, lb).all { it in -1e-4..1.0001 } || c <= 0.0) {
            return Color(enc(lr.coerceIn(0.0, 1.0)), enc(lg.coerceIn(0.0, 1.0)), enc(lb.coerceIn(0.0, 1.0)))
        }
        c = (c - 0.002).coerceAtLeast(0.0)
    }
}

/**
 * How much chroma the tones of [color] take: all of it for a preset (a third of it for the slate,
 * a tinted grey), for a colour of the user's own as much as it has against a preset (0.10), so a
 * grey stays grey and a faint colour stays faint.
 */
internal fun toneChromaShare(color: Color): Double {
    val argb = currentPresetColor(color.copy(alpha = 1f).toArgb())
    val index = TagPalette.indexOfFirst { it.toArgb() == argb }
    return when {
        index == TagPalette.lastIndex -> SLATE_CHROMA
        index >= 0 -> 1.0
        else -> (oklch(color)[1] / PRESET_C).coerceIn(0.0, 1.0)
    }
}

/** The pastel of [color] for covers: a preset as saved, a colour of the user's own at the presets' L 0.86. */
fun tagPastel(color: Color): Color {
    val argb = currentPresetColor(color.copy(alpha = 1f).toArgb())
    if (TagPalette.any { it.toArgb() == argb }) return Color(argb)
    val hue = oklch(color)[2]
    return oklchColor(PRESET_L, PRESET_C * toneChromaShare(color), hue)
}

/** Every tone of [color] on the [dark] or the light theme (see [TagTones]). Pure: no theme read. */
fun tagTones(color: Color, dark: Boolean): TagTones {
    val hue = oklch(color)[2]
    val share = toneChromaShare(color)
    fun tone(l: Double, c: Double) = oklchColor(l, c * share, hue)
    return if (dark) TagTones(
        container = tone(0.32, 0.06),
        onContainer = tone(0.88, 0.08),
        mark = tone(0.80, 0.11),
        onMark = MarkInkDark,
        pastel = tagPastel(color),
    ) else TagTones(
        container = tone(0.90, 0.06),
        onContainer = tone(0.40, 0.12),
        mark = tone(0.55, 0.14),
        onMark = Color.White,
        pastel = tagPastel(color),
    )
}

/**
 * The tones while "Etichette colorate" is off: every tag on the quiet accent ground
 * ([accentTint], #1E2A3D / #D6E4FF) with [primary] text and sign; the blue preset on covers.
 * The text is the accent moved just enough towards ink to read at 4.5:1 ([readableOn]): the
 * light accent #2563EB gives only about 4:1 on #D6E4FF.
 */
fun tagOffTones(dark: Boolean, primary: Color, onPrimary: Color): TagTones {
    val container = if (dark) Color(0xFF1E2A3D) else Color(0xFFD6E4FF)
    return TagTones(
        container = container,
        onContainer = readableOn(primary, container),
        mark = primary,
        onMark = onPrimary,
        pastel = TagOffPastel,
    )
}

/** True on the dark theme (the page is dark). */
@Composable
fun isDarkPage(): Boolean = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * The tones of a tag or folder [color] on the current theme. With [colored] false (tag colours
 * off, or a folder without a colour of its own) the accent's quiet tones ([tagOffTones]).
 */
@Composable
fun rememberTagTones(color: Color?, colored: Boolean = TagColorsEnabled.on): TagTones {
    val dark = isDarkPage()
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val primary = scheme.primary
    val onPrimary = scheme.onPrimary
    return remember(color, colored, dark, primary, onPrimary) {
        if (color == null || !colored) tagOffTones(dark, primary, onPrimary) else tagTones(color, dark)
    }
}

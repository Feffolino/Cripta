package com.cripta.app.ui.theme

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Premium secure dark palette (v2). Deep near-black with layered surfaces + calm blue accent.
val Bg = Color(0xFF0A0C10)
val Surface = Color(0xFF12151C)
val SurfaceVariant = Color(0xFF171B24)
val SurfaceElevated = Color(0xFF1E232E)
val Primary = Color(0xFF5AA9FF)
val OnPrimary = Color(0xFF04121F)
val Accent = Color(0xFFF5B041)
val Danger = Color(0xFFEF4444)
// Error colour used AS TEXT: per theme so it keeps >= 4.5:1 on dialogs and cards.
// Dark #FF7A7A on #1E232E ≈ 6.3:1; light #C62828 on #FFFFFF ≈ 5.6:1 (≈ 4.7:1 on #E7ECF4).
val DangerDark = Color(0xFFFF7A7A)
val OnDangerDark = Color(0xFF2B0A0A)
val DangerLight = Color(0xFFC62828)
val Success = Color(0xFF3FB950)
val OnBg = Color(0xFFF2F5F9)
val OnSurfaceMuted = Color(0xFF98A2B3)
// Outline = borders that must be seen (text fields, outlined buttons/chips): >= 3:1 against the
// surfaces (#646E80 ≈ 3.5:1 on Surface, ≈ 3.1:1 on SurfaceElevated). Dividers and decorative
// hairlines keep the faint OutlineVariant.
val Outline = Color(0xFF646E80)
val OutlineVariant = Color(0xFF2A303C)

// Semantic accents shared by both themes. Favorite = warm gold (the brand's second colour, until
// now hardcoded per-screen); scrim = translucent black behind on-thumbnail badges so they stay
// legible over light images in either theme.
val Favorite = Color(0xFFF5B041)
val BadgeScrim = Color(0x66000000)

// Light palette (v2) — designed, not inverted.
val LightBg = Color(0xFFF7F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE9EDF3)
val LightPrimary = Color(0xFF2563EB)
val LightOnBg = Color(0xFF0F172A)
val LightMuted = Color(0xFF475467) // darkened for >=4.5:1 on light card captions
// #7E889A ≈ 3.6:1 on white, ≈ 3.3:1 on LightBg; the faint one stays for dividers.
val LightOutline = Color(0xFF7E889A)
val LightOutlineVariant = Color(0xFFD9DFEA)

/**
 * The one colour palette of tags and folders (the quick presets of both editors, the automatic
 * colour of a name, "Colori casuali"). Twelve pastel hues in OKLCH at one light lightness (0.86)
 * and a soft chroma (0.10 at most, less where sRGB cannot hold it that light), the first the
 * accent's own hue, the last a blue-tinted slate. Tags show them in full, never darkened, with
 * black text (13:1 or more on every preset). Text on any colour, a preset or the user's own, is
 * black or white, whichever reads at 4.5:1. Colours already saved with a tag or folder are plain
 * ARGB values; the presets of earlier palettes move to these ([FormerPresetColors]).
 */
val TagPalette = listOf(
    Color(0xFFB9D2FF), // blue
    Color(0xFFCCCBFE), // indigo
    Color(0xFFE6C0FE), // purple
    Color(0xFFFEBAD9), // pink
    Color(0xFFFEBEBB), // red
    Color(0xFFFEC29F), // orange
    Color(0xFFF2CB83), // amber
    Color(0xFFC8DB90), // lime
    Color(0xFFA1E4AE), // green
    Color(0xFF7EE7D9), // teal
    Color(0xFF80E1FF), // cyan
    Color(0xFFC8D2DF), // slate
)

/**
 * The presets of the earlier tag and folder palettes (the two before [TagPalette] became pastel,
 * the muted one, the eight folder colours and the first vivid tag colours), each with the
 * [TagPalette] colour of the same hue that replaces it. Saved colours move over once (database
 * versions 36 and 37, the default tag colour when read); a colour of the user's own is never touched.
 */
val FormerPresetColors: Map<Int, Int> = mapOf(
    0xFF90B8FE.toInt() to 0xFFB9D2FF.toInt(),
    0xFFB0ADFE.toInt() to 0xFFCCCBFE.toInt(),
    0xFFD39EF4.toInt() to 0xFFE6C0FE.toInt(),
    0xFFF594C3.toInt() to 0xFFFEBAD9.toInt(),
    0xFFFE9592.toInt() to 0xFFFEBEBB.toInt(),
    0xFFF99E66.toInt() to 0xFFFEC29F.toInt(),
    0xFFDEAF56.toInt() to 0xFFF2CB83.toInt(),
    0xFFACC45F.toInt() to 0xFFC8DB90.toInt(),
    0xFF76CF8A.toInt() to 0xFFA1E4AE.toInt(),
    0xFF62CCBF.toInt() to 0xFF7EE7D9.toInt(),
    0xFF66C6E4.toInt() to 0xFF80E1FF.toInt(),
    0xFFAFB8C5.toInt() to 0xFFC8D2DF.toInt(),
    0xFF6D9DF5.toInt() to 0xFFB9D2FF.toInt(),
    0xFF918BE5.toInt() to 0xFFCCCBFE.toInt(),
    0xFFB37FD2.toInt() to 0xFFE6C0FE.toInt(),
    0xFFDA7BAA.toInt() to 0xFFFEBAD9.toInt(),
    0xFFE27170.toInt() to 0xFFFEBEBB.toInt(),
    0xFFE48B53.toInt() to 0xFFFEC29F.toInt(),
    0xFFD7A94F.toInt() to 0xFFF2CB83.toInt(),
    0xFFA3BA55.toInt() to 0xFFC8DB90.toInt(),
    0xFF5CB572.toInt() to 0xFFA1E4AE.toInt(),
    0xFF46B3A6.toInt() to 0xFF7EE7D9.toInt(),
    0xFF51B3D0.toInt() to 0xFF80E1FF.toInt(),
    0xFF8A939F.toInt() to 0xFFC8D2DF.toInt(),
    0xFF5B7DB1.toInt() to 0xFFB9D2FF.toInt(),
    0xFF6B6FB5.toInt() to 0xFFCCCBFE.toInt(),
    0xFF8A6FB0.toInt() to 0xFFE6C0FE.toInt(),
    0xFFA86B95.toInt() to 0xFFFEBAD9.toInt(),
    0xFFB5677A.toInt() to 0xFFFEBEBB.toInt(),
    0xFFB8735A.toInt() to 0xFFFEC29F.toInt(),
    0xFFA88A4E.toInt() to 0xFFF2CB83.toInt(),
    0xFF7F8F52.toInt() to 0xFFC8DB90.toInt(),
    0xFF5E9473.toInt() to 0xFFA1E4AE.toInt(),
    0xFF4F8F8C.toInt() to 0xFF7EE7D9.toInt(),
    0xFF4F8AA3.toInt() to 0xFF80E1FF.toInt(),
    0xFF8A8580.toInt() to 0xFFC8D2DF.toInt(),
    0xFF4F86C6.toInt() to 0xFFB9D2FF.toInt(),
    0xFF63B76C.toInt() to 0xFFA1E4AE.toInt(),
    0xFFE0A030.toInt() to 0xFFF2CB83.toInt(),
    0xFFD9645B.toInt() to 0xFFFEBEBB.toInt(),
    0xFF9B6BCC.toInt() to 0xFFE6C0FE.toInt(),
    0xFF3FB0B3.toInt() to 0xFF7EE7D9.toInt(),
    0xFFC65B9B.toInt() to 0xFFFEBAD9.toInt(),
    0xFF7A8794.toInt() to 0xFFC8D2DF.toInt(),
    0xFF2563EB.toInt() to 0xFFB9D2FF.toInt(),
    0xFF7C3AED.toInt() to 0xFFCCCBFE.toInt(),
    0xFFDB2777.toInt() to 0xFFFEBAD9.toInt(),
    0xFFDC2626.toInt() to 0xFFFEBEBB.toInt(),
    0xFFC2410C.toInt() to 0xFFFEC29F.toInt(),
    0xFFA16207.toInt() to 0xFFF2CB83.toInt(),
    0xFF15803D.toInt() to 0xFFA1E4AE.toInt(),
    0xFF0F766E.toInt() to 0xFF7EE7D9.toInt(),
    0xFF0E7490.toInt() to 0xFF80E1FF.toInt(),
    0xFF4F46E5.toInt() to 0xFFCCCBFE.toInt(),
    0xFF9333EA.toInt() to 0xFFE6C0FE.toInt(),
    0xFFBE123C.toInt() to 0xFFFEBEBB.toInt(),
)

/** [argb], or the [TagPalette] colour that replaces it when it is a preset of an earlier palette. */
fun currentPresetColor(argb: Int): Int = FormerPresetColors[argb] ?: argb

/**
 * Colour of every tag the user did not colour on its own (Settings › Etichette); null = automatic,
 * one palette colour per name. Snapshot state, so every tag on screen recolours as soon as it changes.
 */
object DefaultTagColor {
    var argb by mutableStateOf<Int?>(null)
}

/**
 * "Etichette colorate" (Settings › Etichette). Off: every tag pill uses the app accent instead of
 * its own colour, wherever it is drawn (read by TagPill, so no screen can forget it).
 */
object TagColorsEnabled {
    var on by mutableStateOf(true)
}

/** How many recently used tags pickers show (Settings › Etichette). */
object RecentTagsCount {
    var value by mutableIntStateOf(6)
}

/** The automatic colour of a name (stable hash into [TagPalette]), ignoring the global default. */
fun autoTagColor(name: String): Color =
    TagPalette[Math.floorMod(name.lowercase().hashCode(), TagPalette.size)]

/** Colour of a tag with no colour of its own: the global default if set, else the automatic one. */
fun tagColor(name: String): Color = DefaultTagColor.argb?.let { Color(it) } ?: autoTagColor(name)

/**
 * [fg] moved towards white (on a dark [bg]) or black (on a light one) just enough to reach [min]
 * contrast: tag names keep their hue while staying readable on their own tinted background.
 */
fun readableOn(fg: Color, bg: Color, min: Double = 4.5): Color {
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }
    val target = if (bg.luminance() < 0.18f) Color.White else Color.Black
    var c = fg
    var t = 0f
    while (contrast(c, bg) < min && t < 1f) { t += 0.05f; c = androidx.compose.ui.graphics.lerp(fg, target, t) }
    return c
}

/** A tag's colour: the one the user picked, else the global default, else the automatic one. */
fun tagColor(tag: com.cripta.app.data.db.TagEntity): Color = tag.color?.let { Color(it) } ?: tagColor(tag.name)

// Cripta Design System roles on top of the Material scheme (same original colours):
// surface = background, surface-raised = surfaceContainer, surface-overlay = surfaceContainerHigh,
// surface-control = secondaryContainer, surface-well = surface.
private val AccentTintDark = Color(0xFF1E2A3D)
private val AccentTintLight = Color(0xFFD6E4FF)

/** DS `accent-tint`: quiet accent ground (navigation indicator, a selected row, empty-state disc). Text on it is ink. */
val androidx.compose.material3.ColorScheme.accentTint: Color
    get() = if (background.luminance() > 0.5f) AccentTintLight else AccentTintDark

/** DS `glass`: dark glass under anything drawn on a picture. */
// Darker than the badges' scrim: white text and icons on it stay at 4.5:1 even over a white frame
// (40% black gave about 2.9:1).
val Glass = Color(0x99000000)

/** Highest OKLCH lightness of a tag or folder colour drawn on a page of the light theme. */
private const val LIGHT_THEME_MAX_L = 0.70f
/** Chroma a coloured (not grey) tag or folder colour gets at least on the light theme. */
private const val LIGHT_THEME_MIN_C = 0.13f

/**
 * [color] as drawn on a page of the light theme: a pastel would almost vanish on the near-white
 * surfaces, so a colour lighter than OKLCH 0.70 is shown at 0.70, same hue, chroma at least 0.13
 * (greys stay grey), trimmed only where sRGB cannot hold it. The presets then stand out from the
 * page at about 2.4:1 and keep black text at 7.4:1 or more. Darker colours are drawn as they are;
 * the saved colour never changes, and the dark theme shows it as chosen.
 */
fun shownOnLightTheme(color: Color): Color {
    fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    fun enc(c: Float) = if (c <= 0.0031308f) c * 12.92f else (1.055 * Math.pow(c.toDouble(), 1 / 2.4) - 0.055).toFloat()
    val r = lin(color.red); val g = lin(color.green); val b = lin(color.blue)
    val l0 = Math.cbrt((0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b))
    val m0 = Math.cbrt((0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b))
    val s0 = Math.cbrt((0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b))
    val okL = (0.2104542553 * l0 + 0.7936177850 * m0 - 0.0040720468 * s0).toFloat()
    if (okL <= LIGHT_THEME_MAX_L) return color
    val okA = 1.9779984951 * l0 - 2.4285922050 * m0 + 0.4505937099 * s0
    val okB = 0.0259040371 * l0 + 0.7827717662 * m0 - 0.8086757660 * s0
    val hue = Math.atan2(okB, okA)
    var chroma = Math.hypot(okA, okB)
    if (chroma > 0.03) chroma = maxOf(chroma, LIGHT_THEME_MIN_C.toDouble())
    val lightness = LIGHT_THEME_MAX_L.toDouble()
    while (true) {
        val a = chroma * Math.cos(hue); val bb = chroma * Math.sin(hue)
        val l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * bb, 3.0)
        val m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * bb, 3.0)
        val s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * bb, 3.0)
        val lr = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        val inGamut = listOf(lr, lg, lb).all { it in -1e-4..1.0001 }
        if (inGamut || chroma <= 0.0) {
            fun ch(v: Double) = enc(v.coerceIn(0.0, 1.0).toFloat()).coerceIn(0f, 1f)
            return Color(ch(lr), ch(lg), ch(lb), color.alpha)
        }
        chroma = (chroma - 0.002).coerceAtLeast(0.0)
    }
}

/** A tag or folder colour as drawn on the current theme's pages ([shownOnLightTheme] on the light one). */
@androidx.compose.runtime.Composable
fun shownTagColor(color: Color): Color {
    val light = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
    return androidx.compose.runtime.remember(color, light) { if (light) shownOnLightTheme(color) else color }
}

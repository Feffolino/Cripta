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
// Error colour used AS TEXT: per theme so it keeps >= 4.5:1 on dialogs and cards.
// Dark #FF7A7A on #1E232E ≈ 6.3:1; light #C62828 on #FFFFFF ≈ 5.6:1 (≈ 4.7:1 on #E7ECF4).
val DangerDark = Color(0xFFFF7A7A)
val OnDangerDark = Color(0xFF2B0A0A)
val DangerLight = Color(0xFFC62828)
val OnBg = Color(0xFFF2F5F9)
val OnSurfaceMuted = Color(0xFF98A2B3)
// Outline = borders that must be seen (text fields, outlined buttons/chips): >= 3:1 against the
// surfaces (#646E80 ≈ 3.5:1 on Surface, ≈ 3.1:1 on SurfaceElevated). Dividers and decorative
// hairlines keep the faint OutlineVariant.
val Outline = Color(0xFF646E80)
val OutlineVariant = Color(0xFF2A303C)

// Favorite = warm gold, the brand's second colour ([Accent]), as drawn on a picture's dark glass.
// On a page or a sheet of the light theme it reads at under 2:1: there [favorite] is darker.
val Favorite = Accent
private val FavoriteLight = Color(0xFFB45309) // ≈ 5:1 on white, ≈ 4:1 on #E2E7EF

/** The favourite star's gold where it is drawn straight on the theme's pages and sheets (see [Favorite]). */
val androidx.compose.material3.ColorScheme.favorite: Color
    get() = if (background.luminance() > 0.5f) FavoriteLight else Favorite

// Light palette (v2) — designed, not inverted.
val LightBg = Color(0xFFF7F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE9EDF3)
// #1D4ED8: text buttons of dialogs and sheets read at 5:1 or more on their containers
// (#2563EB gave 3.9 to 4.4:1 on #DBE1EA..#E9EDF3).
val LightPrimary = Color(0xFF1D4ED8)
val LightOnBg = Color(0xFF0F172A)
val LightMuted = Color(0xFF475467) // darkened for >=4.5:1 on light card captions
// #7E889A ≈ 3.6:1 on white, ≈ 3.3:1 on LightBg; the faint one stays for dividers.
val LightOutline = Color(0xFF7E889A)
val LightOutlineVariant = Color(0xFFD9DFEA)

/**
 * The one colour palette of tags and folders (the quick presets of both editors, the automatic
 * colour of a name, "Colori casuali"). Twelve pastel hues in OKLCH at one light lightness (0.86)
 * and a soft chroma (0.10 at most, less where sRGB cannot hold it that light), the first the
 * accent's own hue, the last a blue-tinted slate. The pastel itself is drawn only on covers, with
 * black text (13:1 or more on every preset); pages draw the tones of its hue ([tagTones]: a tonal
 * container, its text, a stronger mark). Colours already saved with a tag or folder are plain
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
 * "Etichette colorate" (Settings › Etichette). Off: every tag pill takes the accent's quiet tones
 * ([tagOffTones]) and every cover mark the blue preset, wherever it is drawn (read by TagPill and
 * [rememberTagTones], so no screen can forget it).
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
internal val AccentTintDark = Color(0xFF1E2A3D)
internal val AccentTintLight = Color(0xFFD6E4FF)

/** DS `accent-tint`: quiet accent ground (navigation indicator, a selected row, empty-state disc). Text on it is ink. */
val androidx.compose.material3.ColorScheme.accentTint: Color
    get() = if (background.luminance() > 0.5f) AccentTintLight else AccentTintDark

/** DS `glass`: dark glass under anything drawn on a picture. */
// Darker than the badges' scrim: white text and icons on it stay at 4.5:1 even over a white frame
// (40% black gave about 2.9:1).
val Glass = Color(0x99000000)


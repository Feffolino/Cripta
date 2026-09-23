package com.cripta.app.ui.theme

import androidx.compose.ui.graphics.Color

// Premium secure dark palette (v2). Deep near-black with layered surfaces + calm blue accent.
val Bg = Color(0xFF0A0C10)
val Surface = Color(0xFF12151C)
val SurfaceVariant = Color(0xFF171B24)
val SurfaceElevated = Color(0xFF1E232E)
val Primary = Color(0xFF5AA9FF)
val OnPrimary = Color(0xFF04121F)
val Accent = Color(0xFFF5B041)
val Danger = Color(0xFFEF4444)
val Success = Color(0xFF3FB950)
val OnBg = Color(0xFFF2F5F9)
val OnSurfaceMuted = Color(0xFF98A2B3)
val Outline = Color(0xFF2A303C)

// Semantic accents shared by both themes. Favorite = warm gold (the brand's second colour, until
// now hardcoded per-screen); scrim = translucent black behind on-thumbnail badges so they stay
// legible over light images in either theme.
val Favorite = Color(0xFFF5B041)
val BadgeScrim = Color(0x66000000)

// Light palette (v2) — designed, not inverted.
val LightBg = Color(0xFFF6F8FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF1F6)
val LightPrimary = Color(0xFF2563EB)
val LightOnBg = Color(0xFF0F172A)
val LightMuted = Color(0xFF475467) // darkened for >=4.5:1 on light card captions
val LightOutline = Color(0xFFD9DFEA)

/**
 * Tag palette: saturated mid-tones that keep white text readable (>= 4.5:1) in both themes.
 * A tag always maps to the same colour (hash of its name), so it is recognisable at a glance.
 */
val TagPalette = listOf(
    Color(0xFF2563EB), // blue
    Color(0xFF7C3AED), // violet
    Color(0xFFDB2777), // pink
    Color(0xFFDC2626), // red
    Color(0xFFC2410C), // orange
    Color(0xFFA16207), // amber
    Color(0xFF15803D), // green
    Color(0xFF0F766E), // teal
    Color(0xFF0E7490), // cyan
    Color(0xFF4F46E5), // indigo
    Color(0xFF9333EA), // purple
    Color(0xFFBE123C), // rose
)

fun tagColor(name: String): Color =
    TagPalette[Math.floorMod(name.lowercase().hashCode(), TagPalette.size)]

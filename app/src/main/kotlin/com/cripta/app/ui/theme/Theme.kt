package com.cripta.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cripta.app.data.ThemeMode

// Every Material 3 role is set explicitly in both palettes: any role left out falls back to the
// Material baseline (purple / pink), which then leaks into components that use it (tonal
// buttons, chips, menus, the container tiers of sheets and dialogs).

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = OnBg,
    inversePrimary = LightPrimary,
    secondary = Accent,
    onSecondary = Bg,
    secondaryContainer = AccentTintDark,
    onSecondaryContainer = OnBg,
    tertiary = Accent,
    onTertiary = Bg,
    tertiaryContainer = Color(0xFF3A2E14),
    onTertiaryContainer = Color(0xFFF5D08A),
    background = Bg,
    onBackground = OnBg,
    surface = Surface,
    onSurface = OnBg,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    surfaceTint = Primary,
    inverseSurface = Color(0xFFE6EAF0),
    inverseOnSurface = Color(0xFF12151C),
    error = DangerDark,
    onError = OnDangerDark,
    errorContainer = Color(0xFF3B1417),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Color.Black,
    // Container tiers, lowest (behind everything) to highest (dialogs, menus).
    surfaceDim = Bg,
    surfaceBright = Color(0xFF252B37),
    surfaceContainerLowest = Color(0xFF07090C),
    surfaceContainerLow = Color(0xFF0F1218),
    surfaceContainer = SurfaceVariant,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = Color(0xFF252B37),
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = AccentTintLight,
    onPrimaryContainer = Color(0xFF0B2A66),
    inversePrimary = Primary,
    secondary = Accent,
    onSecondary = LightOnBg,
    secondaryContainer = Color(0xFFE3EAF5),
    onSecondaryContainer = LightOnBg,
    tertiary = Color(0xFFA16207),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDC7),
    onTertiaryContainer = Color(0xFF5A3B00),
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    surfaceTint = LightPrimary,
    inverseSurface = Color(0xFF1E232E),
    inverseOnSurface = Color(0xFFF2F5F9),
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF7A1111),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,
    // Light container tiers step down from white in even, visible steps: on the near-white
    // background a tier only a couple of points darker (the Home summary, the search pill, the
    // selection bar) vanished.
    surfaceDim = Color(0xFFD9DFE8),
    surfaceBright = LightSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF2F6),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE2E7EF),
    surfaceContainerHighest = Color(0xFFDBE1EA),
)

@Composable
fun CriptaTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    // Manrope comes from res/font and Compose loads each weight the first time it is drawn, on the
    // UI thread: the first media opened (new weights in the viewer and its panel) stuttered on it.
    // All five weights are loaded once here instead, while the lock screen is up.
    val fontResolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    androidx.compose.runtime.LaunchedEffect(fontResolver) { runCatching { fontResolver.preload(Manrope) } }
    MaterialTheme(
        colorScheme = animateColorScheme(colors),
        typography = CriptaTypography,
        shapes = CriptaShapes,
        content = content,
    )
}

/**
 * Switching light / dark (Impostazioni › Aspetto, or the system following the time of day) fades
 * every role over [Motion.MEDIUM] instead of repainting the whole app in one frame.
 */
@Composable
private fun animateColorScheme(target: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val spec = androidx.compose.animation.core.tween<Color>(Motion.MEDIUM, 0, Motion.EaseOutQuart)
    @Composable fun a(c: Color) = androidx.compose.animation.animateColorAsState(c, spec, label = "scheme").value
    return target.copy(
        primary = a(target.primary), onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer), onPrimaryContainer = a(target.onPrimaryContainer),
        inversePrimary = a(target.inversePrimary),
        secondary = a(target.secondary), onSecondary = a(target.onSecondary),
        secondaryContainer = a(target.secondaryContainer), onSecondaryContainer = a(target.onSecondaryContainer),
        tertiary = a(target.tertiary), onTertiary = a(target.onTertiary),
        tertiaryContainer = a(target.tertiaryContainer), onTertiaryContainer = a(target.onTertiaryContainer),
        background = a(target.background), onBackground = a(target.onBackground),
        surface = a(target.surface), onSurface = a(target.onSurface),
        surfaceVariant = a(target.surfaceVariant), onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceTint = a(target.surfaceTint),
        inverseSurface = a(target.inverseSurface), inverseOnSurface = a(target.inverseOnSurface),
        error = a(target.error), onError = a(target.onError),
        errorContainer = a(target.errorContainer), onErrorContainer = a(target.onErrorContainer),
        outline = a(target.outline), outlineVariant = a(target.outlineVariant), scrim = a(target.scrim),
        surfaceBright = a(target.surfaceBright), surfaceDim = a(target.surfaceDim),
        surfaceContainer = a(target.surfaceContainer), surfaceContainerHigh = a(target.surfaceContainerHigh),
        surfaceContainerHighest = a(target.surfaceContainerHighest), surfaceContainerLow = a(target.surfaceContainerLow),
        surfaceContainerLowest = a(target.surfaceContainerLowest),
    )
}

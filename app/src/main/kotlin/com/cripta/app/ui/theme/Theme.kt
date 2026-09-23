package com.cripta.app.ui.theme

import android.os.Build
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
    secondaryContainer = Color(0xFF1E2A3D),
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
    primaryContainer = Color(0xFFDBE7FF),
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
    surfaceDim = Color(0xFFDDE2EA),
    surfaceBright = LightSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5F9),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE7ECF4),
    surfaceContainerHighest = Color(0xFFE1E6EF),
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
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = CriptaTypography,
        shapes = CriptaShapes,
        content = content,
    )
}

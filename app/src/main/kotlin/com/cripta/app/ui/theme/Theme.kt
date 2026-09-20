package com.cripta.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.cripta.app.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = OnBg,
    secondary = Accent,
    onSecondary = Bg,
    tertiary = Accent,
    background = Bg,
    onBackground = OnBg,
    surface = Surface,
    onSurface = OnBg,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    surfaceContainer = SurfaceVariant,
    surfaceContainerHigh = SurfaceElevated,
    error = Danger,
    outline = Outline,
    outlineVariant = Outline,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Accent,
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE7ECF4),
    error = Danger,
    outline = LightOutline,
    outlineVariant = LightOutline,
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

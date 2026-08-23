package com.cripta.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun CriptaTheme(content: @Composable () -> Unit) {
    // Vault app: a single, committed dark scheme for a calm, low-glare look.
    MaterialTheme(
        colorScheme = DarkColors,
        typography = CriptaTypography,
        shapes = CriptaShapes,
        content = content,
    )
}

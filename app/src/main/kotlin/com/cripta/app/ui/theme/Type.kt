package com.cripta.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cripta.app.R

// Cripta Design System type: one family, Manrope, bundled in the APK (res/font, SIL OFL; licence in
// assets/licenses). Nothing is fetched at runtime: the vault never downloads assets. One static file
// per weight (400–800, instanced from the variable font): the variable file alone rendered at its
// default ExtraLight instance, far thinner than the design.
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

// Every style uses tabular figures: counts, sizes, durations and progress line up and don't jitter.
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontFeatureSettings = "tnum",
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/** DS `display`: the name on the lock screen and empty states only. */
val DisplayStyle = style(34, 40, FontWeight.ExtraBold, -0.8)
/** DS `headline`: the title of a tab, beside the Cripta mark. */
val HeadlineStyle = style(26, 32, FontWeight.Bold, -0.4)
/** DS `figure`: summary figures (Home strip, details panel). */
val FigureStyle = style(26, 30, FontWeight.Bold, -0.3)
/** DS `heading`: section headings, in accent. */
val HeadingStyle = style(13, 18, FontWeight.Bold, 0.2)

// The DS scale mapped onto the Material slots the app already uses.
val CriptaTypography = Typography(
    displayLarge = style(40, 46, FontWeight.ExtraBold, -1.0),
    displayMedium = DisplayStyle,
    displaySmall = DisplayStyle,
    headlineLarge = HeadlineStyle,
    headlineMedium = HeadlineStyle,
    headlineSmall = HeadlineStyle,
    // title-lg: dialog titles, the top bar title of a pushed page.
    titleLarge = style(20, 26, FontWeight.Bold, -0.2),
    // title: sheet headings, the title of a large cover.
    titleMedium = style(16, 22, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.Bold, 0.1),
    // body-lg: row titles in lists and settings.
    bodyLarge = style(16, 22, FontWeight.SemiBold),
    // body: running text, page intros, dialog bodies.
    bodyMedium = style(14, 20, FontWeight.Medium),
    // caption: under a row title and under covers.
    bodySmall = style(12, 16, FontWeight.Medium),
    // label: buttons, chips, the current tab's label.
    labelLarge = style(14, 20, FontWeight.Bold, 0.1),
    labelMedium = style(12, 16, FontWeight.Bold, 0.2),
    // label-sm: badges on glass, small tag pills, tab labels.
    labelSmall = style(11, 14, FontWeight.Bold, 0.3),
)

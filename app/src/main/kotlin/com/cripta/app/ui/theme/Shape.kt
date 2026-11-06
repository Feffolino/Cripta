package com.cripta.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Cripta Design System radii: sm 10, md 14, cover 16, lg 22, sheet 28. Buttons, chips, tags,
// single-line fields, badges and the navigation indicator are pills (CircleShape) on top of this.
val RadiusSm = RoundedCornerShape(10.dp)
val RadiusMd = RoundedCornerShape(14.dp)
val RadiusLg = RoundedCornerShape(22.dp)
val RadiusSheet = RoundedCornerShape(28.dp)

val CriptaShapes = Shapes(
    extraSmall = RadiusSm,
    small = RadiusMd,
    medium = RoundedCornerShape(16.dp),
    large = RadiusLg,
    extraLarge = RadiusSheet,
)

/** File covers in grids, shelves and pickers; folder stacks (radius-cover). */
val CoverShape = RoundedCornerShape(16.dp)

/** Small covers: list rows, previews in settings (radius-sm). */
val CoverShapeSmall = RadiusSm

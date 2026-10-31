package com.cripta.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CriptaShapes = Shapes(
    // Soft, generous radii: controls and surfaces read as rounded slabs, sheets and dialogs as one
    // object. Every chip and pill is fully round (CircleShape) on top of this scale.
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** File covers in grids, shelves and pickers: a touch rounder than cards, so covers read as tiles. */
val CoverShape = RoundedCornerShape(18.dp)

/** Small covers: list rows, previews in settings. */
val CoverShapeSmall = RoundedCornerShape(12.dp)

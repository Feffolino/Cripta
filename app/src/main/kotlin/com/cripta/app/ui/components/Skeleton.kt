package com.cripta.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.animationsEnabled

/**
 * The one placeholder block shown while a screen's data is on its way: a plain tonal surface (no
 * border) the size and shape of what will replace it, so nothing moves when the content arrives.
 * It breathes slowly (ease-out, no bounce); with "Remove animations" on it stays still. Hidden
 * from TalkBack: it says nothing.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    // Read in the layer only: the pulse redraws the box, it no longer recomposes it every frame.
    val pulse = if (animationsEnabled()) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, 0, Motion.EaseOutQuart), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    } else null
    Box(
        modifier.clearAndSetSemantics {}.graphicsLayer { this.alpha = pulse?.value ?: 1f }.clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

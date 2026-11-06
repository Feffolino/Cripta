package com.cripta.app.ui.components

import com.cripta.app.ui.theme.CriptaIcons
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cripta.app.ui.theme.Motion

/**
 * Thumbnail frame that explains selection (DS `cr-cover.is-selected`): a 3dp accent ring on the
 * cover's own edge, the picture drawn 6dp inside it with smaller corners. [content] is drawn inside
 * the (inset, clipped) frame; [overlay] on top, e.g. the [SelectionCheck] in a corner.
 * [selectedScale] is kept for the call sites: the inset now does that job.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SelectableThumbFrame(
    selected: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    selectedScale: Float = 0.92f,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val inset by androidx.compose.animation.core.animateDpAsState(
        if (selected) 6.dp else 0.dp,
        tween(Motion.MEDIUM, 0, Motion.EaseOutQuart), label = "selInset",
    )
    val ringAlpha by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(Motion.MEDIUM, 0, Motion.EaseOutQuart), label = "selBorder",
    )
    val primary = MaterialTheme.colorScheme.primary
    // The picture's corners follow the inset (16 -> 11 on a cover), so the ring stays even.
    val inner = if (inset > 0.dp) androidx.compose.foundation.shape.RoundedCornerShape(11.dp) else shape
    Box(modifier) {
        if (ringAlpha > 0f) {
            Box(Modifier.matchParentSize().clip(shape).border(3.dp, primary.copy(alpha = ringAlpha), shape))
        }
        Box(
            Modifier.matchParentSize()
                .padding(inset)
                .clip(inner),
            contentAlignment = Alignment.Center,
            content = content,
        )
        overlay()
    }
}

/**
 * Selection marker for a tile corner. In selection mode an unselected tile shows an empty ring
 * where the check will land; selecting pops the filled check in (scale 0.4 -> 1 + fade).
 * Purely visual: the selected state is exposed through the cell's semantics.
 */
@Composable
fun SelectionCheck(
    selected: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    AnimatedVisibility(
        visible = selectionMode || selected,
        enter = fadeIn(Motion.enter(Motion.SHORT)),
        exit = fadeOut(Motion.exit(Motion.SHORT)),
        modifier = modifier,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            // Empty ring (always present in selection mode, under the check).
            Box(
                Modifier.matchParentSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            )
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(Motion.enter(Motion.MEDIUM), initialScale = 0.4f) + fadeIn(Motion.enter(Motion.SHORT)),
                exit = scaleOut(Motion.exit(Motion.SHORT), targetScale = 0.4f) + fadeOut(Motion.exit(Motion.SHORT)),
            ) {
                // DS `cr-check`: a plain accent disc with the tick, no rim.
                Box(
                    Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CriptaIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(size * 0.68f))
                }
            }
        }
    }
}

/** Placeholder -> cover crossfade used by every thumbnail (calm, ease-out). */
@Composable
fun ThumbCrossfade(
    bmp: Bitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit,
) {
    Crossfade(
        targetState = bmp,
        animationSpec = tween(Motion.MEDIUM, 0, Motion.EaseOutQuart),
        modifier = modifier,
        label = "thumb",
    ) { b ->
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = contentDescription, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            placeholder()
        }
    }
}

/** Convenience: [SelectionCheck] placed in the top-end corner of a frame. */
@Composable
fun BoxScope.CornerSelectionCheck(selected: Boolean, selectionMode: Boolean, size: Dp = 24.dp) {
    SelectionCheck(selected, selectionMode, Modifier.align(Alignment.TopEnd).padding(8.dp), size)
}

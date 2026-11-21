package com.cripta.app.ui.vault

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import com.cripta.app.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * The chips under the search fold away with the finger, pixel for pixel: a drag up first folds
 * them, then scrolls the grid; a drag down first unfolds them. The content under the finger never
 * stops or jumps, so even a tiny drag moves what is on screen by exactly that much.
 */
@Stable
internal class ChipsCollapse(
    /** False when there is nothing more to reveal below: the chips then stay. */
    private val canHide: () -> Boolean,
) {
    /** 0 = chips fully shown, 1 = fully folded. Read only in layout/draw, so no recomposition. */
    var fraction by mutableFloatStateOf(0f)
        private set

    /** Height of each folding block, as last measured (not state: only the scroll reads it). */
    private val heights = IntArray(SLOTS)

    /** Space that opens below the search as the chips fold, so the net travel stays 1:1. */
    var growingGapPx = 0f

    private val range: Float get() = heights.sum() - growingGapPx

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val (next, used) = collapseStep(fraction, available.y, range, canHide())
            fraction = next
            return Offset(0f, used)
        }

        // Released halfway: finish the fold toward the nearer end, like a collapsing top bar.
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(if (fraction < 0.5f) 0f else 1f)
            return Velocity.Zero
        }
    }

    suspend fun settle(target: Float) {
        if (fraction == target) return
        animate(fraction, target, animationSpec = Motion.enter()) { v, _ -> fraction = v }
    }

    /** One folding block. [slot] tells the blocks apart so their heights add up. */
    fun Modifier.folding(slot: Int): Modifier = this
        .clipToBounds()
        .layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            heights[slot] = p.height
            val shown = (p.height * (1f - fraction)).roundToInt()
            // Slides up under the search as it folds, rather than being cut from the bottom.
            layout(p.width, shown) { p.placeRelative(0, shown - p.height) }
        }
        .graphicsLayer { alpha = 1f - fraction }

    companion object {
        const val SLOTS = 2
    }
}

/**
 * One scroll step: the new fold [fraction] and the scroll it used. [dy] < 0 is a drag up (folds),
 * [dy] > 0 a drag down (unfolds); [range] is the height the fold covers in px.
 */
internal fun collapseStep(fraction: Float, dy: Float, range: Float, canHide: Boolean): Pair<Float, Float> {
    if (range <= 0f || dy == 0f) return fraction to 0f
    if (dy < 0f && !canHide) return fraction to 0f
    val next = (fraction - dy / range).coerceIn(0f, 1f)
    return next to (fraction - next) * range
}

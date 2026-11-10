package com.cripta.app.ui.vault

import kotlin.math.abs

/**
 * How deep [y] is in the grid's auto-scroll edge zones, from -1 to 1: negative inside the top zone
 * (and above the grid), positive inside the bottom one (and below it), 0 in between.
 */
internal fun edgeDepth(y: Float, height: Float, zone: Float): Float {
    if (zone <= 0f || height <= 0f) return 0f
    // A grid shorter than two zones: each zone takes half of it, so the middle is still at rest.
    val z = minOf(zone, height / 2f)
    return when {
        y < z -> -(z - y) / z
        y > height - z -> (y - (height - z)) / z
        else -> 0f
    }.coerceIn(-1f, 1f)
}

/** Auto-scroll speed for [depth] (see [edgeDepth]): squared, so the zone's edge creeps and its far end runs. */
internal fun edgeSpeed(depth: Float, max: Float): Float = max * depth * abs(depth)

/** Where a move drag's files go when the finger lifts. */
internal sealed interface DropTarget {
    /** Into folder [id] (null = the vault's root). */
    data class Into(val id: Long?) : DropTarget
    /** Nowhere: the drag is cancelled and the files stay where they are. */
    data object Stay : DropTarget
}

/**
 * The drop of a move drag: the folder under the finger ([hover]); else, when the drag changed
 * folder (second finger, Back), the folder open now ([here], null = root); else nothing moves.
 * [origin] is the folder the drag started in.
 */
internal fun moveDropTarget(hover: Long?, here: Long?, origin: Long?): DropTarget = when {
    hover != null -> DropTarget.Into(hover)
    here != origin -> DropTarget.Into(here)
    else -> DropTarget.Stay
}

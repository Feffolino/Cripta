package com.cripta.app.ui.vault

import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Depth of the auto-scroll edge zones of a dragging finger (move drag and manual reorder alike). */
internal val EDGE_ZONE = 64.dp

/** Auto-scroll speed at the far end of an edge zone, per second. */
internal val EDGE_MAX_SPEED = 1600.dp

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

/** The outer part of an edge zone where the finger rests without scrolling, as a share of the zone. */
internal const val EDGE_DEAD = 0.3f

/**
 * Auto-scroll speed for [depth] (see [edgeDepth]): still in the first [EDGE_DEAD] of the zone, so a
 * finger only grazing it does not scroll, then squared, so the zone creeps first and its far end runs.
 */
internal fun edgeSpeed(depth: Float, max: Float): Float {
    val d = ((abs(depth) - EDGE_DEAD) / (1f - EDGE_DEAD)).coerceIn(0f, 1f)
    return if (depth < 0f) -max * d * d else max * d * d
}

/** How long a finger waits in the scrolling part of an edge zone before the grid moves. */
internal const val EDGE_DELAY_MS = 200L

/** How long the auto-scroll then takes to reach the speed of the finger's depth. */
internal const val EDGE_RAMP_MS = 450L

/** Share of the auto-scroll speed [elapsedMs] after the finger entered the scrolling part of a zone. */
internal fun edgeRamp(elapsedMs: Long): Float =
    ((elapsedMs - EDGE_DELAY_MS).toFloat() / EDGE_RAMP_MS).coerceIn(0f, 1f)

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

/**
 * The grid's order once a drag is dropped: [dragged] (the list as the user arranged it) over
 * [latest] (the vault now: an update that came during the drag was held back). Files still there
 * keep the user's order, as the vault's copy; files added meanwhile go where the vault has them;
 * files gone are left out.
 */
internal fun <T : Any> afterDrop(dragged: List<T>, latest: List<T>, key: (T) -> String): List<T> {
    val now = latest.associateBy(key)
    val out = dragged.mapNotNull { now[key(it)] }.toMutableList()
    val kept = out.mapTo(HashSet(), key)
    latest.forEachIndexed { i, t -> if (key(t) !in kept) out.add(minOf(i, out.size), t) }
    return out
}

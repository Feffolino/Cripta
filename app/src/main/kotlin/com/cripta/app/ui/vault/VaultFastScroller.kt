package com.cripta.app.ui.vault

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import com.cripta.app.ui.theme.Motion
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.FastScroller(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier,
    labelAt: (Int) -> String? = { null },
) {
    val total by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }
    if (total <= 0) return
    val scope = rememberCoroutineScope()
    var trackH by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Where the finger put the thumb: while dragging the thumb follows the finger exactly instead
    // of the list, whose position moves in whole rows (with few files that made it jump in steps).
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // Scroll position as a continuous 0..1: the items (rows) above the screen against those above
    // plus those below, each counting the part of its edge row already scrolled. Normalising by how
    // many items are visible made the thumb jump: that number changes as rows and full-width headers
    // (the folders, the dates) come and go.
    val listFraction by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val vis = info.visibleItemsInfo
            when {
                info.totalItemsCount <= 1 || vis.isEmpty() || !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> {
                    val first = vis.first()
                    val firstCols = vis.count { it.offset.y == first.offset.y }.coerceAtLeast(1)
                    val before = first.index + (-first.offset.y).coerceAtLeast(0).toFloat() / first.size.height.coerceAtLeast(1) * firstCols
                    val last = vis.last()
                    val lastCols = vis.count { it.offset.y == last.offset.y }.coerceAtLeast(1)
                    val hidden = (last.offset.y + last.size.height - info.viewportEndOffset).coerceAtLeast(0)
                    val after = (info.totalItemsCount - 1 - last.index) + hidden.toFloat() / last.size.height.coerceAtLeast(1) * lastCols
                    (before / (before + after).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                }
            }
        }
    }
    val fraction = if (dragging) dragFraction else listFraction
    val active = state.isScrollInProgress || dragging
    // Stays a moment after the list stops, so it can be caught: it used to fade the instant the
    // fling ended.
    var linger by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) linger = true else { kotlinx.coroutines.delay(1500); linger = false } }
    // Only for a list worth jumping through (two screens or more): on a short one it popped up for
    // nothing over the covers.
    val worth by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            info.visibleItemsInfo.isNotEmpty() && info.totalItemsCount >= info.visibleItemsInfo.size * 2 &&
                (state.canScrollForward || state.canScrollBackward)
        }
    }
    val alpha by animateFloatAsState(if ((active || linger) && (worth || dragging)) 1f else 0f, label = "fastscroll")
    // The strip only takes drags while the scroller is visible: when hidden, touches on the right
    // edge go to the grid (select, open, scroll) instead of an invisible control.
    val visible = alpha > 0.05f || dragging
    val thumbW by animateDpAsState(if (dragging) 10.dp else 6.dp, Motion.enter(Motion.SHORT), label = "thumbW")
    val density = LocalDensity.current
    val thumbH = 48.dp
    val thumbPx = with(density) { thumbH.toPx() }
    val maxOffset = (trackH - thumbPx).coerceAtLeast(0f)
    val offsetY = with(density) { (fraction * maxOffset).toDp() }
  Box(modifier.fillMaxHeight()) {
    // Bubble next to the thumb (section / position of the first visible item) only while the thumb
    // is held: shown on every swipe of the list, it covered the covers for nothing.
    val bubbleAlpha by animateFloatAsState(if (dragging) 1f else 0f, label = "fastscrollBubble")
    val label = if (bubbleAlpha > 0f) labelAt(state.firstVisibleItemIndex) else null
    if (label != null) {
        // A plain drawing, never a touch target: a Surface swallowed the touches landing on it, so a
        // new swipe started on the bubble (while the list was still moving) stopped the list dead.
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 30.dp)
                .offset(y = offsetY + 6.dp)
                .graphicsLayer {
                    this.alpha = bubbleAlpha
                    shadowElevation = 3.dp.toPx()
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    clip = true
                }
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clearAndSetSemantics { },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    Box(
        Modifier.align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(48.dp)
            .onGloballyPositioned { trackH = it.size.height.toFloat() }
            .then(if (!visible) Modifier else Modifier.pointerInput(total, trackH) {
                // Grabbed on the thumb: it keeps the same point under the finger (no jump). Grabbed
                // elsewhere on the track: the thumb centres on the finger.
                var grab = 0f
                fun follow(y: Float) {
                    if (maxOffset <= 0f) return
                    dragFraction = ((y - grab) / maxOffset).coerceIn(0f, 1f)
                    // Same mapping as listFraction, with the offset inside the row, so the list
                    // glides under the thumb instead of stepping a row at a time.
                    val info = state.layoutInfo
                    val vis = info.visibleItemsInfo
                    val cols = vis.firstOrNull()?.let { f -> vis.count { it.offset.y == f.offset.y } }?.coerceAtLeast(1) ?: 1
                    val rowH = vis.firstOrNull()?.size?.height ?: 0
                    val pos = dragFraction * (total - vis.size).coerceAtLeast(1)
                    val idx = pos.toInt().coerceIn(0, total - 1)
                    val rowStart = idx - idx % cols
                    val within = ((pos - rowStart) / cols * rowH).roundToInt().coerceAtLeast(0)
                    scope.launch { state.scrollToItem(rowStart, within) }
                }
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        val top = listFraction * maxOffset
                        grab = if (o.y in top..(top + thumbPx)) o.y - top else thumbPx / 2f
                        dragFraction = listFraction
                        dragging = true
                        follow(o.y)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ -> change.consume(); follow(change.position.y) },
                )
            }),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(end = 3.dp)
                .offset(y = offsetY)
                .width(thumbW)
                .height(thumbH)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
  }
}

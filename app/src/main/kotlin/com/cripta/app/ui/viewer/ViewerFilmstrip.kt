@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cripta.app.ui.viewer

import com.cripta.app.R
import com.cripta.app.str
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.heightIn
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip

/**
 * Strip of the covers around the current file ([ViewerViewModel.PlaybackPrefs.stripSpan] each side),
 * centred on it; tap one to jump. When the file changes the strip glides to centre the new one while
 * it grows to full size and brightens and the previous one shrinks and dims, so the jump reads as
 * movement along the list. It can also be dragged to look further ahead. Shape, size and whether
 * portrait files keep a vertical cover come from Settings › Visualizzatore.
 */
@Composable
internal fun Filmstrip(
    ids: List<String>,
    current: Int,
    vm: ViewerViewModel,
    prefs: ViewerViewModel.PlaybackPrefs,
    vertical: Boolean = false,
    /** A vertical swipe is under way (see verticalLock): the horizontal strip must not take it. */
    swipeLocked: Boolean = false,
    onPick: (Int) -> Unit,
) {
    val span = prefs.stripSpan.coerceIn(1, 4)
    // (other covers, current cover) height per size step; a bit smaller down the landscape edge.
    // Each step is larger than it used to be (the smallest covers were hard to tell apart); the
    // user's size, shape and count choices keep their meaning.
    val (thumbH, selH) = when (prefs.stripSize) {
        0 -> if (vertical) 24.dp to 30.dp else 26.dp to 34.dp
        2 -> if (vertical) 36.dp to 46.dp else 42.dp to 54.dp
        else -> if (vertical) 30.dp to 38.dp else 34.dp to 44.dp
    }
    val rect = prefs.stripShape == com.cripta.app.data.StripShape.RECT
    val baseAspect = if (rect) 16f / 9f else 1f
    val shape = if (prefs.stripShape == com.cripta.app.data.StripShape.CIRCLE) CircleShape
        else androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    // Enough air between covers that each reads as its own file.
    val gap = 10.dp
    // Width/height of each file's cover, learnt as the cells load (portrait files: up to 9:16).
    val aspects = remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    fun aspectOf(id: String) = if (rect && prefs.stripTrueAspect) aspects[id] ?: baseAspect else baseAspect
    // Size of an interactive cover slot along the strip.
    fun along(h: androidx.compose.ui.unit.Dp, aspect: Float) = maxOf(48.dp, if (vertical) h else h * aspect)
    val full = along(selH, baseAspect) + (along(thumbH, baseAspect) + gap) * (2 * span)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current)
    val animate = com.cripta.app.ui.theme.animationsEnabled()
    LaunchedEffect(current) {
        if (listState.firstVisibleItemIndex == current && listState.firstVisibleItemScrollOffset == 0) return@LaunchedEffect
        val near = listState.layoutInfo.visibleItemsInfo.any { it.index == current }
        if (!animate || !near) { listState.scrollToItem(current); return@LaunchedEffect }
        // Glide by chasing the new cover each frame. animateScrollToItem aimed at where the cover
        // was when it started and snapped to the real place at the end; meanwhile the covers grow
        // and shrink (and the edge padding follows the new aspect), so that last snap was a jerk.
        listState.scroll {
            val start = androidx.compose.runtime.withFrameNanos { it }
            var last = start
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current } ?: break
                val left = info.offset.toFloat()
                val elapsed = (now - start) / 1_000_000L
                if (kotlin.math.abs(left) < 0.5f && elapsed > Motion.MEDIUM) break
                // Never longer than the longest motion step: whatever is left is covered at once.
                if (elapsed > Motion.LONG) { scrollBy(left); break }
                // Exponential approach, the same pace whatever the frame rate.
                val k = 1f - kotlin.math.exp(-((now - last) / 1_000_000f) / 70f)
                last = now
                scrollBy(left * k)
            }
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        if (vertical) Modifier.heightIn(max = full) else Modifier.widthIn(max = full),
    ) {
        // Padding on both ends = half the free space, so "scroll to item i" puts item i in the middle.
        val extent = (if (vertical) maxHeight else maxWidth).coerceAtMost(full)
        val curAspect = ids.getOrNull(current)?.let { aspectOf(it) } ?: baseAspect
        val edge by animateDpAsState(((extent - along(selH, curAspect)) / 2).coerceAtLeast(0.dp),
            Motion.enter(Motion.MEDIUM), label = "stripEdge")
        val cell: @Composable (Int) -> Unit = { i ->
            val id = ids[i]
            val sel = i == current
            val h by animateDpAsState(if (sel) selH else thumbH, Motion.enter(Motion.MEDIUM), label = "stripThumb")
            val dim by animateFloatAsState(if (sel) 1f else 0.6f, Motion.enter(Motion.MEDIUM), label = "stripDim")
            val ring by animateFloatAsState(if (sel) 1f else 0f, Motion.enter(Motion.MEDIUM), label = "stripRing")
            val bmp by produceState<Bitmap?>(null, id) { value = vm.thumbOf(id) }
            // With its name: "File 3 of 12" alone made a known file findable only by its position.
            val name by produceState<String?>(null, id) { value = vm.fileById(id)?.originalName }
            if (rect && prefs.stripTrueAspect) {
                LaunchedEffect(id) {
                    val f = vm.fileById(id)
                    val w = f?.width ?: 0; val fh = f?.height ?: 0
                    // Portrait keeps its shape (down to 9:16); landscape stays a uniform 16:9.
                    aspects[id] = if (w > 0 && fh > w) (w.toFloat() / fh).coerceAtLeast(9f / 16f) else 16f / 9f
                }
            }
            val aspect by animateFloatAsState(aspectOf(id), Motion.enter(Motion.MEDIUM), label = "stripAspect")
            val accentRing = MaterialTheme.colorScheme.primary
            val shown by animateFloatAsState(if (bmp != null) 1f else 0f, Motion.enter(Motion.MEDIUM), label = "stripLoad")
            Box(
                Modifier.height(maxOf(48.dp, h)).width(maxOf(48.dp, h * aspect))
                    .clickable(onClickLabel = str(R.string.open)) { onPick(i) }
                    .semantics {
                        this.contentDescription = (name?.let { "$it, " } ?: "") + str(R.string.file_x_of_y, i + 1, ids.size)
                        this.selected = sel
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.height(h).aspectRatio(aspect)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.12f))
                        // DS filmstrip: the current item ringed in accent.
                        .border(2.dp, accentRing.copy(alpha = ring), shape),
                ) {
                    bmp?.let {
                        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                            alpha = dim * shown, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        if (vertical) {
            LazyColumn(
                state = listState,
                modifier = Modifier.height(extent).width(maxOf(48.dp, selH * baseAspect.coerceAtLeast(1f))),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = edge),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { items(ids.size, key = { ids[it] }) { cell(it) } }
        } else {
            // Not during a vertical swipe: the strip sits where the swipe up for the details starts,
            // and its sideways scroll took that swipe as soon as the finger drifted a little.
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                userScrollEnabled = !swipeLocked,
                modifier = Modifier.width(extent).height(maxOf(48.dp, selH)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = edge),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) { items(ids.size, key = { ids[it] }) { cell(it) } }
        }
    }
}

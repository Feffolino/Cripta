package com.cripta.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.cripta.app.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The split-view panel ("Video sopra il pannello"): unlike [CriptaSheet] it is part of the screen,
 * not a window above it, so the media above stays touchable (its controls, pause, seek) while the
 * panel is open. Fixed height; slides up on open, follows the finger when dragged by its handle or
 * pulled down from the top of its content, and closes past a third of the way or on a fling;
 * Back closes it too.
 */
@Stable
class SplitPanelState internal constructor(private val scope: CoroutineScope) {
    /** 1 = below the screen, 0 = fully open. */
    val hidden = Animatable(1f)
    internal var onClosed: () -> Unit = {}
    private var closing by mutableStateOf(false)

    fun open() {
        closing = false
        scope.launch { hidden.animateTo(0f, Motion.enter(Motion.LONG)) }
    }

    /** Slides the panel down, then reports it closed. */
    fun close() = closeThen {}

    /** Slides the panel down, reports it closed, then runs [action] (a dialog, the editor…). */
    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            hidden.animateTo(1f, Motion.exit(Motion.MEDIUM))
            closing = false
            onClosed()
            action()
        }
    }

    internal fun dragBy(fraction: Float) {
        if (closing) return
        scope.launch { hidden.snapTo((hidden.value + fraction).coerceIn(0f, 1f)) }
    }

    /** End of a drag: closes past a third of the way or on a downward fling, else springs back. */
    internal fun settle(velocityFraction: Float) {
        if (hidden.value > 0.33f || velocityFraction > 1.2f) close() else open()
    }
}

@Composable
fun rememberSplitPanelState(): SplitPanelState {
    val scope = rememberCoroutineScope()
    return remember { SplitPanelState(scope) }
}

/**
 * Lay it out filling the screen area it covers (it aligns itself to the bottom). [onTop] gets the
 * panel's top edge in px as it moves (-1 once gone), for the content above to fit the free space.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SplitPanel(
    state: SplitPanelState,
    heightFraction: Float,
    onDismiss: () -> Unit,
    onTop: (Float) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val latestDismiss by rememberUpdatedState(onDismiss)
    SideEffect { state.onClosed = { latestDismiss() } }
    LaunchedEffect(state) { state.open() }
    BackHandler(onBack = state::close)
    val latestTop by rememberUpdatedState(onTop)
    DisposableEffect(state) { onDispose { latestTop(-1f) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = constraints.maxHeight.toFloat()
        val panelH = fullH * heightFraction
        LaunchedEffect(state, fullH, panelH) {
            snapshotFlow { fullH - panelH * (1f - state.hidden.value) }.collect { latestTop(it) }
        }
        // Scrolling the content: pulled down at its top, the panel follows; pushed up while the
        // panel is partly down, the panel rises first.
        val nested = remember(state, panelH) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0f && state.hidden.value > 0f) {
                        state.dragBy(available.y / panelH); return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0f && source == NestedScrollSource.UserInput) {
                        state.dragBy(available.y / panelH); return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (state.hidden.value > 0f) { state.settle(available.y / panelH); return available }
                    return Velocity.Zero
                }
            }
        }
        val drag = rememberDraggableState { d -> state.dragBy(d / panelH) }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(with(LocalDensity.current) { panelH.toDp() })
                .graphicsLayer { translationY = panelH * state.hidden.value }
                // What the panel's children leave is consumed here: a swipe on the panel must not
                // reach the viewer's own gestures underneath (close the viewer, change file).
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                }
                .nestedScroll(nested),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().draggable(
                        state = drag,
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> state.settle(v / panelH) },
                    ),
                    contentAlignment = Alignment.Center,
                ) { BottomSheetDefaults.DragHandle() }
                content()
            }
        }
    }
}

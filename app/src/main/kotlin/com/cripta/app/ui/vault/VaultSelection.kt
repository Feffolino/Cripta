package com.cripta.app.ui.vault

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.cripta.app.ui.components.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.width
import com.cripta.app.ui.theme.Motion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.flow.first

/** "3 selezionati" in the top bar; the number rolls up when it grows and down when it shrinks. */
@Composable
internal fun SelectionCountTitle(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(Motion.enter()) { h -> if (up) h else -h } + fadeIn(Motion.enter())) togetherWith
                    (slideOutVertically(Motion.exit()) { h -> if (up) -h else h } + fadeOut(Motion.exit())) using
                    SizeTransform(clip = true)
            },
            label = "selCount",
        ) { n -> Text("$n", maxLines = 1) }
        // The suffix is a separate text (the number animates on its own): Android trims the
        // resource, so the gap between "3" and "selezionati" is drawn here.
        androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
        Text(if (count == 1) str(R.string.selected_suffix_one) else str(R.string.selected_suffix_many), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

internal class SelAction(val icon: ImageVector, val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The bottom selection bar. When its actions don't all fit (portrait), it scrolls sideways: the
 * last visible action is cut in half, the edge fades with a chevron pointing to the rest, and the
 * very first time it nudges on appearing to show that it moves.
 */
@Composable
internal fun SelectionBar(actions: List<SelAction>) {
    // A tone above the navigation bar below it, so the two read as different layers: actions on the
    // selection here, destinations there.
    androidx.compose.material3.BottomAppBar(
        contentPadding = PaddingValues(0.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Every action keeps its whole name: few actions share the width, many scroll sideways.
            val minW = maxOf(72.dp, maxWidth / actions.size)
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // The nudge only the very first time the actions overflow: after that the fade and the
            // chevron are enough, and sliding on every selection was too much.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(scroll.maxValue > 0) {
                if (scroll.maxValue == 0) return@LaunchedEffect
                val prefs = ctx.getSharedPreferences("vault_hints", android.content.Context.MODE_PRIVATE)
                val shown = prefs.getInt("selection_bar_nudges", 0)
                if (shown >= 1) return@LaunchedEffect
                prefs.edit().putInt("selection_bar_nudges", shown + 1).apply()
                kotlinx.coroutines.delay(350)
                val px = with(density) { 48.dp.roundToPx() }
                scroll.animateScrollTo(px, androidx.compose.animation.core.tween(320))
                scroll.animateScrollTo(0, androidx.compose.animation.core.tween(380))
            }
            val fadeW = with(density) { 28.dp.toPx() }
            Row(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val clear = androidx.compose.ui.graphics.Color.Transparent
                        val solid = androidx.compose.ui.graphics.Color.Black
                        if (if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) scroll.canScrollForward else scroll.canScrollBackward) drawRect(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(clear, solid), 0f, fadeW),
                            size = size.copy(width = fadeW), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                        if (if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) scroll.canScrollBackward else scroll.canScrollForward) drawRect(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(solid, clear), size.width - fadeW, size.width),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width - fadeW, 0f),
                            size = size.copy(width = fadeW), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { SelectionAction(it.icon, it.label, Modifier.widthIn(min = minW), it.destructive, it.onClick) }
            }
            // Chevrons at the edges while there is more that way.
            val fwd by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, label = "selFwd")
            val back by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, label = "selBack")
            Icon(CriptaIcons.KeyboardArrowRight, if (scroll.canScrollForward) str(R.string.more_actions) else null,
                Modifier.align(Alignment.CenterEnd).size(20.dp).graphicsLayer { alpha = fwd },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(CriptaIcons.KeyboardArrowLeft, null,
                Modifier.align(Alignment.CenterStart).size(20.dp).graphicsLayer { alpha = back },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One labelled action of the bottom selection bar: icon over its full name, never cut. */
@Composable
private fun SelectionAction(icon: ImageVector, label: String, modifier: Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(top = 4.dp))
    }
}

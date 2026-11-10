package com.cripta.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Motion tokens. Calm and short: motion only explains a state change (selection, navigation depth,
 * progress, lock/unlock) and never performs. Every curve is a pure ease-out (no overshoot, no bounce).
 *
 * Compose already scales every animation by the system "Animator duration scale", so with
 * "Remove animations" on they finish instantly. [animationsEnabled] covers the few places that
 * sequence work around an animation with plain delays, which that scale does not touch.
 */
object Motion {
    /** Press feedback, toggles, colour changes. */
    const val SHORT = 120
    /** Most state changes: show/hide, crossfades, selection. */
    const val MEDIUM = 220
    /** Screen transitions, reveal of a whole panel. */
    const val LONG = 320

    val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
    val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> enter(duration: Int = MEDIUM, delay: Int = 0): FiniteAnimationSpec<T> =
        tween(duration, delay, EaseOutQuart)

    /** Exits run at ~75% of the matching enter so leaving never feels slower than arriving. */
    fun <T> exit(duration: Int = MEDIUM): FiniteAnimationSpec<T> =
        tween((duration * 0.75f).toInt(), 0, EaseOutQuart)
}

/** False when the user turned animations off (Developer options / Accessibility "Remove animations"). */
@Composable
fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f
    }
}

/** Press-down scale for surfaces whose press state is tracked manually (e.g. grid-level gestures). */
fun Modifier.pressScale(isPressed: Boolean, pressed: Float = 0.96f): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = tween(if (isPressed) Motion.SHORT else Motion.MEDIUM, 0, Motion.EaseOutQuart),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Press-down scale driven by a clickable's [interactionSource]; complements its ripple. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    pressScale(isPressed, pressed)
}

/**
 * A small "it took" when [active] turns true (a star filled, a tag switched on): the element grows
 * from 0.7 to its size on the quint ease-out. No overshoot, and nothing on the way back or on the
 * first composition, so a list that opens with starred items stays still.
 */
fun Modifier.popOn(active: Boolean): Modifier = composed {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active && !previous) {
            scale.snapTo(0.7f)
            scale.animateTo(1f, tween(Motion.LONG, 0, Motion.EaseOutQuint))
        } else scale.snapTo(1f)
        previous = active
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * A refused attempt (wrong PIN or password): a short horizontal shake that dies out, three swings
 * in ~440 ms. Runs each time [trigger] grows; 0 means "never failed yet".
 */
fun Modifier.shakeOn(trigger: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(Motion.LONG + Motion.SHORT, 0, LinearEasing))
        }
    }
    graphicsLayer {
        val p = progress.value
        translationX = if (p <= 0f || p >= 1f) 0f else (sin(p * PI * 6).toFloat() * (1f - p) * 10.dp.toPx())
    }
}

/**
 * The first appearance of a calm block (an empty state): fades in while rising 12 dp, once.
 * [delay] staggers related blocks by ~60 ms each.
 */
fun Modifier.riseIn(delay: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(Motion.LONG, delay, Motion.EaseOutQuint)) }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 12.dp.toPx()
    }
}

/**
 * An element that arrives while its screen is already up (a tag just assigned to a cover): grows
 * in from 0.7 and fades in, once. With [enabled] false (it was there when the screen appeared, or
 * the list recycled it on scroll) it simply shows.
 */
fun Modifier.popIn(enabled: Boolean): Modifier = composed {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (enabled) progress.animateTo(1f, tween(Motion.LONG, 0, Motion.EaseOutQuint)) }
    graphicsLayer {
        val p = progress.value
        alpha = p
        val s = 0.7f + 0.3f * p
        scaleX = s
        scaleY = s
    }
}

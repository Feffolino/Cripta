package com.cripta.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

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

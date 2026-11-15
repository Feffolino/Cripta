package com.cripta.app.ui.viewer

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R

/** Called by the Compose effect; the native controller uses immediate visibility transitions. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerView.syncControllerVisibility(visible: Boolean) {
    if (visible && !isControllerFullyVisible) showController()
    else if (!visible) hideController()
}

/**
 * One timer per mode: Compose owns the normal viewer, Media3 owns split view. Updating the
 * PlayerView timeout calls showController(), so never set it again on unrelated recompositions.
 * Pausing only retains controls that are already visible; it does not open them.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerView.updateControllerTimeout(selfManaged: Boolean, paused: Boolean, timeoutMs: Int) {
    val desired = if (selfManaged && !paused) timeoutMs else 0
    if (controllerShowTimeoutMs != desired) controllerShowTimeoutMs = desired
}

/**
 * DS player (cr-round, cr-seek): rewind, play/pause and forward on dark glass discs, previous and
 * next bare; the seek bar in the accent over a 30% white track. Media3 draws these views, so they
 * are styled here once, when the view is made. Controls absent from the layout are skipped.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerView.styleCriptaController(accent: Int) {
    val glass = 0x66000000
    val track = 0x4DFFFFFF
    listOf(R.id.exo_rew_with_amount, R.id.exo_rew, R.id.exo_play_pause, R.id.exo_ffwd_with_amount, R.id.exo_ffwd).forEach { id ->
        findViewById<android.view.View>(id)?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(glass)
        }
    }
    (findViewById<android.view.View>(R.id.exo_progress) as? androidx.media3.ui.DefaultTimeBar)?.apply {
        setPlayedColor(accent)
        setScrubberColor(accent)
        setBufferedColor(track)
        setUnplayedColor(track)
    }
}

/**
 * Rewind and forward discs: the Tabler "rewind N" glyph (the step drawn in it) on the glass disc,
 * and one step for these buttons and the double tap. Media3's own amount text is hidden (the glyph
 * carries it) and a press seeks by [stepSec]: the player's increments (5 s back, 15 s on by
 * default) are fixed when it is built, while the step setting applies live.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerView.setSeekButtons(stepSec: Int, seekBy: (Long) -> Unit) {
    val glyphPx = (28 * resources.displayMetrics.density).toInt()
    fun disc(icon: Int) = android.graphics.drawable.LayerDrawable(arrayOf(
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x66000000)
        },
        context.getDrawable(icon)!!.mutate().apply { setTint(android.graphics.Color.WHITE) },
    )).apply {
        setLayerGravity(1, android.view.Gravity.CENTER)
        setLayerSize(1, glyphPx, glyphPx)
    }
    val (back, forward) = when (stepSec) {
        5 -> com.cripta.app.R.drawable.tabler_rewind_backward_5 to com.cripta.app.R.drawable.tabler_rewind_forward_5
        30 -> com.cripta.app.R.drawable.tabler_rewind_backward_30 to com.cripta.app.R.drawable.tabler_rewind_forward_30
        else -> com.cripta.app.R.drawable.tabler_rewind_backward_10 to com.cripta.app.R.drawable.tabler_rewind_forward_10
    }
    val stepMs = stepSec * 1000L
    listOf(R.id.exo_rew_with_amount to -stepMs, R.id.exo_ffwd_with_amount to stepMs).forEach { (id, delta) ->
        (findViewById<android.view.View>(id) as? android.widget.TextView)?.apply {
            background = disc(if (delta < 0) back else forward)
            setTextColor(android.graphics.Color.TRANSPARENT)
            contentDescription = resources.getQuantityString(
                if (delta < 0) R.plurals.exo_controls_rewind_by_amount_description
                else R.plurals.exo_controls_fastforward_by_amount_description,
                stepSec, stepSec,
            )
            setOnClickListener { seekBy(delta) }
        }
    }
}

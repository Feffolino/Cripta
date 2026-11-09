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

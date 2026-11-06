package com.cripta.app.ui.viewer

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

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
 * are styled here once, when the view is made. Ids are looked up by name: a missing one is skipped.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerView.styleCriptaController(accent: Int) {
    val glass = 0x66000000
    val track = 0x4DFFFFFF
    fun view(name: String): android.view.View? =
        resources.getIdentifier(name, "id", context.packageName).takeIf { it != 0 }?.let { findViewById(it) }
    listOf("exo_rew_with_amount", "exo_rew", "exo_play_pause", "exo_ffwd_with_amount", "exo_ffwd").forEach { name ->
        view(name)?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(glass)
        }
    }
    (view("exo_progress") as? androidx.media3.ui.DefaultTimeBar)?.apply {
        setPlayedColor(accent)
        setScrubberColor(accent)
        setBufferedColor(track)
        setUnplayedColor(track)
    }
}

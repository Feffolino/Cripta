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

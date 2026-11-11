package com.cripta.app.viewer

import android.util.Rational
import com.cripta.app.R
import com.cripta.app.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between the video player and the activity for Picture-in-Picture: the player "arms" it
 * while a video plays (and the user enabled the option); the activity enters PiP when the user
 * leaves the app, and reports whether it is currently in PiP so the player hides its chrome.
 */
object PipController {
    /** Aspect ratio of the playing video when PiP is allowed now; null = don't enter PiP. */
    @Volatile var armedAspect: Rational? = null

    /** Whether the armed video is playing (the window's button then pauses it). */
    @Volatile var playing: Boolean = true

    /** Broadcast of the window's play / pause button, handled by the player on screen. */
    const val ACTION_PLAY_PAUSE = "com.cripta.app.PIP_PLAY_PAUSE"

    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip
    fun setInPip(v: Boolean) { _inPip.value = v }

    /**
     * The window's parameters: [aspect], and one play / pause button (the player's own controls
     * are off in PiP, and with no button the video could only be stopped by going back to the app).
     */
    fun params(ctx: android.content.Context, aspect: Rational, playing: Boolean): android.app.PictureInPictureParams {
        val intent = android.content.Intent(ACTION_PLAY_PAUSE).setPackage(ctx.packageName)
        val pending = android.app.PendingIntent.getBroadcast(ctx, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val icon = android.graphics.drawable.Icon.createWithResource("android",
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val label = str(if (playing) R.string.pip_pause else R.string.pip_play)
        return android.app.PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .setActions(listOf(android.app.RemoteAction(icon, label, label, pending)))
            .build()
    }
}

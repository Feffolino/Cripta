package com.cripta.app.viewer

import android.util.Rational
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

    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip
    fun setInPip(v: Boolean) { _inPip.value = v }
}

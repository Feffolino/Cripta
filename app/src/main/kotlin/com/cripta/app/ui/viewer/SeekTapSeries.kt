package com.cripta.app.ui.viewer

/**
 * Repeated taps on the player's side seek zones, YouTube style: a double tap seeks one step and
 * opens a series; every further press on the same side within [windowMs] of the last step seeks
 * one more step at once. Presses taken as a step must not also toggle the controls: the zone's
 * gesture detector still reports them as a single or double tap, so it checks [lastPressSeeked].
 */
class SeekTapSeries(private val windowMs: Long = 800) {
    enum class Side { Back, Forward }

    private var side: Side? = null
    private var until = 0L

    /** Steps in the current series (for the cumulative "+30s" label). */
    var steps = 0
        private set

    /** Whether the latest press was taken as a step; its tap / double tap must then do nothing. */
    var lastPressSeeked = false
        private set

    /** A finger went down on [side]. True = seek one more step now. */
    fun onPress(side: Side, now: Long): Boolean {
        lastPressSeeked = this.side == side && now <= until
        if (lastPressSeeked) { steps++; until = now + windowMs } else { this.side = null; steps = 0 }
        return lastPressSeeked
    }

    /** A double tap on [side] finished. True = seek one step (the first of a new series). */
    fun onDoubleTap(side: Side, now: Long): Boolean {
        if (lastPressSeeked) return false
        this.side = side; steps = 1; until = now + windowMs
        return true
    }
}

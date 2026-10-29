package com.cripta.app.ui.viewer

/**
 * What the player does about a known intro or outro at a playback position: show "Salta intro" /
 * "Salta finale", or skip by itself once when the user turned that on. The button's X hides it for
 * the rest of this viewing; "Annulla" after an automatic skip stops it auto-skipping again.
 */
class SegmentSkipper {
    enum class Kind { INTRO, OUTRO }

    data class Segment(val kind: Kind, val startMs: Long, val endMs: Long)

    sealed interface Action {
        data object None : Action
        data class Show(val segment: Segment) : Action
        data class AutoSkip(val segment: Segment) : Action
    }

    private val dismissed = HashSet<Kind>()
    private val undone = HashSet<Kind>()
    private val autoDone = HashSet<Kind>()

    fun at(positionMs: Long, segments: List<Segment>, scrubbing: Boolean, autoIntro: Boolean, autoOutro: Boolean): Action {
        if (scrubbing) return Action.None
        val s = segments.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - END_SLACK_MS } ?: return Action.None
        val auto = if (s.kind == Kind.INTRO) autoIntro else autoOutro
        if (auto && s.kind !in undone && s.kind !in autoDone) {
            autoDone += s.kind
            return Action.AutoSkip(s)
        }
        return if (s.kind in dismissed) Action.None else Action.Show(s)
    }

    fun dismiss(kind: Kind) { dismissed += kind }
    fun undo(kind: Kind) { undone += kind }

    companion object {
        /** No button in a segment's last half second: skipping there gains nothing. */
        const val END_SLACK_MS = 500L
    }
}

package com.cripta.app.media.face

/** Which moments of a video are looked at: one every [STEP_MS], at least [MIN] and at most [MAX], evenly spread. */
object FramePlan {
    const val STEP_MS = 10_000L
    const val MIN = 8
    const val MAX = 40

    fun times(durationMs: Long): List<Long> {
        if (durationMs <= 0) return listOf(0L)
        val n = (durationMs / STEP_MS).toInt().coerceIn(MIN, MAX)
        return List(n) { i -> (durationMs * (2 * i + 1) / (2L * n)) }
    }
}

package com.cripta.app.media.face

/** Which moments of a video are looked at: one every [STEP_MS], at least [MIN] and at most [MAX], evenly spread. */
object FramePlan {
    const val STEP_MS = 10_000L
    const val MIN = 8
    const val MAX = 40

    /**
     * [deep] ("Cerca più a fondo"): twice as many frames where the plan allows it, else (long
     * videos, already at [MAX]) the frames halfway between the usual ones, so the search sees new
     * moments either way.
     */
    fun times(durationMs: Long, deep: Boolean = false): List<Long> {
        if (durationMs <= 0) return listOf(0L)
        val n = (durationMs / STEP_MS).toInt().coerceIn(MIN, MAX)
        if (!deep) return List(n) { i -> (durationMs * (2 * i + 1) / (2L * n)) }
        val m = minOf(MAX, 2 * n)
        return if (m > n) List(m) { i -> durationMs * (2 * i + 1) / (2L * m) } else List(n) { i -> durationMs * (i + 1) / (n + 1L) }
    }
}

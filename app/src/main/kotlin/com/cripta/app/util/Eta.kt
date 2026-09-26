package com.cripta.app.util

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Remaining time of a job from its progress (0..1) over time, one calculation for the
 * notifications, the Hyper Island and the screens, so they all tell the same thing.
 *
 *  - Measured from the moment the job starts moving: a link being analysed or a video being
 *    prepared sits at 0 for a while, and counting that time made the first estimates far too long.
 *  - The speed is taken between one advance of the progress and the next, not per callback: a
 *    job reporting whole percents, or reporting every 500 ms with nothing new, looked alternately
 *    frozen and racing. It is smoothed over about [SMOOTHING_MS], weighted by time, so it follows
 *    a real change of pace (a large file after small ones) and not a single slow moment.
 *  - No advance for longer than one usually takes: the speed decays, so a stalled job's estimate
 *    grows honestly instead of counting down to zero and staying there.
 *  - Nothing at first (under [WARMUP_MS] or [WARMUP_PROGRESS] of measurement a job spinning up
 *    promises hours), at the very end, or beyond [MAX_MS].
 *  - The value counts down steadily while new estimates stay close to it (within 10 %, at least
 *    3 s), so the text doesn't flicker between neighbours; it moves when they drift apart.
 *  - Progress going backwards (another job, a new batch) starts over.
 *
 * [update] takes the time as SystemClock.elapsedRealtime() (any monotonic ms clock).
 */
class EtaEstimator {
    private var started = false
    private var moving = false
    private var startT = 0L
    private var startP = 0f
    private var lastT = 0L
    private var lastP = 0f
    /** Progress per ms, smoothed; < 0 while no advance has been measured. */
    private var rate = -1.0
    /** Usual time between two advances, smoothed. */
    private var stepMs = 0.0
    private var shownMs = -1L
    private var shownAt = 0L

    /** Remaining time in ms at [progress] at [now], or null while there is no honest estimate. */
    @Synchronized
    fun update(progress: Float, now: Long): Long? {
        val p = progress.coerceIn(0f, 1f)
        if (!started || p < lastP - 0.001f) {
            started = true; moving = false
            startT = now; startP = p; lastT = now; lastP = p
            rate = -1.0; stepMs = 0.0; shownMs = -1L
            return null
        }
        if (p > lastP) {
            if (!moving) {
                // First advance: the measurement starts here.
                moving = true
                startT = now; startP = p
            } else {
                val dt = (now - lastT).coerceAtLeast(1L).toDouble()
                val inst = (p - lastP) / dt
                if (rate < 0) {
                    rate = inst; stepMs = dt
                } else {
                    val a = 1 - exp(-dt / SMOOTHING_MS)
                    rate += a * (inst - rate)
                    stepMs += a * (dt - stepMs)
                }
            }
            lastT = now; lastP = p
        }
        if (rate <= 0 || p >= 0.999f || now - startT < WARMUP_MS || p - startP < WARMUP_PROGRESS) return null
        val idle = (now - lastT) - stepMs
        val r = if (idle > 0) rate * exp(-idle / SMOOTHING_MS) else rate
        val raw = (1 - p) / r
        if (raw.isNaN() || raw > MAX_MS) return null
        return steady(raw.toLong(), now)
    }

    private fun steady(raw: Long, now: Long): Long {
        if (shownMs >= 0) {
            val countdown = (shownMs - (now - shownAt)).coerceAtLeast(0L)
            if (abs(raw - countdown) <= max(countdown * 0.1, 3_000.0)) return countdown
        }
        shownMs = raw; shownAt = now
        return raw
    }

    companion object {
        const val WARMUP_MS = 3_000L
        const val WARMUP_PROGRESS = 0.01f
        const val SMOOTHING_MS = 10_000.0
        const val MAX_MS = 24 * 3_600_000L
    }
}

/**
 * Weight of one file in a batch's progress: its size plus a fixed cost for the work every file
 * takes whatever its size (reading its details, the duplicate check, the database), roughly what
 * encrypting a few MB takes. Counted by file alone, a 2 GB video weighed as much as a photo and the
 * progress, hence the time left, raced through the photos and then crawled.
 */
fun batchWeight(bytes: Long): Long = bytes.coerceAtLeast(0L) + PER_FILE_BYTES

private const val PER_FILE_BYTES = 4L shl 20

/** "circa 3 min rimanenti": the screens' form, rounded so it reads as an estimate, not a countdown. */
fun formatEta(ms: Long): String = etaAmount(ms)?.let { "circa $it rimanenti" } ?: "pochi secondi rimanenti"

/** "resta circa 3 min": the shorter form of the notifications and the Hyper Island. */
fun formatEtaShort(ms: Long): String = etaAmount(ms)?.let { "resta circa $it" } ?: "pochi secondi"

/** "45 s" (by 5 s), "3 min", "1 h 20 min", rounded up; null under 10 s. */
internal fun etaAmount(ms: Long): String? {
    val s = ms.coerceAtLeast(0L) / 1000
    if (s < 10) return null
    if (s < 55) return "${(s + 4) / 5 * 5} s"
    val min = (s + 59) / 60
    if (min < 60) return "$min min"
    val h = min / 60
    val m = min % 60
    return if (m == 0L) "$h h" else "$h h $m min"
}

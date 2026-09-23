package com.cripta.app.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Remaining-time estimate from a job's progress rate. Silent at first (the first few seconds and
 * percent say little: a spinning-up job would promise hours), then smoothed so it doesn't jump on
 * every tick. Progress going backwards (a new file of a batch) starts a fresh measurement.
 */
internal class EtaTracker {
    private var t0 = -1L
    private var p0 = 0f
    private var lastP = 0f
    private var smoothed: Double? = null

    fun update(p: Float, now: Long): Long? {
        if (t0 < 0 || p < lastP - 0.001f) { t0 = now; p0 = p; smoothed = null }
        lastP = p
        val dp = p - p0
        val dt = now - t0
        if (p >= 0.999f || dp < 0.02f || dt < 3_000) return null
        val remaining = (1.0 - p) / (dp / dt)
        smoothed = smoothed?.let { it * 0.7 + remaining * 0.3 } ?: remaining
        return smoothed?.toLong()
    }
}

/** "circa 3 min rimanenti" and similar, rounded so it reads as an estimate, not a countdown. */
fun formatEta(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 10 -> "pochi secondi rimanenti"
        s < 60 -> "circa ${((s + 4) / 5) * 5} s rimanenti"
        s < 3600 -> "circa ${(s + 59) / 60} min rimanenti"
        else -> {
            val h = s / 3600
            val m = (s % 3600 + 59) / 60
            if (m == 0L) "circa $h h rimanenti" else "circa $h h $m min rimanenti"
        }
    }
}

/**
 * Live remaining-time text for a job at [progress] (0..1) while [running]; null until there is
 * enough data. Re-evaluated every second so a stalled job's estimate grows honestly. A new [key]
 * (another job) starts over.
 */
@Composable
fun rememberEta(progress: Float, running: Boolean = true, key: Any? = Unit): String? {
    val tracker = remember(key) { EtaTracker() }
    var text by remember(key) { mutableStateOf<String?>(null) }
    val latest by rememberUpdatedState(progress)
    LaunchedEffect(tracker, running) {
        if (!running) { text = null; return@LaunchedEffect }
        while (true) {
            text = tracker.update(latest.coerceIn(0f, 1f), SystemClock.elapsedRealtime())?.let(::formatEta)
            delay(1_000)
        }
    }
    return if (running) text else null
}

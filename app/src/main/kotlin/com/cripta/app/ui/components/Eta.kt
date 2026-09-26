package com.cripta.app.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.cripta.app.util.EtaEstimator
import com.cripta.app.util.formatEta
import kotlinx.coroutines.delay

/**
 * Live remaining-time text for a job at [progress] (0..1) while [running]; null until there is
 * enough data. The same calculation as the notifications ([EtaEstimator]), re-evaluated every
 * second so a stalled job's estimate grows honestly. A new [key] (another job) starts over.
 */
@Composable
fun rememberEta(progress: Float, running: Boolean = true, key: Any? = Unit): String? {
    val tracker = remember(key) { EtaEstimator() }
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

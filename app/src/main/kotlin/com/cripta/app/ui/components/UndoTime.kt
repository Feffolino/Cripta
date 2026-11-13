package com.cripta.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How long an "Annulla" is on offer, the same on every page: 8 s, or longer when the
 * accessibility settings ask for it.
 */
@Composable
fun rememberUndoMillis(): Long {
    val a11y = LocalAccessibilityManager.current
    return remember(a11y) {
        (a11y?.calculateRecommendedTimeoutMillis(UNDO_MS, containsIcons = false, containsText = true, containsControls = true) ?: UNDO_MS)
            .coerceAtLeast(UNDO_MS)
    }
}

/** Whether the vault is locked now, readable from a coroutine (see [waitUnlocked]). */
@Composable
fun rememberVaultLocked(): State<Boolean> = rememberUpdatedState(com.cripta.app.ui.LocalVaultLocked.current)

/**
 * Wait [ms], not counting the time the vault is [locked]: the chance to undo is not used up behind
 * the lock screen.
 */
suspend fun waitUnlocked(ms: Long, locked: State<Boolean>) {
    var left = ms
    while (left > 0) {
        val step = minOf(TICK_MS, left)
        delay(step)
        if (!locked.value) left -= step
    }
}

/**
 * "[message] · Annulla" in [host] for [ms] ([rememberUndoMillis]), paused while the vault is
 * [locked]. True when the user tapped "Annulla"; false when it went by.
 */
suspend fun SnackbarHostState.showUndo(message: String, action: String, ms: Long, locked: State<Boolean>): Boolean = coroutineScope {
    val timer = launch { waitUnlocked(ms, locked); currentSnackbarData?.dismiss() }
    val tapped = showSnackbar(message, action, duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed
    timer.cancel()
    tapped
}

private const val UNDO_MS = 8_000L
private const val TICK_MS = 250L

package com.cripta.app.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A backup or restore handed from Settings to the background service, which runs it in the
 * foreground: in the view model, a long backup with the app in the background died with the
 * process (a truncated archive left behind, a restore half done).
 *
 * The passphrase never travels in an Intent: the request waits here, in memory, until the service
 * takes it ([take]); whoever takes it wipes it.
 */
@Singleton
class BackupJobs @Inject constructor() {

    class Request(val restore: Boolean, val uri: Uri, val passphrase: CharArray, val compressDocs: Boolean)

    /** What runs now: its label and progress (0..1, null while unknown). */
    data class Running(val label: String, val progress: Float?)

    private val _pending = MutableStateFlow<Request?>(null)
    private val _running = MutableStateFlow<Running?>(null)
    val running: StateFlow<Running?> = _running
    private val _result = MutableStateFlow<String?>(null)
    /** The outcome of the last one, until Settings shows it ([consumeResult]). */
    val result: StateFlow<String?> = _result

    /** Queue [request]; false (and the passphrase wiped) when one is already waiting or running. */
    @Synchronized fun offer(request: Request): Boolean {
        if (_pending.value != null || _running.value != null) {
            request.passphrase.fill('\u0000')
            return false
        }
        _pending.value = request
        _running.value = Running(if (request.restore) "Ripristino del backup…" else "Creazione del backup…", null)
        return true
    }

    /** The waiting request (once), or null. */
    fun take(): Request? = _pending.getAndUpdate { null }

    fun progress(p: Float?) {
        _running.value = _running.value?.copy(progress = p)
    }

    fun finish(message: String) {
        _running.value = null
        _result.value = message
    }

    fun consumeResult() { _result.value = null }
}

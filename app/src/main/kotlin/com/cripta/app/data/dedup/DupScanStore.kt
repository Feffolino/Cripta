package com.cripta.app.data.dedup

import com.cripta.app.data.db.FileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state of the duplicate scan, which runs in the foreground service so it keeps going with
 * the app in the background or the screen off. The Settings screen reads progress and results
 * from here; a tap on the "scan finished" notification asks the app to open the results.
 */
@Singleton
class DupScanStore @Inject constructor() {

    enum class Mode { EXACT, SIMILAR }

    data class Result(
        val mode: Mode,
        /** Groups of 2+ files, each sorted oldest import first. */
        val groups: List<List<FileEntity>>,
        /** How many files/media were examined. */
        val scanned: Int,
        val finishedAt: Long = System.currentTimeMillis(),
        /** True once the user has looked at it. */
        val seen: Boolean = false,
    )

    data class State(
        val running: Mode? = null,
        val done: Int = 0,
        val total: Int = 0,
        val result: Result? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val _openRequested = MutableStateFlow(false)
    /** Set by a tap on the result notification; the app navigates to the results and clears it. */
    val openRequested: StateFlow<Boolean> = _openRequested

    fun start(mode: Mode) = _state.update { State(running = mode, result = it.result) }
    fun progress(done: Int, total: Int) = _state.update { it.copy(done = done, total = total) }
    fun finish(result: Result) = _state.update { State(result = result) }
    fun fail(message: String?) = _state.update { State(result = it.result, error = message) }
    fun cancelled() = _state.update { State(result = it.result) }
    fun markSeen() = _state.update { s -> s.copy(result = s.result?.copy(seen = true), error = null) }
    fun clearResult() = _state.update { it.copy(result = null) }

    /** Drop deleted files from the stored result (groups left with one file disappear). */
    fun dropFiles(ids: Set<String>) = _state.update { s ->
        s.copy(result = s.result?.let { r ->
            r.copy(groups = r.groups.map { g -> g.filterNot { it.id in ids } }.filter { it.size > 1 })
        })
    }

    fun requestOpen() { _openRequested.value = true }
    fun consumeOpen() { _openRequested.value = false }
}

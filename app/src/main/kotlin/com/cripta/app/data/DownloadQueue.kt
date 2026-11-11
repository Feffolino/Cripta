package com.cripta.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadPhase { QUEUED, PREPARING, DOWNLOADING, DONE, FAILED, CANCELLED }

/** One link in the downloader queue, with its destination and live progress. */
data class DownloadJob(
    val id: String,
    val url: String,
    val maxHeight: Int? = null,
    val folderId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val phase: DownloadPhase = DownloadPhase.QUEUED,
    val pct: Int = 0,
    val etaSec: Long = 0,
    /** File name once known, or the failure reason. */
    val message: String? = null,
    /** Id of the imported file once done (for "Apri"). */
    val fileId: String? = null,
) {
    val active: Boolean get() = phase == DownloadPhase.PREPARING || phase == DownloadPhase.DOWNLOADING
    val finished: Boolean get() = phase == DownloadPhase.DONE || phase == DownloadPhase.FAILED || phase == DownloadPhase.CANCELLED
}

/**
 * The downloader queue, kept in memory for the whole process: the screen shows it, the background
 * service works through it. A waiting link also has a pending job in the database (so it survives
 * the process being killed); removing the link drops that job too.
 */
@Singleton
class DownloadQueue @Inject constructor(private val repo: VaultRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The saved jobs are written and removed one at a time, in this order: a link removed right
     * after being added had its record deleted before it was written, and came back at the next start.
     */
    private val records = Mutex()

    private val _downloads = MutableStateFlow<List<DownloadJob>>(emptyList())
    /** The downloader queue, oldest first: running, waiting and recently finished links. */
    val downloads: StateFlow<List<DownloadJob>> = _downloads

    fun enqueueDownload(job: DownloadJob) = _downloads.update { it + job }

    /**
     * Save [job]'s record ([payload]) so a waiting link survives the app being killed. Not written
     * when the link is no longer waiting or running by then (removed, or already finished).
     */
    fun persist(job: DownloadJob, payload: String) {
        scope.launch {
            records.withLock {
                if (_downloads.value.any { it.id == job.id && !it.finished }) repo.putPendingJob(job.id, "download", payload)
            }
        }
    }

    /** Drop [id]'s saved record, after any write of it still waiting. */
    suspend fun forget(id: String) = records.withLock { repo.deletePendingJob(id) }

    fun updateDownload(id: String, f: (DownloadJob) -> DownloadJob) =
        _downloads.update { list -> list.map { if (it.id == id) f(it) else it } }
    /** Claim the next waiting link (marks it PREPARING), or null when the queue is drained. */
    @Synchronized fun takeNextDownload(): DownloadJob? {
        val next = _downloads.value.firstOrNull { it.phase == DownloadPhase.QUEUED } ?: return null
        updateDownload(next.id) { it.copy(phase = DownloadPhase.PREPARING) }
        return next
    }
    /**
     * Remove a waiting or finished link from the list (a running one must be cancelled first).
     * Under the same lock as [takeNextDownload]: between its read and its marking the link looked
     * removable, and was handed to the service after it had vanished from the list.
     */
    @Synchronized fun removeDownload(id: String) {
        val removable = _downloads.value.any { it.id == id && !it.active }
        _downloads.update { list -> list.filterNot { it.id == id && !it.active } }
        if (removable) scope.launch { forget(id) }
    }
    fun clearFinishedDownloads() = _downloads.update { list -> list.filterNot { it.finished } }
}

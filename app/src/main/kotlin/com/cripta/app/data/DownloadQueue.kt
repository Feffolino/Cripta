package com.cripta.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _downloads = MutableStateFlow<List<DownloadJob>>(emptyList())
    /** The downloader queue, oldest first: running, waiting and recently finished links. */
    val downloads: StateFlow<List<DownloadJob>> = _downloads

    fun enqueueDownload(job: DownloadJob) = _downloads.update { it + job }
    fun updateDownload(id: String, f: (DownloadJob) -> DownloadJob) =
        _downloads.update { list -> list.map { if (it.id == id) f(it) else it } }
    /** Claim the next waiting link (marks it PREPARING), or null when the queue is drained. */
    @Synchronized fun takeNextDownload(): DownloadJob? {
        val next = _downloads.value.firstOrNull { it.phase == DownloadPhase.QUEUED } ?: return null
        updateDownload(next.id) { it.copy(phase = DownloadPhase.PREPARING) }
        return next
    }
    /** Remove a waiting or finished link from the list (a running one must be cancelled first). */
    fun removeDownload(id: String) {
        val removable = _downloads.value.any { it.id == id && !it.active }
        _downloads.update { list -> list.filterNot { it.id == id && !it.active } }
        if (removable) scope.launch { repo.deletePendingJob(id) }
    }
    fun clearFinishedDownloads() = _downloads.update { list -> list.filterNot { it.finished } }
}

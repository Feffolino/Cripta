package com.cripta.app.work

import com.cripta.app.str
import com.cripta.app.plural

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.cripta.app.R
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.media.VideoConverter
import com.cripta.app.util.EtaEstimator
import com.cripta.app.util.batchWeight
import com.cripta.app.util.formatEtaShort
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Foreground service that runs long crypto operations (encrypt-import, decrypt-download,
 * video transcode) off the UI, surviving app backgrounding, with a progress notification
 * showing percentage and ETA.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@AndroidEntryPoint
class ConversionService : Service() {

    @Inject lateinit var repo: VaultRepository
    @Inject lateinit var downloadQueue: com.cripta.app.data.DownloadQueue
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var converter: VideoConverter
    @Inject lateinit var ytdlp: com.cripta.app.media.YtdlpDownloader
    @Inject lateinit var thumbs: com.cripta.app.media.ThumbnailLoader
    @Inject lateinit var dupScanner: com.cripta.app.data.dedup.DuplicateScanner
    @Inject lateinit var dupStore: com.cripta.app.data.dedup.DupScanStore
    @Inject lateinit var session: com.cripta.app.security.SessionManager
    @Inject lateinit var updater: com.cripta.app.update.AppUpdater
    @Inject lateinit var intro: com.cripta.app.data.intro.IntroRepository
    @Inject lateinit var faces: com.cripta.app.data.face.FaceRepository
    @Inject lateinit var backups: com.cripta.app.data.BackupJobs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Number of in-flight commands; the foreground notification is only torn down when it hits 0,
     *  so a short task (e.g. import) can't stop the service while a long transcode is still running. */
    private val active = java.util.concurrent.atomic.AtomicInteger(0)
    private val originalChoiceMutex = kotlinx.coroutines.sync.Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Keeps the CPU running while the service lives (it stops itself once idle): a foreground
     * service alone doesn't, so with the screen off a conversion, a face scan or a download would
     * stall until the phone woke up. The timeout only bounds a stuck job.
     */
    private val wakeLock by lazy {
        getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Cripta:work")
            .apply { setReferenceCounted(false) }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching { wakeLock.acquire(6 * 60 * 60 * 1000L) }
    }

    override fun onDestroy() {
        runCatching { if (wakeLock.isHeld) wakeLock.release() }
        // An outcome still in the island moves to its own notification now (it went with the service).
        synchronized(lock) {
            main.removeCallbacks(flushProgress); pendingProgress = null
            hold?.let { endHold(it, moveOn = true) }
        }
        // Nothing may outlive the service: cancel whatever is still attached to its scope. A
        // yt-dlp process is native and blocking: cancelling its coroutine doesn't stop it.
        ytdlpProcessId?.let { ytdlp.cancel(it) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EX_MODE)
        if (mode == null) { stopIfIdle(startId); return START_NOT_STICKY }
        // Cancel request from the notification action: abort the running transcode/download and leave.
        if (mode == MODE_CANCEL_SCAN) {
            scanJob?.cancel()
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        if (mode == MODE_CANCEL_FACES) {
            // What was asked before the cancel is dropped; the scan stops within the frame or the
            // photo in hand, and says so meanwhile (the notification and Settings).
            faceScanAll = false; faceAsked.clear(); faceRerun = false
            // A media asked for the deep way and dropped here would have been searched deep the next
            // time it was analysed, asked or not.
            faceDeep.clear(); faceRefine.clear(); faceRefineForced.clear()
            faceJob?.let { job ->
                faceStopping = true
                job.cancel()
                scope.launch { faceRepost?.invoke() }
            }
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        if (mode == MODE_CANCEL_INTRO) {
            introJob?.cancel()
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        // Import / export: stop after the file being processed (never half a file).
        if (mode == MODE_CANCEL_BATCH) {
            batchCancels.incrementAndGet()
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        // "Elimina originali / Mantieni" of an import with the "Chiedi" policy (notification or app).
        if (mode == MODE_ORIGINALS_DELETE || mode == MODE_ORIGINALS_KEEP) {
            cancelOutcome(ResultKind.IMPORT.id)
            val uris = repo.pendingOriginals.value
            repo.clearPendingOriginals()
            if (mode == MODE_ORIGINALS_KEEP || uris.isEmpty()) { stopIfIdle(startId); return START_NOT_STICKY }
            beginJob(startId)
            scope.launch {
                try {
                    repo.deleteOriginals(uris)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ConversionService", "delete originals failed", e)
                } finally {
                    if (active.decrementAndGet() == 0) stopWhenIdle()
                }
            }
            return START_NOT_STICKY
        }
        // "Riprova" on a failed download: only with the vault open (it writes into the vault; the
        // button must not do anything for someone holding the phone while Cripta is locked).
        if (mode == MODE_DOWNLOAD_URL && intent.getBooleanExtra(EX_RETRY, false) && session.locked.value) {
            ensureChannel()
            // Started as a foreground service (the button's PendingIntent): it must go foreground
            // before leaving, even to do nothing.
            // On HyperOS the answer then shows in the island in its place.
            startForeground(NOTIF_ID, build(str(R.string.n_download), 0, indeterminate = true, kind = ResultKind.DOWNLOAD))
            notifyResult(str(R.string.dl_err_failed), str(R.string.n_unlock_then_retry), ResultKind.DOWNLOAD, state = ResultState.FAILED,
                actions = listOf(retryAction(intent)))
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        if (mode == MODE_CANCEL_UPDATE) {
            updateJob?.cancel()
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        // Debug isola: sample notifications to try the variants without a real operation.
        if (mode == MODE_CANCEL_DEBUG) { debugJob?.cancel(); stopIfIdle(startId); return START_NOT_STICKY }
        if (mode == MODE_DEBUG_DISMISS) {
            // Any sample outcome still in the island goes too (their buttons all land here).
            synchronized(lock) { hold?.let { endHold(it, moveOn = false) } }
            getSystemService(NotificationManager::class.java).cancel(DEBUG_NOTIF_ID)
            stopIfIdle(startId); return START_NOT_STICKY
        }
        // A choice answered in the app: its notification goes, and from the island too.
        if (mode == MODE_DROP_OUTCOME) {
            cancelOutcome(intent.getIntExtra(EX_NOTIF_ID, 0))
            stopIfIdle(startId); return START_NOT_STICKY
        }
        if (mode == MODE_DEBUG_CHOICE) {
            ensureChannel()
            // Foreground, as a real choice: HyperOS only puts the service's own notification in the island.
            startForeground(NOTIF_ID, build(str(R.string.n_test_choice), 0, indeterminate = true, kind = ResultKind.CONVERT))
            postChoice(
                DEBUG_NOTIF_ID, ResultKind.CONVERT, str(R.string.n_test_choice), str(R.string.n_test_two_buttons),
                serviceAction(str(R.string.delete), MODE_DEBUG_DISMISS, 20, icon = R.drawable.ic_action_delete),
                serviceAction(str(R.string.keep), MODE_DEBUG_DISMISS, 21, icon = R.drawable.ic_action_keep),
            )
            stopIfIdle(startId); return START_NOT_STICKY
        }
        if (mode == MODE_CANCEL_CONVERT) {
            currentConvertJob?.cancel()
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        if (mode == MODE_CANCEL) {
            cancelRequested = true
            ytdlpProcessId?.let { ytdlp.cancel(it) }
            // A download is cancelled by killing its yt-dlp process above, so the rest of the
            // download queue keeps going. (Conversions have their own cancel command.)
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        // Resolve each queued original independently; only acknowledge a deletion after it succeeds.
        if (mode == MODE_DELETE_ORIG || mode == MODE_DISMISS) {
            val oid = intent.getStringExtra(EX_ID)
            if (oid == null) { stopIfIdle(startId); return START_NOT_STICKY }
            if (session.locked.value) {
                notifyResult(str(R.string.n_original_unchanged), str(R.string.n_unlock_then_choose), ResultKind.CONVERT,
                    state = ResultState.FAILED, island = false)
                stopIfIdle(startId)
                return START_NOT_STICKY
            }
            beginJob(startId)
            val held = session.beginWork()
            scope.launch {
                try {
                    if (!held) return@launch
                    originalChoiceMutex.withLock {
                        // Ignore a stale/double notification action after this original was kept.
                        val choice = repo.convertStatus.value.pendingOriginals.firstOrNull { it.id == oid } ?: return@withLock
                        val delete = mode == MODE_DELETE_ORIG
                        if (delete) {
                            val copy = choice.copyId.takeIf { it.isNotBlank() }?.let { repo.fileById(it) }
                            check(copy != null && copy.deletedAt == null) { str(R.string.n_mp4_copy_missing) }
                        }
                        if (delete) repo.deleteOrTrash(oid)
                        repo.resolveConvertedOriginal(oid, deleted = delete)
                        cancelOutcome(DONE_NOTIF_ID)
                        postConvertDone()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    repo.updateConvertStatus { it.copy(lastOk = false, lastResult = str(R.string.n_choice_failed)) }
                    android.util.Log.e("ConversionService", "Resolving original failed (${e.javaClass.simpleName})")
                } finally {
                    if (held) session.endWork()
                    if (active.decrementAndGet() == 0) stopWhenIdle()
                }
            }
            return START_NOT_STICKY
        }
        ensureChannel()
        val running = synchronized(lock) {
            val wasRunning = active.get() > 0
            beginJob(startId)
            // A new operation takes the island: an outcome still there moves to its own notification.
            hold?.let { endHold(it, moveOn = true, replaced = true) }
            wasRunning
        }
        // Already running: go foreground with the progress on show (a link added to the queue
        // put "Preparazione…" over the running download, without its Annulla).
        startForeground(NOTIF_ID, lastProgress.takeIf { running } ?: build(str(R.string.n_preparing), 0, indeterminate = true,
            kind = when (mode) {
                MODE_IMPORT -> ResultKind.IMPORT
                MODE_DOWNLOAD -> ResultKind.EXPORT // "download" here is the export to the device
                MODE_DOWNLOAD_URL -> ResultKind.DOWNLOAD
                MODE_UPDATE -> ResultKind.UPDATE
                MODE_DUP_SCAN -> ResultKind.SCAN
                MODE_INTRO_SCAN -> ResultKind.INTRO
                MODE_FACE_SCAN -> ResultKind.FACES
                MODE_CONVERT -> ResultKind.CONVERT
                MODE_BACKUP -> ResultKind.BACKUP
                else -> null
            }))
        if (mode == MODE_DOWNLOAD_URL) {
            if (intent.getBooleanExtra(EX_RETRY, false)) getSystemService(NotificationManager::class.java).cancel(ResultKind.DOWNLOAD.id)
            val url = intent.getStringExtra(EX_URL)
            if (url != null) {
                val job = com.cripta.app.data.DownloadJob(
                    id = java.util.UUID.randomUUID().toString(),
                    url = url,
                    maxHeight = intent.getIntExtra(EX_HEIGHT, 0).takeIf { it > 0 },
                    folderId = if (intent.hasExtra(EX_FOLDER)) intent.getLongExtra(EX_FOLDER, -1).takeIf { it >= 0 } else null,
                    tagIds = intent.getLongArrayExtra(EX_TAGS)?.toList().orEmpty(),
                )
                downloadQueue.enqueueDownload(job)
                // Persisted so a waiting link survives the app being killed (resumed at next unlock).
                scope.launch {
                    repo.putPendingJob(job.id, "download", org.json.JSONObject()
                        .put("url", job.url).put("height", job.maxHeight ?: 0).put("folder", job.folderId ?: -1L)
                        .put("tags", org.json.JSONArray(job.tagIds)).toString())
                }
            }
            // A worker already running picks the new link up from the queue.
            val start = synchronized(workerLock) { if (downloadWorkerRunning) false else { downloadWorkerRunning = true; true } }
            if (!start) {
                // The running worker takes it: this command's count goes (see beginJob).
                if (active.decrementAndGet() == 0) stopWhenIdle()
                return START_NOT_STICKY
            }
        }
        // Keep the keys alive while this job runs, even if the vault gets locked meanwhile
        // (auto-lock on leaving the app, "Blocca ora"): the UI locks at once, the keys are wiped
        // as soon as the last job ends. Otherwise every remaining item failed silently.
        val holdsSession = session.beginWork()

        val job = scope.launch {
            try {
                when (mode) {
                    MODE_IMPORT -> {
                        val uris = intent.getParcelableArrayListExtraCompat(EX_URIS)
                        val folderId = if (intent.hasExtra(EX_FOLDER)) intent.getLongExtra(EX_FOLDER, -1).takeIf { it >= 0 } else null
                        val runStart = intent.getLongExtra(EX_RUN_START, 0L).takeIf { it > 0L }
                        val imported = importBatch(uris, folderId, runStart)
                        // Files shared from another app are that app's content, not documents we
                        // may delete: the "delete originals" policy only applies to picked files.
                        if (!intent.getBooleanExtra(EX_FROM_SHARE, false)) applyDeletePolicy(imported)
                    }
                    MODE_DOWNLOAD -> {
                        val ids = intent.getStringArrayListExtra(EX_IDS) ?: arrayListOf()
                        exportBatch(ids, intent.getStringExtra(EX_TREE)?.let(Uri::parse))
                    }
                    MODE_CONVERT -> {
                        val after = intent.getIntExtra(EX_CONVERT_AFTER, -1)
                            .let { i -> com.cripta.app.data.ConvertAfter.entries.getOrNull(i) }
                        intent.getStringExtra(EX_ID)?.let { fid ->
                            // Persisted until done, so a queued conversion survives the app being killed.
                            repo.putPendingJob("conv-$fid", "convert",
                                org.json.JSONObject().put("id", fid).put("after", after?.ordinal ?: -1).toString())
                            try { convertOne(fid, after) } finally { repo.deletePendingJob("conv-$fid") }
                        }
                    }
                    MODE_DOWNLOAD_URL -> downloadWorker()
                    MODE_UPDATE -> downloadUpdate(intent)
                    MODE_BACKUP -> runBackup(holdsSession)
                    MODE_DEBUG_PROGRESS -> debugRun(intent.getStringExtra(EX_DEBUG_KIND) ?: "convert")
                    MODE_DUP_SCAN -> {
                        val similar = intent.getBooleanExtra(EX_SIMILAR, false)
                        scanDuplicates(similar)
                    }
                    MODE_FACE_SCAN -> if (holdsSession) {
                        intent.getStringExtra(EX_RESET)?.let { what ->
                            // Start over: stop a running scan first, so it writes nothing after the wipe.
                            faceJob?.let { it.cancel(); it.join() }
                            faces.resetAll(everything = what == "all")
                        }
                        val ids = intent.getStringArrayListExtra(EX_IDS)
                        if (intent.getBooleanExtra(EX_REFINE, false) && ids != null) {
                            if (intent.getBooleanExtra(EX_FORCE, false)) faceRefineForced += ids
                            faceRefine += ids
                            faceScan(intent.getBooleanExtra(EX_MANUAL, false), emptyList())
                        } else {
                            if (intent.getBooleanExtra(EX_DEEP, false) && ids != null) faceDeep += ids
                            faceScan(intent.getBooleanExtra(EX_MANUAL, false), ids)
                        }
                    }
                    MODE_INTRO_SCAN -> if (holdsSession) {
                        intent.getStringExtra(EX_RESET)?.let { what ->
                            // Start over: stop a running scan first, so it writes nothing after the wipe.
                            introJob?.let { it.cancel(); it.join() }
                            intro.resetAll(everything = what == "all")
                        }
                        introScan(intent.getBooleanExtra(EX_MANUAL, false), intent.getStringArrayListExtra(EX_IDS))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // best-effort; individual items already guarded below
                android.util.Log.e("ConversionService", "job failed: $mode", e)
            } finally {
                // New videos get their intro/outro analysis right after (when on in Settings).
                // Never thrown out of here: Android may refuse a foreground start while the app is in
                // the background (ForegroundServiceStartNotAllowedException), and an escaping
                // exception crashed the app and skipped the lines below (keys held, service stuck).
                if (mode == MODE_IMPORT || mode == MODE_DOWNLOAD_URL || mode == MODE_CONVERT) withContext(NonCancellable) {
                    val s = runCatching { settings.settingsOnce() }.getOrNull()
                    if (s?.introAnalyzeNew == true) runCatching { startIntroScan(this@ConversionService, manual = false) }
                        .onFailure { android.util.Log.w("ConversionService", "intro scan not started", it) }
                    if (s?.facesAnalyzeNew == true) runCatching { startFaceScan(this@ConversionService, manual = false) }
                        .onFailure { android.util.Log.w("ConversionService", "face scan not started", it) }
                }
                if (holdsSession) session.endWork()
                if (active.decrementAndGet() == 0) stopWhenIdle()
            }
        }
        if (mode == MODE_CONVERT) convertJob = job
        if (mode == MODE_DUP_SCAN) scanJob = job
        if (mode == MODE_UPDATE) updateJob = job
        return START_NOT_STICKY
    }

    @Volatile private var convertJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastStartId = 0
    @Volatile private var scanJob: kotlinx.coroutines.Job? = null
    @Volatile private var updateJob: kotlinx.coroutines.Job? = null
    @Volatile private var debugJob: kotlinx.coroutines.Job? = null

    /**
     * Debug isola: a sample of one operation, with the same notifications as the real one (title,
     * icon, text, island chip, Annulla) and its real ending: the keep/delete choice for a
     * conversion or an import (with buttons that only close it), the result for the others, the
     * "annullata" result when cancelled. Nothing in the vault is touched.
     */
    private suspend fun debugRun(kind: String) {
        debugJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        val files = 5
        // The time left from the same calculation as the real operations (nothing the first
        // seconds: with a short Durata prova it only shows near the end, if at all).
        val eta = EtaEstimator()
        fun step(p: Int): Notification {
            val left = eta.left(p / 100f)
            return when (kind) {
                "download", "download_fail" -> build(str(R.string.n_download), p, sub = str(R.string.n_downloading_pct, p),
                    eta = left, header = str(R.string.n_one_queued), cancelable = true, cancelMode = MODE_CANCEL_DEBUG, kind = ResultKind.DOWNLOAD)
                "import" -> {
                    val n = (p * files / 100).coerceAtMost(files - 1)
                    build(str(R.string.n_import), p, sub = str(R.string.n_import_file_pct, n + 1, files, (p * files) % 100), chip = "$n/$files",
                        eta = left, cancelable = true, cancelMode = MODE_CANCEL_DEBUG, kind = ResultKind.IMPORT)
                }
                "export" -> {
                    val n = (p * files / 100).coerceAtMost(files - 1)
                    build(str(R.string.n_export_gallery), p, sub = str(R.string.n_file_of, n + 1, files), chip = "$n/$files",
                        eta = left, cancelable = true, cancelMode = MODE_CANCEL_DEBUG, kind = ResultKind.EXPORT)
                }
                "scan" -> build(str(R.string.n_dup_search), p, sub = str(R.string.n_dup_compared_test, p * 2),
                    eta = left, cancelable = true, cancelMode = MODE_CANCEL_DEBUG, kind = ResultKind.SCAN)
                "update" -> build(str(R.string.n_update), p, sub = str(R.string.n_update_test, p),
                    eta = left, cancelable = true, cancelMode = MODE_CANCEL_DEBUG, kind = ResultKind.UPDATE)
                else -> build(str(R.string.n_convert), p, sub = str(R.string.n_encoding_pct, p), eta = left, header = str(R.string.n_one_queued),
                    cancelable = true, cancelMode = MODE_CANCEL_DEBUG, kind = ResultKind.CONVERT)
            }
        }
        val closeOnly = { label: String, req: Int, icon: Int -> serviceAction(label, MODE_DEBUG_DISMISS, req, icon = icon) }
        // Debug isola › Durata prova: the whole sample in about this many seconds.
        val stepMs = (IslandDebug.get(this).sampleSeconds * 1000L / 25).coerceAtLeast(120L)
        try {
            notify(build(if (kind == "download" || kind == "download_fail") str(R.string.n_download) else str(R.string.n_preparing), 0,
                sub = str(R.string.n_test_starting), indeterminate = true, cancelable = true, cancelMode = MODE_CANCEL_DEBUG,
                kind = when (kind) {
                    "download", "download_fail" -> ResultKind.DOWNLOAD
                    "import" -> ResultKind.IMPORT
                    "export" -> ResultKind.EXPORT
                    "scan" -> ResultKind.SCAN
                    "update" -> ResultKind.UPDATE
                    else -> ResultKind.CONVERT
                }))
            kotlinx.coroutines.delay(stepMs * 2)
            for (p in 0..100 step 4) {
                if (kind == "download_fail" && p >= 60) break
                notify(step(p))
                kotlinx.coroutines.delay(stepMs)
            }
            when (kind) {
                "import" -> postChoice(DEBUG_NOTIF_ID, ResultKind.IMPORT, plural(R.plurals.imported_n, files), str(R.string.n_delete_originals_q),
                    closeOnly(str(R.string.n_delete_originals), 20, R.drawable.ic_action_delete), closeOnly(str(R.string.keep), 21, R.drawable.ic_action_keep))
                "export" -> notifyResult(str(R.string.n_export_done), str(R.string.n_export_saved_test, files), ResultKind.EXPORT)
                "scan" -> notifyResult(str(R.string.n_dups_found), str(R.string.n_dups_found_test), ResultKind.SCAN, openDuplicates = true,
                    actions = listOf(closeOnly(str(R.string.n_see_results), 23, R.drawable.ic_action_open)))
                "update" -> notifyResult(str(R.string.n_update_ready), str(R.string.n_update_ready_test), ResultKind.UPDATE,
                    actions = listOf(closeOnly(str(R.string.install), 24, R.drawable.ic_action_install)))
                "download" -> notifyResult(str(R.string.n_download_done), str(R.string.n_video_encrypted), ResultKind.DOWNLOAD)
                "download_fail" -> notifyResult(str(R.string.dl_err_failed), str(R.string.n_download_failed_test), ResultKind.DOWNLOAD,
                    state = ResultState.FAILED, actions = listOf(closeOnly(str(R.string.retry), 22, R.drawable.ic_action_retry)))
                else -> postChoice(DEBUG_NOTIF_ID, ResultKind.CONVERT, str(R.string.n_mp4_created), str(R.string.n_delete_original_q),
                    closeOnly(str(R.string.n_delete_original), 20, R.drawable.ic_action_delete), closeOnly(str(R.string.keep), 21, R.drawable.ic_action_keep))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            notifyResult(str(R.string.n_test_cancelled), null, ResultKind.CONVERT, state = ResultState.CANCELLED)
            throw e
        } finally {
            debugJob = null
        }
    }

    /**
     * Download the app update in the background (the app can be closed meanwhile): progress and
     * Annulla in the notification, then "Aggiornamento pronto" whose tap / Installa opens the
     * system installer. The settings screen follows it through [AppUpdater.downloadState].
     */
    /**
     * The backup or restore handed over by Settings ([com.cripta.app.data.BackupJobs]), run here in
     * the foreground so it ends even with the app in the background. Not cancellable halfway: an
     * archive stopped there is deleted, a restore is undone ([VaultRepository.importBackup]).
     */
    private suspend fun runBackup(holdsSession: Boolean) {
        val req = backups.take() ?: return
        val title = if (req.restore) str(R.string.n_restoring) else str(R.string.n_backing_up)
        try {
            if (!holdsSession) {
                backups.finish(if (req.restore) str(R.string.n_restore_failed_unlock) else str(R.string.n_backup_failed_unlock))
                return
            }
            var lastPct = -1
            val progress: (Float) -> Unit = { p ->
                backups.progress(p)
                val pct = (p * 100).toInt().coerceIn(0, 100)
                if (pct != lastPct) {
                    lastPct = pct
                    notify(build(title, pct, sub = "$pct%", kind = ResultKind.BACKUP))
                }
            }
            notify(build(title, 0, sub = str(R.string.n_preparing), indeterminate = true, kind = ResultKind.BACKUP))
            val outcome: Result<String> = withContext(NonCancellable) {
                if (req.restore) runCatching { repo.importBackup(req.uri, req.passphrase, progress).describe() }
                else runCatching {
                    val n = repo.exportBackup(req.uri, req.passphrase, req.compressDocs, progress, req.scope)
                    // Shown on the Backup page ("Ultimo backup"); the size only if the provider tells it.
                    val size = runCatching {
                        contentResolver.query(req.uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
                        }
                    }.getOrNull()
                    runCatching { settings.setLastBackup(com.cripta.app.data.LastBackup(System.currentTimeMillis(), n, size)) }
                    plural(R.plurals.backup_created, n)
                }
            }
            outcome.onSuccess { text ->
                backups.finish(text)
                notifyResult(if (req.restore) str(R.string.n_restore_done) else str(R.string.n_backup_done), text, ResultKind.BACKUP)
            }.onFailure { e ->
                android.util.Log.e("ConversionService", "backup failed (restore=${req.restore})", e)
                val text = when {
                    !req.restore -> str(R.string.n_backup_failed_space)
                    e is VaultRepository.WrongPassphraseException -> str(R.string.n_restore_failed_passphrase)
                    else -> str(R.string.n_restore_failed_reason, UserErrors.of(e))
                }
                backups.finish(text)
                notifyResult(if (req.restore) str(R.string.n_restore_failed) else str(R.string.n_backup_failed), text, ResultKind.BACKUP, state = ResultState.FAILED)
            }
        } finally {
            req.passphrase.fill('\u0000')
        }
    }

    private suspend fun downloadUpdate(intent: Intent) {
        val rel = com.cripta.app.update.AppUpdater.Release(
            versionName = intent.getStringExtra(EX_UPD_VERSION) ?: return,
            buildNumber = intent.getIntExtra(EX_UPD_BUILD, 0),
            apkUrl = intent.getStringExtra(EX_URL) ?: return,
            sizeBytes = intent.getLongExtra(EX_UPD_SIZE, 0L),
            sha256Url = intent.getStringExtra(EX_UPD_SHA),
        )
        val title = str(R.string.n_update)
        updater.publish(com.cripta.app.update.AppUpdater.Download.Running(rel, 0))
        notify(build(title, 0, sub = str(R.string.n_version, rel.versionName), indeterminate = true,
            cancelable = true, cancelMode = MODE_CANCEL_UPDATE, kind = ResultKind.UPDATE))
        val eta = EtaEstimator()
        try {
            val apk = updater.download(this, rel.apkUrl, rel.sha256Url) { pct ->
                updater.publish(com.cripta.app.update.AppUpdater.Download.Running(rel, pct))
                notify(build(title, pct, sub = str(R.string.n_version_pct, rel.versionName, pct), eta = eta.left(pct / 100f),
                    cancelable = true, cancelMode = MODE_CANCEL_UPDATE, kind = ResultKind.UPDATE))
            }
            updater.publish(com.cripta.app.update.AppUpdater.Download.Ready(rel, apk))
            // Through Cripta's own invisible activity: the installer handed straight to the
            // notification did not open on HyperOS (see InstallUpdateActivity).
            val install = android.app.PendingIntent.getActivity(
                this, 12, Intent(this, com.cripta.app.update.InstallUpdateActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            notifyResult(str(R.string.n_update_ready), str(R.string.n_update_downloaded, rel.versionName),
                ResultKind.UPDATE, contentIntent = install,
                actions = listOf(NotificationCompat.Action(R.drawable.ic_action_install, str(R.string.install), install)))
        } catch (e: kotlinx.coroutines.CancellationException) {
            updater.publish(com.cripta.app.update.AppUpdater.Download.Cancelled(rel))
            notifyResult(str(R.string.n_update_cancelled), null, ResultKind.UPDATE, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "update download failed", e)
            updater.publish(com.cripta.app.update.AppUpdater.Download.Failed(rel, e))
            notifyResult(str(R.string.n_update_failed), str(R.string.n_update_retry_from),
                ResultKind.UPDATE, state = ResultState.FAILED)
        } finally {
            updateJob = null
        }
    }
    /** Guards [downloadWorkerRunning] so a link enqueued while the worker drains is never missed. */
    private val workerLock = Any()
    private var downloadWorkerRunning = false
    /** Queue id of the link being downloaded now (null when none). */
    @Volatile private var currentDownloadId: String? = null

    /**
     * Stop the service after a control command, unless work is still running or an outcome is in
     * the island (it stops when that ends, with this command's id).
     */
    private fun stopIfIdle(startId: Int) {
        synchronized(lock) {
            if (hold == null && active.get() == 0) stopSelf(startId)
            // Remembered for the stop when the work ends: stopSelf with an older id doesn't stop
            // the service, which then lingered started, with no notification.
            else lastStartId = maxOf(lastStartId, startId)
        }
    }

    /**
     * A job of command [startId] begins: counted at once, under the lock [stopWhenIdle] checks,
     * so a job ending right now can't stop the service under this new one (setting the id first
     * and counting later let it, and onDestroy then cancelled the new job).
     */
    private fun beginJob(startId: Int) {
        synchronized(lock) {
            active.incrementAndGet()
            lastStartId = maxOf(lastStartId, startId)
        }
    }

    /** The last job ended: out of the foreground and stop, unless an outcome is still in the island. */
    private fun stopWhenIdle() {
        synchronized(lock) {
            if (active.get() != 0 || hold != null) return
            main.removeCallbacks(flushProgress); pendingProgress = null; lastProgress = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            // The latest start id: stops only if no newer command arrived meanwhile.
            stopSelf(lastStartId)
        }
    }
    /** Id of the running yt-dlp process, so the Cancel action can kill the native process (a coroutine
     *  cancel alone can't interrupt the blocking execute call). */
    @Volatile private var ytdlpProcessId: String? = null
    /** True when the user hit Cancel, so a resulting yt-dlp failure is reported as "annullato". */
    @Volatile private var cancelRequested = false

    /**
     * Counts the Annulla of imports / exports: a batch stops (after the current file) once it
     * differs from the value when the batch began. A single flag, reset by every new batch, lost
     * a cancel when another batch started meanwhile.
     */
    private val batchCancels = java.util.concurrent.atomic.AtomicInteger()

    /**
     * Decrypt [ids] back to the gallery one by one ("Esporta"), or into the user-picked folder
     * [tree] when given ("Scarica" on a selection), with progress and a final result.
     * Progress and time left go by the files' size ([batchWeight]), not their count.
     */
    private suspend fun exportBatch(ids: List<String>, tree: Uri? = null) {
        val total = ids.size
        if (total == 0) return
        val cancelGen = batchCancels.get()
        val files = ids.map { id ->
            try { repo.fileById(id) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        }
        val weightTotal = files.sumOf { batchWeight(it?.sizeBytes ?: 0L) }
        var weightDone = 0L
        val eta = EtaEstimator()
        var saved = 0; var failed = 0; var done = 0
        val title = if (tree != null) str(R.string.n_export_folder) else str(R.string.n_export_gallery)
        notify(build(title, 0, sub = str(R.string.n_file_first_of, total), chip = "0/$total",
            cancelable = true, cancelMode = MODE_CANCEL_BATCH, kind = ResultKind.EXPORT))
        // Starts the measurement at 0, so the end of the first file already gives a speed.
        eta.left(0f)
        for (f in files) {
            if (batchCancels.get() != cancelGen) break
            // One failed item doesn't stop the batch, but a cancellation does.
            try {
                if (f == null) failed++
                else { if (tree != null) repo.exportToTree(f, tree) else repo.restoreToGallery(f); saved++ }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ConversionService", "export item failed", e)
                failed++
            }
            done++
            weightDone += batchWeight(f?.sizeBytes ?: 0L)
            val fraction = if (weightTotal > 0) weightDone.toFloat() / weightTotal else done.toFloat() / total
            val left = if (done < total) eta.left(fraction) else null
            notify(build(title, (fraction * 100).toInt(), sub = if (done < total) str(R.string.n_file_of, done + 1, total) else str(R.string.n_finishing),
                chip = "$done/$total", eta = left, cancelable = true, cancelMode = MODE_CANCEL_BATCH, kind = ResultKind.EXPORT))
        }
        val cancelled = done < total
        val text = buildString {
            val where = if (tree != null) str(R.string.n_in_chosen_folder) else str(R.string.n_in_gallery)
            append(plural(R.plurals.export_saved, saved, saved, where))
            if (failed > 0) append(str(R.string.n_not_exported, failed))
        }
        when {
            cancelled -> notifyResult(str(R.string.n_export_cancelled), text, ResultKind.EXPORT, state = ResultState.CANCELLED)
            saved == 0 -> notifyResult(str(R.string.n_export_failed), text, ResultKind.EXPORT, state = ResultState.FAILED)
            else -> notifyResult(if (failed == 0) str(R.string.n_export_done) else str(R.string.n_export_done_errors), text, ResultKind.EXPORT)
        }
    }

    /**
     * Encrypt [uris] into the vault one by one, publishing live progress (file n of N, name, bytes
     * of the current file) to [VaultRepository.importState] for the in-app banner and to the
     * notification, then a final "completed" notification with the outcome.
     */
    private suspend fun importBatch(uris: List<Uri>, folderId: Long?, runStart: Long? = null): List<Uri> {
        if (uris.isEmpty()) return emptyList()
        val done = mutableListOf<Uri>()
        // Names and sizes up front: the progress (and the time left) goes by the files' size.
        val info = uris.map { repo.nameAndSize(it) }
        repo.importBegin(uris.size, info.sumOf { batchWeight(it.second) })
        // Vault files imported from now on belong to this run: never taken as a "previous version".
        val batchStart = runStart ?: System.currentTimeMillis()
        val cancelGen = batchCancels.get()
        var cancelled = false
        val eta = EtaEstimator()
        try {
            for ((i, uri) in uris.withIndex()) {
                if (batchCancels.get() != cancelGen) { cancelled = true; break }
                val (name, size) = info[i]
                repo.importCurrent(name, size)
                val st = repo.importState.value
                // Never a file name in a notification (it shows outside the vault): counts only.
                notify(importNote(st.done + 1, st.total, st.fraction, str(R.string.n_encrypting), eta, cancelGen))
                var lastNotify = 0L
                var imported: com.cripta.app.data.db.FileEntity? = null
                val ok = try {
                    imported = repo.import(uri, folderId) { bytes ->
                        repo.importBytes(bytes)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNotify > 500) {
                            lastNotify = now
                            val cur = repo.importState.value
                            val filePct = if (size > 0) str(R.string.n_encrypted_pct, (bytes * 100 / size).coerceAtMost(100)) else str(R.string.n_encrypting)
                            notify(importNote(cur.done + 1, cur.total, cur.fraction, filePct, eta, cancelGen))
                        }
                    }
                    true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Throwable: an OutOfMemoryError on one odd file must not kill the whole batch.
                    android.util.Log.e("ConversionService", "import failed", e)   // never the name: logcat is readable
                    repo.importFailed(name, UserErrors.of(e))
                    false
                }
                // Already in the vault? (byte-identical; only same-size files are even checked)
                imported?.let { f ->
                    val copies = try { dupScanner.copiesOf(f) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
                    val existing = copies?.firstOrNull()
                    if (existing != null) {
                        // The new copy stays until the user answers, once the import ends.
                        repo.importDuplicate(f.id, existing.originalName)
                        repo.askNewVersion(VaultRepository.VersionMatch(
                            f.id, f.originalName, copies.orEmpty().map { it.id }, existing.originalName, identical = true))
                    } else {
                        // Same name as a vault file: maybe an edited version, which would take the
                        // old one's place and tags. Never decided here: the user is asked when the
                        // import ends.
                        val named = try {
                            val previous = repo.previousVersionsOf(f, batchStart)
                            if (previous.isNotEmpty()) repo.askNewVersion(VaultRepository.VersionMatch(
                                f.id, f.originalName, previous.map { it.id }, previous.first().originalName))
                            previous.isNotEmpty()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("ConversionService", "tag inheritance failed", e)
                            false
                        }
                        // Not named like one: maybe the same photo or video re-saved or resized,
                        // found by its fingerprint (kept, so the next "approfondita" scan has it).
                        if (!named && runCatching { settings.settingsOnce().similarOnImport }.getOrDefault(false)) {
                            val like = try { dupScanner.similarTo(f) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
                            like.firstOrNull()?.let { near ->
                                repo.askNewVersion(VaultRepository.VersionMatch(f.id, f.originalName, listOf(near.id), near.originalName, similar = true))
                            }
                        }
                    }
                }
                // Fewer bytes than the source said it had: kept in the vault, but its original is
                // never offered for deletion (a provider cutting the stream short without an error
                // would otherwise have the only complete copy deleted).
                val complete = size <= 0 || (imported?.sizeBytes ?: 0L) >= size
                if (ok && !complete) android.util.Log.w("ConversionService", "import shorter than reported: original kept")
                repo.importItemDone(ok, if (ok && complete) uri else null, batchWeight(size))
                if (ok && complete) done += uri
                val cur = repo.importState.value
                if (cur.done < cur.total) notify(importNote(cur.done + 1, cur.total, cur.fraction, str(R.string.n_queued_lower), eta, cancelGen))
            }
        } finally {
            withContext(NonCancellable) {
                repo.importEnd()?.let { fin ->
                    val title = when {
                        cancelled -> str(R.string.n_import_cancelled)
                        fin.succeeded == 0 && fin.failed > 0 -> str(R.string.n_import_failed)
                        fin.failed == 0 -> str(R.string.n_import_done)
                        else -> str(R.string.n_import_done_errors)
                    }
                    val text = importSummary(fin)
                    lastImportSummary = text
                    lastImportCount = fin.succeeded
                    notifyResult(title, text, ResultKind.IMPORT, state = when {
                        cancelled -> ResultState.CANCELLED
                        fin.succeeded == 0 && fin.failed > 0 -> ResultState.FAILED
                        else -> ResultState.DONE
                    })
                }
            }
        }
        return done
    }

    /** "3 file cifrati nel vault · 1 non importato · 2 erano già presenti · 1 omonimi da confermare". */
    private fun importSummary(fin: VaultRepository.ImportState) = buildString {
        append(plural(R.plurals.import_encrypted, fin.succeeded))
        if (fin.failed > 0) append(str(R.string.n_not_imported, fin.failed))
        if (fin.duplicates.isNotEmpty()) append(str(R.string.n_already_there, fin.duplicates.size))
        // Short: the same text is the Hyper Island's line.
        if (fin.versionAsks > 0) append(str(R.string.n_same_name_confirm, fin.versionAsks))
        if (fin.similarAsks > 0) append(str(R.string.n_similar_confirm, fin.similarAsks))
    }
    /** Summary of the last finished import, reused by the "Chiedi" originals notification. */
    @Volatile private var lastImportSummary: String? = null
    @Volatile private var lastImportCount: Int? = null

    /**
     * Progress of an import: file [n] of [total] and what is happening to it; never its name.
     * After Annulla it says the import stops after this file (a large one can take a while, and
     * the button seemed to do nothing), without the button.
     */
    private fun importNote(n: Int, total: Int, fraction: Float, what: String, eta: EtaEstimator, cancelGen: Int): Notification {
        val done = n - 1
        val left = eta.left(fraction)
        val stopping = batchCancels.get() != cancelGen
        return build(str(R.string.n_import), (fraction * 100).toInt(), sub = str(R.string.n_file_of_sep, n, total) + if (stopping) str(R.string.n_cancel_after_file) else what,
            chip = "$done/$total", eta = left.takeUnless { stopping },
            cancelable = !stopping, cancelMode = MODE_CANCEL_BATCH, kind = ResultKind.IMPORT)
    }

    /** Conversions run strictly one at a time: parallel transcodes fight over the hardware
     *  encoder, which is a classic source of broken/garbled output. */
    private val convertMutex = kotlinx.coroutines.sync.Mutex()
    private val convertWaiting = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var currentConvertJob: kotlinx.coroutines.Job? = null

    /**
     * Transcode one video to MP4 and encrypt it into the vault. Decrypted plaintext lives only in
     * app-private cache and is shredded in a NonCancellable finally block, so it is never left on
     * disk even if the service is torn down mid-operation. The result is verified (duration, audio,
     * frames decodable across the whole video) before it is imported; the original is never
     * shredded here — at most it is moved to the trash when "Sostituisci" is chosen.
     */
    private suspend fun convertOne(id: String, afterOverride: com.cripta.app.data.ConvertAfter? = null) {
        val file = repo.fileById(id) ?: return
        repo.setConverting(id, true)
        // Counted outside update(): its lambda re-runs on a contended write, counting this twice.
        val ahead = convertWaiting.incrementAndGet() - 1
        repo.updateConvertStatus { it.copy(waiting = ahead) }
        try {
            convertMutex.lock()
        } catch (e: kotlinx.coroutines.CancellationException) {
            convertWaiting.decrementAndGet(); repo.setConverting(id, false); throw e
        }
        convertWaiting.decrementAndGet()
        currentConvertJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        repo.updateConvertStatus { it.copy(currentName = file.originalName, pct = 0, waiting = convertWaiting.get(), lastResult = null) }
        // No file names in notifications (the in-app banner shows it, behind the lock).
        val queued = { convertWaiting.get().let { if (it > 0) str(R.string.n_n_queued, it) else null } }
        val eta = EtaEstimator()
        notify(build(str(R.string.n_convert), 0, sub = str(R.string.n_preparing_video), indeterminate = true, header = queued(),
            cancelable = true, cancelMode = MODE_CANCEL_CONVERT, kind = ResultKind.CONVERT))
        var src: java.io.File? = null
        var out: java.io.File? = null
        var result: Pair<Boolean, String>? = null
        try {
            val suffix = file.originalName.substringAfterLast('.', "mpg")
            src = repo.decryptToTempFile(file, suffix)
            out = repo.newTempFile("mp4")
            val bitrate = targetBitrate(file.width, file.height)
            // Transformer requires a Looper; the service main thread has one.
            repo.setConversionProgress(0)
            withContext(Dispatchers.Main) {
                converter.toMp4(src!!, out!!, bitrate) { pct ->
                    repo.setConversionProgress(pct)
                    repo.updateConvertStatus { it.copy(pct = pct, waiting = convertWaiting.get()) }
                    notify(build(str(R.string.n_convert), pct, sub = str(R.string.n_encoding_pct, pct), eta = eta.left(pct / 100f), header = queued(),
                        cancelable = true, cancelMode = MODE_CANCEL_CONVERT, kind = ResultKind.CONVERT))
                }
            }
            // Never import a broken transcode (a corrupt output that looked valid could lead to losing
            // the good original). Verify it thoroughly first.
            val problem = verifyConversion(out!!, src!!, file.durationMs)
            if (problem != null) {
                result = false to str(R.string.n_convert_discarded, problem)
                notifyResult(str(R.string.n_convert_failed), str(R.string.n_original_untouched_sep, problem.replaceFirstChar { it.uppercase() }), state = ResultState.FAILED)
                return
            }
            val mode = afterOverride
                ?: runCatching { settings.settingsOnce().convertAfter }.getOrDefault(com.cripta.app.data.ConvertAfter.REPLACE)
            val replace = mode == com.cripta.app.data.ConvertAfter.REPLACE
            val newFile = repo.importConvertedMp4(file, out!!, replace = replace)
            thumbs.copyCustomCover(file.id, newFile.id)   // keep a cover the user picked
            when (mode) {
                com.cripta.app.data.ConvertAfter.ASK -> {
                    repo.awaitConvertedOriginal(id, file.originalName, newFile.id)
                    postConvertDone()
                }
                com.cripta.app.data.ConvertAfter.REPLACE -> {
                    val days = runCatching { settings.settingsOnce().trashDays }.getOrDefault(7)
                    notifyResult(str(R.string.n_convert_done), str(R.string.n_mp4_created_trash, days))
                }
                com.cripta.app.data.ConvertAfter.KEEP_BOTH -> notifyResult(str(R.string.n_convert_done), str(R.string.n_mp4_added))
            }
            repo.emitConvertResult(
                VaultRepository.ConversionEvent(id, newFile.id, ask = mode == com.cripta.app.data.ConvertAfter.ASK)
            )
            result = true to when {
                replace -> str(R.string.n_converted_trash, newFile.originalName)
                mode == com.cripta.app.data.ConvertAfter.ASK -> str(R.string.n_converted_ask, newFile.originalName)
                else -> str(R.string.n_converted, newFile.originalName)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            result = false to str(R.string.n_convert_cancelled_long)
            notifyResult(str(R.string.n_convert_cancelled), str(R.string.n_original_file_untouched), state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "convert failed: $id", e)
            val msg = convertErrorText(e)
            result = false to str(R.string.n_convert_failed_long, msg)
            notifyResult(str(R.string.n_convert_failed), str(R.string.n_original_untouched_sep, msg.replaceFirstChar { it.uppercase() }), state = ResultState.FAILED)
        } finally {
            withContext(NonCancellable) {
                src?.let { repo.shredTempFile(it) }
                out?.let { repo.shredTempFile(it) }
                repo.setConverting(id, false)
                currentConvertJob = null
                repo.updateConvertStatus {
                    it.copy(currentName = null, pct = 0, waiting = convertWaiting.get(),
                        lastResult = result?.second ?: it.lastResult, lastOk = result?.first ?: it.lastOk)
                }
                convertMutex.unlock()
            }
        }
    }

    /** A bitrate that fits the resolution (≈0.12 bit per pixel per frame at 30 fps), or null = encoder default. */
    private fun targetBitrate(w: Int?, h: Int?): Int? {
        if (w == null || h == null || w <= 0 || h <= 0) return null
        return (w.toLong() * h * 30 * 12 / 100).coerceIn(2_000_000L, 16_000_000L).toInt()
    }

    /**
     * Thorough check of a transcode against its source. Returns null when it is good, otherwise a
     * short reason: no video, wrong duration (must be within 10% of the source), lost audio track,
     * or frames that can't be decoded at the start, middle and end.
     */
    private fun verifyConversion(out: java.io.File, src: java.io.File, knownDurMs: Long?): String? {
        if (!out.exists() || out.length() <= 0L) return str(R.string.conv_empty_file)
        val srcR = MediaMetadataRetriever()
        val (srcDur, srcAudio) = try {
            srcR.setDataSource(src.absolutePath)
            val d = knownDurMs?.takeIf { it > 0 }
                ?: srcR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            d to (srcR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
        } catch (e: Exception) {
            (knownDurMs to false)
        } finally { runCatching { srcR.release() } }

        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(out.absolutePath)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return str(R.string.conv_no_video_track)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (dur <= 0) return str(R.string.conv_bad_duration)
            if (srcDur != null && srcDur > 0 && (dur < srcDur * 9 / 10 || dur > srcDur * 11 / 10)) {
                return str(R.string.conv_duration_differs)
            }
            if (srcAudio && r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") return str(R.string.conv_audio_lost)
            for (frac in listOf(0.1, 0.5, 0.9)) {
                val us = (dur * frac * 1000).toLong()
                val frame = runCatching { r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
                    ?: return str(R.string.conv_unreadable_frames)
                frame.recycle()
            }
            null
        } catch (e: Exception) {
            str(R.string.conv_unreadable_file)
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * True only if [f] is a decodable video with a plausible duration (>= half the source's, when
     * known). Guards against importing a corrupt/truncated transcode.
     */
    private fun isPlayableVideo(f: java.io.File, srcDurMs: Long?): Boolean {
        if (!f.exists() || f.length() <= 0L) return false
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            val hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val durOk = durMs > 0 && (srcDurMs == null || srcDurMs <= 0 || durMs >= srcDurMs / 2)
            hasVideo && durOk
        } catch (e: Exception) {
            false
        } finally {
            runCatching { r.release() }
        }
    }

    /** Use the same container parsers as the player, not the narrower platform retriever. A
     * valid downloaded stream may lack a declared duration; that does not make it invalid. */
    private fun isDownloadedVideo(file: java.io.File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val metadata = androidx.media3.exoplayer.MetadataRetriever.retrieveMetadata(
            this, androidx.media3.common.MediaItem.fromUri(Uri.fromFile(file)),
        )
        return try {
            val tracks = metadata.get(20, java.util.concurrent.TimeUnit.SECONDS)
            (0 until tracks.length).any { tracks[it].type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
        } catch (e: java.util.concurrent.ExecutionException) {
            false
        } catch (e: java.util.concurrent.TimeoutException) {
            throw IllegalStateException(str(R.string.dl_err_analyse_downloaded), e)
        } finally { metadata.cancel(true) }
    }

    /**
     * Duplicate scan (exact or similar) in the service, so it continues in the background with a
     * progress notification; results go to [DupScanStore] and a "finished" notification that
     * opens them on tap.
     */
    private suspend fun scanDuplicates(similar: Boolean) {
        val mode = if (similar) com.cripta.app.data.dedup.DupScanStore.Mode.SIMILAR else com.cripta.app.data.dedup.DupScanStore.Mode.EXACT
        val label = if (similar) str(R.string.n_deep_search) else str(R.string.n_quick_search)
        dupStore.start(mode)
        notify(build(label, 0, sub = str(R.string.n_preparing), indeterminate = true, cancelable = true, cancelMode = MODE_CANCEL_SCAN, kind = ResultKind.SCAN))
        var last = 0L
        val eta = EtaEstimator()
        val onProgress: (Int, Int) -> Unit = { done, total ->
            dupStore.progress(done, total)
            val now = SystemClock.elapsedRealtime()
            if (now - last > 400 || done == total) {
                last = now
                val pct = if (total > 0) done * 100 / total else 0
                notify(build(label, pct, sub = if (total > 0) str(R.string.n_items_compared, done, total) else str(R.string.n_preparing),
                    eta = if (total > 0) eta.left(done.toFloat() / total) else null,
                    indeterminate = total == 0, cancelable = true, cancelMode = MODE_CANCEL_SCAN, kind = ResultKind.SCAN))
            }
        }
        try {
            val (groups, scanned) = if (similar) {
                val r = dupScanner.scanSimilar(onProgress = onProgress)
                r.groups.map { it.files } to r.mediaScanned
            } else {
                val r = dupScanner.scanExact(onProgress)
                r.groups.map { it.files } to r.filesScanned
            }
            dupStore.finish(com.cripta.app.data.dedup.DupScanStore.Result(mode, groups, scanned))
            val title = if (groups.isEmpty()) str(R.string.n_x_done, label) else if (similar) str(R.string.n_similar_found) else str(R.string.n_identical_found)
            val text = if (groups.isEmpty()) str(R.string.n_no_results_of, scanned)
                else str(R.string.n_groups_of, groups.size, scanned)
            notifyResult(title, text, ResultKind.SCAN, openDuplicates = true,
                actions = if (groups.isEmpty()) emptyList()
                    else listOf(NotificationCompat.Action(R.drawable.ic_action_open, str(R.string.n_see_results), openAppIntent(openDuplicates = true))))
        } catch (e: kotlinx.coroutines.CancellationException) {
            dupStore.cancelled()
            notifyResult(str(R.string.n_x_cancelled, label), null, ResultKind.SCAN, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "duplicate scan failed", e)
            val msg = UserErrors.of(e)
            dupStore.fail(msg)
            notifyResult(str(R.string.n_x_failed, label), msg, ResultKind.SCAN, state = ResultState.FAILED)
        } finally {
            scanJob = null
        }
    }

    @Volatile private var introJob: kotlinx.coroutines.Job? = null
    @Volatile private var introRerun = false
    @Volatile private var introManual = false
    private val introMutex = kotlinx.coroutines.sync.Mutex()
    private val introNoteLock = Any()
    /** Videos asked for by the user (selection bar): analysed again, ahead of the rest. */
    private val introAsked = java.util.concurrent.ConcurrentLinkedQueue<String>()
    @Volatile private var introScanAll = false

    /**
     * Fingerprint every video not analysed yet ([onlyIds]: just those, again), then match them
     * against the whole vault. A request arriving while a scan runs is folded into it: chosen videos
     * are taken next, a full request makes the running scan go round once more.
     * [manual]: asked by the user, so it always reports; an automatic one only when it found something.
     */
    private suspend fun introScan(manual: Boolean, onlyIds: List<String>? = null) {
        if (onlyIds == null) introScanAll = true else introAsked += onlyIds
        introManual = introManual || manual
        // A request landing after the running scan's last look at [introRerun] but before it let go
        // of the lock found it taken and was dropped: the scan that ends looks again once unlocked.
        while (introMutex.tryLock()) {
            try { runIntroScan() } finally { introMutex.unlock() }
            if (!introScanAll && introAsked.isEmpty()) return
        }
        introRerun = true
    }

    private suspend fun runIntroScan() {
        introJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        val label = str(R.string.n_intro_analysis)
        val analysedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val skippedCount = java.util.concurrent.atomic.AtomicInteger(0)
        var found = 0
        var anyFull = false
        var rematched = 0
        fun summary() = introSummary(analysedCount.get(), skippedCount.get(), found) +
            (if (rematched > 0) str(R.string.n_rematched, rematched) else "")
        try {
            do {
                introRerun = false
                val quick = runCatching { settings.settingsOnce().introQuickScan }.getOrDefault(true)
                val all = introScanAll
                introScanAll = false
                if (all) anyFull = true
                // Videos fingerprinted by a scan that stopped before matching them (older versions
                // matched only at the very end): compared now, without decoding them again.
                if (all) {
                    val backlog = intro.unmatched()
                    if (backlog.isNotEmpty()) {
                        val eta = EtaEstimator()
                        var done = 0
                        for (part in backlog.chunked(50)) {
                            introProgress.value = IntroProgress(done, backlog.size, str(R.string.n_comparing_analysed))
                            notifyScan(ResultKind.INTRO, done * 100 / backlog.size) { others ->
                                build(label, done * 100 / backlog.size, sub = str(R.string.n_comparing_analysed_n, done, backlog.size),
                                    eta = if (done > 0) eta.left(done.toFloat() / backlog.size) else null, header = others,
                                    cancelable = true, cancelMode = MODE_CANCEL_INTRO, kind = ResultKind.INTRO)
                            }
                            found += intro.matchNew(part)
                            done += part.size
                        }
                        rematched += done
                    }
                }
                val todo = if (all) intro.pending(quick) else emptyList()
                if (todo.isEmpty() && introAsked.isEmpty()) break
                val askedIds = HashSet<String>()
                // Time of the last video analysed, shown so a slow phone or format can be told apart.
                val lastSec = java.util.concurrent.atomic.AtomicLong(-1L)
                val eta = EtaEstimator()
                val finished = java.util.concurrent.atomic.AtomicInteger(0)
                fun progress() = synchronized(introNoteLock) {
                    val d = finished.get()
                    val total = maxOf(1, todo.size + askedIds.size)
                    introProgress.value = IntroProgress(d, total, str(R.string.n_analysing_videos))
                    val last = lastSec.get().takeIf { it >= 0 }?.let { str(R.string.n_last_secs, it) }.orEmpty()
                    notifyScan(ResultKind.INTRO, d * 100 / total) { others ->
                        build(label, d * 100 / total, sub = str(R.string.n_videos_analysed, d, total, last),
                            eta = if (d > 0) eta.left(d.toFloat() / total) else null, header = others,
                            cancelable = true, cancelMode = MODE_CANCEL_INTRO, kind = ResultKind.INTRO)
                    }
                }
                progress()
                var next = 0
                // A chunk at a time, each matched as soon as it is fingerprinted: results show up during
                // a long scan instead of only at its end. Videos the user asked for go first, on their own.
                while (true) {
                    kotlinx.coroutines.yield()   // a cancelled scan stops between chunks
                    val askedNow = generateSequence { introAsked.poll() }.toList().distinct().filter { askedIds.add(it) }
                    val chunk = askedNow.mapNotNull { intro.forget(it) }.toMutableList()
                    if (askedNow.isEmpty()) while (chunk.size < INTRO_CHUNK && next < todo.size) {
                        val t = todo[next++]
                        if (t.id !in askedIds) chunk += t
                    }
                    if (chunk.isEmpty() && askedNow.isEmpty()) break
                    val newIds = java.util.Collections.synchronizedList(mutableListOf<String>())
                    val cursor = java.util.concurrent.atomic.AtomicInteger(0)
                    // A few videos at once: each decoder runs on one core, and the phone has several.
                    kotlinx.coroutines.coroutineScope {
                        repeat(minOf(INTRO_WORKERS, chunk.size)) {
                            launch {
                                while (true) {
                                    val f = chunk.getOrNull(cursor.getAndIncrement()) ?: break
                                    val t0 = SystemClock.elapsedRealtime()
                                    if (intro.fingerprint(f, quick)) { analysedCount.incrementAndGet(); newIds += f.id } else skippedCount.incrementAndGet()
                                    lastSec.set((SystemClock.elapsedRealtime() - t0) / 1000)
                                    finished.incrementAndGet()
                                    progress()
                                }
                            }
                        }
                    }
                    found += intro.matchNew(newIds)
                    // The answer to "Analizza": what the chosen videos have now.
                    if (askedNow.isNotEmpty()) {
                        notifyResult(plural(R.plurals.intro_done_n, askedNow.size),
                            intro.describe(askedNow), ResultKind.INTRO)
                    }
                }
            } while (introRerun)
            if (anyFull && (introManual || found > 0)) notifyResult(str(R.string.n_analysis_done), summary(), ResultKind.INTRO)
        } catch (e: kotlinx.coroutines.CancellationException) {
            notifyResult(str(R.string.n_x_cancelled, label), summary(), ResultKind.INTRO, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "intro scan failed", e)
            notifyResult(str(R.string.n_x_failed, label), UserErrors.of(e), ResultKind.INTRO, state = ResultState.FAILED)
        } finally {
            releaseScan(ResultKind.INTRO)
            introJob = null
            introManual = false
            introProgress.value = null
        }
    }

    @Volatile private var faceJob: kotlinx.coroutines.Job? = null
    @Volatile private var faceRerun = false
    @Volatile private var faceManual = false
    private val faceMutex = kotlinx.coroutines.sync.Mutex()
    private val faceNoteLock = Any()
    /** Media asked for by the user (selection bar, details panel): analysed again, ahead of the rest. */
    private val faceAsked = java.util.concurrent.ConcurrentLinkedQueue<String>()
    /** Media asked for with "Cerca più a fondo": analysed the deep way once. */
    private val faceDeep = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Videos with a confirmed face to look at closer ([com.cripta.app.data.face.FaceRepository.refine]), after the media asked for. */
    private val faceRefine = java.util.concurrent.ConcurrentLinkedQueue<String>()
    /** Those asked with "Analizza meglio": looked at again even when nothing was confirmed since. */
    private val faceRefineForced = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var faceScanAll = false
    /** Annulla was pressed: the running scan is stopping. */
    @Volatile private var faceStopping = false
    /** Posts the running scan's progress again (the cancel's "Annullamento in corso…"). */
    @Volatile private var faceRepost: (() -> Unit)? = null

    /**
     * Find the faces of every photo and video not analysed yet ([onlyIds]: just those, again), a
     * group at a time, each group placed among the people as soon as it is analysed: people show up
     * during a long scan. Requests arriving meanwhile are folded in as in [introScan].
     */
    private suspend fun faceScan(manual: Boolean, onlyIds: List<String>? = null) {
        if (onlyIds == null) faceScanAll = true else faceAsked += onlyIds
        faceManual = faceManual || manual
        while (true) {
            if (!faceMutex.tryLock()) {
                // The running scan takes this request in. One being cancelled would drop it with
                // its own: this request waits for it to end and runs next.
                if (!faceStopping) { faceRerun = true; return }
                faceMutex.lock()
            }
            // A request that found the lock taken just as the scan ended is not lost: the scan
            // looks again once unlocked. Only after a scan that ended well: one that failed was
            // started again at once, failed again, and so on for ever, its progress back to 0 at
            // every turn (the analysis "stuck at 0").
            val ok = try {
                if (!faceScanAll && faceAsked.isEmpty() && faceRefine.isEmpty()) return
                runFaceScan()
            } finally { faceMutex.unlock() }
            if (!ok || !faceScanAll && faceAsked.isEmpty() && faceRefine.isEmpty()) return
        }
    }

    /** False when the scan failed. */
    private suspend fun runFaceScan(): Boolean {
        faceJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        val label = str(R.string.n_face_analysis)
        val analysed = java.util.concurrent.atomic.AtomicInteger(0)
        val skipped = java.util.concurrent.atomic.AtomicInteger(0)
        val stalled = java.util.concurrent.atomic.AtomicInteger(0)
        val refined = java.util.concurrent.atomic.AtomicInteger(0)
        fun summary() = str(R.string.n_analysed_n, analysed.get()) + (if (refined.get() > 0) str(R.string.n_refined_n, refined.get()) else "") +
            (if (skipped.get() > 0) str(R.string.n_unreadable_n, skipped.get()) else "") +
            (if (stalled.get() > 0) str(R.string.n_stalled_n, stalled.get()) else "")
        try {
            // Faces of media deleted for good go; media analysed by a scan that stopped before placing them are placed.
            faces.purgeDeleted()
            faces.tidyOld()
            faces.placeNew()
            do {
                faceRerun = false
                val all = faceScanAll
                faceScanAll = false
                val todo = if (all) faces.pending() else emptyList()
                if (todo.isEmpty() && faceAsked.isEmpty() && faceRefine.isEmpty()) break
                val askedIds = HashSet<String>()
                // Media asked for (and videos to look at closer) so far, the same one twice counting twice.
                val askedCount = java.util.concurrent.atomic.AtomicInteger(0)
                val eta = EtaEstimator()
                val finished = java.util.concurrent.atomic.AtomicInteger(0)
                // How far each media being analysed is (0..1): a deep search of one video takes minutes.
                val partial = java.util.concurrent.ConcurrentHashMap<String, Float>()
                // The media being analysed (name, since when): the one in hand longest is named, so a
                // scan that seems stuck says on which file.
                val running = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
                fun progress() = synchronized(faceNoteLock) {
                    // Ended (a late "Annullamento" repost): nothing to show any more.
                    if (faceRepost == null) return@synchronized
                    val d = finished.get()
                    val total = maxOf(1, todo.size + askedCount.get())
                    val done = (d + partial.values.sum()).coerceAtMost(total.toFloat())
                    val exact = done * 100 / total
                    val pct = exact.toInt()
                    // With a decimal: thousands of media kept the island at "0%" for many minutes,
                    // as if the scan stood still.
                    val chip = String.format(com.cripta.app.Strings.locale, "%.1f%%", exact)
                    val stopping = faceStopping
                    // Settings shows the media finished and how far each one in hand is (longest in
                    // hand first); the notification only how far the whole scan is.
                    val hands = running.entries.sortedBy { it.value.second }.map { it.value.first to partial[it.key] }
                    val current = hands.firstOrNull()?.first
                    faceProgress.value = IntroProgress(d, total, str(R.string.n_face_analysis), stopping, current, done / total, hands)
                    notifyScan(ResultKind.FACES, pct) { others ->
                        build(label, pct, sub = if (stopping) str(R.string.n_cancelling) else chip,
                            eta = if (done > 0f && !stopping) eta.left(done / total) else null, header = others, chip = chip,
                            cancelable = !stopping, cancelMode = MODE_CANCEL_FACES, kind = ResultKind.FACES)
                    }
                }
                synchronized(faceNoteLock) { faceRepost = ::progress }
                progress()
                var next = 0
                while (true) {
                    kotlinx.coroutines.yield()   // a cancelled scan stops between groups
                    // A media asked for again while this scan runs is analysed again too: it was
                    // dropped as already asked, a second "Cerca più a fondo" lost without a word.
                    val askedNow = generateSequence { faceAsked.poll() }.toList().distinct().onEach { askedIds += it }
                    // Videos to look at closer come after the media asked for: a video analysed
                    // again is looked at as it is after that.
                    val refineNow = if (askedNow.isEmpty()) generateSequence { faceRefine.poll() }.toList().distinct() else emptyList()
                    askedCount.addAndGet(askedNow.size + refineNow.size)
                    // The total counts the media asked for right away, not after the first one is done.
                    if (askedNow.isNotEmpty() || refineNow.isNotEmpty()) progress()
                    // Media asked for are forgotten one at a time, just before each is analysed again:
                    // the first starts at once, and a cancelled scan leaves the rest as they were.
                    // True: a closer look ([com.cripta.app.data.face.FaceRepository.refine]) instead of an analysis.
                    val chunk = ArrayList<Pair<Boolean, suspend () -> com.cripta.app.data.db.FileEntity?>>()
                    askedNow.forEach { id -> chunk += false to suspend { faces.forget(id) } }
                    refineNow.forEach { id -> chunk += true to suspend { faces.liveFile(id) } }
                    val forcedNow = refineNow.filter { it in faceRefineForced }
                    if (askedNow.isEmpty() && refineNow.isEmpty()) while (chunk.size < FACE_CHUNK && next < todo.size) {
                        val t = todo[next++]
                        if (t.id !in askedIds) chunk += false to suspend { t }
                    }
                    if (chunk.isEmpty()) break
                    val cursor = java.util.concurrent.atomic.AtomicInteger(0)
                    kotlinx.coroutines.coroutineScope {
                        repeat(minOf(FACE_WORKERS, chunk.size)) {
                            launch {
                                while (true) {
                                    val (closer, take) = chunk.getOrNull(cursor.getAndIncrement()) ?: break
                                    val f = take()
                                    if (f == null) { if (!closer) skipped.incrementAndGet() }
                                    else if (closer) try {
                                        running[f.id] = f.originalName to android.os.SystemClock.elapsedRealtime()
                                        progress()
                                        val r = faces.refine(f, force = faceRefineForced.remove(f.id)) { p -> partial[f.id] = p; progress() }
                                        if (r != null) refined.incrementAndGet()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        // A video that cannot be read again stays as it was: the scan goes on.
                                        android.util.Log.w("ConversionService", "closer look failed (${e.javaClass.simpleName})")
                                    } finally { partial.remove(f.id); running.remove(f.id) }
                                    else try {
                                        running[f.id] = f.originalName to android.os.SystemClock.elapsedRealtime()
                                        progress()
                                        // Media asked for by hand are waited for however long they take;
                                        // in the scan of the whole vault a stuck one is left behind.
                                        val outcome = faces.analyse(f, deep = faceDeep.remove(f.id), watched = f.id !in askedIds) { p ->
                                            partial[f.id] = p; progress()
                                        }
                                        when (outcome) {
                                            com.cripta.app.data.face.FaceRepository.Outcome.OK -> analysed.incrementAndGet()
                                            com.cripta.app.data.face.FaceRepository.Outcome.STALLED -> stalled.incrementAndGet()
                                            com.cripta.app.data.face.FaceRepository.Outcome.UNREADABLE -> skipped.incrementAndGet()
                                        }
                                    } finally { partial.remove(f.id); running.remove(f.id) }
                                    finished.incrementAndGet()
                                    progress()
                                }
                            }
                        }
                    }
                    faces.placeNew()
                    // The answer to "Analizza meglio": what the closer look did.
                    if (forcedNow.isNotEmpty()) {
                        val notes = forcedNow.take(3).mapNotNull { faces.refineNoteNow(it) }
                        notifyResult(plural(R.plurals.refined_n, forcedNow.size),
                            notes.joinToString("\n").ifEmpty { str(R.string.n_nothing_to_join) }, ResultKind.FACES)
                    }
                    // The answer to "Analizza volti": what the chosen media have now.
                    if (askedNow.isNotEmpty()) {
                        notifyResult(plural(R.plurals.faces_done_n, askedNow.size),
                            askedNow.take(3).map { faces.describe(it).lineSequence().first() }.joinToString("\n"), ResultKind.FACES)
                    }
                }
            } while (faceRerun)
            if (faceManual || analysed.get() > 0) notifyResult(str(R.string.n_face_analysis_done), summary(), ResultKind.FACES,
                actions = listOf(NotificationCompat.Action(R.drawable.ic_action_open, str(R.string.n_see_people), openAppIntent(openPeople = true))))
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            notifyResult(str(R.string.n_x_cancelled, label), summary(), ResultKind.FACES, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "face scan failed", e)
            notifyResult(str(R.string.n_x_failed, label), UserErrors.of(e), ResultKind.FACES, state = ResultState.FAILED)
            return false
        } finally {
            synchronized(faceNoteLock) { faceRepost = null }
            releaseScan(ResultKind.FACES)
            faceJob = null
            faceStopping = false
            faceManual = false
            faceProgress.value = null
            faces.release()
        }
    }

    private fun introSummary(analysed: Int, skipped: Int, found: Int): String =
        str(R.string.n_analysed_n, analysed) + (if (skipped > 0) str(R.string.n_skipped_n, skipped) else "") +
            (if (found > 0) str(R.string.n_intros_in, found) else "")

    /** Process the downloader queue one link at a time until it is empty. */
    private suspend fun downloadWorker() {
        var drained = false
        try {
            while (true) {
                val job = synchronized(workerLock) {
                    downloadQueue.takeNextDownload() ?: run { downloadWorkerRunning = false; drained = true; null }
                } ?: break
                downloadUrl(job)
            }
        } finally {
            // Exited early (e.g. cancelled): release the worker slot so the next link can start one.
            if (!drained) synchronized(workerLock) { downloadWorkerRunning = false }
        }
    }

    /**
     * Download a remote video (direct link or HLS) and encrypt its actual container into the vault, in the
     * folder and with the tags chosen for [job]. Progress and a Cancel action live in the
     * notification; the source link is stored on the new file. Cancelling stops only this link:
     * the rest of the queue continues.
     */
    private suspend fun downloadUrl(job: com.cripta.app.data.DownloadJob) {
        val url = job.url
        var produced: java.io.File? = null
        val pid = java.util.UUID.randomUUID().toString()
        cancelRequested = false
        ytdlpProcessId = pid
        currentDownloadId = job.id
        fun set(f: (com.cripta.app.data.DownloadJob) -> com.cripta.app.data.DownloadJob) = downloadQueue.updateDownload(job.id, f)
        val waiting = { downloadQueue.downloads.value.count { it.phase == com.cripta.app.data.DownloadPhase.QUEUED } }
        try {
            repo.setConversionProgress(0)
            set { it.copy(phase = com.cripta.app.data.DownloadPhase.PREPARING) }
            // First download extracts the yt-dlp/Python payload; keep the notification indeterminate
            // until real progress arrives.
            val queued = { waiting().let { if (it > 0) str(R.string.n_n_queued, it) else null } }
            notify(build(str(R.string.n_download), 0, sub = str(R.string.n_analysing_link), indeterminate = true, header = queued(), cancelable = true, kind = ResultKind.DOWNLOAD))
            // The time left by our own estimate, not yt-dlp's (it swings on every fragment of a
            // stream); the download screen shows the same one.
            val eta = EtaEstimator()
            produced = withContext(Dispatchers.IO) {
                var lastPct = -1
                ytdlp.download(url, job.maxHeight, pid, isCancelled = { cancelRequested }) { pct, _ ->
                    // yt-dlp prints a progress line for every block; this runs on the thread that
                    // drains its output, so rebuilding the notification each time stalled yt-dlp
                    // on a full pipe. Only when the percentage moves.
                    if (pct == lastPct) return@download
                    lastPct = pct
                    repo.setConversionProgress(pct)
                    val leftMs = eta.update(pct / 100f, SystemClock.elapsedRealtime())
                    set { it.copy(phase = com.cripta.app.data.DownloadPhase.DOWNLOADING, pct = pct, etaSec = (leftMs ?: 0L) / 1000) }
                    notify(build(str(R.string.n_download), pct, sub = str(R.string.n_downloading_pct, pct), eta = leftMs?.let(::formatEtaShort), header = queued(),
                        cancelable = true, kind = ResultKind.DOWNLOAD))
                }
            }
            if (cancelRequested) throw java.io.InterruptedIOException("cancelled")
            if (!isDownloadedVideo(produced)) {
                val msg = str(R.string.dl_err_player_format)
                set { it.copy(phase = com.cripta.app.data.DownloadPhase.FAILED, message = msg) }
                notifyResult(str(R.string.dl_err_failed), msg, ResultKind.DOWNLOAD, state = ResultState.FAILED,
                    actions = listOf(retryAction(job)))
                return
            }
            if (cancelRequested) throw java.io.InterruptedIOException("cancelled")
            val name = produced.name.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "download"
            val file = repo.importDownloadedVideo(produced, name, folderId = job.folderId, sourceUrl = url, tagIds = job.tagIds)
            set { it.copy(phase = com.cripta.app.data.DownloadPhase.DONE, pct = 100, message = name, fileId = file.id) }
            // The title stays inside the app (queue, vault): the notification only says it is done.
            notifyResult(str(R.string.n_download_done), str(R.string.n_video_encrypted), ResultKind.DOWNLOAD)
        } catch (e: kotlinx.coroutines.CancellationException) {
            set { it.copy(phase = com.cripta.app.data.DownloadPhase.CANCELLED) }
            notifyResult(str(R.string.n_download_cancelled), null, ResultKind.DOWNLOAD, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            // A yt-dlp process killed by the Cancel action surfaces as an ordinary exception, not a
            // coroutine cancellation, so distinguish it here. Otherwise surface the real cause.
            if (cancelRequested) {
                set { it.copy(phase = com.cripta.app.data.DownloadPhase.CANCELLED) }
                notifyResult(str(R.string.n_download_cancelled), null, ResultKind.DOWNLOAD, state = ResultState.CANCELLED)
            } else {
                android.util.Log.e("ConversionService", "Download failed (${e.javaClass.simpleName})")
                val msg = UserErrors.ofDownload(e)
                set { it.copy(phase = com.cripta.app.data.DownloadPhase.FAILED, message = msg) }
                notifyResult(str(R.string.dl_err_failed), msg, ResultKind.DOWNLOAD, state = ResultState.FAILED,
                    actions = listOf(retryAction(job)))
            }
        } finally {
            ytdlpProcessId = null
            currentDownloadId = null
            repo.deletePendingJob(job.id)
            withContext(NonCancellable) {
                produced?.let { repo.shredTempFile(it); it.parentFile?.deleteRecursively() }
            }
        }
    }

    private suspend fun applyDeletePolicy(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val policy = try {
            settings.settingsOnce().deleteOriginalPolicy
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DeleteOriginalPolicy.NEVER
        }
        // Only originals that were actually imported are ever touched (a failed import keeps its file).
        when (policy) {
            DeleteOriginalPolicy.ALWAYS -> try {
                repo.deleteOriginals(uris)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ConversionService", "delete originals failed", e)
            }
            // "Chiedi": the app shows the keep/delete prompt in the vault, and the import's result
            // notification becomes the same choice (it stays in the Hyper Island until answered).
            DeleteOriginalPolicy.ASK -> {
                repo.addPendingOriginals(uris)
                postOriginalsChoice(repo.pendingOriginals.value.size)
            }
            DeleteOriginalPolicy.NEVER -> Unit
        }
    }

    /** Short Italian reason for a failed transcode (never the raw Media3 / codec text). */
    private fun convertErrorText(e: Throwable): String {
        val export = generateSequence(e) { it.cause }.filterIsInstance<androidx.media3.transformer.ExportException>().firstOrNull()
        return when {
            export == null -> UserErrors.of(e).replaceFirstChar { it.lowercase() }
            export.errorCode in 3000..3999 -> str(R.string.conv_format_unsupported)
            export.errorCode in 4000..4999 -> str(R.string.conv_codec_unavailable)
            else -> str(R.string.conv_encode_error)
        }
    }

    /** "ancora 3 min" at [fraction] (0..1) of the job, or null while there is no estimate yet. */
    private fun EtaEstimator.left(fraction: Float): String? =
        update(fraction, SystemClock.elapsedRealtime())?.let(::formatEtaShort)

    /** The parts that are there, joined: "ancora 3 min · 2 in coda". */
    private fun joinParts(vararg parts: String?): String? = parts.filterNotNull().joinToString(" · ").ifEmpty { null }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, str(R.string.ch_ongoing), NotificationManager.IMPORTANCE_LOW)
                )
            }
            // HyperOS focus notifications (Hyper Island): the island skips low-importance channels,
            // so in-progress operations there use this one; silent all the same (no sound, no
            // vibration, no pop-up: those need high importance), and only on Xiaomi phones.
            if (HyperFocus.isSupported(this) && mgr.getNotificationChannel(LIVE_CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(LIVE_CHANNEL, str(R.string.ch_ongoing_island), NotificationManager.IMPORTANCE_DEFAULT).apply {
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    }
                )
            }
            // Outcomes get their own channel (normal importance) so they are noticed, while the
            // ongoing progress stays silent.
            if (mgr.getNotificationChannel(RESULT_CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(RESULT_CHANNEL, str(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    /**
     * The ongoing notification of an operation, laid out the same way for all of them:
     *  - title: the operation ("Download", "Importazione"…), never a file name or title;
     *  - text: what is happening now ("File 3 di 10 · cifrato al 40%", "Codifica del video · 42%");
     *  - [header]: a short extra next to the app name (time left, how many are queued);
     *  - the progress bar and, with [cancelable], an Annulla button;
     *  - Android 16 Live Update (status chip, lock screen; HyperOS 3 shows it in the Hyper Island):
     *    compact it shows the Cripta icon and [chip] (default: the percentage, "Avvio" while
     *    there is none yet); the Hyper Island adds the short name of the operation's [kind].
     * The Live Update keys are set by name: the constants are API 36 and the project compiles
     * against 34; older systems ignore them. The user can still turn it off per app.
     */
    private fun build(
        title: String,
        pct: Int,
        sub: String? = null,
        indeterminate: Boolean = false,
        cancelable: Boolean = false,
        cancelMode: String = MODE_CANCEL,
        kind: ResultKind? = null,
        chip: String? = null,
        header: String? = null,
        /** Time left ("ancora 3 min"), shown before the [header]. */
        eta: String? = null,
    ): Notification {
        val icon = R.drawable.ic_notification
        val b = NotificationCompat.Builder(this, ongoingChannel())
            // Each notification its own group: with four or more ungrouped ones Android folds
            // them into one automatic summary, whose icon HyperOS shows as a plain square.
            .setGroup(GROUP_ONGOING)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(sub)
            .setSubText(joinParts(eta, header))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(title, icon))
        val chipText = chip ?: if (indeterminate) str(R.string.n_chip_start) else "$pct%"
        b.addExtras(liveUpdate(chipText))
        var cancelPi: android.app.PendingIntent? = null
        if (cancelable) {
            val cancelIntent = Intent(this, ConversionService::class.java).putExtra(EX_MODE, cancelMode)
            // One request code per cancel: the intro and face scans shared one, so the PendingIntent
            // carried whichever cancel was built last and "Annulla" could stop the other scan.
            val req = when (cancelMode) { MODE_CANCEL -> 1; MODE_CANCEL_SCAN -> 4; MODE_CANCEL_BATCH -> 5; MODE_CANCEL_UPDATE -> 13; MODE_CANCEL_DEBUG -> 14; MODE_CANCEL_FACES -> 7; else -> 6 }
            val pi = android.app.PendingIntent.getService(
                this, req, cancelIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, str(R.string.cancel), pi)
            cancelPi = pi
        }
        // HyperOS: the Hyper Island's own template on top (large rounded buttons); ignored elsewhere.
        // Its expanded text also carries the time left and the [header], which the template has
        // no place for otherwise; the time left first, as the line is cut at the end when long.
        b.addExtras(HyperFocus.extras(
            this, "cripta_progress", title, joinParts(eta, sub, header), chipText, kind?.label ?: str(R.string.app_name),
            // Not started yet (no percentage): no ring or bar, which stood at 0 as if stuck.
            progress = if (indeterminate) null else pct,
            buttons = listOfNotNull(cancelPi?.let { HyperFocus.Button("cancel", str(R.string.cancel), it, icon = R.drawable.ic_action_cancel) }),
            float = false,
        ))
        return b.build()
    }

    /** Channel of the ongoing notification and the choices: the island's own on HyperOS (see ensureChannel). */
    private fun ongoingChannel(): String {
        if (!HyperFocus.isSupported(this)) return CHANNEL
        if (!IslandDebug.get(this).highChannel) return LIVE_CHANNEL
        // Debug isola: a silent high-importance channel, created when first chosen.
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(LIVE_CHANNEL_HIGH) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(LIVE_CHANNEL_HIGH, str(R.string.ch_ongoing_island_test), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false); setShowBadge(false)
                }
            )
        }
        return LIVE_CHANNEL_HIGH
    }

    /** Extras asking for a Live Update (promoted ongoing) with [chip] as its short text. */
    private fun liveUpdate(chip: String) = android.os.Bundle().apply {
        // HyperOS with focus notifications active: the island's own template (HyperFocus) instead.
        // With both, HyperOS showed the Android Live Update and ignored the focus one (standard
        // buttons instead of the island's large rounded ones).
        if (HyperFocus.isSupported(this@ConversionService) && !IslandDebug.get(this@ConversionService).liveUpdate) return@apply
        putBoolean("android.requestPromotedOngoing", true)
        putCharSequence("android.shortCriticalText", chip)
    }

    /** What the lock screen shows instead: the operation's title, no details, no buttons. */
    private fun publicVersion(title: String, icon: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .build()

    private var lastPostAt = 0L
    private var lastPostKey: String? = null
    private var lastPostTitle: String? = null

    /** Guards the ongoing notification: its updates and the outcome shown in the island in its place. */
    private val lock = Any()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** The last progress posted, to go foreground with when a new command arrives mid-operation. */
    @Volatile private var lastProgress: Notification? = null

    /** An update held back by the once-a-second limit, posted when the interval is up. */
    private var pendingProgress: Notification? = null
    private val flushProgress = Runnable {
        synchronized(lock) {
            val n = pendingProgress ?: return@synchronized
            pendingProgress = null
            if (active.get() > 0) notify(n)
        }
    }

    /** A result or choice shown in the island in place of the progress (see [holdInIsland]). */
    private class Hold(val id: Int, val moveOn: () -> Unit) { lateinit var end: Runnable }
    private var hold: Hold? = null

    /**
     * HyperOS: show [n], the outcome of an operation (a result or a choice), in the island in place
     * of its progress: in the foreground notification itself, so the island turns from "42%" into
     * "Fatto" where it is. A separate notification never showed there (HyperOS puts only the
     * service's own in the island), so the progress just vanished. After [seconds], [moveOn] puts
     * the outcome in its own notification ([id]: the result, or the choice with its buttons) and
     * the island's place goes back to what is still running, or the service stops. A newer
     * outcome, or another operation's progress, moves it on at once.
     */
    private fun holdInIsland(id: Int, n: Notification, seconds: Int, moveOn: () -> Unit) {
        synchronized(lock) {
            hold?.let { endHold(it, moveOn = true, replaced = true) }
            main.removeCallbacks(flushProgress); pendingProgress = null
            val h = Hold(id, moveOn)
            h.end = Runnable { synchronized(lock) { if (hold === h) endHold(h, moveOn = true, stopId = lastStartId) } }
            hold = h
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
            // The next progress (another operation's) goes through at once.
            lastPostKey = null; lastPostTitle = null; lastPostAt = 0L
            main.postDelayed(h.end, seconds * 1000L)
        }
    }

    /**
     * Ends [h]: its outcome moves to its own notification ([moveOn]; not when it was answered).
     * With [replaced] the caller puts its own notification in its place; otherwise the place goes
     * back to the operation still running (a quiet placeholder until its next update) or, with
     * nothing running, the service leaves the foreground and, given [stopId], stops. Under [lock].
     */
    private fun endHold(h: Hold, moveOn: Boolean, replaced: Boolean = false, stopId: Int? = null) {
        main.removeCallbacks(h.end)
        hold = null
        if (moveOn) runCatching { h.moveOn() }
        if (replaced) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (active.get() > 0) {
            // Still running (the clean-up after a conversion or a download, or another operation).
            val quiet = NotificationCompat.Builder(this, CHANNEL)
                .setGroup(GROUP_ONGOING)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(BRAND_COLOR)
                .setContentTitle(str(R.string.app_name))
                .setContentText(str(R.string.n_finishing))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openAppIntent())
                .build()
            lastPostKey = null; lastPostTitle = null; lastPostAt = 0L
            runCatching { mgr.notify(NOTIF_ID, quiet) }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            runCatching { mgr.cancel(NOTIF_ID) }
            stopId?.let { stopSelf(it) }
        }
    }

    /** The outcome [id] was answered or is no longer wanted: its notification goes, and from the island. */
    private fun cancelOutcome(id: Int) {
        synchronized(lock) { hold?.takeIf { it.id == id }?.let { endHold(it, moveOn = false) } }
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.cancel(id)
        refreshSummary(mgr, analysis = true); refreshSummary(mgr, analysis = false)
    }

    /**
     * Update the ongoing notification, at most about once a second. Progress arrived far more often
     * (yt-dlp several times a second, the transcoder every 500 ms even when unchanged) and every
     * post re-laid out the notification and the Hyper Island: it looked laggy, and Android drops
     * posts beyond a few per second anyway, so the bar jumped. An identical update is skipped; one
     * too soon is posted when the interval is up, unless a newer one comes first (it used to be
     * dropped, so the island could stand at 60 % when the operation ended); a new operation
     * (another title) always goes through at once.
     */
    /** When another operation (import, download, conversion…) last posted its progress. */
    private var otherAt = 0L
    private val scans = ScanNotices<ResultKind> { if (it == ResultKind.INTRO) "Intro" else it.label }
    /** Each running scan's notification, given the line about the other scans. */
    private val scanMakers = HashMap<ResultKind, (String?) -> Notification>()

    /**
     * A background scan's progress ([pct] %). Scans never get a notification of their own: the
     * first one running shows the others in the foreground one ([ScanNotices]).
     */
    private fun notifyScan(kind: ResultKind, pct: Int, make: (others: String?) -> Notification) {
        synchronized(lock) {
            scanMakers[kind] = make
            val otherBusy = otherAt > 0 && SystemClock.elapsedRealtime() - otherAt < 10_000
            val shown = scans.update(kind, pct, otherBusy) ?: return
            scanMakers[shown]?.let { notify(it(scans.others(shown)), scan = true) }
        }
    }

    /** [kind]'s scan ended: the next scan to post takes the notification over. */
    private fun releaseScan(kind: ResultKind) {
        synchronized(lock) {
            scans.end(kind)
            scanMakers.remove(kind)
        }
    }

    private fun notify(n: Notification, scan: Boolean = false) {
        synchronized(lock) {
            if (!scan) otherAt = SystemClock.elapsedRealtime()
            // Progress again: an outcome still in the island moves to its own notification.
            hold?.let { endHold(it, moveOn = true, replaced = true) }
            val e = n.extras
            val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val key = listOf(
                title, e.getCharSequence(Notification.EXTRA_TEXT), e.getCharSequence(Notification.EXTRA_SUB_TEXT),
                e.getInt(Notification.EXTRA_PROGRESS), e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
            ).joinToString("|")
            if (key == lastPostKey) return
            val now = SystemClock.elapsedRealtime()
            val interval = IslandDebug.get(this).intervalMs
            if (title == lastPostTitle && now - lastPostAt < interval) {
                if (pendingProgress == null) main.postDelayed(flushProgress, lastPostAt + interval - now)
                pendingProgress = n
                return
            }
            main.removeCallbacks(flushProgress); pendingProgress = null
            lastPostAt = now; lastPostKey = key; lastPostTitle = title
            lastProgress = n
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        }
    }

    /**
     * Which kind of operation a notification belongs to; each result keeps its own notification.
     * [label]: its name next to the icon in the Hyper Island, one short word (the island widens
     * with every letter).
     */
    private enum class ResultKind(val id: Int, private val labelRes: Int, val analysis: Boolean = false) {
        IMPORT(4220, R.string.kind_import), DOWNLOAD(4221, R.string.n_download),
        CONVERT(4222, R.string.kind_convert), SCAN(4223, R.string.kind_duplicates, analysis = true),
        EXPORT(4224, R.string.kind_export), UPDATE(4225, R.string.kind_update),
        INTRO(4226, R.string.kind_analysis, analysis = true), FACES(4227, R.string.kind_faces, analysis = true),
        BACKUP(4228, R.string.kind_backup),
        ;

        val label: String get() = str(labelRes)
    }

    /** How an operation ended (the island's chip: Fatto, Errore, Annullato). */
    private enum class ResultState { DONE, FAILED, CANCELLED }

    /** Tap on a notification: bring the app to the front (optionally straight to the duplicate results). */
    private fun openAppIntent(openDuplicates: Boolean = false, openPeople: Boolean = false): android.app.PendingIntent = android.app.PendingIntent.getActivity(
        this, if (openPeople) 12 else if (openDuplicates) 11 else 10,
        Intent(this, com.cripta.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                if (openDuplicates) putExtra(com.cripta.app.MainActivity.EXTRA_OPEN_DUPLICATES, true)
                if (openPeople) putExtra(com.cripta.app.MainActivity.EXTRA_OPEN_PEOPLE, true)
            },
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Post a terminal result (completed / failed / cancelled) as a dismissible notification. Each
     * [kind] has its own id (an import result no longer overwrites a download one), and never
     * [NOTIF_ID], the ongoing foreground notification that is removed when the job ends. Expanded
     * it shows the whole [text] and the [actions]; the lock screen gets only the title.
     */
    private fun notifyResult(
        title: String,
        text: String?,
        kind: ResultKind = ResultKind.CONVERT,
        openDuplicates: Boolean = false,
        state: ResultState = ResultState.DONE,
        actions: List<NotificationCompat.Action> = emptyList(),
        /** Tap target instead of opening the app (the update's installer). */
        contentIntent: android.app.PendingIntent? = null,
        /** False: straight into the notifications (a note about a tap, not an operation's outcome). */
        island: Boolean = true,
    ) {
        val icon = R.drawable.ic_notification
        val public = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .build()
        fun base(channel: String) = NotificationCompat.Builder(this, channel)
            .setGroup(resultGroup(kind.analysis))
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(text?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setCategory(if (state == ResultState.FAILED) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent ?: openAppIntent(openDuplicates))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .apply { actions.forEach { addAction(it) } }
        val mgr = getSystemService(NotificationManager::class.java)
        if (!island || !HyperFocus.isSupported(this)) { postResult(mgr, kind, base(RESULT_CHANNEL).build()); return }
        // HyperOS: the result takes the progress's place in the island for a few seconds (chip
        // Fatto / Errore / Annullato, its buttons), then becomes the normal, dismissible result.
        val chip = when (state) { ResultState.DONE -> str(R.string.chip_done); ResultState.FAILED -> str(R.string.chip_error); ResultState.CANCELLED -> str(R.string.chip_cancelled) }
        val island = base(ongoingChannel())
            .setGroup(GROUP_ONGOING)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addExtras(liveUpdate(chip))
            .addExtras(HyperFocus.extras(
                this, "cripta_progress", title, text, chip, kind.label, progress = null,
                buttons = actions.mapIndexedNotNull { i, a ->
                    a.actionIntent?.let { HyperFocus.Button("result$i", a.title.toString(), it,
                        icon = a.iconCompat?.resId?.takeIf { r -> r != 0 }) }
                },
                // Not for a cancellation: the user just tapped Annulla, the island needn't pop open to say so.
                float = state != ResultState.CANCELLED,
            ))
            .build()
        // Silent: the island popping open was the alert; a sound at the swap came seconds late.
        val normal = base(RESULT_CHANNEL).setSilent(true).build()
        holdInIsland(kind.id, island, IslandDebug.get(this).resultSeconds) { postResult(mgr, kind, normal) }
    }

    /** The results of the analyses (duplicates, intro, faces) and those of the other operations: two stacks, each folded on its own. */
    private fun resultGroup(analysis: Boolean) = if (analysis) "cripta_results_analysis" else "cripta_results_actions"

    private fun postResult(mgr: NotificationManager, kind: ResultKind, n: Notification) {
        post(mgr, kind.id, n)
        refreshSummary(mgr, kind.analysis)
    }

    /**
     * The summary of one stack of results ([resultGroup]): with two or more of them they fold
     * into it and collapse together, apart from the other stack. Without it they stayed loose
     * (each its own group: Android's automatic summary showed a plain square on HyperOS), so
     * they could not be collapsed at once. Gone with the last result of its stack; never
     * cancelled while one is left (that would take the results with it).
     */
    private fun refreshSummary(mgr: NotificationManager, analysis: Boolean) {
        val key = resultGroup(analysis)
        val id = if (analysis) SUMMARY_ANALYSIS_ID else SUMMARY_ACTIONS_ID
        val children = runCatching {
            mgr.activeNotifications.filter { it.id != id && it.notification.group == key && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        }.getOrNull() ?: return
        if (children.isEmpty()) { runCatching { mgr.cancel(id) }; return }
        if (children.size < 2) return
        val title = if (analysis) str(R.string.kind_analysis) else str(R.string.n_operations)
        val lines = children.sortedByDescending { it.postTime }.mapNotNull { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) }
        val icon = R.drawable.ic_notification
        val summary = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.InboxStyle().also { st -> lines.forEach { st.addLine(it) } })
            .setGroup(key)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(title, icon))
            .build()
        runCatching { mgr.notify(id, summary) }
    }

    /**
     * Post [n] as notification [id]. A replaced notification keeps the timer of its setTimeoutAfter
     * (on Android 14, and 15 without its newer timer helper, only a cancel clears it), and when it
     * fires it removes whatever has that id by then: the island result's timer took away the
     * normal result that had replaced it a few seconds before. So a notification with a timer is
     * cancelled first when [n] has none (a new timer just replaces the old one).
     */
    private fun post(mgr: NotificationManager, id: Int, n: Notification) {
        val old = runCatching { mgr.activeNotifications.firstOrNull { it.id == id && it.tag == null } }.getOrNull()
        if (old != null && old.notification.timeoutAfter > 0 && n.timeoutAfter <= 0) mgr.cancel(id)
        mgr.notify(id, n)
    }

    /** "Riprova" for a failed download: queues the same link again, same quality, folder and tags. */
    private fun retryAction(job: com.cripta.app.data.DownloadJob): NotificationCompat.Action =
        retryAction(downloadUrlIntent(this, job.url, job.maxHeight, job.folderId, job.tagIds))

    private fun retryAction(download: Intent): NotificationCompat.Action {
        val i = Intent(download).putExtra(EX_RETRY, true)
        val pi = android.app.PendingIntent.getForegroundService(
            this, 7, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(R.drawable.ic_action_retry, str(R.string.retry), pi)
    }

    /**
     * A choice left to answer at the end of an operation: a Live Update (ongoing, promoted) so the
     * Hyper Island / status chip keeps it; compact it reads "Fatto", tapped or expanded it shows
     * the buttons. After [DONE_TIMEOUT_MS] unanswered it goes away and the choice stays in the app.
     * Buttons and details are hidden on the lock screen.
     */
    private fun postChoice(id: Int, kind: ResultKind, title: String, text: String, vararg actions: NotificationCompat.Action) {
        val icon = R.drawable.ic_notification
        fun base(channel: String) = NotificationCompat.Builder(this, channel)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(title, icon))
            .apply { actions.forEach { addAction(it) } }
        val shade = base(ongoingChannel())
            .setGroup("cripta_choice_$id")
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .addExtras(liveUpdate(str(R.string.choose)))
            .build()
        val mgr = getSystemService(NotificationManager::class.java)
        if (!HyperFocus.isSupported(this)) { post(mgr, id, shade); return }
        // HyperOS: the choice takes the progress's place in the island, popping it open, with its
        // round buttons: the trash can on red (it deletes) and ✕ (keep: "no"; a tick under
        // "Eliminare l'originale?" read as "yes, delete"). Then it stays in the notifications
        // until answered.
        val island = base(ongoingChannel())
            .setGroup(GROUP_ONGOING)
            .addExtras(liveUpdate(str(R.string.choose)))
            .addExtras(HyperFocus.extras(
                this, "cripta_progress", title, text, str(R.string.choose), kind.label, progress = null,
                buttons = actions.mapIndexedNotNull { i, a ->
                    a.actionIntent?.let {
                        val res = a.iconCompat?.resId?.takeIf { r -> r != 0 }
                        HyperFocus.Button("choice$i", a.title.toString(), it, icon = res,
                            // The action that deletes: in red also as a text button (Debug isola › Tasti).
                            danger = res == R.drawable.ic_action_delete)
                    }
                },
                float = true,
            ))
            .build()
        holdInIsland(id, island, IslandDebug.get(this).choiceSeconds) { post(mgr, id, shade) }
    }

    private fun serviceAction(label: String, mode: String, req: Int, originalId: String? = null, icon: Int = 0) = NotificationCompat.Action(
        icon, label,
        android.app.PendingIntent.getService(
            this, req,
            Intent(this, ConversionService::class.java).putExtra(EX_MODE, mode).apply { originalId?.let { putExtra(EX_ID, it); data = Uri.parse("cripta://conversion-choice/$mode/$it") } },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )

    /** An ASK conversion finished: delete (to the trash) or keep the original video. */
    private fun postConvertDone() {
        val pending = repo.convertStatus.value.pendingOriginals
        val original = pending.firstOrNull() ?: return
        postChoice(
            DONE_NOTIF_ID, ResultKind.CONVERT,
            plural(R.plurals.originals_pending, pending.size),
            str(R.string.n_delete_original_q),
            serviceAction(str(R.string.n_delete_original), MODE_DELETE_ORIG, 2, original.id, R.drawable.ic_action_delete),
            serviceAction(str(R.string.keep), MODE_DISMISS, 3, original.id, R.drawable.ic_action_keep),
        )
    }

    /** An import with the "Chiedi" policy finished: delete the originals from the device, or keep them. */
    private fun postOriginalsChoice(n: Int) {
        if (n <= 0) return
        // Short lines: the island's template shows one line each (the counts become the title).
        val title = lastImportCount?.let { plural(R.plurals.imported_n, it) } ?: str(R.string.n_import_done)
        postChoice(
            ResultKind.IMPORT.id, ResultKind.IMPORT, title,
            plural(R.plurals.delete_original_q, n),
            serviceAction(plural(R.plurals.delete_original_btn, n), MODE_ORIGINALS_DELETE, 8, icon = R.drawable.ic_action_delete),
            serviceAction(str(R.string.keep), MODE_ORIGINALS_KEEP, 9, icon = R.drawable.ic_action_keep),
        )
    }

    private fun Intent.getParcelableArrayListExtraCompat(key: String): ArrayList<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            getParcelableArrayListExtra(key, Uri::class.java) ?: arrayListOf()
        else @Suppress("DEPRECATION") (getParcelableArrayListExtra(key) ?: arrayListOf())

    companion object {
        private const val CHANNEL = "conversion"
        private const val NOTIF_ID = 4211
        private const val EX_MODE = "mode"
        private const val EX_URIS = "uris"
        private const val EX_IDS = "ids"
        private const val EX_ID = "id"
        private const val EX_FOLDER = "folder"
        private const val EX_FROM_SHARE = "from_share"
        /** When the import run began (all its chunks): see [startImport]. */
        private const val EX_RUN_START = "run_start"
        /** Uris per start intent, well under the binder transaction limit. */
        private const val IMPORT_CHUNK = 500
        private const val MODE_IMPORT = "import"
        private const val MODE_DOWNLOAD = "download"
        private const val EX_TREE = "tree"
        private const val MODE_CONVERT = "convert"
        private const val MODE_CANCEL = "cancel"
        private const val MODE_CANCEL_CONVERT = "cancel_convert"
        private const val MODE_DUP_SCAN = "dup_scan"
        private const val MODE_CANCEL_SCAN = "cancel_scan"
        private const val MODE_INTRO_SCAN = "intro_scan"
        private const val MODE_CANCEL_INTRO = "cancel_intro"
        private const val MODE_FACE_SCAN = "face_scan"
        private const val MODE_CANCEL_FACES = "cancel_faces"
        /** Media analysed at the same time: each worker holds one frame (a few MB) and runs the models. */
        private const val FACE_WORKERS = 3
        /** Media analysed before each placing round. */
        private const val FACE_CHUNK = 12
        private const val EX_MANUAL = "manual"
        private const val EX_RESET = "reset"
        private const val EX_DEEP = "deep"
        private const val EX_REFINE = "refine"
        private const val EX_FORCE = "force"
        /** Videos fingerprinted at the same time (memory: about 15 MB of audio each). */
        private const val INTRO_WORKERS = 3
        /** Videos fingerprinted before each matching round. */
        private const val INTRO_CHUNK = 12
        private const val EX_SIMILAR = "similar"
        private const val EX_CONVERT_AFTER = "convert_after"
        private const val MODE_DELETE_ORIG = "delete_orig"
        private const val MODE_DISMISS = "dismiss"
        private const val MODE_DOWNLOAD_URL = "download_url"
        private const val EX_URL = "url"
        private const val EX_HEIGHT = "height"
        private const val EX_TAGS = "tags"
        private const val MODE_CANCEL_BATCH = "cancel_batch"
        private const val MODE_UPDATE = "update"
        private const val MODE_BACKUP = "backup"
        private const val MODE_CANCEL_UPDATE = "cancel_update"
        private const val EX_UPD_VERSION = "upd_version"
        private const val EX_UPD_BUILD = "upd_build"
        private const val EX_UPD_SIZE = "upd_size"
        private const val EX_UPD_SHA = "upd_sha"
        private const val MODE_ORIGINALS_DELETE = "originals_delete"
        private const val MODE_ORIGINALS_KEEP = "originals_keep"
        private const val EX_RETRY = "retry"
        private const val DONE_NOTIF_ID = 4212
        /** The summaries of the two stacks of results ([refreshSummary]). */
        private const val SUMMARY_ANALYSIS_ID = 4240
        private const val SUMMARY_ACTIONS_ID = 4241
        /** How long the unanswered keep/delete choice stays in the notifications (10 min). */
        private const val DONE_TIMEOUT_MS = 10 * 60_000L
        private const val RESULT_CHANNEL = "results"
        private const val LIVE_CHANNEL = "progress_island"
        private const val GROUP_ONGOING = "cripta_ongoing"
        private const val LIVE_CHANNEL_HIGH = "progress_island_debug_high"
        private const val MODE_DEBUG_PROGRESS = "debug_progress"
        private const val MODE_DEBUG_CHOICE = "debug_choice"
        private const val MODE_DEBUG_DISMISS = "debug_dismiss"
        private const val MODE_CANCEL_DEBUG = "cancel_debug"
        private const val DEBUG_NOTIF_ID = 4230
        private const val MODE_DROP_OUTCOME = "drop_outcome"
        private const val EX_NOTIF_ID = "notif_id"
        private const val EX_DEBUG_KIND = "debug_kind"
        private const val BRAND_COLOR = 0xFF5AA9FF.toInt()

        /**
         * Encrypt [uris] into [folderId] (null = root). Progress and the outcome are published to
         * [VaultRepository.importState]; with the "Chiedi" policy the imported originals land in
         * [VaultRepository.pendingOriginals]. [fromShare] = files received from another app's share
         * sheet: they are never deleted afterwards, whatever the policy.
         */
        fun startImport(ctx: Context, uris: List<Uri>, folderId: Long?, fromShare: Boolean = false) {
            if (uris.isEmpty()) return
            // One start per chunk: every uri travels twice (extra + ClipData), and a "select all"
            // of a few thousand files went over the binder limit and crashed the app (nothing
            // imported). The chunks share the run's start, so they never take each other's
            // files for previous versions.
            val runStart = System.currentTimeMillis()
            uris.chunked(IMPORT_CHUNK).forEach { chunk ->
                val i = Intent(ctx, ConversionService::class.java).apply {
                    putExtra(EX_MODE, MODE_IMPORT)
                    putParcelableArrayListExtra(EX_URIS, ArrayList(chunk))
                    folderId?.let { putExtra(EX_FOLDER, it) }
                    if (fromShare) putExtra(EX_FROM_SHARE, true)
                    putExtra(EX_RUN_START, runStart)
                    // Carry the read grant along (shared content uris are granted to the receiving
                    // activity; this keeps them readable by the service too).
                    clipData = android.content.ClipData.newRawUri(null, chunk.first()).also { clip ->
                        chunk.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ContextCompat.startForegroundService(ctx, i)
            }
        }

        /** Decrypt [ids] to the gallery, or into [tree] (a folder picked with ACTION_OPEN_DOCUMENT_TREE). */
        fun startDownload(ctx: Context, ids: List<String>, tree: Uri? = null) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_DOWNLOAD)
                putStringArrayListExtra(EX_IDS, ArrayList(ids))
                tree?.let { putExtra(EX_TREE, it.toString()) }
            }
            ContextCompat.startForegroundService(ctx, i)
        }

        /** Answer "delete or keep the original?" of an ASK conversion (same as its notification). */
        fun resolveOriginal(ctx: Context, originalId: String, delete: Boolean) {
            runCatching {
                ctx.startService(Intent(ctx, ConversionService::class.java)
                    .putExtra(EX_MODE, if (delete) MODE_DELETE_ORIG else MODE_DISMISS).putExtra(EX_ID, originalId))
            }
        }

        /** Cancel the running transcode (queued ones still run). */
        fun cancelConvert(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL_CONVERT))
            }
        }

        /** Run a duplicate scan (exact or [similar]) in the background, with a progress notification. */
        fun startDupScan(ctx: Context, similar: Boolean) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_DUP_SCAN)
                putExtra(EX_SIMILAR, similar)
            }
            ContextCompat.startForegroundService(ctx, i)
        }

        fun cancelDupScan(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL_SCAN)) }
        }

        /**
         * Analyse the videos not analysed yet for intros and outros ([manual]: asked by the user), or
         * only [fileIds], again even if analysed before.
         */
        fun startIntroScan(ctx: Context, manual: Boolean, fileIds: List<String>? = null) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java)
                .putExtra(EX_MODE, MODE_INTRO_SCAN).putExtra(EX_MANUAL, manual)
                .apply { if (fileIds != null) putStringArrayListExtra(EX_IDS, ArrayList(fileIds)) })
        }

        /**
         * A running scan: [done] of [total] in its current [phase]; for Settings. [stopping]:
         * Annulla was pressed and the scan is finishing the frame or photo in hand. [current]: the
         * name of the media in hand longest.
         */
        /**
         * [share]: how far the whole scan is (0..1), counting the frames read inside the media in
         * hand too; null: [done] / [total]. [inHand]: every media being analysed (name, how far
         * when known), longest in hand first.
         */
        data class IntroProgress(val done: Int, val total: Int, val phase: String, val stopping: Boolean = false, val current: String? = null,
                                 val share: Float? = null, val inHand: List<Pair<String, Float?>> = emptyList())

        /** Null while no intro/outro scan runs. */
        val introProgress = kotlinx.coroutines.flow.MutableStateFlow<IntroProgress?>(null)

        /** Throw the analysis away ([everything]: also manual marks and rejections) and analyse every video anew. */
        fun restartIntroScan(ctx: Context, everything: Boolean) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java)
                .putExtra(EX_MODE, MODE_INTRO_SCAN).putExtra(EX_MANUAL, true)
                .putExtra(EX_RESET, if (everything) "all" else "auto"))
        }

        /**
         * Find the faces of the photos and videos not analysed yet ([manual]: asked by the user), or of
         * [fileIds] only, again;
         * [reset]: "auto" forgets the analysis, "all" the user's choices too.
         */
        /**
         * Look closer at the videos [fileIds] where the user confirmed someone
         * ([com.cripta.app.data.face.FaceRepository.refine]), in the face scan; [force]: "Analizza
         * meglio", again even when nothing was confirmed since, its result posted.
         */
        fun startFaceRefine(ctx: Context, fileIds: List<String>, force: Boolean = false, manual: Boolean = force) {
            if (fileIds.isEmpty()) return
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java)
                .putExtra(EX_MODE, MODE_FACE_SCAN).putExtra(EX_MANUAL, manual).putExtra(EX_REFINE, true).putExtra(EX_FORCE, force)
                .putStringArrayListExtra(EX_IDS, ArrayList(fileIds)))
        }

        fun startFaceScan(ctx: Context, manual: Boolean, fileIds: List<String>? = null, reset: String? = null, deep: Boolean = false) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java)
                .putExtra(EX_MODE, MODE_FACE_SCAN).putExtra(EX_MANUAL, manual).putExtra(EX_DEEP, deep)
                .apply { if (fileIds != null) putStringArrayListExtra(EX_IDS, ArrayList(fileIds)); if (reset != null) putExtra(EX_RESET, reset) })
        }

        /** Null while no face scan runs. */
        val faceProgress = kotlinx.coroutines.flow.MutableStateFlow<IntroProgress?>(null)

        fun cancelFaceScan(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL_FACES)) }
        }

        fun cancelIntroScan(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL_INTRO)) }
        }

        /** Cancel the link being downloaded now (the rest of the queue continues). */
        fun cancelDownload(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL))
            }
        }

        /**
         * Queue an in-app video download of [url], capped at [maxHeight]px (null = source quality),
         * saved into [folderId] (null = root) with [tagIds]. Links queue up and run one at a time.
         */
        fun startDownloadUrl(ctx: Context, url: String, maxHeight: Int?, folderId: Long? = null, tagIds: List<Long> = emptyList()) {
            ContextCompat.startForegroundService(ctx, downloadUrlIntent(ctx, url, maxHeight, folderId, tagIds))
        }

        private fun downloadUrlIntent(ctx: Context, url: String, maxHeight: Int?, folderId: Long?, tagIds: List<Long>) =
            Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_DOWNLOAD_URL)
                putExtra(EX_URL, url)
                maxHeight?.let { putExtra(EX_HEIGHT, it) }
                folderId?.let { putExtra(EX_FOLDER, it) }
                if (tagIds.isNotEmpty()) putExtra(EX_TAGS, tagIds.toLongArray())
            }

        /** Download the app update [rel] in the background (see [downloadUpdate]). */
        fun startUpdateDownload(ctx: Context, rel: com.cripta.app.update.AppUpdater.Release) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_UPDATE)
                putExtra(EX_URL, rel.apkUrl)
                putExtra(EX_UPD_VERSION, rel.versionName)
                putExtra(EX_UPD_BUILD, rel.buildNumber)
                putExtra(EX_UPD_SIZE, rel.sizeBytes)
                rel.sha256Url?.let { putExtra(EX_UPD_SHA, it) }
            }
            ContextCompat.startForegroundService(ctx, i)
        }

        /**
         * Debug isola: a sample operation with Annulla, lasting Durata prova (see [debugRun]).
         * [kind]: convert, download, download_fail, import, export, scan, update.
         */
        fun debugProgress(ctx: Context, kind: String = "convert") {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java)
                .putExtra(EX_MODE, MODE_DEBUG_PROGRESS).putExtra(EX_DEBUG_KIND, kind))
        }
        /** Debug isola › Solo scelta: a sample two-button choice. */
        fun debugChoice(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_DEBUG_CHOICE))
        }

        /** Run the backup or restore waiting in [com.cripta.app.data.BackupJobs]. */
        fun startBackup(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_BACKUP))
        }

        fun cancelUpdateDownload(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL_UPDATE)) }
        }

        /** The import's "delete the originals?" was answered in the app: its notification goes. */
        fun dismissImportOriginalsChoice(ctx: Context) {
            runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(ResultKind.IMPORT.id) }
            // Also from the island, if it is showing there (the service holds it in its own notification).
            runCatching {
                ctx.startService(Intent(ctx, ConversionService::class.java)
                    .putExtra(EX_MODE, MODE_DROP_OUTCOME).putExtra(EX_NOTIF_ID, ResultKind.IMPORT.id))
            }
        }

        /** Queue a conversion; [after] overrides the saved "Dopo la conversione" choice for this one. */
        fun startConvert(ctx: Context, id: String, after: com.cripta.app.data.ConvertAfter? = null) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_CONVERT)
                putExtra(EX_ID, id)
                after?.let { putExtra(EX_CONVERT_AFTER, it.ordinal) }
            }
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}

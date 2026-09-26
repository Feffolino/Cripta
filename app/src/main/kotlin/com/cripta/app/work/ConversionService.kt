package com.cripta.app.work

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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that runs long crypto operations (encrypt-import, decrypt-download,
 * video transcode) off the UI, surviving app backgrounding, with a progress notification
 * showing percentage and ETA.
 */
@UnstableApi
@AndroidEntryPoint
class ConversionService : Service() {

    @Inject lateinit var repo: VaultRepository
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var converter: VideoConverter
    @Inject lateinit var ytdlp: com.cripta.app.media.YtdlpDownloader
    @Inject lateinit var thumbs: com.cripta.app.media.ThumbnailLoader
    @Inject lateinit var dupScanner: com.cripta.app.data.dedup.DuplicateScanner
    @Inject lateinit var dupStore: com.cripta.app.data.dedup.DupScanStore
    @Inject lateinit var session: com.cripta.app.security.SessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Number of in-flight commands; the foreground notification is only torn down when it hits 0,
     *  so a short task (e.g. import) can't stop the service while a long transcode is still running. */
    private val active = java.util.concurrent.atomic.AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Nothing may outlive the service: cancel whatever is still attached to its scope.
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
        // Import / export: stop after the file being processed (never half a file).
        if (mode == MODE_CANCEL_BATCH) {
            batchCancel = true
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        // "Elimina originali / Mantieni" of an import with the "Chiedi" policy (notification or app).
        if (mode == MODE_ORIGINALS_DELETE || mode == MODE_ORIGINALS_KEEP) {
            getSystemService(NotificationManager::class.java).cancel(ResultKind.IMPORT.id)
            val uris = repo.pendingOriginals.value
            repo.clearPendingOriginals()
            if (mode == MODE_ORIGINALS_KEEP || uris.isEmpty()) { stopIfIdle(startId); return START_NOT_STICKY }
            active.incrementAndGet()
            lastStartId = startId
            scope.launch {
                try {
                    repo.deleteOriginals(uris)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ConversionService", "delete originals failed", e)
                } finally {
                    if (active.decrementAndGet() == 0) stopSelf(lastStartId)
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
            startForeground(NOTIF_ID, build("Download", 0, indeterminate = true, icon = R.drawable.ic_notif_download))
            if (active.get() == 0) stopForeground(STOP_FOREGROUND_REMOVE)
            notifyResult("Download non riuscito", "Sblocca Cripta, poi tocca Riprova", ResultKind.DOWNLOAD, state = ResultState.FAILED,
                actions = listOf(retryAction(intent)))
            stopIfIdle(startId)
            return START_NOT_STICKY
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
        // Completion-notification actions: delete or keep the original video.
        if (mode == MODE_DELETE_ORIG) {
            val oid = intent.getStringExtra(EX_ID)
            getSystemService(NotificationManager::class.java).cancel(DONE_NOTIF_ID)
            // Answered (notification or the Cartelle banner): the banner stops asking.
            repo.updateConvertStatus {
                if (it.askOriginalId == oid) it.copy(askOriginalId = null, lastResult = "Originale eliminato. Resta la copia MP4.") else it
            }
            if (oid == null) { stopIfIdle(startId); return START_NOT_STICKY }
            active.incrementAndGet()
            lastStartId = startId
            val held = session.beginWork()
            scope.launch {
                try {
                    repo.deleteOrTrash(oid)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ConversionService", "delete original failed: $oid", e)
                } finally {
                    if (held) session.endWork()
                    if (active.decrementAndGet() == 0) stopSelf(lastStartId)
                }
            }
            return START_NOT_STICKY
        }
        if (mode == MODE_DISMISS) {
            getSystemService(NotificationManager::class.java).cancel(DONE_NOTIF_ID)
            val oid = intent.getStringExtra(EX_ID)
            repo.updateConvertStatus {
                if (it.askOriginalId == oid) it.copy(askOriginalId = null, lastResult = "Originale mantenuto accanto alla copia MP4.") else it
            }
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        ensureChannel()
        lastStartId = startId
        startForeground(NOTIF_ID, build("Preparazione…", 0, indeterminate = true))
        if (mode == MODE_DOWNLOAD_URL) {
            if (intent.getBooleanExtra(EX_RETRY, false)) getSystemService(NotificationManager::class.java).cancel(ResultKind.DOWNLOAD.id)
            val url = intent.getStringExtra(EX_URL)
            if (url != null) {
                val job = VaultRepository.DownloadJob(
                    id = java.util.UUID.randomUUID().toString(),
                    url = url,
                    maxHeight = intent.getIntExtra(EX_HEIGHT, 0).takeIf { it > 0 },
                    folderId = if (intent.hasExtra(EX_FOLDER)) intent.getLongExtra(EX_FOLDER, -1).takeIf { it >= 0 } else null,
                    tagIds = intent.getLongArrayExtra(EX_TAGS)?.toList().orEmpty(),
                )
                repo.enqueueDownload(job)
                // Persisted so a waiting link survives the app being killed (resumed at next unlock).
                scope.launch {
                    repo.putPendingJob(job.id, "download", org.json.JSONObject()
                        .put("url", job.url).put("height", job.maxHeight ?: 0).put("folder", job.folderId ?: -1L)
                        .put("tags", org.json.JSONArray(job.tagIds)).toString())
                }
            }
            // A worker already running picks the new link up from the queue.
            val start = synchronized(workerLock) { if (downloadWorkerRunning) false else { downloadWorkerRunning = true; true } }
            if (!start) return START_NOT_STICKY
        }
        active.incrementAndGet()
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
                        val imported = importBatch(uris, folderId)
                        // Files shared from another app are that app's content, not documents we
                        // may delete: the "delete originals" policy only applies to picked files.
                        if (!intent.getBooleanExtra(EX_FROM_SHARE, false)) applyDeletePolicy(imported)
                    }
                    MODE_DOWNLOAD -> {
                        val ids = intent.getStringArrayListExtra(EX_IDS) ?: arrayListOf()
                        exportBatch(ids)
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
                    MODE_DUP_SCAN -> {
                        val similar = intent.getBooleanExtra(EX_SIMILAR, false)
                        scanDuplicates(similar)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // best-effort; individual items already guarded below
                android.util.Log.e("ConversionService", "job failed: $mode", e)
            } finally {
                if (holdsSession) session.endWork()
                if (active.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // The latest start id: stops only if no newer command arrived meanwhile.
                    stopSelf(lastStartId)
                }
            }
        }
        if (mode == MODE_CONVERT) convertJob = job
        if (mode == MODE_DUP_SCAN) scanJob = job
        return START_NOT_STICKY
    }

    @Volatile private var convertJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastStartId = 0
    @Volatile private var scanJob: kotlinx.coroutines.Job? = null
    /** Guards [downloadWorkerRunning] so a link enqueued while the worker drains is never missed. */
    private val workerLock = Any()
    private var downloadWorkerRunning = false
    /** Queue id of the link being downloaded now (null when none). */
    @Volatile private var currentDownloadId: String? = null

    /** Stop the service after a control command, unless work is still running. */
    private fun stopIfIdle(startId: Int) {
        if (active.get() == 0) stopSelf(startId)
    }
    /** Id of the running yt-dlp process, so the Cancel action can kill the native process (a coroutine
     *  cancel alone can't interrupt the blocking execute call). */
    @Volatile private var ytdlpProcessId: String? = null
    /** True when the user hit Cancel, so a resulting yt-dlp failure is reported as "annullato". */
    @Volatile private var cancelRequested = false

    /** Set by the Annulla of an import / export: the batch stops after the current file. */
    @Volatile private var batchCancel = false

    /** Decrypt [ids] back to the gallery one by one ("Esporta"), with progress and a final result. */
    private suspend fun exportBatch(ids: List<String>) {
        val total = ids.size
        if (total == 0) return
        batchCancel = false
        val start = SystemClock.elapsedRealtime()
        var saved = 0; var failed = 0; var done = 0
        notify(build("Esportazione in galleria", 0, sub = "File 1 di $total", chip = "0/$total",
            cancelable = true, cancelMode = MODE_CANCEL_BATCH, icon = R.drawable.ic_notif_export))
        for (id in ids) {
            if (batchCancel) break
            // One failed item doesn't stop the batch, but a cancellation does.
            try {
                val f = repo.fileById(id)
                if (f != null) { repo.restoreToGallery(f); saved++ } else failed++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ConversionService", "export item failed", e)
                failed++
            }
            done++
            val pct = done * 100 / total
            val elapsed = SystemClock.elapsedRealtime() - start
            val eta = if (done < total) etaText(elapsed / done * (total - done)) else null
            notify(build("Esportazione in galleria", pct, sub = if (done < total) "File ${done + 1} di $total" else "Completamento…",
                chip = "$done/$total", header = eta, cancelable = true, cancelMode = MODE_CANCEL_BATCH, icon = R.drawable.ic_notif_export))
        }
        val cancelled = done < total
        val text = buildString {
            append(if (saved == 1) "1 file salvato nella galleria" else "$saved file salvati nella galleria")
            if (failed > 0) append(" · $failed non esportati")
        }
        when {
            cancelled -> notifyResult("Esportazione annullata", text, ResultKind.EXPORT, state = ResultState.CANCELLED)
            saved == 0 -> notifyResult("Esportazione non riuscita", text, ResultKind.EXPORT, state = ResultState.FAILED)
            else -> notifyResult(if (failed == 0) "Esportazione completata" else "Esportazione completata con errori", text, ResultKind.EXPORT)
        }
    }

    /**
     * Encrypt [uris] into the vault one by one, publishing live progress (file n of N, name, bytes
     * of the current file) to [VaultRepository.importState] for the in-app banner and to the
     * notification, then a final "completed" notification with the outcome.
     */
    private suspend fun importBatch(uris: List<Uri>, folderId: Long?): List<Uri> {
        if (uris.isEmpty()) return emptyList()
        val done = mutableListOf<Uri>()
        repo.importBegin(uris.size)
        batchCancel = false
        var cancelled = false
        val start = SystemClock.elapsedRealtime()
        try {
            for (uri in uris) {
                if (batchCancel) { cancelled = true; break }
                val (name, size) = repo.nameAndSize(uri)
                repo.importCurrent(name, size)
                val st = repo.importState.value
                // Never a file name in a notification (it shows outside the vault): counts only.
                notify(importNote(st.done + 1, st.total, (st.fraction * 100).toInt(), "cifratura in corso", start))
                var lastNotify = 0L
                var imported: com.cripta.app.data.db.FileEntity? = null
                val ok = try {
                    imported = repo.import(uri, folderId) { bytes ->
                        repo.importBytes(bytes)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNotify > 500) {
                            lastNotify = now
                            val cur = repo.importState.value
                            val filePct = if (size > 0) "cifrato al ${(bytes * 100 / size).coerceAtMost(100)}%" else "cifratura in corso"
                            notify(importNote(cur.done + 1, cur.total, (cur.fraction * 100).toInt(), filePct, start))
                        }
                    }
                    true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Throwable: an OutOfMemoryError on one odd file must not kill the whole batch.
                    android.util.Log.e("ConversionService", "import failed: $name", e)
                    repo.importFailed(name, UserErrors.of(e))
                    false
                }
                // Already in the vault? (byte-identical; only same-size files are even checked)
                imported?.let { f ->
                    val copies = try { dupScanner.copiesOf(f) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
                    copies?.firstOrNull()?.let { existing ->
                        repo.importDuplicate(f.id, existing.originalName)
                    }
                }
                repo.importItemDone(ok, if (ok) uri else null)
                if (ok) done += uri
                val cur = repo.importState.value
                if (cur.done < cur.total) notify(importNote(cur.done + 1, cur.total, (cur.fraction * 100).toInt(), "in coda", start))
            }
        } finally {
            withContext(NonCancellable) {
                repo.importEnd()?.let { fin ->
                    val title = when {
                        cancelled -> "Importazione annullata"
                        fin.succeeded == 0 && fin.failed > 0 -> "Importazione non riuscita"
                        fin.failed == 0 -> "Importazione completata"
                        else -> "Importazione completata con errori"
                    }
                    val text = importSummary(fin)
                    lastImportSummary = text
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

    /** "3 file cifrati nel vault · 1 non importato · 2 erano già presenti". */
    private fun importSummary(fin: VaultRepository.ImportState) = buildString {
        append(if (fin.succeeded == 1) "1 file cifrato nel vault" else "${fin.succeeded} file cifrati nel vault")
        if (fin.failed > 0) append(" · ${fin.failed} non importati")
        if (fin.duplicates.isNotEmpty()) append(" · ${fin.duplicates.size} erano già presenti")
    }
    /** Summary of the last finished import, reused by the "Chiedi" originals notification. */
    @Volatile private var lastImportSummary: String? = null

    /** Progress of an import: file [n] of [total] and what is happening to it; never its name. */
    private fun importNote(n: Int, total: Int, pct: Int, what: String, start: Long): Notification {
        val done = n - 1
        val elapsed = SystemClock.elapsedRealtime() - start
        val eta = if (done > 0 && done < total) etaText(elapsed / done * (total - done)) else null
        return build("Importazione", pct, sub = "File $n di $total · $what", chip = "$done/$total", header = eta,
            cancelable = true, cancelMode = MODE_CANCEL_BATCH, icon = R.drawable.ic_notif_import)
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
        repo.updateConvertStatus { it.copy(waiting = convertWaiting.incrementAndGet() - 1) }
        try {
            convertMutex.lock()
        } catch (e: kotlinx.coroutines.CancellationException) {
            convertWaiting.decrementAndGet(); repo.setConverting(id, false); throw e
        }
        convertWaiting.decrementAndGet()
        currentConvertJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        repo.updateConvertStatus { it.copy(currentName = file.originalName, pct = 0, waiting = convertWaiting.get(), lastResult = null) }
        // No file names in notifications (the in-app banner shows it, behind the lock).
        val queued = { convertWaiting.get().let { if (it > 0) "$it in coda" else null } }
        notify(build("Conversione in MP4", 0, sub = "Preparazione del video…", indeterminate = true, header = queued(),
            cancelable = true, cancelMode = MODE_CANCEL_CONVERT, icon = R.drawable.ic_notif_convert))
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
                    notify(build("Conversione in MP4", pct, sub = "Codifica del video · $pct%", header = queued(),
                        cancelable = true, cancelMode = MODE_CANCEL_CONVERT, icon = R.drawable.ic_notif_convert))
                }
            }
            // Never import a broken transcode (a corrupt output that looked valid could lead to losing
            // the good original). Verify it thoroughly first.
            val problem = verifyConversion(out!!, src!!, file.durationMs)
            if (problem != null) {
                result = false to "Conversione scartata: $problem. Originale intatto."
                notifyResult("Conversione non riuscita", "${problem.replaceFirstChar { it.uppercase() }} · originale intatto", state = ResultState.FAILED)
                return
            }
            val mode = afterOverride
                ?: runCatching { settings.settingsOnce().convertAfter }.getOrDefault(com.cripta.app.data.ConvertAfter.REPLACE)
            val replace = mode == com.cripta.app.data.ConvertAfter.REPLACE
            val newFile = repo.importConvertedMp4(file, out!!, replace = replace)
            thumbs.copyCustomCover(file.id, newFile.id)   // keep a cover the user picked
            repo.emitConvertResult(
                VaultRepository.ConversionEvent(id, newFile.id, ask = mode == com.cripta.app.data.ConvertAfter.ASK)
            )
            when (mode) {
                com.cripta.app.data.ConvertAfter.ASK -> {
                    postConvertDone(id)
                    repo.updateConvertStatus { it.copy(askOriginalId = id) }
                }
                com.cripta.app.data.ConvertAfter.REPLACE -> {
                    val days = runCatching { settings.settingsOnce().trashDays }.getOrDefault(7)
                    notifyResult("Conversione completata", "Copia MP4 creata · originale nel cestino per $days giorni")
                }
                com.cripta.app.data.ConvertAfter.KEEP_BOTH -> notifyResult("Conversione completata", "Copia MP4 aggiunta accanto all'originale")
            }
            result = true to when {
                replace -> "Convertito: ${newFile.originalName} (originale nel cestino)"
                mode == com.cripta.app.data.ConvertAfter.ASK -> "Convertito: ${newFile.originalName}. Eliminare l'originale?"
                else -> "Convertito: ${newFile.originalName}"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            result = false to "Conversione annullata. Originale intatto."
            notifyResult("Conversione annullata", "File originale intatto", state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "convert failed: $id", e)
            val msg = convertErrorText(e)
            result = false to "Conversione fallita: $msg. Originale intatto."
            notifyResult("Conversione non riuscita", "${msg.replaceFirstChar { it.uppercase() }} · originale intatto", state = ResultState.FAILED)
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
        if (!out.exists() || out.length() <= 0L) return "file vuoto"
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
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return "nessuna traccia video"
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (dur <= 0) return "durata non valida"
            if (srcDur != null && srcDur > 0 && (dur < srcDur * 9 / 10 || dur > srcDur * 11 / 10)) {
                return "durata diversa dall'originale"
            }
            if (srcAudio && r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") return "audio perso"
            for (frac in listOf(0.1, 0.5, 0.9)) {
                val us = (dur * frac * 1000).toLong()
                val frame = runCatching { r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
                    ?: return "fotogrammi illeggibili"
                frame.recycle()
            }
            null
        } catch (e: Exception) {
            "file non leggibile"
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

    /**
     * Duplicate scan (exact or similar) in the service, so it continues in the background with a
     * progress notification; results go to [DupScanStore] and a "finished" notification that
     * opens them on tap.
     */
    private suspend fun scanDuplicates(similar: Boolean) {
        val mode = if (similar) com.cripta.app.data.dedup.DupScanStore.Mode.SIMILAR else com.cripta.app.data.dedup.DupScanStore.Mode.EXACT
        val label = if (similar) "Ricerca media simili" else "Ricerca duplicati"
        dupStore.start(mode)
        notify(build(label, 0, sub = "Preparazione…", indeterminate = true, cancelable = true, cancelMode = MODE_CANCEL_SCAN, icon = R.drawable.ic_notif_scan))
        var last = 0L
        val onProgress: (Int, Int) -> Unit = { done, total ->
            dupStore.progress(done, total)
            val now = SystemClock.elapsedRealtime()
            if (now - last > 400 || done == total) {
                last = now
                val pct = if (total > 0) done * 100 / total else 0
                notify(build(label, pct, sub = if (total > 0) "$done di $total elementi confrontati" else "Preparazione…",
                    indeterminate = total == 0, cancelable = true, cancelMode = MODE_CANCEL_SCAN, icon = R.drawable.ic_notif_scan))
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
            val title = if (groups.isEmpty()) "$label completata" else if (similar) "Media simili trovati" else "Duplicati trovati"
            val text = if (groups.isEmpty()) "Nessun risultato su $scanned elementi"
                else "${groups.size} gruppi su $scanned elementi"
            notifyResult(title, text, ResultKind.SCAN, openDuplicates = true,
                actions = if (groups.isEmpty()) emptyList()
                    else listOf(NotificationCompat.Action(0, "Vedi risultati", openAppIntent(openDuplicates = true))))
        } catch (e: kotlinx.coroutines.CancellationException) {
            dupStore.cancelled()
            notifyResult("$label annullata", null, ResultKind.SCAN, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "duplicate scan failed", e)
            val msg = UserErrors.of(e)
            dupStore.fail(msg)
            notifyResult("$label non riuscita", msg, ResultKind.SCAN, state = ResultState.FAILED)
        } finally {
            scanJob = null
        }
    }

    /** Process the downloader queue one link at a time until it is empty. */
    private suspend fun downloadWorker() {
        var drained = false
        try {
            while (true) {
                val job = synchronized(workerLock) {
                    repo.takeNextDownload() ?: run { downloadWorkerRunning = false; drained = true; null }
                } ?: break
                downloadUrl(job)
            }
        } finally {
            // Exited early (e.g. cancelled): release the worker slot so the next link can start one.
            if (!drained) synchronized(workerLock) { downloadWorkerRunning = false }
        }
    }

    /**
     * Download a remote video (direct link or HLS) to MP4 and encrypt it into the vault, in the
     * folder and with the tags chosen for [job]. Progress and a Cancel action live in the
     * notification; the source link is stored on the new file. Cancelling stops only this link:
     * the rest of the queue continues.
     */
    private suspend fun downloadUrl(job: VaultRepository.DownloadJob) {
        val url = job.url
        var produced: java.io.File? = null
        val pid = java.util.UUID.randomUUID().toString()
        cancelRequested = false
        ytdlpProcessId = pid
        currentDownloadId = job.id
        fun set(f: (VaultRepository.DownloadJob) -> VaultRepository.DownloadJob) = repo.updateDownload(job.id, f)
        val waiting = { repo.downloads.value.count { it.phase == VaultRepository.DownloadPhase.QUEUED } }
        try {
            repo.setConversionProgress(0)
            set { it.copy(phase = VaultRepository.DownloadPhase.PREPARING) }
            // First download extracts the yt-dlp/Python payload; keep the notification indeterminate
            // until real progress arrives.
            val queued = { waiting().let { if (it > 0) "$it in coda" else null } }
            notify(build("Download", 0, sub = "Analisi del link…", indeterminate = true, header = queued(), cancelable = true, icon = R.drawable.ic_notif_download))
            produced = withContext(Dispatchers.IO) {
                ytdlp.download(url, job.maxHeight, pid) { pct, eta ->
                    repo.setConversionProgress(pct)
                    set { it.copy(phase = VaultRepository.DownloadPhase.DOWNLOADING, pct = pct, etaSec = eta) }
                    val sub = if (eta > 0) "$pct% · ${etaText(eta * 1000)}" else "$pct%"
                    notify(build("Download", pct, sub = sub, header = queued(), cancelable = true, icon = R.drawable.ic_notif_download))
                }
            }
            if (cancelRequested) throw java.io.InterruptedIOException("cancelled")
            if (!isPlayableVideo(produced, null)) {
                val msg = "Nessun video valido (link errato o DRM)"
                set { it.copy(phase = VaultRepository.DownloadPhase.FAILED, message = msg) }
                notifyResult("Download non riuscito", msg, ResultKind.DOWNLOAD, state = ResultState.FAILED,
                    actions = listOf(retryAction(job)))
                return
            }
            val name = produced.name.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "download"
            val file = repo.importDownloadedMp4(produced, name, folderId = job.folderId, sourceUrl = url, tagIds = job.tagIds)
            set { it.copy(phase = VaultRepository.DownloadPhase.DONE, pct = 100, message = name, fileId = file.id) }
            // The title stays inside the app (queue, vault): the notification only says it is done.
            notifyResult("Download completato", "Video cifrato nel vault", ResultKind.DOWNLOAD)
        } catch (e: kotlinx.coroutines.CancellationException) {
            set { it.copy(phase = VaultRepository.DownloadPhase.CANCELLED) }
            notifyResult("Download annullato", null, ResultKind.DOWNLOAD, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            // A yt-dlp process killed by the Cancel action surfaces as an ordinary exception, not a
            // coroutine cancellation, so distinguish it here. Otherwise surface the real cause.
            if (cancelRequested) {
                set { it.copy(phase = VaultRepository.DownloadPhase.CANCELLED) }
                notifyResult("Download annullato", null, ResultKind.DOWNLOAD, state = ResultState.CANCELLED)
            } else {
                android.util.Log.e("ConversionService", "download failed: $url", e)
                val msg = UserErrors.ofDownload(e)
                set { it.copy(phase = VaultRepository.DownloadPhase.FAILED, message = msg) }
                notifyResult("Download non riuscito", msg, ResultKind.DOWNLOAD, state = ResultState.FAILED,
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
            export.errorCode in 3000..3999 -> "formato del video non supportato dal dispositivo"
            export.errorCode in 4000..4999 -> "codec del dispositivo non disponibile"
            else -> "errore durante la codifica"
        }
    }

    private fun etaText(ms: Long): String {
        val s = ms / 1000
        return if (s >= 60) "resta ${s / 60}:${(s % 60).toString().padStart(2, '0')}" else "resta ${s}s"
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Operazioni in corso", NotificationManager.IMPORTANCE_LOW)
                )
            }
            // Outcomes get their own channel (normal importance) so they are noticed, while the
            // ongoing progress stays silent.
            if (mgr.getNotificationChannel(RESULT_CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(RESULT_CHANNEL, "Operazioni completate", NotificationManager.IMPORTANCE_DEFAULT)
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
     *    compact it shows the operation's [icon] (the Cripta shield with its symbol) and [chip]
     *    (default: the percentage, "Avvio" while there is none yet).
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
        icon: Int = R.drawable.ic_notification,
        chip: String? = null,
        header: String? = null,
    ): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(sub)
            .setSubText(header)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(title, icon))
        val chipText = chip ?: if (indeterminate) "Avvio" else "$pct%"
        b.addExtras(liveUpdate(chipText))
        var cancelPi: android.app.PendingIntent? = null
        if (cancelable) {
            val cancelIntent = Intent(this, ConversionService::class.java).putExtra(EX_MODE, cancelMode)
            val req = when (cancelMode) { MODE_CANCEL -> 1; MODE_CANCEL_SCAN -> 4; MODE_CANCEL_BATCH -> 5; else -> 6 }
            val pi = android.app.PendingIntent.getService(
                this, req, cancelIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "Annulla", pi)
            cancelPi = pi
        }
        // HyperOS: the Hyper Island's own template on top (large rounded buttons); ignored elsewhere.
        b.addExtras(HyperFocus.extras(
            this, "cripta_progress", title, sub, chipText, icon,
            progress = if (indeterminate) 0 else pct,
            buttons = listOfNotNull(cancelPi?.let { HyperFocus.Button("cancel", "Annulla", it) }),
            float = false,
        ))
        return b.build()
    }

    /** Extras asking for a Live Update (promoted ongoing) with [chip] as its short text. */
    private fun liveUpdate(chip: String) = android.os.Bundle().apply {
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

    /**
     * Update the ongoing notification, at most about once a second. Progress arrived far more often
     * (yt-dlp several times a second, the transcoder every 500 ms even when unchanged) and every
     * post re-laid out the notification and the Hyper Island: it looked laggy, and Android drops
     * posts beyond a few per second anyway, so the bar jumped. An identical update is skipped;
     * a new operation (another title) always goes through at once.
     */
    @Synchronized
    private fun notify(n: Notification) {
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val key = listOf(
            title, e.getCharSequence(Notification.EXTRA_TEXT), e.getCharSequence(Notification.EXTRA_SUB_TEXT),
            e.getInt(Notification.EXTRA_PROGRESS), e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
        ).joinToString("|")
        if (key == lastPostKey) return
        val now = SystemClock.elapsedRealtime()
        if (title == lastPostTitle && now - lastPostAt < 1000L) return
        lastPostAt = now; lastPostKey = key; lastPostTitle = title
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    /** Which kind of operation a result belongs to; each keeps its own notification. */
    private enum class ResultKind(val id: Int, val icon: Int) {
        IMPORT(4220, R.drawable.ic_notif_import), DOWNLOAD(4221, R.drawable.ic_notif_download),
        CONVERT(4222, R.drawable.ic_notif_convert), SCAN(4223, R.drawable.ic_notif_scan),
        EXPORT(4224, R.drawable.ic_notif_export),
    }

    /** How an operation ended; its icon is the shield with a tick, an exclamation mark, or (when
     *  cancelled) the operation's own symbol. */
    private enum class ResultState { DONE, FAILED, CANCELLED }

    /** Tap on a notification: bring the app to the front (optionally straight to the duplicate results). */
    private fun openAppIntent(openDuplicates: Boolean = false): android.app.PendingIntent = android.app.PendingIntent.getActivity(
        this, if (openDuplicates) 11 else 10,
        Intent(this, com.cripta.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (openDuplicates) putExtra(com.cripta.app.MainActivity.EXTRA_OPEN_DUPLICATES, true) },
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
    ) {
        val icon = when (state) {
            ResultState.DONE -> R.drawable.ic_notif_done
            ResultState.FAILED -> R.drawable.ic_notif_error
            ResultState.CANCELLED -> kind.icon
        }
        val public = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .build()
        val b = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(text?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setCategory(if (state == ResultState.FAILED) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent(openDuplicates))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
        actions.forEach { b.addAction(it) }
        getSystemService(NotificationManager::class.java).notify(kind.id, b.build())
    }

    /** "Riprova" for a failed download: queues the same link again, same quality, folder and tags. */
    private fun retryAction(job: VaultRepository.DownloadJob): NotificationCompat.Action =
        retryAction(downloadUrlIntent(this, job.url, job.maxHeight, job.folderId, job.tagIds))

    private fun retryAction(download: Intent): NotificationCompat.Action {
        val i = Intent(download).putExtra(EX_RETRY, true)
        val pi = android.app.PendingIntent.getForegroundService(
            this, 7, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(0, "Riprova", pi)
    }

    /**
     * A choice left to answer at the end of an operation: a Live Update (ongoing, promoted) so the
     * Hyper Island / status chip keeps it; compact it reads "Fatto", tapped or expanded it shows
     * the buttons. After [DONE_TIMEOUT_MS] unanswered it goes away and the choice stays in the app.
     * Buttons and details are hidden on the lock screen.
     */
    private fun postChoice(id: Int, icon: Int, title: String, text: String, vararg actions: NotificationCompat.Action) {
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(title, icon))
            .addExtras(liveUpdate("Fatto"))
        actions.forEach { b.addAction(it) }
        // HyperOS: the choice as the island's own buttons, popping it open once.
        b.addExtras(HyperFocus.extras(
            this, "cripta_choice", title, text, "Fatto", icon, progress = null,
            buttons = actions.mapIndexedNotNull { i, a -> a.actionIntent?.let { HyperFocus.Button("choice$i", a.title.toString(), it) } },
            float = true,
        ))
        getSystemService(NotificationManager::class.java).notify(id, b.build())
    }

    private fun serviceAction(label: String, mode: String, req: Int, originalId: String? = null) = NotificationCompat.Action(
        0, label,
        android.app.PendingIntent.getService(
            this, req,
            Intent(this, ConversionService::class.java).putExtra(EX_MODE, mode).apply { originalId?.let { putExtra(EX_ID, it) } },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )

    /** An ASK conversion finished: delete (to the trash) or keep the original video. */
    private fun postConvertDone(originalId: String) = postChoice(
        DONE_NOTIF_ID, R.drawable.ic_notif_done, "Conversione completata", "Copia MP4 creata. Eliminare l'originale?",
        serviceAction("Elimina originale", MODE_DELETE_ORIG, 2, originalId),
        serviceAction("Mantieni", MODE_DISMISS, 3, originalId),
    )

    /** An import with the "Chiedi" policy finished: delete the originals from the device, or keep them. */
    private fun postOriginalsChoice(n: Int) {
        if (n <= 0) return
        val summary = lastImportSummary?.let { "$it. " }.orEmpty()
        postChoice(
            ResultKind.IMPORT.id, R.drawable.ic_notif_done, "Importazione completata",
            summary + if (n == 1) "Eliminare l'originale dal dispositivo?" else "Eliminare i $n originali dal dispositivo?",
            serviceAction(if (n == 1) "Elimina originale" else "Elimina originali", MODE_ORIGINALS_DELETE, 8),
            serviceAction("Mantieni", MODE_ORIGINALS_KEEP, 9),
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
        private const val MODE_IMPORT = "import"
        private const val MODE_DOWNLOAD = "download"
        private const val MODE_CONVERT = "convert"
        private const val MODE_CANCEL = "cancel"
        private const val MODE_CANCEL_CONVERT = "cancel_convert"
        private const val MODE_DUP_SCAN = "dup_scan"
        private const val MODE_CANCEL_SCAN = "cancel_scan"
        private const val EX_SIMILAR = "similar"
        private const val EX_CONVERT_AFTER = "convert_after"
        private const val MODE_DELETE_ORIG = "delete_orig"
        private const val MODE_DISMISS = "dismiss"
        private const val MODE_DOWNLOAD_URL = "download_url"
        private const val EX_URL = "url"
        private const val EX_HEIGHT = "height"
        private const val EX_TAGS = "tags"
        private const val MODE_CANCEL_BATCH = "cancel_batch"
        private const val MODE_ORIGINALS_DELETE = "originals_delete"
        private const val MODE_ORIGINALS_KEEP = "originals_keep"
        private const val EX_RETRY = "retry"
        private const val DONE_NOTIF_ID = 4212
        /** How long the unanswered keep/delete choice stays in the notifications (10 min). */
        private const val DONE_TIMEOUT_MS = 10 * 60_000L
        private const val RESULT_CHANNEL = "results"
        private const val BRAND_COLOR = 0xFF5AA9FF.toInt()

        /**
         * Encrypt [uris] into [folderId] (null = root). Progress and the outcome are published to
         * [VaultRepository.importState]; with the "Chiedi" policy the imported originals land in
         * [VaultRepository.pendingOriginals]. [fromShare] = files received from another app's share
         * sheet: they are never deleted afterwards, whatever the policy.
         */
        fun startImport(ctx: Context, uris: List<Uri>, folderId: Long?, fromShare: Boolean = false) {
            if (uris.isEmpty()) return
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_IMPORT)
                putParcelableArrayListExtra(EX_URIS, ArrayList(uris))
                folderId?.let { putExtra(EX_FOLDER, it) }
                if (fromShare) putExtra(EX_FROM_SHARE, true)
                // Carry the read grant along (shared content uris are granted to the receiving
                // activity; this keeps them readable by the service too).
                clipData = android.content.ClipData.newRawUri(null, uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ContextCompat.startForegroundService(ctx, i)
        }

        fun startDownload(ctx: Context, ids: List<String>) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_DOWNLOAD)
                putStringArrayListExtra(EX_IDS, ArrayList(ids))
            }
            ContextCompat.startForegroundService(ctx, i)
        }

        /** Cancel the running transcode (queued ones still run). */
        /** Answer "delete or keep the original?" of an ASK conversion (same as its notification). */
        fun resolveOriginal(ctx: Context, originalId: String, delete: Boolean) {
            runCatching {
                ctx.startService(Intent(ctx, ConversionService::class.java)
                    .putExtra(EX_MODE, if (delete) MODE_DELETE_ORIG else MODE_DISMISS).putExtra(EX_ID, originalId))
            }
        }

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

        /** Cancel the link being downloaded now (the rest of the queue continues). */
        fun cancelDownload(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL))
            }
        }

        /**
         * Queue an in-app download of [url] to MP4, capped at [maxHeight]px (null = source quality),
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

        /** The import's "delete the originals?" was answered in the app: its notification goes. */
        fun dismissImportOriginalsChoice(ctx: Context) {
            runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(ResultKind.IMPORT.id) }
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

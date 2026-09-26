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
                        run("Download", ids.size) { i -> repo.fileById(ids[i])?.let { repo.restoreToGallery(it) } }
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

    private inline fun run(label: String, total: Int, op: (Int) -> Unit) {
        if (total == 0) return
        val start = SystemClock.elapsedRealtime()
        for (i in 0 until total) {
            // One failed item doesn't stop the batch, but a cancellation does.
            try {
                op(i)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ConversionService", "$label item $i failed", e)
            }
            val done = i + 1
            val pct = done * 100 / total
            val elapsed = SystemClock.elapsedRealtime() - start
            val eta = if (done > 0) (elapsed / done) * (total - done) else 0L
            notify(build("$label $done/$total", pct, sub = "${pct}% · ${etaText(eta)}"))
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
        val start = SystemClock.elapsedRealtime()
        try {
            for (uri in uris) {
                val (name, size) = repo.nameAndSize(uri)
                repo.importCurrent(name, size)
                val st = repo.importState.value
                notify(build("Importazione ${st.done + 1}/${st.total}", (st.fraction * 100).toInt(), sub = name))
                var lastNotify = 0L
                var imported: com.cripta.app.data.db.FileEntity? = null
                val ok = try {
                    imported = repo.import(uri, folderId) { bytes ->
                        repo.importBytes(bytes)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNotify > 500) {
                            lastNotify = now
                            val cur = repo.importState.value
                            val filePct = if (size > 0) " · ${(bytes * 100 / size).coerceAtMost(100)}%" else ""
                            notify(build("Importazione ${cur.done + 1}/${cur.total}", (cur.fraction * 100).toInt(), sub = "$name$filePct"))
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
                val elapsed = SystemClock.elapsedRealtime() - start
                val left = cur.total - cur.done
                val eta = if (cur.done > 0 && left > 0) " · ${etaText(elapsed / cur.done * left)}" else ""
                notify(build("Importazione ${cur.done}/${cur.total}", (cur.fraction * 100).toInt(), sub = "${(cur.fraction * 100).toInt()}%$eta"))
            }
        } finally {
            withContext(NonCancellable) {
                repo.importEnd()?.let { fin ->
                    val title = if (fin.failed == 0) "Importazione completata" else "Importazione completata con errori"
                    val text = buildString {
                        append(if (fin.succeeded == 1) "1 file cifrato nel vault" else "${fin.succeeded} file cifrati nel vault")
                        if (fin.failed > 0) append(" · ${fin.failed} non importati")
                        if (fin.duplicates.isNotEmpty()) append(" · ${fin.duplicates.size} erano già presenti")
                    }
                    notifyResult(title, text, ResultKind.IMPORT)
                }
            }
        }
        return done
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
        notify(build("Conversione in MP4", 0, sub = file.originalName, indeterminate = true, cancelMode = MODE_CANCEL_CONVERT))
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
                    val q = convertWaiting.get().let { if (it > 0) " · $it in coda" else "" }
                    notify(build("Conversione in MP4", pct, sub = "$pct% · ${file.originalName}$q", cancelable = true, cancelMode = MODE_CANCEL_CONVERT))
                }
            }
            // Never import a broken transcode (a corrupt output that looked valid could lead to losing
            // the good original). Verify it thoroughly first.
            val problem = verifyConversion(out!!, src!!, file.durationMs)
            if (problem != null) {
                result = false to "Conversione scartata: $problem. Originale intatto."
                notifyResult("Conversione non riuscita", "$problem · originale intatto")
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
                    notifyResult("Convertito in MP4", "${newFile.originalName} · originale nel cestino per $days giorni")
                }
                com.cripta.app.data.ConvertAfter.KEEP_BOTH -> notifyResult("Convertito in MP4", newFile.originalName)
            }
            result = true to when {
                replace -> "Convertito: ${newFile.originalName} (originale nel cestino)"
                mode == com.cripta.app.data.ConvertAfter.ASK -> "Convertito: ${newFile.originalName}. Eliminare l'originale?"
                else -> "Convertito: ${newFile.originalName}"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            result = false to "Conversione annullata. Originale intatto."
            notifyResult("Conversione annullata", "File originale intatto")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "convert failed: $id", e)
            val msg = convertErrorText(e)
            result = false to "Conversione fallita: $msg. Originale intatto."
            notifyResult("Conversione fallita", msg)
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
        notify(build(label, 0, sub = "Preparazione…", indeterminate = true, cancelable = true, cancelMode = MODE_CANCEL_SCAN))
        var last = 0L
        val onProgress: (Int, Int) -> Unit = { done, total ->
            dupStore.progress(done, total)
            val now = SystemClock.elapsedRealtime()
            if (now - last > 400 || done == total) {
                last = now
                val pct = if (total > 0) done * 100 / total else 0
                notify(build(label, pct, sub = "$done di $total", indeterminate = total == 0, cancelable = true, cancelMode = MODE_CANCEL_SCAN))
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
                else "${groups.size} gruppi su $scanned elementi · tocca per confrontarli"
            notifyResult(title, text, ResultKind.SCAN, openDuplicates = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            dupStore.cancelled()
            notifyResult("$label annullata", null, ResultKind.SCAN)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "duplicate scan failed", e)
            val msg = UserErrors.of(e)
            dupStore.fail(msg)
            notifyResult("$label non riuscita", msg, ResultKind.SCAN)
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
            notify(build("Preparazione…", 0, indeterminate = true, cancelable = true))
            produced = withContext(Dispatchers.IO) {
                ytdlp.download(url, job.maxHeight, pid) { pct, eta ->
                    repo.setConversionProgress(pct)
                    set { it.copy(phase = VaultRepository.DownloadPhase.DOWNLOADING, pct = pct, etaSec = eta) }
                    val q = waiting().let { if (it > 0) " · $it in coda" else "" }
                    val sub = (if (eta > 0) "$pct% · ${etaText(eta * 1000)}" else "$pct%") + q
                    notify(build("Download in corso", pct, sub = sub, cancelable = true))
                }
            }
            if (cancelRequested) throw java.io.InterruptedIOException("cancelled")
            if (!isPlayableVideo(produced, null)) {
                val msg = "Nessun video valido (link errato o DRM)"
                set { it.copy(phase = VaultRepository.DownloadPhase.FAILED, message = msg) }
                notifyResult("Download fallito", msg, ResultKind.DOWNLOAD)
                return
            }
            val name = produced.name.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "download"
            val file = repo.importDownloadedMp4(produced, name, folderId = job.folderId, sourceUrl = url, tagIds = job.tagIds)
            set { it.copy(phase = VaultRepository.DownloadPhase.DONE, pct = 100, message = name, fileId = file.id) }
            notifyResult("Download completato", name, ResultKind.DOWNLOAD)
        } catch (e: kotlinx.coroutines.CancellationException) {
            set { it.copy(phase = VaultRepository.DownloadPhase.CANCELLED) }
            notifyResult("Download annullato", null, ResultKind.DOWNLOAD)
            throw e
        } catch (e: Exception) {
            // A yt-dlp process killed by the Cancel action surfaces as an ordinary exception, not a
            // coroutine cancellation, so distinguish it here. Otherwise surface the real cause.
            if (cancelRequested) {
                set { it.copy(phase = VaultRepository.DownloadPhase.CANCELLED) }
                notifyResult("Download annullato", null, ResultKind.DOWNLOAD)
            } else {
                android.util.Log.e("ConversionService", "download failed: $url", e)
                val msg = UserErrors.ofDownload(e)
                set { it.copy(phase = VaultRepository.DownloadPhase.FAILED, message = msg) }
                notifyResult("Download fallito", msg, ResultKind.DOWNLOAD)
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
            // "Chiedi": hand them to the app, which shows the keep/delete prompt in the vault.
            DeleteOriginalPolicy.ASK -> repo.addPendingOriginals(uris)
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

    private fun build(
        title: String,
        pct: Int,
        sub: String? = null,
        indeterminate: Boolean = false,
        cancelable: Boolean = false,
        cancelMode: String = MODE_CANCEL,
    ): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(sub)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            // Android 16 Live Update: ask for the ongoing progress to be promoted (status bar chip,
            // lock screen; HyperOS 3 shows it in the Hyper Island), with the percentage as the chip's
            // short text. Set by key: the constants are API 36 and the project compiles against 34;
            // older systems ignore them. The user can still turn it off per app.
            .addExtras(android.os.Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                if (!indeterminate) putCharSequence("android.shortCriticalText", "$pct%")
            })
        if (cancelable) {
            val cancelIntent = Intent(this, ConversionService::class.java).putExtra(EX_MODE, cancelMode)
            val pi = android.app.PendingIntent.getService(
                this, if (cancelMode == MODE_CANCEL) 1 else 4, cancelIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "Annulla", pi)
        }
        return b.build()
    }

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    /**
     * Post a terminal result (completed / failed / cancelled) as a dismissible notification under
     * its own id. Must NOT reuse [NOTIF_ID]: that is the ongoing foreground notification, which
     * onStartCommand's finally tears down with stopForeground(REMOVE) the instant the job ends —
     * a result posted there just flashes and disappears.
     */
    /** Which kind of operation a result belongs to; each keeps its own notification. */
    private enum class ResultKind(val id: Int) { IMPORT(4220), DOWNLOAD(4221), CONVERT(4222), SCAN(4223) }

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
     * [NOTIF_ID], the ongoing foreground notification that is removed when the job ends. File names
     * are hidden on the lock screen (public version without details).
     */
    private fun notifyResult(title: String, text: String?, kind: ResultKind = ResultKind.CONVERT, openDuplicates: Boolean = false) {
        val public = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle("Cripta")
            .setContentText(title)
            .build()
        val n = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(openDuplicates))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(kind.id, n)
    }

    /** Dismissible completion notification offering to delete or keep the original video. */
    private fun postConvertDone(originalId: String) {
        fun pi(mode: String, req: Int) = android.app.PendingIntent.getService(
            this, req,
            Intent(this, ConversionService::class.java).putExtra(EX_MODE, mode).putExtra(EX_ID, originalId),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle("Video convertito")
            .setContentText("Copia MP4 creata. Eliminare l'originale?")
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Elimina originale", pi(MODE_DELETE_ORIG, 2))
            .addAction(0, "Mantieni", pi(MODE_DISMISS, 3))
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_NOTIF_ID, n)
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
        private const val DONE_NOTIF_ID = 4212
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
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_DOWNLOAD_URL)
                putExtra(EX_URL, url)
                maxHeight?.let { putExtra(EX_HEIGHT, it) }
                folderId?.let { putExtra(EX_FOLDER, it) }
                if (tagIds.isNotEmpty()) putExtra(EX_TAGS, tagIds.toLongArray())
            }
            ContextCompat.startForegroundService(ctx, i)
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

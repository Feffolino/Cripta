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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Number of in-flight commands; the foreground notification is only torn down when it hits 0,
     *  so a short task (e.g. import) can't stop the service while a long transcode is still running. */
    private val active = java.util.concurrent.atomic.AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EX_MODE)
        if (mode == null) { stopIfIdle(startId); return START_NOT_STICKY }
        // Cancel request from the notification action: abort the running transcode/download and leave.
        if (mode == MODE_CANCEL) {
            cancelRequested = true
            ytdlpProcessId?.let { ytdlp.cancel(it) }
            // Only a running conversion is a cancellable coroutine; a download is cancelled by
            // killing its yt-dlp process above, so the rest of the download queue keeps going.
            if (currentDownloadId == null) convertJob?.cancel()
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        // Completion-notification actions: delete or keep the original video.
        if (mode == MODE_DELETE_ORIG) {
            val oid = intent.getStringExtra(EX_ID)
            getSystemService(NotificationManager::class.java).cancel(DONE_NOTIF_ID)
            scope.launch { oid?.let { runCatching { repo.deleteOrTrash(it) } } }
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        if (mode == MODE_DISMISS) {
            getSystemService(NotificationManager::class.java).cancel(DONE_NOTIF_ID)
            stopIfIdle(startId)
            return START_NOT_STICKY
        }
        ensureChannel()
        lastStartId = startId
        startForeground(NOTIF_ID, build("Preparazione…", 0, indeterminate = true))
        if (mode == MODE_DOWNLOAD_URL) {
            val url = intent.getStringExtra(EX_URL)
            if (url != null) {
                repo.enqueueDownload(
                    VaultRepository.DownloadJob(
                        id = java.util.UUID.randomUUID().toString(),
                        url = url,
                        maxHeight = intent.getIntExtra(EX_HEIGHT, 0).takeIf { it > 0 },
                        folderId = if (intent.hasExtra(EX_FOLDER)) intent.getLongExtra(EX_FOLDER, -1).takeIf { it >= 0 } else null,
                        tagIds = intent.getLongArrayExtra(EX_TAGS)?.toList().orEmpty(),
                    )
                )
            }
            // A worker already running picks the new link up from the queue.
            val start = synchronized(workerLock) { if (downloadWorkerRunning) false else { downloadWorkerRunning = true; true } }
            if (!start) return START_NOT_STICKY
        }
        active.incrementAndGet()

        val job = scope.launch {
            try {
                when (mode) {
                    MODE_IMPORT -> {
                        val uris = intent.getParcelableArrayListExtraCompat(EX_URIS)
                        val folderId = if (intent.hasExtra(EX_FOLDER)) intent.getLongExtra(EX_FOLDER, -1).takeIf { it >= 0 } else null
                        importBatch(uris, folderId)
                        applyDeletePolicy(uris)
                    }
                    MODE_DOWNLOAD -> {
                        val ids = intent.getStringArrayListExtra(EX_IDS) ?: arrayListOf()
                        run("Download", ids.size) { i -> repo.fileById(ids[i])?.let { repo.restoreToGallery(it) } }
                    }
                    MODE_CONVERT -> {
                        intent.getStringExtra(EX_ID)?.let { convertOne(it) }
                    }
                    MODE_DOWNLOAD_URL -> downloadWorker()
                }
            } catch (_: Exception) {
                // best-effort; individual items already guarded below
            } finally {
                if (active.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // The latest start id: stops only if no newer command arrived meanwhile.
                    stopSelf(lastStartId)
                }
            }
        }
        if (mode == MODE_CONVERT) convertJob = job
        return START_NOT_STICKY
    }

    @Volatile private var convertJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastStartId = 0
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
            runCatching { op(i) }
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
    private suspend fun importBatch(uris: List<Uri>, folderId: Long?) {
        if (uris.isEmpty()) return
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
                val ok = runCatching {
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
                }.onFailure { android.util.Log.e("ConversionService", "import failed: $name", it) }.isSuccess
                // Already in the vault? (byte-identical; only same-size files are even checked)
                imported?.let { f ->
                    runCatching { dupScanner.copiesOf(f) }.getOrNull()?.firstOrNull()?.let { existing ->
                        repo.importDuplicate(f.id, existing.originalName)
                    }
                }
                repo.importItemDone(ok)
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
                    notifyResult(title, text)
                }
            }
        }
    }

    /**
     * Transcode one video to MP4 and encrypt it into the vault. Decrypted plaintext lives only in
     * app-private cache and is shredded in a NonCancellable finally block, so it is never left on
     * disk even if the service is torn down mid-operation.
     */
    private suspend fun convertOne(id: String) {
        val file = repo.fileById(id) ?: return
        repo.setConverting(id, true)
        notify(build("Conversione in MP4", 0, sub = "Ricodifica in corso…", indeterminate = true))
        var src: java.io.File? = null
        var out: java.io.File? = null
        try {
            val suffix = file.originalName.substringAfterLast('.', "mpg")
            src = repo.decryptToTempFile(file, suffix)
            out = repo.newTempFile("mp4")
            // Transformer requires a Looper; the service main thread has one. Progress drives the
            // notification so the user sees percentage and can leave the app / lock the screen.
            repo.setConversionProgress(0)
            notify(build("Conversione in MP4", 0, sub = "0%", cancelable = true))
            withContext(Dispatchers.Main) {
                converter.toMp4(src!!, out!!) { pct ->
                    repo.setConversionProgress(pct)
                    notify(build("Conversione in MP4", pct, sub = "$pct%", cancelable = true))
                }
            }
            // Never import a broken transcode: a corrupt/truncated output that still got saved would
            // look like a valid file and could lead the user to delete the (good) original and lose
            // the media. Verify the result is a playable video of plausible duration first.
            if (!isPlayableVideo(out!!, file.durationMs)) {
                notifyResult("Conversione fallita", "File originale intatto")
                return
            }
            val newFile = repo.importConvertedMp4(file, out!!)
            thumbs.copyCustomCover(file.id, newFile.id)   // keep a cover the user picked
            repo.emitConvertResult(VaultRepository.ConversionEvent(id, newFile.id))
            postConvertDone(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            notifyResult("Conversione annullata", "File originale intatto")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "convert failed: $id", e)
            notifyResult("Conversione fallita", e.message ?: e.javaClass.simpleName)
        } finally {
            withContext(NonCancellable) {
                src?.let { repo.shredTempFile(it) }
                out?.let { repo.shredTempFile(it) }
                repo.setConverting(id, false)
            }
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
                notifyResult("Download fallito", msg)
                return
            }
            val name = produced.name.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "download"
            val file = repo.importDownloadedMp4(produced, name, folderId = job.folderId, sourceUrl = url, tagIds = job.tagIds)
            set { it.copy(phase = VaultRepository.DownloadPhase.DONE, pct = 100, message = name, fileId = file.id) }
            notifyResult("Download completato", name)
        } catch (e: kotlinx.coroutines.CancellationException) {
            set { it.copy(phase = VaultRepository.DownloadPhase.CANCELLED) }
            notifyResult("Download annullato", null)
            throw e
        } catch (e: Exception) {
            // A yt-dlp process killed by the Cancel action surfaces as an ordinary exception, not a
            // coroutine cancellation, so distinguish it here. Otherwise surface the real cause.
            if (cancelRequested) {
                set { it.copy(phase = VaultRepository.DownloadPhase.CANCELLED) }
                notifyResult("Download annullato", null)
            } else {
                android.util.Log.e("ConversionService", "download failed: $url", e)
                val msg = (e.message ?: e.javaClass.simpleName).take(200)
                set { it.copy(phase = VaultRepository.DownloadPhase.FAILED, message = msg) }
                notifyResult("Download fallito", msg)
            }
        } finally {
            ytdlpProcessId = null
            currentDownloadId = null
            withContext(NonCancellable) {
                produced?.let { repo.shredTempFile(it); it.parentFile?.deleteRecursively() }
            }
        }
    }

    private fun applyDeletePolicy(uris: List<Uri>) {
        val policy = runCatching { kotlinx.coroutines.runBlocking { settings.settingsOnce().deleteOriginalPolicy } }
            .getOrDefault(DeleteOriginalPolicy.NEVER)
        if (policy == DeleteOriginalPolicy.ALWAYS) runCatching {
            kotlinx.coroutines.runBlocking { repo.deleteOriginals(uris) }
        }
        // ASK is treated as keep in background (no UI available here).
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
                    NotificationChannel(CHANNEL, "Operazioni Cripta", NotificationManager.IMPORTANCE_LOW)
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
    ): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(sub)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
        if (cancelable) {
            val cancelIntent = Intent(this, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL)
            val pi = android.app.PendingIntent.getService(
                this, 1, cancelIntent,
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
    private fun notifyResult(title: String, text: String?) {
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(RESULT_NOTIF_ID, n)
    }

    /** Dismissible completion notification offering to delete or keep the original video. */
    private fun postConvertDone(originalId: String) {
        fun pi(mode: String, req: Int) = android.app.PendingIntent.getService(
            this, req,
            Intent(this, ConversionService::class.java).putExtra(EX_MODE, mode).putExtra(EX_ID, originalId),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Video convertito")
            .setContentText("Copia MP4 creata. Eliminare l'originale?")
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
        private const val MODE_IMPORT = "import"
        private const val MODE_DOWNLOAD = "download"
        private const val MODE_CONVERT = "convert"
        private const val MODE_CANCEL = "cancel"
        private const val MODE_DELETE_ORIG = "delete_orig"
        private const val MODE_DISMISS = "dismiss"
        private const val MODE_DOWNLOAD_URL = "download_url"
        private const val EX_URL = "url"
        private const val EX_HEIGHT = "height"
        private const val EX_TAGS = "tags"
        private const val DONE_NOTIF_ID = 4212
        private const val RESULT_NOTIF_ID = 4213

        fun startImport(ctx: Context, uris: List<Uri>, folderId: Long?) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_IMPORT)
                putParcelableArrayListExtra(EX_URIS, ArrayList(uris))
                folderId?.let { putExtra(EX_FOLDER, it) }
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

        /** Cancel the running transcode (service is already up while converting). */
        fun cancelConvert(ctx: Context) {
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

        fun startConvert(ctx: Context, id: String) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_CONVERT)
                putExtra(EX_ID, id)
            }
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}

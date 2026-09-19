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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Number of in-flight commands; the foreground notification is only torn down when it hits 0,
     *  so a short task (e.g. import) can't stop the service while a long transcode is still running. */
    private val active = java.util.concurrent.atomic.AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EX_MODE)
        if (mode == null) { stopSelf(startId); return START_NOT_STICKY }
        // Cancel request from the notification action: abort the running transcode and leave.
        if (mode == MODE_CANCEL) {
            convertJob?.cancel()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Completion-notification actions: delete or keep the original video.
        if (mode == MODE_DELETE_ORIG) {
            val oid = intent.getStringExtra(EX_ID)
            getSystemService(NotificationManager::class.java).cancel(DONE_NOTIF_ID)
            scope.launch { oid?.let { runCatching { repo.secureDelete(it) } } }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (mode == MODE_DISMISS) {
            getSystemService(NotificationManager::class.java).cancel(DONE_NOTIF_ID)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ensureChannel()
        startForeground(NOTIF_ID, build("Preparazione…", 0, indeterminate = true))
        active.incrementAndGet()

        val job = scope.launch {
            try {
                when (mode) {
                    MODE_IMPORT -> {
                        val uris = intent.getParcelableArrayListExtraCompat(EX_URIS)
                        val folderId = if (intent.hasExtra(EX_FOLDER)) intent.getLongExtra(EX_FOLDER, -1).takeIf { it >= 0 } else null
                        run("Cifratura", uris.size) { i -> repo.import(uris[i], folderId) }
                        applyDeletePolicy(uris)
                    }
                    MODE_DOWNLOAD -> {
                        val ids = intent.getStringArrayListExtra(EX_IDS) ?: arrayListOf()
                        run("Download", ids.size) { i -> repo.fileById(ids[i])?.let { repo.restoreToGallery(it) } }
                    }
                    MODE_CONVERT -> {
                        intent.getStringExtra(EX_ID)?.let { convertOne(it) }
                    }
                    MODE_DOWNLOAD_URL -> {
                        val url = intent.getStringExtra(EX_URL)
                        val h = intent.getIntExtra(EX_HEIGHT, 0).takeIf { it > 0 }
                        if (url != null) downloadUrl(url, h)
                    }
                }
            } catch (_: Exception) {
                // best-effort; individual items already guarded below
            } finally {
                if (active.decrementAndGet() == 0) stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        if (mode == MODE_CONVERT || mode == MODE_DOWNLOAD_URL) convertJob = job
        return START_NOT_STICKY
    }

    @Volatile private var convertJob: kotlinx.coroutines.Job? = null

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
                notify(build("Conversione fallita", 0, sub = "File originale intatto"))
                return
            }
            val newFile = repo.importConvertedMp4(file, out!!)
            repo.emitConvertResult(VaultRepository.ConversionEvent(id, newFile.id))
            postConvertDone(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            notify(build("Conversione annullata", 0, sub = "File originale intatto"))
            throw e
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

    /**
     * Download a remote video (direct link or HLS) to MP4 and encrypt it into the vault. Progress
     * and a Cancel action live in the notification; the source link is stored on the new file.
     */
    private suspend fun downloadUrl(url: String, maxHeight: Int?) {
        var out: java.io.File? = null
        try {
            repo.setConversionProgress(0)
            notify(build("Download in corso", 0, sub = "0%", cancelable = true))
            out = repo.newTempFile("mp4")
            withContext(Dispatchers.Main) {
                converter.downloadToMp4(url, out!!, maxHeight) { pct ->
                    repo.setConversionProgress(pct)
                    notify(build("Download in corso", pct, sub = "$pct%", cancelable = true))
                }
            }
            if (!isPlayableVideo(out!!, null)) {
                notify(build("Download fallito", 0, sub = "Nessun video valido"))
                return
            }
            val name = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
                ?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "download"
            repo.importDownloadedMp4(out!!, name, folderId = null, sourceUrl = url)
            notify(build("Download completato", 100, sub = name))
        } catch (e: kotlinx.coroutines.CancellationException) {
            notify(build("Download annullato", 0))
            throw e
        } finally {
            withContext(NonCancellable) { out?.let { repo.shredTempFile(it) } }
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
        private const val DONE_NOTIF_ID = 4212

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

        /** Start an in-app download of [url] to MP4, capped at [maxHeight]px (null = source quality). */
        fun startDownloadUrl(ctx: Context, url: String, maxHeight: Int?) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_DOWNLOAD_URL)
                putExtra(EX_URL, url)
                maxHeight?.let { putExtra(EX_HEIGHT, it) }
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

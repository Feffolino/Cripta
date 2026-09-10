package com.cripta.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.cripta.app.media.ThumbnailLoader
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
    @Inject lateinit var thumbs: ThumbnailLoader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Number of in-flight commands; the foreground notification is only torn down when it hits 0,
     *  so a short task (e.g. import) can't stop the service while a long transcode is still running. */
    private val active = java.util.concurrent.atomic.AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EX_MODE)
        if (mode == null) { stopSelf(startId); return START_NOT_STICKY }
        ensureChannel()
        startForeground(NOTIF_ID, build("Preparazione…", 0, indeterminate = true))
        active.incrementAndGet()

        scope.launch {
            try {
                when (mode) {
                    MODE_IMPORT -> {
                        val uris = intent.getParcelableArrayListExtraCompat(EX_URIS)
                        val folderId = if (intent.hasExtra(EX_FOLDER)) intent.getLongExtra(EX_FOLDER, -1).takeIf { it >= 0 } else null
                        run("Cifratura", uris.size) { i ->
                            val file = repo.import(uris[i], folderId)
                            // Generate the cover now so it's cached before the grid asks for it.
                            runCatching { thumbs.load(file) }
                        }
                        applyDeletePolicy(uris)
                    }
                    MODE_DOWNLOAD -> {
                        val ids = intent.getStringArrayListExtra(EX_IDS) ?: arrayListOf()
                        run("Download", ids.size) { i -> repo.fileById(ids[i])?.let { repo.restoreToGallery(it) } }
                    }
                    MODE_CONVERT -> {
                        intent.getStringExtra(EX_ID)?.let { convertOne(it) }
                    }
                }
            } catch (_: Exception) {
                // best-effort; individual items already guarded below
            } finally {
                if (active.decrementAndGet() == 0) stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

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
            // Transformer requires a Looper; the service main thread has one.
            withContext(Dispatchers.Main) { converter.toMp4(src!!, out!!) }
            val newFile = repo.importConvertedMp4(file, out!!)
            runCatching { thumbs.load(newFile) }   // warm the converted file's cover
            repo.emitConvertResult(VaultRepository.ConversionEvent(id, newFile.id))
        } finally {
            withContext(NonCancellable) {
                src?.let { repo.shredTempFile(it) }
                out?.let { repo.shredTempFile(it) }
                repo.setConverting(id, false)
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

    private fun build(title: String, pct: Int, sub: String? = null, indeterminate: Boolean = false): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(sub)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
            .build()

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
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

        fun startConvert(ctx: Context, id: String) {
            val i = Intent(ctx, ConversionService::class.java).apply {
                putExtra(EX_MODE, MODE_CONVERT)
                putExtra(EX_ID, id)
            }
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}

package com.cripta.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Manages the encrypted blob files on internal storage. */
@Singleton
class BlobStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }

    fun blob(uuid: String): File = File(dir, uuid)

    /**
     * Writes [file] and forces it to storage when closed. Without the sync, a power cut seconds
     * after an import could leave an empty blob behind a committed row, with the original already
     * deleted (or a note's only copy renamed over by an empty one).
     */
    fun durableOutput(file: File): OutputStream {
        val fos = FileOutputStream(file)
        return object : FilterOutputStream(fos) {
            private var closed = false
            override fun write(b: ByteArray, off: Int, len: Int) = fos.write(b, off, len)
            override fun close() {
                // Closed twice: by the encrypting stream around it, then by its own use { }.
                if (closed) return
                closed = true
                try { fos.flush(); fos.fd.sync() } finally { fos.close() }
            }
        }
    }

    /** Remove leftovers of a note save interrupted before its rename (see VaultRepository.updateNote). */
    fun sweepTemp() {
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { runCatching { it.delete() } }
    }

    /** Delete every blob. Used when resetting a vault whose key was permanently invalidated. */
    fun wipeAll() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** Best-effort overwrite then delete. Real guarantee is crypto-shredding the keyset. */
    fun shred(uuid: String) {
        val f = blob(uuid)
        if (f.exists()) {
            runCatching {
                val len = f.length()
                RandomAccessFile(f, "rw").use { raf ->
                    val rnd = SecureRandom()
                    val chunk = ByteArray(64 * 1024)
                    var written = 0L
                    while (written < len) {
                        rnd.nextBytes(chunk)
                        val n = minOf(chunk.size.toLong(), len - written).toInt()
                        raf.write(chunk, 0, n)
                        written += n
                    }
                    raf.fd.sync()
                }
            }
            f.delete()
        }
    }
}

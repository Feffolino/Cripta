package com.cripta.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
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

    /**
     * The blobs a restore is writing, one id a line, each written down before its blob: a process
     * killed before the restore's transaction ended left them in the vault with no row, taking up
     * space no screen could free. Kept beside the vault folder (never mistaken for a blob).
     */
    private val restoreJournal = File(context.filesDir, "restore-journal")

    /** Note [uuid] as about to be written by a restore (forced to storage before the blob is). */
    fun journalRestored(uuid: String) {
        FileOutputStream(restoreJournal, true).use { it.write("$uuid\n".toByteArray()); it.fd.sync() }
    }

    /** The ids a restore wrote down and did not clear (it was killed), oldest first. */
    fun restoreJournal(): List<String> =
        runCatching { if (restoreJournal.exists()) restoreJournal.readLines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList() }
            .getOrDefault(emptyList())

    /** The restore ended (kept or undone): nothing of it is left to check. */
    fun clearRestoreJournal() { runCatching { restoreJournal.delete() } }

    /** Remove leftovers of a note save interrupted before its rename (see VaultRepository.updateNote). */
    fun sweepTemp() {
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { runCatching { it.delete() } }
    }

    /**
     * Delete every blob. Used when resetting a vault whose key was permanently invalidated.
     * Returns false when some blob is still there.
     */
    fun wipeAll(): Boolean {
        var all = true
        dir.listFiles()?.forEach { if (!runCatching { it.delete() }.getOrDefault(false) && it.exists()) all = false }
        return all
    }

    /**
     * Delete a blob. It is ciphertext whose key was destroyed with its row (crypto-shredding): that
     * is the guarantee. Overwriting it first cost minutes per video (every byte from SecureRandom)
     * and bought nothing on flash storage, which writes the new bytes elsewhere anyway.
     */
    fun shred(uuid: String) {
        blob(uuid).delete()
    }
}

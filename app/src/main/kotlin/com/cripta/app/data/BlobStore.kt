package com.cripta.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

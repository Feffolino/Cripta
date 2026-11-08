package com.cripta.app.data

import android.app.Application
import com.cripta.app.R
import com.cripta.app.Strings
import com.cripta.app.str
import com.cripta.app.work.UserErrors
import com.cripta.crypto.DekManager
import com.cripta.crypto.FileCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// A restored file goes through the same path as in a real restore: archive bytes -> blob.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class RestoreLimitsTest {
    private lateinit var blobs: BlobStore
    private val dek = DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes())

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        Strings.init(app)
        blobs = BlobStore(app)
    }

    /** [size] zero bytes, deflated as the export does it, without holding them in memory. */
    private fun deflatedZeros(size: Long): ByteArray = ByteArrayOutputStream().also { out ->
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { z ->
            val chunk = ByteArray(1024 * 1024)
            var left = size
            while (left > 0) { val n = minOf(left, chunk.size.toLong()).toInt(); z.write(chunk, 0, n); left -= n }
        }
    }.toByteArray()

    private fun deflated(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(bytes) }
    }.toByteArray()

    /** Restores one archive entry ([stored] header, then [payload]) into a new blob. */
    private fun restore(uuid: String, wrapped: ByteArray, stored: Long, payload: ByteArray, quota: VaultRepository.RestoreQuota) =
        VaultRepository.restoreBlob(blobs, uuid, wrapped, dek,
            DataInputStream(ByteArrayInputStream(payload)), stored, quota)

    private fun plain(uuid: String, wrapped: ByteArray): ByteArray =
        FileCrypto.decryptingStream(wrapped, dek, uuid, blobs.blob(uuid)).use { it.readBytes() }

    @Test
    fun deflatedDocumentExpandingPastTheLimitIsRefusedAndLeavesNoBlob() {
        val bomb = deflatedZeros(VaultRepository.MAX_COMPRESSED_DOC + 1)
        assertTrue("a small stream: ${bomb.size} bytes", bomb.size < 1024 * 1024)
        val uuid = UUID.randomUUID().toString()
        val e = assertThrows(IOException::class.java) {
            restore(uuid, FileCrypto.createWrappedFileKeyset(dek), -(bomb.size.toLong()) - 1, bomb,
                VaultRepository.RestoreQuota(Long.MAX_VALUE))
        }
        assertEquals(str(R.string.file_corrupt), e.message)
        assertFalse(blobs.blob(uuid).exists())
    }

    @Test
    fun ordinaryDeflatedAndStoredFilesStillRestore() {
        val text = "# Note\n".repeat(5000).toByteArray()
        val z = deflated(text)
        val a = UUID.randomUUID().toString(); val ka = FileCrypto.createWrappedFileKeyset(dek)
        assertEquals(text.size.toLong(), restore(a, ka, -(z.size.toLong()) - 1, z, VaultRepository.RestoreQuota(Long.MAX_VALUE)))
        assertArrayEquals(text, plain(a, ka))

        // A document of exactly the largest deflated size is still accepted.
        val full = deflatedZeros(VaultRepository.MAX_COMPRESSED_DOC)
        val b = UUID.randomUUID().toString(); val kb = FileCrypto.createWrappedFileKeyset(dek)
        assertEquals(VaultRepository.MAX_COMPRESSED_DOC,
            restore(b, kb, -(full.size.toLong()) - 1, full, VaultRepository.RestoreQuota(Long.MAX_VALUE)))
        assertArrayEquals(ByteArray(VaultRepository.MAX_COMPRESSED_DOC.toInt()), plain(b, kb))
        blobs.shred(b)

        val media = ByteArray(300_000) { (it * 31).toByte() }
        val c = UUID.randomUUID().toString(); val kc = FileCrypto.createWrappedFileKeyset(dek)
        assertEquals(media.size.toLong(), restore(c, kc, media.size.toLong(), media, VaultRepository.RestoreQuota(Long.MAX_VALUE)))
        assertArrayEquals(media, plain(c, kc))
    }

    @Test
    fun restoreLargerThanTheFreeSpaceStopsAsNoSpaceAndLeavesNoBlob() {
        val quota = VaultRepository.RestoreQuota(500_000)
        val first = ByteArray(300_000)
        val a = UUID.randomUUID().toString(); val ka = FileCrypto.createWrappedFileKeyset(dek)
        val z = deflated(first)
        restore(a, ka, -(z.size.toLong()) - 1, z, quota)
        val b = UUID.randomUUID().toString()
        val e = assertThrows(IOException::class.java) {
            restore(b, FileCrypto.createWrappedFileKeyset(dek), first.size.toLong(), first, quota)
        }
        assertFalse(blobs.blob(b).exists())
        assertEquals(str(R.string.err_no_space), UserErrors.of(e))
    }

    @Test
    fun cancelledRestoreStopsInsideTheCopy() {
        val quota = VaultRepository.RestoreQuota(Long.MAX_VALUE, Job().apply { cancel() })
        val uuid = UUID.randomUUID().toString()
        assertThrows(CancellationException::class.java) {
            restore(uuid, FileCrypto.createWrappedFileKeyset(dek), 1000, ByteArray(1000), quota)
        }
        assertFalse(blobs.blob(uuid).exists())
    }
}

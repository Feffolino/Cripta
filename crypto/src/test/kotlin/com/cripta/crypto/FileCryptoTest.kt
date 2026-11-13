package com.cripta.crypto

import com.google.crypto.tink.Aead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.GeneralSecurityException

class FileCryptoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var dek: Aead

    @Before fun setUp() {
        TinkInit.ensureInitialized()
        dek = DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes())
    }

    private fun plaintext(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    private fun encrypt(plain: ByteArray, uuid: String, keyset: ByteArray = FileCrypto.createWrappedFileKeyset(dek)): Pair<ByteArray, File> {
        val blob = tmp.newFile()
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(keyset, dek, uuid, out).use { it.write(plain) }
        }
        return keyset to blob
    }

    private fun SeekableByteChannel.readFully(at: Long, len: Int): ByteArray {
        position(at)
        val buf = ByteBuffer.allocate(len)
        while (buf.hasRemaining()) { if (read(buf) <= 0) break }
        assertEquals("short read at $at", len, buf.position())
        return buf.array()
    }

    @Test
    fun encryptThenDecrypt_roundTripsAndWipesTheKeysetBytes() {
        lateinit var wrappingInput: ByteArray
        lateinit var unwrappedOutput: ByteArray
        val recordingDek = object : Aead {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
                assertTrue(plaintext.any { it != 0.toByte() })
                wrappingInput = plaintext
                return dek.encrypt(plaintext, associatedData)
            }

            override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray =
                dek.decrypt(ciphertext, associatedData).also { unwrappedOutput = it }
        }
        val uuid = "uuid-1"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(recordingDek)
        assertArrayEquals(ByteArray(wrappingInput.size), wrappingInput)
        val plain = plaintext(3 * MIB)
        val blob = tmp.newFile("blob")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, recordingDek, uuid, out).use { enc ->
                assertArrayEquals(ByteArray(unwrappedOutput.size), unwrappedOutput)
                enc.write(plain)
            }
        }
        val decrypted = ByteArrayOutputStream()
        FileCrypto.decryptingStream(wrappedKeyset, recordingDek, uuid, blob).use { dec ->
            assertArrayEquals(ByteArray(unwrappedOutput.size), unwrappedOutput)
            dec.copyTo(decrypted)
        }
        assertArrayEquals(plain, decrypted.toByteArray())
    }

    /**
     * The backup export writes the plaintext length taken from the seekable channel's size() and
     * rejects the file if the bytes read differ: size() must be exact for every length, including an
     * empty file and the lengths that end exactly on (or one byte around) a segment boundary.
     */
    @Test
    fun sizeAndContentAreExactAtEverySegmentBoundary() {
        val sizes = listOf(
            0, 1, FIRST_SEGMENT - 1, FIRST_SEGMENT, FIRST_SEGMENT + 1,
            FIRST_SEGMENT + SEGMENT - 1, FIRST_SEGMENT + SEGMENT, FIRST_SEGMENT + SEGMENT + 1,
            MIB - 1, MIB, MIB + 1, 3 * MIB,
        )
        for (size in sizes) {
            val plain = plaintext(size)
            val (keyset, blob) = encrypt(plain, "uuid-$size")
            val streamed = FileCrypto.decryptingStream(keyset, dek, "uuid-$size", blob).use { it.readBytes() }
            assertArrayEquals("stream, $size bytes", plain, streamed)
            FileCrypto.seekableDecryptingChannel(keyset, dek, "uuid-$size", blob).use { ch ->
                assertEquals("size(), $size bytes", size.toLong(), ch.size())
                assertArrayEquals("seekable, $size bytes", plain, ch.readFully(0, size))
                val end = ByteBuffer.allocate(1)
                assertEquals("end of $size bytes", -1, ch.read(end))
            }
        }
    }

    @Test
    fun seekableReadsCrossSegmentBoundariesAndMatchTheStream() {
        val plain = plaintext(3 * MIB)
        val (keyset, blob) = encrypt(plain, "uuid-seek")
        FileCrypto.seekableDecryptingChannel(keyset, dek, "uuid-seek", blob).use { ch ->
            for (at in listOf(FIRST_SEGMENT - 100, FIRST_SEGMENT + SEGMENT - 7, 2_000_000, 3 * MIB - 200)) {
                assertArrayEquals("at $at", plain.copyOfRange(at, at + 200), ch.readFully(at.toLong(), 200))
            }
            // Backwards after reading further on.
            assertArrayEquals(plain.copyOfRange(10, 20), ch.readFully(10, 10))
        }
    }

    @Test
    fun aChangedByteInTheMiddleSegmentIsRejectedThereOnly() {
        val plain = plaintext(3 * MIB)
        val (keyset, blob) = encrypt(plain, "uuid-tamper")
        RandomAccessFile(blob, "rw").use { raf ->
            raf.seek(MIB + MIB / 2L)
            val b = raf.read()
            raf.seek(MIB + MIB / 2L)
            raf.write(b xor 0x01)
        }
        try {
            FileCrypto.decryptingStream(keyset, dek, "uuid-tamper", blob).use { it.readBytes() }
            throw AssertionError("a tampered blob must not decrypt")
        } catch (expected: IOException) {
        }
        FileCrypto.seekableDecryptingChannel(keyset, dek, "uuid-tamper", blob).use { ch ->
            // The first segment is intact and still reads.
            assertArrayEquals(plain.copyOfRange(0, 100), ch.readFully(0, 100))
            try {
                ch.readFully(MIB + MIB / 2L, 100)
                throw AssertionError("the tampered segment must not decrypt")
            } catch (expected: IOException) {
            }
        }
    }

    @Test
    fun aBlobCutOnASegmentBoundaryIsRejected() {
        val plain = plaintext(3 * MIB)
        val (keyset, blob) = encrypt(plain, "uuid-cut")
        RandomAccessFile(blob, "rw").use { it.setLength(2L * MIB) }
        try {
            FileCrypto.decryptingStream(keyset, dek, "uuid-cut", blob).use { it.readBytes() }
            throw AssertionError("a truncated blob must not decrypt in full")
        } catch (expected: IOException) {
        }
        FileCrypto.seekableDecryptingChannel(keyset, dek, "uuid-cut", blob).use { ch ->
            try {
                ch.readFully(ch.size() - 10, 10)
                throw AssertionError("the last segment of a truncated blob must not decrypt")
            } catch (expected: IOException) {
            }
        }
    }

    @Test
    fun theFileKeysetIsSealedByTheDekUnderItsOwnAssociatedData() {
        val wrapped = FileCrypto.createWrappedFileKeyset(dek)
        // Pinned: every existing vault stores its file keysets under this associated data.
        dek.decrypt(wrapped, "cripta-file-keyset".toByteArray())
        try {
            dek.decrypt(wrapped, null)
            throw AssertionError("the keyset must not open without its associated data")
        } catch (expected: GeneralSecurityException) {
        }
    }

    @Test(expected = GeneralSecurityException::class)
    fun anotherDekCannotOpenTheFileKeyset() {
        val (keyset, blob) = encrypt("secret".toByteArray(), "uuid-dek")
        val otherDek = DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes())
        FileCrypto.decryptingStream(keyset, otherDek, "uuid-dek", blob).close()
    }

    @Test
    fun cryptoShred_makesBlobUndecryptable() {
        val (_, blob) = encrypt("secret".toByteArray(), "uuid-shred")
        try {
            FileCrypto.decryptingStream(FileCrypto.createWrappedFileKeyset(dek), dek, "uuid-shred", blob)
                .use { it.readBytes() }
            throw AssertionError("blob must not decrypt with a different keyset")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun decrypt_withWrongAad_fails() {
        val (keyset, blob) = encrypt("secret".toByteArray(), "uuid-aad")
        try {
            FileCrypto.decryptingStream(keyset, dek, "different-uuid", blob).use { it.readBytes() }
            throw AssertionError("wrong AAD must fail")
        } catch (expected: IOException) {
        }
        try {
            FileCrypto.seekableDecryptingChannel(keyset, dek, "different-uuid", blob).use { it.readFully(0, 6) }
            throw AssertionError("wrong AAD must fail on the seekable channel too")
        } catch (expected: IOException) {
        }
    }

    /** A blob too short to hold even a header: whatever Tink throws, the file is not left open. */
    @Test
    fun aRejectedBlobIsNotLeftOpen() {
        val (keyset, blob) = encrypt(plaintext(100), "uuid-short")
        RandomAccessFile(blob, "rw").use { it.setLength(3) }
        runCatching { FileCrypto.seekableDecryptingChannel(keyset, dek, "uuid-short", blob).use { it.readFully(0, 1) } }
        runCatching { FileCrypto.decryptingStream(keyset, dek, "uuid-short", blob).use { it.read() } }
        // On Windows an open handle blocks the delete; elsewhere this only checks the calls return.
        assertTrue(blob.delete())
    }

    private companion object {
        const val MIB = 1024 * 1024
        /** Plaintext bytes per segment: 1 MiB ciphertext minus the 16-byte tag. */
        const val SEGMENT = MIB - 16
        /** The first segment also holds the 40-byte header (1 + 32-byte salt + 7-byte nonce prefix). */
        const val FIRST_SEGMENT = SEGMENT - 40
    }
}

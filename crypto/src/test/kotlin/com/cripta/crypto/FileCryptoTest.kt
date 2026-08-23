package com.cripta.crypto

import com.google.crypto.tink.Aead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FileCryptoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var dek: Aead

    @Before fun setUp() {
        TinkInit.ensureInitialized()
        val kek = InMemoryKekProvider.generate()
        dek = DekManager.loadDekAead(DekManager.createWrappedDek(kek), kek)
    }

    private fun bigPlaintext(): ByteArray = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }

    @Test
    fun encryptThenDecrypt_roundTrips() {
        val uuid = "uuid-1"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val plain = bigPlaintext()
        val blob = tmp.newFile("blob")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { enc ->
                enc.write(plain)
            }
        }
        assertTrue(blob.length() > 0)
        val decrypted = ByteArrayOutputStream()
        FileCrypto.decryptingStream(wrappedKeyset, dek, uuid, blob).use { dec ->
            dec.copyTo(decrypted)
        }
        assertArrayEquals(plain, decrypted.toByteArray())
    }

    @Test
    fun seekableDecrypt_readsMiddleRange() {
        val uuid = "uuid-seek"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val plain = bigPlaintext()
        val blob = tmp.newFile("blob-seek")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { it.write(plain) }
        }
        val offset = 2_000_000L
        val len = 500
        val ch = FileCrypto.seekableDecryptingChannel(wrappedKeyset, dek, uuid, blob)
        ch.position(offset)
        val buf = ByteBuffer.allocate(len)
        while (buf.hasRemaining()) { if (ch.read(buf) <= 0) break }
        ch.close()
        val expected = plain.copyOfRange(offset.toInt(), offset.toInt() + len)
        assertArrayEquals(expected, buf.array())
    }

    @Test
    fun cryptoShred_makesBlobUndecryptable() {
        val uuid = "uuid-shred"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val blob = tmp.newFile("blob-shred")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { it.write("secret".toByteArray()) }
        }
        var failed = false
        try {
            FileCrypto.decryptingStream(FileCrypto.createWrappedFileKeyset(dek), dek, uuid, blob)
                .use { it.readBytes() }
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("blob must not decrypt with a different keyset", failed)
    }

    @Test
    fun decrypt_withWrongAad_fails() {
        val uuid = "uuid-aad"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val blob = tmp.newFile("blob-aad")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { it.write("secret".toByteArray()) }
        }
        var failed = false
        try {
            FileCrypto.decryptingStream(wrappedKeyset, dek, "different-uuid", blob).use { it.readBytes() }
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("wrong AAD must fail", failed)
        assertFalse(uuid == "different-uuid")
    }
}

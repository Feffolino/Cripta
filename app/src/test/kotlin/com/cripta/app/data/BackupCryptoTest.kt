package com.cripta.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {

    private fun archive(plain: ByteArray, pass: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, pass.toCharArray(), BackupCrypto.newHeader()).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun open(bytes: ByteArray, pass: String): ByteArray {
        val input = ByteArrayInputStream(bytes)
        val magic = ByteArray(BackupCrypto.MAGIC.size).also { java.io.DataInputStream(input).readFully(it) }
        assertArrayEquals(BackupCrypto.MAGIC, magic)
        return BackupCrypto.decryptingStream(input, pass.toCharArray()).use { it.readBytes() }
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); fail("expected a failure") } catch (e: Exception) { /* expected */ }
    }

    @Test
    fun roundTripAcrossSegments() {
        val plain = ByteArray(3 * (1 shl 20) + 12345) { (it * 31).toByte() }
        val bytes = archive(plain, "correct horse battery")
        assertArrayEquals(plain, open(bytes, "correct horse battery"))
        assertTrue(bytes.size - plain.size <= BackupCrypto.overhead(plain.size.toLong()))
    }

    @Test
    fun wrongPassphraseFails() {
        val bytes = archive(ByteArray(1000) { 7 }, "correct horse battery")
        assertFails { open(bytes, "wrong passphrase!") }
    }

    @Test
    fun truncationIsDetected() {
        val plain = ByteArray(2 * (1 shl 20) + 10) { 1 }
        val bytes = archive(plain, "correct horse battery")
        assertFails { open(bytes.copyOf(bytes.size - (1 shl 20)), "correct horse battery") }
    }

    @Test
    fun tamperedHeaderFails() {
        val bytes = archive(ByteArray(1000) { 3 }, "correct horse battery")
        // Flip a salt byte: the key changes and the header no longer matches the associated data.
        bytes[BackupCrypto.MAGIC.size + 4] = (bytes[BackupCrypto.MAGIC.size + 4].toInt() xor 1).toByte()
        assertFails { open(bytes, "correct horse battery") }
    }
}

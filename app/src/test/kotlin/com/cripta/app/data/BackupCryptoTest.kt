package com.cripta.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.random.Random
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

    private fun assertRejected(bytes: ByteArray, case: String) {
        val passphrase = "test passphrase".toCharArray()
        try {
            val raw = ByteArrayInputStream(bytes)
            val magic = ByteArray(BackupCrypto.MAGIC.size).also { DataInputStream(raw).readFully(it) }
            require(magic.contentEquals(BackupCrypto.MAGIC))
            BackupCrypto.decryptingStream(raw, passphrase).use { stream ->
                val buffer = ByteArray(256)
                while (stream.read(buffer) != -1) { /* Consume through the authentication tag. */ }
            }
            fail("Accepted corrupted backup: $case")
        } catch (_: IOException) {
            // Includes truncated streams and Tink authentication failures.
        } catch (_: GeneralSecurityException) {
            // Also handled by restore.
        } catch (_: IllegalArgumentException) {
            // Invalid header.
        }
    }

    @Test(timeout = 10_000)
    fun malformedArchiveIsRejectedThroughTheFinalTag() {
        val random = Random(90210)
        val header = BackupCrypto.Header(100_001, random.nextBytes(16))
        val plain = random.nextBytes(4096)
        val valid = ByteArrayOutputStream().also { out ->
            BackupCrypto.encryptingStream(out, "test passphrase".toCharArray(), header)
                .use { it.write(plain) }
        }.toByteArray()
        assertArrayEquals(plain, open(valid, "test passphrase"))

        val headerSize = header.bytes().size
        val cuts = (0..headerSize).toList() +
            List(8) { random.nextInt(headerSize + 1, valid.size) } + (valid.size - 1)
        cuts.distinct().forEach { end ->
            assertRejected(valid.copyOf(end), "truncated at $end")
        }

        // Not inside MAGIC: that is read and checked by the restore before BackupCrypto (and here by
        // the helper's require), so a flip there would not prove anything about BackupCrypto.
        val positions = listOf(
            BackupCrypto.MAGIC.size + 3, headerSize - 1,
            headerSize, valid.size / 2, valid.lastIndex,
        )
        positions.forEach { index ->
            val corrupted = valid.copyOf()
            corrupted[index] = (corrupted[index].toInt() xor 1).toByte()
            assertRejected(corrupted, "byte flipped at $index")
        }
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

    /**
     * A changed salt fails. Every header field also feeds the key derivation, so this cannot tell the
     * header-as-associated-data binding apart from a different key: it proves a changed header is refused.
     */
    @Test
    fun tamperedHeaderFails() {
        val bytes = archive(ByteArray(1000) { 3 }, "correct horse battery")
        bytes[BackupCrypto.MAGIC.size + 4] = (bytes[BackupCrypto.MAGIC.size + 4].toInt() xor 1).toByte()
        try {
            open(bytes, "correct horse battery")
            fail("a changed header must not decrypt")
        } catch (_: IOException) {
        }
    }
}

/** The header check speaks the app's texts: Robolectric, for its resources. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class, qualifiers = "en")
class BackupHeaderTest {
    @org.junit.Before
    fun texts() = com.cripta.app.Strings.init(org.robolectric.RuntimeEnvironment.getApplication())

    @Test
    fun anImpossibleIterationCountIsRefusedBeforeAnyKeyIsDerived() {
        for (iterations in listOf(0, 99_999, 10_000_001, -1)) {
            val raw = ByteArrayOutputStream().also { out ->
                java.io.DataOutputStream(out).apply { writeInt(iterations); write(ByteArray(16)); flush() }
            }.toByteArray()
            try {
                BackupCrypto.decryptingStream(ByteArrayInputStream(raw), "x".toCharArray())
                fail("$iterations iterations accepted")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}

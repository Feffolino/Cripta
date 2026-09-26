package com.cripta.app.data

import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * Encryption of a backup archive, format v4:
 *
 *     "CRIPTAB4" | iterations (int) | salt (16) | Tink streaming AEAD (AES-256-GCM-HKDF, 1 MB segments)
 *
 * The key comes from the passphrase (PBKDF2-HMAC-SHA256, [ITERATIONS]) and the header is bound to
 * the ciphertext as associated data. Up to v3 the whole archive was ONE AES-GCM message through
 * Cipher streams: Android's provider holds all of a GCM message in memory until the end, so a
 * vault of a few GB ran out of memory (and over 2 GB could not work at all), and a streaming
 * provider would have written unauthenticated data into the vault before the final tag. Here
 * every 1 MB segment is authenticated on its own before any of it is returned, and a truncated
 * archive is detected.
 */
internal object BackupCrypto {
    val MAGIC: ByteArray = "CRIPTAB4".toByteArray()

    /** PBKDF2 cost of new archives (OWASP 2023 for PBKDF2-HMAC-SHA256); older ones used 210 000. */
    const val ITERATIONS = 600_000
    private const val SALT_LEN = 16
    private const val KEY_LEN = 32
    private const val SEGMENT = 1 shl 20
    private const val HKDF = "HmacSha256"

    class Header(val iterations: Int, val salt: ByteArray) {
        /** The header's bytes, bound to the ciphertext (a changed header fails to decrypt). */
        fun bytes(): ByteArray = java.io.ByteArrayOutputStream().also { bos ->
            DataOutputStream(bos).apply { write(MAGIC); writeInt(iterations); write(salt); flush() }
        }.toByteArray()
    }

    fun newHeader(): Header = Header(ITERATIONS, ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) })

    /** Writes the header, then returns the stream to write the archive's plaintext into. Close it to finish. */
    fun encryptingStream(raw: OutputStream, passphrase: CharArray, header: Header): OutputStream {
        val h = header.bytes()
        raw.write(h)
        return aead(passphrase, header).newEncryptingStream(raw, h)
    }

    /**
     * The plaintext of an archive whose [MAGIC] was already read from [raw]. A wrong passphrase or
     * a damaged file fails on the first read.
     */
    fun decryptingStream(raw: InputStream, passphrase: CharArray): InputStream {
        val din = DataInputStream(raw)
        val iterations = din.readInt()
        require(iterations in 100_000..10_000_000) { "Formato non valido" }
        val salt = ByteArray(SALT_LEN).also { din.readFully(it) }
        val header = Header(iterations, salt)
        return aead(passphrase, header).newDecryptingStream(raw, header.bytes())
    }

    /** Overhead of the encryption on [plaintextBytes] (header and per-segment tags), for estimates. */
    fun overhead(plaintextBytes: Long): Long =
        MAGIC.size + 4L + SALT_LEN + 64L + (plaintextBytes / (SEGMENT - 16) + 1) * 16L

    private fun aead(passphrase: CharArray, header: Header): StreamingAead {
        val spec = javax.crypto.spec.PBEKeySpec(passphrase, header.salt, header.iterations, KEY_LEN * 8)
        val ikm = try {
            javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()   // the spec keeps its own copy of the passphrase
        }
        // The primitive keeps its own copy of the key material.
        return try { AesGcmHkdfStreaming(ikm, HKDF, KEY_LEN, SEGMENT, 0) } finally { java.util.Arrays.fill(ikm, 0) }
    }
}

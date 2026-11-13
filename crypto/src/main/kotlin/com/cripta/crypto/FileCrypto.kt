package com.cripta.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.util.Arrays

/**
 * Per-file encryption. Each file has its own StreamingAead keyset, serialized and
 * wrapped by the DEK Aead. AAD = blobUuid bytes. Crypto-shredding = destroying the
 * file's wrapped keyset (kept in the metadata DB); the blob is then unrecoverable.
 */
object FileCrypto {

    /** Create a new per-file keyset, wrapped by the DEK. Store the returned bytes in the DB. */
    fun createWrappedFileKeyset(dek: Aead): ByteArray {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(StreamingAeadKeyTemplates.AES256_GCM_HKDF_1MB)
        val serialized = serializeKeyset(handle)
        return try {
            dek.encrypt(serialized, KEYSET_AAD)
        } finally {
            Arrays.fill(serialized, 0)
        }
    }

    private fun keyset(wrappedKeyset: ByteArray, dek: Aead): KeysetHandle {
        val serialized = dek.decrypt(wrappedKeyset, KEYSET_AAD)
        return try {
            CleartextKeysetHandle.read(BinaryKeysetReader.withInputStream(ByteArrayInputStream(serialized)))
        } finally {
            Arrays.fill(serialized, 0)
        }
    }

    private fun streamingAead(wrappedKeyset: ByteArray, dek: Aead): StreamingAead =
        keyset(wrappedKeyset, dek).getPrimitive(StreamingAead::class.java)

    /**
     * The file's own key, not the keyset wrapper: the wrapper's seekable channel only picks its key
     * on the first read, and until then its size() throws ("Cannot determine size before first
     * read()-call"), so callers asking for the plaintext length up front never got it. A file keyset
     * holds this one key, so reading through it is the same decryption.
     */
    private fun seekableAead(wrappedKeyset: ByteArray, dek: Aead): StreamingAead {
        val handle = keyset(wrappedKeyset, dek)
        val key = handle.primary.key
        return if (handle.size() == 1 && key is AesGcmHkdfStreamingKey) AesGcmHkdfStreaming.create(key)
        else handle.getPrimitive(StreamingAead::class.java)
    }

    /** Encrypting stream writing ciphertext to [ciphertextOut]. Caller closes it. */
    fun encryptingStream(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, ciphertextOut: OutputStream
    ): OutputStream =
        streamingAead(wrappedKeyset, dek)
            .newEncryptingStream(ciphertextOut, blobUuid.toByteArray())

    /** Full decrypting stream over the ciphertext [blob]. Caller closes it. */
    fun decryptingStream(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, blob: File
    ): InputStream {
        val aead = streamingAead(wrappedKeyset, dek)
        val input = blob.inputStream()
        return closingOnFailure(input) { aead.newDecryptingStream(input, blobUuid.toByteArray()) }
    }

    /**
     * Seekable decrypting channel for random access (video playback/seek). Caller closes it.
     * Its size() is the exact plaintext length, worked out from the ciphertext layout without decrypting.
     */
    fun seekableDecryptingChannel(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, blob: File
    ): SeekableByteChannel {
        val aead = seekableAead(wrappedKeyset, dek)
        val input = SeekableInputByteChannel(blob)
        return closingOnFailure(input) {
            aead.newSeekableDecryptingChannel(input, blobUuid.toByteArray())
        }
    }

    /** The caller only gets [source] back inside the result: if building that fails, nobody else can close it. */
    private inline fun <T> closingOnFailure(source: java.io.Closeable, build: () -> T): T =
        try {
            build()
        } catch (t: Throwable) {
            runCatching { source.close() }
            throw t
        }

    private val KEYSET_AAD = "cripta-file-keyset".toByteArray()
}

package com.cripta.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel

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
        val serialized = ByteArrayOutputStream().use { out ->
            CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(out))
            out.toByteArray()
        }
        return dek.encrypt(serialized, KEYSET_AAD)
    }

    private fun streamingAead(wrappedKeyset: ByteArray, dek: Aead): StreamingAead {
        val serialized = dek.decrypt(wrappedKeyset, KEYSET_AAD)
        val handle = CleartextKeysetHandle.read(
            BinaryKeysetReader.withInputStream(ByteArrayInputStream(serialized))
        )
        return handle.getPrimitive(StreamingAead::class.java)
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
    ): InputStream =
        streamingAead(wrappedKeyset, dek)
            .newDecryptingStream(blob.inputStream(), blobUuid.toByteArray())

    /** Seekable decrypting channel for random access (video playback/seek). */
    fun seekableDecryptingChannel(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, blob: File
    ): SeekableByteChannel =
        streamingAead(wrappedKeyset, dek)
            .newSeekableDecryptingChannel(SeekableInputByteChannel(blob), blobUuid.toByteArray())

    /** Plain readable channel wrapper if needed by callers. */
    fun decryptingChannel(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, blob: File
    ): ReadableByteChannel = seekableDecryptingChannel(wrappedKeyset, dek, blobUuid, blob)

    private val KEYSET_AAD = "cripta-file-keyset".toByteArray()
}

package com.cripta.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadKeyTemplates
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Manages the Data Encryption Key: a Tink AEAD keyset serialized and wrapped by the KEK.
 * The DEK in turn wraps every per-file keyset.
 */
object DekManager {

    /** Generate a new DEK keyset and return it wrapped by the KEK. Call once at setup. */
    fun createWrappedDek(kek: KekProvider): ByteArray {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
        val serialized = ByteArrayOutputStream().use { out ->
            CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(out))
            out.toByteArray()
        }
        return kek.wrap(serialized)
    }

    /** Unwrap the stored DEK and return its Aead primitive. Call after unlock. */
    fun loadDekAead(wrappedDek: ByteArray, kek: KekProvider): Aead {
        TinkInit.ensureInitialized()
        val serialized = kek.unwrap(wrappedDek)
        return dekAeadFromBytes(serialized)
    }

    /**
     * Generate a fresh DEK keyset and return its serialized (cleartext) bytes.
     * The caller is responsible for wrapping these bytes (e.g. with a biometric-authorized
     * Keystore cipher). Used by the app layer where the KEK requires user authentication.
     */
    fun newDekKeysetBytes(): ByteArray {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
        return ByteArrayOutputStream().use { out ->
            CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(out))
            out.toByteArray()
        }
    }

    /** Rebuild the DEK Aead from previously unwrapped serialized keyset bytes. */
    fun dekAeadFromBytes(serialized: ByteArray): Aead {
        TinkInit.ensureInitialized()
        val handle = CleartextKeysetHandle.read(
            BinaryKeysetReader.withInputStream(ByteArrayInputStream(serialized))
        )
        return handle.getPrimitive(Aead::class.java)
    }
}

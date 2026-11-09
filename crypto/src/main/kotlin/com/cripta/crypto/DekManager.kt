package com.cripta.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadKeyTemplates
import java.io.ByteArrayInputStream
import java.util.Arrays

/**
 * Manages the Data Encryption Key: a Tink AEAD keyset serialized and wrapped by the KEK.
 * The DEK in turn wraps every per-file keyset.
 */
object DekManager {

    /** Generate a new DEK keyset and return it wrapped by the KEK. Call once at setup. */
    fun createWrappedDek(kek: KekProvider): ByteArray {
        val serialized = newDekKeysetBytes()
        return try {
            kek.wrap(serialized)
        } finally {
            Arrays.fill(serialized, 0)
        }
    }

    /** Unwrap the stored DEK and return its Aead primitive. Call after unlock. */
    fun loadDekAead(wrappedDek: ByteArray, kek: KekProvider): Aead {
        TinkInit.ensureInitialized()
        val serialized = kek.unwrap(wrappedDek)
        return try {
            dekAeadFromBytes(serialized)
        } finally {
            Arrays.fill(serialized, 0)
        }
    }

    /**
     * Generate a fresh DEK keyset and return its serialized (cleartext) bytes.
     * The caller must wipe these bytes after use, including when wrapping them (e.g. with a biometric-authorized
     * Keystore cipher). Used by the app layer where the KEK requires user authentication.
     */
    fun newDekKeysetBytes(): ByteArray {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
        return serializeKeyset(handle)
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

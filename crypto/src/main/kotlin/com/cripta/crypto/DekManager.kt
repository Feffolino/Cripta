package com.cripta.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadKeyTemplates
import java.io.ByteArrayInputStream

/**
 * The Data Encryption Key: a Tink AEAD keyset that wraps every per-file keyset and the DB key.
 * The app wraps its serialized bytes with a Keystore cipher authorized by the user (see KeyVault),
 * so this object only creates the keyset and rebuilds the primitive from its bytes.
 */
object DekManager {

    /**
     * Generate a fresh DEK keyset and return its serialized (cleartext) bytes.
     * The caller must wipe these bytes after use, including after wrapping them with the
     * biometric-authorized Keystore cipher.
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

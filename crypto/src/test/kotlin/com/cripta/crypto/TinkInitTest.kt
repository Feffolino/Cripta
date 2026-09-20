package com.cripta.crypto

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
import org.junit.Assert.assertNotNull
import org.junit.Test

class TinkInitTest {
    @Test
    fun ensureInitialized_allowsStreamingAeadPrimitive() {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(StreamingAeadKeyTemplates.AES256_GCM_HKDF_1MB)
        val primitive = handle.getPrimitive(StreamingAead::class.java)
        assertNotNull(primitive)
    }
}

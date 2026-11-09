package com.cripta.crypto

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeysetWriter
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.proto.Keyset

/** Serialize the same binary protobuf as BinaryKeysetWriter without an intermediate output buffer. */
internal fun serializeKeyset(handle: KeysetHandle): ByteArray {
    lateinit var bytes: ByteArray
    CleartextKeysetHandle.write(handle, object : KeysetWriter {
        override fun write(keyset: Keyset) {
            bytes = keyset.toByteArray()
        }

        override fun write(keyset: EncryptedKeyset) {
            error("Expected a cleartext keyset")
        }
    })
    return bytes
}

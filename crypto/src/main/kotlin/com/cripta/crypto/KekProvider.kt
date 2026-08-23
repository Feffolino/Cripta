package com.cripta.crypto

/**
 * Wraps/unwraps small key material (the serialized DEK keyset).
 * Real impl is Android Keystore + biometric; test impl is in-memory.
 */
interface KekProvider {
    fun wrap(plaintext: ByteArray): ByteArray
    fun unwrap(ciphertext: ByteArray): ByteArray
}

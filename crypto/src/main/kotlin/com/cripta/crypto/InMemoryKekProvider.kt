package com.cripta.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** JVM-only KEK for unit tests: AES-256-GCM with a random in-memory key. */
class InMemoryKekProvider private constructor(private val key: SecretKey) : KekProvider {

    override fun wrap(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    override fun unwrap(ciphertext: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, IV_LEN)
        val ct = ciphertext.copyOfRange(IV_LEN, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128

        fun generate(): InMemoryKekProvider {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return InMemoryKekProvider(SecretKeySpec(raw, "AES"))
        }
    }
}

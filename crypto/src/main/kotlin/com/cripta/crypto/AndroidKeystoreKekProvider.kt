package com.cripta.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * KEK backed by Android Keystore. In production the key requires user authentication,
 * so [encryptCipher]/[decryptCipher] must be unlocked via a BiometricPrompt CryptoObject
 * before wrap/unwrap. StrongBox is used when available.
 *
 * @param requireAuth false only for instrumented CI tests.
 */
class AndroidKeystoreKekProvider(
    private val requireAuth: Boolean = true,
    private val alias: String = DEFAULT_ALIAS,
) : KekProvider {

    fun keyExists(): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.containsAlias(alias)
    }

    fun ensureKey() {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) return
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(requireAuth)
            // Do NOT invalidate the KEK when biometrics are added/changed. This KEK is the root
            // of the whole key hierarchy (KEK -> DEK -> DB key + per-file keys); invalidating it
            // makes every encrypted file permanently unrecoverable the moment the user enrols a
            // new fingerprint/face. That is unacceptable for a personal vault. The key still
            // requires user authentication and the device credential (PIN/pattern/password) stays
            // a valid unlock path, which matches the app's threat model (casual snoopers).
            .setInvalidatedByBiometricEnrollment(false)
        try {
            builder.setIsStrongBoxBacked(true)
            buildKey(builder)
        } catch (e: android.security.keystore.StrongBoxUnavailableException) {
            builder.setIsStrongBoxBacked(false)
            buildKey(builder)
        }
    }

    fun deleteKey() {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    private fun buildKey(builder: KeyGenParameterSpec.Builder) {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(builder.build())
        kg.generateKey()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /** Cipher for wrapping; feed to BiometricPrompt.CryptoObject before [wrapWith]. */
    fun encryptCipher(): Cipher =
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }

    /** Cipher for unwrapping; the IV is the first 12 bytes of the wrapped blob. */
    fun decryptCipher(wrapped: ByteArray): Cipher {
        val iv = wrapped.copyOfRange(0, IV_LEN)
        return Cipher.getInstance(TRANSFORM)
            .apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv)) }
    }

    fun wrapWith(cipher: Cipher, plaintext: ByteArray): ByteArray =
        cipher.iv + cipher.doFinal(plaintext)

    fun unwrapWith(cipher: Cipher, wrapped: ByteArray): ByteArray =
        cipher.doFinal(wrapped.copyOfRange(IV_LEN, wrapped.size))

    // Convenience path for non-auth (test) keys only.
    override fun wrap(plaintext: ByteArray): ByteArray {
        check(!requireAuth) { "auth-required key must use encryptCipher()/wrapWith()" }
        ensureKey()
        return wrapWith(encryptCipher(), plaintext)
    }

    override fun unwrap(ciphertext: ByteArray): ByteArray {
        check(!requireAuth) { "auth-required key must use decryptCipher()/unwrapWith()" }
        return unwrapWith(decryptCipher(ciphertext), ciphertext)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "cripta_kek"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }
}

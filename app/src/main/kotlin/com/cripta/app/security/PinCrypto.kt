package com.cripta.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sealing with the app PIN.
 *
 * Two layers: AES-GCM under a key derived from the PIN (PBKDF2-HMAC-SHA256), then AES-GCM under a
 * Keystore key that needs no user authentication but never leaves the device's secure hardware
 * (the "pepper"). A PIN has few combinations: without the pepper, a copy of the app data could be
 * brute-forced offline in minutes. With it, every guess has to run on this phone, where
 * [KeyVault] limits the attempts.
 */
object PinCrypto {
    const val MIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 16
    const val MAX_PASSWORD_LENGTH = 64

    private const val ITERATIONS = 210_000
    private const val PEPPER_ALIAS = "cripta_pin_pepper_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun maxLength(kind: SecretKind): Int = if (kind == SecretKind.PIN) MAX_PIN_LENGTH else MAX_PASSWORD_LENGTH

    /** A PIN is 4-16 digits; a password 4-64 characters of any kind. */
    fun isValid(secret: CharArray, kind: SecretKind): Boolean =
        secret.size in MIN_LENGTH..maxLength(kind) && (kind == SecretKind.PASSWORD || secret.all { it in '0'..'9' })

    /** What may be typed so far (the length is checked on submit). */
    fun accepts(text: String, kind: SecretKind): Boolean =
        text.length <= maxLength(kind) && (kind == SecretKind.PASSWORD || text.all { it in '0'..'9' })

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    /** Seals [plaintext] under [pin]. Slow on purpose (key derivation): call it off the main thread. */
    fun seal(pin: CharArray, salt: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val k = derive(pin, salt)
        try {
            val inner = gcmEncrypt(SecretKeySpec(k, "AES"), plaintext, aad)
            return gcmEncrypt(pepperKey(), inner, aad)
        } finally {
            k.fill(0)
        }
    }

    /**
     * Opens a blob made by [seal]; null when the PIN is wrong. Other failures (a missing or broken
     * Keystore key) throw. Slow on purpose: call it off the main thread.
     */
    fun open(pin: CharArray, salt: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? {
        val inner = gcmDecrypt(pepperKey(create = false), sealed, aad)
        val k = derive(pin, salt)
        return try {
            gcmDecrypt(SecretKeySpec(k, "AES"), inner, aad)
        } catch (e: AEADBadTagException) {
            null
        } finally {
            k.fill(0)
            inner.fill(0)
        }
    }

    fun deletePepper() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(PEPPER_ALIAS)) ks.deleteEntry(PEPPER_ALIAS)
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun pepperKey(create: Boolean = true): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(PEPPER_ALIAS)) {
            check(create) { "PIN key missing" }
            val builder = KeyGenParameterSpec.Builder(
                PEPPER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            fun generate() = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(builder.build()) }.generateKey()
            try {
                builder.setIsStrongBoxBacked(true)
                generate()
            } catch (e: android.security.keystore.StrongBoxUnavailableException) {
                builder.setIsStrongBoxBacked(false)
                generate()
            }
        }
        return (ks.getEntry(PEPPER_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun gcmEncrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        if (key is SecretKeySpec) {
            val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        } else {
            c.init(Cipher.ENCRYPT_MODE, key) // Keystore keys pick their own IV
        }
        c.updateAAD(aad)
        return c.iv + c.doFinal(plaintext)
    }

    private fun gcmDecrypt(key: SecretKey, blob: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN))
        c.updateAAD(aad)
        return c.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }
}

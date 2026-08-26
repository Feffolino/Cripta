package com.cripta.app.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import com.cripta.app.data.BlobStore
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.crypto.AndroidKeystoreKekProvider
import com.cripta.crypto.DekManager
import com.google.crypto.tink.Aead
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the key hierarchy and unlock flow.
 *
 * KEK (Android Keystore, biometric-gated) wraps the DEK keyset bytes. The DEK (a Tink Aead)
 * wraps the SQLCipher database passphrase. One biometric authorization unlocks everything.
 *
 * The wrapped blobs are ciphertext and safe to store in plain SharedPreferences.
 */
@Singleton
class KeyVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val blobs: BlobStore,
) {
    private val kek = AndroidKeystoreKekProvider(requireAuth = true)
    private val prefs = context.getSharedPreferences("cripta_vault", Context.MODE_PRIVATE)

    val isInitialized: Boolean get() = prefs.contains(KEY_WRAPPED_DEK)

    // --- Cipher factories to feed BiometricPrompt.CryptoObject ---

    /** Encrypt cipher for first-time setup. Ensures the Keystore key exists. */
    fun cipherForSetup(): Cipher {
        kek.ensureKey()
        return kek.encryptCipher()
    }

    /** Decrypt cipher for unlock, initialized with the stored DEK blob's IV. */
    fun cipherForUnlock(): Cipher {
        val wrapped = loadBytes(KEY_WRAPPED_DEK) ?: error("Not initialized")
        return kek.decryptCipher(wrapped)
    }

    // --- Completion steps run AFTER biometric success with the authorized cipher ---

    /** First run: generate DEK + DB key, persist wrapped, open DB, activate session. */
    fun completeSetup(authorizedEncryptCipher: Cipher) {
        val dekBytes = DekManager.newDekKeysetBytes()
        val wrappedDek = kek.wrapWith(authorizedEncryptCipher, dekBytes)
        storeBytes(KEY_WRAPPED_DEK, wrappedDek)

        val dek = DekManager.dekAeadFromBytes(dekBytes)
        val dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrappedDbKey = dek.encrypt(dbKey, DB_KEY_AAD)
        storeBytes(KEY_WRAPPED_DBKEY, wrappedDbKey)

        val db = CriptaDatabase.open(context, dbKey)
        session.activate(dek, db)
    }

    /** Returning user: unwrap DEK, decrypt DB key, open DB, activate session. */
    fun completeUnlock(authorizedDecryptCipher: Cipher) {
        val wrappedDek = loadBytes(KEY_WRAPPED_DEK) ?: error("Not initialized")
        val dekBytes = kek.unwrapWith(authorizedDecryptCipher, wrappedDek)
        val dek = DekManager.dekAeadFromBytes(dekBytes)

        val wrappedDbKey = loadBytes(KEY_WRAPPED_DBKEY) ?: error("Missing DB key")
        val dbKey = dek.decrypt(wrappedDbKey, DB_KEY_AAD)

        val db = CriptaDatabase.open(context, dbKey)
        session.activate(dek, db)
    }

    fun lock() = session.lock()

    /**
     * True when [throwable] (or any cause in its chain) is a permanently invalidated Keystore key.
     * This happens on older installs whose KEK still carried `setInvalidatedByBiometricEnrollment`,
     * or if the device lock was removed entirely. The wrapped DEK can then never be unwrapped, so
     * the ciphertext behind it is unrecoverable and the only way forward is [resetVault].
     */
    fun isKeyInvalidated(throwable: Throwable): Boolean {
        var e: Throwable? = throwable
        while (e != null) {
            if (e is KeyPermanentlyInvalidatedException) return true
            e = e.cause
        }
        return false
    }

    /**
     * Wipe the vault so the app can start over after the KEK became unusable. Everything protected
     * by the lost key is already cryptographically unrecoverable; this just clears the useless
     * ciphertext (Keystore key, wrapped blobs, encrypted DB, file blobs) and returns the app to a
     * clean first-run state. Destructive and irreversible.
     */
    fun resetVault() {
        session.lock()
        runCatching { kek.deleteKey() }
        prefs.edit().clear().apply()
        runCatching { CriptaDatabase.deleteDatabase(context) }
        runCatching { blobs.wipeAll() }
    }

    private fun storeBytes(key: String, value: ByteArray) {
        prefs.edit().putString(key, Base64.encodeToString(value, Base64.NO_WRAP)).apply()
    }

    private fun loadBytes(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    companion object {
        private const val KEY_WRAPPED_DEK = "wrapped_dek"
        private const val KEY_WRAPPED_DBKEY = "wrapped_dbkey"
        private val DB_KEY_AAD = "cripta-db-key".toByteArray()
    }
}

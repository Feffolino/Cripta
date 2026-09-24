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
 *
 * With an app PIN ([UnlockMode]) the DEK is also, or instead, sealed by [PinCrypto]:
 *  - SYSTEM:          wrapped_dek = KEK(dek)
 *  - SYSTEM_OR_PIN:   wrapped_dek = KEK(dek), wrapped_dek_pin = PIN(dek)   (either opens it)
 *  - SYSTEM_AND_PIN:  wrapped_dek = KEK(PIN(dek))                         (both are needed)
 *  - PIN:             wrapped_dek_pin = PIN(dek)
 */
@Singleton
class KeyVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val blobs: BlobStore,
) {
    private val prefs = context.getSharedPreferences("cripta_vault", Context.MODE_PRIVATE)

    /**
     * The Keystore alias currently protecting the DEK. New/migrated vaults use [ALIAS_V2] (created
     * with `setInvalidatedByBiometricEnrollment(false)`); pre-fix installs still carry their key
     * under the legacy alias and are migrated on first unlock (see [needsKekMigration]).
     */
    private val activeAlias: String = when {
        prefs.contains(KEY_KEK_ALIAS) -> prefs.getString(KEY_KEK_ALIAS, ALIAS_V2)!!
        prefs.contains(KEY_WRAPPED_DEK) -> LEGACY_ALIAS   // existing pre-fix install
        else -> ALIAS_V2                                   // fresh install
    }
    private val kek = AndroidKeystoreKekProvider(requireAuth = true, alias = activeAlias)

    val isInitialized: Boolean get() = prefs.contains(KEY_WRAPPED_DEK) || prefs.contains(KEY_WRAPPED_DEK_PIN)

    val unlockMode: UnlockMode get() = UnlockMode.from(prefs.getString(KEY_UNLOCK_MODE, null))

    /** Whether the app code is a numeric PIN or a password (meaningful when [unlockMode] uses one). */
    val secretKind: SecretKind get() = SecretKind.from(prefs.getString(KEY_SECRET_KIND, null))

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
        // Record the alias so this fresh vault is never treated as a pre-fix install.
        prefs.edit().putString(KEY_KEK_ALIAS, activeAlias).apply()

        val dek = DekManager.dekAeadFromBytes(dekBytes)
        val dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrappedDbKey = dek.encrypt(dbKey, DB_KEY_AAD)
        storeBytes(KEY_WRAPPED_DBKEY, wrappedDbKey)

        val db = CriptaDatabase.open(context, dbKey)
        session.activate(dek, db, dekBytes)
        dekBytes.fill(0)
    }

    /**
     * Returning user: unwrap DEK, decrypt DB key, open DB, activate session. Not for
     * [UnlockMode.SYSTEM_AND_PIN], where the prompt only yields [unwrapSystemLayer]'s blob.
     */
    fun completeUnlock(authorizedDecryptCipher: Cipher) {
        val dekBytes = unwrapSystemLayer(authorizedDecryptCipher)
        try { openWith(dekBytes) } finally { dekBytes.fill(0) }

        // Record the alias marker for pre-marker installs so this only happens once. We do NOT
        // re-wrap under a new key here: a proactive rewrap would need a second BiometricPrompt,
        // which fails back-to-back on many devices (double prompt every unlock). Keys created by
        // the biometric-fix build onward already carry setInvalidatedByBiometricEnrollment(false);
        // a genuinely invalidated pre-fix key is still caught reactively by isKeyInvalidated ->
        // KeyInvalidatedDialog.
        if (!prefs.contains(KEY_KEK_ALIAS)) {
            prefs.edit().putString(KEY_KEK_ALIAS, activeAlias).apply()
        }
    }

    /** What the system prompt unwraps: the DEK, or (SYSTEM_AND_PIN) the PIN-sealed DEK. */
    fun unwrapSystemLayer(authorizedDecryptCipher: Cipher): ByteArray {
        val wrappedDek = loadBytes(KEY_WRAPPED_DEK) ?: error("Not initialized")
        return kek.unwrapWith(authorizedDecryptCipher, wrappedDek)
    }

    private fun openWith(dekBytes: ByteArray) {
        val dek = DekManager.dekAeadFromBytes(dekBytes)
        val wrappedDbKey = loadBytes(KEY_WRAPPED_DBKEY) ?: error("Missing DB key")
        val dbKey = dek.decrypt(wrappedDbKey, DB_KEY_AAD)
        try {
            val db = CriptaDatabase.open(context, dbKey)
            session.activate(dek, db, dekBytes)
        } finally {
            dbKey.fill(0)
        }
    }

    // --- App PIN ---

    sealed interface PinResult {
        data object Ok : PinResult
        /** Wrong PIN; [waitSeconds] > 0 when this failure started a pause before the next try. */
        data class Wrong(val waitSeconds: Long) : PinResult
        /** Too many wrong PINs: no attempt is made until the pause ends. */
        data class Wait(val seconds: Long) : PinResult
    }

    /** Seconds left before another PIN attempt is allowed (0 = now). */
    fun pinWaitSeconds(): Long {
        val left = prefs.getLong(KEY_PIN_WAIT_UNTIL, 0L) - System.currentTimeMillis()
        return if (left <= 0L) 0L else (left + 999L) / 1000L
    }

    /**
     * Unlocks with the app PIN. [systemLayer] is the blob from [unwrapSystemLayer] in
     * SYSTEM_AND_PIN mode, null otherwise. Slow (key derivation): call it off the main thread.
     */
    fun unlockWithPin(pin: CharArray, systemLayer: ByteArray?): PinResult {
        val sealed = if (unlockMode == UnlockMode.SYSTEM_AND_PIN) systemLayer ?: error("System step missing")
            else loadBytes(KEY_WRAPPED_DEK_PIN) ?: error("No PIN set")
        val dekBytes = when (val r = checkPin(pin, sealed)) {
            is PinCheck.Opened -> r.bytes
            is PinCheck.Refused -> return r.result
        }
        try { openWith(dekBytes) } finally { dekBytes.fill(0) }
        return PinResult.Ok
    }

    /** Checks the current PIN (to confirm a settings change in PIN-only mode), counting failures. */
    fun verifyPin(pin: CharArray): PinResult {
        val sealed = loadBytes(KEY_WRAPPED_DEK_PIN) ?: error("No PIN set")
        return when (val r = checkPin(pin, sealed)) {
            is PinCheck.Opened -> { r.bytes.fill(0); PinResult.Ok }
            is PinCheck.Refused -> r.result
        }
    }

    private sealed interface PinCheck {
        class Opened(val bytes: ByteArray) : PinCheck
        class Refused(val result: PinResult) : PinCheck
    }

    private fun checkPin(pin: CharArray, sealed: ByteArray): PinCheck {
        pinWaitSeconds().takeIf { it > 0 }?.let { return PinCheck.Refused(PinResult.Wait(it)) }
        val salt = loadBytes(KEY_PIN_SALT) ?: error("No PIN salt")
        val opened = PinCrypto.open(pin, salt, sealed, PIN_AAD)
        if (opened == null) {
            // A pause grows with repeated failures (5th wrong PIN on): on this phone, with the
            // hardware-bound pepper, that is what keeps a short PIN from being guessed.
            val fails = prefs.getInt(KEY_PIN_FAILS, 0) + 1
            val wait = when {
                fails < 5 -> 0L
                fails == 5 -> 30L
                fails == 6 -> 60L
                fails == 7 -> 5 * 60L
                else -> 15 * 60L
            }
            prefs.edit().putInt(KEY_PIN_FAILS, fails)
                .putLong(KEY_PIN_WAIT_UNTIL, if (wait > 0) System.currentTimeMillis() + wait * 1000L else 0L)
                .commit()
            return PinCheck.Refused(PinResult.Wrong(wait))
        }
        prefs.edit().remove(KEY_PIN_FAILS).remove(KEY_PIN_WAIT_UNTIL).commit()
        return PinCheck.Opened(opened)
    }

    // --- Changing the unlock mode (vault unlocked) ---

    /** Encrypt cipher to authorize with the system prompt before [changeMode] (and to confirm it). */
    fun cipherForModeChange(): Cipher {
        kek.ensureKey()
        return kek.encryptCipher()
    }

    /**
     * Re-wraps the DEK for [newMode]. [newPin] is required when the mode uses a PIN, and
     * [authorizedEncryptCipher] (from [cipherForModeChange], authorized by the prompt) when it
     * uses the system prompt. Needs the vault unlocked. Slow: call it off the main thread.
     */
    fun changeMode(newMode: UnlockMode, newPin: CharArray?, kind: SecretKind, authorizedEncryptCipher: Cipher?) {
        val dekBytes = session.dekBytesCopy() ?: error("Vault locked")
        try {
            val salt = PinCrypto.newSalt()
            val pinSealed = if (newMode.usesPin) {
                require(newPin != null && PinCrypto.isValid(newPin, kind)) { "Invalid PIN" }
                PinCrypto.seal(newPin, salt, dekBytes, PIN_AAD)
            } else null
            val kekBlob = when (newMode) {
                UnlockMode.SYSTEM, UnlockMode.SYSTEM_OR_PIN -> kek.wrapWith(requireNotNull(authorizedEncryptCipher), dekBytes)
                UnlockMode.SYSTEM_AND_PIN -> kek.wrapWith(requireNotNull(authorizedEncryptCipher), pinSealed!!)
                UnlockMode.PIN -> null
            }
            val e = prefs.edit()
            if (kekBlob != null) e.putString(KEY_WRAPPED_DEK, encode(kekBlob)) else e.remove(KEY_WRAPPED_DEK)
            if (newMode == UnlockMode.SYSTEM_OR_PIN || newMode == UnlockMode.PIN) e.putString(KEY_WRAPPED_DEK_PIN, encode(pinSealed!!))
            else e.remove(KEY_WRAPPED_DEK_PIN)
            if (newMode.usesPin) e.putString(KEY_PIN_SALT, encode(salt)).putString(KEY_SECRET_KIND, kind.name)
            else e.remove(KEY_PIN_SALT).remove(KEY_SECRET_KIND)
            e.putString(KEY_UNLOCK_MODE, newMode.name)
                .putString(KEY_KEK_ALIAS, activeAlias)
                .remove(KEY_PIN_FAILS).remove(KEY_PIN_WAIT_UNTIL)
            // One synchronous write: the old and new wrapping never end up mixed on disk.
            check(e.commit()) { "Could not save the unlock mode" }
        } finally {
            dekBytes.fill(0)
        }
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
     * clean first-run state. Destructive and irreversible. Blocking (closes and deletes the
     * database): call it off the main thread.
     */
    fun resetVault() {
        // Close synchronously: the database file is deleted right below.
        session.lockAndCloseNow()
        // Drop both possible keys (legacy + safe) so no orphan alias survives the reset.
        runCatching { kek.deleteKey() }
        runCatching { AndroidKeystoreKekProvider(requireAuth = true, alias = LEGACY_ALIAS).deleteKey() }
        runCatching { AndroidKeystoreKekProvider(requireAuth = true, alias = ALIAS_V2).deleteKey() }
        runCatching { PinCrypto.deletePepper() }
        prefs.edit().clear().apply()
        runCatching { CriptaDatabase.deleteDatabase(context) }
        runCatching { blobs.wipeAll() }
    }

    private fun storeBytes(key: String, value: ByteArray) {
        prefs.edit().putString(key, encode(value)).apply()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun loadBytes(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    companion object {
        private const val KEY_WRAPPED_DEK = "wrapped_dek"
        private const val KEY_WRAPPED_DBKEY = "wrapped_dbkey"
        private const val KEY_KEK_ALIAS = "kek_alias"
        private const val KEY_WRAPPED_DEK_PIN = "wrapped_dek_pin"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_UNLOCK_MODE = "unlock_mode"
        private const val KEY_SECRET_KIND = "secret_kind"
        private const val KEY_PIN_FAILS = "pin_fails"
        private const val KEY_PIN_WAIT_UNTIL = "pin_wait_until"
        private val PIN_AAD = "cripta-dek-pin".toByteArray()
        private const val LEGACY_ALIAS = "cripta_kek"
        private const val ALIAS_V2 = "cripta_kek_v2"
        private val DB_KEY_AAD = "cripta-db-key".toByteArray()
    }
}

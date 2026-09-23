package com.cripta.app.security

import com.cripta.app.data.db.CriptaDatabase
import com.google.crypto.tink.Aead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the unlocked session state in memory only: the DEK (Aead) and the open
 * encrypted database. Cleared on lock/background so nothing sensitive survives in RAM.
 *
 * Locking is two separate things:
 *  - the UI lock ([locked]) flips immediately, so the vault disappears behind the auth screen;
 *  - the keys are released right away too, EXCEPT while background work registered with
 *    [beginWork] (import, conversion, download into the vault) is still running. Those jobs need
 *    the DEK to finish encrypting what the user already handed over; dropping it under them used
 *    to make every remaining item fail silently. The keys are then released the moment the last
 *    job ends ([endWork]), unless the user unlocked again in the meantime.
 */
@Singleton
class SessionManager @Inject constructor() {

    @Volatile private var dek: Aead? = null
    @Volatile private var db: CriptaDatabase? = null

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * The database the UI may observe: null while locked (even when the keys are still retained
     * for background work), a fresh instance after every unlock. Repository flows switch on this,
     * so screens kept composed across a lock never query a closed database and re-query the new
     * one after unlocking.
     */
    private val _database = MutableStateFlow<CriptaDatabase?>(null)
    val database: StateFlow<CriptaDatabase?> = _database.asStateFlow()

    /** Closing SQLCipher can block for a while (checkpoint + key wipe): never on the main thread. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    /** Background jobs currently relying on the keys (see [beginWork]). */
    private var workCount = 0
    /** True when a lock happened while work was running: release the keys when it ends. */
    private var releasePending = false

    /** True when the vault is unlocked for the user (keys present AND not UI-locked). */
    val isUnlocked: Boolean get() = !_locked.value && dek != null && db != null

    fun activate(dek: Aead, db: CriptaDatabase) {
        val stale: CriptaDatabase? = synchronized(lock) {
            val retained = this.db
            val drop = if (retained != null && releasePending) {
                // Unlocked again while background work kept the previous session alive: keep using
                // it (same keyset, same database file) instead of opening a second Room instance
                // over the same file, and drop the freshly opened one.
                releasePending = false
                if (retained !== db) db else null
            } else {
                this.dek = dek
                this.db = db
                if (retained != null && retained !== db) retained else null
            }
            _database.value = this.db
            _locked.value = false
            drop
        }
        stale?.let { closeAsync(it) }
    }

    /**
     * Lock now. Never blocks: the UI lock flips immediately and the database is closed on an IO
     * thread. While background work holds the session the keys are kept until it finishes.
     */
    fun lock() {
        val toClose: CriptaDatabase? = synchronized(lock) {
            _locked.value = true
            _database.value = null
            if (workCount > 0) {
                releasePending = true
                null
            } else {
                releaseKeysLocked()
            }
        }
        toClose?.let { closeAsync(it) }
    }

    /**
     * Lock and close the database synchronously, ignoring running work. Only for destructive
     * paths (vault reset) that delete the database file right after; call it off the main thread.
     */
    fun lockAndCloseNow() {
        val toClose: CriptaDatabase? = synchronized(lock) {
            _locked.value = true
            _database.value = null
            releaseKeysLocked()
        }
        toClose?.let { runCatching { it.close() }; it.wipeKey() }
    }

    /**
     * A background job that needs the keys is starting. Pair every call with [endWork] (in a
     * finally block). Returns false when the vault is fully locked (no keys to work with).
     */
    fun beginWork(): Boolean = synchronized(lock) {
        if (dek == null || db == null) false
        else { workCount++; true }
    }

    /** A job registered with [beginWork] ended; releases the keys if a lock was waiting for it. */
    fun endWork() {
        val toClose: CriptaDatabase? = synchronized(lock) {
            workCount = (workCount - 1).coerceAtLeast(0)
            if (workCount == 0 && releasePending) releaseKeysLocked() else null
        }
        toClose?.let { closeAsync(it) }
    }

    /** True while background work keeps the session alive. */
    val hasActiveWork: Boolean get() = synchronized(lock) { workCount > 0 }

    private fun releaseKeysLocked(): CriptaDatabase? {
        val old = db
        db = null
        dek = null
        releasePending = false
        return old
    }

    private fun closeAsync(d: CriptaDatabase) {
        ioScope.launch {
            // Screens stop their Room Flows when the lock shows, and view models keep upstreams
            // for 5 s (WhileSubscribed(5000)): closing under them made Room reopen the database
            // while cancelling. Close once they are gone; the key goes a while after that.
            kotlinx.coroutines.delay(CLOSE_DELAY_MS)
            runCatching { d.close() }
            kotlinx.coroutines.delay(WIPE_DELAY_MS)
            d.wipeKey()
        }
    }

    private companion object {
        const val CLOSE_DELAY_MS = 6_000L
        const val WIPE_DELAY_MS = 30_000L
    }

    fun requireDek(): Aead = dek ?: error("Vault locked")
    fun requireDb(): CriptaDatabase = db ?: error("Vault locked")
}

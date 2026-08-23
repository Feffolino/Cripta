package com.cripta.app.security

import com.cripta.app.data.db.CriptaDatabase
import com.google.crypto.tink.Aead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the unlocked session state in memory only: the DEK (Aead) and the open
 * encrypted database. Cleared on lock/background so nothing sensitive survives in RAM.
 */
@Singleton
class SessionManager @Inject constructor() {

    @Volatile private var dek: Aead? = null
    @Volatile private var db: CriptaDatabase? = null

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    val isUnlocked: Boolean get() = dek != null && db != null

    fun activate(dek: Aead, db: CriptaDatabase) {
        this.dek = dek
        this.db = db
        _locked.value = false
    }

    fun lock() {
        db?.close()
        db = null
        dek = null
        _locked.value = true
    }

    fun requireDek(): Aead = dek ?: error("Vault locked")
    fun requireDb(): CriptaDatabase = db ?: error("Vault locked")
}

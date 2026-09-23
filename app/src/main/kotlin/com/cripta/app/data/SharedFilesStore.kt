package com.cripta.app.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files shared into Cripta from another app (SEND / SEND_MULTIPLE with EXTRA_STREAM), held until
 * the vault is unlocked: encryption needs the keys, but the share must not be thrown away just
 * because the vault was locked. AppRoot starts the import once unlocked and clears the list.
 */
@Singleton
class SharedFilesStore @Inject constructor() {
    private val _pending = MutableStateFlow<List<Uri>>(emptyList())
    val pending: StateFlow<List<Uri>> = _pending

    fun add(uris: List<Uri>) = _pending.update { (it + uris).distinct() }

    /** Take the pending uris (and clear them), or an empty list. */
    fun take(): List<Uri> {
        val v = _pending.value
        _pending.value = emptyList()
        return v
    }
}

package com.cripta.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a link shared into the app (via the SEND intent) until the Download screen picks it up.
 * Lets a share open the downloader pre-filled — so the user can choose a resolution — instead of
 * starting the download blind.
 */
@Singleton
class SharedLinkStore @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun set(url: String) { _pending.value = url }
    fun consume() { _pending.value = null }
}

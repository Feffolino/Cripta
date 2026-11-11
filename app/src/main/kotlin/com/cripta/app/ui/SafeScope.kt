package com.cripta.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.logException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

/**
 * [viewModelScope] for the screens' single actions (a star, a rename, a new folder or tag): one
 * that fails, because the vault was locked between the tap and the database ("Vault locked") or
 * a write was refused (a full disk), is logged and the action is simply not done. Launched on
 * viewModelScope itself, the error reached the uncaught handler and closed the app.
 */
internal val ViewModel.safeScope: CoroutineScope
    get() = viewModelScope + CoroutineExceptionHandler { _, e ->
        logException(android.util.Log.ERROR, this::class.java.simpleName, "action failed", e)
    }

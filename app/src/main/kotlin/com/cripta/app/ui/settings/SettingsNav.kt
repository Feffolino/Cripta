package com.cripta.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot request, from other screens, to open a given Settings page (by [SettingsPage] name). */
@Singleton
class SettingsNav @Inject constructor() {
    private val _page = MutableStateFlow<String?>(null)
    val page: StateFlow<String?> = _page
    fun open(page: String) { _page.value = page }
    fun consume() { _page.value = null }
}

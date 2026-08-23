package com.cripta.app.viewer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the ordered list of file ids currently being browsed, so the viewer can swipe
 * between them (gallery style) in the same order shown in the vault.
 */
@Singleton
class ViewerQueue @Inject constructor() {
    @Volatile var ids: List<String> = emptyList()
        private set

    fun set(list: List<String>) { ids = list }
}

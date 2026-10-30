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

    /** Where to start one of them (a face found in a video): taken once by the viewer. */
    @Volatile private var seek: Pair<String, Long>? = null

    fun seekOnOpen(id: String, positionMs: Long) { seek = id to positionMs }

    fun takeSeek(id: String): Long? = seek?.takeIf { it.first == id }?.second?.also { seek = null }
}

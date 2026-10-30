package com.cripta.app.work

/**
 * The background scans (intro, faces) share the one ongoing notification: the first scan running
 * owns it and shows the others' progress under its own ("Volti 40%"). While another operation
 * (import, download…) is showing its progress, the scans stay silent. Never a second notification.
 */
internal class ScanNotices<K : Any>(private val name: (K) -> String) {
    private var owner: K? = null
    private val percent = LinkedHashMap<K, Int>()

    /** Records [kind]'s progress; returns the scan whose notification to post now, or null for none. */
    fun update(kind: K, pct: Int, otherBusy: Boolean): K? {
        percent[kind] = pct
        if (otherBusy) return null
        return owner ?: kind.also { owner = it }
    }

    /** The other scans' progress, for the owner's notification. */
    fun others(kind: K): String? =
        percent.entries.filter { it.key != kind }.joinToString(" · ") { "${name(it.key)} ${it.value}%" }.ifEmpty { null }

    /** [kind] ended: the next scan to post takes the notification over. */
    fun end(kind: K) {
        percent.remove(kind)
        if (owner == kind) owner = null
    }
}

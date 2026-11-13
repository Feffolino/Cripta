package com.cripta.app.ui.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot requests from other screens to the vault (Cartelle) screen: open a given folder, or
 * apply a saved filter. The vault view model takes each request (and clears it) from its init,
 * so a request made before the screen exists waits for it.
 */
@Singleton
class VaultNavigator @Inject constructor() {
    private val _folder = MutableStateFlow<Long?>(null)
    val pendingFolder: StateFlow<Long?> = _folder
    private val _filters = MutableStateFlow<Filters?>(null)
    val pendingFilters: StateFlow<Filters?> = _filters

    fun openFolder(id: Long) { _filters.value = null; _folder.value = id }
    fun applyFilters(f: Filters) { _folder.value = null; _filters.value = f }
    fun consumeFolder() { _folder.value = null }
    fun consumeFilters() { _filters.value = null }
}

/**
 * "Cartelle" tab reselected while already on it (bottom bar / rail): the vault returns to its root.
 * A plain object so the nav host can call [notifyReselected] without injection.
 */
object VaultTabReselect {
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: kotlinx.coroutines.flow.SharedFlow<Unit> = _events
    fun notifyReselected() { _events.tryEmit(Unit) }
}

/** Filters <-> JSON, for saved filters (stored encrypted in the vault DB). */
fun Filters.toJson(): String = org.json.JSONObject().apply {
    put("q", query)
    put("tags", org.json.JSONArray(tagIds.toList()))
    put("ex", org.json.JSONArray(excludedTagIds.toList()))
    put("type", type.name)
    put("fav", favoritesOnly)
    put("untagged", untaggedOnly)
    put("all", tagMatchAll)
    put("people", org.json.JSONArray(personIds.toList()))
    put("progress", inProgress)
    put("whole", wholeVault)
}.toString()

fun filtersFromJson(json: String): Filters? = runCatching {
    val o = org.json.JSONObject(json)
    // As the data layer reads them: an unreadable id is skipped, not the whole filter.
    fun ids(key: String): Set<Long> = com.cripta.app.data.SavedFilterJson.ids(o, key).toSet()
    Filters(
        query = o.optString("q", ""),
        tagIds = ids("tags"),
        excludedTagIds = ids("ex"),
        type = runCatching { TypeFilter.valueOf(o.optString("type", "ALL")) }.getOrDefault(TypeFilter.ALL),
        favoritesOnly = o.optBoolean("fav", false),
        untaggedOnly = o.optBoolean("untagged", false),
        tagMatchAll = o.optBoolean("all", true),
        personIds = ids("people"),
        inProgress = o.optBoolean("progress", false),
        wholeVault = o.optBoolean("whole", false),
    )
}.getOrNull()

package com.cripta.app.ui.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot requests from other screens to the vault (Cartelle) screen: open a given folder, or
 * apply a saved filter. The vault consumes each request once it is on screen.
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

/** Filters <-> JSON, for saved filters (stored encrypted in the vault DB). */
fun Filters.toJson(): String = org.json.JSONObject().apply {
    put("q", query)
    put("tags", org.json.JSONArray(tagIds.toList()))
    put("ex", org.json.JSONArray(excludedTagIds.toList()))
    put("type", type.name)
    put("fav", favoritesOnly)
    put("untagged", untaggedOnly)
    put("all", tagMatchAll)
}.toString()

fun filtersFromJson(json: String): Filters? = runCatching {
    val o = org.json.JSONObject(json)
    fun ids(key: String): Set<Long> {
        val a = o.optJSONArray(key) ?: return emptySet()
        return (0 until a.length()).map { a.getLong(it) }.toSet()
    }
    Filters(
        query = o.optString("q", ""),
        tagIds = ids("tags"),
        excludedTagIds = ids("ex"),
        type = runCatching { TypeFilter.valueOf(o.optString("type", "ALL")) }.getOrDefault(TypeFilter.ALL),
        favoritesOnly = o.optBoolean("fav", false),
        untaggedOnly = o.optBoolean("untagged", false),
        tagMatchAll = o.optBoolean("all", true),
    )
}.getOrNull()

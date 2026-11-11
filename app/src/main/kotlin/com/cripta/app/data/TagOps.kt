package com.cripta.app.data

import com.cripta.app.data.db.FileTagCrossRef

/**
 * The deepest a folder path is followed (a guard against loops left by older versions). One value
 * for the vault's path and the download screen's: with 20 there, two deep folders with the same
 * last 20 names read the same.
 */
const val MAX_FOLDER_DEPTH = 64

/** Largest id list passed to one `IN (...)` query (SQLite allows 999 bound variables). */
internal const val SQL_CHUNK = 400

/** What a tag change really did: the links added and the links removed (for an exact "Annulla"). */
data class TagLinkDiff(
    val added: List<FileTagCrossRef> = emptyList(),
    val removed: List<FileTagCrossRef> = emptyList(),
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
    /** How many files the change touched. */
    val files: Int get() = (added.map { it.fileId } + removed.map { it.fileId }).distinct().size
}

/** A tag created ([created] = true) or the one already in the library with that name, case aside. */
data class TagCreated(val id: Long, val created: Boolean)

/** Outcome of saving the tag editor. */
enum class TagSave { SAVED, NAME_TAKEN, MISSING }

/**
 * A saved filter's JSON (see `Filters.toJson`) seen from the data layer: the tags it includes
 * ("tags") and excludes ("ex"), which refer to tag ids.
 */
object SavedFilterJson {
    sealed interface Pruned {
        data object Unchanged : Pruned
        /** Nothing meaningful is left, or every tag it required is gone: the filter goes. */
        data object Empty : Pruned
        data class Changed(val json: org.json.JSONObject) : Pruned
    }

    private fun ids(o: org.json.JSONObject, key: String): List<Long> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { runCatching { a.getLong(it) }.getOrNull() }
    }

    /** Whether the filter still narrows anything (mirrors `Filters.active`). */
    fun hasCriteria(o: org.json.JSONObject): Boolean =
        o.optString("q", "").isNotBlank() || ids(o, "tags").isNotEmpty() || ids(o, "ex").isNotEmpty() ||
            o.optString("type", "ALL") != "ALL" || o.optBoolean("fav", false) || o.optBoolean("untagged", false) ||
            ids(o, "people").isNotEmpty() || o.optBoolean("progress", false)

    /** Every tag id the filter refers to. */
    fun tagIds(o: org.json.JSONObject): Set<Long> = (ids(o, "tags") + ids(o, "ex")).toSet()

    /** [o] without the tag ids in [dead]; a copy, [o] is left as it is. */
    fun withoutTags(o: org.json.JSONObject, dead: Set<Long>): Pruned {
        val tags = ids(o, "tags")
        val ex = ids(o, "ex")
        if (tags.none { it in dead } && ex.none { it in dead }) return Pruned.Unchanged
        val keptTags = tags.filter { it !in dead }
        // It required tags and none of them exists any more: it can match nothing it was meant for.
        if (tags.isNotEmpty() && keptTags.isEmpty()) return Pruned.Empty
        val copy = org.json.JSONObject(o.toString())
            .put("tags", org.json.JSONArray(keptTags))
            .put("ex", org.json.JSONArray(ex.filter { it !in dead }))
        return if (hasCriteria(copy)) Pruned.Changed(copy) else Pruned.Empty
    }
}

/** Alphabetical order of names as the app's language sorts them (accents next to their letter, case aside). */
object NameOrder {
    fun <T> comparator(name: (T) -> String): Comparator<T> {
        val locale = runCatching { com.cripta.app.Strings.locale }.getOrDefault(java.util.Locale.getDefault())
        val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.SECONDARY }
        // Equal for the collator (only case differs): the exact name decides, so the order is stable.
        return Comparator { a, b -> collator.compare(name(a), name(b)).takeIf { it != 0 } ?: name(a).compareTo(name(b)) }
    }
}

/** What a backup reads, all in one transaction (see `exportBackupInner`). */
internal class BackupSnapshot(
    val folders: List<com.cripta.app.data.db.FolderEntity>,
    val tags: List<com.cripta.app.data.db.TagEntity>,
    val files: List<com.cripta.app.data.db.FileWithTags>,
    val savedFilters: List<com.cripta.app.data.db.SavedFilterEntity>,
)

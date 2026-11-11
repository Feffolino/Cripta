package com.cripta.app.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TagOpsTest {
    private fun filter(tags: List<Long> = emptyList(), ex: List<Long> = emptyList(), q: String = "", all: Boolean = true) =
        org.json.JSONObject().put("q", q).put("tags", org.json.JSONArray(tags)).put("ex", org.json.JSONArray(ex))
            .put("type", "ALL").put("fav", false).put("untagged", false).put("all", all).put("people", org.json.JSONArray())

    private fun ids(o: org.json.JSONObject, key: String) = o.getJSONArray(key).let { a -> (0 until a.length()).map { a.getLong(it) } }

    @Test
    fun aFilterWithoutTheDeletedTagIsLeftAsItIs() {
        assertEquals(SavedFilterJson.Pruned.Unchanged, SavedFilterJson.withoutTags(filter(tags = listOf(1, 2)), setOf(3)))
    }

    @Test
    fun aDeletedTagLeavesAnAndFilterInsteadOfEmptyingIt() {
        val pruned = SavedFilterJson.withoutTags(filter(tags = listOf(1, 2), ex = listOf(2, 5)), setOf(2))
        assertTrue(pruned is SavedFilterJson.Pruned.Changed)
        val json = (pruned as SavedFilterJson.Pruned.Changed).json
        assertEquals(listOf(1L), ids(json, "tags"))
        assertEquals(listOf(5L), ids(json, "ex"))
    }

    @Test
    fun aFilterThatRequiredOnlyDeletedTagsGoes() {
        assertEquals(SavedFilterJson.Pruned.Empty, SavedFilterJson.withoutTags(filter(tags = listOf(2), q = "mare"), setOf(2)))
    }

    @Test
    fun aFilterLeftWithNoCriterionGoes() {
        assertEquals(SavedFilterJson.Pruned.Empty, SavedFilterJson.withoutTags(filter(ex = listOf(2)), setOf(2)))
        val kept = SavedFilterJson.withoutTags(filter(ex = listOf(2), q = "mare"), setOf(2))
        assertTrue(kept is SavedFilterJson.Pruned.Changed)
    }

    @Test
    fun namesSortAsTheLanguageDoes() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.ITALIAN)
        try {
            val sorted = listOf("Zoo", "Èrica", "erica", "Elefante").sortedWith(NameOrder.comparator { it })
            assertEquals(listOf("Elefante", "erica", "Èrica", "Zoo"), sorted)
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun aDiffCountsTheFilesItTouched() {
        val d = TagLinkDiff(added = listOf(com.cripta.app.data.db.FileTagCrossRef("a", 1), com.cripta.app.data.db.FileTagCrossRef("a", 2)),
            removed = listOf(com.cripta.app.data.db.FileTagCrossRef("b", 1)))
        assertEquals(2, d.files)
        assertTrue(TagLinkDiff().isEmpty)
    }
}

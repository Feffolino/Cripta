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

    private fun sortedInAppLanguage(vararg names: String): List<String> {
        com.cripta.app.Strings.init(org.robolectric.RuntimeEnvironment.getApplication())
        return names.toList().sortedWith(NameOrder.comparator { it })
    }

    /** The app's language, not the JVM's default: Swedish puts Å after Z, English next to A. */
    @Test
    @Config(qualifiers = "sv")
    fun namesSortAsTheAppLanguageDoes() {
        assertEquals(listOf("Anna", "Zoo", "Åsa"), sortedInAppLanguage("Zoo", "Åsa", "Anna"))
    }

    @Test
    @Config(qualifiers = "en")
    fun accentsSortNextToTheirLetterAndCaseOnlyBreaksTies() {
        assertEquals(listOf("Anna", "Åsa", "Zoo"), sortedInAppLanguage("Zoo", "Åsa", "Anna"))
        assertEquals(listOf("Elefante", "erica", "Èrica", "Zoo"), sortedInAppLanguage("Zoo", "Èrica", "erica", "Elefante"))
    }

    @Test
    fun aMergedTagIsReplacedInSavedFilters() {
        val merged = SavedFilterJson.withTagMerged(filter(tags = listOf(1, 2), ex = listOf(1, 5)), from = 1, into = 3)!!
        assertEquals(listOf(3L, 2L), ids(merged, "tags"))
        // 1 was both required and left out: after the merge 3 stays required only.
        assertEquals(listOf(5L), ids(merged, "ex"))
        // Into a tag it already asks for: once, not twice.
        assertEquals(listOf(2L), ids(SavedFilterJson.withTagMerged(filter(tags = listOf(1, 2)), 1, 2)!!, "tags"))
        // A filter that does not name it is left alone.
        assertEquals(null, SavedFilterJson.withTagMerged(filter(tags = listOf(2)), 1, 3))
    }

    @Test
    fun aTagMergedIntoOneTheFilterRequiresIsNoLongerLeftOut() {
        // "With B, without A", then A merged into B: asking for B and leaving B out finds nothing.
        val merged = SavedFilterJson.withTagMerged(filter(tags = listOf(2), ex = listOf(1, 5)), from = 1, into = 2)!!
        assertEquals(listOf(2L), ids(merged, "tags"))
        assertEquals(listOf(5L), ids(merged, "ex"))
    }

    @Test
    fun aMergedPersonIsReplacedInSavedFilters() {
        val f = filter().put("people", org.json.JSONArray(listOf(7, 8)))
        assertEquals(listOf(9L, 8L), ids(SavedFilterJson.withPersonMerged(f, from = 7, into = 9)!!, "people"))
        assertEquals(listOf(8L), ids(SavedFilterJson.withPersonMerged(f, from = 7, into = 8)!!, "people"))
        assertEquals(null, SavedFilterJson.withPersonMerged(f, from = 1, into = 9))
    }

    @Test
    fun aDiffCountsTheFilesItTouched() {
        val d = TagLinkDiff(added = listOf(com.cripta.app.data.db.FileTagCrossRef("a", 1), com.cripta.app.data.db.FileTagCrossRef("a", 2)),
            removed = listOf(com.cripta.app.data.db.FileTagCrossRef("b", 1)))
        assertEquals(2, d.files)
        assertTrue(TagLinkDiff().isEmpty)
    }
}

package com.cripta.app.ui.vault

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavedFilterJsonTest {
    @Test fun continueWatchingSurvivesSaving() {
        val f = Filters(inProgress = true, wholeVault = true, tagIds = setOf(3))
        val back = filtersFromJson(f.toJson())!!
        assertEquals(f, back)
        assertTrue(back.active)
    }

    @Test fun olderSavedFiltersReadAsBefore() {
        val back = filtersFromJson("""{"q":"x","tags":[],"ex":[],"type":"ALL","fav":false,"untagged":false,"all":true,"people":[]}""")!!
        assertEquals(Filters(query = "x"), back)
    }

    @Test fun anUnreadableIdIsSkippedNotTheWholeFilter() {
        val back = filtersFromJson("""{"q":"","tags":[3,"x"],"ex":[],"type":"ALL","people":[]}""")
        assertEquals(Filters(tagIds = setOf(3)), back)
    }

    @Test fun aGoneTagFollowsItsMergeAndIsNotBothRequiredAndLeftOut() {
        val f = Filters(tagIds = setOf(2), excludedTagIds = setOf(1, 5), query = "q")
        // 1 merged into 2, 5 deleted.
        val after = f.withTagsGone(mapOf(1L to 2L, 5L to null))
        assertEquals(Filters(tagIds = setOf(2), query = "q"), after)
    }
}

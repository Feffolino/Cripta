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
}

package com.cripta.app.ui.vault

import android.app.Application
import com.cripta.app.data.db.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TagUiLogicTest {
    @org.junit.Before
    fun texts() = com.cripta.app.Strings.init(org.robolectric.RuntimeEnvironment.getApplication())

    private fun tag(id: Long, name: String, used: Long? = null, pinned: Boolean = false, alias: String? = null) =
        TagEntity(id = id, name = name, lastUsedAt = used, pinned = pinned, alias = alias)

    @Test
    fun aFreedRecentSlotIsFilledAtTheEnd() {
        val tags = listOf(tag(1, "a", 50), tag(2, "b", 40), tag(4, "d", 20), tag(3, "c", 30, pinned = true))
        // Frozen when 1, 2, 3 were the recents; 3 got pinned since: 4 takes the free place, last.
        assertEquals(listOf(1L, 2L, 4L), stableRecents(tags, listOf(1, 2, 3), 3).map { it.id })
        // A tag deleted since leaves the row as well, and the next one comes in.
        assertEquals(listOf(2L, 4L), stableRecents(tags.filter { it.id != 1L }, listOf(1, 2), 2).map { it.id })
    }

    @Test
    fun recentsKeepTheirPlaces() {
        val tags = listOf(tag(1, "a", 10), tag(2, "b", 99))
        // 2 was used just now: it does not jump ahead of 1 while the row is on screen.
        assertEquals(listOf(1L, 2L), stableRecents(tags, listOf(1, 2), 2).map { it.id })
    }

    @Test
    fun coversFollowTheLibraryOrderPinnedFirst() {
        val tags = listOf(tag(1, "a"), tag(2, "b"), tag(3, "c", pinned = true))
        assertEquals(listOf(3L, 2L, 1L), libraryOrder(tags, mapOf(1L to 5, 2L to 0, 3L to 9)).map { it.id })
    }

    @Test
    fun anAcronymNeverSplitsACharacter() {
        // Outside the basic plane: "𝒜" is two UTF-16 units.
        assertEquals("𝒜B", tagAlias(tag(1, "𝒜bc")))
        // A letter with a combining accent stays whole.
        assertEquals("ÉL", tagAlias(tag(1, "élan")))
        assertEquals("👨‍👩‍👧", takeGraphemes("👨‍👩‍👧x", 1))
    }

    @Test
    fun aTagIsFoundByNameOrAcronym() {
        val t = tag(1, "Montagna", alias = "MO")
        assertTrue(tagMatches(t, "mont"))
        assertTrue(tagMatches(t, "mo"))
        assertTrue(tagMatches(t, " "))
        assertFalse(tagMatches(t, "mare"))
    }
}

package com.cripta.app.ui.vault

import com.cripta.app.data.SortKey
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileWithTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFiltersLogicTest {
    private fun file(id: String, name: String = id) = FileWithTags(
        FileEntity(id = id, originalName = name, mimeType = "image/jpeg", sizeBytes = 1, createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0)),
        emptyList(),
    )

    @Test fun personFilterKeepsOnlyTheirMedia() {
        val list = listOf(file("a"), file("b"), file("c"))
        val shown = Filters(personIds = setOf(1)).applyTo(list, people = setOf("b"), byName = emptySet())
        assertEquals(listOf("b"), shown.map { it.file.id })
    }

    @Test fun noPersonFilterMeansNoPersonLookup() {
        val list = listOf(file("a"), file("b"))
        assertEquals(2, Filters().applyTo(list, people = null, byName = emptySet()).size)
    }

    @Test fun typedNameFindsMediaOfThePersonCalledThat() {
        val list = listOf(file("a", "x.jpg"), file("b", "y.jpg"))
        val shown = Filters(query = "anna").applyTo(list, people = null, byName = setOf("b"))
        assertEquals(listOf("b"), shown.map { it.file.id })
    }

    @Test fun dayHeadingsOnlyForNewestFirstDate() {
        assertTrue(shouldGroupByDay(true, SortKey.DATE, ascending = false))
        assertFalse(shouldGroupByDay(true, SortKey.DATE, ascending = true))
        assertFalse(shouldGroupByDay(true, SortKey.NAME, ascending = false))
        assertFalse(shouldGroupByDay(true, SortKey.SIZE, ascending = false))
        assertFalse(shouldGroupByDay(false, SortKey.DATE, ascending = false))
    }

    @Test fun scrollLabelsCountEveryGridRow() {
        // Folders row + the "File" heading + two files: the bubble must have 4 entries, not 3.
        val labels = fastScrollLabels("Folders", "File", listOf("" to listOf("A", "B")))
        assertEquals(listOf("Folders", "File", "A · 1/2", "B · 2/2"), labels)
        // The first file sits at the grid index the heading pushed it to.
        assertEquals("A · 1/2", labels[2])
    }

    @Test fun scrollLabelsWithDayHeadings() {
        val labels = fastScrollLabels("Folders", null, listOf("Today" to listOf("T"), "Yesterday" to listOf("Y")))
        assertEquals(listOf("Folders", "Today", "T · 1/2", "Yesterday", "Y · 2/2"), labels)
    }

    private fun video(id: String, pos: Long?) = FileWithTags(
        FileEntity(id = id, originalName = id, mimeType = "video/mp4", sizeBytes = 1, createdAt = 0, importedAt = 0,
            wrappedKeyset = ByteArray(0), playbackPosMs = pos),
        emptyList(),
    )

    @Test fun inProgressFilterKeepsOnlyStartedVideos() {
        val f = Filters(inProgress = true)
        assertTrue(f.active)
        assertTrue(f.matches(video("a", 5_000)))
        assertFalse(f.matches(video("b", null)))
    }

    @Test
    fun homeFiguresSearchTheWholeVaultEvenWithCurrentFolderOnly() {
        assertTrue(searchesWholeVault(active = true, wholeVault = true, currentFolderOnly = true))
        assertFalse(searchesWholeVault(active = true, wholeVault = false, currentFolderOnly = true))
        assertTrue(searchesWholeVault(active = true, wholeVault = false, currentFolderOnly = false))
        assertFalse(searchesWholeVault(active = false, wholeVault = true, currentFolderOnly = false))
    }
}

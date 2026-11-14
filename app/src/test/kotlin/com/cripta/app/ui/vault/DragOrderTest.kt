package com.cripta.app.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/** The grid's order once a drag ends: the user's move, over what the vault holds now. */
class DragOrderTest {
    private fun ids(s: String) = s.map { it.toString() }

    @Test
    fun aDropKeepsTheMoveMade() {
        assertEquals(ids("BAC"), afterDrop(ids("BAC"), ids("ABC")) { it })
    }

    @Test
    fun aFileAddedDuringTheDragIsShownWhereTheVaultHasIt() {
        assertEquals(ids("BACD"), afterDrop(ids("BAC"), ids("ABCD")) { it })
        assertEquals(ids("XBAC"), afterDrop(ids("BAC"), ids("XABC")) { it })
    }

    @Test
    fun twoFilesAddedSideBySideKeepTheirPlaceAndOrder() {
        assertEquals(ids("BXYAC"), afterDrop(ids("BAC"), ids("AXYBC")) { it })
        assertEquals(ids("BACXY"), afterDrop(ids("BAC"), ids("ABCXY")) { it })
    }

    @Test
    fun everyFileRemovedOrNoneThereLeavesTheVaultsList() {
        assertEquals(emptyList<String>(), afterDrop(ids("BAC"), emptyList()) { it })
        assertEquals(emptyList<String>(), afterDrop(emptyList(), emptyList<String>()) { it })
        assertEquals(ids("AB"), afterDrop(emptyList(), ids("AB")) { it })
    }

    @Test
    fun aFileRemovedDuringTheDragIsNotShownAgain() {
        assertEquals(ids("BC"), afterDrop(ids("BAC"), ids("BC")) { it })
    }

    @Test
    fun theVaultsCopyOfAFileIsShown() {
        // Same id, newer content (renamed while dragged): the vault's.
        val out = afterDrop(listOf("B" to 1, "A" to 1), listOf("A" to 2, "B" to 2)) { it.first }
        assertEquals(listOf("B" to 2, "A" to 2), out)
    }

    @Test
    fun aDroppedDragIsShownAndSaved() {
        assertEquals(DragEnd(ids("BACD"), ids("BACD")), endDrag(true, ids("BAC"), ids("ABCD")) { it })
    }

    @Test
    fun aDropWhereItStartedSavesNothing() {
        // Held and let go without a move: nothing to write (every weight, and a refresh of the vault).
        assertEquals(DragEnd(ids("ABC"), null), endDrag(true, ids("ABC"), ids("ABC")) { it })
    }

    @Test
    fun aCancelledDragShowsTheStoredOrderAndSavesNothing() {
        // The moves made (B before A) are dropped; a file added meanwhile is shown, as the vault has it.
        assertEquals(DragEnd(ids("ABCD"), null), endDrag(false, ids("BAC"), ids("ABCD")) { it })
    }
}

package com.cripta.app.data

import org.junit.Assert.*
import org.junit.Test

class ConversionChoicesTest {
    @Test fun laterConversionsDoNotOverwriteEarlierOriginals() {
        val first = VaultRepository.ConvertedOriginal("one", "first.mkv")
        val second = VaultRepository.ConvertedOriginal("two", "second.webm")
        val state = VaultRepository.ConvertStatus().awaiting(first)
            .copy(currentName = "second.webm", lastResult = null).awaiting(second)
            .copy(currentName = null, lastResult = "Second conversion done")
        assertEquals(listOf(first, second), state.pendingOriginals)
        assertEquals(listOf(second), state.resolved("one").pendingOriginals)
    }

    @Test fun resolvingOldOriginalDoesNotRemoveNewCompletion() {
        val state = VaultRepository.ConvertStatus()
            .awaiting(VaultRepository.ConvertedOriginal("one", "one"))
            .awaiting(VaultRepository.ConvertedOriginal("two", "two"))
            .awaiting(VaultRepository.ConvertedOriginal("three", "three"))
        assertEquals(listOf("two", "three"), state.resolved("one").resolved("one").pendingOriginals.map { it.id })
    }

    @Test fun restoredChoicesAreIdempotentAndDismissingResultDoesNotLoseThem() {
        val original = VaultRepository.ConvertedOriginal("one", "one")
        val state = VaultRepository.ConvertStatus(lastResult = "Done").awaiting(original).awaiting(original)
        assertEquals(listOf(original), state.copy(lastResult = null).pendingOriginals)
    }
}

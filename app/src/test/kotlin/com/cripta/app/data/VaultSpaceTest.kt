package com.cripta.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSpaceTest {
    @Test
    fun kindsAddUpToTheAppFootprintAndTheRestOfThePhoneIsOther() {
        val r = SpaceReport.of(
            blobs = listOf(SpaceKind.VIDEOS to 700L, SpaceKind.PHOTOS to 100L, SpaceKind.VIDEOS to 50L, SpaceKind.TRASH to 30L),
            cache = 20L, database = 40L, appTotal = 1_000L,
            deviceTotal = 10_000L, deviceFree = 4_000L,
        )
        assertEquals(750L, r.byKind[SpaceKind.VIDEOS])
        assertEquals(60L, r.byKind[SpaceKind.APP]) // 1000 - (880 blobs + 20 cache + 40 db)
        assertEquals(1_000L, r.vault)
        assertEquals(5_000L, r.other) // 10000 used-or-free minus free minus Cripta
        assertEquals(0.1f, r.share(r.vault), 1e-6f)
    }

    @Test
    fun otherNeverGoesNegativeWhenReadingsDisagree() {
        val r = SpaceReport.of(listOf(SpaceKind.PHOTOS to 900L), 0L, 0L, 900L, deviceTotal = 1_000L, deviceFree = 500L)
        assertEquals(0L, r.other)
        assertEquals(0L, r.byKind[SpaceKind.APP])
    }

    @Test
    fun trashWinsOverTypeAndEachTypeHasItsKind() {
        assertEquals(SpaceKind.TRASH, spaceKindOf("video/mp4", trashed = true))
        assertEquals(SpaceKind.VIDEOS, spaceKindOf("video/mp4", trashed = false))
        assertEquals(SpaceKind.PHOTOS, spaceKindOf("image/jpeg", trashed = false))
        assertEquals(SpaceKind.NOTES, spaceKindOf(VaultRepository.MIME_MARKDOWN, trashed = false))
        assertEquals(SpaceKind.PDF, spaceKindOf("application/pdf", trashed = false))
        assertEquals(SpaceKind.OTHER_FILES, spaceKindOf("application/zip", trashed = false))
    }
}

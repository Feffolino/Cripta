package com.cripta.app.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the jobs that touch the vault keep its keys alive past a lock: the app update download (and
 * the debug progress demo) used neither the DEK nor the database, yet held both in memory for as
 * long as the download ran after "Blocca ora".
 */
class SessionHoldTest {
    @Test
    fun theUpdateDownloadDoesNotHoldTheKeys() {
        assertFalse(ConversionService.holdsVaultKeys("update"))
        assertFalse(ConversionService.holdsVaultKeys("debug_progress"))
    }

    @Test
    fun vaultJobsStillHoldTheKeys() {
        for (mode in listOf("import", "download", "convert", "download_url", "backup", "dup_scan", "face_scan", "intro_scan")) {
            assertTrue(mode, ConversionService.holdsVaultKeys(mode))
        }
    }
}

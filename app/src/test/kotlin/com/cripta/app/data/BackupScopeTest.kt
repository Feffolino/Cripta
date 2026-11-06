package com.cripta.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class BackupScopeTest {
    // 1 and 4 are top-level; 2 is in 1, 3 is in 2; 9's parent is gone.
    private val parents = mapOf(1L to null, 2L to 1L, 3L to 2L, 4L to null, 9L to 77L)

    @Test
    fun aFolderLeftOutTakesItsSubfoldersWithIt() {
        val scope = BackupScope(excludedFolders = setOf(1L))
        assertEquals(1L, scope.topOf(3L, parents))
        assertFalse(scope.includesFolder(3L, parents))
        assertFalse(scope.includesFile(2L, parents))
        assertTrue(scope.includesFile(4L, parents))
        assertTrue(scope.includesFile(null, parents))
        // A broken chain stops at the last folder known.
        assertEquals(77L, scope.topOf(9L, parents))
    }

    @Test
    fun filesInNoFolderAndNoFilesAtAll() {
        assertFalse(BackupScope(rootFiles = false).includesFile(null, parents))
        val none = BackupScope(files = false)
        assertFalse(none.includesFile(4L, parents))
        assertFalse(none.includesFolder(4L, parents))
        assertFalse(none.isEmpty)
        assertTrue(BackupScope(files = false, tags = false, people = false, analysis = false, settings = false).isEmpty)
    }

    @Test
    fun positionsFollowTheChosenFilesOnly() {
        // The files left out leave no gap: covers and faces point at the archive's own order.
        val chosen = listOf("a", "c", "d")
        assertEquals(mapOf("a" to 0, "c" to 1, "d" to 2), BackupScope.positions(chosen))
    }

    @Test
    fun folderIdsRoundTrip() {
        assertEquals(setOf(3L, 12L), BackupScope.parseIds(BackupScope.formatIds(setOf(12L, 3L))))
        assertEquals(emptySet<Long>(), BackupScope.parseIds(null))
        assertEquals(setOf(5L), BackupScope.parseIds("5,,x"))
    }

    @Test
    fun settingsRoundTripWithoutTheDeviceOnes() {
        val values = mapOf<String, Any>(
            "theme_mode" to 1, "video_loop" to false, "people_on_home" to "on", "dl_height" to 720,
            "sort_trash_key" to 2, "big" to 5_000_000_000L, "f" to 1.5f, "d" to 2.25, "set" to setOf("x", "y"),
            "allow_screenshots" to true, "auto_lock_minutes" to 30, "dl_folder" to 4L, "viewer_rotation_lock" to 0,
        )
        val back = BackupSettings.decode(BackupSettings.encode(values))
        assertEquals(values - BackupSettings.EXCLUDED, back)
    }

    @Test
    fun theNewPartIsSkippedOrReadInOrder() {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            BackupMore.begin(out)
            BackupMore.record(out, BackupMore.SETTINGS, byteArrayOf(9, 8))
            BackupMore.record(out, 99, byteArrayOf(1, 2, 3)) // a later kind: skipped
            BackupMore.cover(out, 3, byteArrayOf(7, 7))
            BackupMore.end(out)
        }
        var settings: ByteArray? = null
        val covers = ArrayList<Pair<Int, List<Byte>>>()
        val read = BackupMore.read(DataInputStream(ByteArrayInputStream(bos.toByteArray())), { settings = it }) { p, j -> covers += p to j.toList() }
        assertTrue(read)
        assertArrayEquals(byteArrayOf(9, 8), settings)
        assertEquals(listOf(3 to listOf<Byte>(7, 7)), covers)
        // An archive that ends after the extras (made before this part) has none.
        assertFalse(BackupMore.read(DataInputStream(ByteArrayInputStream(ByteArray(0))), {}) { _, _ -> })
    }

    @Test
    fun restoreReportSaysWhatCameIn() {
        assertEquals("Ripristinati: 1 file", RestoreReport(1).describe())
        assertEquals("Ripristinati: 3 file, 2 etichette, 1 persona, analisi, 4 copertine, impostazioni",
            RestoreReport(3, tags = 2, people = 1, analysis = true, settings = true, covers = 4).describe())
    }
}

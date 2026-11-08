package com.cripta.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScopeEdgeCasesTest {
    @Test
    fun rootFilesCanBeLeftOutWithoutLosingIncludedFolders() {
        val parents = mapOf(1L to null, 2L to 1L, 3L to null)
        val scope = BackupScope(rootFiles = false, excludedFolders = setOf(3L))
        assertFalse(scope.includesFile(null, parents))
        assertTrue(scope.includesFolder(1L, parents))
        assertTrue(scope.includesFile(2L, parents))
        assertFalse(scope.includesFile(3L, parents))
    }

    @Test
    fun staleExclusionsDoNotAccidentallyExcludeAnUnrelatedFolder() {
        val parents = mapOf(1L to null, 2L to 1L)
        val scope = BackupScope(excludedFolders = setOf(900L))
        assertTrue(scope.includesFile(2L, parents))
        assertTrue(scope.includesFile(null, parents))
        assertEquals(17L, scope.topOf(17L, parents))
        assertTrue(scope.includesFolder(17L, parents))
    }

    @Test
    fun longValidFolderChainsStillHonourTheTopLevelExclusion() {
        val parents = (1L..250L).associateWith { if (it == 1L) null else it - 1L }
        val scope = BackupScope(excludedFolders = setOf(1L))
        assertEquals(1L, scope.topOf(250L, parents))
        assertFalse(scope.includesFile(250L, parents))
    }

    @Test(timeout = 1000)
    fun corruptFolderCyclesCannotHangABackupSelection() {
        val scope = BackupScope()
        assertEquals(1L, scope.topOf(1L, mapOf(1L to 1L)))
        assertTrue(scope.topOf(1L, mapOf(1L to 2L, 2L to 1L)) in setOf(1L, 2L))
    }

    @Test
    fun emptyScopeDependsOnBackupContentRatherThanDeviceFolderOptions() {
        val empty = BackupScope(files = false, tags = false, people = false,
            analysis = false, settings = false, covers = false)
        assertTrue(empty.isEmpty)
        assertTrue(empty.copy(covers = true, excludedFolders = setOf(3L), rootFiles = false).isEmpty)
        assertFalse(empty.copy(files = true).isEmpty)
        assertFalse(empty.copy(tags = true).isEmpty)
        assertFalse(empty.copy(people = true).isEmpty)
        assertFalse(empty.copy(analysis = true).isEmpty)
        assertFalse(empty.copy(settings = true).isEmpty)
        assertEquals(emptyMap<String, Int>(), BackupScope.positions(emptyList()))
    }

    @Test
    fun folderIdsRecoverValidEntriesWithoutOverflowOrDuplicateValues() {
        val ids = setOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE)
        assertEquals(ids, BackupScope.parseIds(
            " 0, -1, -1, -9223372036854775808,9223372036854775807,9223372036854775808,invalid,, "))
        assertEquals("-9223372036854775808,-1,0,9223372036854775807", BackupScope.formatIds(ids))
        assertEquals(ids, BackupScope.parseIds(BackupScope.formatIds(ids)))
        assertEquals(emptySet<Long>(), BackupScope.parseIds(" ,invalid,9223372036854775808"))
    }
}

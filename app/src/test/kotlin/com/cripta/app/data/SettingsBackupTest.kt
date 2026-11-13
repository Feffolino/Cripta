package com.cripta.app.data

import android.app.Application
import com.cripta.app.Strings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * The settings part of a backup, through the real store: what one phone exports and another imports.
 * [BackupSettings.EXCLUDED] lists preference names by hand, so it is checked against the names the
 * store really writes: renamed in one place only, a backup would carry screenshots or the auto-lock
 * of the phone it was made on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class SettingsBackupTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val store = SettingsStore(app)

    @Before
    fun setUp() {
        Strings.init(app)
        runBlocking { store.clearAll() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clearAll() }
    }

    private suspend fun phone(screenshots: Boolean, lockMinutes: Int, rotation: Int, folder: Long, tag: Long, loop: Boolean, height: Int) {
        store.setAllowScreenshots(screenshots)
        store.setAutoLockMinutes(lockMinutes)
        store.setViewerRotationLock(rotation)
        store.setDownloadDefaults(DownloadDefaults(folderId = folder, tagIds = listOf(tag), height = height))
        store.setBackupScope(BackupScope(excludedFolders = setOf(folder + 100)))
        store.setLastBackup(LastBackup(at = folder * 1000, files = tag.toInt(), bytes = tag * 10))
        store.setVideoLoop(loop)
    }

    @Test
    fun theProtectionAndIdsOfThisPhoneSurviveAnotherPhonesBackup() = runBlocking {
        phone(screenshots = true, lockMinutes = 60, rotation = 0, folder = 3, tag = 4, loop = false, height = 720)
        val archive = store.exportForBackup()
        assertTrue(BackupSettings.decode(archive).keys.none { it in BackupSettings.EXCLUDED })

        store.clearAll()
        phone(screenshots = false, lockMinutes = 1, rotation = 1, folder = 7, tag = 8, loop = true, height = 480)
        val before = store.settings.first()
        store.importFromBackup(archive)
        val after = store.settings.first()

        assertEquals(before.allowScreenshots, after.allowScreenshots)
        assertEquals(before.autoLockMinutes, after.autoLockMinutes)
        assertEquals(before.viewerRotationLock, after.viewerRotationLock)
        assertEquals(before.downloadDefaults.folderId, after.downloadDefaults.folderId)
        assertEquals(before.downloadDefaults.tagIds, after.downloadDefaults.tagIds)
        assertEquals(before.backupScope.excludedFolders, after.backupScope.excludedFolders)
        assertEquals(before.lastBackup, after.lastBackup)
        // The rest travels.
        assertEquals(false, after.videoLoop)
        assertEquals(720, after.downloadDefaults.height)
    }

    private fun archive(vararg entries: Triple<String, Int, Any>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(entries.size)
            for ((name, type, value) in entries) {
                out.writeUTF(name); out.writeByte(type)
                when (value) {
                    is Boolean -> out.writeBoolean(value)
                    is Int -> out.writeInt(value)
                    else -> error("unused here")
                }
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun aValueOfAnotherTypeThanThisPhonesIsLeftAlone() = runBlocking {
        store.setVideoLoop(false)
        store.setVideoStartMuted(false)
        // video_loop as an Int: this phone keeps its Boolean; the other entry is still put back.
        store.importFromBackup(archive(Triple("video_loop", T_INT, 5), Triple("video_start_muted", T_BOOL, true)))
        val s = store.settings.first()
        assertEquals(false, s.videoLoop)
        assertEquals(true, s.videoStartMuted)
    }

    @Test
    fun aValueNoReadCanTakeRefusesTheWholeArchiveAndWritesNothing() = runBlocking {
        // video_loop is not on this phone yet, and arrives as an Int every read of the settings would choke on.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                store.importFromBackup(archive(Triple("video_start_muted", T_BOOL, true), Triple("video_loop", T_INT, 5)))
            }
        }
        val s = store.settings.first()
        assertEquals(Settings().videoLoop, s.videoLoop)
        assertEquals(false, s.videoStartMuted)
    }

    private companion object {
        const val T_BOOL = 1
        const val T_INT = 2
    }
}

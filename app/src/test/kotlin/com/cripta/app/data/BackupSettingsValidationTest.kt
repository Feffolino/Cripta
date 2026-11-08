package com.cripta.app.data

import android.app.Application
import com.cripta.app.Strings
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class BackupSettingsValidationTest {
    @Before
    fun texts() = Strings.init(RuntimeEnvironment.getApplication())

    private fun archive(write: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.write() }
        return bytes.toByteArray()
    }

    @Test
    fun hostileArchiveCannotRestoreSecurityOrDeviceSpecificSettings() {
        // Bypass encode: untrusted archives must be filtered at decode as well.
        val bytes = archive {
            writeInt(1)
            writeInt(BackupSettings.EXCLUDED.size + 1)
            for (key in BackupSettings.EXCLUDED) {
                writeUTF(key); writeByte(1); writeBoolean(true)
            }
            writeUTF("video_loop"); writeByte(1); writeBoolean(false)
        }
        assertEquals(mapOf("video_loop" to false), BackupSettings.decode(bytes))
    }

    @Test
    fun invalidCountsAndUnknownValueTypesAreRejectedBeforeUse() {
        for (count in listOf(-1, 10_001)) {
            assertThrows(IllegalArgumentException::class.java) {
                BackupSettings.decode(archive { writeInt(1); writeInt(count) })
            }
            assertThrows(IllegalArgumentException::class.java) {
                BackupSettings.decode(archive {
                    writeInt(1); writeInt(1); writeUTF("set"); writeByte(7); writeInt(count)
                })
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSettings.decode(archive { writeInt(0); writeInt(0) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSettings.decode(archive {
                writeInt(1); writeInt(1); writeUTF("future_value"); writeByte(99)
            })
        }
    }

    @Test
    fun truncatedSettingsCannotSilentlyRestoreAnIncompletePreference() {
        val valid = BackupSettings.encode(mapOf("video_loop" to true))
        for (length in listOf(0, 3, valid.lastIndex)) {
            assertThrows(EOFException::class.java) { BackupSettings.decode(valid.copyOf(length)) }
        }
    }

    @Test
    fun unsupportedSettingValuesAreSkippedWithoutDroppingSupportedPreferences() {
        val values = mapOf<String, Any>(
            "list" to listOf("unsupported"), "mixed" to setOf("text", 1),
            "empty" to emptySet<String>(), "title" to "Caffè 日本語", "video_loop" to false,
        )
        val decoded = BackupSettings.decode(BackupSettings.encode(values))
        assertFalse(decoded.containsKey("list"))
        assertFalse(decoded.containsKey("mixed"))
        assertEquals(emptySet<String>(), decoded["empty"])
        assertEquals("Caffè 日本語", decoded["title"])
        assertEquals(false, decoded["video_loop"])
    }
}

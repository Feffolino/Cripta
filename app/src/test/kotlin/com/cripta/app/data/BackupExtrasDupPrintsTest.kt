package com.cripta.app.data

import android.app.Application
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.DupPrintEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupExtrasDupPrintsTest {
    private fun db() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CriptaDatabase::class.java)
        .allowMainThreadQueries().build()

    @Test
    fun fingerprintsFollowTheFilesToTheirNewIds() = runBlocking {
        val from = db()
        val hashes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        from.dupPrintDao().put(DupPrintEntity("old-a", 100, 1, hashes))
        from.dupPrintDao().put(DupPrintEntity("old-b", 200, 2, ByteArray(0)))
        // Not in the archive: left out.
        from.dupPrintDao().put(DupPrintEntity("gone", 300, 1, hashes))
        from.dupPrintDao().putApart(listOf(com.cripta.app.data.db.DupApartEntity("old-a", "old-b"), com.cripta.app.data.db.DupApartEntity("gone", "old-a")))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { BackupExtras.write(from, mapOf("old-a" to 0, "old-b" to 1), it) }

        val to = db()
        val read = DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use {
            BackupExtras.read(to, arrayOf("new-a", "new-b"), it)
        }
        assertTrue(read)
        val rows = to.dupPrintDao().all().associateBy { it.fileId }
        assertEquals(setOf("new-a", "new-b"), rows.keys)
        assertEquals(100L, rows.getValue("new-a").sizeBytes)
        assertEquals(1, rows.getValue("new-a").version)
        assertArrayEquals(hashes, rows.getValue("new-a").hashes)
        // An unreadable media stays known as such.
        assertEquals(0, rows.getValue("new-b").hashes.size)
        // "Non sono simili" follows too, only between files in the archive.
        assertEquals(listOf("new-a|new-b"), to.dupPrintDao().apartPairs().map { it.a + "|" + it.b })
        from.close(); to.close()
    }

    @Test
    fun analysisLeftOutWritesNoFingerprints() = runBlocking {
        val from = db()
        from.dupPrintDao().put(DupPrintEntity("old-a", 100, 1, byteArrayOf(1, 2)))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { BackupExtras.write(from, mapOf("old-a" to 0), it, people = true, analysis = false) }
        val to = db()
        val contents = BackupExtras.Contents()
        val read = DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use {
            BackupExtras.read(to, arrayOf("new-a"), it, contents)
        }
        assertTrue(read)
        assertEquals(0, to.dupPrintDao().all().size)
        assertEquals(false, contents.analysis)
        from.close(); to.close()
    }
}

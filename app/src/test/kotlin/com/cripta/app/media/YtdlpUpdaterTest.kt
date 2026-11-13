package com.cripta.app.media

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class YtdlpUpdaterTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val engine get() = File(File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName), YoutubeDL.ytdlpBin)
    private val now = 1_800_000_000_000L

    @Before fun clear() {
        context.getSharedPreferences("download_engine", 0).edit().clear().commit()
        engine.parentFile!!.deleteRecursively()
        engine.parentFile!!.mkdirs()
        engine.writeText("working old engine")
    }

    private fun sha256(b: ByteArray) =
        "sha256:" + java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun release(digest: String?) = JSONObject().put("tag_name", "new-version").put("assets",
        org.json.JSONArray().put(JSONObject().put("name", "yt-dlp").put("browser_download_url",
            "https://github.com/yt-dlp/yt-dlp/releases/download/new-version/yt-dlp")
            .apply { digest?.let { put("digest", it) } })).toString().toByteArray()

    @Test fun failedUpdateKeepsWorkingEngineAndRetriesLater() {
        val updater = YtdlpUpdater(context) { throw java.net.SocketTimeoutException() }
        assertThrows(java.net.SocketTimeoutException::class.java) { updater.updateIfDue(now) }
        assertEquals("working old engine", engine.readText())
        assertFalse(updater.isDue(now + 60_000))
        assertTrue(updater.isDue(now + 5 * 60_000))
    }

    @Test fun invalidDownloadedArchiveDoesNotReplaceEngine() {
        val junk = "not a zip".toByteArray()
        val updater = YtdlpUpdater(context) { if (it == YtdlpUpdater.RELEASE_URL) release(sha256(junk)) else junk }
        assertThrows(Exception::class.java) { updater.updateIfDue(now) }
        assertEquals("working old engine", engine.readText())
        assertEquals(1, engine.parentFile!!.listFiles()!!.size)
    }

    @Test fun validUpdateIsInstalledAtomicallyAndCheckedAgainNextDay() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for (name in listOf("__main__.py", "yt_dlp/__init__.py")) {
                zip.putNextEntry(ZipEntry(name)); zip.write("# fixture".toByteArray()); zip.closeEntry()
            }
        }
        val bytes = output.toByteArray()
        val updater = YtdlpUpdater(context) { if (it == YtdlpUpdater.RELEASE_URL) release(sha256(bytes)) else bytes }
        updater.updateIfDue(now)
        assertArrayEquals(bytes, engine.readBytes())
        assertFalse(updater.isDue(now + 23 * 60 * 60_000L))
        assertTrue(updater.isDue(now + 24 * 60 * 60_000L))
    }

    private fun zip(vararg names: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for (name in names) { zip.putNextEntry(ZipEntry(name)); zip.write("# fixture".toByteArray()); zip.closeEntry() }
        }
        return output.toByteArray()
    }

    @Test fun theReleaseAssetAZipBehindAShebangIsInstalled() {
        // The real `yt-dlp` asset: "#!/usr/bin/env python3" then the zip. Android's ZipFile refused it.
        val bytes = "#!/usr/bin/env python3\n".toByteArray() + zip("__main__.py", "yt_dlp/__init__.py")
        val updater = YtdlpUpdater(context) { if (it == YtdlpUpdater.RELEASE_URL) release(sha256(bytes)) else bytes }
        updater.updateIfDue(now)
        assertArrayEquals(bytes, engine.readBytes())
    }

    @Test fun onlyAZipappWithTheEngineCountsAsTheEngine() {
        assertTrue(YtdlpUpdater.isEngineArchive(zip("__main__.py", "yt_dlp/__init__.py")))
        assertTrue(YtdlpUpdater.isEngineArchive("#!/usr/bin/env python3\n".toByteArray() + zip("__main__.py", "yt_dlp/__init__.py")))
        assertFalse(YtdlpUpdater.isEngineArchive(zip("__main__.py")))
        assertFalse(YtdlpUpdater.isEngineArchive("not a zip".toByteArray()))
        // Something other than a #! line in front of the zip is not the release.
        assertFalse(YtdlpUpdater.isEngineArchive("MZ junk".toByteArray() + zip("__main__.py", "yt_dlp/__init__.py")))
        assertFalse(YtdlpUpdater.isEngineArchive(ByteArray(0)))
    }

    @Test fun updateWithoutOrWithWrongChecksumIsNeverInstalled() {
        var downloaded = false
        val noDigest = YtdlpUpdater(context) { if (it == YtdlpUpdater.RELEASE_URL) release(null) else { downloaded = true; ByteArray(1) } }
        assertThrows(IllegalStateException::class.java) { noDigest.updateIfDue(now) }
        assertFalse(downloaded)
        context.getSharedPreferences("download_engine", 0).edit().clear().commit()
        val wrong = YtdlpUpdater(context) { if (it == YtdlpUpdater.RELEASE_URL) release(sha256(ByteArray(2))) else ByteArray(1) }
        assertThrows(IllegalStateException::class.java) { wrong.updateIfDue(now) }
        assertEquals("working old engine", engine.readText())
    }

    private fun releaseOf(tag: String, name: String, url: String, digest: String) = JSONObject().put("tag_name", tag)
        .put("assets", org.json.JSONArray().put(JSONObject().put("name", name).put("browser_download_url", url).put("digest", digest)))
        .toString().toByteArray()

    @Test fun anAssetOutsideTheYtDlpReleasesIsNeverDownloaded() {
        var downloaded = false
        val bytes = zip("__main__.py", "yt_dlp/__init__.py")
        val updater = YtdlpUpdater(context) {
            if (it == YtdlpUpdater.RELEASE_URL) releaseOf("new-version", "yt-dlp", "https://example.org/yt-dlp", sha256(bytes))
            else { downloaded = true; bytes }
        }
        assertThrows(IllegalArgumentException::class.java) { updater.updateIfDue(now) }
        assertFalse(downloaded)
        assertEquals("working old engine", engine.readText())
    }

    @Test fun aReleaseWithoutTheEngineAssetChangesNothing() {
        val updater = YtdlpUpdater(context) {
            if (it == YtdlpUpdater.RELEASE_URL)
                releaseOf("new-version", "yt-dlp.exe", "https://github.com/yt-dlp/yt-dlp/releases/download/new-version/yt-dlp.exe", sha256(ByteArray(1)))
            else throw AssertionError("nothing to download")
        }
        assertThrows(NoSuchElementException::class.java) { updater.updateIfDue(now) }
        assertEquals("working old engine", engine.readText())
    }

    @Test fun theSameVersionAlreadyInstalledIsNotDownloadedAgain() {
        val bytes = zip("__main__.py", "yt_dlp/__init__.py")
        var downloads = 0
        val updater = YtdlpUpdater(context) {
            if (it == YtdlpUpdater.RELEASE_URL)
                releaseOf("same", "yt-dlp", "https://github.com/yt-dlp/yt-dlp/releases/download/same/yt-dlp", sha256(bytes))
            else { downloads++; bytes }
        }
        updater.updateIfDue(now)
        assertEquals(1, downloads)
        updater.updateIfDue(now + 24 * 60 * 60_000L)
        assertEquals("same version, engine present: not fetched again", 1, downloads)
        // Gone from the disk: fetched again even with the same version.
        engine.delete()
        updater.updateIfDue(now + 48 * 60 * 60_000L)
        assertEquals(2, downloads)
        assertArrayEquals(bytes, engine.readBytes())
    }
}

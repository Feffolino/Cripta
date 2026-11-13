package com.cripta.app.media

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadRequestsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun completedMergedFileWinsOverLargerIntermediateAndPartialFiles() {
        val dir = temp.newFolder()
        val video = File(dir, "finished.webm").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        File(dir, "intermediate.f137.mp4").writeBytes(ByteArray(100))
        File(dir, "unfinished.mp4.part").writeBytes(ByteArray(200))
        File(dir, DownloadRequests.RESULT_FILE).writeText(video.absolutePath + "\n")
        assertEquals(video.canonicalFile, DownloadRequests.completedFile(dir))
        assertEquals("video/webm", downloadedVideoMime(video))
        assertEquals("video/x-matroska", downloadedVideoMime(File("merged.mkv")))
    }

    @Test fun noReportedCompletionNeverImportsAPartial() {
        val dir = temp.newFolder()
        File(dir, "unfinished.mp4.part").writeBytes(ByteArray(200))
        assertThrows(IllegalStateException::class.java) { DownloadRequests.completedFile(dir) }
    }

    @Test fun outputMustStayInItsWorkingDirectory() {
        val dir = temp.newFolder()
        val outside = temp.newFile("outside.mp4").apply { writeBytes(byteArrayOf(1)) }
        File(dir, DownloadRequests.RESULT_FILE).writeText(outside.absolutePath)
        assertThrows(IllegalStateException::class.java) { DownloadRequests.completedFile(dir) }
    }

    @Test fun cappedQualityDoesNotFallBackToAnUncappedFormat() {
        val request = DownloadRequests.download("https://example.com/video", 720, temp.newFolder())
        assertTrue(request.getOption("-f")!!.split('/').all { "[height<=?720]" in it })
        assertTrue(request.hasOption("--abort-on-unavailable-fragments"))
        assertEquals("20", request.getOption("--socket-timeout"))
        assertEquals("8", request.getOption("--concurrent-fragments"))
        val args = request.buildCommand()
        val index = args.indexOf("--print-to-file")
        assertEquals("after_move:filepath", args[index + 1])
        assertTrue(args[index + 2].endsWith(DownloadRequests.RESULT_FILE))
        assertEquals("https://example.com/video", args.last())
    }

    @Test fun onlyWebLinksReachTheEngine() {
        assertThrows(IllegalArgumentException::class.java) { DownloadRequests.common("--exec rm x") }
        assertThrows(IllegalArgumentException::class.java) { DownloadRequests.common("file:///data/x") }
        assertEquals("https://example.com/v", DownloadRequests.checkedUrl("  https://example.com/v "))
    }
}

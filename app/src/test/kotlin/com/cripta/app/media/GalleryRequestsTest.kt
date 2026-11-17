package com.cripta.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GalleryRequestsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `the link is the last argument and settings files are not read`() {
        val cmd = GalleryRequests.command(File("python"), " https://example.com/post/1 ", File("out"))
        assertEquals("https://example.com/post/1", cmd.last())
        assertEquals(listOf("-m", "gallery_dl"), cmd.subList(1, 3))
        assertTrue("--config-ignore" in cmd && "--no-input" in cmd)
        assertEquals("1-${GalleryRequests.MAX_FILES}", cmd[cmd.indexOf("--range") + 1])
    }

    @Test
    fun `text that is not a web link never reaches gallery-dl`() {
        assertThrows(IllegalArgumentException::class.java) { GalleryRequests.command(File("python"), "--exec=rm", File("out")) }
    }

    @Test
    fun `pictures and videos are taken, anything else is not`() {
        assertEquals("image/jpeg", GalleryRequests.mime(File("1.JPG")))
        assertEquals("image/webp", GalleryRequests.mime(File("a.webp")))
        assertEquals("video/mp4", GalleryRequests.mime(File("clip.mp4")))
        assertNull(GalleryRequests.mime(File("post.json")))
        assertNull(GalleryRequests.mime(File("archive.zip")))
        assertNull(GalleryRequests.mime(File("picture.jpg.part")))
    }

    @Test
    fun `only complete media files of the folder are imported, in name order`() {
        val dir = temp.newFolder()
        File(dir, "post_02.png").writeBytes(byteArrayOf(1))
        File(dir, "post_01.jpg").writeBytes(byteArrayOf(1))
        File(dir, "post_03.jpg").writeBytes(ByteArray(0))
        File(dir, "post_04.jpg.part").writeBytes(byteArrayOf(1))
        File(dir, "info.json").writeBytes(byteArrayOf(1))
        File(dir, "sub.jpg").mkdirs()
        assertEquals(listOf("post_01.jpg", "post_02.png"), GalleryRequests.mediaFiles(dir).map { it.name })
    }
}

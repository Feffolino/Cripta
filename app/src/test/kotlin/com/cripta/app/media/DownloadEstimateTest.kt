package com.cripta.app.media

import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadEstimateTest {
    /** The library's classes are plain data holders filled by Jackson: set whichever of [names] exists. */
    private fun set(target: Any, value: Any, vararg names: String) {
        for (n in names) {
            val f = generateSequence(target.javaClass as Class<*>?) { it.superclass }
                .mapNotNull { c -> runCatching { c.getDeclaredField(n) }.getOrNull() }.firstOrNull() ?: continue
            f.isAccessible = true
            f.set(target, value)
            return
        }
        error("no field among ${names.toList()} on ${target.javaClass}")
    }

    private fun format(height: Int, bytes: Long): VideoFormat =
        VideoFormat::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().also {
            set(it, height, "height")
            set(it, "avc1", "vcodec")
            set(it, "mp4a", "acodec")
            set(it, bytes, "fileSize", "filesize")
        }

    private fun info(vararg formats: VideoFormat): VideoInfo =
        VideoInfo::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().also {
            set(it, java.util.ArrayList(formats.toList()), "formats")
            set(it, 100, "duration")
        }

    @Test fun aSourceWithOnlyLargerRenditionsHasNoEstimateForASmallerCap() {
        // Only 1080p exists: a 720p download cannot be made, so no size is promised for it.
        val source = info(format(1080, 50_000_000))
        assertNull(DownloadEstimate.bytes(source, 720))
        assertNotNull(DownloadEstimate.bytes(source, 1080))
        assertNotNull(DownloadEstimate.bytes(source, null))
    }

    private fun stream(ext: String, height: Int, bytes: Long, video: Boolean, abr: Int = 0): VideoFormat =
        VideoFormat::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().also {
            set(it, ext, "ext")
            set(it, height, "height")
            set(it, if (video) "avc1" else "none", "vcodec")
            set(it, if (video) "none" else "mp4a", "acodec")
            set(it, bytes, "fileSize", "filesize")
            if (abr > 0) set(it, abr, "abr")
        }

    // Regression: the estimate took the tallest stream and the best audio of any container, while
    // the download prefers an MP4 video with M4A audio within the cap.
    @Test fun theEstimateFollowsTheFormatsTheDownloadPrefers() {
        val source = info(
            stream("webm", 2160, 400_000_000, video = true),
            stream("mp4", 1080, 100_000_000, video = true),
            stream("m4a", 0, 5_000_000, video = false, abr = 128),
            stream("webm", 0, 6_000_000, video = false, abr = 160),
        )
        assertEquals(105_000_000L, DownloadEstimate.bytes(source, null))
        // No M4A audio: the download takes the best video and audio of any container.
        val noM4a = info(
            stream("webm", 2160, 400_000_000, video = true),
            stream("mp4", 1080, 100_000_000, video = true),
            stream("webm", 0, 6_000_000, video = false, abr = 160),
        )
        assertEquals(406_000_000L, DownloadEstimate.bytes(noM4a, null))
    }

    @Test fun theLargestRenditionWithinTheCapIsUsed() {
        val source = info(format(360, 5_000_000), format(720, 20_000_000), format(1080, 50_000_000))
        assertNotNull(DownloadEstimate.bytes(source, 720))
        assert(DownloadEstimate.bytes(source, 720)!! == 20_000_000L)
    }
}

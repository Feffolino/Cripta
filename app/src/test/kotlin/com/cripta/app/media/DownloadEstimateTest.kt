package com.cripta.app.media

import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
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

    @Test fun theLargestRenditionWithinTheCapIsUsed() {
        val source = info(format(360, 5_000_000), format(720, 20_000_000), format(1080, 50_000_000))
        assertNotNull(DownloadEstimate.bytes(source, 720))
        assert(DownloadEstimate.bytes(source, 720)!! == 20_000_000L)
    }
}

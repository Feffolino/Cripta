package com.cripta.app.ui.settings

import android.app.Application
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** The duplicates' side-by-side preview never decodes a photo whole: too large to draw. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DuplicatePreviewBitmapTest {
    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test
    fun aVeryLargePhotoIsDecodedSmaller() {
        // Thin, so the test stays light: what counts is the longest side.
        val b = previewBitmap(png(12_000, 8))
        assertNotNull(b)
        assertTrue("width ${b!!.width}", b.width <= 4095)
        assertEquals(3_000, b.width)
    }

    @Test
    fun aPhotoAlreadySmallIsDecodedAsItIs() {
        val b = previewBitmap(png(1_000, 600))!!
        assertEquals(1_000, b.width)
        assertEquals(600, b.height)
    }

    @Test
    fun bytesThatAreNotAPictureGiveNothing() {
        assertNull(previewBitmap(ByteArray(64) { it.toByte() }))
        assertNull(previewBitmap(ByteArray(0)))
    }
}

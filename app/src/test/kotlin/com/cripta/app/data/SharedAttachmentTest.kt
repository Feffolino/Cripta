package com.cripta.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAttachmentTest {
    @Test fun aSharedVideoIsTheAttachmentEvenWithALink() {
        assertTrue(isAttachmentNotPreview("video/mp4"))
    }

    @Test fun aPictureStaysAPreviewBecauseABrowserSharesTitleAndLinkWithOne() {
        assertFalse(isAttachmentNotPreview("image/png"))
    }

    @Test fun otherTypesAreNotMedia() {
        assertFalse(isAttachmentNotPreview("text/html"))
    }
}

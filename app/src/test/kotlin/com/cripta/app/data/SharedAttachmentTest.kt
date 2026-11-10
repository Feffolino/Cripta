package com.cripta.app.data

import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.ui.vault.Filters
import com.cripta.app.ui.vault.matches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAttachmentTest {
    @Test fun aSharedVideoIsTheAttachmentEvenWithALink() {
        assertTrue(isAttachmentNotPreview("video/mp4", "https://example.com/v"))
    }

    @Test fun aPictureStaysAPreviewBecauseABrowserSharesTitleAndLinkWithOne() {
        assertFalse(isAttachmentNotPreview("image/png", "A video https://example.com/page"))
        assertFalse(isAttachmentNotPreview("image/png", "https://example.com/page"))
    }

    @Test fun otherTypesAreNotMedia() {
        assertFalse(isAttachmentNotPreview("text/html", "https://example.com/page"))
    }

    private fun video(id: String, pos: Long?) = FileWithTags(
        FileEntity(id = id, originalName = id, mimeType = "video/mp4", sizeBytes = 1, createdAt = 0, importedAt = 0,
            wrappedKeyset = ByteArray(0), playbackPosMs = pos),
        emptyList(),
    )

    @Test fun inProgressFilterKeepsOnlyStartedVideos() {
        val f = Filters(inProgress = true)
        assertTrue(f.active)
        assertTrue(f.matches(video("a", 5_000)))
        assertFalse(f.matches(video("b", null)))
    }
}

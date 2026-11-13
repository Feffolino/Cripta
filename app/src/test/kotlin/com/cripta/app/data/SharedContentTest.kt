package com.cripta.app.data

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedContentTest {
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    @Test fun browserUrlWinsOverTheAttachedTinyPreview() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_TEXT, "A video https://example.com/watch?id=12")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://browser/preview.png"))
        assertEquals(SharedContent.WebLink("https://example.com/watch?id=12"), classifySharedContent(intent, resolver))
    }

    @Test fun clipDataWebLinksAreNotImportedAsFiles() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        intent.clipData = ClipData.newPlainText("page", "https://example.com/watch")
        assertEquals(SharedContent.WebLink("https://example.com/watch"), classifySharedContent(intent, resolver))
    }

    @Test fun actualMediaFilesStillUseTheEncryptedImportPath() {
        val uri = Uri.parse("content://gallery/video.mp4")
        val intent = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(SharedContent.Files(listOf(uri)), classifySharedContent(intent, resolver))
    }

    @Test fun multipleActualAttachmentsArePreservedEvenWithACaptionUrl() {
        val files = arrayListOf(Uri.parse("content://gallery/one.jpg"), Uri.parse("content://gallery/two.jpg"))
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/album")
        assertEquals(SharedContent.Files(files), classifySharedContent(intent, resolver))
    }

    @Test fun htmlAndInternetShortcutsResolveToThePageRatherThanTheFirstAssetUrl() {
        val html = "<script src='https://cdn.example.com/app.js'></script><link href='https://example.com/watch?a=1&amp;b=2' rel='canonical'>"
        assertEquals("https://example.com/watch?a=1&b=2", webShortcutUrl(html, "text/html", "html"))
        assertEquals("https://example.com/watch", webShortcutUrl("[InternetShortcut]\r\nURL=https://example.com/watch\r\n", "application/octet-stream", "url"))
        assertNull(webShortcutUrl("<html>No original page URL</html>", "text/html", "html"))
    }

    @Test fun fileSchemeDoesNotBypassContentPermissions() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///private/video.mp4"))
        assertEquals(SharedContent.None, classifySharedContent(intent, resolver))
    }

    private fun shared(uri: Uri, type: String, content: String): SharedContent {
        org.robolectric.Shadows.shadowOf(resolver).registerInputStream(uri, java.io.ByteArrayInputStream(content.toByteArray()))
        return classifySharedContent(Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri), resolver)
    }

    @Test fun aSharedUriListIsReadForItsLink() {
        assertEquals(SharedContent.WebLink("https://example.com/list"),
            shared(Uri.parse("content://files/links"), "text/uri-list", "# a comment\r\nhttps://example.com/list\r\n"))
    }

    @Test fun anInternetShortcutFileIsReadForItsLink() {
        assertEquals(SharedContent.WebLink("https://example.com/watch"),
            shared(Uri.parse("content://files/page"), "application/x-url", "[InternetShortcut]\r\nURL=https://example.com/watch\r\n"))
    }

    @Test fun aPlainTextFileWithALinkInsideIsAFile() {
        val uri = Uri.parse("content://files/notes")
        assertEquals(SharedContent.Files(listOf(uri)), shared(uri, "text/plain", "see https://example.com/inside"))
    }

    @Test fun anHtmlShareGivesItsLink() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/html")
            .putExtra(Intent.EXTRA_HTML_TEXT, "<p>Look: <a href=\"https://example.com/a?x=1&amp;y=2\">this</a></p>")
        assertEquals(SharedContent.WebLink("https://example.com/a?x=1&y=2"), classifySharedContent(intent, resolver))
    }

    @Test fun aLinkInTheIntentDataOrAsAStreamIsALink() {
        val data = Intent(Intent.ACTION_SEND).setData(Uri.parse("https://example.com/d"))
        assertEquals(SharedContent.WebLink("https://example.com/d"), classifySharedContent(data, resolver))
        val stream = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.parse("https://example.com/s"))
        assertEquals(SharedContent.WebLink("https://example.com/s"), classifySharedContent(stream, resolver))
    }
}

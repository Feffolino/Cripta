package com.cripta.app.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.Html
import com.cripta.app.ui.download.extractUrl
import com.cripta.app.ui.download.looksLikeUrl

internal sealed interface SharedContent {
    data class WebLink(val url: String) : SharedContent
    data class Files(val uris: List<Uri>) : SharedContent
    data object None : SharedContent
}

/** Browsers may attach a preview image/HTML as EXTRA_STREAM alongside the actual page URL. */
internal fun classifySharedContent(intent: Intent, resolver: ContentResolver): SharedContent {
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return SharedContent.None
    val streams = shareStreams(intent)
    val files = streams.filter { it.scheme == "content" }.distinct()
    if (intent.action == Intent.ACTION_SEND_MULTIPLE && files.isNotEmpty()) return SharedContent.Files(files)
    val text = buildList {
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { add(it) }
        intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.let { htmlShareUrl(it)?.let { url -> add(url) } }
        intent.data?.toString()?.let { add(it) }
        streams.filter { it.scheme == "http" || it.scheme == "https" }.forEach { add(it.toString()) }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).text?.toString()?.let { add(it) }
                clip.getItemAt(i).htmlText?.let { htmlShareUrl(it)?.let { url -> add(url) } }
                clip.getItemAt(i).uri?.toString()?.let { add(it) }
            }
        }
    }
    text.map(::extractUrl).firstOrNull(::looksLikeUrl)?.let { return SharedContent.WebLink(it) }

    // Handle actual .url/.webloc/HTML shortcut attachments without treating arbitrary text files
    // or real videos as links. This bounded read runs on IO, never on the Activity's main thread.
    if (files.size == 1) {
        val uri = files.first()
        val mime = runCatching { resolver.getType(uri) }.getOrNull() ?: intent.type.orEmpty()
        val name = if (mime == "application/octet-stream" || mime == "text/plain") runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull().orEmpty() else ""
        val extension = name.substringAfterLast('.', "").lowercase()
        if (mime in setOf("text/html", "application/xhtml+xml", "text/uri-list", "application/x-internet-shortcut", "application/x-url") || extension in setOf("url", "webloc")) {
            val content = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (bytes.size() < 64 * 1024) {
                        val count = input.read(buffer, 0, minOf(buffer.size, 64 * 1024 - bytes.size()))
                        if (count < 0) break
                        bytes.write(buffer, 0, count)
                    }
                    bytes.toString("UTF-8")
                }
            }.getOrNull()
            content?.let { webShortcutUrl(it, mime, extension) }?.let { return SharedContent.WebLink(it) }
        }
    }
    return if (files.isEmpty()) SharedContent.None else SharedContent.Files(files)
}

private fun htmlShareUrl(html: String): String? = webShortcutUrl(html, "text/html", "html")
    ?: Regex("<a\\b[^>]*href\\s*=\\s*[\"'](https?://[^\"']+)[\"']", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.get(1)?.let {
            Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim().takeIf(::looksLikeUrl)
        }

internal fun webShortcutUrl(text: String, mime: String, extension: String): String? {
    val candidate = when {
        mime == "text/uri-list" -> text.lineSequence().firstOrNull { !it.trimStart().startsWith('#') && looksLikeUrl(it) }
        extension == "webloc" -> Regex("<key>URL</key>\\s*<string>([^<]+)</string>", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        extension == "url" || mime == "application/x-internet-shortcut" || mime == "application/x-url" ->
            Regex("^\\s*URL=(https?://[^\\r\\n]+)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(text)?.groupValues?.get(1)
        else -> {
            Regex("<(?:link|meta)\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(text).mapNotNull { tag ->
                val attrs = Regex("([\\w:-]+)\\s*=\\s*[\"']([^\"']*)[\"']").findAll(tag.value)
                    .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                when {
                    attrs["rel"]?.lowercase()?.split(' ')?.contains("canonical") == true -> attrs["href"]
                    attrs["property"]?.lowercase() == "og:url" -> attrs["content"]
                    else -> null
                }
            }.firstOrNull()
        }
    } ?: return null
    val decoded = Html.fromHtml(candidate.trim(), Html.FROM_HTML_MODE_LEGACY).toString().trim()
    return decoded.takeIf(::looksLikeUrl)
}

private fun shareStreams(intent: Intent): List<Uri> {
    val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    } else {
        val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        listOfNotNull(uri)
    }
    return streams.ifEmpty {
        intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
    }
}

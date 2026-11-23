package com.cripta.app.ui.download

import com.cripta.app.data.DownloadJob
import com.cripta.app.data.DownloadPhase

/**
 * What a queue entry is called. Links of one site share their start ("https://www.site.com/video/…"),
 * so the row that showed the link cut at its end looked the same for each of them.
 *
 * The video's title when it is known: the saved file's name once done, the title the analysis or
 * yt-dlp gave, the page's title for a stream picked in it. Until then, the part of the link that
 * tells it from the other links of [queue] (see [linkLabel]).
 */
internal fun queueLabel(job: DownloadJob, queue: List<DownloadJob>): String {
    if (job.phase == DownloadPhase.DONE) job.message?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    sequenceOf(job.title, job.stream?.title)
        .mapNotNull { it?.trim()?.takeIf { t -> t.isNotEmpty() && !isGenericName(t) } }
        .firstOrNull()?.let { return it }
    return linkLabel(job.url, queue.map { it.url })
}

/**
 * The shortest part of [url] that no other link of the same site among [others] shares, then the
 * site: "my-video-title · site.com", "v=abc123 · youtube.com", "abc123 · cdn.site.com" for
 * ".../abc123/master.m3u8". Tried in order: the last path segment that names something (not
 * "watch", "video", "master.m3u8"…, extension dropped) or else the query; both; the whole path and
 * query; with the fragment. Tracking parameters (utm_…, fbclid…) count only in the last two.
 * A link with nothing after its site is the site alone; text that is not a link stays as it is.
 */
internal fun linkLabel(url: String, others: List<String>): String {
    val own = url.trim()
    val link = Link.parse(own) ?: return own
    val rivals = others.asSequence().map { it.trim() }.filter { it != own }
        .mapNotNull(Link::parse).filter { it.host == link.host }.toList()
    for (level in 0 until Link.LEVELS) {
        val tail = link.tail(level)
        if (rivals.none { it.tail(level) == tail }) return if (tail.isEmpty()) link.host else "$tail · ${link.host}"
    }
    return own.substringAfter("://")
}

/** A name that says nothing of the video: what yt-dlp calls a bare stream, or a player's page. */
internal fun isGenericName(name: String): Boolean = name.trim().lowercase() in GENERIC

private val GENERIC = setOf(
    "watch", "video", "videos", "v", "embed", "e", "view", "play", "player", "index", "master",
    "playlist", "manifest", "stream", "streams", "chunklist", "videoplayback", "post", "posts", "p",
    "status", "statuses", "reel", "reels", "clip", "clips", "media", "movie", "file", "files",
    "download", "dl", "default", "main", "hls", "dash",
)

/** Extensions that only say how the page or stream is served, dropped from the label. */
private val SERVED = Regex("""\.(html?|shtml|php\d?|aspx?|jsp|m3u8?|mpd|mp4|m4v|webm|mkv|mov|ts)$""", RegexOption.IGNORE_CASE)

/** Query keys that only track where the link came from. */
private fun isTracking(key: String): Boolean = key.lowercase().let {
    it.startsWith("utm_") || it in setOf("fbclid", "gclid", "igshid", "si", "feature", "ref", "ref_src", "share", "s")
}

/** A link cut by hand: java.net.URI refuses characters yt-dlp takes (`|`, `{`, a bad `%`). */
private class Link(
    val host: String,
    val segments: List<String>,
    val query: List<Pair<String, String>>,
    val fragment: String,
) {
    /** The last segment that names something, without its extension; null when none does. */
    private val key: String? = segments.asReversed().asSequence()
        .map { it.replace(SERVED, "").trim() }
        .firstOrNull { it.isNotEmpty() && !isGenericName(it) }

    private fun params(all: Boolean) = query.filter { all || !isTracking(it.first) }
        .joinToString("&") { (k, v) -> if (v.isEmpty()) k else "$k=$v" }

    fun tail(level: Int): String = when (level) {
        0 -> key ?: params(all = false)
        1 -> join(key.orEmpty(), params(all = false))
        2 -> join(segments.joinToString("/"), params(all = true))
        else -> join(join(segments.joinToString("/"), params(all = true)), fragment, sep = "#")
    }

    private fun join(a: String, b: String, sep: String = "?") = when {
        a.isEmpty() -> b
        b.isEmpty() -> a
        else -> "$a$sep$b"
    }

    companion object {
        const val LEVELS = 4
        private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")

        fun parse(url: String): Link? {
            val scheme = SCHEME.find(url) ?: return null
            var rest = url.substring(scheme.range.last + 1)
            val fragment = rest.substringAfter('#', "")
            rest = rest.substringBefore('#')
            val query = rest.substringAfter('?', "")
            rest = rest.substringBefore('?')
            val authority = rest.substringBefore('/')
            val host = authority.substringAfterLast('@').let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
                .lowercase().removePrefix("www.").removePrefix("m.")
            if (host.isEmpty()) return null
            val segments = rest.substringAfter('/', "").split('/').filter { it.isNotBlank() }.map { decode(it, plusIsSpace = false) }
            val params = query.split('&').filter { it.isNotBlank() }.map {
                decode(it.substringBefore('='), plusIsSpace = true) to decode(it.substringAfter('=', ""), plusIsSpace = true)
            }
            return Link(host, segments, params, decode(fragment, plusIsSpace = false))
        }

        /** Percent-decoded when it is valid UTF-8 escaping, else as written. */
        private fun decode(s: String, plusIsSpace: Boolean): String = runCatching {
            java.net.URLDecoder.decode(if (plusIsSpace) s else s.replace("+", "%2B"), "UTF-8")
        }.getOrDefault(s)
    }
}

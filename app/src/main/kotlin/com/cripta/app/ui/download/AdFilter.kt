package com.cripta.app.ui.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The requests a browser's ad blocker keeps off a page, read from EasyList's network rules (the
 * list Brave and uBlock use): a player told to show an advertisement it cannot load (its server
 * blocked by the network, as a filtering DNS does) waits on it for good and never asks for the
 * video; with the advertisement's request blocked first, it plays the video straight away.
 *
 * Only what decides blocking is kept: cosmetic rules, rules for a few sites ($domain=) and rules
 * about pages or popups are left out, and a rule's other options are not checked. Each rule is
 * filed under a word of it the address must contain, so an address is tried against a handful of
 * rules, not tens of thousands; a rule's pattern is compiled the first time it is tried.
 */
internal class AdFilter private constructor(
    private val block: Rules,
    private val allow: Rules,
) {
    /** Whether [url] is a request the list blocks (and no exception lets through). */
    fun blocks(url: String): Boolean {
        val u = url.lowercase()
        val tokens = TOKEN.findAll(u).map { it.value }.toSet()
        return block.match(u, tokens) && !allow.match(u, tokens)
    }

    private class Rule(private val pattern: String) {
        private val regex: Regex? by lazy { runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() }
        fun matches(url: String) = regex?.containsMatchIn(url) == true
    }

    private class Rules {
        val byToken = HashMap<String, MutableList<Rule>>()
        val untokened = ArrayList<Rule>()
        fun add(token: String?, rule: Rule) {
            if (token == null) untokened.add(rule) else byToken.getOrPut(token) { ArrayList(1) }.add(rule)
        }
        fun match(url: String, tokens: Set<String>): Boolean =
            tokens.any { t -> byToken[t]?.any { it.matches(url) } == true } || untokened.any { it.matches(url) }
    }

    companion object {
        private val TOKEN = Regex("[a-z0-9%]+")
        private val COSMETIC = Regex("#[@?$%]?#")
        private val LEFT_OUT = Regex("(^|,)(domain=|popup|document|csp|redirect|removeparam|elemhide|generichide)")

        fun parse(lines: Sequence<String>): AdFilter {
            val block = Rules()
            val allow = Rules()
            for (raw in lines) {
                var line = raw.trim()
                if (line.isEmpty() || line.startsWith('!') || line.startsWith('[') || COSMETIC.containsMatchIn(line)) continue
                val exception = line.startsWith("@@")
                if (exception) line = line.substring(2)
                val (pattern, options) = splitOptions(line)
                if (pattern.length < 4 || LEFT_OUT.containsMatchIn(options.lowercase())) continue
                val (regex, token) = compile(pattern)
                (if (exception) allow else block).add(token, Rule(regex))
            }
            return AdFilter(block, allow)
        }

        /** "pattern$options": a regular expression ("/…/") keeps its own '$'. */
        private fun splitOptions(line: String): Pair<String, String> {
            if (line.startsWith('/')) {
                val end = line.lastIndexOf("/$")
                if (end > 0) return line.substring(0, end + 1) to line.substring(end + 2)
                if (line.endsWith('/') && line.length > 2) return line to ""
            }
            val d = line.lastIndexOf('$')
            return if (d > 0) line.substring(0, d) to line.substring(d + 1) else line to ""
        }

        /** The rule as a regular expression, and the word an address it matches must hold (null: none sure). */
        private fun compile(pattern: String): Pair<String, String?> {
            // A regular expression as written (lowered, "\D" would turn into "\d"); it is matched ignoring case.
            if (pattern.length > 2 && pattern.startsWith('/') && pattern.endsWith('/')) return pattern.substring(1, pattern.length - 1) to null
            var p = pattern.lowercase()
            val start = when {
                p.startsWith("||") -> { p = p.substring(2); "^[a-z-]+://(?:[^/?#]*\\.)?" }
                p.startsWith("|") -> { p = p.substring(1); "^" }
                else -> ""
            }
            val end = if (p.endsWith("|")) { p = p.dropLast(1); "$" } else ""
            val body = buildString {
                for (c in p) when (c) {
                    '*' -> append(".*")
                    '^' -> append("(?:[^\\w.%-]|$)")
                    '.', '+', '?', '{', '}', '(', ')', '[', ']', '\\', '|', '$' -> append('\\').append(c)
                    else -> append(c)
                }
            }
            return start + body + end to token(p, anchoredStart = start.isNotEmpty(), anchoredEnd = end.isNotEmpty())
        }

        /**
         * The longest whole word of [p] an address it matches must also hold as a whole word: not
         * touching a '*', and at either end of the pattern only where it is anchored there.
         */
        private fun token(p: String, anchoredStart: Boolean, anchoredEnd: Boolean): String? =
            TOKEN.findAll(p).filter { m ->
                val before = m.range.first - 1
                val after = m.range.last + 1
                (if (before < 0) anchoredStart else p[before] != '*') && (if (after >= p.length) anchoredEnd else p[after] != '*')
            }.map { it.value }.filter { it.length >= 2 }.maxByOrNull { it.length }
    }
}

/**
 * EasyList, fetched from its site and kept for [MAX_AGE_MS] in the app's cache (a copy left from
 * before is used when it cannot be fetched), read once per run of the app. Null when there is no
 * list at all: the page search then works as before, without blocking.
 */
internal object EasyList {
    private const val URL_LIST = "https://easylist.to/easylist/easylist.txt"
    private const val MAX_AGE_MS = 4L * 24 * 60 * 60 * 1000 // the list's own update period
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val lock = Mutex()
    @Volatile private var loaded: AdFilter? = null

    suspend fun filter(cacheDir: File): AdFilter? = loaded ?: lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            val file = File(cacheDir, "easylist.txt")
            if (!fresh(file.takeIf { it.exists() }?.lastModified(), System.currentTimeMillis())) runCatching { download(file) }
            file.takeIf { it.exists() && it.length() > 0 }?.let { f -> f.bufferedReader().useLines { AdFilter.parse(it) } }
        }.also { loaded = it }
    }

    /** A copy saved at [savedAt] is still good at [now]. */
    internal fun fresh(savedAt: Long?, now: Long): Boolean = savedAt != null && now - savedAt in 0 until MAX_AGE_MS

    private fun download(file: File) {
        val connection = URL(URL_LIST).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        try {
            check(connection.responseCode in 200..299) { "EasyList HTTP ${connection.responseCode}" }
            val part = File(file.parentFile, file.name + ".part")
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        check(total <= MAX_BYTES) { "EasyList too large" }
                        out.write(buffer, 0, n)
                    }
                }
            }
            check(part.length() > 0) { "EasyList empty" }
            if (!part.renameTo(file)) { file.delete(); check(part.renameTo(file)) { "EasyList not saved" } }
        } finally { connection.disconnect() }
    }
}

/** What a blocked request gets: an empty refusal, at once, so the page goes on without it. */
internal fun blockedResponse(): android.webkit.WebResourceResponse =
    android.webkit.WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))

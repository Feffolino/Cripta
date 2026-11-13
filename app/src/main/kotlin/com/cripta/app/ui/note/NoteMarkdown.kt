package com.cripta.app.ui.note

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/**
 * Notes are Markdown. This styles the common subset (headings, bold, italic, strikethrough, inline
 * and fenced code, quotes, bullet / numbered / check lists, rules, links) with no library.
 *
 * [render] with `keepMarkers = true` returns exactly the input text, only styled, the markers
 * dimmed: the editor uses it as a visual transformation, so the cursor maps 1:1. With `false` the
 * markers are dropped (bullets and boxes drawn as • ☐ ☑): the viewer's reading form.
 */
object NoteMarkdown {
    class Palette(val muted: Color, val accent: Color, val codeBackground: Color)

    private val FENCE = Regex("""^\s*```.*$""")
    private val HEADING = Regex("""^(#{1,6})(\s+)(.*)$""")
    private val QUOTE = Regex("""^(>\s?)(.*)$""")
    private val CHECK = Regex("""^(\s*)([-*+]\s\[([ xX])\]\s)(.*)$""")
    private val BULLET = Regex("""^(\s*)([-*+]\s)(.*)$""")
    private val NUMBER = Regex("""^(\s*)(\d+[.)]\s)(.*)$""")
    private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val INLINE = Regex(
        """(`)([^`\n]+)(`)""" +
            """|(\*\*)(.+?)(\*\*)""" +
            """|(__)(.+?)(__)""" +
            """|(~~)(.+?)(~~)""" +
            // A link's target may hold one level of brackets ("…/Function_(mathematics)").
            """|(\[)([^\]\n]+)(\]\(((?:[^()\s]|\([^()\s]*\))+)\))""" +
            """|(?<![*\w])(\*)(?![*\s])(.+?)(?<![\s*])(\*)(?![*\w])""" +
            """|(?<![_\w])(_)(?![_\s])(.+?)(?<![\s_])(_)(?![_\w])""" +
            """|($URL_PATTERN)""",
    )

    /**
     * A bare web address: brackets only in balanced pairs ("…/Function_(mathematics)" whole, the
     * ")" closing a sentence's own bracket left out), never ending on punctuation.
     */
    private const val URL_PATTERN =
        """https?://(?:[^\s<>()\[\]]|\([^\s<>()\[\]]*\))*(?:[^\s<>()\[\].,;:!?'"*_~]|\([^\s<>()\[\]]*\))"""

    /** A web address in the text: what [urlAt] looks for (the inline rule above is the same shape). */
    private val URL = Regex(URL_PATTERN)

    /** The web address the cursor is in or touches (a bare one, or the target of a Markdown link), else null. */
    fun urlAt(text: String, cursor: Int): String? =
        URL.findAll(text).firstOrNull { cursor in it.range.first..it.range.last + 1 }?.value

    fun render(text: String, p: Palette, keepMarkers: Boolean): AnnotatedString = buildAnnotatedString {
        val lines = text.split('\n')
        var inFence = false
        var first = true
        for (raw in lines) {
            // A file written on Windows ends its lines with CR LF: the CR is no part of the line
            // (none of the rules below matched with it). Kept in the editor's form, which must give
            // back the text exactly; dropped in the reading form.
            val cr = raw.endsWith('\r')
            val line = if (cr) raw.dropLast(1) else raw
            val fence = FENCE.matches(line)
            // Reading form: the ``` lines themselves are not shown.
            if (fence && !keepMarkers) { inFence = !inFence; continue }
            if (!first) append('\n')
            first = false
            when {
                fence -> { withStyle(SpanStyle(color = p.muted, fontFamily = FontFamily.Monospace)) { append(line) }; inFence = !inFence }
                inFence -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBackground)) { append(line) }
                else -> block(line, p, keepMarkers)
            }
            if (cr && keepMarkers) append('\r')
        }
    }

    private fun AnnotatedString.Builder.marker(s: String, p: Palette, color: Color = p.muted) {
        withStyle(SpanStyle(color = color)) { append(s) }
    }

    private fun AnnotatedString.Builder.block(line: String, p: Palette, keep: Boolean) {
        HEADING.matchEntire(line)?.let { m ->
            val level = m.groupValues[1].length
            val size = when (level) { 1 -> 1.5.em; 2 -> 1.3.em; else -> 1.15.em }
            if (keep) marker(m.groupValues[1] + m.groupValues[2], p)
            withStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold)) { inline(m.groupValues[3], p, keep) }
            return
        }
        if (RULE.matches(line)) {
            if (keep) marker(line, p) else marker("──────────", p)
            return
        }
        QUOTE.matchEntire(line)?.let { m ->
            if (keep) marker(m.groupValues[1], p, p.accent) else marker("▎ ", p, p.accent)
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = p.muted)) { inline(m.groupValues[2], p, keep) }
            return
        }
        CHECK.matchEntire(line)?.let { m ->
            val done = m.groupValues[3] != " "
            append(m.groupValues[1])
            if (keep) marker(m.groupValues[2], p, p.accent) else marker(if (done) "☑ " else "☐ ", p, p.accent)
            if (done) withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = p.muted)) { inline(m.groupValues[4], p, keep) }
            else inline(m.groupValues[4], p, keep)
            return
        }
        BULLET.matchEntire(line)?.let { m ->
            append(m.groupValues[1])
            if (keep) marker(m.groupValues[2], p, p.accent) else marker("• ", p, p.accent)
            inline(m.groupValues[3], p, keep)
            return
        }
        NUMBER.matchEntire(line)?.let { m ->
            append(m.groupValues[1])
            marker(m.groupValues[2], p, p.accent)
            inline(m.groupValues[3], p, keep)
            return
        }
        inline(line, p, keep)
    }

    /**
     * Longest line whose inline styles are looked for. The italic and link rules scan to the end of
     * the line from every opening mark without a close: a line of thousands of them ("*a *a ...")
     * costs the square of its length, and the editor renders on every key. Longer lines stay plain.
     */
    private const val INLINE_MAX = 2000

    private fun AnnotatedString.Builder.inline(text: String, p: Palette, keep: Boolean) {
        if (text.length > INLINE_MAX) { append(text); return }
        var at = 0
        for (m in INLINE.findAll(text)) {
            append(text.substring(at, m.range.first))
            val g = m.groups
            when {
                g[1] != null -> {
                    if (keep) marker("`", p)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBackground)) { append(g[2]!!.value) }
                    if (keep) marker("`", p)
                }
                g[4] != null || g[7] != null -> {
                    val (o, inner, c) = if (g[4] != null) Triple(g[4]!!, g[5]!!, g[6]!!) else Triple(g[7]!!, g[8]!!, g[9]!!)
                    if (keep) marker(o.value, p)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(inner.value, p, keep) }
                    if (keep) marker(c.value, p)
                }
                g[10] != null -> {
                    if (keep) marker("~~", p)
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { inline(g[11]!!.value, p, keep) }
                    if (keep) marker("~~", p)
                }
                g[13] != null -> {
                    val label = g[14]!!.value
                    val url = g[16]!!.value
                    val style = SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline)
                    if (keep) {
                        marker("[", p)
                        withStyle(style) { append(label) }
                        marker(g[15]!!.value, p)
                    } else if (url.startsWith("https://") || url.startsWith("http://")) {
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(style))) { append(label) }
                    } else {
                        withStyle(style) { append(label) }
                    }
                }
                g[23] != null -> {
                    val url = g[23]!!.value
                    val style = SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline)
                    if (keep) withStyle(style) { append(url) }
                    else withLink(LinkAnnotation.Url(url, TextLinkStyles(style))) { append(url) }
                }
                else -> {
                    val (o, inner, c) = if (g[17] != null) Triple(g[17]!!, g[18]!!, g[19]!!) else Triple(g[20]!!, g[21]!!, g[22]!!)
                    if (keep) marker(o.value, p)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(inner.value, p, keep) }
                    if (keep) marker(c.value, p)
                }
            }
            at = m.range.last + 1
        }
        append(text.substring(at))
    }

    // ---- Editing helpers (the formatting bar) ----

    /** Wraps the selection in [mark] (or inserts a pair with the cursor between), or unwraps it. */
    fun wrap(v: TextFieldValue, mark: String): TextFieldValue {
        val s = v.selection.min; val e = v.selection.max
        val t = v.text
        val before = t.substring(0, s); val sel = t.substring(s, e); val after = t.substring(e)
        if (before.endsWith(mark) && after.startsWith(mark)) {
            val nt = before.dropLast(mark.length) + sel + after.drop(mark.length)
            return TextFieldValue(nt, TextRange(s - mark.length, e - mark.length))
        }
        val nt = before + mark + sel + mark + after
        return TextFieldValue(nt, TextRange(s + mark.length, e + mark.length))
    }

    /**
     * Enter on a list line starts the next item ("- ", "- [ ] ", "3. "); Enter on an empty item
     * ends the list instead (the empty marker goes). Anything else passes through unchanged.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val c = new.selection.start
        if (!new.selection.collapsed || new.text.length != old.text.length + 1 || c < 1 || new.text[c - 1] != '\n') return new
        if (new.text.substring(0, c - 1) + new.text.substring(c) != old.text) return new
        val lineStart = new.text.lastIndexOf('\n', c - 2) + 1
        val line = new.text.substring(lineStart, c - 1)
        val m = LINE_PREFIX.find(line)!!
        val indent = m.groupValues[1]
        val prefix = m.groupValues[2]
        if (prefix.isEmpty() || prefix.startsWith("#")) return new
        val rest = line.substring(m.range.last + 1)
        if (rest.isBlank()) {
            // Empty item: end the list (the line becomes empty, no new line).
            val nt = new.text.substring(0, lineStart) + new.text.substring(c)
            return TextFieldValue(nt, TextRange(lineStart))
        }
        val next = when {
            // A number too big to count on: the new line starts plain instead of crashing the editor.
            prefix.matches(Regex("""\d+[.)]\s""")) -> nextNumberedPrefix(prefix) ?: return new
            prefix.contains('[') -> prefix.take(2) + "[ ] "
            else -> prefix
        }
        val ins = indent + next
        return TextFieldValue(new.text.substring(0, c) + ins + new.text.substring(c), TextRange(c + ins.length))
    }

    /** Highest list number the editor goes on from ("999999. x" + Enter gives "1000000. "). */
    const val MAX_LIST_NUMBER = 999_999

    /**
     * The marker of the item after [prefix] ("3. " gives "4. "), or null when the number is not a
     * usable one (more digits than an Int holds, or past [MAX_LIST_NUMBER]): then no numbering goes on.
     */
    fun nextNumberedPrefix(prefix: String): String? {
        val digits = prefix.takeWhile { it.isDigit() }
        val n = digits.toIntOrNull() ?: return null
        if (n >= MAX_LIST_NUMBER) return null
        return "${n + 1}${prefix.drop(digits.length)}"
    }

    /** Line [index] of [text] with its check box flipped ("- [ ]" ↔ "- [x]"); other lines as they are. */
    fun toggleCheck(text: String, index: Int): String {
        val lines = text.split('\n').toMutableList()
        val raw = lines.getOrNull(index) ?: return text
        val cr = if (raw.endsWith('\r')) "\r" else ""
        val m = CHECK.matchEntire(raw.removeSuffix("\r")) ?: return text
        val box = if (m.groupValues[3] == " ") "x" else " "
        lines[index] = m.groupValues[1] + m.groupValues[2].replaceRange(3, 4, box) + m.groupValues[4] + cr
        return lines.joinToString("\n")
    }

    /** Whether [line] is a check-list item, and if it is ticked. Null for any other line. */
    fun checkState(line: String): Boolean? = CHECK.matchEntire(line.removeSuffix("\r"))?.let { it.groupValues[3] != " " }

    /** The text of a check-list item, without its "- [ ] ". */
    fun checkText(line: String): String = line.removeSuffix("\r").let { l -> CHECK.matchEntire(l)?.groupValues?.get(4) ?: l }

    /** Whether [line] opens or closes a ``` code block. */
    fun isFence(line: String): Boolean = FENCE.matches(line.removeSuffix("\r"))

    private val LINE_PREFIX = Regex("""^(\s*)(#{1,6}\s+|[-*+]\s\[[ xX]\]\s|[-*+]\s|\d+[.)]\s)?""")

    /**
     * Sets the line prefix of every line in the selection: [prefix] (null = cycle the headings
     * # → ## → ### → none). A line that already has it loses it; any other list or heading
     * prefix is replaced.
     */
    fun linePrefix(v: TextFieldValue, prefix: String?): TextFieldValue {
        val t = v.text
        val start = t.lastIndexOf('\n', (v.selection.min - 1).coerceAtLeast(-1)).let { if (v.selection.min == 0) 0 else it + 1 }
        // A selection ending right after a line break ends on that line: the next one is not in it.
        val endAt = if (!v.selection.collapsed && t.getOrNull(v.selection.max - 1) == '\n') v.selection.max - 1 else v.selection.max
        val endNl = t.indexOf('\n', endAt).let { if (it < 0) t.length else it }
        var numbered = 0
        val changed = t.substring(start, endNl).split('\n').joinToString("\n") { line ->
            val m = LINE_PREFIX.find(line)!!
            val indent = m.groupValues[1]
            val old = m.groupValues[2]
            val rest = line.substring(m.range.last + 1)
            val next = when (prefix) {
                null -> when (old.trim()) { "#" -> "## "; "##" -> "### "; "###" -> ""; else -> "# " }
                "1. " -> { numbered++; if (old.matches(Regex("""\d+[.)]\s"""))) "" else "$numbered. " }
                else -> if (old == prefix) "" else prefix
            }
            indent + next + rest
        }
        val nt = t.substring(0, start) + changed + t.substring(endNl)
        val delta = nt.length - t.length
        val cursor = if (v.selection.collapsed) (v.selection.end + delta).coerceIn(start, start + changed.length) else start + changed.length
        return TextFieldValue(nt, if (v.selection.collapsed) TextRange(cursor) else TextRange(start, start + changed.length))
    }
}

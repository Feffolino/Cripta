package com.cripta.app.ui.note

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMarkdownTest {
    private val p = NoteMarkdown.Palette(Color.Gray, Color.Blue, Color.LightGray)
    private val sample = "# Titolo\n**grassetto** e *corsivo* e `code`\n- uno\n- [x] fatto\n- [ ] da fare\n1. primo\n> citazione\n" +
        "[link](https://example.com) ~~via~~\n---\n```\n**non stile**\n```\nfine_di_parola"

    @Test fun editorFormKeepsTheTextExactly() {
        // The editor maps the cursor 1:1: the styled text must be the typed text.
        assertEquals(sample, NoteMarkdown.render(sample, p, keepMarkers = true).text)
    }

    @Test fun readingFormDropsTheMarkers() {
        val out = NoteMarkdown.render(sample, p, keepMarkers = false).text
        assertEquals(
            "Titolo\ngrassetto e corsivo e code\n• uno\n☑ fatto\n☐ da fare\n1. primo\n▎ citazione\nlink via\n──────────\n**non stile**\nfine_di_parola",
            out,
        )
    }

    @Test fun enterContinuesAndEndsLists() {
        val old = TextFieldValue("- [x] latte", TextRange(11))
        val typed = TextFieldValue("- [x] latte\n", TextRange(12))
        assertEquals("- [x] latte\n- [ ] ", NoteMarkdown.continueList(old, typed).text)
        val num = NoteMarkdown.continueList(TextFieldValue("2. due", TextRange(6)), TextFieldValue("2. due\n", TextRange(7)))
        assertEquals("2. due\n3. ", num.text)
        val end = NoteMarkdown.continueList(TextFieldValue("- uno\n- ", TextRange(8)), TextFieldValue("- uno\n- \n", TextRange(9)))
        assertEquals("- uno\n", end.text)
    }

    @Test fun toolsWrapAndPrefix() {
        assertEquals("a **b** c", NoteMarkdown.wrap(TextFieldValue("a b c", TextRange(2, 3)), "**").text)
        assertEquals("- [ ] x\n- [ ] y", NoteMarkdown.linePrefix(TextFieldValue("x\ny", TextRange(0, 3)), "- [ ] ").text)
        assertEquals("x", NoteMarkdown.linePrefix(TextFieldValue("- x", TextRange(3)), "- ").text)
        assertEquals("# t", NoteMarkdown.linePrefix(TextFieldValue("t", TextRange(1)), null).text)
        assertEquals("- [x] a\nb", NoteMarkdown.toggleCheck("- [ ] a\nb", 0))
    }
}

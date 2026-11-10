package com.cripta.app.ui.note

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteListNumberTest {
    private fun enter(line: String): String {
        val old = TextFieldValue(line, TextRange(line.length))
        val typed = TextFieldValue(line + "\n", TextRange(line.length + 1))
        return NoteMarkdown.continueList(old, typed).text
    }

    @Test fun numbersCountOn() {
        assertEquals("4. ", NoteMarkdown.nextNumberedPrefix("3. "))
        assertEquals("10) ", NoteMarkdown.nextNumberedPrefix("9) "))
    }

    @Test fun aNumberBeyondAnIntDoesNotCrashAndStopsTheList() {
        // 2147483648 does not fit an Int: it used to throw NumberFormatException on Enter.
        assertNull(NoteMarkdown.nextNumberedPrefix("2147483648. "))
        assertEquals("2147483648. Voce\n", enter("2147483648. Voce"))
    }

    @Test fun aNumberAtTheCapDoesNotGoNegativeOrOnAndOn() {
        assertNull(NoteMarkdown.nextNumberedPrefix("2147483647. "))
        assertNull(NoteMarkdown.nextNumberedPrefix("${NoteMarkdown.MAX_LIST_NUMBER}. "))
        assertEquals("${NoteMarkdown.MAX_LIST_NUMBER}. x\n", enter("${NoteMarkdown.MAX_LIST_NUMBER}. x"))
        assertEquals("${NoteMarkdown.MAX_LIST_NUMBER}. ", NoteMarkdown.nextNumberedPrefix("${NoteMarkdown.MAX_LIST_NUMBER - 1}. "))
    }
}

package com.cripta.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** English (res/values) and Italian (res/values-it) have the same texts, with the same placeholders. */
class StringsParityTest {
    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfc%]""")

    private fun holders(text: String) = placeholder.findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()

    /** name (a plural as "name#quantity") → the placeholders it uses, sorted. */
    private fun texts(dir: String): Map<String, List<String>> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing $file", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, List<String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            out[e.getAttribute("name")] = if (e.getAttribute("formatted") == "false") emptyList() else holders(e.textContent)
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val items = e.getElementsByTagName("item")
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                out[e.getAttribute("name") + "#" + item.getAttribute("quantity")] = holders(item.textContent)
            }
        }
        return out
    }

    @Test
    fun everyTextExistsInBothLanguagesWithTheSamePlaceholders() {
        val en = texts("values")
        val it = texts("values-it")
        assertEquals("only in English", emptySet<String>(), en.keys - it.keys)
        assertEquals("only in Italian", emptySet<String>(), it.keys - en.keys)
        val different = en.keys.filter { k -> en[k] != it[k] }.map { k -> "$k: ${en[k]} vs ${it[k]}" }
        assertEquals("different placeholders", emptyList<String>(), different)
    }

    /**
     * A text that keeps a space at its start or end (only possible inside quotes: Android trims
     * unquoted texts) keeps it in both languages. "x GB of y GB" read "x GBofy GB" when only the
     * Italian " di " was quoted.
     */
    @Test
    fun edgeSpacesMatchInBothLanguages() {
        fun edges(dir: String): Map<String, Pair<Boolean, Boolean>> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
            val strings = doc.getElementsByTagName("string")
            return (0 until strings.length).associate { i ->
                val e = strings.item(i) as Element
                val raw = e.textContent
                val kept = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw.trim()
                e.getAttribute("name") to (kept.startsWith(" ") to kept.endsWith(" "))
            }
        }
        val en = edges("values")
        val ita = edges("values-it")
        val different = en.keys.filter { k -> k in ita && en[k] != ita[k] }
        assertEquals("edge spaces differ", emptyList<String>(), different)
    }
}

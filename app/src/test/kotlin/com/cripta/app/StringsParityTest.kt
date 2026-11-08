package com.cripta.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every offered language has complete resources, valid format arguments and preserved spacing. */
class StringsParityTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("""%%(?:\.\d+f|[sdfc])|%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfc%]""")
    private val quantities = mapOf(
        "values-it" to setOf("one", "many", "other"),
        "values-es" to setOf("one", "many", "other"),
        "values-fr" to setOf("one", "many", "other"),
        "values-de" to setOf("one", "other"),
        "values-pt-rBR" to setOf("one", "many", "other"),
        "values-ru" to setOf("one", "few", "many", "other"),
        "values-ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "values-hi" to setOf("one", "other"),
        "values-zh-rCN" to setOf("other"),
        "values-ja" to setOf("other"),
        "values-ko" to setOf("other"),
    )

    private val dynamicOneLocales = setOf("values-fr", "values-pt-rBR", "values-ru", "values-hi")

    private fun elements(file: File, tag: String? = null): List<Element> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        return (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
            .filter { tag == null || it.tagName == tag }
    }

    private fun resources(dir: File) = elements(File(dir, "strings.xml"))

    private fun translations() = res.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").exists() }
        .sortedBy { it.name }

    // formatted="false" disables AAPT format checks; callers still format these diagnostic texts.
    private fun holders(e: Element) = placeholder.findAll(e.textContent).map { it.value }.sorted().toList()

    private fun assertText(source: Element, target: Element, key: String) {
        assertEquals("$key: placeholders", holders(source), holders(target))
        for (token in listOf("\\\"", "\\n")) {
            fun count(e: Element) = e.textContent.windowed(token.length).count { it == token }
            assertEquals("$key: $token escapes", count(source), count(target))
        }
    }

    private fun items(e: Element): List<Element> = (0 until e.childNodes.length)
        .mapNotNull { e.childNodes.item(it) as? Element }

    // These locales use "one" for counts other than 1 (e.g. French 0 and Russian 21).
    // Their singular forms keep the dynamic count supplied by Strings.plural, rather than
    // copying the English literal "1" or dropping the count from an implicit singular.
    private fun reference(dir: String, item: Element, source: Element): Element {
        val original = item.getAttribute("quantity")
        val quantity = if (original == "one" && dir in dynamicOneLocales)
            "other" else original
        val choices = items(source)
        return choices.firstOrNull { it.getAttribute("quantity") == quantity }
            ?: choices.first { it.getAttribute("quantity") == "other" }
    }

    @Test
    fun everyLanguageHasTheSameKeysAndPlaceholders() {
        val en = resources(File(res, "values"))
        val keys = en.map { it.tagName to it.getAttribute("name") }
        val dirs = translations()
        assertTrue("no translations found", dirs.isNotEmpty())
        val offered = elements(File(res, "xml/locales_config.xml"), "locale")
            .map { it.getAttribute("android:name") }.toSet()
        val translated = dirs.map { it.name.removePrefix("values-").replace("-r", "-") }.toSet() + "en"
        assertEquals("locale config and translation directories differ", offered, translated)
        for (dir in dirs) {
            val texts = resources(dir)
            assertEquals("${dir.name}: missing, extra, reordered or duplicate keys", keys,
                texts.map { it.tagName to it.getAttribute("name") })
            for ((source, target) in en.zip(texts)) {
                val key = "${dir.name}/${source.getAttribute("name")}"
                assertEquals("$key: formatting flag", source.getAttribute("formatted"), target.getAttribute("formatted"))
                if (source.tagName == "string") {
                    assertText(source, target, key)
                } else {
                    val entries = items(target)
                    val categories = entries.map { it.getAttribute("quantity") }
                    assertEquals("$key: duplicate plural categories", categories.size, categories.toSet().size)
                    val required = quantities[dir.name] ?: items(source).map { it.getAttribute("quantity") }.toSet()
                    assertEquals("$key: plural categories", required, categories.toSet())
                    for (entry in entries) {
                        assertText(reference(dir.name, entry, source), entry,
                            "$key#${entry.getAttribute("quantity")}")
                    }
                }
            }
        }
    }

    private fun edges(e: Element): Pair<Int, Int> {
        val raw = e.textContent
        val kept = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\""))
            raw.substring(1, raw.length - 1) else raw.trim()
        return kept.takeWhile { it == ' ' }.length to kept.takeLastWhile { it == ' ' }.length
    }

    @Test
    fun settingsFragmentsPreserveSpacing() {
        val suffixes = setOf("sum_dynamic", "sum_filters_folder", "sum_on_covers",
            "sum_recent", "sum_shots_allowed", "sum_shots_blocked", "dup_tags_moved_list")
        for (dir in listOf(File(res, "values")) + translations()) {
            val texts = resources(dir).associateBy { it.getAttribute("name") }
            for (name in suffixes) {
                val text = requireNotNull(texts[name])
                assertEquals("${dir.name}/$name: leading space", 1, edges(text).first)
                assertTrue("${dir.name}/$name: separator", text.textContent.startsWith("\" · "))
            }
            assertEquals("${dir.name}/deep_search_desc: trailing space", 1,
                edges(requireNotNull(texts["deep_search_desc"])).second)
        }
    }

    @Test
    fun edgeSpacesMatchInEveryLanguage() {
        val en = resources(File(res, "values"))
        for (dir in translations()) {
            val texts = resources(dir).associateBy { it.getAttribute("name") }
            for (source in en) {
                val key = "${dir.name}/${source.getAttribute("name")}"
                val target = texts[source.getAttribute("name")]
                assertTrue("$key missing", target != null)
                if (source.tagName == "string") {
                    assertEquals("$key: edge spaces", edges(source), edges(requireNotNull(target)))
                } else {
                    for (entry in items(requireNotNull(target))) {
                        assertEquals("$key#${entry.getAttribute("quantity")}: edge spaces",
                            edges(reference(dir.name, entry, source)), edges(entry))
                    }
                }
            }
        }
    }
}

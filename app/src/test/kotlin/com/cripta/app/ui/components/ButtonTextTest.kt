package com.cripta.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonTextTest {
    @Test
    fun `a label's words are what a line may not break inside`() {
        assertEquals(listOf("Cerca", "nella", "pagina"), unbreakableWords("Cerca nella  pagina"))
        assertEquals(listOf("Riprova"), unbreakableWords(" Riprova "))
        assertEquals(listOf("Скачать", "1", "ссылку"), unbreakableWords("Скачать 1 ссылку"))
    }

    @Test
    fun `chinese and japanese runs may break anywhere, so they are not words`() {
        assertEquals(emptyList<String>(), unbreakableWords("在页面中查找"))
        assertEquals(emptyList<String>(), unbreakableWords("ページ内を探す"))
        // Korean is written with spaces: its words stay whole.
        assertEquals(listOf("페이지에서", "찾기"), unbreakableWords("페이지에서 찾기"))
    }
}

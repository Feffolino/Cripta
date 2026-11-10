package com.cripta.app.data.face

import com.cripta.app.data.PeopleSort
import com.cripta.app.data.db.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleOrderTest {
    private fun person(id: Long, name: String, fav: Boolean = false, ignored: Boolean = false, created: Long = id) =
        FaceRepository.Person(PersonEntity(id = id, name = name, favorite = fav, ignored = ignored, createdAt = created), emptyList(), null, emptyList())

    private val people = listOf(person(1, "Zoe"), person(2, "Anna", fav = true), person(3, "Mario"),
        person(4, "Bea", fav = true), person(5, "Ugo", fav = true, ignored = true))

    private fun ids(l: List<FaceRepository.Person>) = l.map { it.id }

    @Test
    fun starredAlwaysFirstAndHiddenLastWhateverTheOrder() {
        for (k in PeopleSort.entries) for (asc in listOf(true, false)) {
            val out = people.ordered(k, asc)
            assertEquals("$k $asc", setOf(2L, 4L), ids(out).take(2).toSet())
            assertEquals("$k $asc", 5L, out.last().id)
        }
    }

    @Test
    fun orderHoldsInsideEachGroup() {
        assertEquals(listOf(2L, 4L, 3L, 1L, 5L), ids(people.ordered(PeopleSort.NAME, true)))
        assertEquals(listOf(4L, 2L, 1L, 3L, 5L), ids(people.ordered(PeopleSort.NAME, false)))
        // Relevance descending is the list as given (starred first by the repository), ascending its reverse.
        val repo = listOf(person(2, "Anna", fav = true), person(1, "Zoe"), person(5, "Ugo", ignored = true))
        assertEquals(listOf(2L, 1L, 5L), ids(repo.ordered(PeopleSort.RELEVANCE, false)))
    }

    @Test
    fun searchFindsNamedVisiblePeopleKeepingOrder() {
        assertEquals(listOf(2L), ids(people.matching("ANN")))
        assertEquals(emptyList<Long>(), ids(people.matching("ugo")))
        assertEquals(emptyList<Long>(), ids(people.matching("  ")))
        assertEquals(listOf(2L, 3L, 4L), ids(people.matching("a")))
    }
}

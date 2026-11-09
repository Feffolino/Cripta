package com.cripta.app.data.face

import com.cripta.app.data.PeopleSort

/**
 * The people page's order, shared with the filters. Whatever the order, starred people come first
 * and hidden ones last (a stable pass, so the chosen order holds inside each group).
 * [this] must be in the repository's order (starred, main, most media): that is "relevance" descending.
 */
fun List<FaceRepository.Person>.ordered(k: PeopleSort, ascending: Boolean): List<FaceRepository.Person> {
    val sorted = when (k) {
        PeopleSort.RELEVANCE -> reversed()
        PeopleSort.NAME -> sortedBy { it.title.lowercase() }
        PeopleSort.MEDIA -> sortedBy { it.media.size }
        PeopleSort.LATEST -> sortedBy { p -> p.media.maxOfOrNull { it.importedAt } ?: 0L }
        PeopleSort.ADDED -> sortedBy { it.entity.createdAt }
    }
    return (if (ascending) sorted else sorted.reversed()).sortedWith(compareBy({ it.entity.ignored }, { !it.entity.favorite }))
}

/** The named, not hidden people whose name contains [query] (ignoring case); [this] keeps its order, so starred stay first. */
fun List<FaceRepository.Person>.matching(query: String): List<FaceRepository.Person> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return filter { !it.entity.ignored && it.entity.name.contains(q, ignoreCase = true) }
}

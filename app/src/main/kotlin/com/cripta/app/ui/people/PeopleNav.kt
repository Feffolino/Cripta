package com.cripta.app.ui.people

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One-shot "open the People page" requests (Settings, Home, the scan's notification), handled by the nav host. */
object PeopleNav {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open

    fun request() { _open.value = true }
    fun consume() { _open.value = false }

    private val _person = MutableStateFlow<Long?>(null)
    /** A person's profile asked for (Home, the people page). */
    val person: StateFlow<Long?> = _person
    fun openPerson(id: Long) { _person.value = id }
    fun consumePerson() { _person.value = null }
}

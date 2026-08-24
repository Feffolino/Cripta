package com.cripta.app.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repo: VaultRepository,
) : ViewModel() {

    /** Returns (name, text) for an existing note, or null for a new note. */
    suspend fun load(id: String?): Pair<String, String>? {
        if (id == null) return null
        val f = repo.fileById(id) ?: return null
        return f.originalName to repo.noteText(f)
    }

    fun save(id: String?, name: String, text: String, onDone: () -> Unit) = viewModelScope.launch {
        if (id == null) repo.createNote(null, name, text) else repo.updateNote(id, name, text)
        onDone()
    }
}

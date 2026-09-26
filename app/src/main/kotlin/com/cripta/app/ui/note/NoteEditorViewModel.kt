package com.cripta.app.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val thumbs: com.cripta.app.media.ThumbnailLoader,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Folder a NEW note is created in, from the optional "folder" nav argument
     *  (route "note/new?folder={folder}"); null = vault root. */
    private val routeFolderId: Long? =
        savedState.get<Any?>("folder")?.toString()?.toLongOrNull()

    // The draft lives here (not in rememberSaveable): it survives rotation and the lock overlay,
    // but is never written into the saved-instance Bundle, which the system may persist in clear.
    var name by mutableStateOf("")
    var text by mutableStateOf("")
    var loaded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    /**
     * The existing note couldn't be read. The editor then shows empty fields with an empty
     * baseline, so anything typed would look "dirty" and saving would overwrite the real note
     * with it: saving stays disabled for this note.
     */
    var loadFailed by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    private var savedName = ""
    private var savedText = ""
    private var loadedFor: String? = null

    /** True when the draft differs from what is stored (new note: anything typed). */
    val dirty: Boolean get() = name != savedName || text != savedText

    /** Loads an existing note once per id (a recomposition or rotation must not overwrite the draft). */
    suspend fun load(id: String?) {
        val key = id ?: "<new>"
        if (loadedFor == key) return
        loadedFor = key
        if (id != null) {
            try {
                val f = repo.fileById(id)
                if (f == null) {
                    // Gone (deleted elsewhere): nothing to edit, and an update would have no target.
                    loadFailed = true
                    error = "Impossibile aprire la nota."
                } else {
                    val t = repo.noteText(f)
                    name = f.originalName; text = t
                    savedName = f.originalName; savedText = t
                }
            } catch (e: CancellationException) {
                loadedFor = null; throw e
            } catch (_: Exception) {
                loadFailed = true
                error = "Impossibile aprire la nota."
            }
        }
        loaded = true
    }

    /**
     * Save and then [onDone]. A new note goes into [folderId] (screen parameter) or else the folder
     * from the route. On failure the editor stays open with the draft intact.
     */
    fun save(id: String?, folderId: Long?, onDone: () -> Unit) {
        if (saving) return
        // Never write over a note whose content we failed to read (see [loadFailed]).
        if (loadFailed) return
        saving = true
        viewModelScope.launch {
            try {
                val n = name.ifBlank { "Nota" }
                if (id == null) repo.createNote(folderId ?: routeFolderId, n, text) else repo.updateNote(id, n, text)
                savedName = name; savedText = text
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Impossibile salvare la nota. Riprova."
            } finally {
                saving = false
            }
        }
    }

    /** Delete an existing note honouring the trash setting; [onDone] gets true when trashed. */
    fun delete(id: String, onDone: (trashed: Boolean) -> Unit) = viewModelScope.launch {
        try {
            val trashed = repo.deleteOrTrash(id)
            if (!trashed) thumbs.evict(id)
            onDone(trashed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Impossibile eliminare la nota."
        }
    }
}

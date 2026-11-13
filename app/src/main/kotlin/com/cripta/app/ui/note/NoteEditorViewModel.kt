package com.cripta.app.ui.note

import com.cripta.app.ui.safeScope
import com.cripta.app.R
import com.cripta.app.str

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
    private val sharedLinks: com.cripta.app.data.SharedLinkStore,
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
    /** A new note already written by [saveInBackground]: later saves update it, never create another. */
    private var createdId: String? = null

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
                    error = str(R.string.note_open_failed)
                } else {
                    val t = repo.noteText(f)
                    // An imported "x.md" is titled "x": the extension comes back on export.
                    name = f.originalName.replace(Regex("\\.(md|markdown)$", RegexOption.IGNORE_CASE), ""); text = t
                    savedName = name; savedText = t
                }
            } catch (e: CancellationException) {
                loadedFor = null; throw e
            } catch (_: Exception) {
                loadFailed = true
                error = str(R.string.note_open_failed)
            }
        }
        loaded = true
    }

    /** Hands a link of the note to the downloader (AppRoot opens its tab); the user picks the quality there. */
    fun sendToDownloader(url: String) = sharedLinks.set(url)

    /**
     * Save and then [onDone]. A new note goes into [folderId] (screen parameter) or else the folder
     * from the route. On failure the editor stays open with the draft intact, and [onFailed] runs.
     */
    fun save(id: String?, folderId: Long?, onFailed: () -> Unit = {}, onDone: () -> Unit) {
        if (saving) return
        // Never write over a note whose content we failed to read (see [loadFailed]).
        if (loadFailed) return
        saving = true
        safeScope.launch {
            try {
                // What is written is what "saved" means: anything typed meanwhile is not saved yet.
                val writtenName = name; val writtenText = text
                val n = writtenName.ifBlank { str(R.string.default_note_name) }
                val target = id ?: createdId
                if (target == null) createdId = repo.createNote(folderId ?: routeFolderId, n, writtenText).id
                else {
                    repo.updateNote(target, n, writtenText)
                    // Its cover shows its first lines: drawn again from the new text.
                    thumbs.evict(target)
                }
                savedName = writtenName; savedText = writtenText
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = str(R.string.note_save_failed)
                onFailed()
            } finally {
                saving = false
            }
        }
    }

    /**
     * The app goes to the background with the editor open: the changed draft is written to the
     * vault, the editor stays open. Android may close Cripta there, and the draft only lives here.
     * Nothing is written for a note that could not be read, an unchanged draft or an empty new note.
     */
    fun saveInBackground(id: String?, folderId: Long?, onFailed: () -> Unit = {}) {
        if (!loaded || loadFailed || saving || !dirty) return
        if (id == null && emptyNew) return
        save(id, folderId, onFailed) {}
    }

    /** A new note with nothing typed and not written yet: leaving it writes nothing. */
    val emptyNew: Boolean get() = createdId == null && name.isBlank() && text.isBlank()

    /** Delete an existing note honouring the trash setting; [onDone] gets true when trashed. */
    fun delete(id: String, onDone: (trashed: Boolean) -> Unit) = safeScope.launch {
        try {
            val trashed = repo.deleteOrTrash(id)
            if (!trashed) thumbs.evict(id)
            onDone(trashed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = str(R.string.note_delete_failed)
        }
    }
}

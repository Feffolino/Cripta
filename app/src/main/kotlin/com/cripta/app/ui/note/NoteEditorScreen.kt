package com.cripta.app.ui.note

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.viewer.DelayedSpinner

/**
 * Note editor. Leaving it (system Back or the arrow) saves the draft when it changed, so a Back
 * press never discards typed text. [folderId] is where a NEW note is created (null = the "folder"
 * route argument, else the vault root). [onDeleted] runs after the note is deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    fileId: String?,
    onBack: () -> Unit,
    folderId: Long? = null,
    onDeleted: () -> Unit = onBack,
    vm: NoteEditorViewModel = hiltViewModel(),
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(fileId) { vm.load(fileId) }
    LaunchedEffect(vm.error) {
        vm.error?.let { snackbar.showSnackbar(it); vm.error = null }
    }

    // Save on the way out; an unchanged draft (or an empty new note) just closes.
    val leave: () -> Unit = {
        when {
            vm.saving -> Unit
            !vm.loaded || !vm.dirty -> onBack()
            fileId == null && vm.name.isBlank() && vm.text.isBlank() -> onBack()
            else -> vm.save(fileId, folderId) { onBack() }
        }
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fileId == null) "Nuova nota" else "Modifica nota") },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salva e chiudi") } },
                actions = {
                    if (fileId != null) {
                        IconButton(onClick = { confirmDelete = true }, enabled = vm.loaded && !vm.saving) {
                            Icon(Icons.Filled.Delete, "Elimina nota")
                        }
                    }
                    IconButton(onClick = { vm.save(fileId, folderId) { onBack() } }, enabled = vm.loaded && !vm.saving) {
                        Icon(Icons.Filled.Check, "Salva")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
    ) { pad ->
        AnimatedContent(
            targetState = vm.loaded,
            transitionSpec = { fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit(Motion.MEDIUM)) },
            modifier = Modifier.fillMaxSize().padding(pad),
            label = "noteLoaded",
        ) { ready ->
            if (!ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedSpinner() }
            } else {
                // imePadding: the window is edge-to-edge, so without it the keyboard (tall in landscape)
                // would cover the text being typed. consumeWindowInsets first, so the navigation-bar
                // inset already applied by the Scaffold isn't added a second time above the keyboard.
                Column(Modifier.fillMaxSize().consumeWindowInsets(pad).imePadding().padding(16.dp)) {
                    OutlinedTextField(
                        value = vm.name, onValueChange = { vm.name = it },
                        label = { Text("Titolo") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = vm.text, onValueChange = { vm.text = it },
                        label = { Text("Contenuto") },
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete && fileId != null) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la nota?") },
            text = { Text("\"${vm.name.ifBlank { "Nota" }}\" verrà eliminata (nel cestino se attivo in Impostazioni).") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(fileId) { onDeleted() } }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

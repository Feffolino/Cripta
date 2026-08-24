package com.cripta.app.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    fileId: String?,
    onBack: () -> Unit,
    vm: NoteEditorViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(fileId == null) }

    LaunchedEffect(fileId) {
        vm.load(fileId)?.let { (n, t) -> name = n; text = t }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fileId == null) "Nuova nota" else "Modifica nota") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = {
                        vm.save(fileId, name.ifBlank { "Nota" }, text) { onBack() }
                    }) { Icon(Icons.Filled.Check, "Salva") }
                },
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Titolo") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Contenuto") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

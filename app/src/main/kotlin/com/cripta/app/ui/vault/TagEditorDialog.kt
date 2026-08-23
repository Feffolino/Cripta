package com.cripta.app.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.TagEntity

/**
 * Tag editor driven by chips: existing tags toggle on/off, new tags added via a field + button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    allTags: List<TagEntity>,
    initialSelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected: SnapshotStateList<String> = remember {
        initialSelected.map { it.trim() }.filter { it.isNotEmpty() }.toMutableStateList()
    }
    var newTag by remember { mutableStateOf("") }

    // Union of known tags and any freshly added ones, for the chip list.
    val known = remember(allTags, selected.size) {
        (allTags.map { it.name } + selected).distinct().sortedBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (known.isEmpty()) {
                    Text("Nessun tag ancora. Aggiungine uno qui sotto.")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    known.forEach { name ->
                        val isSel = selected.any { it.equals(name, ignoreCase = true) }
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (isSel) selected.removeAll { it.equals(name, ignoreCase = true) }
                                else selected.add(name)
                            },
                            label = { Text("#$name") },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("Nuovo tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val t = newTag.trim()
                            if (t.isNotEmpty() && selected.none { it.equals(t, ignoreCase = true) }) {
                                selected.add(t)
                            }
                            newTag = ""
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Icon(Icons.Filled.Add, "Aggiungi tag") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toList()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

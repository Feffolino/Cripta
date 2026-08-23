package com.cripta.app.ui.vault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.TagEntity

/**
 * Tag editor via chips: tap to toggle a tag on the file, long-press a chip to set its
 * alias (emoji/acronym) shown on thumbnails, and add new tags via the field + button.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TagEditorDialog(
    allTags: List<TagEntity>,
    initialSelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onSetAlias: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected: SnapshotStateList<String> = remember {
        initialSelected.map { it.trim() }.filter { it.isNotEmpty() }.toMutableStateList()
    }
    var newTag by remember { mutableStateOf("") }
    var aliasTarget by remember { mutableStateOf<String?>(null) }

    val aliasByName = remember(allTags) { allTags.associate { it.name to it.alias } }
    val known = remember(allTags, selected.size) {
        (allTags.map { it.name } + selected).distinct().sortedBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etichette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tocca per assegnare. Tieni premuto per impostare un alias (emoji/acronimo).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    known.forEach { name ->
                        val isSel = selected.any { it.equals(name, ignoreCase = true) }
                        val alias = aliasByName[name]
                        val label = if (!alias.isNullOrBlank()) "$alias #$name" else "#$name"
                        Surface(
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (isSel) selected.removeAll { it.equals(name, ignoreCase = true) }
                                    else selected.add(name)
                                },
                                onLongClick = { aliasTarget = name },
                            ),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            if (t.isNotEmpty() && selected.none { it.equals(t, ignoreCase = true) }) selected.add(t)
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

    aliasTarget?.let { name ->
        TextPromptDialog(
            title = "Alias per #$name",
            label = "Emoji o acronimo",
            initial = aliasByName[name] ?: "",
            onConfirm = { onSetAlias(name, it); aliasTarget = null },
            onDismiss = { aliasTarget = null },
        )
    }
}

package com.cripta.app.ui.vault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.TagEntity

/** Quick-pick emojis offered when creating/editing a label alias. */
private val QUICK_EMOJIS = listOf(
    "⭐", "🔒", "❤️", "📌", "🏷️", "📁", "📄", "🖼️", "🎬", "🎵",
    "📷", "🔑", "💼", "🎨", "🎁", "✅", "🔥", "💡", "🧾", "🌍",
)

/** One reusable chip used by the tag dialogs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun chipLabel(name: String, alias: String?): String =
    if (!alias.isNullOrBlank()) "$alias #$name" else "#$name"

/**
 * Create/edit a label choosing a name and, right away, an emoji/acronym alias.
 * The alias is shown as a compact badge on thumbnails.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LabelEditorDialog(
    title: String,
    initialName: String = "",
    initialAlias: String = "",
    onConfirm: (name: String, alias: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var alias by remember { mutableStateOf(initialAlias) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = alias, onValueChange = { alias = it.take(6) },
                    label = { Text("Emoji o acronimo (opzionale)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Suggerimenti", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QUICK_EMOJIS.forEach { e ->
                        Surface(
                            color = if (alias == e) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.combinedClickable(onClick = { alias = e }),
                        ) {
                            Text(e, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), alias.trim().ifEmpty { null }) },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/**
 * Tag editor for a single file: tap to toggle, long-press a chip to edit its alias,
 * "Nuova etichetta" to create one with an emoji/acronym in one step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    allTags: List<TagEntity>,
    initialSelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onSetAlias: (String, String?) -> Unit,
    onCreateTag: (name: String, alias: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected: SnapshotStateList<String> = remember {
        initialSelected.map { it.trim() }.filter { it.isNotEmpty() }.toMutableStateList()
    }
    var aliasTarget by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val aliasByName = remember(allTags) { allTags.associate { it.name to it.alias } }
    val known = remember(allTags, selected.size) {
        (allTags.map { it.name } + selected).distinct().sortedBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etichette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tocca per assegnare. Tieni premuto per modificare l'alias (emoji/acronimo).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    known.forEach { name ->
                        val isSel = selected.any { it.equals(name, ignoreCase = true) }
                        TagChip(
                            label = chipLabel(name, aliasByName[name]),
                            selected = isSel,
                            onClick = {
                                if (isSel) selected.removeAll { it.equals(name, ignoreCase = true) }
                                else selected.add(name)
                            },
                            onLongClick = { aliasTarget = name },
                        )
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 4.dp))
                    Text("Nuova etichetta")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toList()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )

    aliasTarget?.let { name ->
        LabelEditorDialog(
            title = "Modifica #$name",
            initialName = name,
            initialAlias = aliasByName[name] ?: "",
            onConfirm = { newName, alias ->
                // Name edits on an existing tag are out of scope here; keep the name, set the alias.
                onSetAlias(name, alias)
                aliasTarget = null
            },
            onDismiss = { aliasTarget = null },
        )
    }

    if (creating) {
        LabelEditorDialog(
            title = "Nuova etichetta",
            onConfirm = { name, alias ->
                onCreateTag(name, alias)
                if (selected.none { it.equals(name, ignoreCase = true) }) selected.add(name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/**
 * Batch tag editor: pick labels to ADD to every selected file (existing tags are kept).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchTagDialog(
    count: Int,
    allTags: List<TagEntity>,
    onConfirm: (List<String>) -> Unit,
    onCreateTag: (name: String, alias: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val toAdd: SnapshotStateList<String> = remember { mutableStateListOf() }
    var creating by remember { mutableStateOf(false) }
    val aliasByName = remember(allTags) { allTags.associate { it.name to it.alias } }
    val known = remember(allTags, toAdd.size) {
        (allTags.map { it.name } + toAdd).distinct().sortedBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etichette per $count elementi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Le etichette scelte vengono aggiunte a tutti gli elementi selezionati.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (known.isEmpty()) {
                    Text("Nessuna etichetta. Creane una qui sotto.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    known.forEach { name ->
                        val isSel = toAdd.any { it.equals(name, ignoreCase = true) }
                        TagChip(
                            label = chipLabel(name, aliasByName[name]),
                            selected = isSel,
                            onClick = {
                                if (isSel) toAdd.removeAll { it.equals(name, ignoreCase = true) }
                                else toAdd.add(name)
                            },
                        )
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 4.dp))
                    Text("Nuova etichetta")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = toAdd.isNotEmpty(), onClick = { onConfirm(toAdd.toList()) }) { Text("Applica") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )

    if (creating) {
        LabelEditorDialog(
            title = "Nuova etichetta",
            onConfirm = { name, alias ->
                onCreateTag(name, alias)
                if (toAdd.none { it.equals(name, ignoreCase = true) }) toAdd.add(name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

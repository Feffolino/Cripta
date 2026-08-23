package com.cripta.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.vault.TextPromptDialog
import com.cripta.app.ui.vault.tagAlias

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.settings.collectAsState()
    val tags by vm.tags.collectAsState()

    var renameTag by remember { mutableStateOf<TagEntity?>(null) }
    var aliasTag by remember { mutableStateOf<TagEntity?>(null) }
    var deleteTag by remember { mutableStateOf<TagEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Blocco automatico") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 5, 15).forEach { m ->
                        FilterChip(
                            selected = s.autoLockMinutes == m,
                            onClick = { vm.setAutoLock(m) },
                            label = { Text(if (m == 0) "Subito" else "$m min") },
                        )
                    }
                }
            }

            Section("Originale dopo import") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeleteOriginalPolicy.entries.forEach { p ->
                        FilterChip(
                            selected = s.deleteOriginalPolicy == p,
                            onClick = { vm.setDeletePolicy(p) },
                            label = {
                                Text(when (p) {
                                    DeleteOriginalPolicy.ASK -> "Chiedi"
                                    DeleteOriginalPolicy.ALWAYS -> "Elimina"
                                    DeleteOriginalPolicy.NEVER -> "Mantieni"
                                })
                            },
                        )
                    }
                }
            }

            Section("Etichette") {
                if (tags.isEmpty()) {
                    Text("Nessuna etichetta.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tags.forEach { tag ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${tagAlias(tag)}  #${tag.name}", Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { aliasTag = tag }) { Icon(Icons.Filled.Label, "Alias") }
                            IconButton(onClick = { renameTag = tag }) { Icon(Icons.Filled.DriveFileRenameOutline, "Rinomina") }
                            IconButton(onClick = { deleteTag = tag }) { Icon(Icons.Filled.Delete, "Elimina") }
                        }
                    }
                }
            }

            Button(onClick = { vm.lockNow(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Blocca ora")
            }

            Section("Sicurezza — limiti") {
                Text(
                    "Cripta protegge da curiosi occasionali. Non è pensato contro analisi forense o " +
                        "dispositivi con root. L'eliminazione usa crypto-shredding (distrugge la chiave del file); " +
                        "la cancellazione fisica su memoria flash non è garantita dal sistema. Nessun permesso di rete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    renameTag?.let { tag ->
        TextPromptDialog("Rinomina etichetta", "Nome", initial = tag.name,
            onConfirm = { vm.renameTag(tag.id, it); renameTag = null },
            onDismiss = { renameTag = null })
    }
    aliasTag?.let { tag ->
        TextPromptDialog("Alias per #${tag.name}", "Emoji o acronimo", initial = tag.alias ?: "",
            onConfirm = { vm.setTagAlias(tag.name, it); aliasTag = null },
            onDismiss = { aliasTag = null })
    }
    deleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleteTag = null },
            title = { Text("Eliminare l'etichetta?") },
            text = { Text("\"#${tag.name}\" verrà rimossa da tutti i file.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTag(tag.id); deleteTag = null }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTag = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

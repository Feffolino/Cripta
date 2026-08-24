package com.cripta.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import com.cripta.app.data.SortKey
import com.cripta.app.data.ViewMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.vault.LabelEditorDialog
import com.cripta.app.ui.vault.tagAlias

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.settings.collectAsState()
    val tags by vm.tags.collectAsState()
    val message by vm.message.collectAsState()
    val ctx = LocalContext.current
    val pkgInfo = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    LaunchedEffect(message) {
        message?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show(); vm.clearMessage() }
    }

    var pendingExport by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> pendingExport = uri }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    var editTag by remember { mutableStateOf<TagEntity?>(null) }
    var deleteTag by remember { mutableStateOf<TagEntity?>(null) }
    var addTag by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Section("Aspetto", "Tema e colori dell'app.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.cripta.app.data.ThemeMode.entries.forEach { m ->
                            FilterChip(
                                selected = s.themeMode == m,
                                onClick = { vm.setThemeMode(m) },
                                label = {
                                    Text(when (m) {
                                        com.cripta.app.data.ThemeMode.SYSTEM -> "Sistema"
                                        com.cripta.app.data.ThemeMode.LIGHT -> "Chiaro"
                                        com.cripta.app.data.ThemeMode.DARK -> "Scuro"
                                    })
                                },
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Colori dinamici (Material You)", Modifier.weight(1f))
                        Switch(checked = s.dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
                    }
                }
            }

            item {
                Section("Dettagli visualizzati", "Scegli quali informazioni mostrare su file e cartelle.") {
                    ToggleRow("Dimensione e durata", s.display.showFileInfo) { vm.setShowFileInfo(it) }
                    ToggleRow("Tag sulle copertine", s.display.showTagsOnCover) { vm.setShowTagsOnCover(it) }
                    ToggleRow("Intestazioni per data", s.display.showDateHeaders) { vm.setShowDateHeaders(it) }
                    ToggleRow("Dettagli cartelle (conteggio e peso)", s.display.showFolderInfo) { vm.setShowFolderInfo(it) }
                }
            }

            item {
                Section("Blocco automatico", "Blocca il vault quando l'app resta in background per il tempo scelto.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1, 0, 1, 5, 15, 30).forEach { m ->
                            FilterChip(
                                selected = s.autoLockMinutes == m,
                                onClick = { vm.setAutoLock(m) },
                                label = { Text(when (m) { -1 -> "Mai"; 0 -> "Subito"; else -> "$m min" }) },
                            )
                        }
                    }
                }
            }

            item {
                Section("Originale dopo import", "Cosa fare del file originale sul dispositivo dopo averlo cifrato nel vault.") {
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
            }

            item {
                Text("Etichette", style = MaterialTheme.typography.titleMedium)
            }
            item {
                TextButton(onClick = { addTag = true }) { Text("+ Aggiungi etichetta") }
            }
            if (tags.isEmpty()) {
                item {
                    Text("Nessuna etichetta.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(tags, key = { it.id }) { tag ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${tagAlias(tag)}  #${tag.name}", Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { editTag = tag }) { Icon(Icons.Filled.Edit, "Modifica") }
                        IconButton(onClick = { deleteTag = tag }) { Icon(Icons.Filled.Delete, "Elimina") }
                    }
                }
            }

            item {
                Section("Backup cifrato") {
                    Text("Esporta/importa un archivio cifrato del vault, protetto da una passphrase. " +
                        "Ripristinabile anche su un altro dispositivo. La sicurezza dipende dalla passphrase.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("cripta-backup.criptabak") }, modifier = Modifier.weight(1f)) {
                            Text("Esporta")
                        }
                        Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                            Text("Ripristina")
                        }
                    }
                }
            }

            item {
                Button(onClick = { vm.lockNow(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Blocca ora")
                }
            }

            item {
                Section("Privacy") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Consenti screenshot")
                            Text("Se disattivato, blocca gli screenshot e nasconde l'app nelle app recenti.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = s.allowScreenshots, onCheckedChange = { vm.setAllowScreenshots(it) })
                    }
                }
            }

            item {
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

            item {
                Section("Informazioni") {
                    InfoRow("Versione", pkgInfo?.versionName ?: "—")
                    InfoRow("Build", pkgInfo?.longVersionCode?.toString() ?: "—")
                    InfoRow("Pacchetto", ctx.packageName)
                    InfoRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                }
            }
        }
    }

    if (addTag) {
        LabelEditorDialog(
            title = "Nuova etichetta",
            onConfirm = { name, alias -> vm.createTag(name, alias); addTag = false },
            onDismiss = { addTag = false },
        )
    }
    if (pendingExport != null || pendingImport != null) {
        PassphraseDialog(
            title = if (pendingExport != null) "Passphrase del backup" else "Passphrase del ripristino",
            onConfirm = { pass ->
                pendingExport?.let { vm.exportBackup(it, pass) }
                pendingImport?.let { vm.importBackup(it, pass) }
                pendingExport = null; pendingImport = null
            },
            onDismiss = { pendingExport = null; pendingImport = null },
        )
    }
    editTag?.let { tag ->
        LabelEditorDialog(
            title = "Modifica etichetta",
            initialName = tag.name,
            initialAlias = tag.alias ?: "",
            onConfirm = { name, alias -> vm.editTag(tag.id, name, alias); editTag = null },
            onDismiss = { editTag = null },
        )
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
private fun PassphraseDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Passphrase (min 6)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        confirmButton = { TextButton(onClick = { if (pass.length >= 6) onConfirm(pass) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

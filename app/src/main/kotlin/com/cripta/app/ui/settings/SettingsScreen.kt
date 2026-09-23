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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val updateState by vm.update.collectAsState()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
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

    val dupMode by vm.dupMode.collectAsState()
    val dupScanning by vm.dupScanning.collectAsState()
    val dupProgress by vm.dupProgress.collectAsState()
    val dupScannedCount by vm.dupScannedCount.collectAsState()
    val exactGroups by vm.exactGroups.collectAsState()
    val dupGroups by vm.dupGroups.collectAsState()
    val dupNotice by vm.dupNotice.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { com.cripta.app.ui.components.HeaderTitle("Impostazioni") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
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
                Section("Pulsanti azione", "Nascondi i pulsanti flottanti che non usi.") {
                    ToggleRow("Pulsante nuova nota", s.display.showNoteFab) { vm.setShowNoteFab(it) }
                    ToggleRow("Pulsante casuale", s.display.showRandomFab) { vm.setShowRandomFab(it) }
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
                Section("Etichette", "Crea, riordina e rinomina le etichette dei file.") {
                    val custom = s.tagSortMode == com.cripta.app.data.TagSortMode.CUSTOM
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !custom,
                            onClick = { vm.setTagSortMode(com.cripta.app.data.TagSortMode.ALPHA) },
                            label = { Text("Alfabetico") })
                        FilterChip(selected = custom,
                            onClick = { vm.setTagSortMode(com.cripta.app.data.TagSortMode.CUSTOM) },
                            label = { Text("Personalizzato") })
                    }
                    if (tags.isEmpty()) {
                        Text("Nessuna etichetta.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        tags.forEachIndexed { index, tag ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${tagAlias(tag)}  #${tag.name}", Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (custom) {
                                    IconButton(onClick = { vm.moveTag(tag.id, up = true) }, enabled = index > 0) {
                                        Icon(Icons.Filled.KeyboardArrowUp, "Su")
                                    }
                                    IconButton(onClick = { vm.moveTag(tag.id, up = false) }, enabled = index < tags.size - 1) {
                                        Icon(Icons.Filled.KeyboardArrowDown, "Giù")
                                    }
                                }
                                IconButton(onClick = { editTag = tag }) { Icon(Icons.Filled.Edit, "Modifica") }
                                IconButton(onClick = { deleteTag = tag }) { Icon(Icons.Filled.Delete, "Elimina") }
                            }
                        }
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { addTag = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Filled.Add, null); Text("  Aggiungi etichetta") }
                }
            }

            item {
                Section("Backup cifrato") {
                    Text("Esporta/importa un archivio cifrato del vault, protetto da una passphrase. " +
                        "Ripristinabile anche su un altro dispositivo. La sicurezza dipende dalla passphrase.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilledTonalButton(onClick = { exportLauncher.launch("cripta-backup.criptabak") }, modifier = Modifier.weight(1f)) {
                            Text("Esporta")
                        }
                        androidx.compose.material3.FilledTonalButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                            Text("Ripristina")
                        }
                    }
                }
            }

            item {
                Section("File duplicati", "Trova file identici o immagini simili per liberare spazio. Tutto avviene sul dispositivo, sui dati decifrati in memoria.") {
                    if (dupScanning) {
                        val (done, total) = dupProgress
                        Text(if (total > 0) "Scansione… $done/$total" else "Scansione…",
                            style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.FilledTonalButton(onClick = { vm.cancelScan() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Annulla")
                        }
                    } else {
                        androidx.compose.material3.FilledTonalButton(onClick = { vm.scanExact() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Duplicati esatti")
                        }
                        androidx.compose.material3.FilledTonalButton(onClick = { vm.scanSimilar() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Media simili")
                        }
                        Text("Esatti: file byte-identici (qualsiasi tipo). Simili: foto e video uguali anche se ri-salvati, ri-codificati o ridimensionati (i video vengono decifrati temporaneamente per campionare i fotogrammi).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Section("Aggiornamenti", "Scarica l'ultima versione pubblicata.") {
                    when (val u = updateState) {
                        SettingsViewModel.UpdateState.Checking ->
                            Text("Controllo in corso…", style = MaterialTheme.typography.bodyMedium)
                        SettingsViewModel.UpdateState.UpToDate ->
                            Text("Sei alla versione più recente.", style = MaterialTheme.typography.bodyMedium)
                        is SettingsViewModel.UpdateState.Available -> {
                            Text("Disponibile: ${u.release.versionName} (${com.cripta.app.ui.components.formatBytes(u.release.sizeBytes)})",
                                style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { vm.downloadUpdate(ctx) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Scarica e installa")
                            }
                        }
                        is SettingsViewModel.UpdateState.Downloading -> {
                            Text("Download: ${u.pct}%", style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { u.pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                        is SettingsViewModel.UpdateState.Error ->
                            Text("Errore: ${u.message}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                        SettingsViewModel.UpdateState.Idle -> {}
                    }
                    if (updateState !is SettingsViewModel.UpdateState.Downloading) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { vm.checkUpdate(ctx) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Controlla aggiornamenti")
                        }
                    }
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
    when (dupMode) {
        SettingsViewModel.DupMode.EXACT -> DuplicatesCompare(
            title = "Duplicati esatti",
            groups = dupGroups,
            summary = buildString {
                append("Scansionati $dupScannedCount file.")
                val waste = exactGroups.sumOf { it.sizeBytes * (it.files.size - 1) }
                if (waste > 0) append(" Recuperabili ${com.cripta.app.ui.components.formatBytes(waste)} eliminando le copie in eccesso.")
                append(" Le etichette delle copie eliminate passano a quella tenuta.")
            },
            emptyText = "Scansionati $dupScannedCount file. Nessun duplicato esatto trovato.",
            notice = dupNotice,
            thumb = { vm.thumb(it) },
            channelFor = { vm.channelFor(it) },
            imageBytes = { vm.imageBytes(it) },
            onKeepOnly = { vm.keepOnly(it) },
            onDelete = { vm.deleteDuplicate(it) },
            onClearNotice = { vm.clearDupNotice() },
            onDismiss = { vm.closeDuplicates() },
        )
        SettingsViewModel.DupMode.SIMILAR -> DuplicatesCompare(
            title = "Media simili",
            groups = dupGroups,
            summary = "Analizzati $dupScannedCount elementi (foto e video). Confronta le copie: il consigliato è " +
                "quello di qualità migliore. Le etichette delle copie eliminate passano a quella tenuta.",
            emptyText = "Analizzati $dupScannedCount elementi. Nessun media simile trovato.",
            notice = dupNotice,
            thumb = { vm.thumb(it) },
            channelFor = { vm.channelFor(it) },
            imageBytes = { vm.imageBytes(it) },
            onKeepOnly = { vm.keepOnly(it) },
            onDelete = { vm.deleteDuplicate(it) },
            onClearNotice = { vm.clearDupNotice() },
            onDismiss = { vm.closeDuplicates() },
        )
        SettingsViewModel.DupMode.NONE -> Unit
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
    // Grouped, elevated card: gives the flat settings list real depth and binds each group's
    // controls together (iOS-style grouped list / M3 setting card).
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
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

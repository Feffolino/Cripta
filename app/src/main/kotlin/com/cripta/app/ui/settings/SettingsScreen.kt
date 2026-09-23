package com.cripta.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val dupWaiting by vm.dupResultWaiting.collectAsState()

    var showAllTags by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    val trashed by vm.trashed.collectAsState()

    // Two-level navigation: the category list, or one category page. Back returns to the list.
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    /** "Mostra altre opzioni" state of the page being shown (opened by a search hit). */
    var showAdvanced by remember(page) { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf<SettingsPage?>(null) }
    val current = page?.let { p -> SettingsPage.entries.firstOrNull { it.name == p } }
    androidx.activity.compose.BackHandler(enabled = current != null) { page = null }
    // Requests from elsewhere (Home "Strumenti", duplicate-scan notification).
    val requestedPage by vm.requestedPage.collectAsState()
    LaunchedEffect(requestedPage) {
        requestedPage?.let { page = it; vm.consumeRequestedPage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.label ?: "Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = { if (current != null) page = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                },
                actions = {
                    if (current != null && current.resettable) {
                        TextButton(onClick = { confirmReset = current }) { Text("Predefiniti") }
                    }
                    // Quick lock, always at hand.
                    IconButton(onClick = { vm.lockNow(); onBack() }) { Icon(Icons.Filled.Lock, "Blocca ora") }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { pad ->
        if (current == null) {
            MainSettingsList(
                modifier = Modifier.fillMaxSize().padding(pad),
                query = query,
                onQuery = { query = it },
                summaries = SettingsPage.entries.associateWith { pageSummary(it, s, tags.size, trashed.size, dupWaiting?.groups?.size) },
                onOpen = { p, advanced -> page = p.name; showAdvanced = advanced; query = "" },
                onLock = { vm.lockNow(); onBack() },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (current) {
                    SettingsPage.ASPETTO -> {
                        item {
                            Section("Tema", "Tema e colori dell'app.") {
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
                                ToggleRow("Colori dinamici (Material You)", s.dynamicColor) { vm.setDynamicColor(it) }
                            }
                        }
                        item {
                            Section("Nel vault") {
                                ToggleRow("Riepilogo conteggi sopra la griglia", s.display.showStatsStrip) { vm.setShowStatsStrip(it) }
                                ToggleRow("Intestazioni per data", s.display.showDateHeaders) { vm.setShowDateHeaders(it) }
                                Text("Vista, colonne e ordinamento si cambiano direttamente dal vault (icone in alto e pannello filtri).",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item { MoreOptions(showAdvanced) { showAdvanced = it } }
                        if (showAdvanced) item {
                            Section("Altre opzioni") {
                                ToggleRow("Dimensione e durata sotto i file", s.display.showFileInfo) { vm.setShowFileInfo(it) }
                                ToggleRow("Dettagli cartelle (conteggio e peso)", s.display.showFolderInfo) { vm.setShowFolderInfo(it) }
                                ToggleRow("Pulsante nuova nota", s.display.showNoteFab) { vm.setShowNoteFab(it) }
                                ToggleRow("Pulsante casuale", s.display.showRandomFab) { vm.setShowRandomFab(it) }
                            }
                        }
                    }

                    SettingsPage.COPERTINE -> {
                        // The live preview stays pinned on top while the options scroll under it.
                        stickyHeader {
                            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Bottom) {
                                    com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(132.dp))
                                    com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(92.dp))
                                    Text("Anteprima dal vivo", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            Section("Etichette sulla copertina") {
                                ToggleRow("Mostra le etichette", s.display.showTagsOnCover) { vm.setShowTagsOnCover(it) }
                                if (s.display.showTagsOnCover) {
                                    Text("Righe di etichette", style = MaterialTheme.typography.labelLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0 to "Auto", 1 to "1", 2 to "2", 3 to "3").forEach { (v, l) ->
                                            FilterChip(selected = s.display.coverTagRows == v, onClick = { vm.setCoverTagRows(v) }, label = { Text(l) })
                                        }
                                    }
                                    Text("Auto: fino a 3 righe sulle copertine grandi, 1 su quelle piccole.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ToggleRow("Un colore per ogni etichetta", s.display.tagColors) { vm.setTagColors(it) }
                                ToggleRow("Durata sui video (es. 3:12)", s.display.showDurationBadge) { vm.setShowDurationBadge(it) }
                            }
                        }
                        item { MoreOptions(showAdvanced) { showAdvanced = it } }
                        if (showAdvanced) item {
                            Section("Altre opzioni") {
                                Text("Testo delle etichette", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = s.display.coverTagStyle == com.cripta.app.data.CoverTagStyle.ALIAS,
                                        onClick = { vm.setCoverTagStyle(com.cripta.app.data.CoverTagStyle.ALIAS) },
                                        label = { Text("Sigla / emoji") })
                                    FilterChip(selected = s.display.coverTagStyle == com.cripta.app.data.CoverTagStyle.NAME,
                                        onClick = { vm.setCoverTagStyle(com.cripta.app.data.CoverTagStyle.NAME) },
                                        label = { Text("Nome intero") })
                                }
                                ToggleRow("Qualità sui video (4K / HD / SD)", s.display.showQualityBadge) { vm.setShowQualityBadge(it) }
                            }
                        }
                    }

                    SettingsPage.VIDEO -> {
                        item {
                            Section("Riproduzione") {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Riprendi da dove eri rimasto")
                                        Text("Riapre ogni video al punto in cui l'avevi lasciato e mostra una barra di avanzamento sulla copertina.",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = s.display.resumePlayback, onCheckedChange = { vm.setResumePlayback(it) })
                                }
                                ToggleRow("Ripeti il video in loop", s.videoLoop) { vm.setVideoLoop(it) }
                                ToggleRow("Rotazione automatica (orizzontale per i video orizzontali)", s.autoRotate) { vm.setAutoRotate(it) }
                                Text("Doppio tocco sui lati: salta di", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(5, 10, 30).forEach { v ->
                                        FilterChip(selected = s.seekStepSec == v, onClick = { vm.setSeekStep(v) }, label = { Text("$v s") })
                                    }
                                }
                            }
                        }
                        item { MoreOptions(showAdvanced) { showAdvanced = it } }
                        if (showAdvanced) {
                            item {
                                Section("Altre opzioni") {
                                    ToggleRow("Avvia senza audio", s.videoStartMuted) { vm.setVideoStartMuted(it) }
                                    ToggleRow("Luminosità e volume trascinando sui lati", s.gestureControls) { vm.setGestureControls(it) }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Picture-in-Picture")
                                            Text("Uscendo dall'app il video continua in una finestrella sopra le altre app: il contenuto resta visibile a chi guarda lo schermo.",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = s.pictureInPicture, onCheckedChange = { vm.setPictureInPicture(it) })
                                    }
                                }
                            }
                            item {
                                Section("Conversione in MP4", "La scelta viene chiesta alla prima conversione; qui puoi cambiarla.") {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            com.cripta.app.data.ConvertAfter.REPLACE to "Sostituisci l'originale",
                                            com.cripta.app.data.ConvertAfter.ASK to "Chiedi",
                                            com.cripta.app.data.ConvertAfter.KEEP_BOTH to "Tieni entrambi",
                                        ).forEach { (v, l) ->
                                            FilterChip(selected = s.convertAfter == v, onClick = { vm.setConvertAfter(v) }, label = { Text(l) })
                                        }
                                    }
                                    Text(
                                        when (s.convertAfter) {
                                            com.cripta.app.data.ConvertAfter.REPLACE ->
                                                "L'MP4 prende il posto dell'originale (cartella, etichette, posizione, copertina); l'originale va nel cestino per ${s.trashDays} giorni."
                                            com.cripta.app.data.ConvertAfter.ASK -> "A fine conversione ti viene chiesto se eliminare l'originale."
                                            com.cripta.app.data.ConvertAfter.KEEP_BOTH -> "Restano sia l'originale sia la copia MP4."
                                        },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.ETICHETTE -> {
                        item {
                            Section("Libreria etichette", "${tags.size} etichette · tocca il pallino per colore e 📌.") {
                                if (tags.isEmpty()) {
                                    Text("Nessuna etichetta.", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    val custom = s.tagSortMode == com.cripta.app.data.TagSortMode.CUSTOM
                                    // Long libraries are collapsed so the page stays short.
                                    val visible = if (showAllTags || tags.size <= 8) tags else tags.take(8)
                                    visible.forEachIndexed { index, tag ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                    .clickable { editTag = tag },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                androidx.compose.foundation.layout.Box(
                                                    Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(com.cripta.app.ui.theme.tagColor(tag)))
                                            }
                                            Text((if (tag.pinned) "📌 " else "") + "${tagAlias(tag)}  #${tag.name}", Modifier.weight(1f).padding(start = 10.dp),
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
                                    if (tags.size > 8) {
                                        TextButton(onClick = { showAllTags = !showAllTags }) {
                                            Text(if (showAllTags) "Mostra meno" else "Mostra tutte (${tags.size})")
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
                            Section("Assegnazione rapida", "La libreria resta sempre nello stesso ordine; cambiano solo le righe in alto.") {
                                ToggleRow("Riga \"Recenti\" (ultime 6 usate)", s.display.showRecentTags) { vm.setShowRecentTags(it) }
                                ToggleRow("Etichette rapide nel visualizzatore", s.display.viewerQuickTags) { vm.setViewerQuickTags(it) }
                            }
                        }
                        item { MoreOptions(showAdvanced) { showAdvanced = it } }
                        if (showAdvanced) item {
                            Section("Ordine della libreria") {
                                val custom = s.tagSortMode == com.cripta.app.data.TagSortMode.CUSTOM
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = !custom,
                                        onClick = { vm.setTagSortMode(com.cripta.app.data.TagSortMode.ALPHA) },
                                        label = { Text("Alfabetico") })
                                    FilterChip(selected = custom,
                                        onClick = { vm.setTagSortMode(com.cripta.app.data.TagSortMode.CUSTOM) },
                                        label = { Text("Personalizzato") })
                                }
                                Text("Con \"Personalizzato\" compaiono le frecce per spostare le etichette.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    SettingsPage.SICUREZZA -> {
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
                        item { MoreOptions(showAdvanced) { showAdvanced = it } }
                        if (showAdvanced) item {
                            Section("Limiti di sicurezza") {
                                Text(
                                    "Cripta protegge da curiosi occasionali. Non è pensato contro analisi forense o " +
                                        "dispositivi con root. L'eliminazione usa crypto-shredding (distrugge la chiave del file); " +
                                        "la cancellazione fisica su memoria flash non è garantita dal sistema. Internet è usato solo per " +
                                        "scaricare i video dai link e controllare gli aggiornamenti: i file del vault non lasciano mai il dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    SettingsPage.IMPORT -> {
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
                                Text("Vengono toccati solo gli originali dei file importati con successo.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    SettingsPage.STRUMENTI -> {
                        item {
                            Section("File duplicati", "Trova file identici o media simili, confrontali e libera spazio. Tutto sul dispositivo.") {
                                if (dupScanning) {
                                    val (done, total) = dupProgress
                                    Text(if (total > 0) "Scansione… $done/$total" else "Scansione…",
                                        style = MaterialTheme.typography.bodyMedium)
                                    if (total > 0) {
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                    Text("Puoi uscire dall'app: la scansione continua in background e ti avvisa con una notifica.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    androidx.compose.material3.FilledTonalButton(onClick = { vm.cancelScan() }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Annulla")
                                    }
                                } else {
                                    dupWaiting?.let { r ->
                                        Button(onClick = { vm.openScanResult() }, modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                if (r.groups.isEmpty()) "Scansione completata: nessun risultato"
                                                else "Risultati pronti: ${r.groups.size} gruppi · Apri"
                                            )
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        androidx.compose.material3.FilledTonalButton(onClick = { vm.scanExact() }, modifier = Modifier.weight(1f)) {
                                            Text("Esatti")
                                        }
                                        androidx.compose.material3.FilledTonalButton(onClick = { vm.scanSimilar() }, modifier = Modifier.weight(1f)) {
                                            Text("Simili")
                                        }
                                    }
                                    Text("Esatti: file byte-identici (qualsiasi tipo). Simili: foto e video uguali anche se ri-salvati, ri-codificati o ridimensionati.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            Section("Cestino", "Se attivo, i file eliminati restano recuperabili per qualche giorno; poi vengono distrutti in modo sicuro.") {
                                androidx.compose.material3.FilledTonalButton(onClick = { showTrash = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (trashed.isEmpty()) "Cestino vuoto" else "Apri cestino (${trashed.size})")
                                }
                                ToggleRow("Usa il cestino quando elimini", s.trashEnabled) { vm.setTrashEnabled(it) }
                                Text("Conserva per", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3, 7, 30).forEach { d ->
                                        FilterChip(selected = s.trashDays == d, onClick = { vm.setTrashDays(d) }, label = { Text("$d giorni") })
                                    }
                                }
                                Text("Il cestino accoglie anche gli originali sostituiti da una conversione in MP4.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            Section("Backup cifrato", "Archivio del vault protetto da passphrase, ripristinabile anche su un altro dispositivo. La sicurezza dipende dalla passphrase.") {
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
                    }

                    SettingsPage.INFO -> {
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
            }
        }
    }

    confirmReset?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Ripristinare i predefiniti?") },
            text = { Text("Le opzioni di \"${p.label}\" tornano ai valori iniziali. I file e le etichette non vengono toccati.") },
            confirmButton = { TextButton(onClick = { vm.resetPage(p.name); confirmReset = null }) { Text("Ripristina") } },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Annulla") } },
        )
    }

    if (showTrash) {
        TrashDialog(
            items = trashed.map { it.file },
            days = s.trashDays,
            onRestore = { vm.restore(it) },
            onDelete = { vm.deleteForever(it) },
            onEmpty = { vm.emptyTrash() },
            onDismiss = { showTrash = false },
        )
    }
    if (addTag) {
        var newColor by remember { mutableStateOf<Int?>(null) }
        LabelEditorDialog(
            title = "Nuova etichetta",
            onColor = { newColor = it },
            onConfirm = { name, alias -> vm.createTag(name, alias, newColor); addTag = false },
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
            initialColor = tag.color,
            onColor = { vm.setTagColor(tag.id, it) },
            initialPinned = tag.pinned,
            onPinned = { vm.setTagPinned(tag.name, it) },
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

/** Settings pages, in list order; each has its own icon and accent (stats-screen style). */
enum class SettingsPage(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    /** Tools are actions, not preferences: listed in their own group. */
    val tool: Boolean = false,
    val resettable: Boolean = true,
) {
    ASPETTO("Aspetto", Icons.Filled.Palette, androidx.compose.ui.graphics.Color(0xFF3B82F6)),
    COPERTINE("Copertine", Icons.Filled.Image, androidx.compose.ui.graphics.Color(0xFF8B5CF6)),
    VIDEO("Video", Icons.Filled.PlayCircle, androidx.compose.ui.graphics.Color(0xFFEC4899)),
    ETICHETTE("Etichette", Icons.AutoMirrored.Filled.Label, androidx.compose.ui.graphics.Color(0xFFF59E0B)),
    SICUREZZA("Sicurezza", Icons.Filled.Lock, androidx.compose.ui.graphics.Color(0xFFEF4444)),
    IMPORT("Import", Icons.Filled.Download, androidx.compose.ui.graphics.Color(0xFF06B6D4)),
    STRUMENTI("Strumenti", Icons.Filled.Build, androidx.compose.ui.graphics.Color(0xFF10B981), tool = true, resettable = false),
    INFO("Info e aggiornamenti", Icons.Filled.Info, androidx.compose.ui.graphics.Color(0xFF64748B), resettable = false),
}

/** One-line summary of a page's current state, shown under its name in the list. */
private fun pageSummary(p: SettingsPage, s: com.cripta.app.data.Settings, tagCount: Int, trashCount: Int, dupGroups: Int?): String = when (p) {
    SettingsPage.ASPETTO -> when (s.themeMode) {
        com.cripta.app.data.ThemeMode.SYSTEM -> "Tema di sistema"
        com.cripta.app.data.ThemeMode.LIGHT -> "Tema chiaro"
        com.cripta.app.data.ThemeMode.DARK -> "Tema scuro"
    } + if (s.dynamicColor) " · colori dinamici" else ""
    SettingsPage.COPERTINE -> if (!s.display.showTagsOnCover) "Etichette nascoste" else
        (if (s.display.coverTagRows == 0) "Righe automatiche" else "${s.display.coverTagRows} righe") +
            (if (s.display.tagColors) " · colori" else "") + (if (s.display.showDurationBadge) " · durata" else "")
    SettingsPage.VIDEO -> listOfNotNull(
        if (s.display.resumePlayback) "Riprendi" else null,
        if (s.videoLoop) "loop" else null,
        when (s.convertAfter) {
            com.cripta.app.data.ConvertAfter.REPLACE -> "MP4 sostituisce"
            com.cripta.app.data.ConvertAfter.ASK -> "MP4: chiedi"
            com.cripta.app.data.ConvertAfter.KEEP_BOTH -> "MP4: tieni entrambi"
        },
    ).joinToString(" · ")
    SettingsPage.ETICHETTE -> "$tagCount etichette" + if (s.display.showRecentTags) " · recenti" else ""
    SettingsPage.SICUREZZA -> when (s.autoLockMinutes) { -1 -> "Blocco mai"; 0 -> "Blocco subito"; else -> "Blocco dopo ${s.autoLockMinutes} min" } +
        if (s.allowScreenshots) " · screenshot consentiti" else " · screenshot bloccati"
    SettingsPage.IMPORT -> "Originale: " + when (s.deleteOriginalPolicy) {
        DeleteOriginalPolicy.ASK -> "chiedi"; DeleteOriginalPolicy.ALWAYS -> "elimina"; DeleteOriginalPolicy.NEVER -> "mantieni"
    }
    SettingsPage.STRUMENTI -> listOfNotNull(
        "Duplicati",
        if (trashCount > 0) "cestino ($trashCount)" else "cestino",
        "backup",
        dupGroups?.let { "risultati pronti ($it)" },
    ).joinToString(" · ")
    SettingsPage.INFO -> "Versione e aggiornamenti"
}

/** Searchable options: label + extra keywords -> page (opened with "altre opzioni" expanded). */
private val SEARCH_INDEX: List<Triple<String, String, SettingsPage>> = listOf(
    Triple("Tema chiaro / scuro", "tema scuro chiaro dark light sistema", SettingsPage.ASPETTO),
    Triple("Colori dinamici", "material you colori dinamici", SettingsPage.ASPETTO),
    Triple("Riepilogo conteggi", "statistiche conteggi video riepilogo", SettingsPage.ASPETTO),
    Triple("Intestazioni per data", "data oggi ieri gruppi", SettingsPage.ASPETTO),
    Triple("Dimensione e durata sotto i file", "info dimensione durata", SettingsPage.ASPETTO),
    Triple("Dettagli cartelle", "cartelle peso conteggio", SettingsPage.ASPETTO),
    Triple("Pulsanti nuova nota / casuale", "fab pulsante nota casuale random", SettingsPage.ASPETTO),
    Triple("Etichette sulla copertina", "tag copertina badge", SettingsPage.COPERTINE),
    Triple("Righe di etichette", "righe tag copertina", SettingsPage.COPERTINE),
    Triple("Colore delle etichette", "colori tag", SettingsPage.COPERTINE),
    Triple("Durata sui video", "badge durata", SettingsPage.COPERTINE),
    Triple("Qualità 4K / HD", "badge qualità risoluzione hd 4k", SettingsPage.COPERTINE),
    Triple("Testo etichette (sigla o nome)", "sigla alias nome", SettingsPage.COPERTINE),
    Triple("Riprendi da dove eri rimasto", "riprendi posizione resume", SettingsPage.VIDEO),
    Triple("Ripeti in loop", "loop ripeti", SettingsPage.VIDEO),
    Triple("Avvia senza audio", "muto audio", SettingsPage.VIDEO),
    Triple("Salto con doppio tocco", "doppio tocco salta secondi avanti indietro", SettingsPage.VIDEO),
    Triple("Luminosità e volume con i gesti", "gesti luminosità volume", SettingsPage.VIDEO),
    Triple("Rotazione automatica", "rotazione orizzontale verticale", SettingsPage.VIDEO),
    Triple("Picture-in-Picture", "pip finestra finestrella", SettingsPage.VIDEO),
    Triple("Conversione in MP4", "converti mp4 originale sostituisci", SettingsPage.VIDEO),
    Triple("Libreria etichette", "etichette tag crea rinomina elimina colore fissa pin", SettingsPage.ETICHETTE),
    Triple("Riga Recenti", "recenti ultime", SettingsPage.ETICHETTE),
    Triple("Etichette rapide nel visualizzatore", "rapide visualizzatore quick", SettingsPage.ETICHETTE),
    Triple("Ordine delle etichette", "ordine alfabetico personalizzato", SettingsPage.ETICHETTE),
    Triple("Blocco automatico", "blocco lock timeout", SettingsPage.SICUREZZA),
    Triple("Screenshot", "screenshot recenti privacy", SettingsPage.SICUREZZA),
    Triple("Originale dopo import", "originale elimina mantieni import", SettingsPage.IMPORT),
    Triple("File duplicati / media simili", "duplicati simili doppioni", SettingsPage.STRUMENTI),
    Triple("Cestino", "cestino eliminati ripristina", SettingsPage.STRUMENTI),
    Triple("Backup cifrato", "backup esporta ripristina passphrase", SettingsPage.STRUMENTI),
    Triple("Aggiornamenti", "aggiorna versione update", SettingsPage.INFO),
)

/** First level: search + preference pages + tools, each with a live summary. */
@Composable
private fun MainSettingsList(
    modifier: Modifier,
    query: String,
    onQuery: (String) -> Unit,
    summaries: Map<SettingsPage, String>,
    onOpen: (SettingsPage, Boolean) -> Unit,
    onLock: () -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery,
                placeholder = { Text("Cerca un'impostazione") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Cancella") } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            val hits = SEARCH_INDEX.filter { (label, kw, _) -> label.lowercase().contains(q) || kw.contains(q) }
            if (hits.isEmpty()) {
                item { Text("Nessuna impostazione trovata.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)) }
            }
            items(hits.size) { i ->
                val (label, _, p) = hits[i]
                PageRow(p, label, "in ${p.label}") { onOpen(p, true) }
            }
        } else {
            item { GroupLabel("Preferenze") }
            items(SettingsPage.entries.filter { !it.tool && it != SettingsPage.INFO }.size) { i ->
                val p = SettingsPage.entries.filter { !it.tool && it != SettingsPage.INFO }[i]
                PageRow(p, p.label, summaries[p].orEmpty()) { onOpen(p, false) }
            }
            item { GroupLabel("Strumenti") }
            item { PageRow(SettingsPage.STRUMENTI, "Duplicati, cestino e backup", summaries[SettingsPage.STRUMENTI].orEmpty()) { onOpen(SettingsPage.STRUMENTI, false) } }
            item { PageRow(SettingsPage.INFO, SettingsPage.INFO.label, summaries[SettingsPage.INFO].orEmpty()) { onOpen(SettingsPage.INFO, false) } }
            item {
                Button(onClick = onLock, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Filled.Lock, null); Text("  Blocca ora")
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp))
}

/** A page entry: coloured icon badge, name, current-state summary, chevron. */
@Composable
private fun PageRow(p: SettingsPage, title: String, summary: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Surface(color = p.tint.copy(alpha = 0.16f), shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(40.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = p.tint, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary.isNotBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Mostra altre opzioni" toggle that reveals a page's less-used settings. */
@Composable
private fun MoreOptions(expanded: Boolean, onChange: (Boolean) -> Unit) {
    TextButton(onClick = { onChange(!expanded) }) {
        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
        Text(if (expanded) "  Nascondi altre opzioni" else "  Mostra altre opzioni")
    }
}

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    // Grouped, elevated card: binds each group's controls together.
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

/** Trash contents: restore or destroy each file, or empty everything at once. */
@Composable
private fun TrashDialog(
    items: List<com.cripta.app.data.db.FileEntity>,
    days: Int,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEmpty: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    val fmt = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cestino") },
        text = {
            if (items.isEmpty()) {
                Text("Il cestino è vuoto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEach { f ->
                        val left = f.deletedAt?.let { days - ((System.currentTimeMillis() - it) / 86_400_000L).toInt() } ?: days
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Eliminato il ${f.deletedAt?.let { fmt.format(java.util.Date(it)) } ?: "—"} · " +
                                        if (left <= 1) "distrutto entro oggi" else "ancora $left giorni",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onRestore(f.id) }) { Text("Ripristina") }
                            IconButton(onClick = { onDelete(f.id) }) {
                                Icon(Icons.Filled.Delete, "Elimina definitivamente", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        dismissButton = {
            if (items.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("Svuota", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Svuotare il cestino?") },
            text = { Text("${items.size} file verranno distrutti in modo sicuro. Irreversibile.") },
            confirmButton = {
                TextButton(onClick = { onEmpty(); confirmEmpty = false }) { Text("Svuota", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Annulla") } },
        )
    }
}

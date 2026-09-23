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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Item 0 is the sticky category bar; category i lives at item i + 1.
    val currentCat by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val atEnd = !listState.canScrollForward
            if (atEnd) SettingsCategory.entries.last() else
                SettingsCategory.entries[(first - 1).coerceIn(0, SettingsCategory.entries.lastIndex)]
        }
    }
    var showAllTags by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    val trashed by vm.trashed.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    // Quick lock, always at hand instead of buried mid-list.
                    IconButton(onClick = { vm.lockNow(); onBack() }) { Icon(Icons.Filled.Lock, "Blocca ora") }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            stickyHeader(key = "nav") {
                CategoryBar(currentCat) { cat ->
                    scope.launch { listState.animateScrollToItem(cat.ordinal + 1) }
                }
            }

            item(key = SettingsCategory.ASPETTO.name) {
                CategoryBlock(SettingsCategory.ASPETTO) {
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
                    Section("Vista del vault", "Come vengono mostrati i file all'apertura.") {
                        Text("Vista", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = s.viewMode == ViewMode.GRID, onClick = { vm.setViewMode(ViewMode.GRID) },
                                label = { Text("Griglia") })
                            FilterChip(selected = s.viewMode == ViewMode.LIST, onClick = { vm.setViewMode(ViewMode.LIST) },
                                label = { Text("Lista") })
                        }
                        Text("Colonne della griglia", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (2..5).forEach { n ->
                                FilterChip(selected = s.gridColumns == n, onClick = { vm.setGridColumns(n) }, label = { Text("$n") })
                            }
                        }
                        Text("Ordinamento", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(SortKey.DATE to "Data", SortKey.NAME to "Nome", SortKey.SIZE to "Dimensione", SortKey.MANUAL to "Manuale")
                                .forEach { (k, l) ->
                                    FilterChip(selected = s.sortKey == k, onClick = { vm.setSort(k, s.sortAscending) }, label = { Text(l) })
                                }
                        }
                        if (s.sortKey != SortKey.MANUAL) {
                            ToggleRow("Ordine crescente", s.sortAscending) { vm.setSort(s.sortKey, it) }
                        }
                    }
                    Section("Dettagli visualizzati", "Informazioni mostrate nel vault.") {
                        ToggleRow("Dimensione e durata sotto i file", s.display.showFileInfo) { vm.setShowFileInfo(it) }
                        ToggleRow("Intestazioni per data", s.display.showDateHeaders) { vm.setShowDateHeaders(it) }
                        ToggleRow("Dettagli cartelle (conteggio e peso)", s.display.showFolderInfo) { vm.setShowFolderInfo(it) }
                        ToggleRow("Riepilogo conteggi sopra la griglia", s.display.showStatsStrip) { vm.setShowStatsStrip(it) }
                        ToggleRow("Pulsante nuova nota", s.display.showNoteFab) { vm.setShowNoteFab(it) }
                        ToggleRow("Pulsante casuale", s.display.showRandomFab) { vm.setShowRandomFab(it) }
                    }
                }
            }

            item(key = SettingsCategory.COPERTINE.name) {
                CategoryBlock(SettingsCategory.COPERTINE) {
                    Section("Anteprima", "Si aggiorna mentre cambi le opzioni qui sotto.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                            // Two sizes: roughly a 2-column and a 3-column grid cell.
                            com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(150.dp))
                            com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(104.dp))
                        }
                    }
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
                            Text("Testo", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = s.display.coverTagStyle == com.cripta.app.data.CoverTagStyle.ALIAS,
                                    onClick = { vm.setCoverTagStyle(com.cripta.app.data.CoverTagStyle.ALIAS) },
                                    label = { Text("Sigla / emoji") })
                                FilterChip(selected = s.display.coverTagStyle == com.cripta.app.data.CoverTagStyle.NAME,
                                    onClick = { vm.setCoverTagStyle(com.cripta.app.data.CoverTagStyle.NAME) },
                                    label = { Text("Nome intero") })
                            }
                        }
                        ToggleRow("Un colore per ogni etichetta", s.display.tagColors) { vm.setTagColors(it) }
                    }
                    Section("Badge sui video") {
                        ToggleRow("Durata (es. 3:12)", s.display.showDurationBadge) { vm.setShowDurationBadge(it) }
                        ToggleRow("Qualità (4K / HD / SD)", s.display.showQualityBadge) { vm.setShowQualityBadge(it) }
                    }
                }
            }

            item(key = SettingsCategory.RIPRODUZIONE.name) {
                CategoryBlock(SettingsCategory.RIPRODUZIONE) {
                    Section("Video") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Riprendi da dove eri rimasto")
                                Text("Riapre ogni video al punto in cui l'avevi lasciato e mostra una barra di avanzamento sulla copertina.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = s.display.resumePlayback, onCheckedChange = { vm.setResumePlayback(it) })
                        }
                        ToggleRow("Ripeti il video in loop", s.videoLoop) { vm.setVideoLoop(it) }
                        ToggleRow("Avvia senza audio", s.videoStartMuted) { vm.setVideoStartMuted(it) }
                    }
                    Section("Conversione in MP4", "Per i video non scorribili (es. MPEG). Le conversioni vanno in coda, una alla volta, e ogni copia viene verificata prima di essere salvata.") {
                        Text("Dopo la conversione", style = MaterialTheme.typography.labelLarge)
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

            item(key = SettingsCategory.ETICHETTE.name) {
                CategoryBlock(SettingsCategory.ETICHETTE) {
                    Section("Libreria etichette", "${tags.size} etichette · crea, riordina e rinomina.") {
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
                            // Long libraries are collapsed so the rest of the settings stay reachable.
                            val visible = if (showAllTags || tags.size <= 8) tags else tags.take(8)
                            visible.forEachIndexed { index, tag ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    // Tap the colour dot to change the colour (opens the editor).
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .clickable { editTag = tag },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(com.cripta.app.ui.theme.tagColor(tag)))
                                    }
                                    Text("${tagAlias(tag)}  #${tag.name}", Modifier.weight(1f).padding(start = 10.dp),
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
            }

            item(key = SettingsCategory.SICUREZZA.name) {
                CategoryBlock(SettingsCategory.SICUREZZA) {
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
                        Button(onClick = { vm.lockNow(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Lock, null); Text("  Blocca ora")
                        }
                    }
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

            item(key = SettingsCategory.ARCHIVIO.name) {
                CategoryBlock(SettingsCategory.ARCHIVIO) {
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
                    Section("Cestino", "Se attivo, i file eliminati restano recuperabili per qualche giorno; poi vengono distrutti in modo sicuro.") {
                        ToggleRow("Usa il cestino", s.trashEnabled) { vm.setTrashEnabled(it) }
                        if (s.trashEnabled) {
                            Text("Conserva per", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3, 7, 30).forEach { d ->
                                    FilterChip(selected = s.trashDays == d, onClick = { vm.setTrashDays(d) }, label = { Text("$d giorni") })
                                }
                            }
                        }
                        if (trashed.isNotEmpty() || s.trashEnabled) {
                            androidx.compose.material3.FilledTonalButton(onClick = { showTrash = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (trashed.isEmpty()) "Cestino vuoto" else "Apri cestino (${trashed.size})")
                            }
                        }
                        Text("Finché un file è nel cestino la sua chiave non è ancora distrutta.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                            androidx.compose.material3.FilledTonalButton(onClick = { vm.cancelScan() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Annulla")
                            }
                        } else {
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
            }

            item(key = SettingsCategory.INFO.name) {
                CategoryBlock(SettingsCategory.INFO) {
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

/** Settings categories, in screen order; each has its own icon and accent (stats-screen style). */
private enum class SettingsCategory(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
) {
    ASPETTO("Aspetto", Icons.Filled.Palette, androidx.compose.ui.graphics.Color(0xFF3B82F6)),
    COPERTINE("Copertine", Icons.Filled.Image, androidx.compose.ui.graphics.Color(0xFF8B5CF6)),
    RIPRODUZIONE("Video", Icons.Filled.PlayCircle, androidx.compose.ui.graphics.Color(0xFFEC4899)),
    ETICHETTE("Etichette", Icons.AutoMirrored.Filled.Label, androidx.compose.ui.graphics.Color(0xFFF59E0B)),
    SICUREZZA("Sicurezza", Icons.Filled.Lock, androidx.compose.ui.graphics.Color(0xFFEF4444)),
    ARCHIVIO("Archivio", Icons.Filled.Inventory2, androidx.compose.ui.graphics.Color(0xFF10B981)),
    INFO("Info", Icons.Filled.Info, androidx.compose.ui.graphics.Color(0xFF64748B)),
}

/** Sticky chip bar: jump to a category; the one on screen is highlighted as you scroll. */
@Composable
private fun CategoryBar(current: SettingsCategory, onPick: (SettingsCategory) -> Unit) {
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { rowState.animateScrollToItem(current.ordinal) }
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.lazy.LazyRow(
            state = rowState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SettingsCategory.entries) { c ->
                FilterChip(
                    selected = c == current,
                    onClick = { onPick(c) },
                    leadingIcon = { Icon(c.icon, null, tint = c.tint, modifier = Modifier.size(18.dp)) },
                    label = { Text(c.label) },
                )
            }
        }
    }
}

/** A category: coloured icon badge + big title, then its sections. */
@Composable
private fun CategoryBlock(cat: SettingsCategory, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Surface(color = cat.tint.copy(alpha = 0.16f), shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(40.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(cat.icon, null, tint = cat.tint, modifier = Modifier.size(22.dp))
                }
            }
            Text(cat.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp))
        }
        content()
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

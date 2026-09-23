package com.cripta.app.ui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.cripta.app.ui.theme.Motion
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
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
    val dupUndo by vm.dupUndo.collectAsState()
    val backupBusy by vm.backupBusy.collectAsState()

    var showAllTags by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    val trashed by vm.trashed.collectAsState()

    // Two-level navigation: the category list, or one category page. Back returns to the list.
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    /** "Mostra altre opzioni" state of the page being shown (opened by a search hit). */
    var confirmReset by remember { mutableStateOf<SettingsPage?>(null) }
    val current = page?.let { p -> SettingsPage.entries.firstOrNull { it.name == p } }
    androidx.activity.compose.BackHandler(enabled = current != null) { page = null }
    // Requests from elsewhere (Home "Strumenti", duplicate-scan notification).
    val requestedPage by vm.requestedPage.collectAsState()
    LaunchedEffect(requestedPage) {
        requestedPage?.let { page = it; vm.consumeRequestedPage() }
    }

    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Landscape: the top bar scrolls away (down hides, up reveals), like in Cartelle.
    val scrollBehavior = if (landscape) androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior()
        else androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
    val maxW = if (landscape) 1400.dp else 720.dp
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                title = { Text(current?.label ?: "Impostazioni") },
                // Impostazioni is a tab root: the back arrow only appears inside a category page.
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Torna alle impostazioni")
                        }
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
      // List <-> category page: a short crossfade, so the swap reads as one step deeper/back.
      androidx.compose.animation.AnimatedContent(
          targetState = current,
          transitionSpec = {
              androidx.compose.animation.fadeIn(Motion.enter()) togetherWith androidx.compose.animation.fadeOut(Motion.exit())
          },
          label = "settingsPage",
      ) { current ->
        if (current == null) {
            MainSettingsList(
                modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()
                    .wrapContentWidth().widthIn(max = maxW).fillMaxWidth(),
                columns = if (landscape) 2 else 1,
                query = query,
                onQuery = { query = it },
                summaries = SettingsPage.entries.associateWith { pageSummary(it, s, tags.size, trashed.size, dupWaiting?.groups?.size) },
                onOpen = { p, _ -> page = p.name; query = "" },
                onLock = { vm.lockNow(); onBack() },
            )
        } else {
          Column(Modifier.fillMaxSize().padding(pad).wrapContentWidth().widthIn(max = maxW).fillMaxWidth()) {
            if (current == SettingsPage.COPERTINE) {
                // The live preview stays pinned on top while the options scroll under it.
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom) {
                    com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(if (landscape) 96.dp else 132.dp))
                    com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(if (landscape) 72.dp else 92.dp))
                    Text("Anteprima dal vivo", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Portrait: one column. Landscape: sections flow in two columns, so the width is used
            // instead of stretching every row across the screen.
            androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(if (landscape) 2 else 1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        item {
                            Section("Pulsanti e dettagli") {
                                ToggleRow("Dimensione e durata sotto i file", s.display.showFileInfo) { vm.setShowFileInfo(it) }
                                ToggleRow("Dettagli cartelle (conteggio e peso)", s.display.showFolderInfo) { vm.setShowFolderInfo(it) }
                                ToggleRow("Pulsante nuova nota", s.display.showNoteFab) { vm.setShowNoteFab(it) }
                                ToggleRow("Pulsante casuale", s.display.showRandomFab) { vm.setShowRandomFab(it) }
                            }
                        }
                    }

                    SettingsPage.COPERTINE -> {
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
                        item {
                            Section("Testo e badge") {
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
                            Section("Riproduzione", "Come parte e si comporta ogni video.") {
                                ToggleRow("Riprendi da dove eri rimasto", s.display.resumePlayback,
                                    desc = "Riapre il video al punto lasciato; la copertina mostra quanto hai visto.",
                                    icon = Icons.Filled.History) { vm.setResumePlayback(it) }
                                ToggleRow("Ripeti in loop", s.videoLoop,
                                    desc = "Il video ricomincia da capo quando finisce.",
                                    icon = Icons.Filled.Repeat) { vm.setVideoLoop(it) }
                                ToggleRow("Avvia senza audio", s.videoStartMuted,
                                    desc = "Ogni video parte muto; l'audio si riattiva dai comandi.",
                                    icon = Icons.AutoMirrored.Filled.VolumeOff) { vm.setVideoStartMuted(it) }
                            }
                        }
                        item {
                            Section("Fine video") {
                                ToggleRow("Passa al file successivo", s.autoNext,
                                    desc = if (s.videoLoop) "Non si attiva finché la ripetizione in loop è accesa."
                                        else "Negli ultimi secondi compare «Prossimo» con il conto alla rovescia.",
                                    icon = Icons.Filled.SkipNext) { vm.setAutoNext(it) }
                                if (s.autoNext) {
                                    ChoiceRow("Mostra «Prossimo» negli ultimi", listOf(5 to "5 s", 8 to "8 s", 15 to "15 s"),
                                        s.autoNextSec) { vm.setAutoNextSec(it) }
                                }
                            }
                        }
                        item {
                            Section("Gesti", "Cosa succede toccando e trascinando sullo schermo del visualizzatore.") {
                                ToggleRow("Scorri giù per chiudere", s.swipeToClose,
                                    desc = "Trascina verso il basso per uscire dal visualizzatore (video, foto e note).",
                                    icon = Icons.Filled.KeyboardArrowDown) { vm.setSwipeToClose(it) }
                                ToggleRow("Scorri su per etichette e dettagli", s.swipeForDetails,
                                    desc = "Trascina verso l'alto per aprire il pannello del file.",
                                    icon = Icons.Filled.KeyboardArrowUp) { vm.setSwipeForDetails(it) }
                                ToggleRow("Luminosità sul lato sinistro", s.gestureControls,
                                    desc = "Trascina in su o in giù sul lato sinistro del video.",
                                    icon = Icons.Filled.LightMode) { vm.setGestureControls(it) }
                                ToggleRow("Volume sul lato destro", s.gestureVolume,
                                    desc = "Trascina in su o in giù sul lato destro del video.",
                                    icon = Icons.AutoMirrored.Filled.VolumeUp) { vm.setGestureVolume(it) }
                                ToggleRow("Tieni premuto per accelerare", s.holdForSpeed,
                                    desc = "Tieni il dito su un lato: il video va più veloce finché non lo alzi.",
                                    icon = Icons.Filled.FastForward) { vm.setHoldForSpeed(it) }
                                if (s.holdForSpeed) {
                                    ChoiceRow("Velocità tenendo premuto", listOf(15 to "1,5×", 20 to "2×", 30 to "3×"),
                                        s.holdSpeedX10) { vm.setHoldSpeed(it) }
                                }
                                ChoiceRow("Doppio tocco sui lati: salta di", listOf(5 to "5 s", 10 to "10 s", 30 to "30 s"),
                                    s.seekStepSec, icon = Icons.Filled.FastRewind) { vm.setSeekStep(it) }
                            }
                        }
                        item {
                            Section("Schermo") {
                                ToggleRow("Segui l'orientamento del video", s.autoRotate,
                                    desc = if (s.autoRotate) "I video orizzontali si girano in orizzontale, quelli verticali in verticale."
                                        else "Lo schermo ruota liberamente con il telefono.",
                                    icon = Icons.Filled.ScreenRotation) { vm.setAutoRotate(it) }
                                ChoiceRow("I comandi restano visibili per", listOf(2 to "2 s", 4 to "4 s", 8 to "8 s"),
                                    s.controlsTimeoutSec, icon = Icons.Filled.Timer) { vm.setControlsTimeout(it) }
                                ToggleRow("Anteprime dei file vicini", s.viewerFilmstrip,
                                    desc = "Piccole copertine dei file prima e dopo, insieme ai comandi.",
                                    icon = Icons.Filled.ViewCarousel) { vm.setViewerFilmstrip(it) }
                                ToggleRow("Picture-in-Picture automatico", s.pictureInPicture,
                                    desc = "Uscendo dall'app il video continua in una finestrella (visibile a chi guarda lo schermo). Da spento resta il pulsante nel player.",
                                    icon = Icons.Filled.PictureInPictureAlt) { vm.setPictureInPicture(it) }
                            }
                        }
                        run {
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
                                                // 48dp touch target around the small colour dot.
                                                Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                    .clickable(onClickLabel = "Cambia colore", role = Role.Button) { editTag = tag }
                                                    .semantics { contentDescription = "Colore di #${tag.name}" },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                androidx.compose.foundation.layout.Box(
                                                    Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(com.cripta.app.ui.theme.tagColor(tag)))
                                            }
                                            Text((if (tag.pinned) "📌 " else "") + "${tagAlias(tag)}  #${tag.name}", Modifier.weight(1f).padding(start = 2.dp),
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
                                ) {
                                    Icon(Icons.Filled.Add, null, Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Aggiungi etichetta")
                                }
                            }
                        }
                        item {
                            Section("Assegnazione rapida", "La libreria resta sempre nello stesso ordine; cambiano solo le righe in alto.") {
                                ToggleRow("Riga \"Recenti\" (ultime 6 usate)", s.display.showRecentTags) { vm.setShowRecentTags(it) }
                                ToggleRow("Etichette rapide nel visualizzatore", s.display.viewerQuickTags) { vm.setViewerQuickTags(it) }
                            }
                        }
                        item {
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
                                ToggleRow("Consenti screenshot", s.allowScreenshots,
                                    desc = "Se disattivato, blocca gli screenshot e nasconde l'app nelle app recenti.") {
                                    vm.setAllowScreenshots(it)
                                }
                            }
                        }
                        item {
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
                                // What the chosen option actually does; "Elimina" warns that it is not reversible.
                                androidx.compose.animation.AnimatedContent(
                                    targetState = s.deleteOriginalPolicy,
                                    transitionSpec = {
                                        androidx.compose.animation.fadeIn(Motion.enter()) togetherWith
                                            androidx.compose.animation.fadeOut(Motion.exit()) using
                                            androidx.compose.animation.SizeTransform(clip = false) { _, _ -> Motion.enter(Motion.MEDIUM) }
                                    },
                                    label = "deletePolicyDesc",
                                ) { p ->
                                    Text(
                                        when (p) {
                                            DeleteOriginalPolicy.ASK -> "A fine import ti viene chiesto se eliminare gli originali dal dispositivo."
                                            DeleteOriginalPolicy.ALWAYS -> "Attenzione: gli originali vengono eliminati dal dispositivo dopo l'import e non si " +
                                                "possono recuperare. Resta solo la copia cifrata nel vault."
                                            DeleteOriginalPolicy.NEVER -> "Gli originali restano sul dispositivo: eliminali tu se non vuoi che siano visibili fuori dal vault."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (p == DeleteOriginalPolicy.ALWAYS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("Vengono toccati solo gli originali dei file importati con successo.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    SettingsPage.STRUMENTI -> {
                        item {
                            Section("File duplicati", "Trova file identici o media simili, confrontali e libera spazio. Tutto sul dispositivo.") {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = dupScanning,
                                    transitionSpec = { calmSwap() },
                                    label = "dupScan",
                                ) { scanning ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (scanning) {
                                    val (done, total) = dupProgress
                                    Text(if (total > 0) "Scansione… $done/$total" else "Scansione…",
                                        style = MaterialTheme.typography.bodyMedium)
                                    if (total > 0) {
                                        val smooth by animateFloatAsState(
                                            (done.toFloat() / total).coerceIn(0f, 1f),
                                            tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "dupProgress",
                                        )
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { smooth }, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                    Text("Puoi uscire dall'app: la scansione continua in background e ti avvisa con una notifica.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    androidx.compose.material3.FilledTonalButton(onClick = { vm.cancelScan() }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Interrompi scansione")
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
                                androidx.compose.animation.AnimatedContent(
                                    targetState = backupBusy,
                                    transitionSpec = { calmSwap() },
                                    label = "backupBusy",
                                ) { busy ->
                                    if (busy != null) {
                                        // Runs on its own; the vault stays usable, the result arrives as a message.
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                                            Text(busy, style = MaterialTheme.typography.bodyMedium)
                                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                            Text("Può richiedere qualche minuto con molti file. Tieni l'app aperta.",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.FilledTonalButton(
                                                onClick = { exportLauncher.launch("cripta-backup-${backupStamp()}.criptabak") },
                                                modifier = Modifier.weight(1f),
                                            ) { Text("Esporta") }
                                            androidx.compose.material3.FilledTonalButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                                                Text("Ripristina")
                                            }
                                        }
                                    }
                                }
                                Text("Il ripristino aggiunge i file del backup a quelli già nel vault: nulla di ciò che c'è viene cancellato o sostituito.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    SettingsPage.INFO -> {
                        item {
                            Section("Aggiornamenti", "Scarica l'ultima versione pubblicata.") {
                                // Phases swap in place (keyed by phase, so progress ticks don't re-animate).
                                androidx.compose.animation.AnimatedContent(
                                    targetState = updateState,
                                    contentKey = { it::class },
                                    transitionSpec = { calmSwap() },
                                    label = "updateState",
                                ) { u ->
                                Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (u) {
                                    SettingsViewModel.UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        Text("Controllo in corso…", style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(start = 12.dp))
                                    }
                                    SettingsViewModel.UpdateState.UpToDate ->
                                        Text("Sei alla versione più recente.", style = MaterialTheme.typography.bodyMedium)
                                    is SettingsViewModel.UpdateState.Available -> {
                                        Text("Disponibile: ${u.release.versionName} (${com.cripta.app.ui.components.formatBytes(u.release.sizeBytes)})",
                                            style = MaterialTheme.typography.bodyMedium)
                                        Button(onClick = { vm.downloadUpdate(ctx) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Installa aggiornamento")
                                        }
                                    }
                                    is SettingsViewModel.UpdateState.Downloading -> {
                                        // Read the live state (not the frozen target) so the bar keeps moving.
                                        val pct = (updateState as? SettingsViewModel.UpdateState.Downloading)?.pct ?: u.pct
                                        val smooth by animateFloatAsState(pct / 100f, tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "updatePct")
                                        Text("Download dell'aggiornamento: $pct%", style = MaterialTheme.typography.bodyMedium)
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { smooth }, modifier = Modifier.fillMaxWidth())
                                    }
                                    is SettingsViewModel.UpdateState.ReadyToInstall -> {
                                        Text("Pronto: ${u.release.versionName}. Se hai chiuso l'installazione, puoi riaprirla da qui.",
                                            style = MaterialTheme.typography.bodyMedium)
                                        Button(onClick = { vm.installUpdate(ctx) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Installa aggiornamento")
                                        }
                                    }
                                    is SettingsViewModel.UpdateState.Error ->
                                        Text(u.message, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error)
                                    SettingsViewModel.UpdateState.Idle -> {}
                                }
                                }
                                }
                                ToggleRow("Includi pre-release (build di prova)", s.updatePrerelease) { vm.setUpdatePrerelease(it) }
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
            creating = pendingExport != null,
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
            trashDays = if (s.trashEnabled) s.trashDays else null,
            canUndo = dupUndo.isNotEmpty(),
            onUndo = { vm.undoDuplicates() },
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
            trashDays = if (s.trashEnabled) s.trashDays else null,
            canUndo = dupUndo.isNotEmpty(),
            onUndo = { vm.undoDuplicates() },
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

private const val MIN_PASSPHRASE = 6

/**
 * Passphrase entry. Creating a backup asks for it twice (a typo would make the archive
 * unrecoverable); restoring asks once and explains that files are added, not replaced.
 */
@Composable
private fun PassphraseDialog(title: String, creating: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val tooShort = pass.length < MIN_PASSPHRASE
    val mismatch = creating && confirm.isNotEmpty() && confirm != pass
    val valid = !tooShort && (!creating || confirm == pass)
    val transformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(
                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                if (visible) "Nascondi passphrase" else "Mostra passphrase",
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (creating) "Servirà per ripristinare il backup: senza di essa l'archivio non si può aprire in alcun modo."
                    else "I file del backup vengono aggiunti a quelli già nel vault; nulla viene cancellato.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = transformation,
                    trailingIcon = toggle,
                    supportingText = {
                        Text(if (tooShort) "Almeno $MIN_PASSPHRASE caratteri (${pass.length}/$MIN_PASSPHRASE)" else "Lunghezza ok")
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = if (creating) androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (valid) onConfirm(pass) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (creating) {
                    OutlinedTextField(
                        value = confirm, onValueChange = { confirm = it },
                        label = { Text("Ripeti la passphrase") },
                        singleLine = true,
                        visualTransformation = transformation,
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text("Le passphrase non coincidono") }) else null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (valid) onConfirm(pass) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = valid) { Text(if (creating) "Crea backup" else "Ripristina") }
        },
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

/** Searchable options: label + extra keywords -> page. */
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
    Triple("Luminosità con il gesto", "gesti luminosità trascina sinistra", SettingsPage.VIDEO),
    Triple("Volume con il gesto", "gesti volume trascina destra", SettingsPage.VIDEO),
    Triple("Rotazione automatica", "rotazione orizzontale verticale", SettingsPage.VIDEO),
    Triple("Picture-in-Picture", "pip finestra finestrella", SettingsPage.VIDEO),
    Triple("Scorri giù per chiudere", "swipe chiudi chiusura scorri giù gesto", SettingsPage.VIDEO),
    Triple("Scorri su per etichette e dettagli", "swipe dettagli pannello scorri su gesto", SettingsPage.VIDEO),
    Triple("Tieni premuto per accelerare", "velocità veloce 2x premi tieni", SettingsPage.VIDEO),
    Triple("Durata dei comandi a schermo", "comandi controlli timeout nascondi visibili", SettingsPage.VIDEO),
    Triple("Prossimo video a fine riproduzione", "successivo automatico fine video prossimo coda autoplay", SettingsPage.VIDEO),
    Triple("Anteprime dei file vicini", "filmstrip striscia copertine successivi precedenti", SettingsPage.VIDEO),
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
    Triple("Aggiornamenti", "aggiorna versione update pre-release beta", SettingsPage.INFO),
)

/** First level: search + preference pages + tools, each with a live summary. */
@Composable
private fun MainSettingsList(
    modifier: Modifier,
    columns: Int,
    query: String,
    onQuery: (String) -> Unit,
    summaries: Map<SettingsPage, String>,
    onOpen: (SettingsPage, Boolean) -> Unit,
    onLock: () -> Unit,
) {
    // Two columns of page rows in landscape; search, group labels and the lock button span the width.
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan =
        { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = full) {
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
                item(span = full) { Text("Nessuna impostazione trovata.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)) }
            }
            items(hits.size) { i ->
                val (label, _, p) = hits[i]
                PageRow(p, label, "in ${p.label}") { onOpen(p, true) }
            }
        } else {
            item(span = full) { GroupLabel("Preferenze") }
            items(SettingsPage.entries.filter { !it.tool && it != SettingsPage.INFO }.size) { i ->
                val p = SettingsPage.entries.filter { !it.tool && it != SettingsPage.INFO }[i]
                PageRow(p, p.label, summaries[p].orEmpty()) { onOpen(p, false) }
            }
            item(span = full) { GroupLabel("Strumenti") }
            item { PageRow(SettingsPage.STRUMENTI, "Duplicati, cestino e backup", summaries[SettingsPage.STRUMENTI].orEmpty()) { onOpen(SettingsPage.STRUMENTI, false) } }
            item { PageRow(SettingsPage.INFO, SettingsPage.INFO.label, summaries[SettingsPage.INFO].orEmpty()) { onOpen(SettingsPage.INFO, false) } }
            item(span = full) {
                Button(onClick = onLock, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Blocca ora")
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp).semantics { heading() })
}

/** Calm swap between two states of a panel: crossfade while the height follows. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<*>.calmSwap(): androidx.compose.animation.ContentTransform =
    (androidx.compose.animation.fadeIn(Motion.enter()) togetherWith androidx.compose.animation.fadeOut(Motion.exit())).using(
        androidx.compose.animation.SizeTransform { _, _ -> Motion.enter(Motion.MEDIUM) })

/** Date/time stamp for the backup file name, so successive backups don't overwrite each other. */
private fun backupStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.ROOT).format(java.util.Date())

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
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    // Grouped, elevated card: binds each group's controls together.
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    desc: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    // The whole row is one switch (label included) for touch and TalkBack; the Switch only draws.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp).size(22.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A labelled single choice shown as chips, aligned with the toggle rows (icon column). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onPick: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp, top = 2.dp).size(22.dp))
        } else {
            // Sub-option of the toggle above: indent it under that toggle's text.
            androidx.compose.foundation.layout.Spacer(Modifier.width(36.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (v, l) -> FilterChip(selected = v == selected, onClick = { onPick(v) }, label = { Text(l) }) }
            }
        }
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

package com.cripta.app.ui.settings

import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.CriptaDialog
import androidx.compose.material3.DropdownMenu
import com.cripta.app.ui.components.vaultUnlocked
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import com.cripta.app.media.intro.SegmentKind
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.cripta.app.data.Settings
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.intro.IntroRepository
import com.cripta.app.ui.components.ChipFlowRow
import com.cripta.app.ui.components.MediaThumb
import kotlinx.coroutines.delay

/**
 * Settings › Persone e intro › "Intro e finale": a live status card, then three groups (how skipping behaves,
 * the analysis, maintenance at the bottom), and the full-page results with covers.
 */
@Composable
internal fun IntroSettings(s: Settings, vm: SettingsViewModel, onOpenFile: (String) -> Unit) {
    val overview by vm.introOverview.collectAsState()
    val progress by vm.introProgress.collectAsState()
    val resultsOpen by vm.introResultsOpen.collectAsState()
    // Live while the section is on screen: faster while a scan runs.
    LaunchedEffect(progress != null) {
        while (true) { vm.refreshIntro(); delay(if (progress != null) 3_000 else 15_000) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Section("Intro e finale", "Cripta riconosce sigle e finali confrontando l'audio dei video del vault, in qualunque cartella. " +
            "Per segnarli a mano apri un video e scorri su: la sezione è sotto le etichette.") {
            StatusCard(overview, progress, onStop = { vm.cancelIntroScan() }, onResults = { vm.introResultsOpen.value = true })
        }
        Section("Salto") {
            ToggleRow("Salta l'intro in automatico", s.introAutoSkip,
                desc = if (s.introAutoSkip) "Il video salta da solo; «Annulla» torna indietro." else "Compare il pulsante «Salta intro».",
                icon = Icons.Filled.SkipNext) { vm.setIntroAutoSkip(it) }
            ToggleRow("Salta il finale in automatico", s.outroAutoSkip,
                desc = "Passa al file successivo, se c'è, altrimenti va alla fine del video.",
                icon = Icons.Filled.SkipNext) { vm.setOutroAutoSkip(it) }
        }
        Section("Analisi") {
            ToggleRow("Analizza i nuovi video", s.introAnalyzeNew,
                desc = "Dopo ogni importazione, download o conversione, in background.",
                icon = Icons.Filled.GraphicEq) { vm.setIntroAnalyzeNew(it) }
            ToggleRow("Analisi rapida", s.introQuickScan,
                desc = if (s.introQuickScan) "Solo il primo e l'ultimo minuto di ogni video: molto più veloce, " +
                    "ma non trova le sigle che iniziano dopo il primo minuto."
                    else "Primi 10 e ultimi 5 minuti di ogni video: trova più sigle, ma è molto più lenta.",
                icon = Icons.Filled.Speed) { vm.setIntroQuickScan(it) }
            FilledTonalButton(onClick = { vm.startIntroScan() }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
                Text(if (progress == null) "Analizza i video esistenti" else "Analisi in corso…")
            }
        }
        Maintenance(vm, onOpenFile)
    }
    if (resultsOpen) overview?.let { o ->
        val groups by vm.introGroups.collectAsState()
        val introMessage by vm.introMessage.collectAsState()
        val actions = remember(vm) {
            SignatureActions(
                rename = vm::renameSignature, merge = vm::mergeSignatures, delete = vm::deleteSignatures,
                duration = vm::setSignatureDuration, removeVideo = vm::removeFromSignature,
            )
        }
        IntroResultsPage(o, thumb = { vm.thumbOf(it) }, groups = groups, onLoadGroups = { vm.loadIntroGroups(it) },
            actions = actions, message = introMessage, onMessageShown = { vm.introMessage.value = null },
            onClose = { vm.introResultsOpen.value = false },
            onOpen = { ids, id -> vm.openFromResults(ids); onOpenFile(id) })
    }
}

/** How far the analysis is, what it found, and a way into the results. */
@Composable
private fun StatusCard(o: IntroRepository.Overview?, progress: com.cripta.app.work.ConversionService.Companion.IntroProgress?,
                       onStop: () -> Unit, onResults: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (o == null) {
            Text("Lettura dello stato…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        // One line per figure: any number fits ("Analizzati 1.234 di 5.678"), nothing is cut.
        val fmt: (Number) -> String = { com.cripta.app.ui.components.formatCount(it) }
        if (progress != null) {
            com.cripta.app.ui.components.CountRow(progress.phase, fmt(progress.done), of = fmt(progress.total),
                progress = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f)
        }
        com.cripta.app.ui.components.CountRow("Analizzati", fmt(o.analysed), of = fmt(o.videos),
            progress = if (progress == null && o.videos > 0) o.analysed.toFloat() / o.videos else null)
        com.cripta.app.ui.components.CountRow("Con intro", fmt(o.withIntro))
        com.cripta.app.ui.components.CountRow("Con finale", fmt(o.withOutro))
        if (progress == null && o.skipped > 0) Text("${fmt(o.skipped)} non analizzabili: il motivo è nei risultati.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilledTonalButton(onClick = onResults, modifier = Modifier.weight(1f)) { Text("Risultati") }
            if (progress != null) androidx.compose.material3.FilledTonalButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Interrompi") }
        }
    }
}

/** Rejected samples and the restart, apart at the bottom: rare, and the restart throws work away. */
@Composable
private fun Maintenance(vm: SettingsViewModel, onOpenFile: (String) -> Unit) {
    val rejected by vm.rejectedSegments.collectAsState()
    var showRejected by remember { mutableStateOf(false) }
    var askRestart by remember { mutableStateOf(false) }
    var restartAll by remember { mutableStateOf(false) }
    Section("Manutenzione") {
        androidx.compose.material3.FilledTonalButton(onClick = { showRejected = true }, enabled = rejected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (rejected.isEmpty()) "Nessun segmento scartato" else "Segmenti scartati (${rejected.size})")
        }
        androidx.compose.material3.FilledTonalButton(onClick = { restartAll = false; askRestart = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text("Ricomincia l'analisi da capo")
        }
    }
    if (showRejected) CriptaAlertDialog(
        onDismissRequest = { showRejected = false },
        confirmButton = { TextButton(onClick = { showRejected = false }) { Text("Chiudi") } },
        title = { Text("Segmenti scartati") },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Questi tratti audio non diventano mai intro o finali. «Togli» rimette subito i segmenti che bloccavano.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(rejected, key = { it.id }) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { showRejected = false; onOpenFile(r.originFileId) }.padding(vertical = 4.dp)) {
                            Text(r.originName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (r.kind == "intro") "Intro" else "Finale") +
                                " · ${span(r.startMs, r.endMs)} · ${(r.endMs - r.startMs + 500) / 1000} s · tocca per aprire",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.restoreRejected(r.id) }) { Text("Togli") }
                    }
                }
            }
        },
    )
    if (askRestart) CriptaAlertDialog(
        onDismissRequest = { askRestart = false },
        title = { Text("Ricominciare da capo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ferma l'analisi in corso, cancella tutte le impronte audio e tutte le intro e i finali trovati, " +
                    "poi analizza di nuovo ogni video. Con molti video richiede tempo.")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restartAll = !restartAll }) {
                    Checkbox(checked = restartAll, onCheckedChange = { restartAll = it })
                    Text("Cancella anche i segni fatti a mano e i segmenti scartati")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.restartIntroScan(restartAll); askRestart = false }) {
                Text("Ricomincia", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { askRestart = false }) { Text("Annulla") } },
    )
}

private enum class ResultFilter(val label: String, val kind: SegmentKind? = null) {
    WITH("Con intro o finale"), WITHOUT("Senza"), SKIPPED("Non analizzabili"),
    // The signatures: videos grouped by the sound of their intro / outro.
    BY_INTRO("Firme intro", SegmentKind.INTRO), BY_OUTRO("Firme finale", SegmentKind.OUTRO),
}

/**
 * Every analysed video with its cover and times, filtered and searchable; a tap opens it in the
 * viewer (swiping goes through the same list) and Back comes back here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IntroResultsPage(
    o: IntroRepository.Overview,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    groups: Pair<SegmentKind, List<IntroRepository.Signature>>?,
    onLoadGroups: (SegmentKind) -> Unit,
    actions: SignatureActions,
    message: String?,
    onMessageShown: () -> Unit,
    onClose: () -> Unit,
    onOpen: (List<String>, String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(ResultFilter.WITH) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var renaming by remember { mutableStateOf<IntroRepository.Signature?>(null) }
    var timing by remember { mutableStateOf<IntroRepository.Signature?>(null) }
    var deleting by remember { mutableStateOf<List<IntroRepository.Signature>?>(null) }
    var merging by remember { mutableStateOf<List<IntroRepository.Signature>?>(null) }
    LaunchedEffect(filter) { filter.kind?.let(onLoadGroups); expanded = emptySet(); selected = emptySet() }
    // After a change the list is reloaded: its positions (unsaved keys) may have moved.
    LaunchedEffect(groups) { selected = emptySet() }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    val selectedSigs = groups?.second?.withIndex()?.filter { sigKey(it.index, it.value) in selected }?.map { it.value }.orEmpty()
    fun matches(name: String) = query.isBlank() || name.contains(query.trim(), ignoreCase = true)
    val shown = remember(o, filter, query) {
        o.entries.filter { e ->
            when (filter) {
                ResultFilter.WITH -> e.intro != null || e.outro != null
                ResultFilter.WITHOUT -> e.skipReason == null && e.intro == null && e.outro == null
                ResultFilter.SKIPPED -> e.skipReason != null
                else -> false
            } && matches(e.file.originalName)
        }
    }
    // Groups of the chosen kind, those holding a matching name when searching.
    val shownGroups = remember(groups, filter, query) {
        groups?.takeIf { it.first == filter.kind }?.second?.withIndex()?.filter { g -> g.value.entries.any { matches(it.first.originalName) } }
    }
    val counts = remember(o, groups) {
        mapOf(ResultFilter.WITH to o.entries.count { it.intro != null || it.outro != null },
            ResultFilter.WITHOUT to o.entries.count { it.skipReason == null && it.intro == null && it.outro == null },
            ResultFilter.SKIPPED to o.skipped,
            ResultFilter.BY_INTRO to groups?.takeIf { it.first == SegmentKind.INTRO }?.second?.size,
            ResultFilter.BY_OUTRO to groups?.takeIf { it.first == SegmentKind.OUTRO }?.second?.size)
    }
    CriptaDialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                if (selected.isNotEmpty()) TopAppBar(
                    colors = com.cripta.app.ui.components.criptaTopBarColors(),
                    title = { Text("${selected.size} selezionate") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Annulla selezione") } },
                    actions = {
                        TextButton(onClick = { merging = selectedSigs }, enabled = selectedSigs.size >= 2) { Text("Unisci") }
                        TextButton(onClick = { deleting = selectedSigs }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                    },
                ) else TopAppBar(
                    colors = com.cripta.app.ui.components.criptaTopBarColors(),
                    title = { Text("Risultati dell'analisi") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                )
            },
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    com.cripta.app.ui.components.CriptaTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Cerca per nome") },
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    ChipFlowRow {
                        ResultFilter.entries.forEach { f ->
                            com.cripta.app.ui.components.ChoiceChip(selected = filter == f, onClick = { filter = f },
                                label = { Text(f.label + (counts[f]?.let { " · $it" } ?: "")) })
                        }
                    }
                }
                if (filter.kind != null) {
                    item {
                        Text("Ogni firma raggruppa i video con lo stesso suono di " +
                            (if (filter.kind == SegmentKind.INTRO) "intro" else "finale") +
                            ". In cima quelle senza nome e con pochi video, i falsi positivi più probabili. " +
                            "Dal menu ⋮ rinomina, unisci, dai una durata o elimina; tieni premuto per selezionarne più di una.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        shownGroups == null -> item {
                            Text("Confronto dei suoni…", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                        }
                        shownGroups.isEmpty() -> item {
                            Text("Nessuna firma.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                        }
                    }
                    shownGroups?.forEach { (i, g) ->
                        val key = sigKey(i, g)
                        item(key = key) {
                            SignatureCard(i + 1, g, thumb, open = i in expanded, selecting = selected.isNotEmpty(), selected = key in selected,
                                onToggleOpen = { expanded = if (i in expanded) expanded - i else expanded + i },
                                onToggleSelect = { selected = if (key in selected) selected - key else selected + key },
                                onRename = { renaming = g }, onMerge = { selected = setOf(key) }, onDuration = { timing = g },
                                onDelete = { deleting = listOf(g) })
                        }
                        if (i in expanded && selected.isEmpty()) items(g.entries, key = { "$key-${it.first.id}" }) { (file, seg) ->
                            val e = IntroRepository.Entry(file, seg.takeIf { filter.kind == SegmentKind.INTRO },
                                seg.takeIf { filter.kind == SegmentKind.OUTRO }, null)
                            EntryRow(e, thumb, Modifier.padding(start = 12.dp), trailing = {
                                IconButton(onClick = { actions.removeVideo(g, file.id) }) {
                                    Icon(Icons.Filled.RemoveCircleOutline, "Togli ${file.originalName} da questa firma")
                                }
                            }) { onOpen(g.entries.map { it.first.id }, file.id) }
                        }
                    }
                    return@LazyColumn
                }
                if (shown.isEmpty()) item {
                    Text(
                        when (filter) {
                            ResultFilter.WITH -> "Nessun video con intro o finale. Servono almeno due video con lo stesso audio; " +
                                "con l'analisi rapida la sigla deve stare nel primo minuto."
                            ResultFilter.WITHOUT -> "Tutti i video analizzati hanno un'intro o un finale."
                            ResultFilter.SKIPPED -> "Nessun video non analizzabile."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                items(shown, key = { it.file.id }) { e ->
                    EntryRow(e, thumb) { onOpen(shown.map { it.file.id }, e.file.id) }
                }
            }
        }
        renaming?.let { s ->
            NameDialog("Rinomina la firma", s.name.orEmpty(), confirm = "Rinomina", onDismiss = { renaming = null }) {
                actions.rename(s, it); renaming = null
            }
        }
        merging?.let { sigs ->
            NameDialog("Unire ${sigs.size} firme?", sigs.firstNotNullOfOrNull { it.name }.orEmpty(), confirm = "Unisci",
                hint = "Diventano una sola firma che riconosce tutti i loro suoni (${sigs.sumOf { it.entries.size }} video). Nome:",
                allowBlank = true, onDismiss = { merging = null }) {
                actions.merge(sigs, it.ifBlank { null }); merging = null; selected = emptySet()
            }
        }
        deleting?.let { sigs ->
            CriptaAlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(if (sigs.size == 1) "Non è una sigla?" else "${sigs.size} firme non sono sigle?") },
                text = {
                    Text("${sigs.sumOf { it.entries.size }} video perdono questo segmento (quelli segnati a mano restano) e il suono " +
                        "non verrà più rilevato. Si può annullare da Manutenzione › Segmenti scartati.")
                },
                confirmButton = {
                    TextButton(onClick = { actions.delete(sigs); deleting = null; selected = emptySet() }) {
                        Text("Elimina", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
            )
        }
        timing?.let { s -> DurationDialog(s, onDismiss = { timing = null }) { ds, de -> actions.duration(s, ds, de); timing = null } }
    }
}

/** What the signatures page can do (the settings view model behind it). */
internal class SignatureActions(
    val rename: (IntroRepository.Signature, String) -> Unit,
    val merge: (List<IntroRepository.Signature>, String?) -> Unit,
    val delete: (List<IntroRepository.Signature>) -> Unit,
    val duration: (IntroRepository.Signature, Long, Long) -> Unit,
    val removeVideo: (IntroRepository.Signature, String) -> Unit,
)

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, hint: String? = null, allowBlank: Boolean = false,
                       onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                com.cripta.app.ui.components.CriptaTextField(value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("Es. Sigla One Piece") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = allowBlank || text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/**
 * "Durata della sigla": how far before / after its sound every video's segment starts and ends (an
 * intro that goes on past its distinctive sound); applied to the signature's videos, now and later.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationDialog(s: IntroRepository.Signature, onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    var start by remember { mutableStateOf(s.startDeltaMs) }
    var end by remember { mutableStateOf(s.endDeltaMs) }
    @Composable
    fun Stepper(label: String, value: Long, onChange: (Long) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$label: ${if (value == 0L) "come il suono" else deltaText(value)}", style = MaterialTheme.typography.bodyMedium)
            ChipFlowRow(gap = 4.dp) {
                for ((d, t) in listOf(-5_000L to "−5 s", -1_000L to "−1 s", -500L to "−0,5", 500L to "+0,5", 1_000L to "+1 s", 5_000L to "+5 s")) {
                    TextButton(onClick = { onChange(value + d) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text(t) }
                }
            }
        }
    }
    CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Durata della sigla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Il suono riconosciuto dura circa " + "%.1f s".format(s.soundMs / 1000.0).replace('.', ',') +
                    ". Sposta inizio e fine rispetto al suono: vale per i ${s.entries.size} video della firma e per quelli futuri " +
                    "(i segni fatti a mano restano).", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stepper("Inizio", start) { start = it }
                Stepper("Fine", end) { end = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(start, end) }) { Text("Applica") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** One video in the results: cover, name, its intro / outro times or why it could not be analysed. */
@Composable
private fun EntryRow(e: IntroRepository.Entry, thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
                     modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaThumb(e.file, thumb, Modifier.size(width = 112.dp, height = 63.dp), showFavoriteBadge = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.file.originalName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                e.skipReason != null -> Text(e.skipReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                e.intro == null && e.outro == null -> Text("Nessuna intro né finale", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    e.intro?.let { SegLine("Intro", it, MaterialTheme.colorScheme.primary) }
                    e.outro?.let { SegLine("Finale", it, MaterialTheme.colorScheme.tertiary) }
                }
            }
        }
        trailing?.invoke()
    }
}

private fun sigKey(i: Int, g: IntroRepository.Signature) = g.id?.let { "s$it" } ?: "u$i"

private fun deltaText(ms: Long) = (if (ms > 0) "+" else if (ms < 0) "−" else "") + "%.1f s".format(kotlin.math.abs(ms) / 1000.0).replace('.', ',')

/**
 * A signature: its name (or number), how many videos, the sound's length and its duration, a few
 * covers. Tap: list its videos (or select, while selecting); long press: start selecting; ⋮: actions.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SignatureCard(
    n: Int, g: IntroRepository.Signature, thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    open: Boolean, selecting: Boolean, selected: Boolean,
    onToggleOpen: () -> Unit, onToggleSelect: () -> Unit,
    onRename: () -> Unit, onMerge: () -> Unit, onDuration: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    androidx.compose.material3.Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selecting) onToggleSelect() else onToggleOpen() },
            onLongClick = { if (!selecting) onToggleSelect() },
            onLongClickLabel = "Seleziona",
        ),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.name ?: "Firma senza nome ($n)", style = MaterialTheme.typography.titleSmall,
                        color = if (g.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text("${g.entries.size} video · suono di circa " + "%.1f s".format(g.soundMs / 1000.0).replace('.', ',') +
                        (if (g.startDeltaMs != 0L || g.endDeltaMs != 0L) " · inizio ${deltaText(g.startDeltaMs)}, fine ${deltaText(g.endDeltaMs)}" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                } else {
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Azioni per la firma") }
                        DropdownMenu(expanded = menu && vaultUnlocked(), onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Rinomina") }, onClick = { menu = false; onRename() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Unisci con altre…") }, onClick = { menu = false; onMerge() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Durata della sigla") }, onClick = { menu = false; onDuration() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Non è una sigla", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                g.entries.take(4).forEach { (file, _) ->
                    MediaThumb(file, thumb, Modifier.size(width = 72.dp, height = 40.dp), showFavoriteBadge = false)
                }
                if (g.entries.size > 4) Text("+${g.entries.size - 4}", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun SegLine(label: String, seg: MediaSegmentEntity, color: androidx.compose.ui.graphics.Color) {
    if (seg.source == "none") return
    Text("$label ${span(seg.startMs, seg.endMs)}" + if (seg.source == "auto") "" else " · segnata da te",
        style = MaterialTheme.typography.bodySmall, color = color)
}

private fun span(startMs: Long, endMs: Long) =
    (com.cripta.app.ui.components.formatDuration(startMs) ?: "0:00") + "–" + (com.cripta.app.ui.components.formatDuration(endMs) ?: "0:00")

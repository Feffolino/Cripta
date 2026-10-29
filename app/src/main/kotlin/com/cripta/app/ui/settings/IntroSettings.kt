package com.cripta.app.ui.settings

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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cripta.app.data.Settings
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.intro.IntroRepository
import com.cripta.app.ui.components.ChipFlowRow
import com.cripta.app.ui.components.MediaThumb
import kotlinx.coroutines.delay

/**
 * Settings › Video › "Intro e finale": a live status card, then three groups (how skipping behaves,
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        IntroResultsPage(o, thumb = { vm.thumbOf(it) }, onClose = { vm.introResultsOpen.value = false },
            onOpen = { ids, id -> vm.openFromResults(ids); onOpenFile(id) })
    }
}

/** How far the analysis is, what it found, and a way into the results. */
@Composable
private fun StatusCard(o: IntroRepository.Overview?, progress: com.cripta.app.work.ConversionService.Companion.IntroProgress?,
                       onStop: () -> Unit, onResults: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (o == null) {
            Text("Lettura dello stato…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Analizzati", "${o.analysed}", "di ${o.videos}", Modifier.weight(1f))
            Stat("Con intro", "${o.withIntro}", null, Modifier.weight(1f))
            Stat("Con finale", "${o.withOutro}", null, Modifier.weight(1f))
        }
        if (progress != null) {
            Text("${progress.phase}: ${progress.done} di ${progress.total}", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                modifier = Modifier.fillMaxWidth())
        } else if (o.videos > 0) {
            LinearProgressIndicator(progress = { o.analysed.toFloat() / o.videos }, modifier = Modifier.fillMaxWidth())
            if (o.skipped > 0) Text("${o.skipped} non analizzabili: il motivo è nei risultati.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onResults, modifier = Modifier.weight(1f)) { Text("Risultati") }
            if (progress != null) OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Interrompi") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String?, modifier: Modifier) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge)
                if (sub != null) { Spacer(Modifier.width(4.dp)); Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp)) }
            }
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
        OutlinedButton(onClick = { showRejected = true }, enabled = rejected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (rejected.isEmpty()) "Nessun segmento scartato" else "Segmenti scartati (${rejected.size})")
        }
        OutlinedButton(onClick = { restartAll = false; askRestart = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text("Ricomincia l'analisi da capo")
        }
    }
    if (showRejected) AlertDialog(
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
    if (askRestart) AlertDialog(
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

private enum class ResultFilter(val label: String) { WITH("Con intro o finale"), WITHOUT("Senza"), SKIPPED("Non analizzabili") }

/**
 * Every analysed video with its cover and times, filtered and searchable; a tap opens it in the
 * viewer (swiping goes through the same list) and Back comes back here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IntroResultsPage(
    o: IntroRepository.Overview,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onClose: () -> Unit,
    onOpen: (List<String>, String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(ResultFilter.WITH) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(o, filter, query) {
        o.entries.filter { e ->
            when (filter) {
                ResultFilter.WITH -> e.intro != null || e.outro != null
                ResultFilter.WITHOUT -> e.skipReason == null && e.intro == null && e.outro == null
                ResultFilter.SKIPPED -> e.skipReason != null
            } && (query.isBlank() || e.file.originalName.contains(query.trim(), ignoreCase = true))
        }
    }
    val counts = remember(o) {
        mapOf(ResultFilter.WITH to o.entries.count { it.intro != null || it.outro != null },
            ResultFilter.WITHOUT to o.entries.count { it.skipReason == null && it.intro == null && it.outro == null },
            ResultFilter.SKIPPED to o.skipped)
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Risultati dell'analisi") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                )
            },
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Cerca per nome") },
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    ChipFlowRow {
                        ResultFilter.entries.forEach { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} · ${counts[f]}") })
                        }
                    }
                }
                if (shown.isEmpty()) item {
                    Text(
                        when (filter) {
                            ResultFilter.WITH -> "Nessun video con intro o finale. Servono almeno due video con lo stesso audio; " +
                                "con l'analisi rapida la sigla deve stare nel primo minuto."
                            ResultFilter.WITHOUT -> "Tutti i video analizzati hanno un'intro o un finale."
                            ResultFilter.SKIPPED -> "Nessun video non analizzabile."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                items(shown, key = { it.file.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(shown.map { it.file.id }, e.file.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                    }
                }
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

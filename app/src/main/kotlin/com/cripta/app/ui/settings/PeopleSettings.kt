package com.cripta.app.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.CriptaDialog
import com.cripta.app.ui.components.vaultUnlocked
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.cripta.app.ui.theme.pressScale
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.Settings
import com.cripta.app.data.face.FaceRepository
import com.cripta.app.media.face.FaceStats
import com.cripta.app.ui.components.MediaThumb
import com.cripta.app.work.ConversionService

/**
 * Settings › "Persone": the analysis status with its live progress, the settings, maintenance and
 * statistics. The people themselves live on their own page ([PeopleScreen]).
 */
@Composable
internal fun PeopleSettings(s: Settings, vm: SettingsViewModel) {
    val overview by vm.people.collectAsStateWithLifecycle()
    val progress by vm.faceProgress.collectAsStateWithLifecycle()
    var askRestart by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    // Same order as Intro e finale: what it does, its state and main action; the settings; then the
    // rare and destructive actions apart at the bottom.
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
    PageIntro("Cripta trova i volti di foto e video, in qualunque cartella; sei tu a dire chi sono, dai dettagli di ogni media. " +
        "Tutto avviene sul telefono: volti e persone restano nel database cifrato.") {
        ScanStatus(overview, progress, onStop = { vm.cancelFaceScan() })
        FilledTonalButton(onClick = { vm.openPeople() }, modifier = Modifier.fillMaxWidth()) {
            com.cripta.app.ui.components.ButtonText("Vedi le persone")
        }
    }
    Section("Analisi") {
        ToggleRow("Analizza i nuovi media", s.facesAnalyzeNew, desc = "Dopo ogni importazione, download o conversione, in background.",
            icon = Icons.Filled.Face) { vm.setFacesAnalyzeNew(it) }
        ChoiceRow("Riconoscimento", listOf("all" to "Tutti i volti", "known" to "Solo persone note"), s.facesMode,
            icon = Icons.Filled.Groups,
            desc = if (s.facesMode == "all") "Ti chiede chi sono tutti i volti trovati, raggruppati per somiglianza in «Da sistemare»."
                else "Ti propone, in «Da confermare», solo volti simili a persone che hanno volti in almeno 2 media. Gli altri restano nei dettagli di ogni media.") {
            vm.setFacesMode(it)
        }
        FilledTonalButton(onClick = { vm.startFaceScan() }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
            com.cripta.app.ui.components.ButtonText(if (progress == null) "Analizza foto e video mancanti" else "Analisi in corso…")
        }
    }
    Section("Visualizzazione") {
        ToggleRow("Momenti nel lettore", s.faceMoments,
            desc = "Segni sulla barra del video dove compaiono le persone; nei dettagli ogni volto elenca i suoi momenti.",
            icon = Icons.Filled.Timeline) { vm.setFaceMoments(it) }
        ChoiceRow("Persone in Home", listOf("off" to "Nascoste", "blur" to "Sfocate", "on" to "Visibili"), s.peopleOnHome,
            icon = Icons.Filled.Home,
            desc = when (s.peopleOnHome) {
                "off" -> "La Home non mostra volti."
                "blur" -> "Sfocati finché non tocchi la riga: utile in pubblico."
                else -> "Subito visibili."
            }) { vm.setPeopleOnHome(it) }
    }
    Section("Manutenzione") {
        FilledTonalButton(onClick = { clearing = true }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
            com.cripta.app.ui.components.ButtonText("Elimina volti trovati per errore…")
        }
        val ignored by vm.ignoredFaces.collectAsStateWithLifecycle()
        if (ignored > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${com.cripta.app.ui.components.formatCount(ignored)} volti eliminati da te: le nuove analisi non li ripropongono.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.forgetIgnoredFaces() }) { com.cripta.app.ui.components.ButtonText("Dimentica") }
        }
        Row {
            TextButton(onClick = { showStats = true }) { com.cripta.app.ui.components.ButtonText("Statistiche") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { askRestart = true }, enabled = progress == null) { com.cripta.app.ui.components.ButtonText("Ricomincia da capo", color = MaterialTheme.colorScheme.error) }
        }
    }
    }
    if (askRestart) {
        var everything by remember { mutableStateOf(false) }
        CriptaAlertDialog(onDismissRequest = { askRestart = false },
            title = { Text("Ricominciare da capo?") },
            text = {
                Column {
                    Text("Tutti i media verranno analizzati di nuovo.")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { everything = !everything }) {
                        Checkbox(everything, { everything = it })
                        Text("Cancella anche nomi, preferiti, unioni e correzioni")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.restartFaces(everything); askRestart = false }) { com.cripta.app.ui.components.ButtonText("Ricomincia") } },
            dismissButton = { TextButton(onClick = { askRestart = false }) { com.cripta.app.ui.components.ButtonText("Annulla") } })
    }
    if (showStats) StatsDialog(vm) { showStats = false }
    if (clearing) ClearDetectedDialog(vm) { clearing = false }
}

/**
 * "Elimina volti trovati per errore": the analysis ran when it should not have. The faces found
 * in a chosen period that nobody confirmed are forgotten; confirmed faces and people stay, and those
 * media are not analysed again unless asked ("Rianalizza" on the media).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClearDetectedDialog(vm: SettingsViewModel, onClose: () -> Unit) {
    val periods = listOf(3_600_000L to "Ultima ora", 86_400_000L to "Ultime 24 ore", 7 * 86_400_000L to "Ultimi 7 giorni",
        30 * 86_400_000L to "Ultimi 30 giorni", Long.MAX_VALUE to "Sempre")
    var period by remember { mutableStateOf(periods[1].first) }
    val found by produceState<FaceRepository.Detected?>(null, period) {
        value = null
        val since = if (period == Long.MAX_VALUE) 0L else System.currentTimeMillis() - period
        value = runCatching { vm.detectedSince(since) }.getOrNull()
    }
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text("Elimina volti trovati") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Per quando l'analisi era attiva per errore. Restano i volti che hai confermato o aggiunto, " +
                    "le persone e i loro nomi; questi media non vengono riesaminati da soli.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Analizzati nel periodo", style = MaterialTheme.typography.labelLarge)
                com.cripta.app.ui.components.ChipFlowRow {
                    periods.forEach { (ms, label) ->
                        com.cripta.app.ui.components.ChoiceChip(selected = period == ms, onClick = { period = ms }, label = { Text(label) })
                    }
                }
                val f = found
                Text(when {
                    f == null -> "Conteggio…"
                    f.faces == 0 -> "Nessun volto da eliminare in questo periodo."
                    else -> "${f.faces} volti in ${f.media.size} media."
                }, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            val f = found
            TextButton(enabled = f != null && f.faces > 0, onClick = { f?.let { vm.clearDetected(it.media) }; onClose() }) {
                com.cripta.app.ui.components.ButtonText("Elimina", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText("Annulla") } })
}

/** Live progress while a scan runs, else how much of the vault is analysed. */
@Composable
private fun ScanStatus(o: FaceRepository.Overview?, progress: ConversionService.Companion.IntroProgress?, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (progress != null) {
            val pct = progress.done * 100 / maxOf(1, progress.total)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Analisi in corso: ${progress.done} di ${progress.total} media · $pct%", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onStop) { com.cripta.app.ui.components.ButtonText("Ferma") }
            }
            LinearProgressIndicator(progress = { progress.done.toFloat() / maxOf(1, progress.total) }, modifier = Modifier.fillMaxWidth())
            Text("Le persone compaiono e si aggiornano mentre l'analisi procede.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (o == null) {
            Text("Lettura dello stato…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val ok = o.scans.count { it.status == "ok" }
            val skipped = o.scans.count { it.status != "ok" && it.status != FaceRepository.CLEARED }
            val missing = (o.media - o.scans.size).coerceAtLeast(0)
            Text("Analizzati ${o.scans.size} di ${o.media} media" + (if (skipped > 0) " ($skipped non leggibili)" else "") +
                " · ${o.people.count { it.main }} persone", style = MaterialTheme.typography.bodyMedium)
            if (progress == null && missing > 0) Text("Mancano $missing media: avvia l'analisi qui sotto.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (progress == null) LinearProgressIndicator(progress = { o.scans.size.toFloat() / maxOf(1, o.media) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatsDialog(vm: SettingsViewModel, onClose: () -> Unit) {
    // null = still computing; a failure is its own state, with a retry, never an endless "Calcolo…".
    var attempt by remember { mutableStateOf(0) }
    var tech by remember { mutableStateOf(false) }
    val st by produceState<Result<FaceRepository.Stats>?>(null, attempt) { value = null; value = runCatching { vm.faceStats() } }
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text("Statistiche dei volti") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val r = st
                if (r == null) { Text("Calcolo…"); return@Column }
                val x = r.getOrNull()
                if (x == null) {
                    Text("Non è stato possibile leggere le statistiche.")
                    TextButton(onClick = { attempt++ }) { com.cripta.app.ui.components.ButtonText("Riprova") }
                    return@Column
                }
                Text("Media analizzati: ${x.media}" + if (x.skipped > 0) " (${x.skipped} non leggibili)" else "")
                if (x.videos > 0) Text("Tempo medio per video: %.1f s".format(x.videoMs / 1000.0 / x.videos))
                if (x.photos > 0) Text("Tempo medio per foto: %.2f s".format(x.photoMs / 1000.0 / x.photos))
                Text("Volti trovati: ${x.faces}")
                if (x.rejects.isNotEmpty()) Text("Scartati: " + x.rejects.entries.joinToString(", ") { "${it.value} ${it.key.label}" })
                // The algorithm's numbers stay available, folded away under what a person needs to know.
                TextButton(onClick = { tech = !tech }, contentPadding = PaddingValues(0.dp)) {
                    com.cripta.app.ui.components.ButtonText(if (tech) "Nascondi i dettagli tecnici" else "Dettagli tecnici")
                }
                if (tech) {
                    Text("Fotogrammi uguali saltati: ${x.duplicates} · aggiunti vicino ai volti: ${x.extra}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Nitidezza dei volti allineati (sotto ${"%.0f".format(com.cripta.app.media.face.FaceEngine.MIN_SHARPNESS)} = sfocati):",
                        style = MaterialTheme.typography.labelLarge)
                    x.sharp.forEachIndexed { i, n ->
                        Text("  ${FaceStats.bandLabel(i)}: $n", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText("Chiudi") } })
}

/**
 * A person's face (their cover). Keyed on the picture too: "Dividi" can give the same face another
 * picture, and a list that kept the old one would show a face the person no longer has.
 */
@Composable
internal fun FaceAvatar(face: com.cripta.app.data.db.TrackCard?, load: suspend (Long) -> android.graphics.Bitmap?, size: Dp,
                        modifier: Modifier = Modifier, selected: Boolean = false) =
    FaceAvatar(face?.id, load, size, modifier, key = face?.timeMs, selected = selected)

/**
 * A face picture from the database, round. [selected]: the one way a picked face looks everywhere
 * (filters, people, groups, details, pickers): a 3dp accent ring, as in the vault's filters.
 */
@Composable
internal fun FaceAvatar(trackId: Long?, load: suspend (Long) -> android.graphics.Bitmap?, size: Dp, modifier: Modifier = Modifier,
                        /** Changes when the face's picture does (a face split keeps its id). */
                        key: Any? = null, selected: Boolean = false) {
    val bmp by produceState<android.graphics.Bitmap?>(null, trackId, key) { value = trackId?.let { load(it) } }
    Box(modifier.size(size).then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
        .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Face, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A star on a face: one tap adds or removes the person from the favourites. */
@Composable
internal fun FavoriteStar(on: Boolean, name: String, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = modifier.size(36.dp)) {
        Icon(if (on) Icons.Filled.Star else Icons.Filled.StarBorder, if (on) "Togli $name dai preferiti" else "Aggiungi $name ai preferiti",
            tint = if (on) com.cripta.app.ui.theme.Favorite else androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(26.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).padding(3.dp))
    }
}

/** The mark of a favourite person where it cannot be changed (Home): the same star as everywhere. */
@Composable
internal fun FavoriteBadge(modifier: Modifier = Modifier) {
    Icon(Icons.Filled.Star, "Preferita", tint = com.cripta.app.ui.theme.Favorite,
        modifier = modifier.size(24.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).padding(3.dp))
}

private enum class PeopleFilter(val label: String) {
    MAIN("Persone"), FAVORITE("Preferite"), NAMED("Con nome"), SINGLE("Una volta"), IGNORED("Ignorate"), TODO("Da sistemare"), NONE("Senza volti");

    fun matches(p: FaceRepository.Person) = when (this) {
        MAIN -> p.main
        FAVORITE -> !p.entity.ignored && p.entity.favorite
        NAMED -> !p.entity.ignored && p.entity.name.isNotBlank()
        SINGLE -> !p.entity.ignored && !p.main
        IGNORED -> p.entity.ignored
        TODO, NONE -> false
    }
}

private fun sortLabel(k: com.cripta.app.data.PeopleSort) = when (k) {
    com.cripta.app.data.PeopleSort.RELEVANCE -> "Preferite, poi più media"
    com.cripta.app.data.PeopleSort.NAME -> "Nome"
    com.cripta.app.data.PeopleSort.MEDIA -> "Numero di media"
    com.cripta.app.data.PeopleSort.LATEST -> "Media più recente"
    com.cripta.app.data.PeopleSort.ADDED -> "Data di creazione"
}

/** The two directions of [k], in words (descending first). */
private fun sortDirections(k: com.cripta.app.data.PeopleSort) = when (k) {
    com.cripta.app.data.PeopleSort.NAME -> "Z → A" to "A → Z"
    com.cripta.app.data.PeopleSort.MEDIA -> "Più media" to "Meno media"
    com.cripta.app.data.PeopleSort.LATEST, com.cripta.app.data.PeopleSort.ADDED -> "Più recenti" to "Meno recenti"
    com.cripta.app.data.PeopleSort.RELEVANCE -> "Prima le principali" to "Prima le altre"
}

/** The people page's order. Hidden people stay last whatever the order. */
private fun sortPeople(list: List<FaceRepository.Person>, k: com.cripta.app.data.PeopleSort, ascending: Boolean): List<FaceRepository.Person> {
    val sorted = when (k) {
        // The repository's order (starred, main, most media) is the "descending" one.
        com.cripta.app.data.PeopleSort.RELEVANCE -> list.reversed()
        com.cripta.app.data.PeopleSort.NAME -> list.sortedBy { it.title.lowercase() }
        com.cripta.app.data.PeopleSort.MEDIA -> list.sortedBy { it.media.size }
        com.cripta.app.data.PeopleSort.LATEST -> list.sortedBy { p -> p.media.maxOfOrNull { it.importedAt } ?: 0L }
        com.cripta.app.data.PeopleSort.ADDED -> list.sortedBy { it.entity.createdAt }
    }
    return (if (ascending) sorted else sorted.reversed()).sortedBy { it.entity.ignored }
}

/** The people page: its own destination (Settings, Home, the scan's notification lead here). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PeopleScreen(onBack: () -> Unit, onOpenFile: (String) -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val overview by vm.people.collectAsStateWithLifecycle()
    val progress by vm.faceProgress.collectAsStateWithLifecycle()
    val message by vm.faceMessage.collectAsStateWithLifecycle()
    val undo by vm.faceUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf(PeopleFilter.MAIN) }
    var query by rememberSaveable { mutableStateOf("") }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    /** The faces of a "Da sistemare" group being named. */
    var naming by remember { mutableStateOf<List<Long>?>(null) }
    /** People picked as in Cartelle: long press, then drag over the others. */
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var askMerge by remember { mutableStateOf(false) }
    /** "Da sistemare" open one group at a time (kept across a trip to a media). */
    var sorting by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    val st by vm.settings.collectAsStateWithLifecycle()
    val sortKey = com.cripta.app.data.PeopleSort.entries.firstOrNull { it.name == st.peopleSort } ?: com.cripta.app.data.PeopleSort.RELEVANCE
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        if (snackbar.showSnackbar(u.first, "Annulla", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoFaceChange()
        else if (vm.faceUndo.value === u) vm.faceUndo.value = null
    }
    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                val chosen = overview?.people.orEmpty().filter { it.id in selected }
                TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                    title = { Text(if (selected.size == 1) "1 selezionata" else "${selected.size} selezionate") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Annulla selezione") } },
                    actions = {
                        val allFav = chosen.isNotEmpty() && chosen.all { it.entity.favorite }
                        IconButton(onClick = { chosen.forEach { vm.setPersonFavorite(it.id, !allFav) }; selected = emptySet() }) {
                            Icon(if (allFav) Icons.Filled.Star else Icons.Filled.StarBorder, if (allFav) "Togli dai preferiti" else "Aggiungi ai preferiti")
                        }
                        if (selected.size >= 2) TextButton(onClick = { askMerge = true }) { com.cripta.app.ui.components.ButtonText("Unisci") }
                        IconButton(onClick = { chosen.forEach { vm.ignorePerson(it.id) }; selected = emptySet() }) {
                            Icon(Icons.Filled.VisibilityOff, "Nascondi")
                        }
                    })
            } else
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(), title = { Text("Persone") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordina") }
                        DropdownMenu(sortMenu && vaultUnlocked(), { sortMenu = false }) {
                            com.cripta.app.data.PeopleSort.entries.forEach { k ->
                                DropdownMenuItem(text = { Text(sortLabel(k)) },
                                    leadingIcon = { androidx.compose.material3.RadioButton(selected = k == sortKey, onClick = null) },
                                    onClick = { vm.setPeopleSort(k, st.peopleSortAscending) })
                            }
                            androidx.compose.material3.HorizontalDivider()
                            val (desc, asc) = sortDirections(sortKey)
                            DropdownMenuItem(text = { Text(desc) },
                                leadingIcon = { androidx.compose.material3.RadioButton(selected = !st.peopleSortAscending, onClick = null) },
                                onClick = { vm.setPeopleSort(sortKey, false) })
                            DropdownMenuItem(text = { Text(asc) },
                                leadingIcon = { androidx.compose.material3.RadioButton(selected = st.peopleSortAscending, onClick = null) },
                                onClick = { vm.setPeopleSort(sortKey, true) })
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                        DropdownMenu(menu && vaultUnlocked(), { menu = false }) {
                            DropdownMenuItem(text = { Text("Diagnostica") }, onClick = { menu = false; showStats = true })
                            DropdownMenuItem(text = { Text("Analizza i media mancanti") }, enabled = progress == null, onClick = { menu = false; vm.startFaceScan() })
                        }
                    }
                })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val o = overview
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Lettura…") }
            return@Scaffold
        }
        val shown = sortPeople(o.people.filter { filter.matches(it) && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) },
            sortKey, st.peopleSortAscending)
        val noFaces = remember(o) {
            o.scans.filter { s -> s.status != "ok" || s.faces == 0 || s.faces == s.rejects.split(',').sumOf { it.substringAfter('=', "0").toIntOrNull() ?: 0 } }
        }
        // "Da sistemare": grouped again whenever the unassigned faces change.
        val groupsVersion by vm.groupsVersion.collectAsStateWithLifecycle()
        val groups by produceState(if (filter == PeopleFilter.TODO) vm.lastGroups else null, filter, o.unassigned, groupsVersion) {
            value = if (filter == PeopleFilter.TODO) vm.unsortedGroups().also { vm.lastGroups = it } else null
        }
        // "Solo persone note": "Da sistemare" becomes "Da confermare", the faces like someone known.
        val known = st.facesMode == "known"
        val knownList by produceState(if (known) vm.lastKnown else null, known, o.unassigned, o.people.size, groupsVersion) {
            value = if (known) runCatching { vm.knownSuggestions() }.getOrNull() else null
        }
        var answered by remember { mutableStateOf(emptySet<Long>()) }
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        /** The person under a finger held for 60 ms: pressed, as a cell in Cartelle. */
        var pressedId by remember { mutableStateOf<Long?>(null) }
        val order = shown.map { it.id }
        val currentOrder by androidx.compose.runtime.rememberUpdatedState(order)
        val currentSelected by androidx.compose.runtime.rememberUpdatedState(selected)
        fun personAt(pos: androidx.compose.ui.geometry.Offset): Long? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { i ->
            pos.x >= i.offset.x && pos.x <= i.offset.x + i.size.width && pos.y >= i.offset.y && pos.y <= i.offset.y + i.size.height
        }?.key as? Long
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize().padding(pad)
            // As in Cartelle: tap opens (or toggles while selecting); a long press selects, and
            // dragging from it picks (or drops, if it started on a picked one) everyone in between.
            .pointerInput(Unit) {
                detectTapGestures(onPress = { off ->
                    val id = personAt(off)
                    if (id != null) {
                        val job = scope.launch { kotlinx.coroutines.delay(60); pressedId = id }
                        tryAwaitRelease()
                        job.cancel(); pressedId = null
                    }
                }, onTap = { off ->
                    val id = personAt(off) ?: return@detectTapGestures
                    if (currentSelected.isNotEmpty()) selected = if (id in currentSelected) currentSelected - id else currentSelected + id
                    else vm.openPerson(id)
                })
            }
            .pointerInput(Unit) {
                var anchor: Long? = null
                var deselect = false
                var base = emptySet<Long>()
                var pointer = androidx.compose.ui.geometry.Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = { off ->
                        pointer = off
                        anchor = personAt(off)?.takeIf { it in currentOrder }
                        anchor?.let { a ->
                            deselect = a in currentSelected; base = currentSelected
                            selected = if (deselect) base - a else base + a
                        }
                    },
                    onDrag = { change, amount ->
                        pointer += amount
                        val a = anchor ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        val cur = personAt(pointer) ?: return@detectDragGesturesAfterLongPress
                        val ai = currentOrder.indexOf(a); val ci = currentOrder.indexOf(cur)
                        if (ai >= 0 && ci >= 0) {
                            val range = currentOrder.subList(minOf(ai, ci), maxOf(ai, ci) + 1).toSet()
                            selected = if (deselect) base - range else base + range
                        }
                    },
                    onDragEnd = { anchor = null },
                    onDragCancel = { anchor = null },
                )
            },
            state = gridState, contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (progress != null || o.scans.size < o.media) item(span = { GridItemSpan(maxLineSpan) }) {
                ScanStatus(o, progress, onStop = { vm.cancelFaceScan() })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                com.cripta.app.ui.components.CriptaTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Cerca per nome") }, modifier = Modifier.fillMaxWidth())
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeopleFilter.entries.forEach { f ->
                        val n = when (f) { PeopleFilter.NONE -> noFaces.size; PeopleFilter.TODO -> o.unassigned; else -> o.people.count { f.matches(it) } }
                        val todo = f == PeopleFilter.TODO
                        val label = if (todo && known) "Da confermare" else f.label
                        val count = if (todo && known) knownList?.size ?: 0 else n
                        if (count > 0 || f == PeopleFilter.MAIN || f == filter) com.cripta.app.ui.components.ChoiceChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} · $n") })
                    }
                }
            }
            if (filter == PeopleFilter.NONE) {
                // All worth a deeper look, unreadable ones too (the deep search reads frames another way).
                val again = noFaces.map { it.fileId }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Media in cui nessun volto è utilizzabile. Tocca per aprirlo e aggiungere un volto a mano dai dettagli; tieni premuto per il motivo.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Already searched the usual way: the deeper search (more frames, smaller and
                        // less sure faces) is the one worth running here.
                        if (again.isNotEmpty()) {
                            FilledTonalButton(onClick = { vm.analyseAgain(again, deep = true) }, enabled = progress == null) {
                                com.cripta.app.ui.components.ButtonText(if (again.size == 1) "Cerca più a fondo in questo media" else "Cerca più a fondo in ${again.size} media")
                            }
                            Text("Più fotogrammi, volti più piccoli o meno nitidi: richiede più tempo.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(noFaces, key = { it.fileId }) { scan ->
                    val file = o.files[scan.fileId]
                    // Tap: the media itself (swiping goes through this list), to add the faces by hand.
                    Column(Modifier.clip(MaterialTheme.shapes.medium).combinedClickable(onClickLabel = "Apri", onLongClickLabel = "Perché",
                        onLongClick = { vm.describeMedia(scan.fileId) }) {
                        vm.openPersonMedia(noFaces.map { it.fileId }, scan.fileId, null); onOpenFile(scan.fileId)
                    }) {
                        if (file != null) MediaThumb(file, { vm.thumbOf(it) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false)
                        Text(file?.originalName ?: "File non trovato", style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            scan.status == FaceRepository.CLEARED -> "Volti eliminati da te"
                            scan.status != "ok" -> "Non letto: " + scan.status.removePrefix("skip:")
                            scan.faces == 0 -> "Nessun volto"
                            else -> "Volti scartati"
                        },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            } else if (filter == PeopleFilter.TODO && known) {
                val list = knownList?.filter { it.suggestion.card.id !in answered }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(when {
                        list == null -> "Confronto i volti con le persone note…"
                        list.isEmpty() -> "Niente da confermare. Qui arrivano i volti simili a chi ha volti in almeno 2 media."
                        else -> "Volti simili a persone note, i più simili prima. Nulla viene aggiunto senza un sì."
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list.orEmpty(), key = { "k" + it.suggestion.card.id }, span = { GridItemSpan(maxLineSpan) }) { ps ->
                    val who = o.people.firstOrNull { it.id == ps.personId }
                    val s = ps.suggestion
                    if (who != null) com.cripta.app.ui.people.SuggestionItem(s, who, o.files[s.card.fileId], vm,
                        onYes = { answered = answered + s.card.id; vm.acceptSuggestion(s.card.id, who.id) },
                        onNo = { answered = answered + s.card.id; vm.refuseSuggestion(s.card.id, who.id) },
                        onOpen = { vm.openPersonMedia(listOf(s.card.fileId), s.card.fileId, s.card.timeMs); onOpenFile(s.card.fileId) },
                        modifier = Modifier.animateItem())
                }
            } else if (filter == PeopleFilter.TODO) {
                val g = groups
                val many = g.orEmpty().count { it.cards.size > 1 }
                val single = g.orEmpty().count { it.cards.size == 1 }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(when {
                            g == null -> "Raggruppo i volti…"
                            g.isEmpty() -> "Nessun volto da sistemare."
                            else -> listOfNotNull(
                                many.takeIf { it > 0 }?.let { if (it == 1) "1 gruppo di volti simili" else "$it gruppi di volti simili" },
                                single.takeIf { it > 0 }?.let { if (it == 1) "1 volto visto una volta" else "$it volti visti una volta" },
                            ).joinToString(", ") + ". Uno alla volta, a tutto schermo: chi è, dividi o elimina."
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!g.isNullOrEmpty()) FilledTonalButton(onClick = { sorting = true }, modifier = Modifier.fillMaxWidth()) {
                            com.cripta.app.ui.components.ButtonText("Sistema i volti")
                        }
                    }
                }
            } else {
                if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    val withFaces = o.scans.filter { it.status == "ok" && it.faces > 0 }
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            o.scans.isEmpty() -> Text("Nessun media analizzato: avvia l'analisi dal menu in alto.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            filter == PeopleFilter.MAIN && o.people.none { it.main } -> {
                                Text(if (withFaces.isEmpty()) "Ancora nessuna persona" else "Volti trovati in ${withFaces.size} media, nessuna persona ancora",
                                    style = MaterialTheme.typography.titleMedium)
                                Text("Le persone le crei tu: apri una foto o un video, scorri su per i dettagli e in Persone tocca un volto, " +
                                    "poi \"Nuova persona\". Il nome è facoltativo.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                withFaces.firstOrNull()?.let { first ->
                                    FilledTonalButton(onClick = { onOpenFile(first.fileId) }) { com.cripta.app.ui.components.ButtonText("Apri un media con volti") }
                                }
                            }
                            else -> Text("Nessuna persona con questo filtro.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(shown, key = { it.id }) { p ->
                    val on = p.id in selected
                    Column(Modifier.animateItem().pressScale(pressedId == p.id).semantics(mergeDescendants = true) {
                        this.selected = on
                        onClick(label = if (selected.isEmpty()) "Apri ${p.title}" else if (on) "Deseleziona" else "Seleziona") {
                            if (selected.isEmpty()) vm.openPerson(p.id) else selected = if (on) selected - p.id else selected + p.id
                            true
                        }
                        onLongClick(label = "Seleziona") { selected = selected + p.id; true }
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            FaceAvatar(p.cover, { vm.faceCrop(it) }, 96.dp, selected = on)
                            if (!p.entity.ignored && selected.isEmpty()) FavoriteStar(p.entity.favorite, p.title, Modifier.align(Alignment.TopEnd)) {
                                vm.setPersonFavorite(p.id, !p.entity.favorite)
                            }
                        }
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (on) MaterialTheme.colorScheme.primary
                                else if (p.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("${p.media.size} media",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        if (showStats) StatsDialog(vm) { showStats = false }
        if (sorting) groups?.let { g -> TodoPager(g, o, vm, onClose = { sorting = false }, onOpenFile = onOpenFile) }
        else naming?.let { ids -> WhoDialog(ids, o, vm) { naming = null } }
        if (askMerge) {
            // The user picks who stays (name, face, star); a named one is offered first.
            val chosen = shown.filter { it.id in selected }
            var keepId by remember { mutableStateOf((chosen.firstOrNull { it.entity.name.isNotBlank() } ?: chosen.firstOrNull())?.id) }
            val into = chosen.firstOrNull { it.id == keepId }
            if (chosen.size < 2) askMerge = false
            else CriptaAlertDialog(onDismissRequest = { askMerge = false },
                title = { Text("Unire ${chosen.size} persone?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Diventano una sola persona con tutti i volti e i media. Scegli chi resta (nome e volto). Si può annullare.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.heightIn(max = 320.dp).selectableGroup()) {
                            items(chosen, key = { it.id }) { q ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
                                    .selectable(selected = q.id == keepId, role = androidx.compose.ui.semantics.Role.RadioButton) { keepId = q.id },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    androidx.compose.material3.RadioButton(selected = q.id == keepId, onClick = null)
                                    FaceAvatar(q.cover, { vm.faceCrop(it) }, 40.dp)
                                    Text("${q.title} · ${q.media.size} media", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(enabled = into != null, onClick = {
                    into?.let { vm.mergePeople(it.id, chosen.map { c -> c.id } - it.id) }; askMerge = false; selected = emptySet()
                }) { com.cripta.app.ui.components.ButtonText("Unisci") } },
                dismissButton = { TextButton(onClick = { askMerge = false }) { com.cripta.app.ui.components.ButtonText("Annulla") } })
        }
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text("Perché") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { com.cripta.app.ui.components.ButtonText("OK") } })
    }
}

/**
 * "Da sistemare" one group at a time, full screen, swiped up like "Forse è …": the frame of the
 * face in view takes the free space (tap plays from there), the group's faces in a row under it
 * (tap leaves one out or puts it back, and shows its frame; long press only shows it), then
 * "Chi è?" and "Dividi" across the width, the deletions as text under them. Faces seen once come
 * last, one per page. An answer regroups the faces and the next group takes the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoPager(groups: List<FaceRepository.FaceGroup>, o: FaceRepository.Overview, vm: SettingsViewModel,
                      onClose: () -> Unit, onOpenFile: (String) -> Unit) {
    val pages = remember(groups) { groups.filter { it.cards.size > 1 } + groups.filter { it.cards.size == 1 } }
    // Back from a media: the same page (the groups are kept by the view model).
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = vm.lastTodoPage.coerceIn(0, maxOf(0, pages.size - 1))) { pages.size }
    LaunchedEffect(pager) { androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { vm.lastTodoPage = it } }
    var naming by remember { mutableStateOf<List<Long>?>(null) }
    val undo by vm.faceUndo.collectAsStateWithLifecycle()
    val close = { vm.lastTodoPage = 0; onClose() }
    com.cripta.app.ui.components.CriptaDialog(onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = {
                    Column {
                        Text("Da sistemare")
                        if (pages.isNotEmpty()) Text("${(pager.currentPage + 1).coerceAtMost(pages.size)} di ${pages.size}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
        }, bottomBar = {
            // The snackbar of the page is under this screen: "Annulla" lives here instead.
            androidx.compose.animation.AnimatedVisibility(undo != null,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(undo?.first.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.undoFaceChange() }) { com.cripta.app.ui.components.ButtonText("Annulla") }
                }
            }
        }) { pad ->
            if (pages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Nessun volto da sistemare.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }
            androidx.compose.foundation.pager.VerticalPager(state = pager, modifier = Modifier.fillMaxSize().padding(pad),
                key = { pages.getOrNull(it)?.cards?.first()?.id ?: it }, beyondViewportPageCount = 2) { page ->
                val g = pages.getOrNull(page) ?: return@VerticalPager
                TodoPage(g, vm, onName = { naming = it }, onOpen = { c ->
                    vm.openPersonMedia(listOf(c.fileId), c.fileId, c.timeMs); onOpenFile(c.fileId)
                })
            }
        }
    }
    naming?.let { ids -> WhoDialog(ids, o, vm) { naming = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoPage(g: FaceRepository.FaceGroup, vm: SettingsViewModel, onName: (List<Long>) -> Unit,
                     onOpen: (com.cripta.app.data.db.TrackCard) -> Unit) {
    var off by remember(g) { mutableStateOf(emptySet<Long>()) }
    var focus by remember(g) { mutableStateOf(g.cards.first()) }
    val picked = g.cards.map { it.id }.filter { it !in off }
    val many = g.cards.size > 1
    val frame by produceState<android.graphics.Bitmap?>(null, focus.id) { value = runCatching { vm.frameOf(focus) }.getOrNull() }
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val picture: @Composable (Modifier) -> Unit = { m ->
        Box(m.clip(MaterialTheme.shapes.large).clickable(onClickLabel = "Riproduci da qui") { onOpen(focus) }, contentAlignment = Alignment.Center) {
            if (frame == null) FaceAvatar(focus, { vm.faceCrop(it) }, 180.dp)
            else com.cripta.app.ui.people.FrameWithFace(frame, focus.box, Modifier.fillMaxSize())
        }
    }
    val controls: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(if (many) "${g.cards.size} volti simili in ${g.cards.map { it.fileId }.distinct().size} media" else "Un volto visto una volta",
                    style = MaterialTheme.typography.titleMedium)
                Text(when {
                    !many -> "Di chi è? Tocca il fotogramma per vederlo nel video."
                    off.isEmpty() -> "Tocca i volti di un'altra persona per toglierli; tieni premuto per vederne il fotogramma."
                    else -> "«Dividi»: quelli tolti non sono la stessa persona degli altri, e non tornano insieme."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            if (many) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(g.cards, key = { it.id }) { c ->
                    val on = c.id !in off
                    FaceAvatar(c, { vm.faceCrop(it) }, 64.dp,
                        Modifier.clip(CircleShape).combinedClickable(onClickLabel = if (on) "Togli" else "Rimetti", onLongClickLabel = "Mostra il fotogramma",
                            onLongClick = { focus = c }) { off = if (on) off + c.id else off - c.id; focus = c },
                        selected = on)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(enabled = picked.isNotEmpty(), onClick = { onName(picked) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText("Chi è?") }
                if (many) FilledTonalButton(enabled = off.isNotEmpty() && picked.isNotEmpty(), onClick = { vm.keepFacesApart(picked, off.toList()) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText("Dividi") }
            }
            Row {
                TextButton(enabled = picked.isNotEmpty(), onClick = { vm.deleteFaces(picked, everywhere = false) }) {
                    com.cripta.app.ui.components.ButtonText("Elimina i volti", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = picked.isNotEmpty(), onClick = { vm.deleteFaces(picked, everywhere = true) }) {
                    com.cripta.app.ui.components.ButtonText("Non è un volto", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (landscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            picture(Modifier.weight(1.4f).fillMaxHeight())
            controls(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            picture(Modifier.weight(1f).fillMaxWidth())
            controls(Modifier.fillMaxWidth())
        }
    }
}

/** "Chi è?" for a group of faces: someone already there, or a new person. */
@Composable
private fun WhoDialog(ids: List<Long>, o: FaceRepository.Overview, vm: SettingsViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    CriptaAlertDialog(onDismissRequest = onDismiss, title = { Text(if (ids.size == 1) "Chi è?" else "Chi è? · ${ids.size} volti") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.cripta.app.ui.components.CriptaTextField(query, { query = it }, singleLine = true, label = { Text("Nome (cerca o crea)") },
                    modifier = Modifier.fillMaxWidth())
                val list = o.people.filter { !it.entity.ignored && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
                    .sortedWith(compareBy({ !it.entity.favorite }, { -it.entity.createdAt }))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    if (query.isBlank() || o.people.none { it.entity.name.equals(query.trim(), ignoreCase = true) }) item {
                        Text(if (query.isBlank()) "+ Nuova persona senza nome (Volto…)" else "+ Nuova persona «${query.trim()}»", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable { vm.assignFaces(ids, null, query); onDismiss() }.padding(vertical = 12.dp))
                    }
                    items(list, key = { it.id }) { q ->
                        Row(Modifier.fillMaxWidth().clickable { vm.assignFaces(ids, q.id, ""); onDismiss() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FaceAvatar(q.cover, { vm.faceCrop(it) }, 48.dp)
                            Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                color = if (q.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            if (q.entity.favorite) Icon(Icons.Filled.Star, "Preferita", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText("Chiudi") } })
}

/** Pick people ([multi]) or one person, or name a new one ([allowNew]: "Dividi"). */
@Composable
internal fun PeoplePicker(title: String, o: FaceRepository.Overview, exclude: Long, multi: Boolean, vm: SettingsViewModel,
                         allowNew: Boolean = false, onDismiss: () -> Unit, onDone: (List<Long>, String) -> Unit) {
    var picked by remember { mutableStateOf(emptyList<Long>()) }
    var newName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    // The main people at first; a search reaches everyone (faces seen once included).
    val q = query.trim()
    val others = o.people.filter { it.id != exclude && !it.entity.ignored &&
        (if (q.isEmpty()) it.main || it.id in picked else it.title.contains(q, ignoreCase = true)) }
    CriptaAlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowNew) com.cripta.app.ui.components.CriptaTextField(newName, { newName = it; if (it.isNotBlank()) picked = emptyList() }, singleLine = true,
                    label = { Text("Nuova persona (dividi)") }, modifier = Modifier.fillMaxWidth())
                com.cripta.app.ui.components.CriptaTextField(query, { query = it }, singleLine = true,
                    placeholder = { Text("Cerca tra tutte le persone") }, modifier = Modifier.fillMaxWidth())
                if (others.isEmpty()) Text("Nessuna persona trovata.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(others, key = { it.id }) { q ->
                        val on = q.id in picked
                        Row(Modifier.fillMaxWidth().clickable {
                            picked = if (multi) (if (on) picked - q.id else picked + q.id) else listOf(q.id); newName = ""
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(on, null)
                            FaceAvatar(q.cover, { vm.faceCrop(it) }, 40.dp)
                            Text("${q.title} · ${q.media.size}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (q.entity.favorite) Icon(Icons.Filled.Star, "Preferita", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty() || (allowNew && newName.isNotBlank()), onClick = { onDone(picked, newName) }) {
                com.cripta.app.ui.components.ButtonText(if (allowNew && picked.isEmpty()) "Dividi" else if (multi) "Unisci" else "Sposta")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText("Annulla") } })
}

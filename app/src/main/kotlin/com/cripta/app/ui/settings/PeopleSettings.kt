package com.cripta.app.ui.settings

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
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
import androidx.compose.runtime.collectAsState
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
    val overview by vm.people.collectAsState()
    val progress by vm.faceProgress.collectAsState()
    var askRestart by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
    Section("Persone", "Cripta trova i volti di foto e video, in qualunque cartella; sei tu a dire chi sono. " +
        "Tutto avviene sul telefono; volti e persone restano nel database cifrato.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScanStatus(overview, progress, onStop = { vm.cancelFaceScan() })
            FilledTonalButton(onClick = { vm.openPeople() }, modifier = Modifier.fillMaxWidth()) {
                Text("Vedi le persone")
            }
        }
    }
    Section("Analisi dei volti") {
        ToggleRow("Analizza i nuovi media", s.facesAnalyzeNew, desc = "Dopo ogni importazione, download o conversione, in background.",
            icon = Icons.Filled.Face) { vm.setFacesAnalyzeNew(it) }
        FilledTonalButton(onClick = { vm.startFaceScan() }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
            Text(if (progress == null) "Analizza foto e video mancanti" else "Analisi in corso…")
        }
        Text("L'analisi trova i volti; sei tu a dire chi sono, dai dettagli di ogni media.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(onClick = { showStats = true }) { Text("Statistiche") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { askRestart = true }, enabled = progress == null) { Text("Ricomincia da capo", color = MaterialTheme.colorScheme.error) }
        }
    }
    Section("Visualizzazione") {
        ToggleRow("Momenti nel lettore", s.faceMoments,
            desc = "Segni sulla barra del video dove compaiono le persone; nel pannello info ogni volto elenca i suoi momenti.",
            icon = Icons.Filled.Timeline) { vm.setFaceMoments(it) }
        Text("Persone in Home", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("off" to "Nascoste", "blur" to "Sfocate", "on" to "Visibili").forEach { (k, label) ->
                com.cripta.app.ui.components.ChoiceChip(selected = s.peopleOnHome == k, onClick = { vm.setPeopleOnHome(k) }, label = { Text(label) })
            }
        }
        Text(when (s.peopleOnHome) {
            "off" -> "La Home non mostra volti."
            "blur" -> "I volti in Home restano sfocati finché non tocchi la riga: utile in pubblico."
            else -> "I volti in Home sono subito visibili."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            confirmButton = { TextButton(onClick = { vm.restartFaces(everything); askRestart = false }) { Text("Ricomincia") } },
            dismissButton = { TextButton(onClick = { askRestart = false }) { Text("Annulla") } })
    }
    if (showStats) StatsDialog(vm) { showStats = false }
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
                TextButton(onClick = onStop) { Text("Ferma") }
            }
            LinearProgressIndicator(progress = { progress.done.toFloat() / maxOf(1, progress.total) }, modifier = Modifier.fillMaxWidth())
            Text("Le persone compaiono e si aggiornano mentre l'analisi procede.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (o == null) {
            Text("Lettura dello stato…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val ok = o.scans.count { it.status == "ok" }
            val skipped = o.scans.size - ok
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
                    TextButton(onClick = { attempt++ }) { Text("Riprova") }
                    return@Column
                }
                Text("Media analizzati: ${x.media}" + if (x.skipped > 0) " (${x.skipped} non leggibili)" else "")
                if (x.videos > 0) Text("Tempo medio per video: %.1f s".format(x.videoMs / 1000.0 / x.videos))
                if (x.photos > 0) Text("Tempo medio per foto: %.2f s".format(x.photoMs / 1000.0 / x.photos))
                Text("Volti trovati: ${x.faces}")
                if (x.rejects.isNotEmpty()) Text("Scartati: " + x.rejects.entries.joinToString(", ") { "${it.value} ${it.key.label}" })
                // The algorithm's numbers stay available, folded away under what a person needs to know.
                TextButton(onClick = { tech = !tech }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (tech) "Nascondi i dettagli tecnici" else "Dettagli tecnici")
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
        confirmButton = { TextButton(onClick = onClose) { Text("Chiudi") } })
}

/** A face picture from the database, round. */
@Composable
internal fun FaceAvatar(trackId: Long?, load: suspend (Long) -> android.graphics.Bitmap?, size: Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<android.graphics.Bitmap?>(null, trackId) { value = trackId?.let { load(it) } }
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
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
    MAIN("Persone"), FAVORITE("Preferite"), NAMED("Con nome"), SINGLE("Una volta"), IGNORED("Ignorate"), NONE("Senza volti");

    fun matches(p: FaceRepository.Person) = when (this) {
        MAIN -> p.main
        FAVORITE -> !p.entity.ignored && p.entity.favorite
        NAMED -> !p.entity.ignored && p.entity.name.isNotBlank()
        SINGLE -> !p.entity.ignored && !p.main
        IGNORED -> p.entity.ignored
        NONE -> false
    }
}

/** The people page: its own destination (Settings, Home, the scan's notification lead here). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(onBack: () -> Unit, onOpenFile: (String) -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val overview by vm.people.collectAsState()
    val progress by vm.faceProgress.collectAsState()
    val message by vm.faceMessage.collectAsState()
    val undo by vm.faceUndo.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf(PeopleFilter.MAIN) }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        if (snackbar.showSnackbar(u.first, "Annulla", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoFaceChange()
        else if (vm.faceUndo.value === u) vm.faceUndo.value = null
    }
    Scaffold(
        topBar = {
            TopAppBar(colors = com.cripta.app.ui.components.criptaTopBarColors(), title = { Text("Persone") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
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
        val shown = o.people.filter { filter.matches(it) && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
        val noFaces = remember(o) {
            o.scans.filter { s -> s.status != "ok" || s.faces == 0 || s.faces == s.rejects.split(',').sumOf { it.substringAfter('=', "0").toIntOrNull() ?: 0 } }
        }
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
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
                        val n = if (f == PeopleFilter.NONE) noFaces.size else o.people.count { f.matches(it) }
                        if (n > 0 || f == PeopleFilter.MAIN || f == filter) com.cripta.app.ui.components.ChoiceChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} · $n") })
                    }
                }
            }
            if (filter == PeopleFilter.NONE) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Media in cui nessun volto è utilizzabile. Tocca per il motivo.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(noFaces, key = { it.fileId }) { scan ->
                    val file = o.files[scan.fileId]
                    Column(Modifier.clickable(onClickLabel = "Perché") { vm.describeMedia(scan.fileId) }) {
                        if (file != null) MediaThumb(file, { vm.thumbOf(it) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false)
                        Text(file?.originalName ?: "File non trovato", style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (scan.status != "ok") "Non leggibile" else if (scan.faces == 0) "Nessun volto" else "Volti scartati",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
                                    FilledTonalButton(onClick = { onOpenFile(first.fileId) }) { Text("Apri un media con volti") }
                                }
                            }
                            else -> Text("Nessuna persona con questo filtro.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(shown, key = { it.id }) { p ->
                    Column(Modifier.clickable(onClickLabel = "Apri ${p.title}") { openId = p.id }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            FaceAvatar(p.cover?.id, { vm.faceCrop(it) }, 96.dp,
                                Modifier)
                            if (!p.entity.ignored) FavoriteStar(p.entity.favorite, p.title, Modifier.align(Alignment.TopEnd)) {
                                vm.setPersonFavorite(p.id, !p.entity.favorite)
                            }
                        }
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (p.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("${p.media.size} media",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        openId?.let { id ->
            o.people.firstOrNull { it.id == id }?.let { p -> PersonPage(p, o, vm, onClose = { openId = null }, onOpenFile = onOpenFile, onOpenPerson = { openId = it }) }
                ?: run { openId = null }
        }
        if (showStats) StatsDialog(vm) { showStats = false }
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text("Perché") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { Text("OK") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PersonPage(p: FaceRepository.Person, o: FaceRepository.Overview, vm: SettingsViewModel, onClose: () -> Unit,
                       onOpenFile: (String) -> Unit, onOpenPerson: (Long) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    /** Selected media (long press starts the selection). */
    var selected by remember(p.id) { mutableStateOf(emptySet<String>()) }
    var moving by remember { mutableStateOf(false) }
    var selMenu by remember { mutableStateOf(false) }
    val faceOf = remember(p) { p.faces.groupBy { it.fileId }.mapValues { (_, l) -> l.maxBy { it.width } } }
    var comparing by remember { mutableStateOf<Pair<com.cripta.app.data.db.TrackCard, com.cripta.app.data.db.TrackCard>?>(null) }
    val ids = p.media.map { it.id }
    fun open(fileId: String, timeMs: Long?) { vm.openPersonMedia(ids, fileId, timeMs); onOpenFile(fileId) }
    CriptaDialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            if (selected.isNotEmpty()) TopAppBar(
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { Text("${selected.size} selezionati") },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Annulla selezione") } },
                actions = {
                    TextButton(onClick = { vm.removeFromPerson(p.id, selected); selected = emptySet() }) { Text("Togli") }
                    TextButton(onClick = { moving = true }) { Text("Sposta") }
                    Box {
                        IconButton(onClick = { selMenu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                        DropdownMenu(selMenu && vaultUnlocked(), { selMenu = false }) {
                            val one = selected.singleOrNull()
                            val face = one?.let { faceOf[it] }
                            if (face != null) {
                                DropdownMenuItem(text = { Text("Usa come suo volto") }, onClick = { selMenu = false; vm.setPersonCover(p.id, face.id); selected = emptySet() })
                            }
                            if (one != null) DropdownMenuItem(text = { Text("Volti di questo media") }, onClick = { selMenu = false; vm.describeMedia(one) })
                            val two = selected.toList().takeIf { it.size == 2 }?.mapNotNull { faceOf[it] }?.takeIf { it.size == 2 }
                            if (two != null) DropdownMenuItem(text = { Text("Confronta i due volti") }, onClick = {
                                selMenu = false; comparing = two[0] to two[1]
                            })
                        }
                    }
                },
            ) else TopAppBar(colors = com.cripta.app.ui.components.criptaTopBarColors(), title = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = { vm.setPersonFavorite(p.id, !p.entity.favorite) }) {
                        Icon(if (p.entity.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            if (p.entity.favorite) "Togli dai preferiti" else "Aggiungi ai preferiti",
                            tint = if (p.entity.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { renaming = true }) { Text("Rinomina") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                        DropdownMenu(menu && vaultUnlocked(), { menu = false }) {
                            DropdownMenuItem(text = { Text("Unisci con…") }, onClick = { menu = false; merging = true })
                            if (p.entity.ignored) DropdownMenuItem(text = { Text("Ripristina") }, onClick = { menu = false; vm.restorePerson(p.id) })
                            // With no media left, the useful choice is to remove the profile, not to call it "not a person".
                            else if (p.media.isEmpty()) DropdownMenuItem(text = { Text("Elimina la persona") }, onClick = { menu = false; vm.deleteEmptyPerson(p.id); onClose() })
                            else DropdownMenuItem(text = { Text("Non è una persona") }, onClick = { menu = false; vm.ignorePerson(p.id); onClose() })
                        }
                    }
                })
        }) { pad ->
            LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text((if (p.entity.ignored) "Ignorata: i volti simili vengono nascosti. " else "") +
                        if (p.media.isEmpty()) "Nessun media collegato. Il nome resta finché non elimini la persona dal menu in alto. Per aggiungere media, " +
                            "apri una foto o un video, scorri su per i dettagli e in Persone scegli ${p.title}."
                        else "${p.media.size} media. Tocca per aprire, tieni premuto per selezionare e togliere, spostare o dividere.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(p.media, key = { it.id }) { f ->
                    val face = faceOf[f.id]
                    val on = f.id in selected
                    Box(Modifier.combinedClickable(onClickLabel = if (selected.isEmpty()) "Apri" else "Seleziona", onLongClickLabel = "Seleziona",
                        onClick = { if (selected.isEmpty()) open(f.id, face?.timeMs) else selected = if (on) selected - f.id else selected + f.id },
                        onLongClick = { selected = selected + f.id })) {
                        MediaThumb(f, { vm.thumbOf(it) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false, selected = on,
                            selectionMode = selected.isNotEmpty())
                        if (face != null) FaceAvatar(face.id, { vm.faceCrop(it) }, 36.dp, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                    }
                }
            }
        }
        if (renaming) {
            var name by remember { mutableStateOf(p.entity.name) }
            CriptaAlertDialog(onDismissRequest = { renaming = false }, title = { Text("Nome della persona") },
                text = {
                    Column {
                        com.cripta.app.ui.components.CriptaTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val twin = o.people.firstOrNull { it.id != p.id && name.isNotBlank() && it.entity.name.equals(name.trim(), ignoreCase = true) }
                        Text(if (twin != null) "Esiste già «${twin.title}»: dopo il salvataggio puoi unirle da «Unisci con…»."
                            else "Il nome vale per tutti i suoi media. Un volto sbagliato si toglie tenendo premuto il media.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (twin != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.renamePerson(p.id, name); renaming = false }) { Text("Salva") } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annulla") } })
        }
        comparing?.let { (a, b) -> CompareDialog(a, b, o, vm) { comparing = null } }
        if (merging) PeoplePicker("Unisci a ${p.title}", o, exclude = p.id, multi = true, vm = vm, onDismiss = { merging = false }) { picked, _ ->
            vm.mergePeople(p.id, picked); merging = false
        }
        if (moving) PeoplePicker("Sposta ${selected.size} media a…", o, exclude = p.id, multi = false, vm = vm, allowNew = true,
            onDismiss = { moving = false }) { picked, newName ->
            vm.moveToPerson(p.id, selected, picked.firstOrNull(), newName); moving = false; selected = emptySet()
        }
    }
}

/** Pick people ([multi]) or one person, or name a new one ([allowNew]: "Dividi"). */
@Composable
private fun PeoplePicker(title: String, o: FaceRepository.Overview, exclude: Long, multi: Boolean, vm: SettingsViewModel,
                         allowNew: Boolean = false, onDismiss: () -> Unit, onDone: (List<Long>, String) -> Unit) {
    var picked by remember { mutableStateOf(emptyList<Long>()) }
    var newName by remember { mutableStateOf("") }
    val others = o.people.filter { it.id != exclude && !it.entity.ignored && (it.main || it.id in picked) }
    CriptaAlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowNew) com.cripta.app.ui.components.CriptaTextField(newName, { newName = it; if (it.isNotBlank()) picked = emptyList() }, singleLine = true,
                    label = { Text("Nuova persona (dividi)") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(others, key = { it.id }) { q ->
                        val on = q.id in picked
                        Row(Modifier.fillMaxWidth().clickable {
                            picked = if (multi) (if (on) picked - q.id else picked + q.id) else listOf(q.id); newName = ""
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(on, null)
                            FaceAvatar(q.cover?.id, { vm.faceCrop(it) }, 40.dp)
                            Text("${q.title} · ${q.media.size}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (q.entity.favorite) Icon(Icons.Filled.Star, "Preferita", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty() || (allowNew && newName.isNotBlank()), onClick = { onDone(picked, newName) }) {
                Text(if (allowNew && picked.isEmpty()) "Dividi" else if (multi) "Unisci" else "Sposta")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

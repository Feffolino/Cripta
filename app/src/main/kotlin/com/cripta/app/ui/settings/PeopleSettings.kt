package com.cripta.app.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cripta.app.data.Settings
import com.cripta.app.data.face.FaceRepository
import com.cripta.app.ui.components.ChipFlowRow
import com.cripta.app.ui.components.MediaThumb

/**
 * Settings › "Persone": live status, the analysis settings and maintenance, the full-page people
 * with their faces, and each person's page (media, suggestions, corrections, diagnostics).
 */
@Composable
internal fun PeopleSettings(s: Settings, vm: SettingsViewModel, onOpenFile: (String) -> Unit) {
    val overview by vm.people.collectAsState()
    val progress by vm.faceProgress.collectAsState()
    val open by vm.peopleOpen.collectAsState()
    val message by vm.faceMessage.collectAsState()
    var askRestart by remember { mutableStateOf(false) }

    Section("Persone", "Cripta riconosce i volti di foto e video, in qualunque cartella, e li raggruppa per persona. " +
        "Tutto avviene sul telefono; volti e persone restano nel database cifrato.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val o = overview
            if (o == null) Text("Lettura dello stato…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val ok = o.scans.count { it.status == "ok" }
                val skipped = o.scans.size - ok
                val visible = o.people.count { !it.entity.ignored && (it.media.size >= 2 || it.entity.name.isNotBlank()) }
                Text("Analizzati ${o.scans.size} di ${o.media} media" + (if (skipped > 0) " ($skipped non leggibili)" else "") +
                    " · $visible persone", style = MaterialTheme.typography.bodyMedium)
            }
            progress?.let { p ->
                LinearProgressIndicator(progress = { p.done.toFloat() / maxOf(1, p.total) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.phase}: ${p.done} di ${p.total}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.cancelFaceScan() }) { Text("Ferma") }
                }
            }
            FilledTonalButton(onClick = { vm.peopleOpen.value = true }, modifier = Modifier.fillMaxWidth(), enabled = overview != null) {
                Text("Vedi le persone")
            }
        }
    }
    Section("Analisi dei volti") {
        ToggleRow("Analizza i nuovi media", s.facesAnalyzeNew, desc = "Dopo ogni importazione, download o conversione, in background.",
            icon = Icons.Filled.Face) { vm.setFacesAnalyzeNew(it) }
        FilledTonalButton(onClick = { vm.startFaceScan() }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
            Text(if (progress == null) "Analizza foto e video esistenti" else "Analisi in corso…")
        }
        OutlinedButton(onClick = { vm.regroupFaces() }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
            Text("Ricalcola i gruppi")
        }
        Text("Ricalcola rimette insieme i volti già trovati senza rianalizzare: le persone con un nome e le tue correzioni restano.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { askRestart = true }, enabled = progress == null) { Text("Ricomincia da capo", color = MaterialTheme.colorScheme.error) }
    }
    if (askRestart) {
        var everything by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { askRestart = false },
            title = { Text("Ricominciare da capo?") },
            text = {
                Column {
                    Text("Tutti i media verranno analizzati di nuovo.")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { everything = !everything }) {
                        Checkbox(everything, { everything = it })
                        Text("Cancella anche nomi, unioni e correzioni")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.restartFaces(everything); askRestart = false }) { Text("Ricomincia") } },
            dismissButton = { TextButton(onClick = { askRestart = false }) { Text("Annulla") } })
    }
    if (open) overview?.let { o -> PeoplePage(o, vm, onClose = { vm.peopleOpen.value = false }, onOpenFile = onOpenFile) }
    message?.let { m ->
        AlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text("Perché") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { Text("OK") } })
    }
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

private enum class PeopleFilter(val label: String) { MAIN("Persone"), NAMED("Con nome"), SINGLE("Una volta"), SUGGESTED("Da confermare"), IGNORED("Ignorate"), NONE("Senza volti") }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PeoplePage(o: FaceRepository.Overview, vm: SettingsViewModel, onClose: () -> Unit, onOpenFile: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(PeopleFilter.MAIN) }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    fun matches(p: FaceRepository.Person) = when (filter) {
        PeopleFilter.MAIN -> !p.entity.ignored && (p.media.size >= 2 || p.entity.name.isNotBlank())
        PeopleFilter.NAMED -> !p.entity.ignored && p.entity.name.isNotBlank()
        PeopleFilter.SINGLE -> !p.entity.ignored && p.entity.name.isBlank() && p.media.size < 2
        PeopleFilter.SUGGESTED -> !p.entity.ignored && p.suggestions.isNotEmpty()
        PeopleFilter.IGNORED -> p.entity.ignored
        PeopleFilter.NONE -> false
    }
    val shown = o.people.filter { matches(it) && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
    val noFaces = remember(o) { o.scans.filter { it.status != "ok" || it.faces == 0 || it.rejects.isNotBlank() && it.faces == it.rejects.split(',').sumOf { e -> e.substringAfter('=').toIntOrNull() ?: 0 } } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Persone") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
        }) { pad ->
            LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Cerca per nome") }, modifier = Modifier.fillMaxWidth())
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChipFlowRow {
                        PeopleFilter.entries.forEach { f ->
                            val n = if (f == PeopleFilter.NONE) noFaces.size else o.people.count { p ->
                                when (f) {
                                    PeopleFilter.MAIN -> !p.entity.ignored && (p.media.size >= 2 || p.entity.name.isNotBlank())
                                    PeopleFilter.NAMED -> !p.entity.ignored && p.entity.name.isNotBlank()
                                    PeopleFilter.SINGLE -> !p.entity.ignored && p.entity.name.isBlank() && p.media.size < 2
                                    PeopleFilter.SUGGESTED -> !p.entity.ignored && p.suggestions.isNotEmpty()
                                    else -> p.entity.ignored
                                }
                            }
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} · $n") })
                        }
                    }
                }
                if (filter == PeopleFilter.NONE) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Media analizzati in cui nessun volto è utilizzabile. Tocca per il motivo.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(noFaces, key = { it.fileId }) { scan ->
                        val file = o.files[scan.fileId]
                        Column(Modifier.clickable { vm.describeMedia(scan.fileId) }) {
                            if (file != null) MediaThumb(file, { vm.thumbOf(it) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false)
                            Text(if (scan.status != "ok") "Non leggibile" else if (scan.faces == 0) "Nessun volto" else "Volti scartati",
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                } else {
                    if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(if (o.scans.isEmpty()) "Nessun media analizzato: avvia l'analisi dalle impostazioni." else "Nessuna persona qui.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(shown, key = { it.id }) { p ->
                        Column(Modifier.clickable { openId = p.id }, horizontalAlignment = Alignment.CenterHorizontally) {
                            FaceAvatar(p.cover?.id, { vm.faceCrop(it) }, 96.dp,
                                if (p.suggestions.isNotEmpty()) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.media.size} media" + if (p.suggestions.isNotEmpty()) " · ${p.suggestions.size} da confermare" else "",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
        openId?.let { id ->
            o.people.firstOrNull { it.id == id }?.let { p -> PersonPage(p, o, vm, onClose = { openId = null }, onOpenFile = onOpenFile) }
                ?: run { openId = null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PersonPage(p: FaceRepository.Person, o: FaceRepository.Overview, vm: SettingsViewModel, onClose: () -> Unit, onOpenFile: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    /** The media whose menu is open (long press). */
    var menuFor by remember { mutableStateOf<String?>(null) }
    val faceOf = remember(p) { p.faces.groupBy { it.fileId }.mapValues { (_, l) -> l.maxBy { it.width } } }
    val ids = p.media.map { it.id }
    fun open(fileId: String, timeMs: Long?) { vm.openPersonMedia(ids, fileId, timeMs); onOpenFile(fileId) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    TextButton(onClick = { renaming = true }) { Text("Rinomina") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Unisci con…") }, onClick = { menu = false; merging = true })
                            if (p.entity.ignored) DropdownMenuItem(text = { Text("Ripristina") }, onClick = { menu = false; vm.restorePerson(p.id) })
                            else DropdownMenuItem(text = { Text("Non è una persona") }, onClick = { menu = false; vm.ignorePerson(p.id); onClose() })
                        }
                    }
                })
        }) { pad ->
            LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text((if (p.entity.ignored) "Ignorata: i volti simili vengono nascosti. " else "") +
                        "${p.media.size} media. Tocca per aprire, tieni premuto per togliere un media o capire perché c'è.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("Forse è ${p.title}?", style = MaterialTheme.typography.titleSmall) }
                    items(p.suggestions, key = { "s${it.id}" }) { t ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.clickable { o.files[t.fileId]?.let { open(it.id, t.timeMs) } }) {
                                o.files[t.fileId]?.let { MediaThumb(it, { f -> vm.thumbOf(f) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false) }
                                FaceAvatar(t.id, { vm.faceCrop(it) }, 40.dp, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                            }
                            Row {
                                IconButton(onClick = { vm.confirmFace(t.id, p.id) }) { Icon(Icons.Filled.Check, "È ${p.title}") }
                                IconButton(onClick = { vm.rejectFace(t.id, p.id) }) { Icon(Icons.Filled.Close, "Non è ${p.title}") }
                            }
                            Text("%.2f".format(t.similarity), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("Media", style = MaterialTheme.typography.titleSmall) }
                }
                items(p.media, key = { it.id }) { f ->
                    val face = faceOf[f.id]
                    Box(Modifier.combinedClickable(onClickLabel = "Apri", onLongClickLabel = "Opzioni",
                        onClick = { open(f.id, face?.timeMs) }, onLongClick = { menuFor = f.id })) {
                        MediaThumb(f, { vm.thumbOf(it) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false)
                        if (face != null) FaceAvatar(face.id, { vm.faceCrop(it) }, 36.dp, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                        DropdownMenu(menuFor == f.id, { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Togli da ${p.title}") }, onClick = { menuFor = null; vm.removeFromPerson(p.id, f.id) })
                            if (face != null) {
                                DropdownMenuItem(text = { Text("Usa come suo volto") }, onClick = { menuFor = null; vm.setPersonCover(p.id, face.id) })
                                DropdownMenuItem(text = { Text("Confronta con le altre persone") }, onClick = { menuFor = null; vm.explainFace(face.id) })
                            }
                            DropdownMenuItem(text = { Text("Volti di questo media") }, onClick = { menuFor = null; vm.describeMedia(f.id) })
                        }
                    }
                }
            }
        }
        if (renaming) {
            var name by remember { mutableStateOf(p.entity.name) }
            AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Nome della persona") },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Con un nome, i volti attuali restano a questa persona anche quando ricalcoli i gruppi.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.renamePerson(p.id, name); renaming = false }) { Text("Salva") } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annulla") } })
        }
        if (merging) {
            var picked by remember { mutableStateOf(emptySet<Long>()) }
            val others = o.people.filter { it.id != p.id && !it.entity.ignored }
            AlertDialog(onDismissRequest = { merging = false }, title = { Text("Unisci a ${p.title}") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
                        items(others.size) { i ->
                            val q = others[i]
                            Row(Modifier.fillMaxWidth().clickable { picked = if (q.id in picked) picked - q.id else picked + q.id }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Checkbox(q.id in picked, null)
                                FaceAvatar(q.cover?.id, { vm.faceCrop(it) }, 40.dp)
                                Text("${q.title} · ${q.media.size}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { vm.mergePeople(p.id, picked); merging = false }) { Text("Unisci") } },
                dismissButton = { TextButton(onClick = { merging = false }) { Text("Annulla") } })
        }
    }
}

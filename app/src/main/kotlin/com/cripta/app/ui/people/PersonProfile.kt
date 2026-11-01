package com.cripta.app.ui.people

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.face.FaceRepository
import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.CriptaDialog
import com.cripta.app.ui.components.MediaThumb
import com.cripta.app.ui.components.vaultUnlocked
import com.cripta.app.ui.settings.CompareDialog
import com.cripta.app.ui.settings.FaceAvatar
import com.cripta.app.ui.settings.PeoplePicker
import com.cripta.app.ui.settings.SettingsViewModel

/** How a person's media are ordered on their profile. */
private enum class MediaOrder(val label: String) { NEWEST("Più recenti"), OLDEST("Meno recenti"), NAME("Nome"), SIZE("Più grandi") }

/**
 * A person's profile: their face (tap to pick another one among those found), name and star, the
 * user's notes and links, the faces elsewhere that may be theirs, and below every media they are
 * in. Reached from Home and from the people page.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PersonProfileScreen(personId: Long, onBack: () -> Unit, onOpenFile: (String) -> Unit, onShowInVault: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val overview by vm.people.collectAsStateWithLifecycle()
    val undo by vm.faceUndo.collectAsStateWithLifecycle()
    val message by vm.faceMessage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        if (snackbar.showSnackbar(u.first, "Annulla", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoFaceChange()
        else if (vm.faceUndo.value === u) vm.faceUndo.value = null
    }
    val o = overview
    val p = o?.people?.firstOrNull { it.id == personId }
    // Gone (merged into another, deleted): nothing to show here any more.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(p != null, o != null) { if (p != null) seen = true else if (o != null && seen) onBack() }

    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var pickingCover by remember { mutableStateOf(false) }
    var suggesting by rememberSaveable { mutableStateOf(false) }
    var selected by remember(personId) { mutableStateOf(emptySet<String>()) }
    var moving by remember { mutableStateOf(false) }
    var selMenu by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf<Pair<TrackCard, TrackCard>?>(null) }
    var order by rememberSaveable { mutableStateOf(MediaOrder.NEWEST) }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty() && p != null) TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { Text("${selected.size} selezionati") },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Annulla selezione") } },
                actions = {
                    TextButton(onClick = { vm.removeFromPerson(p.id, selected); selected = emptySet() }) { com.cripta.app.ui.components.ButtonText("Togli") }
                    TextButton(onClick = { moving = true }) { com.cripta.app.ui.components.ButtonText("Sposta") }
                    Box {
                        IconButton(onClick = { selMenu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                        val faceOf = remember(p) { p.faces.groupBy { it.fileId }.mapValues { (_, l) -> l.maxBy { it.width } } }
                        DropdownMenu(selMenu && vaultUnlocked(), { selMenu = false }) {
                            val one = selected.singleOrNull()
                            val face = one?.let { faceOf[it] }
                            if (face != null) DropdownMenuItem(text = { Text("Usa come suo volto") },
                                onClick = { selMenu = false; vm.setPersonCover(p.id, face.id); selected = emptySet() })
                            if (one != null) DropdownMenuItem(text = { Text("Volti di questo media") }, onClick = { selMenu = false; vm.describeMedia(one) })
                            val two = selected.toList().takeIf { it.size == 2 }?.mapNotNull { faceOf[it] }?.takeIf { it.size == 2 }
                            if (two != null) DropdownMenuItem(text = { Text("Confronta i due volti") }, onClick = { selMenu = false; comparing = two[0] to two[1] })
                        }
                    }
                },
            ) else TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { Text(p?.title ?: "Persona", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (p != null) {
                        IconButton(onClick = { vm.setPersonFavorite(p.id, !p.entity.favorite) }) {
                            Icon(if (p.entity.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                if (p.entity.favorite) "Togli dai preferiti" else "Aggiungi ai preferiti",
                                tint = if (p.entity.favorite) com.cripta.app.ui.theme.Favorite else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                            DropdownMenu(menu && vaultUnlocked(), { menu = false }) {
                                DropdownMenuItem(text = { Text("Rinomina") }, onClick = { menu = false; renaming = true })
                                DropdownMenuItem(text = { Text("Mostra in Cartelle") }, onClick = { menu = false; vm.showPersonInVault(p.id); onShowInVault() })
                                DropdownMenuItem(text = { Text("Unisci con…") }, onClick = { menu = false; merging = true })
                                if (p.entity.ignored) DropdownMenuItem(text = { Text("Ripristina") }, onClick = { menu = false; vm.restorePerson(p.id) })
                                else if (p.media.isEmpty()) DropdownMenuItem(text = { Text("Elimina la persona") }, onClick = { menu = false; vm.deleteEmptyPerson(p.id); onBack() })
                                else DropdownMenuItem(text = { Text("Non è una persona") }, onClick = { menu = false; vm.ignorePerson(p.id); onBack() })
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (p == null || o == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Lettura…") }
            return@Scaffold
        }
        val media = remember(p.media, order) {
            when (order) {
                MediaOrder.NEWEST -> p.media.sortedByDescending { it.importedAt }
                MediaOrder.OLDEST -> p.media.sortedBy { it.importedAt }
                MediaOrder.NAME -> p.media.sortedBy { it.originalName.lowercase() }
                MediaOrder.SIZE -> p.media.sortedByDescending { it.sizeBytes }
            }
        }
        val faceOf = remember(p) { p.faces.groupBy { it.fileId }.mapValues { (_, l) -> l.maxBy { it.width } } }
        val ids = media.map { it.id }
        fun open(fileId: String, timeMs: Long?) { vm.openPersonMedia(ids, fileId, timeMs); onOpenFile(fileId) }
        LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(p, vm, onCover = { pickingCover = true }, onRename = { renaming = true })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SuggestionsEntry(p, vm) { suggesting = true }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { NotesAndLinks(p, vm) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Media · ${p.media.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() })
                    if (p.media.isEmpty()) {
                        Text("Nessun media. Apri una foto o un video, scorri su per i dettagli e in Persone scegli ${p.title}.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaOrder.entries.forEach { m ->
                                com.cripta.app.ui.components.ChoiceChip(selected = order == m, onClick = { order = m }, label = { Text(m.label) })
                            }
                        }
                        Text("Tocca per aprire, tieni premuto per selezionare e togliere, spostare o dividere.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(media, key = { it.id }) { f ->
                val face = faceOf[f.id]
                val on = f.id in selected
                Box(Modifier.animateItem().combinedClickable(onClickLabel = if (selected.isEmpty()) "Apri" else "Seleziona", onLongClickLabel = "Seleziona",
                    onClick = { if (selected.isEmpty()) open(f.id, face?.timeMs) else selected = if (on) selected - f.id else selected + f.id },
                    onLongClick = { selected = selected + f.id })) {
                    MediaThumb(f, { vm.thumbOf(it) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false, selected = on,
                        selectionMode = selected.isNotEmpty())
                    if (face != null) FaceAvatar(face.id, { vm.faceCrop(it) }, 36.dp, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
            }
        }
        if (renaming) RenameDialog(p, o, vm) { renaming = false }
        if (pickingCover) CoverPicker(p, vm) { pickingCover = false }
        comparing?.let { (a, b) -> CompareDialog(a, b, o, vm) { comparing = null } }
        if (merging) PeoplePicker("Unisci a ${p.title}", o, exclude = p.id, multi = true, vm = vm, onDismiss = { merging = false }) { picked, _ ->
            vm.mergePeople(p.id, picked); merging = false
        }
        if (moving) PeoplePicker("Sposta ${selected.size} media a…", o, exclude = p.id, multi = false, vm = vm, allowNew = true,
            onDismiss = { moving = false }) { picked, newName ->
            vm.moveToPerson(p.id, selected, picked.firstOrNull(), newName); moving = false; selected = emptySet()
        }
        if (suggesting) SuggestionsDialog(p, o.files, vm, onClose = { suggesting = false }, onOpen = { card ->
            vm.openPersonMedia(listOf(card.fileId), card.fileId, card.timeMs); onOpenFile(card.fileId)
        })
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text("Volti") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { com.cripta.app.ui.components.ButtonText("OK") } })
    }
}

/** Face (tap: choose another), name (tap: rename) and how many media. */
@Composable
private fun ProfileHeader(p: FaceRepository.Person, vm: SettingsViewModel, onCover: () -> Unit, onRename: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FaceAvatar(p.cover, { vm.faceCrop(it) }, 112.dp,
            Modifier.clip(CircleShape).clickable(onClickLabel = "Scegli il volto da mostrare", onClick = onCover))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (p.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClickLabel = "Rinomina", onClick = onRename))
            val videos = p.media.count { VaultRepository.isVideo(it.mimeType) }
            Text(listOfNotNull(
                "${p.media.size} media",
                if (videos > 0) "$videos video" else null,
                if (p.entity.ignored) "nascosta" else null,
            ).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onCover, contentPadding = PaddingValues(0.dp)) { com.cripta.app.ui.components.ButtonText("Cambia volto") }
        }
    }
}

/** "N volti potrebbero essere suoi": counted when the profile opens, opens the review. */
@Composable
private fun SuggestionsEntry(p: FaceRepository.Person, vm: SettingsViewModel, onOpen: () -> Unit) {
    val mode = vm.settings.collectAsStateWithLifecycle().value.facesMode
    val found by produceState<Int?>(null, p.id, p.faces.size, mode) { value = runCatching { vm.suggestionsFor(p.id).size }.getOrDefault(0) }
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Suggerimenti", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        val n = found
        val known = vm.settings.collectAsStateWithLifecycle().value.facesMode == "known"
        Text(when {
            p.faces.isEmpty() -> "Assegnagli almeno un volto: poi Cripta cerca volti simili negli altri media."
            known && p.faces.map { it.fileId }.distinct().size < 2 -> "Dagli un volto in almeno 2 media diversi: con «Solo persone note» i suggerimenti partono da lì."
            n == null -> "Cerco volti simili…"
            n == 0 -> "Nessun volto simile da controllare. Più media gli assegni, più i suggerimenti diventano precisi."
            n == 1 -> "1 volto potrebbe essere suo. Nulla viene aggiunto senza la tua conferma."
            else -> "$n volti potrebbero essere suoi. Nulla viene aggiunto senza la tua conferma."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if ((n ?: 0) > 0) FilledTonalButton(onClick = onOpen) { com.cripta.app.ui.components.ButtonText("Controlla i suggerimenti") }
    }
}

/** The user's notes and links, saved as they type. */
@Composable
private fun NotesAndLinks(p: FaceRepository.Person, vm: SettingsViewModel) {
    val context = LocalContext.current
    var notes by remember(p.id) { mutableStateOf(p.entity.notes) }
    val links = remember(p.id) { mutableStateListOf<String>().apply { addAll(p.entity.links.lines().filter { it.isNotBlank() }) } }
    var newLink by remember(p.id) { mutableStateOf("") }
    fun save() = vm.setPersonProfile(p.id, notes, links.toList())
    LaunchedEffect(notes) {
        kotlinx.coroutines.delay(700)
        if (notes != p.entity.notes) save()
    }
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Note", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        com.cripta.app.ui.components.CriptaTextField(notes, { notes = it }, placeholder = { Text("Scrivi qualcosa su questa persona") },
            minLines = 2, modifier = Modifier.fillMaxWidth())
        Text("Link", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() })
        links.toList().forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(l, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).clickable(onClickLabel = "Apri il link") { openLink(context, l) }
                        .padding(vertical = 10.dp))
                IconButton(onClick = { links.removeAt(i); save() }) { Icon(Icons.Filled.Close, "Togli il link") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.cripta.app.ui.components.CriptaTextField(newLink, { newLink = it }, singleLine = true,
                placeholder = { Text("Aggiungi un link") }, modifier = Modifier.weight(1f))
            TextButton(enabled = newLink.isNotBlank(), onClick = { links.add(newLink.trim()); newLink = ""; save() }) { com.cripta.app.ui.components.ButtonText("Aggiungi") }
        }
    }
}

/** A link opens in the user's browser (Cripta itself has no network access). */
private fun openLink(context: android.content.Context, link: String) {
    val url = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(link)) link else "https://$link"
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { android.widget.Toast.makeText(context, "Nessuna app per aprire il link", android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable
private fun RenameDialog(p: FaceRepository.Person, o: FaceRepository.Overview, vm: SettingsViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf(p.entity.name) }
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text("Nome della persona") },
        text = {
            Column {
                com.cripta.app.ui.components.CriptaTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val twin = o.people.firstOrNull { it.id != p.id && name.isNotBlank() && it.entity.name.equals(name.trim(), ignoreCase = true) }
                Text(if (twin != null) "Esiste già «${twin.title}»: dopo il salvataggio puoi unirle da «Unisci con…»."
                    else "Il nome vale per tutti i suoi media.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (twin != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { vm.renamePerson(p.id, name); onClose() }) { com.cripta.app.ui.components.ButtonText("Salva") } },
        dismissButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText("Annulla") } })
}

/** Every face found of the person: tap one to show it on their profile, Home and the lists. */
@Composable
private fun CoverPicker(p: FaceRepository.Person, vm: SettingsViewModel, onClose: () -> Unit) {
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text("Volto da mostrare") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (p.faces.isEmpty()) "Nessun volto trovato per questa persona." else "${p.faces.size} volti trovati nei suoi media.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(p.faces.sortedByDescending { it.width }, key = { it.id }) { f ->
                        val on = f.id == p.cover?.id
                        FaceAvatar(f, { vm.faceCrop(it) }, 88.dp,
                            Modifier.clip(CircleShape)
                                .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .clickable(onClickLabel = "Usa questo volto") { vm.setPersonCover(p.id, f.id); onClose() })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText("Chiudi") } })
}

private fun likeness(s: Float) = when {
    s >= 0.5f -> "Molto simile"
    s >= 0.42f -> "Simile"
    else -> "Forse"
}

private fun clock(ms: Long) = (ms / 1000).let { s -> if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

/**
 * "Forse è …": faces elsewhere that look like the person, most alike first. Each shows the face,
 * the whole frame it was found in (the face marked) and opens the video at that moment, so the
 * user compares quickly; "È lui/lei" and "No" teach the next round. Nothing is added by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionsDialog(p: FaceRepository.Person, files: Map<String, FileEntity>, vm: SettingsViewModel,
                              onClose: () -> Unit, onOpen: (TrackCard) -> Unit) {
    // Back from playing a suggestion: the same list at once, so the scroll position comes back too
    // (loading it again showed an empty list first and the page opened at the top).
    val cached = remember(p.id) { vm.lastSuggestions?.takeIf { it.first == p.id }?.second }
    val list = remember(p.id) { mutableStateListOf<FaceRepository.Suggestion>().apply { cached?.let { addAll(it) } } }
    var loading by remember(p.id) { mutableStateOf(cached == null) }
    LaunchedEffect(p.id) {
        if (cached == null) {
            list.addAll(runCatching { vm.suggestionsFor(p.id) }.getOrDefault(emptyList())); loading = false
        }
        vm.lastSuggestions = p.id to list.toList()
    }
    fun answered(s: FaceRepository.Suggestion) { list.remove(s); vm.lastSuggestions = p.id to list.toList() }
    val close = { vm.lastSuggestions = null; onClose() }
    CriptaDialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { Text("Forse è ${p.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
        }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)) {
                item {
                    Text(when {
                        loading -> "Confronto con i ${p.faces.size} volti di ${p.title}…"
                        list.isEmpty() -> "Nient'altro da controllare. Più media gli assegni, più i suggerimenti diventano precisi."
                        else -> "${list.size} da controllare, i più simili prima. Confrontati con i ${p.faces.size} volti che gli hai dato."
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list.toList(), key = { it.card.id }) { s ->
                    SuggestionItem(s, p, files[s.card.fileId], vm,
                        onYes = { answered(s); vm.acceptSuggestion(s.card.id, p.id) },
                        onNo = { answered(s); vm.refuseSuggestion(s.card.id, p.id) },
                        onOpen = { onOpen(s.card) },
                        modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
internal fun SuggestionItem(s: FaceRepository.Suggestion, p: FaceRepository.Person, file: FileEntity?, vm: SettingsViewModel,
                           onYes: () -> Unit, onNo: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val frame by produceState<Bitmap?>(null, s.card.id) { value = runCatching { vm.frameOf(s.card) }.getOrNull() }
    val isVideo = file != null && VaultRepository.isVideo(file.mimeType)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The face found, and next to it the person's own face to compare with.
            FaceAvatar(s.card.id, { vm.faceCrop(it) }, 96.dp)
            FaceAvatar(p.cover, { vm.faceCrop(it) }, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(likeness(s.similarity), style = MaterialTheme.typography.titleMedium)
                Text("somiglianza %.2f".format(s.similarity), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FrameWithFace(frame, s.card.box, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "Apri", onClick = onOpen))
        Text((file?.originalName ?: "Media") + if (isVideo) " · ${clock(s.card.timeMs)}" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onYes) { com.cripta.app.ui.components.ButtonText("È ${p.title}") }
            TextButton(onClick = onNo) { com.cripta.app.ui.components.ButtonText("Non è lui/lei") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpen) { Icon(Icons.Filled.PlayArrow, if (isVideo) "Riproduci da qui" else "Apri") }
        }
    }
}

/** The frame a face was found in, the face marked by a ring (the box is stored as fractions of it). */
@Composable
private fun FrameWithFace(frame: Bitmap?, box: String, modifier: Modifier) {
    val r = box.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }
    val accent = MaterialTheme.colorScheme.primary
    if (frame == null) {
        Box(modifier.height(180.dp), contentAlignment = Alignment.Center) {
            Text("Caricamento del fotogramma…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Image(frame.asImageBitmap(), "Fotogramma", contentScale = ContentScale.Fit,
        modifier = modifier.aspectRatio(frame.width.toFloat() / frame.height.coerceAtLeast(1)).drawWithContent {
            drawContent()
            if (r != null) {
                val pad = 0.15f
                val x = (r[0] - r[2] * pad) * size.width; val y = (r[1] - r[3] * pad) * size.height
                val w = r[2] * (1 + 2 * pad) * size.width; val h = r[3] * (1 + 2 * pad) * size.height
                drawRoundRect(accent, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
        })
}

package com.cripta.app.ui.viewer

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import kotlinx.coroutines.delay
import com.cripta.app.ui.components.CriptaAlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.face.FaceRepository
import com.cripta.app.ui.settings.FaceAvatar
import com.cripta.app.ui.settings.FavoriteStar

/**
 * Details panel › "Persone": every face that shows in this media, who it is and (videos) when it
 * shows, a tap on a moment jumping there. A tap on a face opens one editor for everything: its
 * person's name, another person, not them, the star. A long press selects faces: two or more of
 * one person become one ("Stessa persona"), faces found by mistake go ("Elimina").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PeopleSection(file: FileEntity, vm: ViewerViewModel) {
    val faces by remember(file.id) { vm.facesOf(file.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val links by remember(file.id) { vm.personLinksOf(file.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val people by vm.peopleList.collectAsStateWithLifecycle()
    val message by vm.faceMessage.collectAsStateWithLifecycle()
    val byId = remember(people) { people.associateBy { it.id } }
    val isVideo = VaultRepository.isVideo(file.mimeType)
    /** The face being edited; with [adding] the whole media is added to a person instead. */
    var editing by remember(file.id) { mutableStateOf<TrackCard?>(null) }
    var adding by remember(file.id) { mutableStateOf(false) }
    /** Faces picked with a long press, for "Stessa persona" and "Elimina". */
    var selected by remember(file.id) { mutableStateOf(emptySet<Long>()) }
    var showUnclear by remember(file.id) { mutableStateOf(false) }
    val undo by vm.faceUndo.collectAsStateWithLifecycle()
    LaunchedEffect(file.id) { vm.tidyFaces(file.id) }
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        delay(8_000)
        if (vm.faceUndo.value === u) vm.faceUndo.value = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Persone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        val shown = faces.sortedWith(compareBy<TrackCard>({ it.personId == null }, { it.moments.firstOrNull() ?: it.timeMs }))
        // Faces gone meanwhile (joined, deleted, analysed again) leave the selection.
        val chosen = selected.filterTo(HashSet()) { id -> shown.any { it.id == id } }
        fun toggle(id: Long) { selected = if (id in chosen) chosen - id else chosen + id }
        // Faces nearly always found by mistake wait folded at the end.
        val (unclear, clear) = shown.partition { FaceRepository.uncertain(it, isVideo) }
        if (shown.isEmpty() && links.isEmpty()) {
            Text("Nessun volto riconosciuto.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chosen.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Esci dalla selezione") }
                Text(if (chosen.size == 1) "1 volto" else "${chosen.size} volti", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = chosen.size >= 2, onClick = { vm.mergeFaces(file.id, chosen); selected = emptySet() }) { com.cripta.app.ui.components.ButtonText("Stessa persona") }
                TextButton(onClick = { vm.deleteFaces(file.id, chosen); selected = emptySet() }) {
                    com.cripta.app.ui.components.ButtonText("Elimina", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { vm.deleteFaces(file.id, chosen, everywhere = true); selected = emptySet() }) {
                    com.cripta.app.ui.components.ButtonText("Non è un volto", color = MaterialTheme.colorScheme.error)
                }
            }
        } else if (clear.size >= 2) {
            Text("Tieni premuto un volto per unirlo a un altro della stessa persona o per eliminarlo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        @Composable
        fun row(t: TrackCard) = FaceRow(t, t.personId?.let { byId[it] }, file, isVideo, vm, on = t.id in chosen, selecting = chosen.isNotEmpty(),
            onTap = { if (chosen.isEmpty()) editing = t else toggle(t.id) }, onLongPress = { toggle(t.id) })
        for (t in clear) row(t)
        if (unclear.isNotEmpty()) {
            TextButton(onClick = { showUnclear = !showUnclear }) {
                com.cripta.app.ui.components.ButtonText(if (showUnclear) "Nascondi i volti poco chiari" else if (unclear.size == 1) "Un altro volto poco chiaro" else "Altri ${unclear.size} volti poco chiari")
            }
            AnimatedVisibility(showUnclear, enter = fadeIn(tween(220)) + expandVertically(tween(220)), exit = fadeOut(tween(120)) + shrinkVertically(tween(120))) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Visti una sola volta o poco nitidi: spesso non sono volti.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (t in unclear) row(t)
                    TextButton(onClick = { vm.deleteFaces(file.id, unclear.map { it.id }) }) {
                        com.cripta.app.ui.components.ButtonText("Elimina i volti poco chiari", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        links.forEach { pid ->
            Text("Aggiunto a mano a ${byId[pid]?.title ?: FaceRepository.UNNAMED}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.findFaces(file) }) { com.cripta.app.ui.components.ButtonText("Aggiungi un volto") }
            TextButton(onClick = { vm.describeFaces(file.id) }) { com.cripta.app.ui.components.ButtonText(if (shown.isEmpty()) "Perché?" else "Dettagli") }
            TextButton(onClick = { vm.analyseFaces(file.id) }) { com.cripta.app.ui.components.ButtonText("Rianalizza") }
        }
        if (shown.any { it.personId == null && it.state != "manual" }) {
            TextButton(onClick = { vm.clearDetectedFaces(file.id) }) {
                com.cripta.app.ui.components.ButtonText("Elimina i volti non assegnati", color = MaterialTheme.colorScheme.error)
            }
        }
        // "Annulla" for the last join or deletion here; the message stays while it fades out.
        val mine = undo?.takeIf { it.fileId == file.id }
        var last by remember(file.id) { mutableStateOf("") }
        if (mine != null) last = mine.message
        AnimatedVisibility(mine != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(last, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.undoFaceChange() }) { com.cripta.app.ui.components.ButtonText("Annulla") }
            }
        }
    }

    if (editing != null || adding) {
        val t = editing
        FaceEditor(t, t?.personId?.let { byId[it] }, people, vm,
            onPick = { id -> if (t != null) vm.putInPerson(file.id, t.id, id) else vm.putInPerson(file.id, null, id) },
            onNew = { name -> vm.putInNewPerson(file.id, t?.id, name) },
            onDismiss = { editing = null; adding = false })
    }
    val found by vm.foundFaces.collectAsStateWithLifecycle()
    val finding by vm.findingFaces.collectAsStateWithLifecycle()
    var picked by remember(file.id) { mutableStateOf<FaceRepository.FoundFace?>(null) }
    val start by vm.findStart.collectAsStateWithLifecycle()
    found?.let { list ->
        val at = list.firstOrNull()?.timeMs ?: start
        val later = isVideo && list.isNotEmpty() && at > start
        if (picked == null) CriptaAlertDialog(onDismissRequest = { vm.foundFaces.value = null },
            title = { Text(if (!isVideo) "Volti nella foto" else if (later) "Volti a ${clock(at)}" else "Volti in questo momento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        finding -> Text(if (isVideo) "Ricerca dei volti, anche nei secondi dopo…" else "Ricerca dei volti…")
                        list.isEmpty() -> Text("Nessun volto trovato" + (if (isVideo) " qui né nei 15 secondi dopo: metti in pausa dove il volto si vede bene e riprova." else ".") +
                            " Puoi anche aggiungere il media a una persona senza volto.")
                        else -> {
                            if (later) Text("Qui nessun volto: questi sono poco più avanti.", style = MaterialTheme.typography.bodyMedium)
                            Text("Tocca il volto da aggiungere. Insegna alla persona anche questo aspetto.", style = MaterialTheme.typography.bodySmall)
                            // Large, so faces are told apart at a glance: two per row in a portrait dialog.
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                list.forEach { f ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { picked = f }) {
                                        Image(f.picture.asImageBitmap(), null, Modifier.size(128.dp).clip(MaterialTheme.shapes.medium))
                                        Text(f.why ?: "buono", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    // Not the face wanted: look on from just after these (or after the stretch searched).
                    if (isVideo && !finding) TextButton(onClick = { vm.findFaces(file, from = if (list.isEmpty()) start + 15_500 else at + 1_000) }) {
                        com.cripta.app.ui.components.ButtonText("Cerca più avanti")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.foundFaces.value = null }) { com.cripta.app.ui.components.ButtonText("Chiudi") } },
            dismissButton = { TextButton(onClick = { vm.foundFaces.value = null; adding = true }) { com.cripta.app.ui.components.ButtonText("Senza volto") } })
        else picked?.let { chosen ->
            FaceEditor(null, null, people, vm,
                onPick = { id -> vm.addFoundFace(file, chosen, id, "") },
                onNew = { name -> vm.addFoundFace(file, chosen, null, name) },
                onDismiss = { picked = null; vm.foundFaces.value = null })
        }
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text("Volti") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { com.cripta.app.ui.components.ButtonText("OK") } })
    }
    val also by vm.alsoFaces.collectAsStateWithLifecycle()
    also?.takeIf { it.fileId == file.id }?.let { a -> AlsoDialog(a, byId[a.personId]?.title ?: FaceRepository.UNNAMED, vm) }
}

/** One face of the media: its picture (ring and tick when selected), who it is, its moments, the star. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FaceRow(t: TrackCard, p: FaceRepository.Person?, file: FileEntity, isVideo: Boolean, vm: ViewerViewModel,
                    on: Boolean, selecting: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val tap = Modifier.combinedClickable(onClickLabel = if (!selecting) "Modifica" else if (on) "Deseleziona" else "Seleziona",
        onLongClickLabel = "Seleziona", onLongClick = onLongPress, onClick = onTap)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(tap) {
            FaceAvatar(t.id, { vm.faceCrop(it) }, 64.dp,
                if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier, key = t.timeMs)
            if (on) Icon(Icons.Filled.Check, "Selezionato", tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary).padding(3.dp))
        }
        Column(Modifier.weight(1f).then(tap)) {
            Text(p?.title ?: "Sconosciuto · tocca per dire chi è", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (p == null || p.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (isVideo) {
                val moments = t.moments.ifEmpty { listOf(t.timeMs) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    moments.forEach { ms ->
                        com.cripta.app.ui.components.ActionChip(onClick = { vm.seekPlayer(file.id, maxOf(0L, ms - 1_000), play = true) }, label = { Text(clock(ms)) })
                    }
                }
            }
        }
        if (p != null && t.state != "suggested") FavoriteStar(p.entity.favorite, p.title) { vm.setPersonFavorite(p.id, !p.entity.favorite) }
    }
}

/** "Anche questi sono …?": the other faces of the media like the person just named; tap to leave one out. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AlsoDialog(a: ViewerViewModel.AlsoFaces, name: String, vm: ViewerViewModel) {
    var off by remember(a) { mutableStateOf(emptySet<Long>()) }
    val picked = a.faces.map { it.card.id }.filter { it !in off }
    CriptaAlertDialog(onDismissRequest = { vm.alsoFaces.value = null },
        title = { Text("Anche questi sono $name?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Volti simili in questo media. Tocca quelli che non sono $name.", style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    a.faces.forEach { s ->
                        val on = s.card.id !in off
                        Box(Modifier.clip(CircleShape).clickable(onClickLabel = if (on) "Escludi" else "Includi") {
                            off = if (on) off + s.card.id else off - s.card.id
                        }) {
                            FaceAvatar(s.card.id, { vm.faceCrop(it) }, 88.dp,
                                if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            if (on) Icon(Icons.Filled.Check, "Incluso", tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary).padding(3.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { vm.joinAlso(a, picked) }) { com.cripta.app.ui.components.ButtonText("Sì, unisci") } },
        dismissButton = { TextButton(onClick = { vm.alsoFaces.value = null }) { com.cripta.app.ui.components.ButtonText("No") } })
}

/** "Dividi": the moments of a face that are someone else become a face of their own. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SplitDialog(t: TrackCard, moments: List<Long>, vm: ViewerViewModel, onDismiss: () -> Unit) {
    var picked by remember(t.id) { mutableStateOf(emptySet<Long>()) }
    CriptaAlertDialog(onDismissRequest = onDismiss, title = { Text("Dividi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Scegli i momenti in cui c'è un'altra persona: diventano un volto a parte, da assegnare.",
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moments.forEach { ms ->
                        com.cripta.app.ui.components.ChoiceChip(selected = ms in picked, onClick = { picked = if (ms in picked) picked - ms else picked + ms },
                            label = { Text(clock(ms)) })
                    }
                }
                TextButton(onClick = { vm.seekPlayer(t.fileId, maxOf(0L, (picked.minOrNull() ?: moments.first()) - 1_000), play = true) }) {
                    com.cripta.app.ui.components.ButtonText("Guarda dal primo momento scelto")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty() && picked.size < moments.size, onClick = { vm.splitFace(t.fileId, t.id, picked); onDismiss() }) { com.cripta.app.ui.components.ButtonText("Dividi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText("Annulla") } })
}

/**
 * "Chi è?": the name of the face's person (renaming it renames them everywhere), or another person,
 * or a new one; for a suggestion, yes or no; for a placed face, "not them" and the star.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FaceEditor(
    t: TrackCard?, current: FaceRepository.Person?, people: List<FaceRepository.Person>, vm: ViewerViewModel,
    onPick: (Long) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit,
) {
    val placed = t != null && current != null && t.state != "suggested"
    var name by remember(t?.id) { mutableStateOf(if (placed && current != null) current.entity.name else "") }
    var query by remember(t?.id) { mutableStateOf("") }
    /** Moments "Dividi" can move (faces analysed before they were kept one by one have none). */
    val moments by produceState(emptyList<Long>(), t?.id) { value = t?.let { vm.splittable(it.id) }.orEmpty() }
    var splitting by remember(t?.id) { mutableStateOf(false) }
    if (splitting && t != null) { SplitDialog(t, moments, vm) { splitting = false; onDismiss() }; return }
    CriptaAlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (t == null) "Aggiungi a una persona" else "Chi è?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FaceAvatar(t, { vm.faceCrop(it) }, 96.dp)
                    Column(Modifier.weight(1f)) {
                        Text(when {
                            current == null -> "Non ancora assegnato"
                            else -> current.title
                        }, style = MaterialTheme.typography.bodyMedium)
                        if (placed && current != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            FavoriteStar(current.entity.favorite, current.title) { vm.setPersonFavorite(current.id, !current.entity.favorite) }
                            TextButton(onClick = { vm.rejectFace(t.id, current.id); onDismiss() }) { com.cripta.app.ui.components.ButtonText("Non è lui/lei") }
                        }
                    }
                }
                if (t != null) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (moments.size >= 2) TextButton(onClick = { splitting = true }) { com.cripta.app.ui.components.ButtonText("Dividi…") }
                    TextButton(onClick = { vm.deleteFaces(t.fileId, listOf(t.id)); onDismiss() }) {
                        com.cripta.app.ui.components.ButtonText("Elimina", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { vm.deleteFaces(t.fileId, listOf(t.id), everywhere = true); onDismiss() }) {
                        com.cripta.app.ui.components.ButtonText("Non è un volto", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (placed) {
                    com.cripta.app.ui.components.CriptaTextField(name, { name = it }, singleLine = true, label = { Text("Nome") },
                        supportingText = { Text("Vale per tutti i suoi media.") }, modifier = Modifier.fillMaxWidth())
                    Text("Oppure è un'altra persona:", style = MaterialTheme.typography.labelLarge)
                }
                com.cripta.app.ui.components.CriptaTextField(query, { query = it }, singleLine = true,
                    label = { Text(if (placed) "Cerca o crea" else "Nome (cerca o crea)") }, modifier = Modifier.fillMaxWidth())
                // Everyone (the user makes them all), starred first, then the newest.
                val list = people.filter { it.id != current?.id && !it.entity.ignored && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
                    .sortedWith(compareBy({ !it.entity.favorite }, { -it.entity.createdAt }))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    if (query.isBlank() || people.none { it.entity.name.equals(query.trim(), ignoreCase = true) }) item {
                        Text(if (query.isBlank()) "+ Nuova persona senza nome (Volto…)" else "+ Nuova persona «${query.trim()}»", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable { onNew(query); onDismiss() }.padding(vertical = 12.dp))
                    }
                    items(list, key = { it.id }) { q ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(q.id); onDismiss() }.padding(vertical = 6.dp),
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
        confirmButton = {
            if (placed && current != null) TextButton(enabled = name.trim() != current.entity.name, onClick = { vm.renamePerson(current.id, name); onDismiss() }) { com.cripta.app.ui.components.ButtonText("Salva nome") }
            else TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText("Chiudi") }
        },
        dismissButton = { if (placed) TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText("Chiudi") } })
}

private fun clock(ms: Long) = (ms / 1000).let { s -> if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

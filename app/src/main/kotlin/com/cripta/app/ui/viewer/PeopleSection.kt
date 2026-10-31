package com.cripta.app.ui.viewer

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
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
 * person's name, another person, not them, the star.
 */
@Composable
internal fun PeopleSection(file: FileEntity, vm: ViewerViewModel) {
    val faces by remember(file.id) { vm.facesOf(file.id) }.collectAsState(initial = emptyList())
    val links by remember(file.id) { vm.personLinksOf(file.id) }.collectAsState(initial = emptyList())
    val people by vm.peopleList.collectAsState()
    val message by vm.faceMessage.collectAsState()
    val byId = remember(people) { people.associateBy { it.id } }
    val isVideo = VaultRepository.isVideo(file.mimeType)
    /** The face being edited; with [adding] the whole media is added to a person instead. */
    var editing by remember(file.id) { mutableStateOf<TrackCard?>(null) }
    var adding by remember(file.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Persone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        val shown = faces.sortedWith(compareBy<TrackCard>({ it.personId == null }, { it.moments.firstOrNull() ?: it.timeMs }))
        if (shown.isEmpty() && links.isEmpty()) {
            Text("Nessun volto riconosciuto.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (t in shown) {
            val p = t.personId?.let { byId[it] }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FaceAvatar(t.id, { vm.faceCrop(it) }, 52.dp, Modifier.clickable(onClickLabel = "Modifica") { editing = t })
                Column(Modifier.weight(1f).clickable(onClickLabel = "Modifica") { editing = t }) {
                    Text(when {
                        p == null -> "Sconosciuto · tocca per dire chi è"
                        else -> p.title
                    }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (p == null || p.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (isVideo) {
                        val moments = t.moments.ifEmpty { listOf(t.timeMs) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            moments.forEach { ms ->
                                AssistChip(onClick = { vm.seekPlayer(file.id, maxOf(0L, ms - 1_000), play = true) }, label = { Text(clock(ms)) })
                            }
                        }
                    }
                }
                if (p != null && t.state != "suggested") FavoriteStar(p.entity.favorite, p.title) { vm.setPersonFavorite(p.id, !p.entity.favorite) }
            }
        }
        links.forEach { pid ->
            Text("Aggiunto a mano a ${byId[pid]?.title ?: FaceRepository.UNNAMED}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.findFaces(file) }) { Text("Aggiungi un volto") }
            TextButton(onClick = { vm.describeFaces(file.id) }) { Text(if (shown.isEmpty()) "Perché?" else "Dettagli") }
            TextButton(onClick = { vm.analyseFaces(file.id) }) { Text("Rianalizza") }
        }
    }

    if (editing != null || adding) {
        val t = editing
        FaceEditor(t, t?.personId?.let { byId[it] }, people, vm,
            onPick = { id -> if (t != null) vm.putInPerson(file.id, t.id, id) else vm.putInPerson(file.id, null, id) },
            onNew = { name -> vm.putInNewPerson(file.id, t?.id, name) },
            onDismiss = { editing = null; adding = false })
    }
    val found by vm.foundFaces.collectAsState()
    val finding by vm.findingFaces.collectAsState()
    var picked by remember(file.id) { mutableStateOf<FaceRepository.FoundFace?>(null) }
    found?.let { list ->
        if (picked == null) CriptaAlertDialog(onDismissRequest = { vm.foundFaces.value = null },
            title = { Text(if (isVideo) "Volti in questo momento" else "Volti nella foto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        finding -> Text("Ricerca dei volti…")
                        list.isEmpty() -> Text("Nessun volto trovato" + (if (isVideo) " qui: metti in pausa dove il volto si vede bene e riprova." else ".") +
                            " Puoi anche aggiungere il media a una persona senza volto.")
                        else -> {
                            Text("Tocca il volto da aggiungere. Insegna alla persona anche questo aspetto.", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                list.forEach { f ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { picked = f }) {
                                        Image(f.picture.asImageBitmap(), null, Modifier.size(88.dp).clip(MaterialTheme.shapes.medium))
                                        Text(f.why ?: "buono", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.foundFaces.value = null }) { Text("Chiudi") } },
            dismissButton = { TextButton(onClick = { vm.foundFaces.value = null; adding = true }) { Text("Senza volto") } })
        else FaceEditor(null, null, people, vm,
            onPick = { id -> vm.addFoundFace(file, picked!!, id, "") },
            onNew = { name -> vm.addFoundFace(file, picked!!, null, name) },
            onDismiss = { picked = null; vm.foundFaces.value = null })
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text("Volti") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { Text("OK") } })
    }
}

/**
 * "Chi è?": the name of the face's person (renaming it renames them everywhere), or another person,
 * or a new one; for a suggestion, yes or no; for a placed face, "not them" and the star.
 */
@Composable
private fun FaceEditor(
    t: TrackCard?, current: FaceRepository.Person?, people: List<FaceRepository.Person>, vm: ViewerViewModel,
    onPick: (Long) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit,
) {
    val placed = t != null && current != null && t.state != "suggested"
    var name by remember(t?.id) { mutableStateOf(if (placed) current!!.entity.name else "") }
    var query by remember(t?.id) { mutableStateOf("") }
    CriptaAlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (t == null) "Aggiungi a una persona" else "Chi è?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FaceAvatar(t.id, { vm.faceCrop(it) }, 64.dp)
                    Column(Modifier.weight(1f)) {
                        Text(when {
                            current == null -> "Non ancora assegnato"
                            else -> current.title
                        }, style = MaterialTheme.typography.bodyMedium)
                        if (placed) Row(verticalAlignment = Alignment.CenterVertically) {
                            FavoriteStar(current!!.entity.favorite, current.title) { vm.setPersonFavorite(current.id, !current.entity.favorite) }
                            TextButton(onClick = { vm.rejectFace(t.id, current.id); onDismiss() }) { Text("Non è lui/lei") }
                        }
                    }
                }
                if (placed) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome") },
                        supportingText = { Text("Vale per tutti i suoi media.") }, modifier = Modifier.fillMaxWidth())
                    Text("Oppure è un'altra persona:", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedTextField(query, { query = it }, singleLine = true,
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
                            FaceAvatar(q.cover?.id, { vm.faceCrop(it) }, 36.dp)
                            Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                color = if (q.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            if (q.entity.favorite) Icon(Icons.Filled.Star, "Preferita", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (placed) TextButton(enabled = name.trim() != current!!.entity.name, onClick = { vm.renamePerson(current.id, name); onDismiss() }) { Text("Salva nome") }
            else TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
        dismissButton = { if (placed) TextButton(onClick = onDismiss) { Text("Chiudi") } })
}

private fun clock(ms: Long) = (ms / 1000).let { s -> if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

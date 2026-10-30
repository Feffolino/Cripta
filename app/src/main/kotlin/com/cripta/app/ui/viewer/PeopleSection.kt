package com.cripta.app.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import com.cripta.app.ui.settings.FaceAvatar
import com.cripta.app.ui.settings.FavoriteStar

/**
 * Details panel › "Persone": every face that shows in this media, who it is and (videos) when it
 * shows, a tap jumping there; the corrections (someone else, not them, add to a person), the star,
 * and the reason when no face is there.
 */
@Composable
internal fun PeopleSection(file: FileEntity, vm: ViewerViewModel) {
    val faces by remember(file.id) { vm.facesOf(file.id) }.collectAsState(initial = emptyList())
    val links by remember(file.id) { vm.personLinksOf(file.id) }.collectAsState(initial = emptyList())
    val people by vm.peopleNames.collectAsState()
    val favorites by vm.favoritePeople.collectAsState()
    val message by vm.faceMessage.collectAsState()
    val isVideo = VaultRepository.isVideo(file.mimeType)
    /** The face being assigned (null track = the whole media, without a face). */
    var picking by remember(file.id) { mutableStateOf<Pair<TrackCard?, Boolean>?>(null) }
    var menuFor by remember(file.id) { mutableStateOf<Long?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Persone", style = MaterialTheme.typography.titleSmall)
        val shown = faces.sortedWith(compareBy<TrackCard>({ it.personId == null }, { it.moments.firstOrNull() ?: it.timeMs }))
        if (shown.isEmpty() && links.isEmpty()) {
            Text("Nessun volto riconosciuto.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (t in shown) {
            val name = t.personId?.let { people[it] }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    FaceAvatar(t.id, { vm.faceCrop(it) }, 52.dp, Modifier.clickable(onClickLabel = "Opzioni del volto") { menuFor = t.id })
                    DropdownMenu(menuFor == t.id, { menuFor = null }) {
                        if (t.state == "suggested" && t.personId != null) {
                            DropdownMenuItem(text = { Text("Sì, è $name") }, onClick = { menuFor = null; vm.confirmFace(t.id, t.personId) })
                        }
                        DropdownMenuItem(text = { Text("È un'altra persona…") }, onClick = { menuFor = null; picking = t to true })
                        if (t.personId != null) {
                            DropdownMenuItem(text = { Text("Non è $name") }, onClick = { menuFor = null; vm.rejectFace(t.id, t.personId) })
                        }
                        DropdownMenuItem(text = { Text("Confronta con le persone") }, onClick = { menuFor = null; vm.explainFace(t.id) })
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(when {
                        name == null -> "Sconosciuto"
                        t.state == "suggested" -> "Forse $name?"
                        else -> name
                    }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isVideo) {
                        val moments = t.moments.ifEmpty { listOf(t.timeMs) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            moments.forEach { ms ->
                                AssistChip(onClick = { vm.seekPlayer(file.id, maxOf(0L, ms - 1_000), play = true) }, label = { Text(clock(ms)) })
                            }
                        }
                    }
                }
                if (t.personId != null && t.state != "suggested" && name != null) {
                    FavoriteStar(t.personId in favorites, name) { vm.setPersonFavorite(t.personId, t.personId !in favorites) }
                }
            }
        }
        links.forEach { pid ->
            Text("Aggiunto a mano a ${people[pid] ?: "Persona $pid"}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { picking = null to false }) { Text("Aggiungi a una persona") }
            TextButton(onClick = { vm.describeFaces(file.id) }) { Text(if (shown.isEmpty()) "Perché?" else "Dettagli") }
            TextButton(onClick = { vm.analyseFaces(file.id) }) { Text("Rianalizza") }
        }
    }

    picking?.let { (track, _) ->
        var newName by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { picking = null },
            title = { Text(if (track == null) "Aggiungi a una persona" else "Chi è?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Nuova persona") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(people.entries.toList(), key = { it.key }) { (id, name) ->
                            Text((if (id in favorites) "★ " else "") + name,
                                Modifier.fillMaxWidth().clickable { vm.putInPerson(file.id, track?.id, id); picking = null }.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = { vm.putInNewPerson(file.id, track?.id, newName); picking = null }) { Text("Crea") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Annulla") } })
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text("Volti") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { Text("OK") } })
    }
}

private fun clock(ms: Long) = (ms / 1000).let { s -> if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

package com.cripta.app.ui.viewer

import com.cripta.app.ui.theme.favorite
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import com.cripta.app.ui.theme.popOn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
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
import com.cripta.app.ui.components.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
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

/**
 * Details panel › "Persone": every face that shows in this media, who it is and (videos) when it
 * shows, a tap on a moment jumping there. A tap on a face asks who it is; a suggestion is answered
 * on its row (✓ / ✗); the rarer actions of a face (not them, split, delete) are in its ⋮ menu, those
 * of the media (why, analyse again) in the menu by the heading. A long press selects faces: two or
 * more of one person become one ("Stessa persona"), faces found by mistake go ("Elimina").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PeopleSection(file: FileEntity, vm: ViewerViewModel) {
    val faces by remember(file.id) { vm.facesOf(file.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val links by remember(file.id) { vm.personLinksOf(file.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val people by vm.peopleList.collectAsStateWithLifecycle()
    val message by vm.faceMessage.collectAsStateWithLifecycle()
    val refined by remember(file.id) { vm.refineNote(file.id) }.collectAsStateWithLifecycle(initialValue = null)
    val byId = remember(people) { people.associateBy { it.id } }
    val isVideo = VaultRepository.isVideo(file.mimeType)
    /** The face being edited; with [adding] the whole media is added to a person instead. */
    var editing by remember(file.id) { mutableStateOf<TrackCard?>(null) }
    var adding by remember(file.id) { mutableStateOf(false) }
    /** Faces picked with a long press, for "Stessa persona" and "Elimina". */
    var selected by remember(file.id) { mutableStateOf(emptySet<Long>()) }
    var showUnclear by remember(file.id) { mutableStateOf(false) }
    /** The face "Dividi" was asked for (from its ⋮ menu). */
    var splitting by remember(file.id) { mutableStateOf<TrackCard?>(null) }
    var menu by remember { mutableStateOf(false) }
    val undo by vm.faceUndo.collectAsStateWithLifecycle()
    LaunchedEffect(file.id) { vm.tidyFaces(file.id) }
    // As long as on the people pages ([rememberUndoMillis]), not counting while the vault is locked:
    // the chance to undo is not used up behind the lock screen.
    val undoMs = com.cripta.app.ui.components.rememberUndoMillis()
    val locked = com.cripta.app.ui.components.rememberVaultLocked()
    LaunchedEffect(undo, undoMs) {
        val u = undo ?: return@LaunchedEffect
        com.cripta.app.ui.components.waitUnlocked(undoMs, locked)
        if (vm.faceUndo.value === u) vm.faceUndo.value = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val shown = remember(faces) {
            faces.sortedWith(compareBy<TrackCard>({ it.personId == null }, { it.moments.firstOrNull() ?: it.timeMs }))
        }
        // Faces gone meanwhile (joined, deleted, analysed again) leave the selection.
        val chosen = selected.filterTo(HashSet()) { id -> shown.any { it.id == id } }
        fun toggle(id: Long) { selected = if (id in chosen) chosen - id else chosen + id }
        // Faces nearly always found by mistake wait folded at the end.
        val (unclear, clear) = remember(shown, isVideo) { shown.partition { FaceRepository.uncertain(it, isVideo) } }
        val unassigned = shown.any { it.personId == null && it.state != "manual" }
        // Someone confirmed in a video: it can be looked at closer around them.
        val confirmed = isVideo && shown.any { it.personId != null && it.state == "manual" }
        PanelHeading(str(R.string.people), if (chosen.isEmpty() && clear.size >= 2) str(R.string.faces_tap_hint) else null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(CriptaIcons.MoreVert, str(R.string.faces_more_actions)) }
                DropdownMenu(menu && com.cripta.app.ui.components.vaultUnlocked(), { menu = false }) {
                    DropdownMenuItem(text = { Text(if (shown.isEmpty()) str(R.string.faces_why_none) else str(R.string.faces_how_found)) },
                        onClick = { menu = false; vm.describeFaces(file.id) })
                    DropdownMenuItem(text = { Text(str(R.string.analyse_again)) }, onClick = { menu = false; vm.analyseFaces(file.id) })
                    DropdownMenuItem(text = { Text(str(R.string.search_deeper)) }, onClick = { menu = false; vm.analyseFaces(file.id, deep = true) })
                    if (confirmed) DropdownMenuItem(text = { Text(str(R.string.analyse_better)) }, onClick = { menu = false; vm.refineFaces(file.id) })
                    if (unassigned) DropdownMenuItem(
                        text = { Text(str(R.string.delete_unassigned_faces), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; vm.clearDetectedFaces(file.id) })
                }
            }
        }
        // What the closer look did here, so that faces joined or folded away are not a mystery.
        refined?.takeIf { confirmed }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (shown.isEmpty() && links.isEmpty()) {
            Text(str(R.string.no_face_recognised), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chosen.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { selected = emptySet() }) { Icon(CriptaIcons.Close, str(R.string.exit_selection)) }
                Text(plural(R.plurals.faces_n, chosen.size), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), enabled = chosen.size >= 2, onClick = { vm.mergeFaces(file.id, chosen); selected = emptySet() }) { com.cripta.app.ui.components.ButtonText(str(R.string.same_person)) }
                com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { vm.deleteFaces(file.id, chosen); selected = emptySet() })
                com.cripta.app.ui.components.DestructiveButton(str(R.string.not_a_face),
                    onClick = { vm.deleteFaces(file.id, chosen, everywhere = true); selected = emptySet() })
            }
        }
        @Composable
        fun row(t: TrackCard) = FaceRow(t, t.personId?.let { byId[it] }, file, isVideo, vm, on = t.id in chosen, selecting = chosen.isNotEmpty(),
            onTap = { if (chosen.isEmpty()) editing = t else toggle(t.id) }, onLongPress = { toggle(t.id) },
            onSplit = { splitting = t })
        for (t in clear) row(t)
        // Only unclear faces here: shown as they are, folding them would leave the section empty.
        if (clear.isEmpty()) for (t in unclear) row(t)
        else if (unclear.isNotEmpty()) {
            TextButton(onClick = { showUnclear = !showUnclear }) {
                com.cripta.app.ui.components.ButtonText(if (showUnclear) str(R.string.hide_unclear_faces) else plural(R.plurals.more_unclear_faces, unclear.size))
            }
            AnimatedVisibility(showUnclear, enter = fadeIn(tween(220)) + expandVertically(tween(220)), exit = fadeOut(tween(120)) + shrinkVertically(tween(120))) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (unclear.any { it.state == FaceRepository.ASIDE }) str(R.string.unclear_hint_aside)
                        else str(R.string.unclear_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (t in unclear) row(t)
                    com.cripta.app.ui.components.DestructiveButton(str(R.string.delete_unclear_faces),
                        onClick = { vm.deleteFaces(file.id, unclear.map { it.id }) })
                }
            }
        }
        // People given this media by hand (no face): their face, and "Togli" to take it back.
        links.forEach { pid ->
            val p = byId[pid]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FaceAvatar(p?.cover, { vm.faceCrop(it) }, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(p?.title ?: FaceRepository.UNNAMED, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(str(R.string.added_by_hand), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                TextButton(onClick = { vm.unlinkPerson(file.id, pid) }) { com.cripta.app.ui.components.ButtonText(str(R.string.remove_short)) }
            }
        }
        FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { vm.findFaces(file) }) { com.cripta.app.ui.components.ButtonText(str(R.string.add_a_face)) }
        // "Annulla" for the last join or deletion here; the message stays while it fades out.
        val mine = undo?.takeIf { it.fileId == file.id }
        var last by remember(file.id) { mutableStateOf("") }
        if (mine != null) last = mine.message
        AnimatedVisibility(mine != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(last, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.undoFaceChange() }) { com.cripta.app.ui.components.ButtonText(str(R.string.undo)) }
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
    splitting?.let { t ->
        val moments by produceState<List<Long>?>(null, t.id) { value = vm.splittable(t.id) }
        val m = moments
        if (m != null && m.size >= 2) SplitDialog(t, m, vm) { splitting = null }
        else if (m != null) {
            // Faces analysed before they were kept one by one cannot be split.
            CriptaAlertDialog(onDismissRequest = { splitting = null }, title = { Text(str(R.string.split)) },
                text = { Text(str(R.string.split_need_two)) },
                confirmButton = { TextButton(onClick = { splitting = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } })
        }
    }
    val foundAll by vm.foundFaces.collectAsStateWithLifecycle()
    val foundFor by vm.foundFacesFile.collectAsStateWithLifecycle()
    // Faces found for another media (the viewer moved on during the search) are not offered here.
    val found = foundAll?.takeIf { foundFor == file.id }
    androidx.compose.runtime.LaunchedEffect(file.id) { vm.closeFoundFacesUnless(file.id) }
    val finding by vm.findingFaces.collectAsStateWithLifecycle()
    var picked by remember(file.id) { mutableStateOf<FaceRepository.FoundFace?>(null) }
    val start by vm.findStart.collectAsStateWithLifecycle()
    found?.let { list ->
        val at = list.firstOrNull()?.timeMs ?: start
        val later = isVideo && list.isNotEmpty() && at > start
        if (picked == null) CriptaAlertDialog(onDismissRequest = { vm.closeFoundFaces() },
            title = { Text(if (!isVideo) str(R.string.faces_in_photo) else if (later) str(R.string.faces_at, clock(at)) else str(R.string.faces_now)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        finding -> Text(if (isVideo) str(R.string.faces_searching_later) else str(R.string.faces_searching))
                        list.isEmpty() -> Text(str(if (isVideo) R.string.faces_none_video else R.string.faces_none_photo))
                        else -> {
                            if (later) Text(str(R.string.faces_a_bit_later), style = MaterialTheme.typography.bodyMedium)
                            Text(str(R.string.faces_tap_to_add), style = MaterialTheme.typography.bodySmall)
                            // Large, so faces are told apart at a glance: two per row in a portrait dialog.
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                list.forEachIndexed { n, f ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { picked = f }) {
                                        Image(f.picture.asImageBitmap(), str(R.string.face_candidate_n, n + 1, list.size),
                                            Modifier.size(128.dp).clip(MaterialTheme.shapes.medium))
                                        Text(f.why ?: str(R.string.face_quality_good), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    // Not the face wanted: look on from just after these (or after the stretch searched).
                    if (isVideo && !finding) TextButton(onClick = { vm.findFaces(file, from = if (list.isEmpty()) start + 15_500 else at + 1_000) }) {
                        com.cripta.app.ui.components.ButtonText(str(R.string.search_further))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.closeFoundFaces() }) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } },
            dismissButton = { TextButton(onClick = { vm.closeFoundFaces(); adding = true }) { com.cripta.app.ui.components.ButtonText(str(R.string.without_face)) } })
        else picked?.let { chosen ->
            FaceEditor(null, null, people, vm,
                onPick = { id -> vm.addFoundFace(file, chosen, id, "") },
                onNew = { name -> vm.addFoundFace(file, chosen, null, name) },
                onDismiss = { picked = null; vm.closeFoundFaces() })
        }
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text(str(R.string.faces_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } })
    }
    val also by vm.alsoFaces.collectAsStateWithLifecycle()
    also?.takeIf { it.fileId == file.id }?.let { a -> AlsoDialog(a, byId[a.personId]?.title ?: FaceRepository.UNNAMED, vm) }
}

/**
 * One face of the media: its picture (ring and tick when selected), who it is, its moments. A
 * suggestion ("Forse è …?") is answered right here; a placed face has its star; the rest is in ⋮.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FaceRow(t: TrackCard, p: FaceRepository.Person?, file: FileEntity, isVideo: Boolean, vm: ViewerViewModel,
                    on: Boolean, selecting: Boolean, onTap: () -> Unit, onLongPress: () -> Unit, onSplit: () -> Unit) {
    val tap = Modifier.combinedClickable(onClickLabel = if (!selecting) str(R.string.who_is_it) else if (on) str(R.string.deselect) else str(R.string.select),
        onLongClickLabel = str(R.string.select), onLongClick = onLongPress, onClick = onTap)
    val suggested = p != null && t.state == "suggested"
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(tap) {
            FaceAvatar(t.id, { vm.faceCrop(it) }, 64.dp, key = t.timeMs, selected = on)
        }
        Column(Modifier.weight(1f).then(tap)) {
            Text(when {
                p == null -> str(R.string.who_tap)
                suggested -> str(R.string.maybe_is, p.title)
                else -> p.title
            }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (p == null || p.entity.name.isBlank() || suggested) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (isVideo) {
                val moments = t.moments.ifEmpty { listOf(t.timeMs) }
                Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    moments.forEach { ms ->
                        com.cripta.app.ui.components.ActionChip(onClick = { vm.seekPlayer(file.id, maxOf(0L, ms - 1_000), play = true) }, label = { com.cripta.app.ui.components.OneLineText(clock(ms)) })
                    }
                }
            }
        }
        if (selecting) return@Row
        if (suggested && p != null) {
            // One tap answers the suggestion; nothing is added without this yes.
            IconButton(onClick = { vm.confirmFace(t.id, p.id) }) {
                Icon(CriptaIcons.Check, str(R.string.yes_is, p.title), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { vm.rejectFace(t.id, p.id) }) {
                Icon(CriptaIcons.Close, str(R.string.no_not, p.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Row
        }
        if (p != null) IconButton(onClick = { vm.setPersonFavorite(p.id, !p.entity.favorite) }) {
            val fav = p.entity.favorite
            androidx.compose.animation.Crossfade(fav, animationSpec = com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.SHORT), label = "personStar") { on ->
                Icon(if (on) CriptaIcons.Star else CriptaIcons.StarBorder,
                    if (on) str(R.string.unfavorite_x, p.title) else str(R.string.favorite_x, p.title),
                    tint = if (on) MaterialTheme.colorScheme.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.popOn(fav))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(CriptaIcons.MoreVert, str(R.string.face_more_actions)) }
            DropdownMenu(menu && com.cripta.app.ui.components.vaultUnlocked(), { menu = false }) {
                if (p != null) DropdownMenuItem(text = { Text(str(R.string.not_x, p.title)) }, onClick = { menu = false; vm.rejectFace(t.id, p.id) })
                if (isVideo) DropdownMenuItem(text = { Text(str(R.string.split_more)) }, onClick = { menu = false; onSplit() })
                DropdownMenuItem(text = { Text(str(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; vm.deleteFaces(t.fileId, listOf(t.id)) })
                DropdownMenuItem(text = { Text(str(R.string.not_a_face), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; vm.deleteFaces(t.fileId, listOf(t.id), everywhere = true) })
            }
        }
    }
}

/** "Anche questi sono …?": the other faces of the media like the person just named; tap to leave one out. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AlsoDialog(a: ViewerViewModel.AlsoFaces, name: String, vm: ViewerViewModel) {
    val off by a.off.collectAsStateWithLifecycle()
    val picked = a.faces.map { it.card.id }.filter { it !in off }
    CriptaAlertDialog(onDismissRequest = { vm.dismissAlso() },
        title = { Text(str(R.string.also_these_q, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(str(R.string.also_these_hint, name), style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    a.faces.forEachIndexed { n, s ->
                        val on = s.card.id !in off
                        // Which face it is, not only what a tap does: TalkBack read the same for each.
                        val which = str(R.string.face_candidate_n, n + 1, a.faces.size)
                        Box(Modifier.clip(CircleShape)
                            .semantics { contentDescription = which; selected = on }
                            .clickable(onClickLabel = if (on) str(R.string.exclude) else str(R.string.include)) {
                                a.off.value = if (on) off + s.card.id else off - s.card.id
                            }) {
                            FaceAvatar(s.card.id, { vm.faceCrop(it) }, 88.dp, selected = on)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { vm.joinAlso(a, picked) }) { com.cripta.app.ui.components.ButtonText(str(R.string.yes_join)) } },
        dismissButton = { TextButton(onClick = { vm.dismissAlso() }) { com.cripta.app.ui.components.ButtonText(str(R.string.no)) } })
}

/** "Dividi": the moments of a face that are someone else become a face of their own. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SplitDialog(t: TrackCard, moments: List<Long>, vm: ViewerViewModel, onDismiss: () -> Unit) {
    var picked by remember(t.id) { mutableStateOf(emptySet<Long>()) }
    CriptaAlertDialog(onDismissRequest = onDismiss, title = { Text(str(R.string.split)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(str(R.string.split_hint),
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moments.forEach { ms ->
                        com.cripta.app.ui.components.ChoiceChip(selected = ms in picked, onClick = { picked = if (ms in picked) picked - ms else picked + ms },
                            label = { com.cripta.app.ui.components.OneLineText(clock(ms)) })
                    }
                }
                TextButton(onClick = { vm.seekPlayer(t.fileId, maxOf(0L, (picked.minOrNull() ?: moments.first()) - 1_000), play = true) }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.watch_from_first))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty() && picked.size < moments.size, onClick = { vm.splitFace(t.fileId, t.id, picked); onDismiss() }) { com.cripta.app.ui.components.ButtonText(str(R.string.split)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } })
}

/**
 * "Chi è?": who the face is. The people first (search, or a new one), the name of its current
 * person after (renaming renames them everywhere). Not them, split and delete live in the row's ⋮.
 */
@Composable
private fun FaceEditor(
    t: TrackCard?, current: FaceRepository.Person?, people: List<FaceRepository.Person>, vm: ViewerViewModel,
    onPick: (Long) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit,
) {
    val placed = t != null && current != null && t.state != "suggested"
    var name by remember(t?.id) { mutableStateOf(if (placed && current != null) current.entity.name else "") }
    var query by remember(t?.id) { mutableStateOf("") }
    CriptaAlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (t == null) str(R.string.add_to_person) else str(R.string.who_is_it)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FaceAvatar(t, { vm.faceCrop(it) }, 72.dp)
                    Text(when {
                        current == null -> str(R.string.not_assigned_yet)
                        !placed -> str(R.string.maybe_is_plain, current.title)
                        else -> str(R.string.now_is, current.title)
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                com.cripta.app.ui.components.CriptaTextField(query, { query = it }, singleLine = true,
                    placeholder = { Text(str(R.string.search_or_new_name)) }, modifier = Modifier.fillMaxWidth())
                // Everyone (the user makes them all), starred first, then the newest.
                val list = people.filter { it.id != current?.id && !it.entity.ignored && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
                    .sortedWith(compareBy({ !it.entity.favorite }, { -it.entity.createdAt }))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    if (query.isBlank() || people.none { it.entity.name.equals(query.trim(), ignoreCase = true) }) item {
                        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onNew(query); onDismiss() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center) {
                                Icon(CriptaIcons.Add, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(if (query.isBlank()) str(R.string.new_person) else str(R.string.new_person_named, query.trim()), color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                    items(list, key = { it.id }) { q ->
                        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onPick(q.id); onDismiss() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FaceAvatar(q.cover, { vm.faceCrop(it) }, 48.dp)
                            Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                color = if (q.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            if (q.entity.favorite) Icon(CriptaIcons.Star, str(R.string.favorite_f), tint = MaterialTheme.colorScheme.favorite)
                        }
                    }
                }
                if (placed && current != null) {
                    com.cripta.app.ui.components.CriptaTextField(name, { name = it }, singleLine = true, label = { Text(str(R.string.name_of, current.title)) },
                        supportingText = { Text(str(R.string.applies_to_all_media)) }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (placed && current != null) TextButton(enabled = name.trim() != current.entity.name, onClick = { vm.renamePerson(current.id, name); onDismiss() }) { com.cripta.app.ui.components.ButtonText(str(R.string.save_name)) }
            else TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) }
        },
        dismissButton = { if (placed) TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } })
}

private fun clock(ms: Long) = (ms / 1000).let { s -> if (s >= 3600) "%d:%02d:%02d".format(com.cripta.app.Strings.locale, s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(com.cripta.app.Strings.locale, s / 60, s % 60) }

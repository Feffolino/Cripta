package com.cripta.app.ui.people

import com.cripta.app.ui.theme.favorite
import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.cripta.app.ui.components.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.tween
import com.cripta.app.ui.theme.Motion
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.face.FaceRepository
import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.CriptaDialog
import com.cripta.app.ui.components.MediaThumb
import com.cripta.app.ui.components.showUndo
import com.cripta.app.ui.components.vaultUnlocked
import com.cripta.app.ui.settings.CompareDialog
import com.cripta.app.ui.settings.FaceAvatar
import com.cripta.app.ui.settings.PeoplePicker
import com.cripta.app.ui.settings.SettingsViewModel

/** How a person's media are ordered on their profile. */
private enum class MediaOrder(private val labelRes: Int) {
    NEWEST(R.string.order_newest), OLDEST(R.string.order_oldest), NAME(R.string.sort_name), SIZE(R.string.order_largest);

    val label: String get() = str(labelRes)
}

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
    // As long as in the viewer and the people page, not counting while the vault is locked.
    val undoMs = com.cripta.app.ui.components.rememberUndoMillis()
    val locked = com.cripta.app.ui.components.rememberVaultLocked()
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        if (snackbar.showUndo(u.first, str(R.string.undo), undoMs, locked)) vm.undoFaceChange()
        else if (vm.faceUndo.value === u) vm.faceUndo.value = null
    }
    val o = overview
    val p = o?.people?.firstOrNull { it.id == personId }
    // Gone (merged into another, deleted): nothing to show here any more. Not while the "Annulla"
    // of what took them away is on offer: it lives on this page, and leaving at once lost it (the
    // last media taken from a nameless person, "Elimina la persona"); taken back, they show again.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(p != null, o != null, undo == null) { if (p != null) seen = true else if (o != null && seen && undo == null) onBack() }

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
                title = { com.cripta.app.ui.components.RollingCountText(selected.size) { str(R.string.n_selected, it) } },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(CriptaIcons.Close, str(R.string.cancel_selection)) } },
                actions = {
                    TextButton(onClick = { vm.removeFromPerson(p.id, selected); selected = emptySet() }) { com.cripta.app.ui.components.ButtonText(str(R.string.remove_short)) }
                    TextButton(onClick = { moving = true }) { com.cripta.app.ui.components.ButtonText(str(R.string.move)) }
                    Box {
                        IconButton(onClick = { selMenu = true }) { Icon(CriptaIcons.MoreVert, str(R.string.more)) }
                        val faceOf = remember(p) { p.faces.groupBy { it.fileId }.mapValues { (_, l) -> l.maxBy { it.width } } }
                        DropdownMenu(selMenu && vaultUnlocked(), { selMenu = false }) {
                            val one = selected.singleOrNull()
                            val face = one?.let { faceOf[it] }
                            if (face != null) DropdownMenuItem(text = { Text(str(R.string.use_as_face)) },
                                onClick = { selMenu = false; vm.setPersonCover(p.id, face.id); selected = emptySet() })
                            if (one != null) DropdownMenuItem(text = { Text(str(R.string.faces_of_media)) }, onClick = { selMenu = false; vm.describeMedia(one) })
                            val two = selected.toList().takeIf { it.size == 2 }?.mapNotNull { faceOf[it] }?.takeIf { it.size == 2 }
                            if (two != null) DropdownMenuItem(text = { Text(str(R.string.compare_two_faces)) }, onClick = { selMenu = false; comparing = two[0] to two[1] })
                        }
                    }
                },
            ) else TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
                // DS Profilo: no title in the bar, the name is the page's headline.
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } },
                actions = {
                    if (p != null) {
                        IconButton(onClick = { vm.setPersonFavorite(p.id, !p.entity.favorite) }) {
                            Icon(if (p.entity.favorite) CriptaIcons.Star else CriptaIcons.StarBorder,
                                if (p.entity.favorite) str(R.string.unfavorite_short) else str(R.string.favorite_add),
                                tint = if (p.entity.favorite) MaterialTheme.colorScheme.favorite else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(CriptaIcons.MoreVert, str(R.string.more)) }
                            DropdownMenu(menu && vaultUnlocked(), { menu = false }) {
                                DropdownMenuItem(text = { Text(str(R.string.rename)) }, onClick = { menu = false; renaming = true })
                                DropdownMenuItem(text = { Text(str(R.string.change_face)) }, onClick = { menu = false; pickingCover = true })
                                DropdownMenuItem(text = { Text(str(R.string.show_in_folders)) }, onClick = { menu = false; vm.showPersonInVault(p.id); onShowInVault() })
                                DropdownMenuItem(text = { Text(str(R.string.merge_with)) }, onClick = { menu = false; merging = true })
                                // Their videos where the user confirmed them: each looked at closer around them.
                                val confirmedVideos = p.faces.filter { it.state == "manual" }.map { it.fileId }.distinct()
                                    .filter { id -> p.media.any { it.id == id && com.cripta.app.data.VaultRepository.isVideo(it.mimeType) } }
                                if (confirmedVideos.isNotEmpty()) DropdownMenuItem(text = { Text(str(R.string.analyse_their_videos)) },
                                    onClick = { menu = false; vm.refineVideos(confirmedVideos) })
                                if (p.entity.ignored) DropdownMenuItem(text = { Text(str(R.string.restore)) }, onClick = { menu = false; vm.restorePerson(p.id) })
                                // The page goes once the person is gone and its "Annulla" with it (see
                                // `seen` above), so the work is never cut short with it.
                                else if (p.media.isEmpty()) DropdownMenuItem(text = { Text(str(R.string.delete_person)) }, onClick = { menu = false; vm.deleteEmptyPerson(p.id) })
                                // Hidden, the person keeps their profile (marked hidden, "Ripristina" in
                                // this menu): the page stays, with its "Annulla". Leaving at once lost it.
                                else DropdownMenuItem(text = { Text(str(R.string.not_a_person)) }, onClick = { menu = false; vm.ignorePerson(p.id) })
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (p == null || o == null) {
            ProfileSkeleton(Modifier.fillMaxSize().padding(pad))
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
        LazyVerticalGrid(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) GridCells.Fixed(3) else GridCells.Adaptive(112.dp),
            Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(p, vm, onCover = { pickingCover = true }, onRename = { renaming = true })
            }
            // DS order: notes, the "Forse è …" row, then the media.
            item(span = { GridItemSpan(maxLineSpan) }) { NotesAndLinks(p, vm) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SuggestionsEntry(p, vm) { suggesting = true }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(str(R.string.media_count, p.media.size), style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() })
                    if (p.media.isEmpty()) {
                        Text(str(R.string.person_no_media, p.title),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaOrder.entries.forEach { m ->
                                com.cripta.app.ui.components.ChoiceChip(selected = order == m, onClick = { order = m }, label = { com.cripta.app.ui.components.OneLineText(m.label) })
                            }
                        }
                        Text(str(R.string.person_media_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(media, key = { it.id }) { f ->
                val face = faceOf[f.id]
                val on = f.id in selected
                // TalkBack: the file's name and whether it is selected (the picture says neither).
                Box(Modifier.animateItem().semantics {
                    contentDescription = f.originalName
                    this.selected = on
                }.combinedClickable(onClickLabel = if (selected.isEmpty()) str(R.string.open) else str(R.string.select), onLongClickLabel = str(R.string.select),
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
        if (merging) PeoplePicker(str(R.string.merge_into, p.title), o, exclude = p.id, multi = true, vm = vm, onDismiss = { merging = false }) { picked, _ ->
            vm.mergePeople(p.id, picked); merging = false
        }
        if (moving) PeoplePicker(str(R.string.move_media_to, selected.size), o, exclude = p.id, multi = false, vm = vm, allowNew = true,
            onDismiss = { moving = false }) { picked, newName ->
            vm.moveToPerson(p.id, selected, picked.firstOrNull(), newName); moving = false; selected = emptySet()
        }
        if (suggesting) SuggestionsDialog(p, o.files, vm, onClose = { suggesting = false }, onOpen = { card ->
            vm.openPersonMedia(listOf(card.fileId), card.fileId, card.timeMs); onOpenFile(card.fileId)
        })
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text(str(R.string.faces_title)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(m) } },
            confirmButton = { TextButton(onClick = { vm.faceMessage.value = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } })
    }
}

/** The profile while it is read: the round face, the name and count lines, then the grid of media. */
@Composable
private fun ProfileSkeleton(modifier: Modifier) {
    LazyVerticalGrid(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) GridCells.Fixed(3) else GridCells.Adaptive(112.dp),
        modifier, userScrollEnabled = false, contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                com.cripta.app.ui.components.Skeleton(Modifier.size(112.dp), CircleShape)
                com.cripta.app.ui.components.Skeleton(Modifier.padding(top = 18.dp).width(160.dp).height(24.dp))
                com.cripta.app.ui.components.Skeleton(Modifier.padding(top = 8.dp).width(96.dp).height(12.dp))
            }
        }
        items(12) { com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), MaterialTheme.shapes.small) }
    }
}

/** DS Profilo: the face (tap: choose another), the name with its pencil (rename), how many media. */
@Composable
private fun ProfileHeader(p: FaceRepository.Person, vm: SettingsViewModel, onCover: () -> Unit, onRename: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FaceAvatar(p.cover, { vm.faceCrop(it) }, 112.dp,
            Modifier.clip(CircleShape).clickable(onClickLabel = str(R.string.choose_face_to_show), onClick = onCover))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (p.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false).minimumInteractiveComponentSize().clickable(onClickLabel = str(R.string.rename), onClick = onRename))
            IconButton(onClick = onRename, modifier = Modifier.padding(start = 4.dp).minimumInteractiveComponentSize().size(40.dp)) {
                Icon(CriptaIcons.Edit, str(R.string.rename), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        val videos = p.media.count { VaultRepository.isVideo(it.mimeType) }
        Text(listOfNotNull(
            plural(R.plurals.media_n, p.media.size),
            if (videos > 0) plural(R.plurals.videos_n, videos) else null,
            if (p.entity.ignored) str(R.string.hidden_f) else null,
        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * DS row "Forse è …": how many faces could be theirs, counted when the profile opens; opens the
 * review when there is something to check.
 */
@Composable
private fun SuggestionsEntry(p: FaceRepository.Person, vm: SettingsViewModel, onOpen: () -> Unit) {
    val mode = vm.settings.collectAsStateWithLifecycle().value.facesMode
    // Counted again when their faces grow too (the closer look at a video joins faces into theirs), not only when they get one more.
    val found by produceState<Int?>(null, p.id, p.faces.size, p.faces.sumOf { it.faceCount }, mode) {
        value = com.cripta.app.util.catching { vm.suggestionsFor(p.id).size }.getOrDefault(0)
    }
    val n = found
    val known = mode == "known"
    val caption = when {
        p.faces.isEmpty() -> str(R.string.person_assign_first)
        known && p.faces.map { it.fileId }.distinct().size < 2 -> str(R.string.person_two_media)
        n == null -> str(R.string.looking_similar)
        n == 0 -> str(R.string.nothing_to_confirm)
        n == 1 -> plural(R.plurals.faces_to_confirm, 1, 1, "1")
        else -> plural(R.plurals.faces_to_confirm, n, n, com.cripta.app.ui.components.formatCount(n))
    }
    val open = (n ?: 0) > 0
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .then(if (open) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = str(R.string.check_suggestions), onClick = onOpen) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CriptaIcons.HelpOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(str(R.string.maybe_is_plain, p.title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (open) Icon(CriptaIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The user's notes and links, saved as they type. */
@Composable
private fun NotesAndLinks(p: FaceRepository.Person, vm: SettingsViewModel) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var notes by remember(p.id) { mutableStateOf(p.entity.notes) }
    val links = remember(p.id) { mutableStateListOf<String>().apply { addAll(p.entity.links.lines().filter { it.isNotBlank() }) } }
    var newLink by remember(p.id) { mutableStateOf("") }
    fun linkList(s: String) = s.lines().filter { it.isNotBlank() }
    // What the fields were last in step with: what is stored, as this page last saw it.
    var base by remember(p.id) { mutableStateOf(p.entity.notes to p.entity.links) }
    // Changed elsewhere (another profile merged into this one brings its notes and links): taken
    // in where nothing typed here is waiting. Kept as it was, the draft overwrote them on leaving.
    LaunchedEffect(p.entity.notes, p.entity.links) {
        val (bn, bl) = base
        if (p.entity.notes == bn && p.entity.links == bl) return@LaunchedEffect
        if (notes == bn) notes = p.entity.notes
        if (links.toList() == linkList(bl)) { links.clear(); links.addAll(linkList(p.entity.links)) }
        base = p.entity.notes to p.entity.links
    }
    fun save() = vm.setPersonProfile(p.id, notes, links.toList())
    LaunchedEffect(notes) {
        kotlinx.coroutines.delay(700)
        if (notes != p.entity.notes) save()
    }
    // Back or a media opened within the pause above: the last characters typed were never saved
    // (the wait was cancelled with the page). Only what was typed here and not stored yet goes now.
    val latestNotes by androidx.compose.runtime.rememberUpdatedState(notes)
    val latestBase by androidx.compose.runtime.rememberUpdatedState(base)
    androidx.compose.runtime.DisposableEffect(p.id) {
        onDispose {
            val l = links.toList()
            val (bn, bl) = latestBase
            if (latestNotes != bn || l != linkList(bl)) vm.flushPersonProfile(p.id, latestNotes, l)
        }
    }
    // DS Profilo: the notes as one slab field right under the name (no heading: the placeholder
    // says what it is); the links follow as plain rows.
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.cripta.app.ui.components.CriptaTextField(notes, { notes = it }, placeholder = { Text(str(R.string.person_notes_hint)) },
            minLines = 2, modifier = Modifier.fillMaxWidth())
        links.toList().forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CriptaIcons.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(l, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).minimumInteractiveComponentSize().clickable(onClickLabel = str(R.string.open_link)) { openLink(context, l) }
                        .padding(vertical = 10.dp))
                IconButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(l))
                    android.widget.Toast.makeText(context, str(R.string.link_copied), android.widget.Toast.LENGTH_SHORT).show()
                }) { Icon(CriptaIcons.ContentCopy, str(R.string.copy_link)) }
                IconButton(onClick = { links.removeAt(i); save() }) { Icon(CriptaIcons.Close, str(R.string.remove_link)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.cripta.app.ui.components.CriptaTextField(newLink, { newLink = it }, singleLine = true,
                placeholder = { Text(str(R.string.add_link)) },
                // As in the video details: paste what was copied and keep it as a link at once.
                trailingIcon = if (newLink.isNotBlank()) null else ({
                    IconButton(onClick = {
                        val clip = clipboard.getText()?.text?.trim().orEmpty()
                        if (clip.isBlank()) android.widget.Toast.makeText(context, str(R.string.clipboard_empty), android.widget.Toast.LENGTH_SHORT).show()
                        else { links.add(clip); save() }
                    }) { Icon(CriptaIcons.ContentPaste, str(R.string.paste_copied_link), tint = MaterialTheme.colorScheme.primary) }
                }),
                modifier = Modifier.weight(1f))
            TextButton(enabled = newLink.isNotBlank(), onClick = { links.add(newLink.trim()); newLink = ""; save() }) { com.cripta.app.ui.components.ButtonText(str(R.string.add)) }
        }
    }
}

/**
 * Whether [link] starts with its own scheme ("https:", "mailto:", "tel:"). An address with a port
 * and no scheme ("example.com:8080/a", "localhost:3000") is not one: taken as the scheme
 * "example.com", no app could open it.
 */
internal fun hasScheme(link: String): Boolean {
    if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(link)) return false
    val hostWithPort = Regex("^([^/:]+):\\d+(?:[/?#]|$)").find(link)?.groupValues?.get(1)
    return hostWithPort == null || !(hostWithPort.contains('.') || hostWithPort.equals("localhost", ignoreCase = true))
}

/** A link opens in the user's browser (Cripta itself has no network access). */
internal fun openLink(context: android.content.Context, link: String) {
    val url = if (hasScheme(link)) link else "https://$link"
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { android.widget.Toast.makeText(context, str(R.string.no_app_for_link), android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable
private fun RenameDialog(p: FaceRepository.Person, o: FaceRepository.Overview, vm: SettingsViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf(p.entity.name) }
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text(str(R.string.person_name)) },
        text = {
            Column {
                com.cripta.app.ui.components.CriptaTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val twin = o.people.firstOrNull { it.id != p.id && name.isNotBlank() && it.entity.name.equals(name.trim(), ignoreCase = true) }
                Text(if (twin != null) str(R.string.name_exists_merge, twin.title)
                    else str(R.string.name_applies_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (twin != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { vm.renamePerson(p.id, name); onClose() }) { com.cripta.app.ui.components.ButtonText(str(R.string.save)) } },
        dismissButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } })
}

/** Every face found of the person: tap one to show it on their profile, Home and the lists. */
@Composable
private fun CoverPicker(p: FaceRepository.Person, vm: SettingsViewModel, onClose: () -> Unit) {
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text(str(R.string.face_to_show)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (p.faces.isEmpty()) str(R.string.person_no_faces) else plural(R.plurals.faces_found_n, p.faces.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(p.faces.sortedByDescending { it.width }, key = { it.id }) { f ->
                        val on = f.id == p.cover?.id
                        FaceAvatar(f, { vm.faceCrop(it) }, 88.dp,
                            Modifier.clip(CircleShape).clickable(onClickLabel = str(R.string.use_this_face)) { vm.setPersonCover(p.id, f.id); onClose() },
                            selected = on)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } })
}

private fun likeness(s: Float) = when {
    s >= 0.5f -> str(R.string.very_similar)
    s >= 0.42f -> str(R.string.similar)
    else -> str(R.string.maybe)
}

private fun clock(ms: Long) = (ms / 1000).let { s -> if (s >= 3600) "%d:%02d:%02d".format(com.cripta.app.Strings.locale, s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(com.cripta.app.Strings.locale, s / 60, s % 60) }

/**
 * "Forse è …": one face at a time, full screen, swiped up like a feed (most alike first). The frame
 * it was found in takes the free space (the face ringed; a tap plays from there), under it the face
 * found next to the person's own, then "No" and "È …" across the width. An answer moves on to the
 * next by itself; swiping skips without answering. Nothing is added by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionsDialog(p: FaceRepository.Person, files: Map<String, FileEntity>, vm: SettingsViewModel,
                              onClose: () -> Unit, onOpen: (TrackCard) -> Unit) {
    // Back from playing a suggestion: the same list at once, on the same face.
    val cached = remember(p.id) { vm.lastSuggestions?.takeIf { it.first == p.id }?.second }
    val list = remember(p.id) { mutableStateListOf<FaceRepository.Suggestion>().apply { cached?.let { addAll(it) } } }
    var loading by remember(p.id) { mutableStateOf(cached == null) }
    LaunchedEffect(p.id) {
        if (cached == null) {
            list.addAll(com.cripta.app.util.catching { vm.suggestionsFor(p.id) }.getOrDefault(emptyList())); loading = false
        }
        vm.lastSuggestions = p.id to list.toList()
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = if (cached != null) vm.lastSuggestionPage else 0) { list.size }
    LaunchedEffect(pager) { androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { vm.lastSuggestionPage = it } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    /** The answers given here (face → yes): answered pages stay where they are, marked, so nothing jumps. */
    var answers by remember(p.id) { mutableStateOf(if (cached != null) vm.lastAnswers else emptyMap()) }
    val answeredCount = answers.size
    /**
     * After a yes the person has one more face: the faces still ahead are looked for again with it,
     * so the next ones are the new most alike (and those no longer alike enough go). The pages seen
     * so far stay as they are. A newer answer cancels a refresh still running.
     */
    var refresh by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun refreshAhead() {
        refresh?.cancel()
        refresh = scope.launch {
            kotlinx.coroutines.delay(600)   // the yes is written first
            val fresh = com.cripta.app.util.catching { vm.suggestionsFor(p.id) }.getOrNull() ?: return@launch
            val keep = list.take(pager.currentPage + 1)
            val kept = keep.map { it.card.id }.toSet()
            val ahead = fresh.filter { it.card.id !in kept && it.card.id !in answers }
            list.removeRange(keep.size, list.size)
            list.addAll(ahead)
            vm.lastSuggestions = p.id to list.toList()
        }
    }
    /** Answer the face on [page], then glide to the next one. */
    fun answer(page: Int, s: FaceRepository.Suggestion, yes: Boolean) {
        // Not saved (the message says so): the face is asked again, not left marked as answered.
        val undo = {
            answers = answers - s.card.id
            vm.lastAnswers = answers
        }
        if (yes) vm.acceptSuggestion(s.card.id, p.id, undo) else vm.refuseSuggestion(s.card.id, p.id, undo)
        answers = answers + (s.card.id to yes)
        vm.lastAnswers = answers
        scope.launch {
            if (page + 1 < list.size) pager.animateScrollToPage(page + 1, animationSpec = tween(Motion.LONG, 0, Motion.EaseOutQuart))
            if (yes) refreshAhead()
        }
    }
    val close = { vm.lastSuggestions = null; vm.lastAnswers = emptyMap(); vm.lastSuggestionPage = 0; onClose() }
    CriptaDialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { Text(str(R.string.maybe_is, p.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = close) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } },
                // DS: where you are ("3 di 18") as a muted caption at the bar's end.
                actions = {
                    if (list.isNotEmpty()) Text(if (answeredCount == list.size) str(R.string.done)
                        else str(R.string.x_of_y, com.cripta.app.ui.components.formatCount((pager.currentPage + 1).coerceAtMost(list.size)), com.cripta.app.ui.components.formatCount(list.size)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp))
                })
        }) { pad ->
            if (loading || list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(if (loading) str(R.string.comparing_with_faces, p.faces.size, p.title)
                        else if (answeredCount > 0) str(R.string.done_nothing_left)
                        else str(R.string.nothing_left),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                return@Scaffold
            }
            androidx.compose.foundation.pager.VerticalPager(state = pager, modifier = Modifier.fillMaxSize().padding(pad),
                key = { list.getOrNull(it)?.card?.id ?: it }, beyondViewportPageCount = 2) { page ->
                val s = list.getOrNull(page) ?: return@VerticalPager
                SuggestionPage(s, p, files[s.card.fileId], vm, answer = answers[s.card.id],
                    onYes = { answer(page, s, true) }, onNo = { answer(page, s, false) }, onOpen = { onOpen(s.card) })
            }
        }
    }
}

/** One page of "Forse è …": the frame as large as the screen allows, the two faces, the answer. */
@Composable
private fun SuggestionPage(s: FaceRepository.Suggestion, p: FaceRepository.Person, file: FileEntity?, vm: SettingsViewModel,
                           answer: Boolean?, onYes: () -> Unit, onNo: () -> Unit, onOpen: () -> Unit) {
    val frame by produceState<Bitmap?>(null, s.card.id) { value = com.cripta.app.util.catching { vm.frameOf(s.card) }.getOrNull() }
    val isVideo = file != null && VaultRepository.isVideo(file.mimeType)
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val picture: @Composable (Modifier) -> Unit = { m ->
        // The whole frame, letterboxed in the free space; a tap plays from that moment.
        Box(m.clip(com.cripta.app.ui.theme.CoverShape).clickable(onClickLabel = if (isVideo) str(R.string.play_from_here) else str(R.string.open), onClick = onOpen),
            contentAlignment = Alignment.Center) {
            // While the frame is read from the video, the face itself, large: the page is usable at once.
            if (frame == null) FaceAvatar(s.card, { vm.faceCrop(it) }, 180.dp)
            else FrameWithFace(frame, s.card.box, Modifier.fillMaxSize())
            // DS: on a video, where it plays from, on the glass at the bottom left.
            if (isVideo) Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).heightIn(min = 22.dp).clip(CircleShape)
                    .background(com.cripta.app.ui.components.OnImageGlass).padding(start = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CriptaIcons.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                Text(str(R.string.from_prefix) + clock(s.card.timeMs), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
    val decision: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // DS "Forse è …": the face found and the person's own, large and centred, then one line.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
                Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FaceAvatar(s.card, { vm.faceCrop(it) }, 96.dp)
                    Text(str(R.string.found_here), style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 6.dp))
                }
                Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FaceAvatar(p.cover, { vm.faceCrop(it) }, 96.dp)
                    Text(p.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Text(likeness(s.similarity) + " · " + com.cripta.app.ui.components.formatCount(p.media.size) + str(R.string.media_of_sep) + p.title +
                    (file?.let { " · " + it.originalName + if (isVideo) str(R.string.from_sep) + clock(s.card.timeMs) else "" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth())
            // The answer across the width, large: the one thing to do on this page. Once given, it
            // is said in its place (the page stays, the next one is below).
            if (answer != null) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Icon(if (answer) CriptaIcons.Check else CriptaIcons.Close, null,
                    tint = if (answer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (answer) "  " + str(R.string.is_x, p.title) else "  " + str(R.string.not_x, p.title), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onNo, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.not_x, p.title))
                }
                com.cripta.app.ui.components.Button(onClick = onYes, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(CriptaIcons.Check, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    com.cripta.app.ui.components.ButtonText(str(R.string.is_x, p.title))
                }
            }
        }
    }
    if (landscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            picture(Modifier.weight(1.4f).fillMaxHeight())
            decision(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            picture(Modifier.weight(1f).fillMaxWidth())
            decision(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun SuggestionItem(s: FaceRepository.Suggestion, p: FaceRepository.Person, file: FileEntity?, vm: SettingsViewModel,
                           onYes: () -> Unit, onNo: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val frame by produceState<Bitmap?>(null, s.card.id) { value = com.cripta.app.util.catching { vm.frameOf(s.card) }.getOrNull() }
    val isVideo = file != null && VaultRepository.isVideo(file.mimeType)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The face found, and next to it the person's own face to compare with.
            FaceAvatar(s.card.id, { vm.faceCrop(it) }, 96.dp)
            FaceAvatar(p.cover, { vm.faceCrop(it) }, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(likeness(s.similarity), style = MaterialTheme.typography.titleMedium)
                Text(str(R.string.similarity_fmt).format(com.cripta.app.Strings.locale, s.similarity), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FrameWithFace(frame, s.card.box, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClickLabel = str(R.string.open), onClick = onOpen))
        Text((file?.originalName ?: str(R.string.media)) + if (isVideo) " · ${clock(s.card.timeMs)}" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onYes) { com.cripta.app.ui.components.ButtonText(str(R.string.is_x, p.title)) }
            TextButton(onClick = onNo) { com.cripta.app.ui.components.ButtonText(str(R.string.not_them)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpen) { Icon(CriptaIcons.PlayArrow, if (isVideo) str(R.string.play_from_here) else str(R.string.open)) }
        }
    }
}

/** The frame a face was found in, the face marked by a ring (the box is stored as fractions of it). */
@Composable
internal fun FrameWithFace(frame: Bitmap?, box: String, modifier: Modifier) {
    val r = box.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }
    val accent = MaterialTheme.colorScheme.primary
    if (frame == null) {
        Box(modifier.height(180.dp), contentAlignment = Alignment.Center) {
            Text(str(R.string.loading_frame), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Image(frame.asImageBitmap(), str(R.string.frame), contentScale = ContentScale.Fit,
        modifier = modifier.aspectRatio(frame.width.toFloat() / frame.height.coerceAtLeast(1), matchHeightConstraintsFirst = frame.height > frame.width).drawWithContent {
            drawContent()
            if (r != null) {
                // Where the picture really is: letterboxed (Fit) when the space given has another shape.
                val k = minOf(size.width / frame.width, size.height / frame.height.coerceAtLeast(1))
                val fw = frame.width * k; val fh = frame.height * k
                val ox = (size.width - fw) / 2; val oy = (size.height - fh) / 2
                val pad = 0.15f
                val x = ox + (r[0] - r[2] * pad) * fw; val y = oy + (r[1] - r[3] * pad) * fh
                val w = r[2] * (1 + 2 * pad) * fw; val h = r[3] * (1 + 2 * pad) * fh
                drawRoundRect(accent, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
        })
}

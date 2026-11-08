package com.cripta.app.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.offset
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
import com.cripta.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.cripta.app.ui.components.FilledTonalButton
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
import com.cripta.app.ui.components.TextButton
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
@OptIn(ExperimentalLayoutApi::class)
internal fun PeopleSettings(s: Settings, vm: SettingsViewModel) {
    val overview by vm.people.collectAsStateWithLifecycle()
    val progress by vm.faceProgress.collectAsStateWithLifecycle()
    var askRestart by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    // Same order as Intro e finale: what it does, its state and main action; the settings; then the
    // rare and destructive actions apart at the bottom.
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
    PageIntro(str(R.string.people_page_intro)) {
        ScanStatus(overview, progress, onStop = { vm.cancelFaceScan() })
        // DS Impostazioni › Persone: the state, then "Analizza ora" as the page's tonal action.
        // Wraps: side by side "Analisi in corso…" and "Vedi le persone" ran off a narrow screen.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { vm.startFaceScan() }, enabled = progress == null) {
                Icon(CriptaIcons.Face, null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                com.cripta.app.ui.components.ButtonText(if (progress == null) str(R.string.analyse_now) else str(R.string.analysis_running))
            }
            FilledTonalButton(onClick = { vm.openPeople() }) {
                com.cripta.app.ui.components.ButtonText(str(R.string.see_people))
            }
        }
    }
    Section(str(R.string.recognition)) {
        RadioRow(str(R.string.all_faces), str(R.string.all_faces_desc), s.facesMode == "all") { vm.setFacesMode("all") }
        RadioRow(str(R.string.known_only), str(R.string.known_only_desc), s.facesMode == "known") { vm.setFacesMode("known") }
        ToggleRow(str(R.string.analyse_new_media), s.facesAnalyzeNew, desc = str(R.string.analyse_new_videos_desc),
            icon = CriptaIcons.Face) { vm.setFacesAnalyzeNew(it) }
        // Videos with someone confirmed that the closer look has not seen as they are now (counted again after each scan).
        val wanted by produceState(0, progress == null) { value = if (progress == null) runCatching { vm.refineWanted().size }.getOrDefault(0) else value }
        if (wanted > 0) {
            Text(str(R.string.refine_explain),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { vm.refineAll() }, enabled = progress == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                com.cripta.app.ui.components.ButtonText(plural(R.plurals.refine_videos_btn, wanted, wanted, com.cripta.app.ui.components.formatCount(wanted)))
            }
        }
    }
    Section(str(R.string.display)) {
        ToggleRow(str(R.string.moments_in_player), s.faceMoments,
            desc = str(R.string.moments_in_player_desc),
            icon = CriptaIcons.Timeline) { vm.setFaceMoments(it) }
        ChoiceRow(str(R.string.people_on_home), listOf("off" to str(R.string.hidden_pl), "blur" to str(R.string.blurred), "on" to str(R.string.visible)), s.peopleOnHome,
            icon = CriptaIcons.Home,
            desc = when (s.peopleOnHome) {
                "off" -> str(R.string.home_no_faces)
                "blur" -> str(R.string.home_blurred)
                else -> str(R.string.home_visible)
            }) { vm.setPeopleOnHome(it) }
    }
    Section(str(R.string.maintenance)) {
        TextButton(onClick = { clearing = true }, enabled = progress == null, modifier = Modifier.offset(x = (-12).dp)) {
            com.cripta.app.ui.components.ButtonText(str(R.string.delete_faces_by_mistake))
        }
        val ignored by vm.ignoredFaces.collectAsStateWithLifecycle()
        if (ignored > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.ignored_faces_n, com.cripta.app.ui.components.formatCount(ignored)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.forgetIgnoredFaces() }) { com.cripta.app.ui.components.ButtonText(str(R.string.forget)) }
        }
        TextButton(onClick = { showStats = true }, modifier = Modifier.offset(x = (-12).dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.statistics)) }
        com.cripta.app.ui.components.DestructiveButton(str(R.string.start_over), onClick = { askRestart = true }, enabled = progress == null,
            icon = CriptaIcons.Delete, modifier = Modifier.fillMaxWidth())
    }
    }
    if (askRestart) {
        var everything by remember { mutableStateOf(false) }
        CriptaAlertDialog(onDismissRequest = { askRestart = false },
            title = { Text(str(R.string.start_over_q)) },
            text = {
                Column {
                    Text(str(R.string.people_reanalysed))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { everything = !everything }) {
                        Checkbox(everything, { everything = it })
                        Text(str(R.string.people_clear_names))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.restartFaces(everything); askRestart = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.start_over_btn)) } },
            dismissButton = { TextButton(onClick = { askRestart = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } })
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
    val periods = listOf(3_600_000L to str(R.string.last_hour), 86_400_000L to str(R.string.last_24h), 7 * 86_400_000L to str(R.string.last_7d),
        30 * 86_400_000L to str(R.string.last_30d), Long.MAX_VALUE to str(R.string.always))
    var period by remember { mutableLongStateOf(periods[1].first) }
    val found by produceState<FaceRepository.Detected?>(null, period) {
        value = null
        val since = if (period == Long.MAX_VALUE) 0L else System.currentTimeMillis() - period
        value = runCatching { vm.detectedSince(since) }.getOrNull()
    }
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text(str(R.string.delete_found_faces)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(str(R.string.delete_found_faces_text),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(str(R.string.analysed_in_period), style = MaterialTheme.typography.labelLarge)
                com.cripta.app.ui.components.ChipFlowRow {
                    periods.forEach { (ms, label) ->
                        com.cripta.app.ui.components.ChoiceChip(selected = period == ms, onClick = { period = ms }, label = { Text(label) })
                    }
                }
                val f = found
                Text(when {
                    f == null -> str(R.string.counting)
                    f.faces == 0 -> str(R.string.no_faces_in_period)
                    else -> str(R.string.faces_in_media, f.faces, f.media.size)
                }, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            val f = found
            TextButton(enabled = f != null && f.faces > 0, onClick = { f?.let { vm.clearDetected(it.media) }; onClose() }) {
                com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } })
}

/** Live progress while a scan runs, else how much of the vault is analysed. */
@Composable
private fun ScanStatus(o: FaceRepository.Overview?, progress: ConversionService.Companion.IntroProgress?, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (progress != null) {
            // Frames read inside the videos in hand count too: a scan of long videos moves before
            // the first one ends. The count stays the media finished.
            val share = progress.share ?: (progress.done.toFloat() / maxOf(1, progress.total))
            val exact = share * 100
            val pct = if (exact < 10f) String.format(com.cripta.app.Strings.locale, "%.1f%%", exact) else String.format(com.cripta.app.Strings.locale, "%d%%", exact.toInt())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (progress.stopping) str(R.string.n_cancelling) else str(R.string.analysis_progress, progress.done, progress.total, pct),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onStop, enabled = !progress.stopping) { com.cripta.app.ui.components.ButtonText(str(R.string.stop_short)) }
            }
            LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
            // Which files are in hand, and how far each: a scan that seems stuck says where.
            if (!progress.stopping) {
                val hands = progress.inHand.ifEmpty { listOfNotNull(progress.current?.let { it to null }) }
                for ((name, s) in hands) {
                    Text(str(R.string.analysing_x, name) + (s?.let { String.format(com.cripta.app.Strings.locale, " · %d%%", (it * 100).toInt()) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(str(R.string.people_appear_live), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (o == null) {
            Text(str(R.string.reading_state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val ok = o.scans.count { it.status == "ok" }
            val stalled = o.scans.count { it.status.startsWith(FaceRepository.STALLED) }
            val skipped = o.scans.count { it.status != "ok" && it.status != FaceRepository.CLEARED } - stalled
            val missing = (o.media - o.scans.size).coerceAtLeast(0)
            val notes = listOfNotNull(
                str(R.string.n_unreadable, skipped).takeIf { skipped > 0 },
                plural(R.plurals.stalled_n, stalled).takeIf { stalled > 0 },
            )
            Text(str(R.string.analysed_of, o.scans.size, o.media) + (if (notes.isNotEmpty()) " (${notes.joinToString(", ")})" else "") +
                str(R.string.people_n_sep, o.people.count { it.main }), style = MaterialTheme.typography.bodyMedium)
            if (progress == null && missing > 0) Text(str(R.string.media_missing, missing),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (progress == null) LinearProgressIndicator(progress = { o.scans.size.toFloat() / maxOf(1, o.media) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatsDialog(vm: SettingsViewModel, onClose: () -> Unit) {
    // null = still computing; a failure is its own state, with a retry, never an endless "Calcolo…".
    var attempt by remember { mutableIntStateOf(0) }
    var tech by remember { mutableStateOf(false) }
    val st by produceState<Result<FaceRepository.Stats>?>(null, attempt) { value = null; value = runCatching { vm.faceStats() } }
    CriptaAlertDialog(onDismissRequest = onClose, title = { Text(str(R.string.face_statistics)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val r = st
                if (r == null) { Text(str(R.string.computing)); return@Column }
                val x = r.getOrNull()
                if (x == null) {
                    Text(str(R.string.stats_failed))
                    TextButton(onClick = { attempt++ }) { com.cripta.app.ui.components.ButtonText(str(R.string.retry)) }
                    return@Column
                }
                Text(str(R.string.media_analysed, x.media) + if (x.skipped > 0) str(R.string.n_unreadable_paren, x.skipped) else "")
                if (x.videos > 0) Text(str(R.string.avg_per_video).format(com.cripta.app.Strings.locale, x.videoMs / 1000.0 / x.videos))
                if (x.photos > 0) Text(str(R.string.avg_per_photo).format(com.cripta.app.Strings.locale, x.photoMs / 1000.0 / x.photos))
                Text(str(R.string.faces_found, x.faces))
                if (x.rejects.isNotEmpty()) Text(str(R.string.set_aside_prefix) + x.rejects.entries.joinToString(", ") { "${it.value} ${it.key.label}" })
                // The algorithm's numbers stay available, folded away under what a person needs to know.
                TextButton(onClick = { tech = !tech }, contentPadding = PaddingValues(0.dp)) {
                    com.cripta.app.ui.components.ButtonText(if (tech) str(R.string.hide_tech) else str(R.string.technical_details))
                }
                if (tech) {
                    Text(str(R.string.frames_dup_extra, x.duplicates, x.extra),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(str(R.string.sharpness_header, "%.0f".format(com.cripta.app.Strings.locale, com.cripta.app.media.face.FaceEngine.MIN_SHARPNESS)),
                        style = MaterialTheme.typography.labelLarge)
                    x.sharp.forEachIndexed { i, n ->
                        Text("  ${FaceStats.bandLabel(i)}: $n", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } })
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
    Box(modifier.size(size).then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(5.dp) else Modifier)
        .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(CriptaIcons.Face, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A star on a face: one tap adds or removes the person from the favourites. */
@Composable
internal fun FavoriteStar(on: Boolean, name: String, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = modifier.minimumInteractiveComponentSize().size(36.dp)) {
        Icon(if (on) CriptaIcons.Star else CriptaIcons.StarBorder, if (on) str(R.string.unfavorite_x, name) else str(R.string.favorite_x, name),
            tint = if (on) com.cripta.app.ui.theme.Favorite else androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(26.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).padding(3.dp))
    }
}

/** The mark of a favourite person where it cannot be changed (Home): the same star as everywhere. */
@Composable
internal fun FavoriteBadge(modifier: Modifier = Modifier) {
    Icon(CriptaIcons.Star, str(R.string.favorite_f), tint = com.cripta.app.ui.theme.Favorite,
        modifier = modifier.size(24.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).padding(3.dp))
}

private enum class PeopleFilter(private val labelRes: Int) {
    MAIN(R.string.people), FAVORITE(R.string.pf_favorite), NAMED(R.string.pf_named), SINGLE(R.string.pf_single), IGNORED(R.string.pf_ignored), TODO(R.string.to_sort), NONE(R.string.pf_none);

    val label: String get() = str(labelRes)

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
    com.cripta.app.data.PeopleSort.RELEVANCE -> str(R.string.sort_relevance)
    com.cripta.app.data.PeopleSort.NAME -> str(R.string.sort_name)
    com.cripta.app.data.PeopleSort.MEDIA -> str(R.string.sort_media_count)
    com.cripta.app.data.PeopleSort.LATEST -> str(R.string.sort_latest_media)
    com.cripta.app.data.PeopleSort.ADDED -> str(R.string.sort_created)
}

/** The two directions of [k], in words (descending first). */
private fun sortDirections(k: com.cripta.app.data.PeopleSort) = when (k) {
    com.cripta.app.data.PeopleSort.NAME -> str(R.string.z_to_a) to str(R.string.a_to_z)
    com.cripta.app.data.PeopleSort.MEDIA -> str(R.string.most_media) to str(R.string.fewest_media)
    com.cripta.app.data.PeopleSort.LATEST, com.cripta.app.data.PeopleSort.ADDED -> str(R.string.order_newest) to str(R.string.order_oldest)
    com.cripta.app.data.PeopleSort.RELEVANCE -> str(R.string.main_first) to str(R.string.others_first)
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
        if (snackbar.showSnackbar(u.first, str(R.string.undo), duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoFaceChange()
        else if (vm.faceUndo.value === u) vm.faceUndo.value = null
    }
    val topLift = remember { com.cripta.app.ui.components.TopBarLift() }
    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                val chosen = overview?.people.orEmpty().filter { it.id in selected }
                TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                    title = { Text(plural(R.plurals.n_selected_f_pl, selected.size)) },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(CriptaIcons.Close, str(R.string.cancel_selection)) } },
                    actions = {
                        val allFav = chosen.isNotEmpty() && chosen.all { it.entity.favorite }
                        IconButton(onClick = { chosen.forEach { vm.setPersonFavorite(it.id, !allFav) }; selected = emptySet() }) {
                            Icon(if (allFav) CriptaIcons.Star else CriptaIcons.StarBorder, if (allFav) str(R.string.unfavorite_short) else str(R.string.favorite_add))
                        }
                        if (selected.size >= 2) TextButton(onClick = { askMerge = true }) { com.cripta.app.ui.components.ButtonText(str(R.string.merge)) }
                        IconButton(onClick = { chosen.forEach { vm.ignorePerson(it.id) }; selected = emptySet() }) {
                            Icon(CriptaIcons.VisibilityOff, str(R.string.hide))
                        }
                    })
            } else
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(topLift.lifted), title = { Text(str(R.string.people)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(CriptaIcons.Sort, str(R.string.sort)) }
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
                        IconButton(onClick = { menu = true }) { Icon(CriptaIcons.MoreVert, str(R.string.more)) }
                        DropdownMenu(menu && vaultUnlocked(), { menu = false }) {
                            DropdownMenuItem(text = { Text(str(R.string.diagnostics)) }, onClick = { menu = false; showStats = true })
                            DropdownMenuItem(text = { Text(str(R.string.analyse_missing)) }, enabled = progress == null, onClick = { menu = false; vm.startFaceScan() })
                        }
                    }
                })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val o = overview
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text(str(R.string.reading)) }
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
        com.cripta.app.ui.components.TrackTopBarLift(topLift, gridState)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        /** The person under a finger held for 60 ms: pressed, as a cell in Cartelle. */
        var pressedId by remember { mutableStateOf<Long?>(null) }
        val order = shown.map { it.id }
        val currentOrder by androidx.compose.runtime.rememberUpdatedState(order)
        val currentSelected by androidx.compose.runtime.rememberUpdatedState(selected)
        fun personAt(pos: androidx.compose.ui.geometry.Offset): Long? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { i ->
            pos.x >= i.offset.x && pos.x <= i.offset.x + i.size.width && pos.y >= i.offset.y && pos.y <= i.offset.y + i.size.height
        }?.key as? Long
        // DS Persone: three faces a row on a phone (more on a wide screen).
        val peopleCols = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) GridCells.Fixed(3) else GridCells.Adaptive(104.dp)
        LazyVerticalGrid(peopleCols, Modifier.fillMaxSize().padding(pad)
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
            // DS cr-people: 24dp between rows, 8 between columns.
            state = gridState, contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (progress != null || o.scans.size < o.media) item(span = { GridItemSpan(maxLineSpan) }) {
                ScanStatus(o, progress, onStop = { vm.cancelFaceScan() })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                com.cripta.app.ui.components.CriptaTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(CriptaIcons.Search, null) }, placeholder = { Text(str(R.string.search_by_name)) }, modifier = Modifier.fillMaxWidth())
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeopleFilter.entries.forEach { f ->
                        val n = when (f) { PeopleFilter.NONE -> noFaces.size; PeopleFilter.TODO -> o.unassigned; else -> o.people.count { f.matches(it) } }
                        val todo = f == PeopleFilter.TODO
                        val label = if (todo && known) str(R.string.to_confirm) else f.label
                        val count = if (todo && known) knownList?.size ?: 0 else n
                        // DS Persone: chips with their count; "Preferite" leads with the star.
                        if (count > 0 || f == PeopleFilter.MAIN || f == filter) com.cripta.app.ui.components.ChoiceChip(selected = filter == f, onClick = { filter = f },
                            label = { Text("$label · $count") },
                            leadingIcon = if (f == PeopleFilter.FAVORITE) ({ Icon(CriptaIcons.StarBorder, null, Modifier.size(18.dp)) }) else null)
                    }
                }
            }
            if (filter == PeopleFilter.NONE) {
                // All worth a deeper look, unreadable ones too (the deep search reads frames another way).
                val again = noFaces.map { it.fileId }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str(R.string.no_usable_face_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Already searched the usual way: the deeper search (more frames, smaller and
                        // less sure faces) is the one worth running here.
                        if (again.isNotEmpty()) {
                            FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { vm.analyseAgain(again, deep = true) }, enabled = progress == null) {
                                com.cripta.app.ui.components.ButtonText(plural(R.plurals.search_deeper_n, again.size))
                            }
                            Text(str(R.string.search_deeper_desc),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(noFaces, key = { it.fileId }) { scan ->
                    val file = o.files[scan.fileId]
                    // Tap: the media itself (swiping goes through this list), to add the faces by hand.
                    Column(Modifier.clip(MaterialTheme.shapes.medium).combinedClickable(onClickLabel = str(R.string.open), onLongClickLabel = str(R.string.why),
                        onLongClick = { vm.describeMedia(scan.fileId) }) {
                        vm.openPersonMedia(noFaces.map { it.fileId }, scan.fileId, null); onOpenFile(scan.fileId)
                    }) {
                        if (file != null) MediaThumb(file, { vm.thumbOf(it) }, Modifier.fillMaxWidth().aspectRatio(1f), showFavoriteBadge = false)
                        Text(file?.originalName ?: str(R.string.file_not_found), style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            scan.status == FaceRepository.CLEARED -> str(R.string.faces_deleted_by_you)
                            scan.status.startsWith(FaceRepository.STALLED) -> str(R.string.analysis_stuck_retry)
                            scan.status != "ok" -> str(R.string.not_read, com.cripta.app.data.SkipReasons.text(scan.status))
                            scan.faces == 0 -> str(R.string.no_face)
                            else -> str(R.string.faces_rejected)
                        },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            } else if (filter == PeopleFilter.TODO && known) {
                val list = knownList?.filter { it.suggestion.card.id !in answered }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(when {
                        list == null -> str(R.string.comparing_known)
                        list.isEmpty() -> str(R.string.nothing_to_confirm_hint)
                        else -> str(R.string.similar_known_hint)
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
                            g == null -> str(R.string.grouping_faces)
                            g.isEmpty() -> str(R.string.no_faces_to_sort)
                            else -> listOfNotNull(
                                many.takeIf { it > 0 }?.let { plural(R.plurals.similar_groups_n, it) },
                                single.takeIf { it > 0 }?.let { plural(R.plurals.seen_once_n, it) },
                            ).joinToString(", ") + str(R.string.sort_one_at_a_time)
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!g.isNullOrEmpty()) FilledTonalButton(onClick = { sorting = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            com.cripta.app.ui.components.ButtonText(str(R.string.sort_faces))
                        }
                    }
                }
            } else {
                if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    val withFaces = o.scans.filter { it.status == "ok" && it.faces > 0 }
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            o.scans.isEmpty() -> Text(str(R.string.no_media_analysed),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            filter == PeopleFilter.MAIN && o.people.none { it.main } -> {
                                Text(if (withFaces.isEmpty()) str(R.string.no_person_yet) else str(R.string.faces_no_person, withFaces.size),
                                    style = MaterialTheme.typography.titleMedium)
                                Text(str(R.string.create_people_hint),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                withFaces.firstOrNull()?.let { first ->
                                    FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { onOpenFile(first.fileId) }) { com.cripta.app.ui.components.ButtonText(str(R.string.open_media_with_faces)) }
                                }
                            }
                            else -> Text(str(R.string.no_person_filter),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(shown, key = { it.id }) { p ->
                    val on = p.id in selected
                    Column(Modifier.animateItem().pressScale(pressedId == p.id).semantics(mergeDescendants = true) {
                        this.selected = on
                        onClick(label = if (selected.isEmpty()) str(R.string.open_x, p.title) else if (on) str(R.string.deselect) else str(R.string.select)) {
                            if (selected.isEmpty()) vm.openPerson(p.id) else selected = if (on) selected - p.id else selected + p.id
                            true
                        }
                        onLongClick(label = str(R.string.select)) { selected = selected + p.id; true }
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            FaceAvatar(p.cover, { vm.faceCrop(it) }, 88.dp, selected = on)
                            if (!p.entity.ignored && selected.isEmpty()) FavoriteStar(p.entity.favorite, p.title, Modifier.align(Alignment.TopEnd)) {
                                vm.setPersonFavorite(p.id, !p.entity.favorite)
                            }
                        }
                        Text(p.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                            color = if (on) MaterialTheme.colorScheme.primary
                                else if (p.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text(str(R.string.media_n, com.cripta.app.ui.components.formatCount(p.media.size)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
                title = { Text(str(R.string.merge_people_q, chosen.size)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str(R.string.merge_people_text),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.heightIn(max = 320.dp).selectableGroup()) {
                            items(chosen, key = { it.id }) { q ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
                                    .selectable(selected = q.id == keepId, role = androidx.compose.ui.semantics.Role.RadioButton) { keepId = q.id },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    androidx.compose.material3.RadioButton(selected = q.id == keepId, onClick = null)
                                    FaceAvatar(q.cover, { vm.faceCrop(it) }, 40.dp)
                                    Text(str(R.string.person_media_line, q.title, q.media.size), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(enabled = into != null, onClick = {
                    into?.let { vm.mergePeople(it.id, chosen.map { c -> c.id } - it.id) }; askMerge = false; selected = emptySet()
                }) { com.cripta.app.ui.components.ButtonText(str(R.string.merge)) } },
                dismissButton = { TextButton(onClick = { askMerge = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } })
        }
    }
    message?.let { m ->
        CriptaAlertDialog(onDismissRequest = { vm.faceMessage.value = null }, title = { Text(str(R.string.why)) },
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
                        Text(str(R.string.to_sort))
                        if (pages.isNotEmpty()) Text(str(R.string.x_of_y, (pager.currentPage + 1).coerceAtMost(pages.size), pages.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = close) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } })
        }, bottomBar = {
            // The snackbar of the page is under this screen: "Annulla" lives here instead.
            androidx.compose.animation.AnimatedVisibility(undo != null,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(undo?.first.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.undoFaceChange() }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) }
                }
            }
        }) { pad ->
            if (pages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(str(R.string.no_faces_to_sort), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Box(m.clip(MaterialTheme.shapes.large).clickable(onClickLabel = str(R.string.play_from_here)) { onOpen(focus) }, contentAlignment = Alignment.Center) {
            if (frame == null) FaceAvatar(focus, { vm.faceCrop(it) }, 180.dp)
            else com.cripta.app.ui.people.FrameWithFace(frame, focus.box, Modifier.fillMaxSize())
        }
    }
    val controls: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(if (many) str(R.string.similar_faces_in, g.cards.size, g.cards.map { it.fileId }.distinct().size) else str(R.string.face_seen_once),
                    style = MaterialTheme.typography.titleMedium)
                Text(when {
                    !many -> str(R.string.whose_tap_frame)
                    off.isEmpty() -> str(R.string.tap_other_person_faces)
                    else -> str(R.string.split_explain)
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            if (many) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(g.cards, key = { it.id }) { c ->
                    val on = c.id !in off
                    FaceAvatar(c, { vm.faceCrop(it) }, 64.dp,
                        Modifier.clip(CircleShape).combinedClickable(onClickLabel = if (on) str(R.string.remove_short) else str(R.string.put_back), onLongClickLabel = str(R.string.show_frame),
                            onLongClick = { focus = c }) { off = if (on) off + c.id else off - c.id; focus = c },
                        selected = on)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.cripta.app.ui.components.Button(enabled = picked.isNotEmpty(), onClick = { onName(picked) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.who_is_it)) }
                if (many) FilledTonalButton(enabled = off.isNotEmpty() && picked.isNotEmpty(), onClick = { vm.keepFacesApart(picked, off.toList()) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.split)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.cripta.app.ui.components.DestructiveButton(str(R.string.delete_the_faces), enabled = picked.isNotEmpty(),
                    onClick = { vm.deleteFaces(picked, everywhere = false) }, modifier = Modifier.weight(1f))
                com.cripta.app.ui.components.DestructiveButton(str(R.string.not_a_face), enabled = picked.isNotEmpty(),
                    onClick = { vm.deleteFaces(picked, everywhere = true) }, modifier = Modifier.weight(1f))
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
    CriptaAlertDialog(onDismissRequest = onDismiss, title = { Text(if (ids.size == 1) str(R.string.who_is_it) else str(R.string.who_is_it_n, ids.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.cripta.app.ui.components.CriptaTextField(query, { query = it }, singleLine = true, label = { Text(str(R.string.name_search_create)) },
                    modifier = Modifier.fillMaxWidth())
                val list = o.people.filter { !it.entity.ignored && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
                    .sortedWith(compareBy({ !it.entity.favorite }, { -it.entity.createdAt }))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    if (query.isBlank() || o.people.none { it.entity.name.equals(query.trim(), ignoreCase = true) }) item {
                        Text(if (query.isBlank()) str(R.string.new_unnamed_person) else str(R.string.new_person_plus, query.trim()), color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable { vm.assignFaces(ids, null, query); onDismiss() }.padding(vertical = 12.dp))
                    }
                    items(list, key = { it.id }) { q ->
                        Row(Modifier.fillMaxWidth().clickable { vm.assignFaces(ids, q.id, ""); onDismiss() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FaceAvatar(q.cover, { vm.faceCrop(it) }, 48.dp)
                            Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                color = if (q.entity.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            if (q.entity.favorite) Icon(CriptaIcons.Star, str(R.string.favorite_f), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } })
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
                    label = { Text(str(R.string.new_person_split)) }, modifier = Modifier.fillMaxWidth())
                com.cripta.app.ui.components.CriptaTextField(query, { query = it }, singleLine = true,
                    placeholder = { Text(str(R.string.search_all_people)) }, modifier = Modifier.fillMaxWidth())
                if (others.isEmpty()) Text(str(R.string.no_person_found), style = MaterialTheme.typography.bodySmall,
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
                            if (q.entity.favorite) Icon(CriptaIcons.Star, str(R.string.favorite_f), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty() || (allowNew && newName.isNotBlank()), onClick = { onDone(picked, newName) }) {
                com.cripta.app.ui.components.ButtonText(if (allowNew && picked.isEmpty()) str(R.string.split) else if (multi) str(R.string.merge) else str(R.string.move))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } })
}

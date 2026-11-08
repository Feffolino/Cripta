package com.cripta.app.ui.settings

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip

import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.CriptaDialog
import androidx.compose.material3.DropdownMenu
import com.cripta.app.ui.components.vaultUnlocked
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import com.cripta.app.media.intro.SegmentKind
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.cripta.app.data.Settings
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.intro.IntroRepository
import com.cripta.app.ui.components.ChipFlowRow
import com.cripta.app.ui.components.MediaThumb
import kotlinx.coroutines.delay

/**
 * Settings › Persone e intro › "Intro e finale": a live status card, then three groups (how skipping behaves,
 * the analysis, maintenance at the bottom), and the full-page results with covers.
 */
@Composable
internal fun IntroSettings(s: Settings, vm: SettingsViewModel, onOpenFile: (String) -> Unit) {
    val overview by vm.introOverview.collectAsStateWithLifecycle()
    val progress by vm.introProgress.collectAsStateWithLifecycle()
    val resultsOpen by vm.introResultsOpen.collectAsStateWithLifecycle()
    // Live while the section is on screen: faster while a scan runs.
    LaunchedEffect(progress != null) {
        while (true) { vm.refreshIntro(); delay(if (progress != null) 3_000 else 15_000) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        PageIntro(str(R.string.intro_page_intro)) {
            StatusCard(overview, progress, onStop = { vm.cancelIntroScan() }, onResults = { vm.introResultsOpen.value = true })
        }
        Section(str(R.string.skipping)) {
            ToggleRow(str(R.string.skip_intro_auto), s.introAutoSkip,
                desc = if (s.introAutoSkip) str(R.string.skip_intro_auto_on) else str(R.string.skip_intro_auto_off),
                icon = CriptaIcons.SkipNext) { vm.setIntroAutoSkip(it) }
            ToggleRow(str(R.string.skip_ending_auto), s.outroAutoSkip,
                desc = str(R.string.skip_ending_auto_desc),
                icon = CriptaIcons.SkipNext) { vm.setOutroAutoSkip(it) }
        }
        Section(str(R.string.kind_analysis)) {
            ToggleRow(str(R.string.analyse_new_videos), s.introAnalyzeNew,
                desc = str(R.string.analyse_new_videos_desc),
                icon = CriptaIcons.GraphicEq) { vm.setIntroAnalyzeNew(it) }
            ToggleRow(str(R.string.quick_analysis), s.introQuickScan,
                desc = if (s.introQuickScan) str(R.string.quick_analysis_on)
                    else str(R.string.quick_analysis_off),
                icon = CriptaIcons.Speed) { vm.setIntroQuickScan(it) }
            FilledTonalButton(onClick = { vm.startIntroScan() }, enabled = progress == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                com.cripta.app.ui.components.ButtonText(if (progress == null) str(R.string.analyse_existing) else str(R.string.analysis_running))
            }
        }
        Maintenance(vm, onOpenFile)
    }
    if (resultsOpen) overview?.let { o ->
        val groups by vm.introGroups.collectAsStateWithLifecycle()
        val introMessage by vm.introMessage.collectAsStateWithLifecycle()
        val actions = remember(vm) {
            SignatureActions(
                rename = vm::renameSignature, merge = vm::mergeSignatures, delete = vm::deleteSignatures,
                duration = vm::setSignatureDuration, removeVideo = vm::removeFromSignature,
            )
        }
        IntroResultsPage(o, thumb = { vm.thumbOf(it) }, groups = groups, onLoadGroups = { vm.loadIntroGroups(it) },
            actions = actions, message = introMessage, onMessageShown = { vm.introMessage.value = null },
            onClose = { vm.introResultsOpen.value = false },
            onOpen = { ids, id -> vm.openFromResults(ids); onOpenFile(id) })
    }
}

/** How far the analysis is, what it found, and a way into the results. */
@Composable
private fun StatusCard(o: IntroRepository.Overview?, progress: com.cripta.app.work.ConversionService.Companion.IntroProgress?,
                       onStop: () -> Unit, onResults: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (o == null) {
            Text(str(R.string.reading_state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        // One line per figure: any number fits ("Analizzati 1.234 di 5.678"), nothing is cut.
        val fmt: (Number) -> String = { com.cripta.app.ui.components.formatCount(it) }
        if (progress != null) {
            com.cripta.app.ui.components.CountRow(progress.phase, fmt(progress.done), of = fmt(progress.total),
                progress = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f)
        }
        com.cripta.app.ui.components.CountRow(str(R.string.analysed), fmt(o.analysed), of = fmt(o.videos),
            progress = if (progress == null && o.videos > 0) o.analysed.toFloat() / o.videos else null)
        com.cripta.app.ui.components.CountRow(str(R.string.with_intro), fmt(o.withIntro))
        com.cripta.app.ui.components.CountRow(str(R.string.with_ending), fmt(o.withOutro))
        if (progress == null && o.skipped > 0) Text(str(R.string.n_not_analysable, fmt(o.skipped)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.cripta.app.ui.components.FilledTonalButton(onClick = onResults, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.results)) }
            if (progress != null) com.cripta.app.ui.components.FilledTonalButton(onClick = onStop, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.stop)) }
        }
    }
}

/** Rejected samples and the restart, apart at the bottom: rare, and the restart throws work away. */
@Composable
private fun Maintenance(vm: SettingsViewModel, onOpenFile: (String) -> Unit) {
    val rejected by vm.rejectedSegments.collectAsStateWithLifecycle()
    var showRejected by remember { mutableStateOf(false) }
    var askRestart by remember { mutableStateOf(false) }
    var restartAll by remember { mutableStateOf(false) }
    // Like the people page: the rare actions as text, the one that throws work away a destructive button.
    Section(str(R.string.maintenance)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rejected.isNotEmpty()) TextButton(onClick = { showRejected = true }) {
                com.cripta.app.ui.components.ButtonText(str(R.string.discarded_segments_n, rejected.size))
            }
            Spacer(Modifier.weight(1f))
            com.cripta.app.ui.components.DestructiveButton(str(R.string.start_over), onClick = { restartAll = false; askRestart = true })
        }
    }
    if (showRejected) CriptaAlertDialog(
        onDismissRequest = { showRejected = false },
        confirmButton = { TextButton(onClick = { showRejected = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } },
        title = { Text(str(R.string.discarded_segments)) },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(str(R.string.discarded_segments_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(rejected, key = { it.id }) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).minimumInteractiveComponentSize().clickable { showRejected = false; onOpenFile(r.originFileId) }.padding(vertical = 4.dp)) {
                            Text(r.originName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (r.kind == "intro") str(R.string.intro) else str(R.string.ending)) +
                                str(R.string.discarded_line, span(r.startMs, r.endMs), (r.endMs - r.startMs + 500) / 1000),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.restoreRejected(r.id) }) { com.cripta.app.ui.components.ButtonText(str(R.string.remove_short)) }
                    }
                }
            }
        },
    )
    if (askRestart) CriptaAlertDialog(
        onDismissRequest = { askRestart = false },
        title = { Text(str(R.string.start_over_q)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(str(R.string.start_over_text))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restartAll = !restartAll }) {
                    Checkbox(checked = restartAll, onCheckedChange = { restartAll = it })
                    Text(str(R.string.start_over_clear_marks))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.restartIntroScan(restartAll); askRestart = false }) {
                com.cripta.app.ui.components.ButtonText(str(R.string.start_over_btn), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { askRestart = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

private enum class ResultFilter(private val labelRes: Int, val kind: SegmentKind? = null) {
    WITH(R.string.filter_with_intro), WITHOUT(R.string.filter_without), SKIPPED(R.string.filter_skipped),
    // The signatures: videos grouped by the sound of their intro / outro.
    BY_INTRO(R.string.filter_sig_intro, SegmentKind.INTRO), BY_OUTRO(R.string.filter_sig_ending, SegmentKind.OUTRO),
    ;

    val label: String get() = str(labelRes)
}

/**
 * Every analysed video with its cover and times, filtered and searchable; a tap opens it in the
 * viewer (swiping goes through the same list) and Back comes back here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IntroResultsPage(
    o: IntroRepository.Overview,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    groups: Pair<SegmentKind, List<IntroRepository.Signature>>?,
    onLoadGroups: (SegmentKind) -> Unit,
    actions: SignatureActions,
    message: String?,
    onMessageShown: () -> Unit,
    onClose: () -> Unit,
    onOpen: (List<String>, String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(ResultFilter.WITH) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var renaming by remember { mutableStateOf<IntroRepository.Signature?>(null) }
    var timing by remember { mutableStateOf<IntroRepository.Signature?>(null) }
    var deleting by remember { mutableStateOf<List<IntroRepository.Signature>?>(null) }
    var merging by remember { mutableStateOf<List<IntroRepository.Signature>?>(null) }
    LaunchedEffect(filter) { filter.kind?.let(onLoadGroups); expanded = emptySet(); selected = emptySet() }
    // After a change the list is reloaded: its positions (unsaved keys) may have moved.
    LaunchedEffect(groups) { selected = emptySet() }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    val selectedSigs = groups?.second?.withIndex()?.filter { sigKey(it.index, it.value) in selected }?.map { it.value }.orEmpty()
    fun matches(name: String) = query.isBlank() || name.contains(query.trim(), ignoreCase = true)
    val shown = remember(o, filter, query) {
        o.entries.filter { e ->
            when (filter) {
                ResultFilter.WITH -> e.intro != null || e.outro != null
                ResultFilter.WITHOUT -> e.skipReason == null && e.intro == null && e.outro == null
                ResultFilter.SKIPPED -> e.skipReason != null
                else -> false
            } && matches(e.file.originalName)
        }
    }
    // Groups of the chosen kind, those holding a matching name when searching.
    val shownGroups = remember(groups, filter, query) {
        groups?.takeIf { it.first == filter.kind }?.second?.withIndex()?.filter { g -> g.value.entries.any { matches(it.first.originalName) } }
    }
    val counts = remember(o, groups) {
        mapOf(ResultFilter.WITH to o.entries.count { it.intro != null || it.outro != null },
            ResultFilter.WITHOUT to o.entries.count { it.skipReason == null && it.intro == null && it.outro == null },
            ResultFilter.SKIPPED to o.skipped,
            ResultFilter.BY_INTRO to groups?.takeIf { it.first == SegmentKind.INTRO }?.second?.size,
            ResultFilter.BY_OUTRO to groups?.takeIf { it.first == SegmentKind.OUTRO }?.second?.size)
    }
    CriptaDialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                if (selected.isNotEmpty()) TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                    colors = com.cripta.app.ui.components.criptaTopBarColors(),
                    title = { Text(str(R.string.n_selected_f, selected.size)) },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(CriptaIcons.Close, str(R.string.cancel_selection)) } },
                    actions = {
                        TextButton(onClick = { merging = selectedSigs }, enabled = selectedSigs.size >= 2) { com.cripta.app.ui.components.ButtonText(str(R.string.merge)) }
                        com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { deleting = selectedSigs },
                            modifier = Modifier.padding(end = 8.dp))
                    },
                ) else TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                    colors = com.cripta.app.ui.components.criptaTopBarColors(),
                    title = { Text(str(R.string.analysis_results)) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } },
                )
            },
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    com.cripta.app.ui.components.CriptaTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        leadingIcon = { Icon(CriptaIcons.Search, null) }, placeholder = { Text(str(R.string.search_by_name)) },
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    ChipFlowRow {
                        ResultFilter.entries.forEach { f ->
                            com.cripta.app.ui.components.ChoiceChip(selected = filter == f, onClick = { filter = f },
                                label = { Text(f.label + (counts[f]?.let { " · $it" } ?: "")) })
                        }
                    }
                }
                if (filter.kind != null) {
                    item {
                        Text(str(if (filter.kind == SegmentKind.INTRO) R.string.signatures_hint_intro else R.string.signatures_hint_ending),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        shownGroups == null -> item {
                            Text(str(R.string.comparing_sounds), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                        }
                        shownGroups.isEmpty() -> item {
                            Text(str(R.string.no_signatures), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                        }
                    }
                    shownGroups?.forEach { (i, g) ->
                        val key = sigKey(i, g)
                        item(key = key) {
                            SignatureCard(i + 1, g, thumb, open = i in expanded, selecting = selected.isNotEmpty(), selected = key in selected,
                                onToggleOpen = { expanded = if (i in expanded) expanded - i else expanded + i },
                                onToggleSelect = { selected = if (key in selected) selected - key else selected + key },
                                onRename = { renaming = g }, onMerge = { selected = setOf(key) }, onDuration = { timing = g },
                                onDelete = { deleting = listOf(g) })
                        }
                        if (i in expanded && selected.isEmpty()) items(g.entries, key = { "$key-${it.first.id}" }) { (file, seg) ->
                            val e = IntroRepository.Entry(file, seg.takeIf { filter.kind == SegmentKind.INTRO },
                                seg.takeIf { filter.kind == SegmentKind.OUTRO }, null)
                            EntryRow(e, thumb, Modifier.padding(start = 12.dp), trailing = {
                                IconButton(onClick = { actions.removeVideo(g, file.id) }) {
                                    Icon(CriptaIcons.RemoveCircleOutline, str(R.string.remove_from_signature, file.originalName))
                                }
                            }) { onOpen(g.entries.map { it.first.id }, file.id) }
                        }
                    }
                    return@LazyColumn
                }
                if (shown.isEmpty()) item {
                    Text(
                        when (filter) {
                            ResultFilter.WITH -> str(R.string.results_none_with)
                            ResultFilter.WITHOUT -> str(R.string.results_none_without)
                            ResultFilter.SKIPPED -> str(R.string.results_none_skipped)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                items(shown, key = { it.file.id }) { e ->
                    EntryRow(e, thumb) { onOpen(shown.map { it.file.id }, e.file.id) }
                }
            }
        }
        renaming?.let { s ->
            NameDialog(str(R.string.rename_signature), s.name.orEmpty(), confirm = str(R.string.rename), onDismiss = { renaming = null }) {
                actions.rename(s, it); renaming = null
            }
        }
        merging?.let { sigs ->
            NameDialog(str(R.string.merge_signatures_q, sigs.size), sigs.firstNotNullOfOrNull { it.name }.orEmpty(), confirm = str(R.string.merge),
                hint = str(R.string.merge_signatures_hint, sigs.sumOf { it.entries.size }),
                allowBlank = true, onDismiss = { merging = null }) {
                actions.merge(sigs, it.ifBlank { null }); merging = null; selected = emptySet()
            }
        }
        deleting?.let { sigs ->
            CriptaAlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(plural(R.plurals.not_a_theme_q, sigs.size)) },
                text = {
                    Text(str(R.string.not_a_theme_text, sigs.sumOf { it.entries.size }))
                },
                confirmButton = {
                    TextButton(onClick = { actions.delete(sigs); deleting = null; selected = emptySet() }) {
                        com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
        timing?.let { s -> DurationDialog(s, onDismiss = { timing = null }) { ds, de -> actions.duration(s, ds, de); timing = null } }
    }
}

/** What the signatures page can do (the settings view model behind it). */
internal class SignatureActions(
    val rename: (IntroRepository.Signature, String) -> Unit,
    val merge: (List<IntroRepository.Signature>, String?) -> Unit,
    val delete: (List<IntroRepository.Signature>) -> Unit,
    val duration: (IntroRepository.Signature, Long, Long) -> Unit,
    val removeVideo: (IntroRepository.Signature, String) -> Unit,
)

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, hint: String? = null, allowBlank: Boolean = false,
                       onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                com.cripta.app.ui.components.CriptaTextField(value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text(str(R.string.signature_name_hint)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = allowBlank || text.isNotBlank()) { com.cripta.app.ui.components.ButtonText(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

/**
 * "Durata della sigla": how far before / after its sound every video's segment starts and ends (an
 * intro that goes on past its distinctive sound); applied to the signature's videos, now and later.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationDialog(s: IntroRepository.Signature, onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    var start by remember { mutableLongStateOf(s.startDeltaMs) }
    var end by remember { mutableLongStateOf(s.endDeltaMs) }
    @Composable
    fun Stepper(label: String, value: Long, onChange: (Long) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$label: ${if (value == 0L) str(R.string.same_as_sound) else deltaText(value)}", style = MaterialTheme.typography.bodyMedium)
            ChipFlowRow(gap = 4.dp) {
                val half = String.format(com.cripta.app.Strings.locale, "%.1f", 0.5)
                for ((d, t) in listOf(-5_000L to "−5 s", -1_000L to "−1 s", -500L to "−$half", 500L to "+$half", 1_000L to "+1 s", 5_000L to "+5 s")) {
                    TextButton(onClick = { onChange(value + d) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { com.cripta.app.ui.components.ButtonText(t) }
                }
            }
        }
    }
    CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.theme_length)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(str(R.string.theme_length_text, String.format(com.cripta.app.Strings.locale, "%.1f s", s.soundMs / 1000.0), s.entries.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stepper(str(R.string.start), start) { start = it }
                Stepper(str(R.string.end), end) { end = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(start, end) }) { com.cripta.app.ui.components.ButtonText(str(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

/** One video in the results: cover, name, its intro / outro times or why it could not be analysed. */
@Composable
private fun EntryRow(e: IntroRepository.Entry, thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
                     modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaThumb(e.file, thumb, Modifier.size(width = 112.dp, height = 63.dp), showFavoriteBadge = false,
            shape = com.cripta.app.ui.theme.CoverShapeSmall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.file.originalName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                e.skipReason != null -> Text(e.skipReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                e.intro == null && e.outro == null -> Text(str(R.string.intro_none_nor_ending), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    e.intro?.let { SegLine(str(R.string.intro), it, MaterialTheme.colorScheme.primary) }
                    e.outro?.let { SegLine(str(R.string.ending), it, MaterialTheme.colorScheme.tertiary) }
                }
            }
        }
        trailing?.invoke()
    }
}

private fun sigKey(i: Int, g: IntroRepository.Signature) = g.id?.let { "s$it" } ?: "u$i"

private fun deltaText(ms: Long) = (if (ms > 0) "+" else if (ms < 0) "−" else "") + String.format(com.cripta.app.Strings.locale, "%.1f s", kotlin.math.abs(ms) / 1000.0)

/**
 * A signature: its name (or number), how many videos, the sound's length and its duration, a few
 * covers. Tap: list its videos (or select, while selecting); long press: start selecting; ⋮: actions.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SignatureCard(
    n: Int, g: IntroRepository.Signature, thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    open: Boolean, selecting: Boolean, selected: Boolean,
    onToggleOpen: () -> Unit, onToggleSelect: () -> Unit,
    onRename: () -> Unit, onMerge: () -> Unit, onDuration: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // A plain entry, no frame: only a selected one gets a tonal slab (with its checkbox).
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onToggleOpen() },
                onLongClick = { if (!selecting) onToggleSelect() },
                onLongClickLabel = str(R.string.select),
            ),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.name ?: str(R.string.unnamed_signature, n), style = MaterialTheme.typography.titleSmall,
                        color = if (g.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text(str(R.string.signature_line, g.entries.size, String.format(com.cripta.app.Strings.locale, "%.1f s", g.soundMs / 1000.0)) +
                        (if (g.startDeltaMs != 0L || g.endDeltaMs != 0L) str(R.string.signature_deltas, deltaText(g.startDeltaMs), deltaText(g.endDeltaMs)) else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                } else {
                    Icon(if (open) CriptaIcons.ExpandLess else CriptaIcons.ExpandMore, null)
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) { Icon(CriptaIcons.MoreVert, str(R.string.signature_actions)) }
                        DropdownMenu(expanded = menu && vaultUnlocked(), onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text(str(R.string.rename)) }, onClick = { menu = false; onRename() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text(str(R.string.merge_with_others)) }, onClick = { menu = false; onMerge() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text(str(R.string.theme_length)) }, onClick = { menu = false; onDuration() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text(str(R.string.not_a_theme), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                g.entries.take(4).forEach { (file, _) ->
                    MediaThumb(file, thumb, Modifier.size(width = 72.dp, height = 40.dp), showFavoriteBadge = false,
                        shape = com.cripta.app.ui.theme.CoverShapeSmall)
                }
                if (g.entries.size > 4) Text("+${g.entries.size - 4}", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun SegLine(label: String, seg: MediaSegmentEntity, color: androidx.compose.ui.graphics.Color) {
    if (seg.source == "none") return
    Text("$label ${span(seg.startMs, seg.endMs)}" + if (seg.source == "auto") "" else str(R.string.marked_by_you_f),
        style = MaterialTheme.typography.bodySmall, color = color)
}

private fun span(startMs: Long, endMs: Long) =
    (com.cripta.app.ui.components.formatDuration(startMs) ?: "0:00") + "–" + (com.cripta.app.ui.components.formatDuration(endMs) ?: "0:00")

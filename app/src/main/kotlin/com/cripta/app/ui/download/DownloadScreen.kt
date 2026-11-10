package com.cripta.app.ui.download

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import kotlinx.coroutines.launch

import com.cripta.app.ui.theme.CriptaIcons
import com.cripta.app.ui.theme.accentTint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.cripta.app.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.DownloadJob
import com.cripta.app.data.DownloadPhase
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.components.TagPill
import com.cripta.app.ui.components.TagPillSize
import com.cripta.app.ui.components.TagPillState
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.components.tagText
import com.cripta.app.ui.theme.CoverShape
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.tagColor
import kotlinx.coroutines.delay

/**
 * In-app video downloader: paste (or share in) a link, see what it is, pick a quality — with a
 * size estimate per resolution — and where it goes (folder + tags, remembered for next time).
 * Links queue up and are fetched one at a time with yt-dlp, encrypted into the vault with their
 * source link stored.
 *
 * Layout: the link field leads; once analysed, the video's cover and title; then "Qualità" as one
 * segmented control and "Destinazione" as two plain rows that open a sheet; one wide button. The
 * queue below is grouped by heading (In corso / In attesa / Finiti), no frames around entries.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(
    initialUrl: String? = null,
    onOpenFile: (String) -> Unit = {},
    vm: DownloadViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Saved across a recreated Activity and a change of tab: a link and a choice not yet sent are kept.
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    val qualities = listOf<Pair<Int?, String>>(null to str(R.string.quality_auto), 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
    val heights = qualities.map { it.first }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val estimate by vm.estimate.collectAsStateWithLifecycle()
    val existing by vm.existing.collectAsStateWithLifecycle()
    val pending by vm.pendingSharedLink.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val defaults by vm.defaults.collectAsStateWithLifecycle()

    // Destination + quality start from the last used ones (loaded once), then follow the user.
    var quality by rememberSaveable { mutableStateOf<Int?>(null) }
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var tagIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var defaultsApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(defaults) {
        val d = defaults
        if (d != null && !defaultsApplied) {
            quality = d.height; folderId = d.folderId; tagIds = d.tagIds.toSet(); defaultsApplied = true
        }
    }
    // Drop remembered ids that no longer exist (deleted folder/tag).
    val folderValid = folderId == null || folders.any { it.id == folderId }
    val effectiveFolder = if (folderValid) folderId else null
    val effectiveTags = tagIds.filter { id -> tags.any { it.id == id } }
    var pickFolder by remember { mutableStateOf(false) }
    var pickTags by remember { mutableStateOf(false) }

    // A link shared from another app: pre-fill the field so the user can pick a resolution.
    LaunchedEffect(pending) {
        pending?.let { url = extractUrl(it); vm.consumeSharedLink() }
    }
    // Debounced size probe (and "already downloaded" check) whenever the link settles.
    LaunchedEffect(url) {
        val u = url.trim()
        vm.onLinkEdited(u)
        // Not a link (yet): nothing to probe, and no misleading "estimate failed".
        if (!looksLikeUrl(u)) { vm.resetEstimate() } else { delay(700); vm.estimate(u, heights) }
    }

    fun paste() {
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.isNotBlank()) url = extractUrl(clip)
        else Toast.makeText(ctx, str(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
    }

    val urlValid = looksLikeUrl(url)
    // Only complain once something has been typed that clearly isn't a link.
    val urlError = url.isNotBlank() && !urlValid
    fun submit() {
        val u = url.trim()
        if (!looksLikeUrl(u)) return
        if (vm.isQueued(u)) {
            Toast.makeText(ctx, str(R.string.dl_already_queued), Toast.LENGTH_SHORT).show()
        } else {
            vm.enqueue(ctx, u, quality, effectiveFolder, effectiveTags)
            url = ""; vm.resetEstimate()
        }
    }

    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Landscape: the top bar scrolls away (down hides, up reveals), like in Cartelle.
    val scrollBehavior = if (landscape) androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior()
        else androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()

    val form: @Composable ColumnScope.() -> Unit = {
        // The link: one filled field, paste inside it while empty, clear once something is in it.
        com.cripta.app.ui.components.CriptaTextField(
            value = url,
            onValueChange = { url = it },
            // DS cr-field: icon and link in one pill, no floating label.
            placeholder = { Text(str(R.string.dl_link_hint)) },
            leadingIcon = { Icon(CriptaIcons.Link, null) },
            trailingIcon = {
                if (url.isBlank()) {
                    // DS: the paste action as an accent icon at the field's end.
                    IconButton(onClick = { paste() }) {
                        Icon(CriptaIcons.ContentPaste, str(R.string.paste), tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { url = ""; vm.resetEstimate() }) { Icon(CriptaIcons.Clear, str(R.string.dl_clear_link)) }
                }
            },
            singleLine = true,
            isError = urlError,
            supportingText = if (urlError) ({ Text(str(R.string.dl_not_a_link)) }) else null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                imeAction = androidx.compose.ui.text.input.ImeAction.Go,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        // What the link is, once analysed: its cover, title and length, before downloading.
        val ready = (estimate as? DownloadViewModel.Estimate.Ready)?.takeIf { it.thumbnailUrl != null || it.title != null }
        var lastReady by remember { mutableStateOf(ready) }
        if (ready != null) lastReady = ready
        AnimatedVisibility(
            visible = ready != null,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            lastReady?.let { LinkPreview(it, url) }
        }
        if (url.isBlank()) {
            Text(str(R.string.dl_sites_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Already downloaded from this link? One notice, in a tonal slab of its own.
        existing?.let { f ->
            Surface(color = MaterialTheme.colorScheme.accentTint, shape = com.cripta.app.ui.theme.RadiusMd,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CriptaIcons.Info, null, tint = MaterialTheme.colorScheme.onSurface)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(str(R.string.dl_already_in_vault), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(f.originalName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { vm.prepareOpen(f.id); onOpenFile(f.id) }) {
                        com.cripta.app.ui.components.ButtonText(str(R.string.open), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Heading(str(R.string.quality), Modifier.padding(top = 8.dp))
        QualitySelector(qualities, quality, estimate) { quality = it }
        // Estimate line: takes no space while idle, crossfades between "computing" / result / error.
        val estimateText = when (val e = estimate) {
            is DownloadViewModel.Estimate.Error -> str(R.string.dl_size_unknown_err, e.message)
            DownloadViewModel.Estimate.Loading -> str(R.string.dl_size_computing)
            is DownloadViewModel.Estimate.Ready -> str(R.string.dl_size_hint)
            else -> null
        }
        var lastEstimateText by remember { mutableStateOf("") }
        if (estimateText != null) lastEstimateText = estimateText
        AnimatedVisibility(
            visible = estimateText != null,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Crossfade(lastEstimateText, animationSpec = Motion.enter(), label = "estimateLine") { t ->
                Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            }
        }

        // Destination (remembered): two plain rows, each opens its picker.
        Column {
            val f = folders.firstOrNull { it.id == effectiveFolder }
            DestinationRow(
                icon = { com.cripta.app.ui.components.FolderGlyph(f?.color, f?.icon, f?.emoji, 26.dp) },
                title = str(R.string.folder),
                onClick = { pickFolder = true },
                onClickLabel = str(R.string.change_folder),
            ) {
                Text(folderPath(f, folders), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tags.isNotEmpty()) {
                DestinationRow(
                    icon = { Icon(CriptaIcons.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = str(R.string.tags),
                    onClick = { pickTags = true },
                    onClickLabel = str(R.string.choose_tags),
                ) {
                    val chosen = tags.filter { it.id in effectiveTags }
                    if (chosen.isEmpty()) {
                        Text(str(R.string.none_f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            chosen.forEach { t -> TagPill(tagText(t), tagColor(t), base = MaterialTheme.colorScheme.background, tagIcon = t.icon) }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { submit() },
            enabled = urlValid,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp),
        ) {
            Icon(CriptaIcons.Download, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (downloads.any { it.active }) str(R.string.dl_add_to_queue) else if (existing != null) str(R.string.dl_download_anyway) else str(R.string.dl_download),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }

    val queue: @Composable ColumnScope.() -> Unit = {
        val running = downloads.filter { phaseGroup(it.phase) == PhaseGroup.RUNNING }
        val waiting = downloads.filter { phaseGroup(it.phase) == PhaseGroup.WAITING }
        val finished = downloads.filter { it.finished }
        fun dest(job: DownloadJob) =
            job.folderId?.let { id -> folders.firstOrNull { it.id == id } }?.let { folderLabel(it, folders) } ?: str(R.string.root)
        val jobs: @Composable (List<DownloadJob>) -> Unit = { list ->
            list.forEach { job ->
                androidx.compose.runtime.key(job.id) {
                    JobRow(
                        job,
                        destination = dest(job),
                        onCancel = { vm.cancel(ctx) },
                        onRemove = { vm.remove(job.id) },
                        onRetry = { vm.retry(ctx, job) },
                        onOpen = { id -> vm.prepareOpen(id); onOpenFile(id) },
                    )
                }
            }
        }
        if (downloads.isEmpty()) {
            // An empty queue says what will happen here, quietly.
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(CriptaIcons.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text(str(R.string.dl_queue_empty), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp))
                Text(str(R.string.dl_queue_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (running.isNotEmpty()) { Heading(str(R.string.in_progress_label)); jobs(running) }
        if (waiting.isNotEmpty()) { Heading(str(R.string.dl_waiting_n, waiting.size), Modifier.padding(top = 8.dp)); jobs(waiting) }
        if (finished.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Heading(str(R.string.dl_finished), Modifier.weight(1f))
                TextButton(onClick = { vm.clearFinished() }) { com.cripta.app.ui.components.ButtonText(str(R.string.clear)) }
            }
            jobs(finished)
        }
        Text(
            str(R.string.dl_first_slow),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    // The bar lifts from the page's real scroll position (portrait: one scroll; landscape: either side).
    val formScroll = rememberScrollState()
    val queueScroll = rememberScrollState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                title = { com.cripta.app.ui.components.HeaderTitle(str(R.string.n_download)) },
                scrollBehavior = scrollBehavior,
                colors = com.cripta.app.ui.components.criptaTopBarColors(formScroll.canScrollBackward || (landscape && queueScroll.canScrollBackward)),
            )
        },
    ) { pad ->
        if (landscape) {
            // Landscape: the form on the left, the queue on the right, each scrolling on its own.
            Row(
                Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().imePadding().verticalScroll(formScroll).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = form,
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(queueScroll).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = queue,
                )
            }
        } else {
            Column(
                // imePadding keeps the field and the "Scarica" button reachable above the keyboard.
                Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
                    .fillMaxWidth().wrapContentWidth().widthIn(max = 720.dp).fillMaxWidth()
                    .verticalScroll(formScroll).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                form()
                Spacer(Modifier.height(16.dp))
                queue()
            }
        }
    }

    if (pickFolder) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        FolderPickerSheet(folders, effectiveFolder, onPick = { folderId = it; pickFolder = false }, onDismiss = { pickFolder = false },
            onCreate = { name, parent -> scope.launch { folderId = vm.createFolder(name, parent); pickFolder = false } })
    }
    if (pickTags) {
        TagPickerSheet(tags, effectiveTags.toSet(),
            onToggle = { id -> tagIds = if (id in tagIds) tagIds - id else tagIds + id },
            onClear = { tagIds = emptySet() },
            onDismiss = { pickTags = false })
    }
}

/** Section heading of this screen: the app's accent `titleSmall`, marked as a heading. */
@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() })
}

/** A plain row (no frame): leading icon, title, what is chosen under it, chevron. */
@Composable
private fun DestinationRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
    onClickLabel: String,
    value: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.padding(top = 2.dp)) { value() }
        }
        Icon(CriptaIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Quality as a grid of roomy options, three per row (five side by side were too narrow): each
 * with its estimated size under the name. The chosen one is filled with the accent and carries a
 * tick (never colour alone).
 */
@Composable
private fun QualitySelector(
    qualities: List<Pair<Int?, String>>,
    selected: Int?,
    estimate: DownloadViewModel.Estimate,
    onSelect: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        qualities.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (h, label) ->
                    val on = selected == h
                    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier.weight(1f).heightIn(min = 60.dp).clip(com.cripta.app.ui.theme.RadiusMd)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
                            .clickable(role = Role.RadioButton, onClick = { onSelect(h) })
                            .semantics { this.selected = on }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (on) Icon(CriptaIcons.Check, null, tint = fg, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                            Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
                        }
                        SizeHint(estimate, h, if (on) fg else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Keep the last row's options the same width as the ones above.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Small size estimate under a quality option. */
@Composable
private fun SizeHint(estimate: DownloadViewModel.Estimate, height: Int?, color: Color) {
    // Spinner -> size crossfades in place. Nothing while there is no link.
    if (estimate !is DownloadViewModel.Estimate.Loading && estimate !is DownloadViewModel.Estimate.Ready) return
    AnimatedContent(
        targetState = estimate,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit())) using SizeTransform(clip = false) { _, _ -> Motion.enter() }
        },
        label = "sizeHint",
    ) { e ->
        when (e) {
            DownloadViewModel.Estimate.Loading ->
                CircularProgressIndicator(strokeWidth = 2.dp, color = color, modifier = Modifier.padding(top = 2.dp).size(12.dp))
            is DownloadViewModel.Estimate.Ready -> {
                val bytes = e.bytesByHeight[height]
                Text(
                    if (bytes != null) formatBytes(bytes) else "—",
                    // DS cr-option caption: 12sp medium, 2dp under the name.
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    maxLines = 1,
                    modifier = if (bytes == null) Modifier.semantics { contentDescription = str(R.string.dl_size_unknown) } else Modifier,
                )
            }
            else -> {}
        }
    }
}

/** "Parent › Child" label for a folder, so same-named folders are distinguishable. */
private fun folderLabel(f: FolderEntity, all: List<FolderEntity>): String {
    val byId = all.associateBy { it.id }
    val names = ArrayDeque<String>()
    var cur: FolderEntity? = f
    var guard = 0
    while (cur != null && guard++ < 20) { names.addFirst(cur.name); cur = cur.parentId?.let { byId[it] } }
    return names.joinToString(" › ")
}

/** The full path of a destination, from the root: "Radice › Serie › Stagione 1". */
private fun folderPath(f: FolderEntity?, all: List<FolderEntity>): String =
    if (f == null) str(R.string.root) else str(R.string.root) + " › " + folderLabel(f, all)

/**
 * Where a download is saved: the chosen path in full at the top, then one level at a time like a
 * file manager. "Salva qui" picks the level on screen (the root included, named as such), a row
 * picks that folder, its arrow opens it to go deeper, and a new folder can be made where you are.
 * The current choice carries the accent and a tick wherever it shows.
 */
@Composable
private fun FolderPickerSheet(
    folders: List<FolderEntity>,
    selected: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, Long?) -> Unit,
) {
    val byId = remember(folders) { folders.associateBy { it.id } }
    val children = remember(folders) { folders.groupBy { it.parentId } }
    // Opens on the level that holds the current choice, so it is in view with its neighbours.
    var at by remember { mutableStateOf(byId[selected]?.parentId?.takeIf { it in byId }) }
    var naming by remember { mutableStateOf(false) }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
        // Fixed height (the sheet clamps it to the screen): a level with fewer folders must not
        // shrink the sheet, which made it jump while the next level came in.
        Column(Modifier.fillMaxWidth().height(560.dp).padding(bottom = 20.dp)) {
            Text(str(R.string.save_in), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
            Text(
                str(R.string.dl_dest_now, folderPath(byId[selected], folders)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 8.dp),
            )
            // Where you are: each step of the path goes back there.
            val trail = remember(at, byId) {
                val out = ArrayDeque<FolderEntity>()
                var cur = at?.let { byId[it] }
                var guard = 0
                while (cur != null && guard++ < 50) { out.addFirst(cur); cur = cur.parentId?.let { byId[it] } }
                out.toList()
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (at != null) IconButton(onClick = { at = byId[at]?.parentId }) { Icon(CriptaIcons.ArrowBack, str(R.string.up)) }
                TextButton(onClick = { at = null }, enabled = at != null) { Text(str(R.string.root)) }
                trail.forEach { f ->
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { at = f.id }, enabled = f.id != at) { Text(f.name, maxLines = 1) }
                }
            }
            // The level on screen slides in as a page (shared X axis, 320 ms ease-out), keyed by the
            // folder it shows; every page scrolls on its own inside the fixed-height sheet.
            fun depthOf(id: Long?): Int { var d = 0; var c = id?.let { byId[it] }; while (c != null && d < 50) { d++; c = c.parentId?.let { byId[it] } }; return d }
            AnimatedContent(
                targetState = at,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (depthOf(targetState) >= depthOf(initialState)) 1 else -1
                    (fadeIn(tween(Motion.MEDIUM, Motion.SHORT / 2, Motion.EaseOutQuart)) +
                        slideInHorizontally(Motion.enter(Motion.LONG)) { w -> dir * w / 8 }) togetherWith
                        (fadeOut(tween(Motion.MEDIUM, 0, Motion.EaseOutQuart)) +
                            slideOutHorizontally(Motion.exit(Motion.LONG)) { w -> -dir * w / 12 })
                },
                label = "folderLevel",
            ) { lvl ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // This level itself.
                    val here = lvl?.let { byId[it] }
                    FolderRow(
                        if (here == null) str(R.string.dl_save_in_root) else str(R.string.dl_save_here, here.name),
                        here?.color, here?.icon, here?.emoji, selected == lvl, 0,
                    ) { afterSheet { onPick(lvl) } }
                    val level = children[lvl].orEmpty()
                    if (level.isEmpty()) {
                        Text(str(R.string.dl_no_subfolders), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    level.forEach { f ->
                        val inside = children[f.id].orEmpty().size
                        FolderRow(f.name, f.color, f.icon, f.emoji, selected == f.id, 0,
                            caption = if (inside > 0) plural(R.plurals.subfolders_n, inside) else null,
                            onOpen = { at = f.id }) { afterSheet { onPick(f.id) } }
                    }
                    TextButton(onClick = { naming = true }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(CriptaIcons.Add, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(if (here == null) str(R.string.dl_new_folder_root) else str(R.string.dl_new_folder_in, here.name))
                    }
                }
            }
        }
    }
    if (naming) {
        com.cripta.app.ui.vault.TextPromptDialog(
            title = str(R.string.new_folder), label = str(R.string.sort_name),
            onConfirm = { naming = false; onCreate(it, at) }, onDismiss = { naming = false },
            confirmLabel = str(R.string.create),
        )
    }
}

@Composable
private fun FolderRow(
    name: String, color: Int?, icon: String?, emoji: String?, selected: Boolean, depth: Int,
    caption: String? = null,
    /** Opens the folder to see its subfolders; null on the "Salva qui" row. */
    onOpen: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = if (onOpen != null) 4.dp else 20.dp)
            .padding(vertical = if (onOpen != null) 2.dp else 12.dp)
            .padding(start = (14 * depth).dp)
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color == null && icon == null && emoji == null) Icon(CriptaIcons.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        else com.cripta.app.ui.components.FolderGlyph(color, icon, emoji, 22.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(name, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(CriptaIcons.CheckCircle, str(R.string.selected_f), tint = MaterialTheme.colorScheme.primary)
        if (onOpen != null) IconButton(onClick = onOpen) { Icon(CriptaIcons.ChevronRight, str(R.string.dl_open_folder, name)) }
    }
}

/** The tags new downloads get: the shared pills, tap to add or remove; changes apply at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSheet(tags: List<TagEntity>, chosen: Set<Long>, onToggle: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.dl_tags_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (chosen.isNotEmpty()) TextButton(onClick = onClear) { com.cripta.app.ui.components.ButtonText(str(R.string.remove_all)) }
            }
            Text(str(R.string.dl_tags_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            com.cripta.app.ui.components.ChipFlowRow {
                tags.forEach { t ->
                    val sel = t.id in chosen
                    TagPill(
                        tagText(t), tagColor(t), tagIcon = t.icon,
                        size = TagPillSize.Medium,
                        state = if (sel) TagPillState.On else TagPillState.Off,
                        base = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = { onToggle(t.id) },
                        onClickLabel = if (sel) str(R.string.remove_short) else str(R.string.add),
                        stateLabel = if (sel) str(R.string.chosen) else str(R.string.not_chosen),
                    )
                }
            }
        }
    }
}

/**
 * One queue entry, no frame: a round status mark, the name/link, its state in words, and the
 * action that fits. While it runs, a thick rounded bar under it.
 */
@Composable
private fun JobRow(
    job: DownloadJob,
    destination: String,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val (icon, tint) = when (job.phase) {
        DownloadPhase.DONE -> CriptaIcons.CheckCircle to MaterialTheme.colorScheme.primary
        DownloadPhase.FAILED -> CriptaIcons.ErrorOutline to MaterialTheme.colorScheme.error
        DownloadPhase.CANCELLED -> CriptaIcons.ErrorOutline to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadPhase.QUEUED -> CriptaIcons.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        else -> CriptaIcons.Download to MaterialTheme.colorScheme.primary
    }
    val group = phaseGroup(job.phase)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center) {
                // A finished job's icon settles in (scale up + fade) instead of popping.
                AnimatedContent(
                    targetState = icon to tint,
                    transitionSpec = {
                        val settle = targetState.first == CriptaIcons.CheckCircle || targetState.first == CriptaIcons.ErrorOutline
                        (fadeIn(Motion.enter()) + (if (settle) scaleIn(Motion.enter(Motion.LONG), initialScale = 0.6f)
                            else androidx.compose.animation.EnterTransition.None)) togetherWith fadeOut(Motion.exit())
                    },
                    label = "jobIcon",
                ) { (i, t) -> Icon(i, null, tint = t, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    if (job.phase == DownloadPhase.DONE && job.message != null) job.message else job.url,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val status = when (job.phase) {
                    DownloadPhase.QUEUED -> str(R.string.dl_waiting_in, destination)
                    DownloadPhase.PREPARING -> str(R.string.n_preparing)
                    DownloadPhase.DOWNLOADING ->
                        "${job.pct}%" + (if (job.etaSec > 0) " · " + com.cripta.app.util.formatEtaShort(job.etaSec * 1000) else "") +
                            str(R.string.dl_in_dest, destination)
                    DownloadPhase.DONE -> str(R.string.dl_saved_in, destination)
                    DownloadPhase.FAILED -> friendlyDownloadError(job.message)
                    DownloadPhase.CANCELLED -> str(R.string.chip_cancelled)
                }
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = if (job.phase == DownloadPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            // The action that fits the phase, swapped with a crossfade.
            AnimatedContent(
                targetState = group,
                transitionSpec = {
                    (fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit())) using SizeTransform(clip = false) { _, _ -> Motion.enter() }
                },
                label = "jobAction",
            ) { g ->
                when {
                    g == PhaseGroup.RUNNING -> TextButton(onClick = onCancel) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) }
                    g == PhaseGroup.DONE && job.fileId != null -> TextButton(onClick = { job.fileId?.let(onOpen) }) { com.cripta.app.ui.components.ButtonText(str(R.string.open)) }
                    g == PhaseGroup.STOPPED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onRetry) { com.cripta.app.ui.components.ButtonText(str(R.string.retry)) }
                        IconButton(onClick = onRemove) {
                            Icon(CriptaIcons.Close, str(R.string.dl_remove_from_queue), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> IconButton(onClick = onRemove) {
                        Icon(CriptaIcons.Close, str(R.string.dl_remove_from_queue), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Progress: only while running; indeterminate while preparing, smoothed while downloading.
        AnimatedVisibility(
            visible = group == PhaseGroup.RUNNING,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            val smooth by animateFloatAsState(
                targetValue = if (job.phase == DownloadPhase.DOWNLOADING) job.pct / 100f else 0f,
                animationSpec = tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "jobProgress",
            )
            val bar = Modifier.fillMaxWidth().padding(start = 54.dp).height(6.dp).clip(CircleShape)
            Crossfade(job.phase == DownloadPhase.DOWNLOADING, animationSpec = Motion.enter(), label = "jobBar") { determinate ->
                if (determinate) LinearProgressIndicator(progress = { smooth }, modifier = bar,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                else LinearProgressIndicator(bar, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        }
    }
}

/** Coarse phases: an entry only re-animates when one of these changes, not on every % tick. */
private enum class PhaseGroup { WAITING, RUNNING, DONE, STOPPED }

private fun phaseGroup(p: DownloadPhase): PhaseGroup = when (p) {
    DownloadPhase.QUEUED -> PhaseGroup.WAITING
    DownloadPhase.PREPARING, DownloadPhase.DOWNLOADING -> PhaseGroup.RUNNING
    DownloadPhase.DONE -> PhaseGroup.DONE
    DownloadPhase.FAILED, DownloadPhase.CANCELLED -> PhaseGroup.STOPPED
}

/**
 * Preview of an analysed link: the site's cover large, title and length under it. The cover is
 * fetched straight from the site (as the download will be) and kept in memory only, never in a
 * disk cache.
 */
@Composable
private fun LinkPreview(e: DownloadViewModel.Estimate.Ready, link: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(com.cripta.app.ui.theme.RadiusLg).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CriptaIcons.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            e.thumbnailUrl?.let { url ->
                val ctx = LocalContext.current
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(ctx).data(url)
                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                        .crossfade(true).build(),
                    contentDescription = str(R.string.cover),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // DS: the length on the glass, top right of the cover.
            com.cripta.app.ui.components.formatDuration(e.durationSec * 1000L)?.let { d ->
                com.cripta.app.ui.components.CoverBadge(d, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Text(
            e.title ?: str(R.string.dl_video_found),
            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        // DS caption: the site, then the length.
        val host = runCatching { android.net.Uri.parse(link.trim()).host?.removePrefix("www.") }.getOrNull()
        listOfNotNull(host, com.cripta.app.ui.components.formatDuration(e.durationSec * 1000L)).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { d ->
            Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

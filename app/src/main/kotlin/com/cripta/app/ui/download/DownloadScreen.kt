package com.cripta.app.ui.download

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.cripta.app.data.VaultRepository.DownloadJob
import com.cripta.app.data.VaultRepository.DownloadPhase
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
    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    val qualities = listOf<Pair<Int?, String>>(null to "Auto", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
    val heights = qualities.map { it.first }
    val downloads by vm.downloads.collectAsState()
    val estimate by vm.estimate.collectAsState()
    val existing by vm.existing.collectAsState()
    val pending by vm.pendingSharedLink.collectAsState()
    val folders by vm.folders.collectAsState()
    val tags by vm.tags.collectAsState()
    val defaults by vm.defaults.collectAsState()

    // Destination + quality start from the last used ones (loaded once), then follow the user.
    var quality by remember { mutableStateOf<Int?>(null) }
    var folderId by remember { mutableStateOf<Long?>(null) }
    var tagIds by remember { mutableStateOf(setOf<Long>()) }
    var defaultsApplied by remember { mutableStateOf(false) }
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
        // Not a link (yet): nothing to probe, and no misleading "estimate failed".
        if (!looksLikeUrl(u)) { vm.resetEstimate() } else { delay(700); vm.estimate(u, heights) }
    }

    fun paste() {
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.isNotBlank()) url = extractUrl(clip)
        else Toast.makeText(ctx, "Appunti vuoti", Toast.LENGTH_SHORT).show()
    }

    val urlValid = looksLikeUrl(url)
    // Only complain once something has been typed that clearly isn't a link.
    val urlError = url.isNotBlank() && !urlValid
    fun submit() {
        val u = url.trim()
        if (!looksLikeUrl(u)) return
        if (vm.isQueued(u)) {
            Toast.makeText(ctx, "Questo link è già in coda", Toast.LENGTH_SHORT).show()
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
            label = { Text("Link del video") },
            placeholder = { Text("https://…") },
            leadingIcon = { Icon(Icons.Filled.Link, null) },
            trailingIcon = {
                if (url.isBlank()) {
                    TextButton(onClick = { paste() }, modifier = Modifier.padding(end = 4.dp)) { Text("Incolla") }
                } else {
                    IconButton(onClick = { url = ""; vm.resetEstimate() }) { Icon(Icons.Filled.Clear, "Svuota il link") }
                }
            },
            singleLine = true,
            isError = urlError,
            supportingText = if (urlError) ({ Text("Non sembra un link: deve iniziare con http:// o https://") }) else null,
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
            lastReady?.let { LinkPreview(it) }
        }
        if (url.isBlank()) {
            Text("YouTube, Vimeo e molti altri siti. Il video finisce cifrato nel vault.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Already downloaded from this link? One notice, in a tonal slab of its own.
        existing?.let { f ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Già nel vault", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(f.originalName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    TextButton(onClick = { vm.prepareOpen(f.id); onOpenFile(f.id) }) {
                        Text("Apri", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        Heading("Qualità", Modifier.padding(top = 8.dp))
        QualitySelector(qualities, quality, estimate) { quality = it }
        // Estimate line: takes no space while idle, crossfades between "computing" / result / error.
        val estimateText = when (val e = estimate) {
            is DownloadViewModel.Estimate.Error -> "Dimensioni non stimabili: ${e.message}"
            DownloadViewModel.Estimate.Loading -> "Calcolo delle dimensioni…"
            is DownloadViewModel.Estimate.Ready -> "Auto prende la migliore qualità disponibile. «—»: dimensione non stimabile."
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
        Heading("Destinazione", Modifier.padding(top = 8.dp))
        Column {
            val f = folders.firstOrNull { it.id == effectiveFolder }
            DestinationRow(
                icon = { com.cripta.app.ui.components.FolderGlyph(f?.color, f?.emoji, 26.dp) },
                title = "Cartella",
                onClick = { pickFolder = true },
                onClickLabel = "Cambia cartella",
            ) {
                Text(f?.let { folderLabel(it, folders) } ?: "Radice del vault", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tags.isNotEmpty()) {
                DestinationRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = "Etichette",
                    onClick = { pickTags = true },
                    onClickLabel = "Scegli etichette",
                ) {
                    val chosen = tags.filter { it.id in effectiveTags }
                    if (chosen.isEmpty()) {
                        Text("Nessuna", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            chosen.forEach { t -> TagPill(tagText(t), tagColor(t), base = MaterialTheme.colorScheme.background) }
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
            Icon(Icons.Filled.Download, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                if (downloads.any { it.active }) "Aggiungi alla coda" else if (existing != null) "Scarica comunque" else "Scarica",
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }

    val queue: @Composable ColumnScope.() -> Unit = {
        val running = downloads.filter { phaseGroup(it.phase) == PhaseGroup.RUNNING }
        val waiting = downloads.filter { phaseGroup(it.phase) == PhaseGroup.WAITING }
        val finished = downloads.filter { it.finished }
        fun dest(job: DownloadJob) =
            job.folderId?.let { id -> folders.firstOrNull { it.id == id } }?.let { folderLabel(it, folders) } ?: "Radice"
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
                Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text("Nessun download in coda", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(top = 8.dp))
                Text("I link si scaricano uno alla volta, anche con l'app chiusa.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (running.isNotEmpty()) { Heading("In corso"); jobs(running) }
        if (waiting.isNotEmpty()) { Heading("In attesa · ${waiting.size}", Modifier.padding(top = 8.dp)); jobs(waiting) }
        if (finished.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Heading("Finiti", Modifier.weight(1f))
                TextButton(onClick = { vm.clearFinished() }) { Text("Pulisci") }
            }
            jobs(finished)
        }
        Text(
            "Il primo download può richiedere qualche secondo in più. I video protetti (es. piattaforme " +
                "a pagamento) non si possono scaricare.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Download") },
                scrollBehavior = scrollBehavior,
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
            )
        },
    ) { pad ->
        if (landscape) {
            // Landscape: the form on the left, the queue on the right, each scrolling on its own.
            Row(
                Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = form,
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = queue,
                )
            }
        } else {
            Column(
                // imePadding keeps the field and the "Scarica" button reachable above the keyboard.
                Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
                    .fillMaxWidth().wrapContentWidth().widthIn(max = 720.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                form()
                Spacer(Modifier.height(16.dp))
                queue()
            }
        }
    }

    if (pickFolder) {
        FolderPickerSheet(folders, effectiveFolder, onPick = { folderId = it; pickFolder = false }, onDismiss = { pickFolder = false })
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
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
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
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Box(Modifier.padding(top = 2.dp)) { value() }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Modifier.weight(1f).heightIn(min = 60.dp).clip(MaterialTheme.shapes.medium)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(role = Role.RadioButton, onClick = { onSelect(h) })
                            .semantics { this.selected = on }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (on) Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.padding(end = 4.dp).size(16.dp))
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
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    modifier = if (bytes == null) Modifier.semantics { contentDescription = "dimensione non stimabile" } else Modifier,
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

@Composable
private fun FolderPickerSheet(folders: List<FolderEntity>, selected: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Text("Salva in…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            FolderRow("Radice del vault", null, null, selected == null, 0) { afterSheet { onPick(null) } }
            // Tree order: each folder under its parent, indented by depth.
            val children = folders.groupBy { it.parentId }
            fun walk(parent: Long?, depth: Int, out: MutableList<Pair<FolderEntity, Int>>) {
                children[parent].orEmpty().forEach { out += it to depth; walk(it.id, depth + 1, out) }
            }
            val ordered = mutableListOf<Pair<FolderEntity, Int>>().also { walk(null, 0, it) }
            ordered.forEach { (f, d) -> FolderRow(f.name, f.color, f.emoji, selected == f.id, d) { afterSheet { onPick(f.id) } } }
        }
    }
}

@Composable
private fun FolderRow(name: String, color: Int?, emoji: String?, selected: Boolean, depth: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(start = (14 * depth).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color == null && emoji == null) Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        else com.cripta.app.ui.components.FolderGlyph(color, emoji, 22.dp)
        Text(name, Modifier.weight(1f).padding(start = 14.dp))
        if (selected) Icon(Icons.Filled.CheckCircle, "Selezionata", tint = MaterialTheme.colorScheme.primary)
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
                Text("Etichette dei download", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (chosen.isNotEmpty()) TextButton(onClick = onClear) { Text("Togli tutte") }
            }
            Text("Vanno su ogni video scaricato da qui in poi.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            com.cripta.app.ui.components.ChipFlowRow {
                tags.forEach { t ->
                    val sel = t.id in chosen
                    TagPill(
                        tagText(t), tagColor(t),
                        size = TagPillSize.Medium,
                        state = if (sel) TagPillState.On else TagPillState.Off,
                        base = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = { onToggle(t.id) },
                        onClickLabel = if (sel) "Togli" else "Aggiungi",
                        stateLabel = if (sel) "Scelta" else "Non scelta",
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
        DownloadPhase.DONE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        DownloadPhase.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        DownloadPhase.CANCELLED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadPhase.QUEUED -> Icons.Filled.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Filled.Download to MaterialTheme.colorScheme.primary
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
                        val settle = targetState.first == Icons.Filled.CheckCircle || targetState.first == Icons.Filled.ErrorOutline
                        (fadeIn(Motion.enter()) + (if (settle) scaleIn(Motion.enter(Motion.LONG), initialScale = 0.6f)
                            else androidx.compose.animation.EnterTransition.None)) togetherWith fadeOut(Motion.exit())
                    },
                    label = "jobIcon",
                ) { (i, t) -> Icon(i, null, tint = t, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    if (job.phase == DownloadPhase.DONE && job.message != null) job.message else job.url,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val status = when (job.phase) {
                    DownloadPhase.QUEUED -> "In attesa · in $destination"
                    DownloadPhase.PREPARING -> "Preparazione…"
                    DownloadPhase.DOWNLOADING ->
                        "${job.pct}%" + (if (job.etaSec > 0) " · " + com.cripta.app.util.formatEtaShort(job.etaSec * 1000) else "") +
                            " · in $destination"
                    DownloadPhase.DONE -> "Salvato in $destination"
                    DownloadPhase.FAILED -> friendlyDownloadError(job.message)
                    DownloadPhase.CANCELLED -> "Annullato"
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
                    g == PhaseGroup.RUNNING -> TextButton(onClick = onCancel) { Text("Annulla") }
                    g == PhaseGroup.DONE && job.fileId != null -> TextButton(onClick = { job.fileId?.let(onOpen) }) { Text("Apri") }
                    g == PhaseGroup.STOPPED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onRetry) { Text("Riprova") }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Filled.Close, "Rimuovi dalla coda", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, "Rimuovi dalla coda", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun LinkPreview(e: DownloadViewModel.Estimate.Ready) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(CoverShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            e.thumbnailUrl?.let { url ->
                val ctx = LocalContext.current
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(ctx).data(url)
                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                        .crossfade(true).build(),
                    contentDescription = "Copertina",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Text(
            e.title ?: "Video trovato",
            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        com.cripta.app.ui.components.formatDuration(e.durationSec * 1000L)?.let { d ->
            Text("Durata $d", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

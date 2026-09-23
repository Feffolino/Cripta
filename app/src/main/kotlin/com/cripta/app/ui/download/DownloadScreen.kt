package com.cripta.app.ui.download

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.VaultRepository.DownloadJob
import com.cripta.app.data.VaultRepository.DownloadPhase
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.ui.components.formatBytes
import kotlinx.coroutines.delay

/**
 * In-app video downloader: paste (or share in) a link, pick a quality — with a size estimate per
 * resolution — and where it goes (folder + tags, remembered for next time). Links queue up and are
 * fetched one at a time with yt-dlp, encrypted into the vault with their source link stored.
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
    var tagsOpen by remember { mutableStateOf(false) }

    // A link shared from another app: pre-fill the field so the user can pick a resolution.
    LaunchedEffect(pending) {
        pending?.let { url = it; vm.consumeSharedLink() }
    }
    // Debounced size probe (and "already downloaded" check) whenever the link settles.
    LaunchedEffect(url) {
        val u = url.trim()
        if (u.isBlank()) { vm.resetEstimate() } else { delay(700); vm.estimate(u, heights) }
    }

    fun paste() {
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.isNotBlank()) url = clip
        else Toast.makeText(ctx, "Appunti vuoti", Toast.LENGTH_SHORT).show()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Download") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Scarica un video", style = MaterialTheme.typography.titleMedium)
                    Text("YouTube, Vimeo, HLS, link diretti e altri. Cifrato nel vault.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link video") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Paste the clipboard link in one tap, or clear the field to start over.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { paste() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Text("  Incolla")
                }
                OutlinedButton(onClick = { url = ""; vm.resetEstimate() }, enabled = url.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp))
                    Text("  Cancella")
                }
            }

            // Already downloaded from this link?
            existing?.let { f ->
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Già nel vault", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(f.originalName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        TextButton(onClick = { vm.prepareOpen(f.id); onOpenFile(f.id) }) { Text("Apri") }
                    }
                }
            }

            Text("Qualità", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { (h, label) ->
                    FilterChip(
                        selected = quality == h,
                        onClick = { quality = h },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label)
                                SizeHint(estimate, h)
                            }
                        },
                    )
                }
            }
            when (val e = estimate) {
                is DownloadViewModel.Estimate.Error ->
                    Text("Stima non disponibile: ${e.message}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                DownloadViewModel.Estimate.Loading ->
                    Text("Calcolo dimensioni…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {}
            }

            // Destination (remembered).
            Text("Salva in", style = MaterialTheme.typography.labelLarge)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { pickFolder = true },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val f = folders.firstOrNull { it.id == effectiveFolder }
                    com.cripta.app.ui.components.FolderGlyph(f?.color, f?.emoji, 26.dp)
                    Text(f?.let { folderLabel(it, folders) } ?: "Radice", Modifier.weight(1f).padding(start = 12.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Cambia", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (tags.isNotEmpty()) {
                // Collapsed by default: a one-line summary of the chosen tags; tap to expand the picker.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { tagsOpen = !tagsOpen },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Etichette", style = MaterialTheme.typography.bodyLarge)
                            val chosen = tags.filter { it.id in effectiveTags }
                            Text(
                                if (chosen.isEmpty()) "Nessuna" else chosen.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(if (tagsOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            if (tagsOpen) "Comprimi" else "Espandi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = tagsOpen) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { t ->
                            val sel = t.id in effectiveTags
                            FilterChip(
                                selected = sel,
                                onClick = { tagIds = if (sel) tagIds - t.id else tagIds + t.id },
                                leadingIcon = {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(com.cripta.app.ui.theme.tagColor(t)))
                                },
                                label = { Text(if (!t.alias.isNullOrBlank()) "${t.alias} ${t.name}" else t.name) },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val u = url.trim()
                    if (u.isNotBlank()) {
                        if (vm.isQueued(u)) {
                            Toast.makeText(ctx, "Questo link è già in coda", Toast.LENGTH_SHORT).show()
                        } else {
                            vm.enqueue(ctx, u, quality, effectiveFolder, effectiveTags)
                            url = ""; vm.resetEstimate()
                        }
                    }
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Text(if (downloads.any { it.active }) "  Aggiungi alla coda" else if (existing != null) "  Scarica comunque" else "  Scarica")
            }

            if (downloads.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Coda", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (downloads.any { it.finished }) TextButton(onClick = { vm.clearFinished() }) { Text("Pulisci completati") }
                }
                downloads.forEach { job ->
                    JobCard(
                        job,
                        onCancel = { vm.cancel(ctx) },
                        onRemove = { vm.remove(job.id) },
                        onOpen = { id -> vm.prepareOpen(id); onOpenFile(id) },
                    )
                }
            }

            Text(
                "I contenuti protetti da DRM non sono scaricabili. Il primo download prepara il motore " +
                    "e può richiedere qualche secondo. Puoi aggiungere più link: vengono scaricati uno alla volta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickFolder) {
        FolderPickerSheet(folders, effectiveFolder, onPick = { folderId = it; pickFolder = false }, onDismiss = { pickFolder = false })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerSheet(folders: List<FolderEntity>, selected: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Text("Salva in…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            FolderRow("Radice", null, null, selected == null, 0) { onPick(null) }
            // Tree order: each folder under its parent, indented by depth.
            val children = folders.groupBy { it.parentId }
            fun walk(parent: Long?, depth: Int, out: MutableList<Pair<FolderEntity, Int>>) {
                children[parent].orEmpty().forEach { out += it to depth; walk(it.id, depth + 1, out) }
            }
            val ordered = mutableListOf<Pair<FolderEntity, Int>>().also { walk(null, 0, it) }
            ordered.forEach { (f, d) -> FolderRow(f.name, f.color, f.emoji, selected == f.id, d) { onPick(f.id) } }
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

/** Small size estimate shown inside a quality chip. */
@Composable
private fun SizeHint(estimate: DownloadViewModel.Estimate, height: Int?) {
    when (estimate) {
        DownloadViewModel.Estimate.Loading ->
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
        is DownloadViewModel.Estimate.Ready -> {
            val bytes = estimate.bytesByHeight[height]
            Text(
                if (bytes != null) "~${formatBytes(bytes)}" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {}
    }
}

/** One queue entry: its link/name, live progress, and the action that fits its state. */
@Composable
private fun JobCard(job: DownloadJob, onCancel: () -> Unit, onRemove: () -> Unit, onOpen: (String) -> Unit) {
    val (icon, tint) = when (job.phase) {
        DownloadPhase.DONE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        DownloadPhase.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        DownloadPhase.CANCELLED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadPhase.QUEUED -> Icons.Filled.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Filled.Download to MaterialTheme.colorScheme.primary
    }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        if (job.phase == DownloadPhase.DONE && job.message != null) job.message else job.url,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val status = when (job.phase) {
                        DownloadPhase.QUEUED -> "In coda"
                        DownloadPhase.PREPARING -> "Preparazione…"
                        DownloadPhase.DOWNLOADING ->
                            "${job.pct}%" + if (job.etaSec > 0) " · resta ${etaText(job.etaSec)}" else ""
                        DownloadPhase.DONE -> "Completato"
                        DownloadPhase.FAILED -> "Fallito" + (job.message?.let { ": $it" } ?: "")
                        DownloadPhase.CANCELLED -> "Annullato"
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium,
                        color = if (job.phase == DownloadPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                when {
                    job.active -> TextButton(onClick = onCancel) { Text("Annulla") }
                    job.phase == DownloadPhase.DONE && job.fileId != null -> TextButton(onClick = { job.fileId?.let(onOpen) }) { Text("Apri") }
                    else -> IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, "Rimuovi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            when (job.phase) {
                DownloadPhase.PREPARING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                DownloadPhase.DOWNLOADING -> LinearProgressIndicator(progress = { job.pct / 100f }, modifier = Modifier.fillMaxWidth())
                else -> {}
            }
        }
    }
}

private fun etaText(sec: Long): String =
    if (sec >= 60) "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}" else "${sec}s"

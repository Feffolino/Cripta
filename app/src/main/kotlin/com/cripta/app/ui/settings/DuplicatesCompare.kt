package com.cripta.app.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.components.formatDuration

/** A pending destructive choice, confirmed before anything is shredded. */
private sealed interface DupAction {
    data class KeepOnly(val group: SettingsViewModel.DupGroup, val keep: SettingsViewModel.DupCandidate) : DupAction
    data class DeleteOne(val group: SettingsViewModel.DupGroup, val target: SettingsViewModel.DupCandidate) : DupAction
}

/**
 * Full-screen, side-by-side comparison of duplicate groups. Every copy shows its cover, resolution,
 * size, duration, date and tags; the suggested copy to keep is highlighted with the reasons. Keeping
 * one copy deletes the others and moves their tags onto it, so deleting a tagged copy while keeping
 * an untagged one never loses the tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesCompare(
    title: String,
    groups: List<SettingsViewModel.DupGroup>,
    summary: String,
    emptyText: String,
    notice: String?,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onKeepOnly: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearNotice: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember { mutableStateOf<DupAction?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) { kotlinx.coroutines.delay(4000); onClearNotice() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Chiudi") } },
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(summary, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (notice != null) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(notice, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
                if (groups.isEmpty()) {
                    item {
                        Text(emptyText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 24.dp))
                    }
                }
                items(groups, key = { g -> g.candidates.joinToString("|") { it.file.id } }) { g ->
                    GroupCard(
                        group = g,
                        thumb = thumb,
                        onKeep = { c -> pending = DupAction.KeepOnly(g, c) },
                        onDelete = { c -> pending = DupAction.DeleteOne(g, c) },
                    )
                }
            }
        }

        pending?.let { action -> ConfirmDialog(action, onConfirm = {
            when (action) {
                is DupAction.KeepOnly -> onKeepOnly(action.keep.file.id)
                is DupAction.DeleteOne -> onDelete(action.target.file.id)
            }
            pending = null
        }, onDismiss = { pending = null }) }
    }
}

@Composable
private fun ConfirmDialog(action: DupAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (action) {
        is DupAction.KeepOnly -> {
            val others = action.group.candidates.filter { it.file.id != action.keep.file.id }
            val moving = others.flatMap { it.tags }.distinct().filterNot { it in action.keep.tags }
            val t = if (others.size == 1) "Eliminare l'altra copia?" else "Eliminare le altre ${others.size} copie?"
            val b = buildString {
                append("Resta \"${action.keep.file.originalName}\". ")
                append(if (moving.isEmpty()) "Nessuna etichetta da spostare."
                    else "Le etichette ${moving.joinToString(", ") { "#$it" }} verranno spostate sulla copia tenuta.")
                append(" Eliminazione sicura, irreversibile.")
            }
            t to b
        }
        is DupAction.DeleteOne -> {
            val g = action.group
            val heir = if (g.bestId != action.target.file.id) g.best
                else g.candidates.firstOrNull { it.file.id != action.target.file.id }
            val moving = heir?.let { h -> action.target.tags.filterNot { it in h.tags } }.orEmpty()
            "Eliminare questa copia?" to buildString {
                append("\"${action.target.file.originalName}\" verrà eliminato in modo sicuro. ")
                if (moving.isNotEmpty() && heir != null) {
                    append("Le sue etichette ${moving.joinToString(", ") { "#$it" }} verranno spostate su \"${heir.file.originalName}\".")
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Elimina", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun GroupCard(
    group: SettingsViewModel.DupGroup,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onKeep: (SettingsViewModel.DupCandidate) -> Unit,
    onDelete: (SettingsViewModel.DupCandidate) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val total = group.candidates.sumOf { it.file.sizeBytes }
            val reclaim = total - group.best.file.sizeBytes
            Text(
                "${group.candidates.size} copie · ${formatBytes(total)} · recuperabili ${formatBytes(reclaim)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.ThumbUp, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Text(
                    buildString {
                        append("Consigliato: \"${group.best.file.originalName}\"")
                        if (group.reasons.isNotEmpty()) append(" — ${group.reasons.joinToString(", ")}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                group.candidates.forEach { c ->
                    CandidateCard(
                        c = c,
                        best = c.file.id == group.bestId,
                        bestPixels = group.best.pixels,
                        thumb = thumb,
                        onKeep = { onKeep(c) },
                        onDelete = { onDelete(c) },
                    )
                }
            }
            Button(onClick = { onKeep(group.best) }, modifier = Modifier.fillMaxWidth()) {
                val n = group.candidates.size - 1
                Text(if (n == 1) "Tieni il consigliato, elimina l'altra" else "Tieni il consigliato, elimina le altre $n")
            }
            if (group.tagsToMove.isNotEmpty()) {
                Text(
                    "Le etichette ${group.tagsToMove.joinToString(", ") { "#$it" }} verranno spostate sulla copia tenuta.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    c: SettingsViewModel.DupCandidate,
    best: Boolean,
    bestPixels: Long,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
) {
    val f = c.file
    val border = if (best) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Surface(
        color = if (best) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        border = border,
        modifier = Modifier.width(188.dp),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small)) {
                val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
                Crossfade(bmp, label = "dupthumb") { b ->
                    if (b != null) {
                        Image(b.asImageBitmap(), f.originalName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                when {
                                    VaultRepository.isVideo(f.mimeType) -> Icons.Filled.Movie
                                    VaultRepository.isImage(f.mimeType) -> Icons.Filled.Image
                                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                if (best) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                        Text("CONSIGLIATO", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (f.isFavorite) {
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Star, "Preferito", tint = com.cripta.app.ui.theme.Favorite, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(f.originalName, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // Resolution is highlighted when it is lower than the suggested copy's.
            c.resolution?.let { (w, h) ->
                val lower = c.pixels in 1 until bestPixels
                Text("$w×$h", style = MaterialTheme.typography.labelMedium,
                    color = if (lower) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            Text(
                listOfNotNull(
                    formatBytes(f.sizeBytes),
                    formatDuration(f.durationMs),
                    f.mimeType.substringAfter('/').uppercase().take(6),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(f.importedAt)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!f.sourceUrl.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(" link di origine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (c.tags.isEmpty()) {
                Text("Nessuna etichetta", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.tags.forEach { t ->
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                            Text("#$t", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = onKeep, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Tieni solo questo", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Elimina questa copia", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

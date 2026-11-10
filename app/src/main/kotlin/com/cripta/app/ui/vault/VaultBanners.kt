package com.cripta.app.ui.vault

import com.cripta.app.ui.theme.riseIn
import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.cripta.app.ui.components.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import android.content.res.Configuration
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import com.cripta.app.data.VaultRepository
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.first

@Composable
internal fun EmptyState(
    filtering: Boolean,
    modifier: Modifier,
    onImport: () -> Unit = {},
    folderName: String? = null,
    searchedWhere: String = str(R.string.in_whole_vault),
    onClearFilters: () -> Unit = {},
) {
    // Landscape: smaller badge and padding + scroll, so the import button is always reachable.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val outer = (if (landscape) modifier.verticalScroll(rememberScrollState()).padding(16.dp) else modifier.padding(32.dp)).riseIn()
    val badge = if (landscape) 56.dp else 96.dp
    val glyph = if (landscape) 28.dp else 44.dp
    if (!filtering && folderName != null) {
        // Empty folder: say where we are and how to fill it.
        Column(outer, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(badge)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(CriptaIcons.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(glyph))
                }
            }
            Text(str(R.string.folder_x_empty, folderName), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp),
                textAlign = TextAlign.Center)
            Text(str(R.string.folder_empty_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onImport, modifier = Modifier.padding(top = 20.dp).heightIn(min = 56.dp)) {
                Icon(CriptaIcons.EnhancedEncryption, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                com.cripta.app.ui.components.ButtonText(str(R.string.import_here))
            }
        }
        return
    }
    Column(outer, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(badge),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (filtering) CriptaIcons.Search else CriptaIcons.EnhancedEncryption,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(glyph),
                )
            }
        }
        Text(if (filtering) str(R.string.no_results) else str(R.string.vault_empty),
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
        Text(if (filtering) str(R.string.no_file_matches, searchedWhere)
            else str(R.string.vault_empty_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (filtering) {
            com.cripta.app.ui.components.FilledTonalButton(onClick = onClearFilters, modifier = Modifier.padding(top = 20.dp).heightIn(min = 48.dp)) {
                com.cripta.app.ui.components.ButtonText(str(R.string.reset_filters))
            }
        } else {
            Button(onClick = onImport, modifier = Modifier.padding(top = 20.dp).heightIn(min = 56.dp)) {
                Icon(CriptaIcons.EnhancedEncryption, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                com.cripta.app.ui.components.ButtonText(str(R.string.import_files))
            }
        }
    }
}

/**
 * The "+" actions, in a column above the button (the first one nearest to it), right-aligned with
 * it: each a tonal pill button with its icon and name, like Material's FAB menu.
 */
@Composable
internal fun FabMenu(actions: List<FabAction>, modifier: Modifier = Modifier) {
    // DS cr-fab-pill: one floating pill (surface-overlay, radius-lg) holding the actions side by
    // side, each an accent disc with its name under it.
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = com.cripta.app.ui.theme.RadiusLg,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { a ->
                Column(
                    Modifier.width(64.dp).clip(com.cripta.app.ui.theme.RadiusMd)
                        .clickable(role = Role.Button, onClickLabel = a.caption, onClick = a.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(a.icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                    Text(a.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Live import status under the search bar: while encrypting it shows "n of N", the file being
 * processed and its progress; when done it confirms how many files made it (and how many failed).
 */
@Composable
internal fun ImportBanner(state: VaultRepository.ImportState, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = state.active || state.finished,
        enter = androidx.compose.animation.expandVertically() + fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
    ) {
        val ok = state.failed == 0 && state.incomplete == 0
        val container = when {
            state.active -> com.cripta.app.ui.components.tonalContainer()
            ok -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        }
        val onContainer = when {
            state.active -> MaterialTheme.colorScheme.onSecondaryContainer
            ok -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onErrorContainer
        }
        Surface(
            color = container,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.active) {
                        androidx.compose.material3.CircularProgressIndicator(
                            strokeWidth = 2.dp, color = onContainer, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(if (ok) CriptaIcons.CheckCircle else CriptaIcons.ErrorOutline, null,
                            tint = onContainer, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        val title = when {
                            state.active -> str(R.string.importing_progress, minOf(state.done + 1, state.total), state.total)
                            ok -> str(R.string.n_import_done)
                            else -> str(R.string.n_import_done_errors)
                        }
                        Text(title, style = MaterialTheme.typography.titleSmall, color = onContainer)
                        val sub = if (state.active) {
                            val name = state.currentName ?: str(R.string.n_preparing)
                            val pct = if (state.currentTotalBytes > 0)
                                " · ${(state.currentBytes * 100 / state.currentTotalBytes).coerceIn(0, 100)}%" else ""
                            name + pct
                        } else buildString {
                            append(plural(R.plurals.import_encrypted, state.succeeded))
                            if (state.failed > 0) append(str(R.string.n_not_imported, state.failed))
                            if (state.incomplete > 0) append(str(R.string.n_maybe_incomplete, state.incomplete))
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = onContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!state.active) {
                        IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                            Icon(CriptaIcons.Close, str(R.string.close), tint = onContainer, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Text("${(state.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge,
                            color = onContainer, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (state.active) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.fraction },
                        color = onContainer,
                        trackColor = onContainer.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val eta = com.cripta.app.ui.components.rememberEta(state.fraction, state.active)
                    Text((eta?.replaceFirstChar { it.uppercase() }?.let { "$it. " } ?: "") +
                        str(R.string.import_continues),
                        style = MaterialTheme.typography.labelSmall, color = onContainer.copy(alpha = 0.8f))
                }
                // Which files failed and why, so the user knows what to try again.
                if (!state.active && state.failures.isNotEmpty()) {
                    state.failures.take(5).forEach { (name, reason) ->
                        Text("$name: $reason", style = MaterialTheme.typography.bodySmall, color = onContainer,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.failures.size > 5) {
                        Text(str(R.string.and_n_more, state.failures.size - 5), style = MaterialTheme.typography.bodySmall, color = onContainer)
                    }
                }
                // Byte-identical copies of files already in the vault: asked about once the import ends.
                if (state.duplicates.isNotEmpty()) {
                    val n = state.duplicates.size
                    Text(
                        if (n == 1) str(R.string.already_in_vault_one, state.duplicates.first().second)
                        else str(R.string.already_in_vault_n, n),
                        style = MaterialTheme.typography.bodySmall, color = onContainer,
                    )
                }
            }
        }
    }
}

/** Conversion queue status: current video + progress + how many wait, then the outcome. */
@Composable
fun ConvertBanner(
    state: VaultRepository.ConvertStatus,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onResolveOriginal: (originalId: String, delete: Boolean) -> Unit = { _, _ -> },
) {
    AnimatedVisibility(
        visible = state.active || state.lastResult != null || state.pendingOriginals.isNotEmpty(),
        enter = androidx.compose.animation.expandVertically() + fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
    ) {
        val container = when {
            state.active -> com.cripta.app.ui.components.tonalContainer()
            state.lastOk -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        }
        val on = when {
            state.active -> MaterialTheme.colorScheme.onSecondaryContainer
            state.lastOk -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onErrorContainer
        }
        Surface(color = container, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.active) CriptaIcons.Movie else if (state.lastOk) CriptaIcons.CheckCircle else CriptaIcons.ErrorOutline,
                        null, tint = on, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        if (state.active) {
                            Text(str(R.string.converting_mp4_pct, state.pct) + if (state.waiting > 0) str(R.string.n_queued_sep, state.waiting) else "",
                                style = MaterialTheme.typography.titleSmall, color = on)
                            Text(state.currentName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = on,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            val pending = state.pendingOriginals
                            if (pending.isNotEmpty()) {
                                Text("${pending.size} ${if (pending.size == 1) "originale da gestire" else "originali da gestire"}",
                                    style = MaterialTheme.typography.titleSmall, color = on)
                                Text(pending.first().name, style = MaterialTheme.typography.bodySmall, color = on,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else Text(state.lastResult.orEmpty(), style = MaterialTheme.typography.bodySmall, color = on)
                        }
                    }
                    if (state.active) TextButton(onClick = onCancel) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel), color = on) }
                    else IconButton(onClick = {
                        state.pendingOriginals.firstOrNull()?.let { onResolveOriginal(it.id, false) } ?: onDismiss()
                    }, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(CriptaIcons.Close, if (state.pendingOriginals.isEmpty()) str(R.string.close) else str(R.string.close_keep_original), tint = on, modifier = Modifier.size(18.dp))
                    }
                }
                if (!state.lastOk && state.lastResult != null && state.pendingOriginals.isNotEmpty()) {
                    Text(state.lastResult, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                // "Chiedimi alla fine": the same choice as the completion notification, here too.
                val ask = state.pendingOriginals.firstOrNull()?.id
                if (!state.active && ask != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onResolveOriginal(ask, false) }) { com.cripta.app.ui.components.ButtonText(str(R.string.keep), color = on) }
                        com.cripta.app.ui.components.DestructiveButton(str(R.string.n_delete_original), onClick = { onResolveOriginal(ask, true) })
                    }
                    if (state.pendingOriginals.size > 1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { state.pendingOriginals.forEach { onResolveOriginal(it.id, false) } }) { com.cripta.app.ui.components.ButtonText(str(R.string.keep_all), color = on) }
                            com.cripta.app.ui.components.DestructiveButton(str(R.string.delete_all_pl),
                                onClick = { state.pendingOriginals.forEach { onResolveOriginal(it.id, true) } })
                        }
                    }
                }
                if (state.active) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.pct / 100f }, color = on, trackColor = on.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Per video: a new file of the queue restarts the estimate.
                    com.cripta.app.ui.components.rememberEta(state.pct / 100f, state.active, key = state.currentName)?.let {
                        Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.labelSmall,
                            color = on.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

/**
 * One-line summary of what is on screen. Tap the counter for the full breakdown; tap a type count
 * to show only that type (tap again to show everything).
 */
@Composable
internal fun StatsStrip(stats: VaultStats, filtering: Boolean, type: TypeFilter, onOpen: () -> Unit, onType: (TypeFilter) -> Unit) {
    val shown = stats.shown
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val lead = if (filtering || stats.filtered) str(R.string.x_of_y, shown.total, stats.scope.total)
            else plural(R.plurals.items_n, shown.total)
        Text(lead, style = MaterialTheme.typography.labelLarge,
            color = if (filtering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.minimumInteractiveComponentSize().clip(MaterialTheme.shapes.small).clickable(onClickLabel = str(R.string.summary), onClick = onOpen).padding(horizontal = 6.dp, vertical = 4.dp))
        CountPill(CriptaIcons.Movie, shown.videos, str(R.string.videos_lower), type == TypeFilter.VIDEO) { onType(TypeFilter.VIDEO) }
        CountPill(CriptaIcons.Image, shown.images, str(R.string.photos_lower), type == TypeFilter.IMAGE) { onType(TypeFilter.IMAGE) }
        if (shown.others > 0 || type == TypeFilter.OTHER) {
            CountPill(CriptaIcons.InsertDriveFile, shown.others, str(R.string.other_files_lower), type == TypeFilter.OTHER) { onType(TypeFilter.OTHER) }
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onOpen, modifier = Modifier.minimumInteractiveComponentSize().size(28.dp)) {
            Icon(CriptaIcons.ExpandMore, str(R.string.summary), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CountPill(icon: ImageVector, count: Int, what: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.minimumInteractiveComponentSize().clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics(mergeDescendants = true) { this.contentDescription = str(R.string.count_show_only, count, what, what) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.padding(start = 3.dp))
    }
}

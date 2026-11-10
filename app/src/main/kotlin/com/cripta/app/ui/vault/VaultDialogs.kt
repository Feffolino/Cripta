package com.cripta.app.ui.vault

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import com.cripta.app.ui.theme.CriptaIcons
import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.tagicons.TagIconChoices
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.cripta.app.ui.components.Button
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.cripta.app.data.FolderStat
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.ui.components.formatBytes
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToFolderDialog(folders: List<FolderEntity>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val byId = remember(folders) { folders.associateBy { it.id } }
    fun depth(f: FolderEntity): Int {
        var d = 0; var p = f.parentId; var guard = 0
        while (p != null && guard < 50) { d++; p = byId[p]?.parentId; guard++ }
        return d
    }
    /** "A / B" for the folders above [f]; null at the top level. */
    fun parentPath(f: FolderEntity): String? {
        val names = ArrayList<String>()
        var p = f.parentId; var guard = 0
        while (p != null && guard++ < 50) { val parent = byId[p] ?: break; names.add(0, parent.name); p = parent.parentId }
        return names.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Text(str(R.string.move_to), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            Row(
                Modifier.fillMaxWidth().clickable { afterSheet { onPick(null) } }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CriptaIcons.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Text(str(R.string.root), Modifier.padding(start = 16.dp))
            }
            folders.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { afterSheet { onPick(f.id) } }
                        .padding(horizontal = 20.dp, vertical = 12.dp).padding(start = (12 * depth(f)).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(CriptaIcons.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(f.name)
                        // Two folders can share a name: the path above this one tells them apart.
                        parentPath(f)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Lets the user pick which frame of the selected video(s) becomes the new cover. */
@Composable
internal fun CoverOptionDialog(
    count: Int,
    onPick: (ThumbnailLoader.VideoCover) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ThumbnailLoader.VideoCover.AUTO to str(R.string.cover_auto_best),
        ThumbnailLoader.VideoCover.START to str(R.string.start),
        ThumbnailLoader.VideoCover.MIDDLE to str(R.string.middle),
        ThumbnailLoader.VideoCover.END to str(R.string.end),
        ThumbnailLoader.VideoCover.RANDOM to str(R.string.random_frame),
    )
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) str(R.string.regenerate_cover) else str(R.string.regenerate_cover_n, count)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    str(R.string.regenerate_cover_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                options.forEach { (cover, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(cover) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(CriptaIcons.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

@Composable
internal fun FolderStyleDialog(
    folderName: String,
    initialColor: Int?,
    initialIcon: String?,
    onConfirm: (Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(initialColor) }
    var icon by remember { mutableStateOf(initialIcon) }
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.customise_folder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(str(R.string.colour), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ColorSwatch(null, color == null, str(R.string.no_colour)) { color = null }
                    com.cripta.app.ui.components.FOLDER_COLORS.forEachIndexed { i, c ->
                        ColorSwatch(c, color == c, FOLDER_COLOR_NAMES.getOrElse(i) { str(R.string.colour_n, i + 1) }) { color = c }
                    }
                }
                Text(str(R.string.icon), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TagIconChoices(folderName, icon, 220.dp) { icon = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color, icon) }) { com.cripta.app.ui.components.ButtonText(str(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

/** Spoken names of [com.cripta.app.ui.components.FOLDER_COLORS], same order. */
private val FOLDER_COLOR_NAMES get() = listOf(
    R.string.colour_blue, R.string.colour_green, R.string.colour_amber, R.string.colour_red,
    R.string.colour_purple, R.string.colour_teal, R.string.colour_pink, R.string.colour_grey,
).map { str(it) }

/** Colour choice: 48dp target, radio semantics with its name, a check (not only a ring) when chosen. */
@Composable
private fun ColorSwatch(color: Int?, selected: Boolean, name: String, onClick: () -> Unit) {
    val fill = color?.let { Color(it) } ?: MaterialTheme.colorScheme.surface
    Box(
        Modifier.minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                CircleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { this.contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        when {
            color == null -> Icon(CriptaIcons.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            selected -> Icon(CriptaIcons.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun SheetAction(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    // The sheet slides away first, then the action runs (a dialog, opening the folder…).
    val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
    Row(
        Modifier.fillMaxWidth().clickable { afterSheet(onClick) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, Modifier.padding(start = 16.dp), color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Full-screen-ish breakdown: how many videos (and other media) are loaded, filtered or not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsSheet(stats: VaultStats, filtering: Boolean, folderName: String?, subtree: com.cripta.app.data.FolderStat? = null, onDismiss: () -> Unit) {
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss, wideInLandscape = true) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val scopeLabel = when {
                filtering -> str(R.string.whole_vault)
                folderName != null -> str(R.string.folder_named, folderName)
                else -> str(R.string.vault_root)
            }
            Column {
                Text(str(R.string.summary), style = MaterialTheme.typography.headlineSmall)
                Text(if (filtering) str(R.string.filtered_results_in, scopeLabel) else scopeLabel,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The tiles below count this folder only; show the whole subtree too when it differs.
                if (!filtering && subtree != null && subtree.count != stats.scope.total) {
                    Text(str(R.string.with_subfolders, subtree.count, com.cripta.app.ui.components.formatBytes(subtree.bytes)),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            // The video count leads (the number people care about most), large; every other figure
            // is one line, label left and number right, so 4-5 digit counts never get cut.
            val fmt: (Number) -> String = { com.cripta.app.ui.components.formatCount(it) }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(CriptaIcons.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text(fmt(stats.shown.videos), style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(start = 12.dp))
                }
                Text(
                    when {
                        stats.filtered -> str(R.string.videos_shown_of, fmt(stats.scope.videos))
                        stats.shown.videos == 1 -> str(R.string.video_loaded)
                        else -> str(R.string.videos_loaded)
                    } + (com.cripta.app.ui.components.formatDuration(stats.shown.videoDurationMs.takeIf { it > 0 })
                        ?.let { str(R.string.total_length_sep, it) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (stats.filtered && stats.scope.videos > 0) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { stats.shown.videos.toFloat() / stats.scope.videos },
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
            Column {
                val of: (Int) -> String? = { if (stats.filtered) fmt(it) else null }
                com.cripta.app.ui.components.CountRow(str(R.string.photos), fmt(stats.shown.images), of = of(stats.scope.images), icon = CriptaIcons.Image)
                com.cripta.app.ui.components.CountRow(str(R.string.other_files), fmt(stats.shown.others), of = of(stats.scope.others),
                    icon = CriptaIcons.InsertDriveFile)
                com.cripta.app.ui.components.CountRow(str(R.string.total), fmt(stats.shown.total), of = of(stats.scope.total), icon = CriptaIcons.GridView)
                com.cripta.app.ui.components.CountRow(str(R.string.space_used), formatBytes(stats.shown.bytes),
                    of = if (stats.filtered) formatBytes(stats.scope.bytes) else null, icon = CriptaIcons.EnhancedEncryption)
            }
            if (!filtering) {
                Text(str(R.string.summary_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** What to do with one import that matches a vault file (DS Import chips). */
private enum class ConflictChoice { REPLACE, KEEP, REMOVE }

/**
 * DS "Già nel vault": every import that matches a vault file, one row each (cover, name, why) with
 * its choice as chips, then one filled button for all. Identical copies: remove the new one or keep
 * both. Same name or look-alike: replace (the new one takes labels, folder, favourite and place),
 * keep both, or remove the new one.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ImportConflictsPage(
    matches: List<VaultRepository.VersionMatch>,
    trashEnabled: Boolean,
    thumbOf: suspend (String) -> android.graphics.Bitmap?,
    onApply: (replace: List<VaultRepository.VersionMatch>, keep: List<VaultRepository.VersionMatch>, remove: List<VaultRepository.VersionMatch>) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember { androidx.compose.runtime.mutableStateMapOf<String, ConflictChoice>() }
    fun choiceOf(m: VaultRepository.VersionMatch) = choices[m.newId] ?: when {
        m.identical -> ConflictChoice.REMOVE
        m.similar || m.previousIds.size > 1 -> ConflictChoice.KEEP
        else -> ConflictChoice.REPLACE
    }
    com.cripta.app.ui.components.CriptaDialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { com.cripta.app.ui.components.PageTitle(str(R.string.dl_already_in_vault)) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(CriptaIcons.Close, str(R.string.close_keep_all)) } })
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Text(plural(R.plurals.conflict_head, matches.size) + (if (trashEnabled) str(R.string.old_to_trash) else "."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(matches.size, key = { matches[it].newId }) { i ->
                        val m = matches[i]
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val bmp by produceState<android.graphics.Bitmap?>(null, m.newId) { value = thumbOf(m.newId) }
                                Box(Modifier.size(48.dp).clip(com.cripta.app.ui.theme.CoverShapeSmall).background(MaterialTheme.colorScheme.surfaceContainer)) {
                                    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                }
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text(m.newName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(when {
                                        m.identical -> str(R.string.identical_to, m.previousName)
                                        m.similar -> str(R.string.similar_to, m.previousName)
                                        m.previousIds.size > 1 -> str(R.string.same_name_n, m.previousIds.size)
                                        else -> str(R.string.same_name)
                                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            val options = buildList {
                                if (!m.identical) add(ConflictChoice.REPLACE to if (m.previousIds.size > 1) str(R.string.take_tags) else str(R.string.replace))
                                add(ConflictChoice.KEEP to str(R.string.convert_keep_both))
                                add(ConflictChoice.REMOVE to str(R.string.remove))
                            }
                            com.cripta.app.ui.components.ChipFlowRow(Modifier.padding(top = 10.dp)) {
                                options.forEach { (c, label) ->
                                    com.cripta.app.ui.components.ChoiceChip(selected = choiceOf(m) == c, onClick = { choices[m.newId] = c }, label = { Text(label) })
                                }
                            }
                        }
                    }
                }
                Button(onClick = {
                    val by = matches.groupBy { choiceOf(it) }
                    onApply(by[ConflictChoice.REPLACE].orEmpty(), by[ConflictChoice.KEEP].orEmpty(), by[ConflictChoice.REMOVE].orEmpty())
                }, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    com.cripta.app.ui.components.ButtonText(plural(R.plurals.apply_to_files, matches.size))
                }
            }
        }
    }
}

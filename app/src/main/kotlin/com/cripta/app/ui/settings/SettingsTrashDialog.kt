package com.cripta.app.ui.settings

import com.cripta.app.plural
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.cripta.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Selection mark over a trash cell: a filled tick when selected, an empty ring while selecting. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.TrashSelectionMark(selecting: Boolean, selected: Boolean) {
    if (!selecting) return
    if (selected) {
        // DS cr-cover.is-selected: a 3dp accent ring on the cover's edge.
        Box(Modifier.matchParentSize().clip(com.cripta.app.ui.theme.CoverShape)
            .border(3.dp, MaterialTheme.colorScheme.primary, com.cripta.app.ui.theme.CoverShape))
    }
    Box(
        Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
            .then(if (selected) Modifier else Modifier.border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(CriptaIcons.Check, str(R.string.selected_m), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/**
 * Trash as a full screen browsable like the vault: folder mosaics and file covers in a grid.
 * Deleted folders open to show what they held, so a single file can be restored, and it reappears
 * at its original path. A tap on a file (or a folder's ⋮) opens its details with "Ripristina" and
 * "Elimina definitivamente"; a long-press starts selecting several, to restore or destroy just
 * those; "Svuota" empties everything.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun TrashDialog(
    items: List<com.cripta.app.data.db.FileEntity>,
    folders: List<com.cripta.app.data.db.FolderEntity>,
    days: Int,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRestoreFolder: (Long) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onEmpty: () -> Unit,
    onRestoreMany: (List<String>, List<Long>) -> Unit,
    onDeleteMany: (List<String>, List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    // Selection (long-press to start, then taps add / remove), within the level being shown.
    var selFiles by remember { mutableStateOf(emptySet<String>()) }
    var selFolders by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDeleteSel by remember { mutableStateOf(false) }
    var confirmFolder by remember { mutableStateOf<com.cripta.app.data.db.FolderEntity?>(null) }
    var confirmFile by remember { mutableStateOf<com.cripta.app.data.db.FileEntity?>(null) }
    var fileSheet by remember { mutableStateOf<com.cripta.app.data.db.FileEntity?>(null) }
    var folderSheet by remember { mutableStateOf<com.cripta.app.data.db.FolderEntity?>(null) }
    // Path of opened trashed folders (empty = top level of the trash).
    val path = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val fmt = remember(com.cripta.app.Strings.locale) { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.cripta.app.Strings.locale) }
    val trashedIds = remember(folders) { folders.map { it.id }.toSet() }
    val byId = remember(folders) { folders.associateBy { it.id } }
    // A restored/destroyed folder disappears: drop it (and what's below) from the path.
    val openId = path.lastOrNull()?.takeIf { it in trashedIds }
    if (path.isNotEmpty() && openId == null) { androidx.compose.runtime.SideEffect { path.clear() } }
    // Top level: trashed folders/files whose parent is not itself in the trash. Inside: direct content.
    val shownFolders = folders.filter { if (openId == null) it.parentId == null || it.parentId !in trashedIds else it.parentId == openId }
    val shownFiles = items.filter { if (openId == null) it.folderId == null || it.folderId !in trashedIds else it.folderId == openId }
    fun left(at: Long?) = at?.let { days - ((System.currentTimeMillis() - it) / 86_400_000L).toInt() } ?: days
    fun leftText(at: Long?): String { val l = left(at); return if (l <= 1) str(R.string.destroyed_today) else str(R.string.days_left, l) }
    fun subtree(id: Long): Set<Long> {
        val ids = HashSet<Long>(); val todo = ArrayDeque(listOf(id))
        while (todo.isNotEmpty()) { val x = todo.removeFirst(); if (ids.add(x)) folders.filter { it.parentId == x }.forEach { todo.add(it.id) } }
        return ids
    }
    fun filesUnder(id: Long): List<com.cripta.app.data.db.FileEntity> = subtree(id).let { ids -> items.filter { it.folderId in ids } }
    /** Elements inside a trashed folder: its files and its subfolders, at every depth. */
    fun elementsUnder(id: Long): Int = subtree(id).let { ids -> items.count { it.folderId in ids } + ids.size - 1 }
    // Only what is still here and shown counts (restored / destroyed meanwhile, or another level).
    val shownFileIds = shownFiles.map { it.id }.toSet()
    val shownFolderIds = shownFolders.map { it.id }.toSet()
    val selF = selFiles intersect shownFileIds
    val selD = selFolders intersect shownFolderIds
    val inSel = selF.isNotEmpty() || selD.isNotEmpty()
    fun clearSel() { selFiles = emptySet(); selFolders = emptySet() }
    // Opening or leaving a folder starts over.
    LaunchedEffect(openId) { clearSel() }
    /** Elements a selection destroys: the selected files, the selected folders and all they hold. */
    val selElements = selF.size + selD.sumOf { elementsUnder(it) + 1 }

    com.cripta.app.ui.components.CriptaDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        androidx.activity.compose.BackHandler {
            when {
                inSel -> clearSel()
                path.isNotEmpty() -> path.removeAt(path.lastIndex)
                else -> onDismiss()
            }
        }
        // Its own full-screen window: the camera cutout (a side one in landscape) is not kept clear
        // by the app's usual insets here, and covered the back arrow, the title and the first
        // covers. safeDrawing = system bars + cutout (+ keyboard).
        val safe = androidx.compose.foundation.layout.WindowInsets.safeDrawing
        androidx.compose.material3.Scaffold(
            contentWindowInsets = safe,
            topBar = {
                androidx.compose.material3.TopAppBar(
                    // DS: while selecting, the bar lifts one tier (surface-raised).
                    colors = if (inSel) androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer) else com.cripta.app.ui.components.criptaTopBarColors(),
                    windowInsets = safe.only(
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                            androidx.compose.foundation.layout.WindowInsetsSides.Top
                    ),
                    navigationIcon = {
                        if (inSel) {
                            IconButton(onClick = { clearSel() }) { Icon(CriptaIcons.Close, str(R.string.exit_selection)) }
                        } else {
                            IconButton(onClick = { if (path.isNotEmpty()) path.removeAt(path.lastIndex) else onDismiss() }) {
                                Icon(CriptaIcons.ArrowBack, if (path.isNotEmpty()) str(R.string.back) else str(R.string.close_trash))
                            }
                        }
                    },
                    title = {
                        if (inSel) {
                            val n = selF.size + selD.size
                            com.cripta.app.ui.components.RollingCountText(n) { plural(R.plurals.n_selected_m, it) }
                        } else Column {
                            Text(if (openId == null) str(R.string.sec_trash) else byId[openId]?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // The top level says its count and rule in the page (DS); a folder shows where it was.
                            if (openId != null) Text((listOf(str(R.string.sec_trash)) + path.dropLast(1).mapNotNull { byId[it]?.name }).joinToString(" › "),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    actions = {
                        if (inSel) {
                            val all = selF.size == shownFileIds.size && selD.size == shownFolderIds.size
                            IconButton(onClick = {
                                if (all) clearSel() else { selFiles = shownFileIds; selFolders = shownFolderIds }
                            }) {
                                Icon(CriptaIcons.SelectAll, if (all) str(R.string.deselect_all) else str(R.string.select_all),
                                    tint = if (all) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current)
                            }
                        } else if (openId != null) {
                            TextButton(onClick = { onRestoreFolder(openId) }) { com.cripta.app.ui.components.ButtonText(str(R.string.restore_all)) }
                        }
                    },
                )
            },
            bottomBar = {
                // The trash's actions sit at the bottom, always at the same height (the main buttons'
                // 56dp, as in the faces' pages): "Svuota il cestino", or those of the selection.
                val top = openId == null && (items.isNotEmpty() || folders.isNotEmpty())
                if (inSel || top) {
                    // DS cr-bottombar: surface-raised, both buttons 56dp, the destructive one tonal in danger.
                    androidx.compose.material3.BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        windowInsets = safe.only(
                            androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                                androidx.compose.foundation.layout.WindowInsetsSides.Bottom
                        ),
                    ) {
                        if (!inSel) com.cripta.app.ui.components.DestructiveButton(str(R.string.empty_trash), onClick = { confirmEmpty = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), icon = CriptaIcons.Delete)
                        else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { confirmDeleteSel = true },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp), icon = CriptaIcons.Delete)
                            com.cripta.app.ui.components.FilledTonalButton(onClick = { onRestoreMany(selF.toList(), selD.toList()); clearSel() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                Icon(CriptaIcons.RestoreFromTrash, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                com.cripta.app.ui.components.ButtonText(str(R.string.restore))
                            }
                        }
                    }
                }
            },
        ) { pad ->
            if (shownFolders.isEmpty() && shownFiles.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(CriptaIcons.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Text(if (openId == null) str(R.string.trash_is_empty) else str(R.string.nothing_else_folder),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp))
                    }
                }
            } else {
                val phone = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = if (phone) androidx.compose.foundation.lazy.grid.GridCells.Fixed(3)
                        else androidx.compose.foundation.lazy.grid.GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (openId == null) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        // DS Cestino: the rule in one line, then what the trash holds.
                        Column {
                            Text(str(R.string.trash_days_hint, days),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            com.cripta.app.ui.components.CountRow(str(R.string.in_trash), com.cripta.app.ui.components.formatCount(items.size + folders.size),
                                after = (if (items.size + folders.size == 1) " elemento" else " elementi") + " · " +
                                    com.cripta.app.ui.components.formatBytes(items.sumOf { it.sizeBytes }),
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    items(shownFolders.size, key = { "d" + shownFolders[it].id }) { i ->
                        val fo = shownFolders[i]
                        val under = filesUnder(fo.id)
                        val selected = fo.id in selD
                        fun toggle() { selFolders = if (selected) selFolders - fo.id else selFolders + fo.id }
                        Column {
                            Box {
                                com.cripta.app.ui.vault.FolderMosaic(
                                    folder = fo,
                                    stat = com.cripta.app.data.FolderStat(elementsUnder(fo.id), under.sumOf { it.sizeBytes }),
                                    previews = under.sortedByDescending { it.importedAt }.take(4),
                                    thumb = thumb,
                                    // Tap: its details and actions (restore, delete, open), like a file here.
                                    modifier = Modifier.combinedClickable(
                                        onClickLabel = if (inSel) (if (selected) str(R.string.deselect) else str(R.string.select)) else str(R.string.details),
                                        onLongClickLabel = str(R.string.select),
                                        onClick = { if (inSel) toggle() else folderSheet = fo }, onLongClick = { toggle() },
                                    ),
                                )
                                TrashSelectionMark(inSel, selected)
                            }
                            Text(leftText(fo.deletedAt), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                    items(shownFiles.size, key = { "f" + shownFiles[it].id }) { i ->
                        val f = shownFiles[i]
                        // The rounded clip bounds the touch ripple; the bottom padding keeps the two text
                        // lines out of its lower corners, which used to shave off their first/last letters.
                        val selected = f.id in selF
                        fun toggle() { selFiles = if (selected) selFiles - f.id else selFiles + f.id }
                        Column(Modifier.clip(com.cripta.app.ui.theme.CoverShape)
                            .combinedClickable(
                                onClickLabel = if (inSel) (if (selected) str(R.string.deselect) else str(R.string.select)) else str(R.string.details),
                                onLongClickLabel = str(R.string.select),
                                onClick = { if (inSel) toggle() else fileSheet = f }, onLongClick = { toggle() },
                            )
                            .padding(bottom = 8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(com.cripta.app.ui.theme.CoverShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                                val b = bmp
                                if (b != null) {
                                    androidx.compose.foundation.Image(b.asImageBitmap(), f.originalName,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(if (com.cripta.app.data.VaultRepository.isVideo(f.mimeType)) CriptaIcons.Movie
                                        else CriptaIcons.InsertDriveFile, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                }
                                if (!inSel) com.cripta.app.ui.components.formatDuration(f.durationMs)?.let {
                                    com.cripta.app.ui.components.CoverBadge(it, Modifier.align(Alignment.TopEnd).padding(6.dp))
                                }
                                TrashSelectionMark(inSel, selected)
                            }
                            Text(f.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                            Text("${com.cripta.app.ui.components.formatBytes(f.sizeBytes)} · ${leftText(f.deletedAt)}",
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
        }

        // ---- details + actions of a file
        fileSheet?.let { f ->
            com.cripta.app.ui.components.CriptaSheet(onDismissRequest = { fileSheet = null }) {
                val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(f.originalName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val where = f.folderId?.let { id ->
                        // Original path: trashed folders by name (live ones are not in this list).
                        generateSequence(byId[id]) { it.parentId?.let(byId::get) }.map { it.name }.toList().reversed()
                            .joinToString(" › ").ifEmpty { null }
                    } ?: str(R.string.vault)
                    listOfNotNull(
                        str(R.string.size_label) to com.cripta.app.ui.components.formatBytes(f.sizeBytes),
                        com.cripta.app.ui.components.formatDuration(f.durationMs)?.let { str(R.string.duration) to it },
                        if (f.width != null && f.height != null) str(R.string.resolution) to "${f.width}×${f.height}" else null,
                        str(R.string.location) to where,
                        str(R.string.deleted_on_m) to (f.deletedAt?.let { fmt.format(java.util.Date(it)) } ?: "—"),
                        str(R.string.destruction) to leftText(f.deletedAt).replaceFirstChar { it.uppercase() },
                    ).forEach { (k, v) -> InfoRow(k, v) }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Asked first, as for folders and several files: this destroys it for good.
                        com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { afterSheet { confirmFile = f } },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp))
                        Button(onClick = { afterSheet { onRestore(f.id) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.restore)) }
                    }
                    if (f.folderId != null && f.folderId in trashedIds) {
                        Text(str(R.string.restore_to_folder_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // ---- details + actions of a folder
        folderSheet?.let { fo ->
            val under = filesUnder(fo.id)
            com.cripta.app.ui.components.CriptaSheet(onDismissRequest = { folderSheet = null }) {
                val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fo.name, style = MaterialTheme.typography.titleMedium)
                    InfoRow(str(R.string.contents), elementsLabel(elementsUnder(fo.id)) +
                        " · " + com.cripta.app.ui.components.formatBytes(under.sumOf { it.sizeBytes }))
                    InfoRow(str(R.string.deleted_on_f), fo.deletedAt?.let { fmt.format(java.util.Date(it)) } ?: "—")
                    InfoRow(str(R.string.destruction), leftText(fo.deletedAt).replaceFirstChar { it.uppercase() })
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { afterSheet { confirmFolder = fo } },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp))
                        Button(onClick = { afterSheet { onRestoreFolder(fo.id) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.restore)) }
                    }
                    TextButton(onClick = { afterSheet { path.add(fo.id) } }, modifier = Modifier.fillMaxWidth()) { com.cripta.app.ui.components.ButtonText(str(R.string.open_choose_items)) }
                }
            }
        }
        confirmFile?.let { cf ->
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmFile = null },
                title = { Text(str(R.string.delete_forever_q)) },
                text = { Text(str(R.string.file_destroy_secure, cf.originalName)) },
                confirmButton = {
                    TextButton(onClick = { onDelete(cf.id); confirmFile = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmFile = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
        confirmFolder?.let { fo ->
            val n = elementsUnder(fo.id)
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmFolder = null },
                title = { Text(str(R.string.delete_forever_q)) },
                text = {
                    Text(if (n == 0) str(R.string.folder_destroy_secure, fo.name)
                        else str(R.string.folder_items_destroy, fo.name, elementsLabel(n)))
                },
                confirmButton = {
                    TextButton(onClick = { onDeleteFolder(fo.id); confirmFolder = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmFolder = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
        if (confirmDeleteSel && inSel) {
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmDeleteSel = false },
                title = { Text(str(R.string.delete_forever_q)) },
                text = {
                    Text(plural(R.plurals.items_destroyed, selElements, elementsLabel(selElements)))
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteMany(selF.toList(), selD.toList()); clearSel(); confirmDeleteSel = false
                    }) { Text(str(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDeleteSel = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
        if (confirmEmpty) {
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmEmpty = false },
                title = { Text(str(R.string.empty_trash_q)) },
                text = {
                    val total = items.size + folders.size
                    Text(plural(R.plurals.items_destroyed, total, elementsLabel(total)))
                },
                confirmButton = {
                    TextButton(onClick = { onEmpty(); confirmEmpty = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.empty_short), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmEmpty = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
    }
}

package com.cripta.app.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.cripta.app.data.DisplayPrefs
import com.cripta.app.data.FolderStat
import com.cripta.app.data.SortKey
import com.cripta.app.data.ViewMode
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.components.fileMeta
import com.cripta.app.ui.components.formatBytes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun typeLabel(t: TypeFilter) = when (t) {
    TypeFilter.ALL -> "Tutti"
    TypeFilter.IMAGE -> "Immagini"
    TypeFilter.VIDEO -> "Video"
    TypeFilter.OTHER -> "Altro"
}

fun tagAlias(tag: TagEntity): String =
    tag.alias?.takeIf { it.isNotBlank() } ?: tag.name.take(2).uppercase(Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    onOpenFile: (String) -> Unit,
    onNewNote: () -> Unit = {},
    vm: VaultViewModel = hiltViewModel(),
) {
    val folders by vm.folders.collectAsState()
    val files by vm.files.collectAsState()
    val tags by vm.tags.collectAsState()
    val filters by vm.filters.collectAsState()
    val path by vm.path.collectAsState()
    val pendingOriginals by vm.pendingOriginals.collectAsState()
    val viewMode by vm.viewMode.collectAsState()
    val gridColumns by vm.gridColumns.collectAsState()
    val allFolders by vm.allFolders.collectAsState()
    val sortKey by vm.sortKey.collectAsState()
    val sortAscending by vm.sortAscending.collectAsState()
    val folderStats by vm.folderStats.collectAsState()
    val display by vm.display.collectAsState()

    var selection by remember { mutableStateOf(setOf<String>()) }
    // Swipe/range multi-select (gallery-style) drag state.
    val gridState = rememberLazyGridState()
    var dragAnchor by remember { mutableStateOf<String?>(null) }
    var dragBase by remember { mutableStateOf(setOf<String>()) }
    var dragDeselect by remember { mutableStateOf(false) }
    var hoverFolder by remember { mutableStateOf<Long?>(null) }
    var selPointer by remember { mutableStateOf(Offset.Zero) }
    var batchTag by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var tagTargetId by remember { mutableStateOf<String?>(null) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var folderMenu by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToStyle by remember { mutableStateOf<FolderEntity?>(null) }
    var confirmMultiDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Collapse the secondary FABs while scrolling down so they don't cover the content
    // (they otherwise block dragging items in Manual reorder); bring them back on scroll up.
    var fabsVisible by remember { mutableStateOf(true) }
    val fabScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -3f) fabsVisible = false
                else if (available.y > 3f) fabsVisible = true
                return Offset.Zero
            }
        }
    }

    BackHandler(enabled = selection.isNotEmpty() || filters.active || path.isNotEmpty()) {
        when {
            selection.isNotEmpty() -> selection = emptySet()
            filters.active -> vm.clearFilters()
            path.isNotEmpty() -> vm.goUp()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importThenHandleOriginals(uris) }

    val inSelection = selection.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (inSelection) {
                        Text("${selection.size} selezionati", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    } else {
                        com.cripta.app.ui.components.HeaderTitle(path.lastOrNull()?.name ?: "Cripta")
                    }
                },
                navigationIcon = {
                    if (inSelection) {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.ArrowBack, "Annulla") }
                    } else if (path.isNotEmpty()) {
                        IconButton(onClick = { vm.goUp() }) { Icon(Icons.Filled.ArrowBack, "Su") }
                    }
                },
                actions = {
                    if (inSelection) {
                        val allFav = files.filter { it.file.id in selection }.all { it.file.isFavorite }
                        IconButton(onClick = { vm.setFavorite(selection.toList(), !allFav); selection = emptySet() }) {
                            Icon(Icons.Filled.Star, if (allFav) "Rimuovi preferito" else "Aggiungi preferito")
                        }
                        IconButton(onClick = { confirmMultiDelete = true }) {
                            Icon(Icons.Filled.Delete, "Elimina")
                        }
                        var selMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { selMenu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                        DropdownMenu(expanded = selMenu, onDismissRequest = { selMenu = false }) {
                            DropdownMenuItem(text = { Text("Sposta") }, onClick = { selMenu = false; showMove = true })
                            DropdownMenuItem(
                                text = { Text(if (selection.size == 1) "Etichette" else "Etichette (${selection.size})") },
                                onClick = {
                                    selMenu = false
                                    if (selection.size == 1) tagTargetId = selection.first() else batchTag = true
                                },
                            )
                            if (selection.size == 1) {
                                DropdownMenuItem(text = { Text("Rinomina") }, onClick = { selMenu = false; renameTargetId = selection.first() })
                            }
                        }
                    } else {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Filled.Tune, "Filtri e ordinamento",
                                tint = if (filters.active) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        if (viewMode == ViewMode.GRID) {
                            IconButton(onClick = { vm.setGridColumns(if (gridColumns >= 5) 2 else gridColumns + 1) }) {
                                Icon(Icons.Filled.ViewModule, "Dimensione griglia")
                            }
                        }
                        IconButton(onClick = { vm.setViewMode(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }) {
                            Icon(if (viewMode == ViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView, "Vista")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!inSelection) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(
                        visible = fabsVisible,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (display.showNoteFab) {
                                SmallFloatingActionButton(onClick = onNewNote) {
                                    Icon(Icons.Filled.Description, "Nuova nota")
                                }
                            }
                            SmallFloatingActionButton(onClick = { showNewFolder = true }) {
                                Icon(Icons.Filled.CreateNewFolder, "Nuova cartella")
                            }
                            if (display.showRandomFab) {
                                SmallFloatingActionButton(onClick = { vm.randomShuffleOpen(onOpenFile) }) {
                                    Icon(Icons.Filled.Casino, "Casuale")
                                }
                            }
                        }
                    }
                    FloatingActionButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.EnhancedEncryption, "Cifra e importa")
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).nestedScroll(fabScroll)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = vm::setQuery,
                label = { Text("Cerca nome o tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            ActiveFilterBar(filters, tags, vm::setType, { vm.setFavoritesOnly(false) },
                { vm.setUntaggedOnly(false) }, vm::toggleTag, vm::clearFilters)

            val manual = sortKey == SortKey.MANUAL
            val showFolders = folders.isNotEmpty() && !filters.active && !manual
            val columns = if (viewMode == ViewMode.GRID) GridCells.Fixed(gridColumns) else GridCells.Fixed(1)
            val grouped = remember(files, display.showDateHeaders) {
                if (display.showDateHeaders) groupByDay(files) else listOf("" to files)
            }
            val orderedIds = remember(grouped) { grouped.flatMap { it.second.map { f -> f.file.id } } }
            val idSet = remember(orderedIds) { orderedIds.toSet() }
            val inSelState = rememberUpdatedState(inSelection)

            fun toggleSel(id: String) {
                selection = if (id in selection) selection - id else selection + id
            }

            when {
                folders.isEmpty() && files.isEmpty() ->
                    EmptyState(filters.active, Modifier.fillMaxSize())
                manual ->
                    ReorderableFileGrid(
                        items = files,
                        columns = if (viewMode == ViewMode.GRID) gridColumns else 1,
                        asList = viewMode == ViewMode.LIST,
                        selection = selection,
                        display = display,
                        thumb = { vm.thumb(it) },
                        onOpen = { vm.publishViewerQueue(); onOpenFile(it) },
                        onReorder = { vm.reorder(it) },
                    )
                else -> LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                        // Tap: open a file (or toggle it in selection mode), open a folder.
                        .pointerInput(orderedIds, inSelection) {
                            detectTapGestures(onTap = { off ->
                                val key = keyAt(off, gridState) as? String ?: return@detectTapGestures
                                if (key.startsWith("f-")) {
                                    folders.firstOrNull { "f-${it.id}" == key }?.let { vm.enterFolder(it) }
                                } else if (key in idSet) {
                                    if (inSelection) toggleSel(key) else { vm.publishViewerQueue(); onOpenFile(key) }
                                }
                            })
                        }
                        // Long-press a file then drag = gallery-style range select (or deselect if
                        // the anchor was already selected). Long-press a folder = its action menu.
                        .pointerInput(orderedIds) {
                            val onStart: (Offset) -> Unit = { off ->
                                selPointer = off
                                hoverFolder = null
                                val key = keyAt(off, gridState) as? String
                                when {
                                    key == null -> {}
                                    key.startsWith("f-") ->
                                        folders.firstOrNull { "f-${it.id}" == key }?.let { folderMenu = it }
                                    key in idSet -> {
                                        dragAnchor = key
                                        dragDeselect = key in selection
                                        dragBase = selection
                                        if (!dragDeselect) selection = selection + key
                                    }
                                }
                            }
                            val onMove: (Offset) -> Unit = { delta ->
                                selPointer += delta
                                val anchor = dragAnchor
                                if (anchor != null) {
                                    val curKey = keyAt(selPointer, gridState) as? String
                                    if (curKey != null && curKey.startsWith("f-")) {
                                        hoverFolder = folders.firstOrNull { "f-${it.id}" == curKey }?.id
                                    } else {
                                        hoverFolder = null
                                        val cur = curKey?.takeIf { it in idSet }
                                        if (cur != null) {
                                            val ai = orderedIds.indexOf(anchor)
                                            val ci = orderedIds.indexOf(cur)
                                            if (ai >= 0 && ci >= 0) {
                                                val range = orderedIds.subList(minOf(ai, ci), maxOf(ai, ci) + 1).toSet()
                                                selection = if (dragDeselect) dragBase - range else dragBase + range
                                            }
                                        }
                                    }
                                }
                            }
                            val onEnd: () -> Unit = {
                                val target = hoverFolder
                                if (target != null && selection.isNotEmpty()) {
                                    vm.moveFiles(selection.toList(), target)
                                    selection = emptySet()
                                }
                                dragAnchor = null; hoverFolder = null
                            }
                            // Long-press an item then drag to range-select; a plain long-press selects one.
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off -> onStart(off) },
                                onDrag = { _, amount -> onMove(amount) },
                                onDragEnd = { onEnd() },
                                onDragCancel = { dragAnchor = null; hoverFolder = null },
                            )
                        },
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showFolders) {
                        header("Cartelle")
                        items(folders, key = { "f-${it.id}" }, span = { GridItemSpan(1) }) { folder ->
                            FolderCell(folder, viewMode, folderStats[folder.id], display.showFolderInfo,
                                modifier = Modifier.animateItem(),
                                onOpen = { vm.enterFolder(folder) }, onLongPress = { folderMenu = folder })
                        }
                    }
                    grouped.forEach { (label, group) ->
                        if (label.isNotEmpty()) header(label)
                        items(group, key = { it.file.id }, span = { GridItemSpan(1) }) { fwt ->
                            FileCell(fwt, viewMode, fwt.file.id in selection, inSelection, display, Modifier.animateItem(),
                                { vm.thumb(fwt.file) }, { vm.publishViewerQueue(); onOpenFile(fwt.file.id) }, { toggleSel(fwt.file.id) })
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        TextPromptDialog("Nuova cartella", "Nome",
            onConfirm = { vm.createFolder(it); showNewFolder = false },
            onDismiss = { showNewFolder = false })
    }

    renameTargetId?.let { id ->
        val current = files.firstOrNull { it.file.id == id }?.file?.originalName ?: ""
        TextPromptDialog("Rinomina file", "Nome", initial = current,
            onConfirm = { vm.renameFile(id, it); renameTargetId = null; selection = emptySet() },
            onDismiss = { renameTargetId = null })
    }

    tagTargetId?.let { id ->
        val target = files.firstOrNull { it.file.id == id }
        TagEditorDialog(
            allTags = tags,
            initialSelected = target?.tags?.map { it.name } ?: emptyList(),
            onConfirm = { vm.setTags(id, it); tagTargetId = null; selection = emptySet() },
            onSetAlias = { name, alias -> vm.setTagAlias(name, alias) },
            onCreateTag = { name, alias -> vm.createTag(name, alias) },
            onDismiss = { tagTargetId = null },
        )
    }

    if (batchTag) {
        BatchTagDialog(
            count = selection.size,
            allTags = tags,
            onConfirm = { names -> vm.addTagsToFiles(selection.toList(), names); batchTag = false; selection = emptySet() },
            onCreateTag = { name, alias -> vm.createTag(name, alias) },
            onDismiss = { batchTag = false },
        )
    }

    folderMenu?.let { folder ->
        ModalBottomSheet(onDismissRequest = { folderMenu = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                SheetAction(Icons.Filled.Folder, "Apri") { vm.enterFolder(folder); folderMenu = null }
                SheetAction(Icons.Filled.DriveFileRenameOutline, "Rinomina") { folderToRename = folder; folderMenu = null }
                SheetAction(Icons.Filled.Palette, "Personalizza") { folderToStyle = folder; folderMenu = null }
                SheetAction(Icons.Filled.Delete, "Elimina", destructive = true) { folderToDelete = folder; folderMenu = null }
            }
        }
    }

    folderToRename?.let { folder ->
        TextPromptDialog("Rinomina cartella", "Nome", initial = folder.name,
            onConfirm = { vm.renameFolder(folder, it); folderToRename = null },
            onDismiss = { folderToRename = null })
    }

    folderToStyle?.let { folder ->
        FolderStyleDialog(
            initialColor = folder.color,
            initialEmoji = folder.emoji,
            onConfirm = { c, e -> vm.setFolderStyle(folder.id, c, e); folderToStyle = null },
            onDismiss = { folderToStyle = null },
        )
    }

    if (showFilterSheet) {
        FilterSortSheet(
            filters = filters,
            tags = tags,
            sortKey = sortKey,
            sortAscending = sortAscending,
            onType = vm::setType,
            onFav = { vm.setFavoritesOnly(!filters.favoritesOnly) },
            onUntagged = { vm.setUntaggedOnly(!filters.untaggedOnly) },
            onTag = vm::toggleTag,
            onSort = { k, a -> vm.setSort(k, a) },
            onClear = { vm.clearFilters() },
            onDismiss = { showFilterSheet = false },
        )
    }

    if (confirmMultiDelete) {
        AlertDialog(
            onDismissRequest = { confirmMultiDelete = false },
            title = { Text("Eliminare ${selection.size} file?") },
            text = { Text("Eliminazione sicura (crypto-shredding). Irreversibile.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFiles(selection.toList()); selection = emptySet(); confirmMultiDelete = false }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmMultiDelete = false }) { Text("Annulla") } },
        )
    }

    if (showMove) {
        MoveToFolderDialog(allFolders,
            onPick = { fid -> vm.moveFiles(selection.toList(), fid); selection = emptySet(); showMove = false },
            onDismiss = { showMove = false })
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Eliminare la cartella?") },
            text = { Text("\"${folder.name}\" e tutto il suo contenuto verranno eliminati in modo sicuro. Irreversibile.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFolder(folder); folderToDelete = null }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Annulla") } },
        )
    }

    if (pendingOriginals.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.clearPendingOriginals() },
            title = { Text("Eliminare gli originali?") },
            text = { Text("${pendingOriginals.size} file importati nel vault. Eliminare le copie originali dal dispositivo? (Non è una cancellazione sicura dell'originale.)") },
            confirmButton = { TextButton(onClick = { vm.deleteOriginals(pendingOriginals) }) { Text("Elimina originali") } },
            dismissButton = { TextButton(onClick = { vm.clearPendingOriginals() }) { Text("Mantieni") } },
        )
    }
}

private fun LazyGridScope.header(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    }
}

private fun groupByDay(files: List<FileWithTags>): List<Pair<String, List<FileWithTags>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    return files.groupBy { Instant.ofEpochMilli(it.file.importedAt).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (day, list) ->
            val label = when (day) {
                today -> "Oggi"
                today.minusDays(1) -> "Ieri"
                else -> day.format(fmt)
            }
            label to list
        }
}

/** Lazy-grid item key under [pos] (viewport coords): a file id, "f-<id>" folder key, or null. */
private fun keyAt(pos: Offset, state: LazyGridState): Any? =
    state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        pos.x >= info.offset.x && pos.x <= info.offset.x + info.size.width &&
            pos.y >= info.offset.y && pos.y <= info.offset.y + info.size.height
    }?.key

private fun folderSubtitle(stat: FolderStat?): String {
    if (stat == null || stat.count == 0) return "Vuota"
    val n = stat.count
    val items = if (n == 1) "1 elemento" else "$n elementi"
    return "$items · ${formatBytes(stat.bytes)}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCell(
    folder: FolderEntity,
    viewMode: ViewMode,
    stat: FolderStat?,
    showInfo: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val subtitle = folderSubtitle(stat)
    if (viewMode == ViewMode.LIST) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.cripta.app.ui.components.FolderGlyph(folder.color, folder.emoji, 32.dp)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (showInfo) {
                        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
    ) {
        Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            com.cripta.app.ui.components.FolderGlyph(folder.color, folder.emoji, 36.dp)
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
            if (showInfo) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FileCell(
    item: FileWithTags,
    viewMode: ViewMode,
    selected: Boolean,
    selectionMode: Boolean,
    display: DisplayPrefs,
    modifier: Modifier,
    thumb: suspend () -> android.graphics.Bitmap?,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val mime = item.file.mimeType
    val isVideo = VaultRepository.isVideo(mime)
    val fallbackIcon = when {
        VaultRepository.isImage(mime) -> Icons.Filled.Image
        isVideo -> Icons.Filled.Movie
        else -> Icons.Filled.InsertDriveFile
    }
    val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, item.file.id) { value = thumb() }

    // Tap/long-press are handled by the grid container (unified gesture), not per cell.
    if (viewMode == ViewMode.LIST) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // List mode: no tag badges on the cover — tags are shown as text below the name.
                ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite,
                    emptyList(), Modifier.size(56.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (display.showFileInfo) {
                        Text(fileMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.tags.isNotEmpty()) {
                        Text(item.tags.joinToString(" ") { "#${it.name}" }, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite,
            if (display.showTagsOnCover) item.tags else emptyList(),
            Modifier.fillMaxWidth().aspectRatio(1f))
        Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        if (display.showFileInfo) {
            Text(fileMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThumbBox(
    bmp: android.graphics.Bitmap?,
    fallbackIcon: ImageVector,
    isVideo: Boolean,
    name: String,
    selected: Boolean,
    favorite: Boolean,
    tags: List<TagEntity>,
    modifier: Modifier,
) {
    val borderMod = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier
    Box(modifier.clip(MaterialTheme.shapes.medium).then(borderMod), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        Crossfade(targetState = bmp, label = "thumb") { b ->
            if (b != null) {
                Image(b.asImageBitmap(), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(fallbackIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                }
            }
        }
        // Clear video marker regardless of the thumbnail behind it.
        if (isVideo) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayCircle, "Video", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        if (favorite) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Star, "Preferito", tint = Color(0xFFFFC531), modifier = Modifier.size(18.dp))
            }
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, "Selezionato", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        if (tags.isNotEmpty()) {
            TagBadges(
                tags = tags,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(3.dp),
            )
        }
    }
}

@Composable
private fun TagBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

/**
 * Acronym/emoji tag badges sized to the thumbnail: fit as many as the available width allows on a
 * single row (measuring each alias), with a "+N" chip for the rest. Bigger thumbnails (fewer grid
 * columns) therefore show more badges; small ones show fewer. No fixed cap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagBadges(tags: List<TagEntity>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val availPx = with(density) { maxWidth.toPx() }
        val hPadPx = with(density) { 8.dp.toPx() }   // 4dp padding on each side of a badge
        val spacingPx = with(density) { 3.dp.toPx() }
        // Measured on-screen width of each badge (alias text + horizontal padding).
        val widths = remember(tags) {
            tags.map { measurer.measure(tagAlias(it), style).size.width + hPadPx }
        }

        // Greedily fit badges on one row; how many depends purely on the thumbnail width.
        fun fit(budgetPx: Float): Int {
            var used = 0f; var n = 0
            for (w in widths) {
                val add = w + if (n > 0) spacingPx else 0f
                if (used + add <= budgetPx) { used += add; n++ } else break
            }
            return n
        }

        var count = fit(availPx)
        if (count < tags.size) {
            // Some overflow -> reserve room for a "+N" chip, then refit.
            val overflowPx = measurer.measure("+${tags.size}", style).size.width + hPadPx + spacingPx
            count = fit(availPx - overflowPx).coerceAtLeast(1)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tags.take(count).forEach { TagBadge(tagAlias(it)) }
            val extra = tags.size - count
            if (extra > 0) TagBadge("+$extra")
        }
    }
}

/** Compact, at-a-glance summary of active filters with one-tap removal. Hidden when none active. */
@Composable
private fun ActiveFilterBar(
    filters: Filters,
    tags: List<TagEntity>,
    onType: (TypeFilter) -> Unit,
    onClearFav: () -> Unit,
    onClearUntagged: () -> Unit,
    onTag: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    if (!filters.active) return
    val tagById = remember(tags) { tags.associateBy { it.id } }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filters.type != TypeFilter.ALL) DismissChip(typeLabel(filters.type)) { onType(TypeFilter.ALL) }
        if (filters.favoritesOnly) DismissChip("Preferiti") { onClearFav() }
        if (filters.untaggedOnly) DismissChip("Senza etichette") { onClearUntagged() }
        filters.tagIds.forEach { id ->
            val t = tagById[id] ?: return@forEach
            DismissChip("#${t.name}") { onTag(id) }
        }
        TextButton(onClick = onClearAll) { Text("Azzera") }
    }
}

@Composable
private fun DismissChip(label: String, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.clickable(onClick = onClear).padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Icon(Icons.Filled.Close, "Rimuovi", tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

/** Unified sort + filter menu as a Material3 bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSortSheet(
    filters: Filters,
    tags: List<TagEntity>,
    sortKey: SortKey,
    sortAscending: Boolean,
    onType: (TypeFilter) -> Unit,
    onFav: () -> Unit,
    onUntagged: () -> Unit,
    onTag: (Long) -> Unit,
    onSort: (SortKey, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Ordina", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SortKey.DATE to "Data", SortKey.NAME to "Nome",
                    SortKey.SIZE to "Dimensione", SortKey.MANUAL to "Manuale",
                ).forEach { (k, lbl) ->
                    FilterChip(selected = sortKey == k, onClick = { onSort(k, sortAscending) }, label = { Text(lbl) })
                }
            }
            if (sortKey == SortKey.MANUAL) {
                Text("Trascina gli elementi in modalità Lista per riordinarli a piacere.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FilterChip(
                    selected = false,
                    onClick = { onSort(sortKey, !sortAscending) },
                    leadingIcon = { Icon(Icons.Filled.SwapVert, null) },
                    label = { Text(if (sortAscending) "Crescente" else "Decrescente") },
                )
            }

            HorizontalDivider()

            Text("Tipo", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilter.entries.forEach { t ->
                    FilterChip(selected = filters.type == t, onClick = { onType(t) }, label = { Text(typeLabel(t)) })
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Solo preferiti", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = filters.favoritesOnly, onCheckedChange = { onFav() })
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Senza etichette", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = filters.untaggedOnly, onCheckedChange = { onUntagged() })
            }

            if (tags.isNotEmpty()) {
                HorizontalDivider()
                Text("Tag", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        FilterChip(selected = tag.id in filters.tagIds, onClick = { onTag(tag.id) },
                            label = { Text(if (!tag.alias.isNullOrBlank()) "${tag.alias} #${tag.name}" else "#${tag.name}") })
                    }
                }
            }

            if (filters.active) {
                TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) { Text("Azzera filtri") }
            }
        }
    }
}

/**
 * Drag-to-reorder used by the Manual sort in BOTH grid and list. Reorder starts only after a
 * long press (so a stray tap never changes the order), then the item follows the finger and the
 * target slot is found by hit-testing the grid's layout info (works for any cell size / column count).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableFileGrid(
    items: List<FileWithTags>,
    columns: Int,
    asList: Boolean,
    selection: Set<String>,
    display: DisplayPrefs,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onOpen: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val list = remember { mutableStateListOf<FileWithTags>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) { list.clear(); list.addAll(items) } }

    val gridState = rememberLazyGridState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }    // finger position in the grid viewport
    var pressLocal by remember { mutableStateOf(Offset.Zero) } // grab point inside the cell

    Column(Modifier.fillMaxSize()) {
        Text("Ordine manuale · tieni premuto e trascina per riordinare",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(list, key = { _, it -> it.file.id }) { _, fwt ->
                val isDragged = fwt.file.id == draggedId
                val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, fwt.file.id) { value = thumb(fwt.file) }
                ReorderCell(
                    item = fwt,
                    bmp = bmp,
                    selected = fwt.file.id in selection,
                    dragged = isDragged,
                    asList = asList,
                    display = display,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .then(if (!isDragged) Modifier.animateItem() else Modifier)
                        .graphicsLayer {
                            if (isDragged) {
                                val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId }
                                if (info != null) {
                                    translationX = pointer.x - pressLocal.x - info.offset.x
                                    translationY = pointer.y - pressLocal.y - info.offset.y
                                }
                            }
                        }
                        .pointerInput(fwt.file.id) { detectTapGestures(onTap = { onOpen(fwt.file.id) }) }
                        .pointerInput(fwt.file.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { local ->
                                    draggedId = fwt.file.id
                                    dragging = true
                                    pressLocal = local
                                    val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == fwt.file.id }
                                    pointer = Offset((info?.offset?.x ?: 0) + local.x, (info?.offset?.y ?: 0) + local.y)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    pointer += amount
                                    val from = list.indexOfFirst { it.file.id == draggedId }
                                    val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                        pointer.x >= info.offset.x && pointer.x <= info.offset.x + info.size.width &&
                                            pointer.y >= info.offset.y && pointer.y <= info.offset.y + info.size.height
                                    }?.index
                                    if (from >= 0 && target != null && target != from && target < list.size) {
                                        list.add(target, list.removeAt(from))
                                    }
                                },
                                onDragEnd = { dragging = false; draggedId = null; onReorder(list.map { it.file.id }) },
                                onDragCancel = { dragging = false; draggedId = null; onReorder(list.map { it.file.id }) },
                            )
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReorderCell(
    item: FileWithTags,
    bmp: android.graphics.Bitmap?,
    selected: Boolean,
    dragged: Boolean,
    asList: Boolean,
    display: DisplayPrefs,
    modifier: Modifier,
) {
    val mime = item.file.mimeType
    val isVideo = VaultRepository.isVideo(mime)
    val fallbackIcon = when {
        VaultRepository.isImage(mime) -> Icons.Filled.Image
        isVideo -> Icons.Filled.Movie
        else -> Icons.Filled.InsertDriveFile
    }
    if (asList) {
        Surface(
            color = if (dragged) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (dragged) 8.dp else 0.dp,
            modifier = modifier.fillMaxWidth().height(72.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite, emptyList(), Modifier.size(52.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (display.showFileInfo) {
                        Text(fileMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Filled.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).size(22.dp))
            }
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            Box {
                ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite,
                    if (display.showTagsOnCover) item.tags else emptyList(),
                    Modifier.fillMaxWidth().aspectRatio(1f))
                if (dragged) {
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.DragHandle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
            if (display.showFileInfo) {
                Text(fileMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToFolderDialog(folders: List<FolderEntity>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val byId = remember(folders) { folders.associateBy { it.id } }
    fun depth(f: FolderEntity): Int {
        var d = 0; var p = f.parentId; var guard = 0
        while (p != null && guard < 50) { d++; p = byId[p]?.parentId; guard++ }
        return d
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Text("Sposta in…", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onPick(null) }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Text("Radice", Modifier.padding(start = 16.dp))
            }
            folders.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(f.id) }
                        .padding(horizontal = 20.dp, vertical = 12.dp).padding(start = (12 * depth(f)).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text(f.name, Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun FolderStyleDialog(
    initialColor: Int?,
    initialEmoji: String?,
    onConfirm: (Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(initialColor) }
    var emoji by remember { mutableStateOf(initialEmoji ?: "") }
    val emojis = listOf("📁", "⭐", "🔒", "❤️", "📷", "🎬", "🎵", "📄", "💼", "🎨", "🔑", "🎁", "🌍", "🔥", "💡", "✅")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personalizza cartella") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Colore", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ColorSwatch(null, color == null) { color = null }
                    com.cripta.app.ui.components.FOLDER_COLORS.forEach { c -> ColorSwatch(c, color == c) { color = c } }
                }
                Text("Emoji", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EmojiPick("✕", emoji.isBlank()) { emoji = "" }
                    emojis.forEach { e -> EmojiPick(e, emoji == e) { emoji = e } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color, emoji.ifBlank { null }) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun ColorSwatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val fill = color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(fill)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Icon(Icons.Filled.Close, "Nessun colore", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EmojiPick(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, Modifier.padding(start = 16.dp), color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyState(filtering: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Text(if (filtering) "Nessun risultato" else "Vault vuoto",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(if (filtering) "Prova a cambiare i filtri." else "Tocca + per importare file.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

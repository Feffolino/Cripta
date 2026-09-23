package com.cripta.app.ui.vault

import android.widget.Toast
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
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
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import com.cripta.app.data.DisplayPrefs
import com.cripta.app.data.FolderStat
import com.cripta.app.data.SortKey
import com.cripta.app.data.ViewMode
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.media.ThumbnailLoader
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
    val coverOverrides by vm.coverOverrides.collectAsState()
    val coverVersions by vm.coverVersions.collectAsState()
    val stats by vm.stats.collectAsState()
    val importState by vm.importState.collectAsState()
    val savedFilters by vm.savedFilters.collectAsState()
    val trashEnabled by vm.trashEnabled.collectAsState()
    val convertStatus by vm.convertStatus.collectAsState()
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var searchExpanded by remember { mutableStateOf(false) }

    // Warm covers only while the grid is on screen; leaving for the viewer cancels this so the
    // background video decodes don't compete with the player for hardware codecs. Debounced so a
    // multi-file import (a change per file) doesn't restart it constantly.
    LaunchedEffect(files) {
        kotlinx.coroutines.delay(500)
        vm.prewarmCovers(files)
    }

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
    var showRegenCover by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    // A clean import result disappears by itself; one with failures stays until dismissed.
    LaunchedEffect(importState.finished, importState.failed, importState.duplicates.size) {
        if (importState.finished && importState.failed == 0 && importState.duplicates.isEmpty()) {
            kotlinx.coroutines.delay(6000)
            vm.dismissImportResult()
        }
    }

    // Collapse the secondary FABs while scrolling down so they don't cover the content
    // (they otherwise block dragging items in Manual reorder); bring them back on scroll up.
    var fabsVisible by remember { mutableStateOf(true) }
    var fabExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(fabsVisible) { if (!fabsVisible) fabExpanded = false }
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

    // Landscape: let the top bar scroll away (down hides, up reveals) to reclaim the short height.
    // Portrait keeps it pinned.
    val scrollBehavior = if (landscape) TopAppBarDefaults.enterAlwaysScrollBehavior()
        else TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    if (inSelection) {
                        Text("${selection.size} selezionati", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    } else {
                        com.cripta.app.ui.components.HeaderTitle(path.lastOrNull()?.name ?: "Cripta")
                    }
                },
                navigationIcon = {
                    if (inSelection) {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Annulla") }
                    } else if (path.isNotEmpty()) {
                        IconButton(onClick = { vm.goUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Su") }
                    }
                },
                actions = {
                    if (inSelection) {
                        val allIds = files.map { it.file.id }.toSet()
                        val allSelected = allIds.isNotEmpty() && selection.containsAll(allIds)
                        IconButton(onClick = { selection = if (allSelected) emptySet() else allIds }) {
                            Icon(Icons.Filled.SelectAll, if (allSelected) "Deseleziona tutto" else "Seleziona tutto",
                                tint = if (allSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
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
                            val convertible = files.count {
                                it.file.id in selection && VaultRepository.isVideo(it.file.mimeType) && it.file.mimeType != "video/mp4"
                            }
                            if (convertible > 0) {
                                DropdownMenuItem(
                                    text = { Text(if (convertible == 1) "Converti in MP4" else "Converti in MP4 ($convertible)") },
                                    onClick = {
                                        selMenu = false
                                        vm.convertToMp4(selection.toList())
                                        selection = emptySet()
                                        Toast.makeText(ctx, "Conversione in coda: prosegue in background", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                            val videoCount = files.count { it.file.id in selection && VaultRepository.isVideo(it.file.mimeType) }
                            if (videoCount > 0) {
                                DropdownMenuItem(
                                    text = { Text(if (videoCount == 1) "Rigenera copertina" else "Rigenera copertina ($videoCount)") },
                                    onClick = { selMenu = false; showRegenCover = true },
                                )
                            }
                        }
                    } else {
                        if (landscape) {
                            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                                Icon(Icons.Filled.Search, "Cerca",
                                    tint = if (searchExpanded || filters.query.isNotBlank())
                                        MaterialTheme.colorScheme.primary else LocalContentColor.current)
                            }
                        }
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
                            Icon(if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, "Vista")
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
                        // Single expanding FAB (speed-dial): collapsed it shows just "+"; tapping it
                        // reveals the labelled actions, so the screen isn't crowded by a stack of FABs.
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedVisibility(visible = fabExpanded) {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MiniFabAction("Cifra e importa", Icons.Filled.EnhancedEncryption) {
                                        fabExpanded = false; importLauncher.launch(arrayOf("*/*"))
                                    }
                                    MiniFabAction("Nuova cartella", Icons.Filled.CreateNewFolder) {
                                        fabExpanded = false; showNewFolder = true
                                    }
                                    if (display.showNoteFab) {
                                        MiniFabAction("Nuova nota", Icons.Filled.Description) {
                                            fabExpanded = false; onNewNote()
                                        }
                                    }
                                    if (display.showRandomFab) {
                                        MiniFabAction("Casuale", Icons.Filled.Casino) {
                                            fabExpanded = false; vm.randomShuffleOpen(onOpenFile)
                                        }
                                    }
                                }
                            }
                            FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                                Icon(
                                    if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                                    if (fabExpanded) "Chiudi" else "Azioni",
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).nestedScroll(fabScroll)) {
            // Landscape has little vertical room: the inline search bar is revealed on demand from
            // the top-bar search icon. Portrait: keep it, but hide it while scrolling down (same
            // gesture that hides the FABs) so the grid gets full height, and bring it back on scroll up.
            val showSearch = if (landscape) searchExpanded else (fabsVisible || searchExpanded)
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = vm::setQuery,
                    label = if (landscape) null else ({ Text("Cerca nome o tag") }),
                    placeholder = if (landscape) ({ Text("Cerca nome o tag") }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = if (landscape) 2.dp else 6.dp),
                )
            }
            ActiveFilterBar(filters, tags, vm::setType, { vm.setFavoritesOnly(false) },
                { vm.setUntaggedOnly(false) }, vm::toggleTag, vm::toggleExcludedTag, vm::clearFilters)
            if (path.isNotEmpty() && !filters.active) {
                Breadcrumb(path.map { it.name }, onGo = vm::goToDepth)
            }
            ImportBanner(importState, onDismiss = vm::dismissImportResult,
                onRemoveDuplicates = { vm.removeImportDuplicates() })
            ConvertBanner(convertStatus, onCancel = vm::cancelConversion, onDismiss = vm::dismissConvertResult)
            if (stats.scope.total > 0 && display.showStatsStrip) {
                StatsStrip(stats, filters.active, filters.type, onOpen = { showStats = true }, onType = vm::toggleType)
            }

            val manual = sortKey == SortKey.MANUAL
            val showFolders = folders.isNotEmpty() && !filters.active && !manual
            // Landscape is wide and short: add columns so cells are smaller and more rows are
            // visible at once (otherwise a few huge thumbnails fill the short height).
            val effectiveColumns = if (landscape) gridColumns + 2 else gridColumns
            val columns = if (viewMode == ViewMode.GRID) GridCells.Fixed(effectiveColumns) else GridCells.Fixed(1)
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
                    EmptyState(filters.active, Modifier.weight(1f).fillMaxWidth(),
                        onImport = { importLauncher.launch(arrayOf("*/*")) })
                manual ->
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ReorderableFileGrid(
                            items = files,
                            columns = if (viewMode == ViewMode.GRID) gridColumns else 1,
                            asList = viewMode == ViewMode.LIST,
                            selection = selection,
                            display = display,
                            coverOverrides = coverOverrides,
                            coverVersions = coverVersions,
                            thumb = { vm.thumb(it) },
                            onOpen = { vm.publishViewerQueue(); onOpenFile(it) },
                            onReorder = { vm.reorder(it) },
                        )
                    }
                else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(
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
                                modifier = Modifier.animateItem(placementSpec = null),
                                onOpen = { vm.enterFolder(folder) }, onLongPress = { folderMenu = folder })
                        }
                    }
                    grouped.forEach { (label, group) ->
                        if (label.isNotEmpty()) header(label)
                        items(group, key = { it.file.id }, span = { GridItemSpan(1) }) { fwt ->
                            FileCell(fwt, viewMode, fwt.file.id in selection, inSelection, display,
                                // No placement animation: toggling a filter reshuffles the whole
                                // result set and every cell used to visibly slide to its new slot.
                                Modifier.animateItem(placementSpec = null),
                                coverOverrides[fwt.file.id], coverVersions[fwt.file.id] ?: 0,
                                { vm.thumb(fwt.file) }, { vm.publishViewerQueue(); onOpenFile(fwt.file.id) }, { toggleSel(fwt.file.id) })
                        }
                    }
                    }
                    FastScroller(gridState, Modifier.align(Alignment.CenterEnd))
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
            showRecents = display.showRecentTags,
            onSetColor = { name, c -> vm.setTagColor(name, c) },
            onSetPinned = { name, p -> vm.setTagPinned(name, p) },
        )
    }

    if (batchTag) {
        BatchTagDialog(
            count = selection.size,
            allTags = tags,
            onConfirm = { names -> vm.addTagsToFiles(selection.toList(), names); batchTag = false; selection = emptySet() },
            onCreateTag = { name, alias -> vm.createTag(name, alias) },
            onDismiss = { batchTag = false },
            showRecents = display.showRecentTags,
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
            onExcludeTag = vm::toggleExcludedTag,
            onSort = { k, a -> vm.setSort(k, a) },
            onClear = { vm.clearFilters() },
            onDismiss = { showFilterSheet = false },
            tagColors = display.tagColors,
            onTagMatchAll = vm::setTagMatchAll,
            saved = savedFilters,
            onApplySaved = vm::applySavedFilter,
            onDeleteSaved = { vm.deleteSavedFilter(it) },
            onSave = { vm.saveCurrentFilter(it) },
        )
    }

    if (showStats) {
        StatsSheet(stats, filters.active, path.lastOrNull()?.name, onDismiss = { showStats = false })
    }

    if (confirmMultiDelete) {
        AlertDialog(
            onDismissRequest = { confirmMultiDelete = false },
            title = { Text("Eliminare ${selection.size} file?") },
            text = {
                Text(if (trashEnabled) "Verranno spostati nel cestino: potrai ripristinarli da Impostazioni › Archivio."
                    else "Eliminazione sicura (crypto-shredding). Irreversibile.")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteFiles(selection.toList()); selection = emptySet(); confirmMultiDelete = false }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmMultiDelete = false }) { Text("Annulla") } },
        )
    }

    if (showRegenCover) {
        val videoIds = files.filter { it.file.id in selection && VaultRepository.isVideo(it.file.mimeType) }.map { it.file.id }
        CoverOptionDialog(
            count = videoIds.size,
            onPick = { cover ->
                vm.regenerateCovers(videoIds, cover)
                showRegenCover = false
                selection = emptySet()
                Toast.makeText(ctx, "Rigenerazione copertine…", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showRegenCover = false },
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
private fun androidx.compose.foundation.layout.BoxScope.FastScroller(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier,
) {
    val total = state.layoutInfo.totalItemsCount
    if (total <= 0) return
    val scope = rememberCoroutineScope()
    var trackH by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val fraction = if (total <= 1) 0f
        else (state.firstVisibleItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)
    val active = state.isScrollInProgress || dragging
    val alpha by animateFloatAsState(if (active) 1f else 0f, label = "fastscroll")
    val density = LocalDensity.current
    val thumbH = 48.dp
    Box(
        modifier
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { trackH = it.size.height.toFloat() }
            .pointerInput(total, trackH) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ ->
                        if (trackH > 0f) {
                            val f = (change.position.y / trackH).coerceIn(0f, 1f)
                            scope.launch { state.scrollToItem((f * (total - 1)).roundToInt()) }
                        }
                    },
                )
            },
    ) {
        val thumbPx = with(density) { thumbH.toPx() }
        val maxOffset = (trackH - thumbPx).coerceAtLeast(0f)
        val offsetY = with(density) { (fraction * maxOffset).toDp() }
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(end = 3.dp)
                .offset(y = offsetY)
                .width(6.dp)
                .height(thumbH)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
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
    coverOverride: android.graphics.Bitmap?,
    coverVersion: Int,
    thumb: suspend () -> android.graphics.Bitmap?,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val mime = item.file.mimeType
    val isVideo = VaultRepository.isVideo(mime)
    val fallbackIcon = when {
        VaultRepository.isImage(mime) -> Icons.Filled.Image
        isVideo -> Icons.Filled.Movie
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val loaded by produceState<android.graphics.Bitmap?>(initialValue = null, item.file.id, coverVersion) { value = thumb() }
    // A freshly regenerated cover (override) wins over the cached/loaded one so it shows at once.
    val bmp = coverOverride ?: loaded

    // Tap/long-press are handled by the grid container (unified gesture), not per cell.
    if (viewMode == ViewMode.LIST) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // List mode: no tag badges on the cover — every tag is shown as a chip beside it.
                ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite,
                    emptyList(), Modifier.size(72.dp), file = item.file, display = display, compact = true)
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (display.showFileInfo) {
                        Text(listMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.tags.forEach { TagChip(it, display) }
                        }
                    }
                }
            }
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite,
            if (display.showTagsOnCover) item.tags else emptyList(),
            Modifier.fillMaxWidth().aspectRatio(1f), file = item.file, display = display)
        Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        if (display.showFileInfo) {
            Text(fileMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp))
        }
    }
}

/** "4K" / "HD" / "SD" from a display resolution (short side), or null when unknown. */
fun qualityLabel(width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val short = minOf(width, height)
    return when {
        short >= 2000 -> "4K"
        short >= 720 -> "HD"
        else -> "SD"
    }
}

/** Size · date (duration and quality are on the cover itself in list mode). */
private fun listMeta(f: com.cripta.app.data.db.FileEntity): String {
    val parts = mutableListOf<String>()
    com.cripta.app.ui.components.formatDuration(f.durationMs)?.let { parts += "⏱ $it" }
    if (VaultRepository.isVideo(f.mimeType)) qualityLabel(f.width, f.height)?.let { parts += it }
    parts += formatBytes(f.sizeBytes)
    parts += java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(f.importedAt))
    return parts.joinToString(" · ")
}

/** Text a tag shows on a cover badge, per the user's style choice. */
private fun badgeText(tag: TagEntity, display: DisplayPrefs?): String =
    if (display?.coverTagStyle == com.cripta.app.data.CoverTagStyle.NAME) tag.name else tagAlias(tag)

/** Badge/chip colour of a tag: its own stable colour, or the app accent when tag colours are off. */
@Composable
private fun tagBg(tag: TagEntity, display: DisplayPrefs?): Color =
    if (display?.tagColors != false) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary

@Composable
private fun tagFg(display: DisplayPrefs?): Color =
    if (display?.tagColors != false) Color.White else MaterialTheme.colorScheme.onPrimary

/** Small rounded label on a dark scrim, for the duration/quality corner of a cover. */
@Composable
private fun CornerBadge(text: String, emphasis: Boolean = false) {
    Box(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(if (emphasis) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            color = if (emphasis) MaterialTheme.colorScheme.onPrimary else Color.White, maxLines = 1)
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
    file: com.cripta.app.data.db.FileEntity? = null,
    display: DisplayPrefs? = null,
    /** Small list-mode thumbnail: only the duration, bottom-right. */
    compact: Boolean = false,
) {
    val borderMod = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier
    val duration = if (isVideo && display?.showDurationBadge != false)
        com.cripta.app.ui.components.formatDuration(file?.durationMs) else null
    val quality = if (isVideo && !compact && display?.showQualityBadge != false)
        qualityLabel(file?.width, file?.height) else null
    Box(modifier.clip(MaterialTheme.shapes.medium).then(borderMod), contentAlignment = Alignment.Center) {
        // Without a cover, a tint per type (video blue / photo green / other amber) instead of flat grey.
        val typeTint = when {
            isVideo -> Color(0xFF3B82F6)
            fallbackIcon == Icons.Filled.Image -> Color(0xFF22C55E)
            else -> Color(0xFFF59E0B)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        Crossfade(targetState = bmp, label = "thumb") { b ->
            if (b != null) {
                Image(b.asImageBitmap(), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(typeTint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(fallbackIcon, null, tint = typeTint, modifier = Modifier.size(if (compact) 26.dp else 32.dp))
                }
            }
        }
        // Play marker only when no duration badge already says "this is a video".
        if (isVideo && duration == null) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayCircle, "Video", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        if (tags.isNotEmpty()) {
            TagBadges(
                tags = tags,
                display = display,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            )
        }
        // "Resume" progress: how much of the video has already been watched.
        val watched = if (isVideo && display?.resumePlayback != false) file?.let { f ->
            val p = f.playbackPosMs; val d = f.durationMs
            if (p != null && d != null && d > 0) (p.toFloat() / d).coerceIn(0f, 1f) else null
        } else null
        if (watched != null && watched > 0.01f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.28f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(watched).background(MaterialTheme.colorScheme.primary))
            }
        }
        if (favorite) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(if (compact) 18.dp else 22.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Star, "Preferito", tint = com.cripta.app.ui.theme.Favorite,
                    modifier = Modifier.size(if (compact) 13.dp else 16.dp))
            }
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, "Selezionato", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        } else if (compact) {
            if (duration != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(3.dp)) { CornerBadge(duration) }
            }
        } else if (duration != null || quality != null) {
            // Top-right, so the whole bottom edge stays free for tags.
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                quality?.let { CornerBadge(it, emphasis = it == "4K") }
                duration?.let { CornerBadge(it) }
            }
        }
    }
}

@Composable
private fun TagBadge(tag: TagEntity?, text: String, display: DisplayPrefs?, maxWidth: androidx.compose.ui.unit.Dp) {
    val bg = if (tag != null) tagBg(tag, display) else Color.Black.copy(alpha = 0.6f)
    val fg = if (tag != null) tagFg(display) else Color.White
    Box(
        Modifier.widthIn(max = maxWidth)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 3.dp, vertical = 0.5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Colored tag chip for the list view (full name, all tags visible). */
@Composable
private fun TagChip(tag: TagEntity, display: DisplayPrefs) {
    val colored = display.tagColors
    val c = com.cripta.app.ui.theme.tagColor(tag)
    Box(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(if (colored) c.copy(alpha = 0.22f) else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            if (!tag.alias.isNullOrBlank()) "${tag.alias} ${tag.name}" else tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (colored) c else MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/**
 * Tag badges over the bottom of a cover, on a dark gradient so they stay legible on any image.
 * Up to 3 rows (automatic: by cover width — bigger covers show more rows — or fixed by the user),
 * packed greedily by measured width; a "+N" badge appears only once every row is full.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagBadges(tags: List<TagEntity>, display: DisplayPrefs?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val outerPad = 3.dp
        val availDp = maxWidth - outerPad * 2
        val availPx = with(density) { availDp.toPx() }
        val hPadPx = with(density) { 6.dp.toPx() }   // 3dp padding on each side of a badge
        val spacingPx = with(density) { 2.dp.toPx() }
        val rowsWanted = when (val r = display?.coverTagRows ?: 0) {
            0 -> when {
                maxWidth >= 150.dp -> 3
                maxWidth >= 100.dp -> 2
                else -> 1
            }
            else -> r
        }
        val texts = remember(tags, display?.coverTagStyle) { tags.map { badgeText(it, display) } }
        // Measured on-screen width of each badge (text + padding), capped to a full row.
        val widths = remember(texts, availPx) {
            texts.map { minOf(measurer.measure(it, style).size.width + hPadPx, availPx) }
        }

        // Greedy row packing: returns the tag indices of each row.
        fun pack(reserveLastPx: Float): List<List<Int>> {
            val rows = mutableListOf<MutableList<Int>>()
            var i = 0
            while (i < widths.size && rows.size < rowsWanted) {
                val row = mutableListOf<Int>()
                val last = rows.size == rowsWanted - 1
                val budget = if (last) availPx - reserveLastPx else availPx
                var used = 0f
                while (i < widths.size) {
                    val add = widths[i] + if (row.isNotEmpty()) spacingPx else 0f
                    if (used + add <= budget || row.isEmpty() && budget >= widths[i]) { row += i; used += add; i++ } else break
                }
                if (row.isEmpty()) break
                rows += row
            }
            return rows
        }

        var rows = pack(0f)
        var shown = rows.sumOf { it.size }
        if (shown < tags.size) {
            val overflowPx = measurer.measure("+${tags.size}", style).size.width + hPadPx + spacingPx
            rows = pack(overflowPx)
            shown = rows.sumOf { it.size }
        }
        val extra = tags.size - shown
        val badgeH = with(density) { (measurer.measure("Ag", style).size.height).toDp() } + 1.dp

        Box(Modifier.fillMaxWidth()) {
            // Scrim sized to the rows actually used.
            Box(
                Modifier.matchParentSize()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
            )
            Column(
                Modifier.padding(start = outerPad, end = outerPad, bottom = outerPad, top = badgeH),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rows.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { i -> TagBadge(tags[i], texts[i], display, availDp) }
                        if (r == rows.lastIndex && extra > 0) TagBadge(null, "+$extra", display, availDp)
                    }
                }
            }
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
    onExcludeTag: (Long) -> Unit,
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
        filters.excludedTagIds.forEach { id ->
            val t = tagById[id] ?: return@forEach
            DismissChip("⊘ #${t.name}") { onExcludeTag(id) }
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

private enum class TagFilterState { NEUTRAL, INCLUDE, EXCLUDE }

/** Tri-state tag chip: neutral, include (primary), exclude (error). Colour-coded, no icons. */
@Composable
private fun TagFilterChip(label: String, state: TagFilterState, dot: Color?, onClick: () -> Unit) {
    val bg = when (state) {
        TagFilterState.INCLUDE -> MaterialTheme.colorScheme.primary
        TagFilterState.EXCLUDE -> MaterialTheme.colorScheme.errorContainer
        TagFilterState.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (state) {
        TagFilterState.INCLUDE -> MaterialTheme.colorScheme.onPrimary
        TagFilterState.EXCLUDE -> MaterialTheme.colorScheme.onErrorContainer
        TagFilterState.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.clickable(onClick = onClick)) {
        // An excluded (hidden) tag is struck through instead of getting a "⊘" prefix: the chip keeps
        // exactly the same width in every state (a width change reflows the rows and makes the sheet
        // jump) without the invisible leading space a reserved prefix would leave.
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // The tag's own colour (same as on the covers), in every state so the width never changes.
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Box(Modifier.width(6.dp))
            }
            Text(
                label,
                textDecoration = if (state == TagFilterState.EXCLUDE)
                    androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
            )
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
    onExcludeTag: (Long) -> Unit,
    onSort: (SortKey, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    tagColors: Boolean = true,
    onTagMatchAll: (Boolean) -> Unit = {},
    saved: List<com.cripta.app.data.db.SavedFilterEntity> = emptyList(),
    onApplySaved: (String) -> Unit = {},
    onDeleteSaved: (Long) -> Unit = {},
    onSave: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var naming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (saved.isNotEmpty()) {
                Text("Filtri salvati", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    saved.forEach { sf ->
                        androidx.compose.material3.InputChip(
                            selected = false,
                            onClick = { onApplySaved(sf.json) },
                            label = { Text(sf.name) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, "Elimina filtro salvato", modifier = Modifier.size(16.dp)
                                    .clickable { onDeleteSaved(sf.id) })
                            },
                        )
                    }
                }
                HorizontalDivider()
            }
            Text("Ordina", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SortKey.DATE to "Data", SortKey.NAME to "Nome",
                    SortKey.SIZE to "Dimensione", SortKey.MANUAL to "Manuale",
                ).forEach { (k, lbl) ->
                    FilterChip(selected = sortKey == k, onClick = { onSort(k, sortAscending) }, label = { Text(lbl) })
                }
            }
            // Fixed-height slot so switching to/from Manual doesn't resize (and slide) the sheet.
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
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
                Text("Tocca per includere, ancora per escludere (barrato, nascosto), ancora per azzerare.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // With several tags chosen: must a file have all of them, or is one enough?
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostra file con", style = MaterialTheme.typography.bodyMedium)
                    FilterChip(selected = filters.tagMatchAll, onClick = { onTagMatchAll(true) }, label = { Text("tutte") })
                    FilterChip(selected = !filters.tagMatchAll, onClick = { onTagMatchAll(false) }, label = { Text("almeno una") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { tag ->
                        val label = if (!tag.alias.isNullOrBlank()) "${tag.alias} #${tag.name}" else "#${tag.name}"
                        val state = when {
                            tag.id in filters.tagIds -> TagFilterState.INCLUDE
                            tag.id in filters.excludedTagIds -> TagFilterState.EXCLUDE
                            else -> TagFilterState.NEUTRAL
                        }
                        TagFilterChip(
                            label = label,
                            state = state,
                            dot = if (tagColors) com.cripta.app.ui.theme.tagColor(tag) else null,
                            // neutral -> include; include -> exclude; exclude -> neutral.
                            onClick = { if (state == TagFilterState.NEUTRAL) onTag(tag.id) else onExcludeTag(tag.id) },
                        )
                    }
                }
            }

            // Always laid out (just disabled when nothing is filtered): if it appeared/disappeared,
            // flipping a toggle would change the sheet's height and make it slide up/down.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { naming = true }, enabled = filters.active) { Text("Salva filtro") }
                TextButton(onClick = onClear, enabled = filters.active) { Text("Azzera filtri") }
            }
        }
    }
    if (naming) {
        TextPromptDialog("Salva filtro", "Nome (es. Video da vedere)",
            onConfirm = { onSave(it); naming = false },
            onDismiss = { naming = false })
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
    coverOverrides: Map<String, android.graphics.Bitmap>,
    coverVersions: Map<String, Int>,
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
                val loaded by produceState<android.graphics.Bitmap?>(initialValue = null, fwt.file.id, coverVersions[fwt.file.id] ?: 0) { value = thumb(fwt.file) }
                val bmp = coverOverrides[fwt.file.id] ?: loaded
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
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    if (asList) {
        Surface(
            color = if (dragged) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (dragged) 8.dp else 0.dp,
            modifier = modifier.fillMaxWidth().height(72.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite, emptyList(), Modifier.size(52.dp),
                    file = item.file, display = display, compact = true)
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
                    Modifier.fillMaxWidth().aspectRatio(1f), file = item.file, display = display)
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

/** Lets the user pick which frame of the selected video(s) becomes the new cover. */
@Composable
private fun CoverOptionDialog(
    count: Int,
    onPick: (ThumbnailLoader.VideoCover) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ThumbnailLoader.VideoCover.AUTO to "Automatica (fotogramma migliore)",
        ThumbnailLoader.VideoCover.START to "Inizio",
        ThumbnailLoader.VideoCover.MIDDLE to "Metà",
        ThumbnailLoader.VideoCover.END to "Fine",
        ThumbnailLoader.VideoCover.RANDOM to "Fotogramma casuale",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Rigenera copertina" else "Rigenera copertina ($count)") },
        text = {
            Column {
                Text(
                    "Scegli da quale punto del video generare la copertina.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                options.forEach { (cover, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(cover) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
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
private fun EmptyState(filtering: Boolean, modifier: Modifier, onImport: () -> Unit = {}) {
    Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (filtering) Icons.Filled.Search else Icons.Filled.EnhancedEncryption,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp),
                )
            }
        }
        Text(if (filtering) "Nessun risultato" else "Vault vuoto",
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
        Text(if (filtering) "Prova a cambiare i filtri." else "Importa foto e video: restano cifrati e visibili solo qui.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (!filtering) {
            Button(onClick = onImport, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Filled.EnhancedEncryption, null, modifier = Modifier.size(18.dp))
                Text("  Importa file")
            }
        }
    }
}

/** One labelled row in the speed-dial: a name pill next to a small FAB. */
@Composable
private fun MiniFabAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        SmallFloatingActionButton(onClick = onClick) { Icon(icon, label) }
    }
}

/**
 * Live import status under the search bar: while encrypting it shows "n of N", the file being
 * processed and its progress; when done it confirms how many files made it (and how many failed).
 */
@Composable
private fun ImportBanner(state: VaultRepository.ImportState, onDismiss: () -> Unit, onRemoveDuplicates: () -> Unit) {
    AnimatedVisibility(
        visible = state.active || state.finished,
        enter = androidx.compose.animation.expandVertically() + fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
    ) {
        val ok = state.failed == 0
        val container = when {
            state.active -> MaterialTheme.colorScheme.secondaryContainer
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
                        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null,
                            tint = onContainer, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        val title = when {
                            state.active -> "Importazione in corso · ${minOf(state.done + 1, state.total)} di ${state.total}"
                            ok -> "Importazione completata"
                            else -> "Importazione completata con errori"
                        }
                        Text(title, style = MaterialTheme.typography.titleSmall, color = onContainer)
                        val sub = if (state.active) {
                            val name = state.currentName ?: "Preparazione…"
                            val pct = if (state.currentTotalBytes > 0)
                                " · ${(state.currentBytes * 100 / state.currentTotalBytes).coerceIn(0, 100)}%" else ""
                            name + pct
                        } else buildString {
                            append(if (state.succeeded == 1) "1 file cifrato nel vault" else "${state.succeeded} file cifrati nel vault")
                            if (state.failed > 0) append(" · ${state.failed} non importati")
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = onContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!state.active) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Chiudi", tint = onContainer, modifier = Modifier.size(18.dp))
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
                    Text("Puoi uscire dall'app: l'importazione continua in background.",
                        style = MaterialTheme.typography.labelSmall, color = onContainer.copy(alpha = 0.8f))
                }
                // Byte-identical copies of files already in the vault: offer to drop the new copies.
                if (state.duplicates.isNotEmpty()) {
                    val n = state.duplicates.size
                    Text(
                        if (n == 1) "\"${state.duplicates.first().second}\" era già nel vault."
                        else "$n file erano già nel vault (copie identiche).",
                        style = MaterialTheme.typography.bodySmall, color = onContainer,
                    )
                    if (!state.active) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.FilledTonalButton(onClick = onRemoveDuplicates) {
                                Text(if (n == 1) "Rimuovi il doppione" else "Rimuovi i $n doppioni")
                            }
                            TextButton(onClick = onDismiss) { Text("Tienili", color = onContainer) }
                        }
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
private fun StatsStrip(stats: VaultStats, filtering: Boolean, type: TypeFilter, onOpen: () -> Unit, onType: (TypeFilter) -> Unit) {
    val shown = stats.shown
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val lead = if (filtering || stats.filtered) "${shown.total} di ${stats.scope.total}"
            else if (shown.total == 1) "1 elemento" else "${shown.total} elementi"
        Text(lead, style = MaterialTheme.typography.labelLarge,
            color = if (filtering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onOpen).padding(horizontal = 6.dp, vertical = 4.dp))
        CountPill(Icons.Filled.Movie, shown.videos, type == TypeFilter.VIDEO) { onType(TypeFilter.VIDEO) }
        CountPill(Icons.Filled.Image, shown.images, type == TypeFilter.IMAGE) { onType(TypeFilter.IMAGE) }
        if (shown.others > 0 || type == TypeFilter.OTHER) {
            CountPill(Icons.AutoMirrored.Filled.InsertDriveFile, shown.others, type == TypeFilter.OTHER) { onType(TypeFilter.OTHER) }
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.ExpandMore, "Riepilogo", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

/** Clickable path "Radice › A › B": tap a level to jump straight back to it. */
@Composable
private fun Breadcrumb(names: List<String>, onGo: (Int) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(names.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val all = listOf("Radice") + names
        all.forEachIndexed { i, n ->
            val last = i == all.lastIndex
            Text(
                n,
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.clip(MaterialTheme.shapes.small)
                    .then(if (last) Modifier else Modifier.clickable { onGo(i) })
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            if (!last) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountPill(icon: ImageVector, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Text(" $count", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Full-screen-ish breakdown: how many videos (and other media) are loaded, filtered or not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsSheet(stats: VaultStats, filtering: Boolean, folderName: String?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val scopeLabel = when {
                filtering -> "Tutto il vault"
                folderName != null -> "Cartella \"$folderName\""
                else -> "Radice del vault"
            }
            Column {
                Text("Riepilogo", style = MaterialTheme.typography.headlineSmall)
                Text(if (filtering) "Risultati filtrati · $scopeLabel" else scopeLabel,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Hero: the video count, the number people care about most.
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${stats.shown.videos}", style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (stats.filtered) {
                                Text(" / ${stats.scope.videos}", style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        Text(
                            when {
                                stats.filtered -> "video mostrati su ${stats.scope.videos} caricati"
                                stats.shown.videos == 1 -> "video caricato"
                                else -> "video caricati"
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        com.cripta.app.ui.components.formatDuration(stats.shown.videoDurationMs.takeIf { it > 0 })?.let {
                            Text("Durata totale $it", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            if (stats.filtered && stats.scope.videos > 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { stats.shown.videos.toFloat() / stats.scope.videos },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(Icons.Filled.Image, "Foto", stats.shown.images, stats.scope.images, stats.filtered, Modifier.weight(1f))
                StatTile(Icons.AutoMirrored.Filled.InsertDriveFile, "Altri", stats.shown.others, stats.scope.others, stats.filtered, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(Icons.Filled.GridView, "Totale", stats.shown.total, stats.scope.total, stats.filtered, Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.EnhancedEncryption, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(formatBytes(stats.shown.bytes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
                        Text(if (stats.filtered) "su ${formatBytes(stats.scope.bytes)}" else "Spazio occupato",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!filtering) {
                Text("Conteggi degli elementi in questa cartella (le sottocartelle non sono incluse). Con un filtro attivo la ricerca copre tutto il vault.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, shown: Int, scope: Int, filtered: Boolean, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Text("$shown", style = MaterialTheme.typography.titleLarge)
                if (filtered) {
                    Text(" / $scope", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Live sample cover for the settings screen, drawn with the real cover renderer so every cover
 * option (tag rows, style, colours, duration/quality badges) can be judged before leaving.
 */
@Composable
fun CoverPreview(display: DisplayPrefs, modifier: Modifier = Modifier) {
    val sample = remember {
        listOf("Mare" to "🌊", "Famiglia" to "FA", "Estate" to "ES", "Viaggi" to "✈️", "Amici" to "AM",
            "Festa" to "🎉", "Montagna" to "MO", "2024" to "24", "Preferiti" to "PR", "Cane" to "🐶")
            .mapIndexed { i, (n, a) -> TagEntity(id = -1L - i, name = n, alias = a) }
    }
    val file = remember {
        com.cripta.app.data.db.FileEntity(
            id = "preview", originalName = "Esempio.mp4", mimeType = "video/mp4", sizeBytes = 48_000_000,
            createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0),
            durationMs = 192_000, width = 1920, height = 1080,
        )
    }
    ThumbBox(null, Icons.Filled.Movie, isVideo = true, name = "Anteprima", selected = false, favorite = true,
        tags = if (display.showTagsOnCover) sample else emptyList(), modifier = modifier, file = file, display = display)
}

/** Conversion queue status: current video + progress + how many wait, then the outcome. */
@Composable
private fun ConvertBanner(state: VaultRepository.ConvertStatus, onCancel: () -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = state.active || state.lastResult != null,
        enter = androidx.compose.animation.expandVertically() + fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
    ) {
        val container = when {
            state.active -> MaterialTheme.colorScheme.secondaryContainer
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
                    Icon(if (state.active) Icons.Filled.Movie else if (state.lastOk) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        null, tint = on, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        if (state.active) {
                            Text("Conversione in MP4 · ${state.pct}%" + if (state.waiting > 0) " · ${state.waiting} in coda" else "",
                                style = MaterialTheme.typography.titleSmall, color = on)
                            Text(state.currentName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = on,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(state.lastResult.orEmpty(), style = MaterialTheme.typography.bodySmall, color = on)
                        }
                    }
                    if (state.active) TextButton(onClick = onCancel) { Text("Annulla", color = on) }
                    else IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Chiudi", tint = on, modifier = Modifier.size(18.dp))
                    }
                }
                if (state.active) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.pct / 100f }, color = on, trackColor = on.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

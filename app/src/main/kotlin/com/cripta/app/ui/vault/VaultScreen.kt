package com.cripta.app.ui.vault

import androidx.compose.material3.SegmentedButton
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.StarBorder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.layout.boundsInRoot
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
import androidx.compose.runtime.derivedStateOf
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
import com.cripta.app.ui.components.CornerSelectionCheck
import com.cripta.app.ui.components.SelectableThumbFrame
import com.cripta.app.ui.components.ThumbCrossfade
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.animationsEnabled
import com.cripta.app.ui.theme.pressScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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

/** Scroll-memory key of the vault root (folders use their id). */
private const val ROOT_KEY = -1L

/** Grid key of the folders row (file keys are ids, so they never clash). */
private const val FOLDERS_KEY = "folders"

/** One entry of the speed-dial FAB. */
private class FabAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

fun tagAlias(tag: TagEntity): String =
    tag.alias?.takeIf { it.isNotBlank() } ?: tag.name.take(2).uppercase(Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    onOpenFile: (String) -> Unit,
    onNewNote: (Long?) -> Unit = {},
    vm: VaultViewModel = hiltViewModel(),
) {
    val folders by vm.folders.collectAsState()
    val files by vm.files.collectAsState()
    val tags by vm.tags.collectAsState()
    val filters by vm.filters.collectAsState()
    val path by vm.path.collectAsState()
    val pendingOriginals by vm.pendingOriginals.collectAsState()
    val viewMode by vm.viewMode.collectAsState()
    val portraitColumns by vm.gridColumns.collectAsState()
    val landscapeColumns by vm.gridColumnsLandscape.collectAsState()
    val allFolders by vm.allFolders.collectAsState()
    val sortKey by vm.sortKey.collectAsState()
    val sortAscending by vm.sortAscending.collectAsState()
    val folderStats by vm.folderStats.collectAsState()
    val folderPreviews by vm.folderPreviews.collectAsState()
    val display by vm.display.collectAsState()
    val coverOverrides by vm.coverOverrides.collectAsState()
    val coverVersions by vm.coverVersions.collectAsState()
    val stats by vm.stats.collectAsState()
    val importState by vm.importState.collectAsState()
    val savedFilters by vm.savedFilters.collectAsState()
    val trashEnabled by vm.trashEnabled.collectAsState()
    val convertStatus by vm.convertStatus.collectAsState()
    val convertSettings by vm.convertSettings.collectAsState()
    /** Ids waiting for the first-conversion choice dialog. */
    var convertAsk by remember { mutableStateOf<List<String>?>(null) }
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Portrait and landscape each keep their own column count; the "Vista" menu edits the one in use.
    val gridColumns = if (landscape) landscapeColumns else portraitColumns
    val columnChoices = if (landscape) 3..8 else 2..5
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
    /** Grid cell under a finger held down (after a short delay, so a scroll doesn't flash it). */
    var pressedKey by remember { mutableStateOf<String?>(null) }

    // Feedback for every action, with "Annulla" where the change can be reverted.
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    fun notify(message: String, undo: (() -> Unit)? = null) {
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                message,
                actionLabel = if (undo != null) "Annulla" else null,
                withDismissAction = undo == null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
    fun countLabel(n: Int) = if (n == 1) "1 elemento" else "$n elementi"
    fun folderLabel(id: Long?) = if (id == null) "Radice" else "\"${allFolders.firstOrNull { it.id == id }?.name ?: "cartella"}\""
    fun moveWithUndo(ids: List<String>, target: Long?) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        val prev = files.filter { it.file.id in set }.associate { it.file.id to it.file.folderId }
        vm.moveFiles(ids, target)
        notify("${if (ids.size == 1) "Spostato 1 elemento" else "Spostati ${ids.size} elementi"} in ${folderLabel(target)}") {
            vm.restoreFolders(prev)
        }
    }
    fun favoriteWithUndo(ids: List<String>, fav: Boolean) {
        val set = ids.toSet()
        val prev = files.filter { it.file.id in set }.associate { it.file.id to it.file.isFavorite }
        vm.setFavorite(ids, fav)
        val n = ids.size
        notify(
            if (fav) (if (n == 1) "Aggiunto ai preferiti" else "$n elementi aggiunti ai preferiti")
            else (if (n == 1) "Tolto dai preferiti" else "$n elementi tolti dai preferiti"),
        ) { vm.restoreFavorites(prev) }
    }
    fun tagsOf(ids: Collection<String>): Map<String, List<String>> {
        val set = ids.toSet()
        return files.filter { it.file.id in set }.associate { fwt -> fwt.file.id to fwt.tags.map { it.name } }
    }

    // A selection belongs to the folder it was made in.
    LaunchedEffect(path) { selection = emptySet() }

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

    BackHandler(enabled = selection.isNotEmpty() || filters.active || searchExpanded || path.isNotEmpty()) {
        when {
            selection.isNotEmpty() -> selection = emptySet()
            filters.active -> { vm.clearFilters(); searchExpanded = false }
            searchExpanded -> searchExpanded = false
            path.isNotEmpty() -> vm.goUp()
        }
    }
    // Registered last, so it wins: Back first closes the open speed-dial.
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importThenHandleOriginals(uris) }
    // Progress and results of an import are shown in-app (banner) and in a notification: the
    // notification permission is asked here, the first time the user imports, not at app start.
    // The notification permission is asked (with a rationale) by MainActivity when the first
    // background job starts, so importing just opens the picker.
    fun startImport() { importLauncher.launch(arrayOf("*/*")) }

    val inSelection = selection.isNotEmpty()

    // Landscape: let the top bar scroll away (down hides, up reveals) to reclaim the short height.
    // Portrait keeps it pinned.
    val scrollBehavior = if (landscape) TopAppBarDefaults.enterAlwaysScrollBehavior()
        else TopAppBarDefaults.pinnedScrollBehavior()

    // Keep the last count while the title crossfades out of selection mode (selection is empty by then).
    var lastSelCount by remember { mutableIntStateOf(1) }
    SideEffect { if (selection.isNotEmpty()) lastSelCount = selection.size }
    val shownSelCount = if (selection.isNotEmpty()) selection.size else lastSelCount

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                // Same colour when the content scrolls under it (M3 otherwise switches to a tinted
                // surface-container colour, which looked light blue on this theme).
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    AnimatedContent(
                        targetState = inSelection,
                        transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
                        label = "title",
                    ) { sel ->
                        if (sel) SelectionCountTitle(shownSelCount)
                        else com.cripta.app.ui.components.HeaderTitle(path.lastOrNull()?.name ?: "Cripta")
                    }
                },
                navigationIcon = {
                    if (inSelection) {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, "Esci dalla selezione") }
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
                    } else {
                        // Search is an icon that opens the field (it stays open while a query is typed);
                        // tapping it again closes the field and clears the query.
                        val searchShown = searchExpanded || filters.query.isNotBlank()
                        IconButton(onClick = {
                            if (searchShown) { searchExpanded = false; vm.setQuery("") } else searchExpanded = true
                        }) {
                            Icon(Icons.Filled.Search, if (searchShown) "Chiudi ricerca" else "Cerca",
                                tint = if (searchShown) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Filled.Tune, "Filtri e ordinamento",
                                tint = if (filters.active) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        // One "Vista" menu: grid / list and the number of columns.
                        var viewMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { viewMenu = true }) {
                            Icon(if (viewMode == ViewMode.GRID) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                                if (viewMode == ViewMode.GRID) "Vista: griglia, $gridColumns colonne" else "Vista: lista")
                        }
                        DropdownMenu(expanded = viewMenu && com.cripta.app.ui.components.vaultUnlocked(), onDismissRequest = { viewMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Griglia") },
                                leadingIcon = { Icon(Icons.Filled.GridView, null) },
                                trailingIcon = { if (viewMode == ViewMode.GRID) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { vm.setViewMode(ViewMode.GRID) },
                            )
                            DropdownMenuItem(
                                text = { Text("Lista") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, null) },
                                trailingIcon = { if (viewMode == ViewMode.LIST) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { vm.setViewMode(ViewMode.LIST); viewMenu = false },
                            )
                            if (viewMode == ViewMode.GRID) {
                                HorizontalDivider()
                                Text(if (landscape) "Colonne in orizzontale" else "Colonne in verticale", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    columnChoices.forEach { n ->
                                        FilterChip(selected = gridColumns == n, onClick = { vm.setGridColumns(n, landscape) }, label = { Text("$n") },
                                            modifier = Modifier.semantics { this.contentDescription = "$n colonne" })
                                    }
                                }
                            }
                            HorizontalDivider()
                            // A way into selection mode besides long-press.
                            DropdownMenuItem(
                                text = { Text("Seleziona tutto") },
                                leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                                enabled = files.isNotEmpty(),
                                onClick = { viewMenu = false; selection = files.map { it.file.id }.toSet() },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Selection actions at the bottom, labelled and within thumb reach.
            if (inSelection) {
                val allFav = files.filter { it.file.id in selection }.all { it.file.isFavorite }
                val videoCount = files.count { it.file.id in selection && VaultRepository.isVideo(it.file.mimeType) }
                // By codec, as in the viewer: an MP4 holding AV1/VP9 is offered too. The file type
                // gives a first answer at once, the codec check refines it.
                val convertibleIds by produceState(
                    initialValue = files.filter { it.file.id in selection && VaultRepository.isVideo(it.file.mimeType) && it.file.mimeType != "video/mp4" }
                        .map { it.file.id }.toSet(),
                    selection, files,
                ) { value = vm.convertibleIds(selection) }
                val convertible = convertibleIds.size
                val actions = buildList {
                    add(SelAction(Icons.Filled.Star, if (allFav) "Togli pref." else "Preferito") {
                        favoriteWithUndo(selection.toList(), !allFav); selection = emptySet()
                    })
                    add(SelAction(Icons.Filled.Label, "Etichette") {
                        if (selection.size == 1) tagTargetId = selection.first() else batchTag = true
                    })
                    add(SelAction(Icons.Filled.DriveFileMove, "Sposta") { showMove = true })
                    add(SelAction(Icons.Filled.Delete, "Elimina", destructive = true) { confirmMultiDelete = true })
                    if (convertible > 0) add(SelAction(Icons.Filled.Transform, if (convertible == 1) "Converti MP4" else "Converti ($convertible)") {
                        val ids = convertibleIds.toList()
                        if (!convertSettings.first) {
                            convertAsk = ids
                        } else {
                            vm.convertToMp4(ids)
                            notify("Conversione in coda: prosegue in background")
                        }
                        selection = emptySet()
                    })
                    if (videoCount > 0) add(SelAction(Icons.Filled.Image, if (videoCount == 1) "Copertina" else "Copertine ($videoCount)") {
                        showRegenCover = true
                    })
                    if (selection.size == 1) add(SelAction(Icons.Filled.Edit, "Rinomina") { renameTargetId = selection.first() })
                }
                SelectionBar(actions)
            }
        },
        floatingActionButton = {
            if (!inSelection) {
                AnimatedVisibility(
                    visible = fabsVisible,
                    enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 2 },
                    exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { it / 2 },
                ) {
                    // Single expanding FAB (speed-dial): collapsed it shows just "+"; tapping it
                    // reveals the labelled actions, so the screen isn't crowded by a stack of FABs.
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val actions = buildList {
                            add(FabAction("Importa file", Icons.Filled.EnhancedEncryption) { fabExpanded = false; startImport() })
                            add(FabAction("Nuova cartella", Icons.Filled.CreateNewFolder) { fabExpanded = false; showNewFolder = true })
                            if (display.showNoteFab) add(FabAction("Nuova nota", Icons.Filled.Description) { fabExpanded = false; onNewNote(path.lastOrNull()?.id) })
                            if (display.showRandomFab) {
                                val label = when {
                                    filters.active -> "Casuale tra i risultati"
                                    path.isNotEmpty() -> "Casuale in questa cartella"
                                    else -> "Casuale"
                                }
                                add(FabAction(label, Icons.Filled.Casino) { fabExpanded = false; vm.randomShuffleOpen(onOpenFile) })
                            }
                        }
                        Column(
                            // Landscape: capped + scrollable so the top action never slides under the bar.
                            (if (landscape) Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()) else Modifier),
                            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            actions.forEachIndexed { i, a ->
                                // Nearest to the FAB first, 30ms apart.
                                val delay = (actions.size - 1 - i) * 30
                                val origin = TransformOrigin(1f, 1f)
                                AnimatedVisibility(
                                    visible = fabExpanded,
                                    enter = fadeIn(Motion.enter(Motion.MEDIUM, delay)) +
                                        slideInVertically(Motion.enter(Motion.MEDIUM, delay)) { it / 3 } +
                                        scaleIn(Motion.enter(Motion.MEDIUM, delay), initialScale = 0.9f, transformOrigin = origin),
                                    exit = fadeOut(Motion.exit(Motion.SHORT)) +
                                        scaleOut(Motion.exit(Motion.SHORT), targetScale = 0.9f, transformOrigin = origin),
                                ) {
                                    MiniFabAction(a.label, a.icon, a.onClick)
                                }
                            }
                        }
                        // "+" turns 45 degrees into an "x" while the actions are open.
                        val fabRotation by animateFloatAsState(if (fabExpanded) 45f else 0f, Motion.enter(Motion.MEDIUM), label = "fabRot")
                        FloatingActionButton(
                            onClick = { fabExpanded = !fabExpanded },
                            modifier = Modifier.semantics { this.stateDescription = if (fabExpanded) "Aperto" else "Chiuso" },
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                if (fabExpanded) "Chiudi azioni" else "Azioni",
                                Modifier.graphicsLayer { rotationZ = fabRotation },
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
      Box(Modifier.fillMaxSize().padding(pad)) {
        Column(Modifier.fillMaxSize().nestedScroll(fabScroll)) {
            // Search field, revealed from the top-bar icon; it stays while a query is typed.
            val showSearch = searchExpanded || filters.query.isNotBlank()
            val searchFocus = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            LaunchedEffect(searchExpanded) {
                if (searchExpanded && filters.query.isBlank()) {
                    kotlinx.coroutines.delay(60)
                    runCatching { searchFocus.requestFocus() }
                }
            }
            AnimatedVisibility(
                visible = showSearch,
                enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
                exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
            ) {
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = vm::setQuery,
                    label = if (landscape) null else ({ Text("Cerca nome o etichetta") }),
                    placeholder = if (landscape) ({ Text("Cerca nome o etichetta") }) else null,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (filters.query.isNotEmpty()) ({
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Cancella ricerca") }
                    }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = if (landscape) 2.dp else 6.dp)
                        .focusRequester(searchFocus),
                )
            }
            // Landscape: the banners/strips above the grid get a capped, scrollable area so an
            // import in progress plus active filters can't squeeze the grid down to nothing.
            Column(if (landscape) Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()) else Modifier) {
            ActiveFilterBar(filters, tags, vm::setType, { vm.setFavoritesOnly(false) },
                { vm.setUntaggedOnly(false) }, vm::toggleTag, vm::toggleExcludedTag,
                { vm.clearFilters(); searchExpanded = false }, onClearQuery = { vm.setQuery("") })
            if (path.isNotEmpty() && !filters.active) {
                Breadcrumb(path.map { it.name }, onGo = vm::goToDepth)
            }
            ImportBanner(importState, onDismiss = vm::dismissImportResult,
                onRemoveDuplicates = { vm.removeImportDuplicates() })
            ConvertBanner(convertStatus, onCancel = vm::cancelConversion, onDismiss = vm::dismissConvertResult,
                onResolveOriginal = vm::resolveOriginal)
            if (stats.scope.total > 0 && display.showStatsStrip) {
                StatsStrip(stats, filters.active, filters.type, onOpen = { showStats = true }, onType = vm::toggleType)
            }
            }

            val manual = sortKey == SortKey.MANUAL
            // Folders stay visible in every sort (above the reorderable grid in Manual); a filter
            // searches the whole vault, so the current folder's subfolders don't apply then.
            val showFolders = folders.isNotEmpty() && !filters.active
            // Exactly the count chosen for the current orientation. Only a wide portrait window
            // (tablet, unfolded foldable) adds columns in proportion so cells don't grow huge.
            val screenW = LocalConfiguration.current.screenWidthDp
            val gridCount = if (!landscape && screenW >= 600) maxOf(gridColumns, (screenW * gridColumns / 460f).roundToInt()).coerceAtMost(12)
                else gridColumns
            val columnCount = if (viewMode == ViewMode.GRID) gridCount else if (screenW >= 840) 2 else 1
            val columns = GridCells.Fixed(columnCount)
            val grouped = remember(files, display.showDateHeaders) {
                if (display.showDateHeaders) groupByDay(files) else listOf("" to files)
            }
            val orderedIds = remember(grouped) { grouped.flatMap { it.second.map { f -> f.file.id } } }
            val idSet = remember(orderedIds) { orderedIds.toSet() }

            fun toggleSel(id: String) {
                selection = if (id in selection) selection - id else selection + id
            }
            fun openFile(id: String) { vm.publishViewerQueue(); onOpenFile(id) }
            /** TalkBack actions of a file cell (everything a long-press + bottom bar offers). */
            fun fileActions(fwt: FileWithTags): List<CustomAccessibilityAction> {
                val id = fwt.file.id
                return listOf(
                    CustomAccessibilityAction(if (fwt.file.isFavorite) "Togli dai preferiti" else "Aggiungi ai preferiti") {
                        favoriteWithUndo(listOf(id), !fwt.file.isFavorite); true
                    },
                    CustomAccessibilityAction("Etichette") { tagTargetId = id; true },
                    CustomAccessibilityAction("Rinomina") { renameTargetId = id; true },
                    CustomAccessibilityAction("Sposta") { selection = setOf(id); showMove = true; true },
                    CustomAccessibilityAction("Elimina") { selection = setOf(id); confirmMultiDelete = true; true },
                )
            }
            // Folder cards live in a horizontal row; their on-screen bounds let a drag-selection
            // still be dropped onto a folder (the grid's own hit-testing can't see inside the row).
            val folderBounds = remember { androidx.compose.runtime.mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
            var gridCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

            val folderRow: @Composable () -> Unit = {
                Column {
                    SectionLabel("Cartelle", folders.size)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(folders.size, key = { folders[it].id }) { i ->
                            val folder = folders[i]
                            androidx.compose.runtime.DisposableEffect(folder.id) {
                                onDispose { folderBounds.remove(folder.id) }
                            }
                            // While selecting, a folder tap would silently drop the selection: say why instead.
                            val open = {
                                if (inSelection) notify("Esci dalla selezione per aprire una cartella")
                                else vm.enterFolder(folder)
                            }
                            FolderMosaic(
                                folder = folder,
                                stat = folderStats[folder.id],
                                previews = folderPreviews[folder.id].orEmpty(),
                                thumb = { vm.thumb(it) },
                                showInfo = display.showFolderInfo,
                                highlighted = hoverFolder == folder.id,
                                onMore = { folderMenu = folder },
                                modifier = Modifier.width(if (landscape) 110.dp else 124.dp)
                                    .onGloballyPositioned { folderBounds[folder.id] = it.boundsInRoot() }
                                    .combinedClickable(
                                        onClickLabel = "Apri",
                                        onLongClickLabel = "Azioni cartella",
                                        onClick = open,
                                        onLongClick = { folderMenu = folder },
                                    )
                                    .semantics {
                                        this.customActions = listOf(
                                            CustomAccessibilityAction("Rinomina") { folderToRename = folder; true },
                                            CustomAccessibilityAction("Personalizza") { folderToStyle = folder; true },
                                            CustomAccessibilityAction("Elimina") { folderToDelete = folder; true },
                                        )
                                    },
                            )
                        }
                    }
                }
            }

            // Folder depth navigation: a new level slides in from the right (going up: from the
            // left) and fades in. The old level is hidden at once and the slide waits (briefly) for
            // the new level's data, so it's never the old content that moves. Each level's scroll
            // position is remembered and restored when coming back up.
            val density = LocalDensity.current
            val animOn = animationsEnabled()
            val levelX = remember { Animatable(0f) }
            val levelAlpha = remember { Animatable(1f) }
            val scrollMemory = remember { mutableMapOf<Long, Pair<Int, Int>>() }
            var shownLevel by remember { mutableStateOf(path.lastOrNull()?.id ?: ROOT_KEY) }
            var shownDepth by remember { mutableIntStateOf(path.size) }
            LaunchedEffect(path) {
                val newKey = path.lastOrNull()?.id ?: ROOT_KEY
                if (newKey == shownLevel) return@LaunchedEffect
                val deeper = path.size > shownDepth
                scrollMemory[shownLevel] = gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                shownLevel = newKey
                shownDepth = path.size
                val keep = path.map { it.id }.toSet() + ROOT_KEY
                scrollMemory.keys.retainAll(keep)
                if (animOn) {
                    levelAlpha.snapTo(0f)
                    withTimeoutOrNull(160) { snapshotFlow { files to folders }.drop(1).first() }
                }
                val restore = if (!deeper) scrollMemory[newKey] else null
                gridState.scrollToItem(restore?.first ?: 0, restore?.second ?: 0)
                if (animOn) {
                    levelX.snapTo(with(density) { 32.dp.toPx() } * if (deeper) 1f else -1f)
                    launch { levelX.animateTo(0f, Motion.enter(Motion.LONG)) }
                    levelAlpha.animateTo(1f, Motion.enter(Motion.MEDIUM))
                } else {
                    levelX.snapTo(0f); levelAlpha.snapTo(1f)
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { translationX = levelX.value; alpha = levelAlpha.value },
            ) {
            when {
                files.isEmpty() && !showFolders ->
                    EmptyState(filters.active, Modifier.fillMaxSize(),
                        onImport = { startImport() },
                        folderName = path.lastOrNull()?.name,
                        onClearFilters = { vm.clearFilters(); searchExpanded = false })
                manual ->
                    ReorderableFileGrid(
                        items = files,
                        columns = columnCount,
                        asList = viewMode == ViewMode.LIST,
                        selection = selection,
                        display = display,
                        coverOverrides = coverOverrides,
                        coverVersions = coverVersions,
                        thumb = { vm.thumb(it) },
                        onOpen = { openFile(it) },
                        onReorder = { vm.reorder(it) },
                        header = if (showFolders) folderRow else null,
                    )
                else -> Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                        .onGloballyPositioned { gridCoords = it }
                        // Tap: open a file (or toggle it in selection mode). A held finger marks the
                        // cell as pressed after 60ms, so starting a scroll doesn't flash it.
                        .pointerInput(orderedIds, inSelection) {
                            detectTapGestures(
                                onPress = { off ->
                                    val key = keyAt(off, gridState) as? String
                                    if (key != null && key in idSet) {
                                        val job = uiScope.launch { kotlinx.coroutines.delay(60); pressedKey = key }
                                        tryAwaitRelease()
                                        job.cancel()
                                        pressedKey = null
                                    }
                                },
                                onTap = { off ->
                                    val key = keyAt(off, gridState) as? String ?: return@detectTapGestures
                                    if (key in idSet) {
                                        if (inSelection) toggleSel(key) else openFile(key)
                                    }
                                },
                            )
                        }
                        // Long-press a file then drag = gallery-style range select (or deselect if
                        // the anchor was already selected); release over a folder to move there.
                        .pointerInput(orderedIds) {
                            val onStart: (Offset) -> Unit = { off ->
                                selPointer = off
                                hoverFolder = null
                                val key = keyAt(off, gridState) as? String
                                if (key != null && key in idSet) {
                                    dragAnchor = key
                                    dragDeselect = key in selection
                                    dragBase = selection
                                    if (!dragDeselect) selection = selection + key
                                }
                            }
                            val onMove: (Offset) -> Unit = { delta ->
                                selPointer += delta
                                val anchor = dragAnchor
                                if (anchor != null) {
                                    val curKey = keyAt(selPointer, gridState) as? String
                                    val rootPos = gridCoords?.takeIf { it.isAttached }?.localToRoot(selPointer)
                                    val overFolder = rootPos?.let { p -> folderBounds.entries.firstOrNull { it.value.contains(p) }?.key }
                                    if (overFolder != null) {
                                        hoverFolder = overFolder
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
                                    val ids = selection.toList()
                                    selection = emptySet()
                                    moveWithUndo(ids, target)
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
                        // Folders in their own scrollable row, so the files below stay a uniform grid.
                        item(key = FOLDERS_KEY, span = { GridItemSpan(maxLineSpan) }) { folderRow() }
                    }
                    grouped.forEach { (label, group) ->
                        if (label.isNotEmpty()) header(label, group.size)
                        items(group, key = { it.file.id }, span = { GridItemSpan(1) }) { fwt ->
                            val id = fwt.file.id
                            val isSel = id in selection
                            // Gestures are handled by the grid (drag range-select); each cell still
                            // exposes its actions and state to accessibility services.
                            val cellSemantics = Modifier.semantics(mergeDescendants = true) {
                                if (inSelection) this.selected = isSel
                                onClick(label = if (inSelection) (if (isSel) "Deseleziona" else "Seleziona") else "Apri") {
                                    if (inSelection) toggleSel(id) else openFile(id)
                                    true
                                }
                                onLongClick(label = if (isSel) "Deseleziona" else "Seleziona") { toggleSel(id); true }
                                this.customActions = fileActions(fwt)
                            }
                            FileCell(fwt, viewMode, isSel, inSelection, display,
                                // No placement animation: toggling a filter reshuffles the whole
                                // result set and every cell used to visibly slide to its new slot.
                                Modifier.animateItem(placementSpec = null)
                                    .pressScale(pressedKey == id, if (viewMode == ViewMode.GRID) 0.96f else 0.98f)
                                    .then(cellSemantics),
                                coverOverrides[id], coverVersions[id] ?: 0,
                                { vm.thumb(fwt.file) })
                        }
                    }
                    }
                    // Label per grid item for the fast-scroll bubble: where you are (day / letter /
                    // size, depending on the sort) and the position in the list.
                    val scrollLabels = remember(grouped, showFolders, sortKey) {
                        val out = ArrayList<String?>()
                        if (showFolders) out += "Cartelle"
                        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                        val total = grouped.sumOf { it.second.size }
                        var n = 0
                        grouped.forEach { (label, group) ->
                            if (label.isNotEmpty()) out += label
                            group.forEach { f ->
                                n++
                                val key = when (sortKey) {
                                    SortKey.NAME -> f.file.originalName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                                    SortKey.SIZE -> com.cripta.app.ui.components.formatBytes(f.file.sizeBytes)
                                    else -> label.ifEmpty {
                                        Instant.ofEpochMilli(f.file.importedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)
                                    }
                                }
                                out += "$key · $n/$total"
                            }
                        }
                        out
                    }
                    FastScroller(gridState, Modifier.align(Alignment.CenterEnd), labelAt = { scrollLabels.getOrNull(it) })
                }
            }
            }
        }
        // Scrim behind the open speed-dial: dims the content and closes the menu on tap.
        AnimatedVisibility(
            visible = fabExpanded && !inSelection,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { fabExpanded = false } }
                    .semantics {
                        this.contentDescription = "Chiudi menu azioni"
                        onClick(label = "Chiudi") { fabExpanded = false; true }
                    },
            )
        }
      }
    }

    if (showNewFolder) {
        TextPromptDialog("Nuova cartella", "Nome", confirmLabel = "Crea",
            onConfirm = { name -> vm.createFolder(name); showNewFolder = false; notify("Cartella \"$name\" creata") },
            onDismiss = { showNewFolder = false })
    }

    renameTargetId?.let { id ->
        val current = files.firstOrNull { it.file.id == id }?.file?.originalName ?: ""
        TextPromptDialog("Rinomina file", "Nome", initial = current, selectBaseName = true, confirmLabel = "Rinomina",
            onConfirm = { name ->
                vm.renameFile(id, name); renameTargetId = null; selection = emptySet()
                if (name != current) notify("Rinominato in \"$name\"") { vm.renameFile(id, current) }
            },
            onDismiss = { renameTargetId = null })
    }

    tagTargetId?.let { id ->
        val target = files.firstOrNull { it.file.id == id }
        TagEditorDialog(
            allTags = tags,
            initialSelected = target?.tags?.map { it.name } ?: emptyList(),
            onConfirm = { names ->
                val before = tagsOf(listOf(id))
                vm.setTags(id, names); tagTargetId = null; selection = emptySet()
                if (before[id].orEmpty().toSet() != names.toSet()) notify("Etichette aggiornate") { vm.restoreTags(before) }
            },
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
            onConfirm = { names ->
                val ids = selection.toList()
                val before = tagsOf(ids)
                vm.addTagsToFiles(ids, names); batchTag = false; selection = emptySet()
                notify("Etichette aggiunte a ${countLabel(ids.size)}") { vm.restoreTags(before) }
            },
            onRemove = { names ->
                val ids = selection.toList()
                val before = tagsOf(ids)
                vm.removeTagsFromFiles(ids, names); batchTag = false; selection = emptySet()
                notify("Etichette rimosse da ${countLabel(ids.size)}") { vm.restoreTags(before) }
            },
            onCreateTag = { name, alias -> vm.createTag(name, alias) },
            onDismiss = { batchTag = false },
            showRecents = display.showRecentTags,
        )
    }

    folderMenu?.let { folder ->
        com.cripta.app.ui.components.CriptaSheet(onDismissRequest = { folderMenu = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp).semantics { heading() })
                // Folder details (moved here from under the card): contents, space, creation date.
                Text(
                    folderSubtitle(folderStats[folder.id]) + " · creata il " +
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(folder.createdAt)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
                )
                SheetAction(Icons.Filled.Folder, "Apri") {
                    folderMenu = null
                    if (inSelection) notify("Esci dalla selezione per aprire una cartella") else vm.enterFolder(folder)
                }
                SheetAction(Icons.Filled.DriveFileRenameOutline, "Rinomina") { folderToRename = folder; folderMenu = null }
                SheetAction(Icons.Filled.Palette, "Personalizza") { folderToStyle = folder; folderMenu = null }
                SheetAction(Icons.Filled.Delete, "Elimina", destructive = true) { folderToDelete = folder; folderMenu = null }
            }
        }
    }

    folderToRename?.let { folder ->
        TextPromptDialog("Rinomina cartella", "Nome", initial = folder.name, confirmLabel = "Rinomina",
            onConfirm = { name ->
                vm.renameFolder(folder, name); folderToRename = null
                if (name != folder.name) notify("Cartella rinominata") { vm.renameFolder(folder, folder.name) }
            },
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
            resultCount = files.size,
        )
    }

    convertAsk?.let { ids ->
        com.cripta.app.ui.components.ConvertChoiceDialog(
            count = ids.size,
            trashDays = convertSettings.second,
            onConfirm = { after, rememberIt ->
                vm.convertToMp4(ids, after, rememberIt)
                convertAsk = null
                notify("Conversione in coda: prosegue in background")
            },
            onDismiss = { convertAsk = null },
        )
    }

    if (showStats) {
        StatsSheet(stats, filters.active, path.lastOrNull()?.name, subtree = path.lastOrNull()?.let { folderStats[it.id] }, onDismiss = { showStats = false })
    }

    if (confirmMultiDelete) {
        val n = selection.size
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmMultiDelete = false },
            title = { Text(if (n == 1) "Eliminare 1 file?" else "Eliminare $n file?") },
            text = {
                Text(if (trashEnabled) "Verranno spostati nel cestino: potrai ripristinarli da Impostazioni › Archivio."
                    else "Eliminazione sicura (crypto-shredding). Irreversibile.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFiles(selection.toList()); selection = emptySet(); confirmMultiDelete = false
                    notify(
                        if (trashEnabled) (if (n == 1) "1 file spostato nel cestino" else "$n file spostati nel cestino")
                        else (if (n == 1) "1 file eliminato" else "$n file eliminati"),
                    )
                }) {
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
                notify("Rigenerazione copertine…")
            },
            onDismiss = { showRegenCover = false },
        )
    }

    if (showMove) {
        MoveToFolderDialog(allFolders,
            onPick = { fid -> val ids = selection.toList(); selection = emptySet(); showMove = false; moveWithUndo(ids, fid) },
            onDismiss = { showMove = false })
    }

    folderToDelete?.let { folder ->
        val n = folderStats[folder.id]?.count ?: 0
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Eliminare la cartella?") },
            text = {
                Text(
                    when {
                        trashEnabled -> "\"${folder.name}\"" + (if (n == 0) "" else " e tutto il suo contenuto (${if (n == 1) "1 file" else "$n file"}, sottocartelle incluse)") +
                            " andrà nel cestino. Da Impostazioni › Archivio puoi aprirla e ripristinarla tutta o solo alcuni file."
                        n == 0 -> "\"${folder.name}\" è vuota e verrà eliminata."
                        else -> "\"${folder.name}\" e tutto il suo contenuto (${if (n == 1) "1 file" else "$n file"}, " +
                            "sottocartelle incluse) verranno eliminati in modo sicuro. Irreversibile."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder); folderToDelete = null
                    notify(if (trashEnabled) "Cartella \"${folder.name}\" nel cestino" else "Cartella \"${folder.name}\" eliminata")
                }) {
                    Text(if (trashEnabled) "Sposta nel cestino" else if (n == 0) "Elimina" else "Elimina tutto", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Annulla") } },
        )
    }

    if (pendingOriginals.isNotEmpty()) {
        val n = pendingOriginals.size
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { vm.clearPendingOriginals() },
            title = { Text(if (n == 1) "Eliminare l'originale?" else "Eliminare gli originali?") },
            text = {
                Text(
                    (if (n == 1) "1 file importato nel vault. Eliminare la copia originale dal dispositivo?"
                    else "$n file importati nel vault. Eliminare le copie originali dal dispositivo?") +
                        " (Non è una cancellazione sicura dell'originale.)",
                )
            },
            confirmButton = { TextButton(onClick = { vm.deleteOriginals(pendingOriginals) }) { Text(if (n == 1) "Elimina originale" else "Elimina originali") } },
            dismissButton = { TextButton(onClick = { vm.clearPendingOriginals() }) { Text("Mantieni") } },
        )
    }
}

/** "3 selezionati" in the top bar; the number rolls up when it grows and down when it shrinks. */
@Composable
private fun SelectionCountTitle(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(Motion.enter()) { h -> if (up) h else -h } + fadeIn(Motion.enter())) togetherWith
                    (slideOutVertically(Motion.exit()) { h -> if (up) -h else h } + fadeOut(Motion.exit())) using
                    SizeTransform(clip = true)
            },
            label = "selCount",
        ) { n -> Text("$n", maxLines = 1) }
        Text(if (count == 1) " selezionato" else " selezionati", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

private fun LazyGridScope.header(text: String, count: Int? = null) {
    item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(text, count) }
}

/** Light section label: "OGGI · 12" in small caps, instead of a big title. */
@Composable
private fun SectionLabel(text: String, count: Int? = null) {
    Text(
        text.uppercase() + (count?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 2.dp).semantics { heading() },
    )
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.BoxScope.FastScroller(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier,
    labelAt: (Int) -> String? = { null },
) {
    val total = state.layoutInfo.totalItemsCount
    if (total <= 0) return
    val scope = rememberCoroutineScope()
    var trackH by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Where the finger put the thumb: while dragging the thumb follows the finger exactly instead
    // of the list, whose position moves in whole rows (with few files that made it jump in steps).
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // Scroll position as a continuous 0..1: the first visible item plus how far its row is scrolled,
    // against the first index reachable at the very end (not the last index, which is never at the
    // top: with a short list the thumb stopped halfway and then snapped to the bottom).
    val listFraction by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val vis = info.visibleItemsInfo
            when {
                info.totalItemsCount <= 1 || vis.isEmpty() || !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> {
                    val first = vis.first()
                    val cols = vis.count { it.offset.y == first.offset.y }.coerceAtLeast(1)
                    val pos = first.index + state.firstVisibleItemScrollOffset.toFloat() / first.size.height.coerceAtLeast(1) * cols
                    val maxPos = (info.totalItemsCount - vis.size).coerceAtLeast(1)
                    (pos / maxPos).coerceIn(0f, 1f)
                }
            }
        }
    }
    val fraction = if (dragging) dragFraction else listFraction
    val active = state.isScrollInProgress || dragging
    val alpha by animateFloatAsState(if (active) 1f else 0f, label = "fastscroll")
    // The strip only takes drags while the scroller is visible: when hidden, touches on the right
    // edge go to the grid (select, open, scroll) instead of an invisible control.
    val visible = active || alpha > 0.05f
    val thumbW by animateDpAsState(if (dragging) 10.dp else 6.dp, Motion.enter(Motion.SHORT), label = "thumbW")
    val density = LocalDensity.current
    val thumbH = 48.dp
    val thumbPx = with(density) { thumbH.toPx() }
    val maxOffset = (trackH - thumbPx).coerceAtLeast(0f)
    val offsetY = with(density) { (fraction * maxOffset).toDp() }
  Box(modifier.fillMaxHeight()) {
    // Bubble next to the thumb while scrolling: section / position of the first visible item.
    val label = if (alpha > 0f) labelAt(state.firstVisibleItemIndex) else null
    if (label != null) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 30.dp)
                .offset(y = offsetY + 6.dp)
                .graphicsLayer { this.alpha = alpha },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    Box(
        Modifier.align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { trackH = it.size.height.toFloat() }
            .then(if (!visible) Modifier else Modifier.pointerInput(total, trackH) {
                // Grabbed on the thumb: it keeps the same point under the finger (no jump). Grabbed
                // elsewhere on the track: the thumb centres on the finger.
                var grab = 0f
                fun follow(y: Float) {
                    if (maxOffset <= 0f) return
                    dragFraction = ((y - grab) / maxOffset).coerceIn(0f, 1f)
                    // Same mapping as listFraction, with the offset inside the row, so the list
                    // glides under the thumb instead of stepping a row at a time.
                    val info = state.layoutInfo
                    val vis = info.visibleItemsInfo
                    val cols = vis.firstOrNull()?.let { f -> vis.count { it.offset.y == f.offset.y } }?.coerceAtLeast(1) ?: 1
                    val rowH = vis.firstOrNull()?.size?.height ?: 0
                    val pos = dragFraction * (total - vis.size).coerceAtLeast(1)
                    val idx = pos.toInt().coerceIn(0, total - 1)
                    val rowStart = idx - idx % cols
                    val within = ((pos - rowStart) / cols * rowH).roundToInt().coerceAtLeast(0)
                    scope.launch { state.scrollToItem(rowStart, within) }
                }
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        val top = listFraction * maxOffset
                        grab = if (o.y in top..(top + thumbPx)) o.y - top else thumbPx / 2f
                        dragFraction = listFraction
                        dragging = true
                        follow(o.y)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ -> change.consume(); follow(change.position.y) },
                )
            }),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(end = 3.dp)
                .offset(y = offsetY)
                .width(thumbW)
                .height(thumbH)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
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
    coverOverride: android.graphics.Bitmap?,
    coverVersion: Int,
    thumb: suspend () -> android.graphics.Bitmap?,
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
                ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite,
                    emptyList(), Modifier.size(72.dp), file = item.file, display = display, compact = true,
                    selectionMode = selectionMode)
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
        ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite,
            if (display.showTagsOnCover) item.tags else emptyList(),
            Modifier.fillMaxWidth().aspectRatio(1f), file = item.file, display = display, selectionMode = selectionMode)
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
    /** Image description; null inside cells, where the file name is already shown as text. */
    name: String?,
    selected: Boolean,
    favorite: Boolean,
    tags: List<TagEntity>,
    modifier: Modifier,
    file: com.cripta.app.data.db.FileEntity? = null,
    display: DisplayPrefs? = null,
    /** Small list-mode thumbnail: only the duration, bottom-right. */
    compact: Boolean = false,
    /** Selection mode: unselected covers show the empty ring where the check lands. */
    selectionMode: Boolean = false,
) {
    val duration = if (isVideo && display?.showDurationBadge != false)
        com.cripta.app.ui.components.formatDuration(file?.durationMs) else null
    val quality = if (isVideo && !compact && display?.showQualityBadge != false)
        qualityLabel(file?.width, file?.height) else null
    SelectableThumbFrame(
        selected = selected,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        selectedScale = if (compact) 0.9f else 0.92f,
        overlay = { CornerSelectionCheck(selected, selectionMode, size = if (compact) 20.dp else 24.dp) },
    ) {
        // Without a cover, a tint per type (video blue / photo green / other amber) instead of flat grey.
        val typeTint = when {
            isVideo -> Color(0xFF3B82F6)
            fallbackIcon == Icons.Filled.Image -> Color(0xFF22C55E)
            else -> Color(0xFFF59E0B)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        ThumbCrossfade(bmp, contentDescription = name, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(typeTint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(fallbackIcon, null, tint = typeTint, modifier = Modifier.size(if (compact) 26.dp else 32.dp))
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
        if (compact) {
            if (duration != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(3.dp)) { CornerBadge(duration) }
            }
        } else if (!selectionMode && !selected && (duration != null || quality != null)) {
            // Top-right (the selection check's corner in selection mode), so the bottom stays free for tags.
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
                // Filled from the bottom: the full rows sit on the cover's edge and the shorter
                // last row (plus the "+N" badge) goes on top, so it hides less of the image.
                rows.asReversed().forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { i -> TagBadge(tags[i], texts[i], display, availDp) }
                        if (r == 0 && extra > 0) TagBadge(null, "+$extra", display, availDp)
                    }
                }
            }
        }
    }
}

/**
 * Compact, at-a-glance summary of active filters (search text included) with one-tap removal, and
 * a note that results come from the whole vault. Expands/collapses when filters turn on/off.
 */
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
    onClearQuery: () -> Unit,
) {
    // Keep drawing the last active filters while the bar collapses.
    val last = remember { mutableStateOf(filters) }
    SideEffect { if (filters.active) last.value = filters }
    val f = if (filters.active) filters else last.value
    val tagById = remember(tags) { tags.associateBy { it.id } }
    AnimatedVisibility(
        visible = filters.active,
        enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
        exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .animateContentSize(Motion.enter())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (f.query.isNotBlank()) DismissChip("“${f.query}”", "ricerca ${f.query}") { onClearQuery() }
                if (f.type != TypeFilter.ALL) DismissChip(typeLabel(f.type)) { onType(TypeFilter.ALL) }
                if (f.favoritesOnly) DismissChip("Preferiti") { onClearFav() }
                if (f.untaggedOnly) DismissChip("Senza etichette") { onClearUntagged() }
                f.tagIds.forEach { id ->
                    val t = tagById[id] ?: return@forEach
                    DismissChip("#${t.name}", "etichetta ${t.name}") { onTag(id) }
                }
                f.excludedTagIds.forEach { id ->
                    val t = tagById[id] ?: return@forEach
                    DismissChip("⊘ #${t.name}", "esclusione etichetta ${t.name}") { onExcludeTag(id) }
                }
                TextButton(onClick = onClearAll) { Text("Azzera") }
            }
            // The title still names the folder, but a filter searches everywhere: say so.
            Text("Risultati da tutto il vault, sottocartelle incluse",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
        }
    }
}

/** Removable filter chip: 48dp touch target, announced as "Rimuovi filtro …". */
@Composable
private fun DismissChip(label: String, description: String = label, onClear: () -> Unit) {
    Surface(onClick = onClear, color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1, modifier = Modifier.clearAndSetSemantics { this.contentDescription = "Rimuovi filtro $description" })
            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

private enum class TagFilterState { NEUTRAL, INCLUDE, EXCLUDE }

/** Tri-state tag chip: neutral, include (primary), exclude (error + strike-through). */
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
    // Clickable Surface = 48dp touch target; the state is spoken, not only shown by colour.
    Surface(
        onClick = onClick,
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics {
            this.stateDescription = when (state) {
                TagFilterState.INCLUDE -> "Inclusa"
                TagFilterState.EXCLUDE -> "Esclusa"
                TagFilterState.NEUTRAL -> "Non filtrata"
            }
        },
    ) {
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

/**
 * Sort + filter sheet. A fixed header keeps the live result count and "Azzera" in reach even at half
 * height; below it the groups follow the order people reach for them: saved filters, order, what
 * to show, then the (possibly long) tag library last. Every change applies at once; the sheet is
 * only closed by the user. Tags keep the library's fixed order so each one is always in its place.
 */
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
    /** Files matching right now, shown in the header (-1 = unknown). */
    resultCount: Int = -1,
) {
    // The shared sheet: opens at half height (drag up for the rest), one swipe down closes it.
    var naming by remember { mutableStateOf(false) }
    val activeCount = (if (filters.type != TypeFilter.ALL) 1 else 0) + (if (filters.favoritesOnly) 1 else 0) +
        (if (filters.untaggedOnly) 1 else 0) + filters.tagIds.size + filters.excludedTagIds.size
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss, wideInLandscape = true) {
        // ---- Header: what the filters give, and the way back.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Filtri e ordine", style = MaterialTheme.typography.titleLarge)
                val summary = listOfNotNull(
                    resultCount.takeIf { it >= 0 }?.let { if (it == 1) "1 file" else "$it file" },
                    activeCount.takeIf { it > 0 }?.let { if (it == 1) "1 filtro attivo" else "$it filtri attivi" },
                ).joinToString(" · ").ifEmpty { "Nessun filtro attivo" }
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            }
            TextButton(onClick = onClear, enabled = filters.active) { Text("Azzera") }
        }
        HorizontalDivider()

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ---- Saved filters: one tap applies; the current set can be saved from here. Always
            // shown (the save chip is just disabled without filters): appearing with the first
            // filter pushed everything below it down under the finger.
            run {
                FilterGroup("Filtri salvati") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        saved.forEach { sf ->
                            androidx.compose.material3.InputChip(
                                selected = false,
                                onClick = { onApplySaved(sf.json) },
                                label = { Text(sf.name, maxLines = 1) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, "Elimina filtro salvato ${sf.name}",
                                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onDeleteSaved(sf.id) })
                                },
                            )
                        }
                        androidx.compose.material3.AssistChip(
                            onClick = { naming = true },
                            enabled = filters.active,
                            label = { Text("Salva questi filtri") },
                            leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            // ---- Order: key as one segmented control, direction spelled out.
            FilterGroup("Ordina per") {
                val keys = listOf(SortKey.DATE to "Data", SortKey.NAME to "Nome", SortKey.SIZE to "Peso", SortKey.MANUAL to "Manuale")
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    keys.forEachIndexed { i, (k, lbl) ->
                        SegmentedButton(
                            selected = sortKey == k,
                            onClick = { onSort(k, sortAscending) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, keys.size),
                            icon = {},
                            label = { Text(lbl, maxLines = 1) },
                        )
                    }
                }
                // Fixed-height slot so switching to/from Manual doesn't resize (and slide) the sheet.
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = sortKey == SortKey.MANUAL,
                        transitionSpec = { fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.SHORT)) },
                        label = "sortDirection",
                    ) { manual ->
                        if (manual) {
                            Text("Tieni premuto un file e trascinalo per riordinarlo.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val (descLabel, ascLabel) = when (sortKey) {
                                SortKey.DATE -> "Più recenti" to "Meno recenti"
                                SortKey.NAME -> "Z → A" to "A → Z"
                                SortKey.SIZE -> "Più grandi" to "Più piccoli"
                                else -> "Decrescente" to "Crescente"
                            }
                            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !sortAscending, onClick = { onSort(sortKey, false) },
                                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                                    icon = { Icon(Icons.Filled.ArrowDownward, null, Modifier.size(18.dp)) },
                                    label = { Text(descLabel, maxLines = 1) },
                                )
                                SegmentedButton(
                                    selected = sortAscending, onClick = { onSort(sortKey, true) },
                                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                                    icon = { Icon(Icons.Filled.ArrowUpward, null, Modifier.size(18.dp)) },
                                    label = { Text(ascLabel, maxLines = 1) },
                                )
                            }
                        }
                    }
                }
            }

            // ---- What to show: type, then two quick narrowing switches as chips.
            FilterGroup("Mostra") {
                val types = TypeFilter.entries
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = filters.type == t,
                            onClick = { onType(t) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, types.size),
                            icon = {},
                            label = { Text(typeLabel(t), maxLines = 1) },
                        )
                    }
                }
                com.cripta.app.ui.components.ChipFlowRow {
                    FilterChip(
                        selected = filters.favoritesOnly, onClick = onFav,
                        label = { Text("Solo preferiti") },
                        leadingIcon = {
                            Icon(if (filters.favoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                                Modifier.size(FilterChipDefaults.IconSize))
                        },
                    )
                    FilterChip(
                        selected = filters.untaggedOnly, onClick = onUntagged,
                        label = { Text("Senza etichette") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.LabelOff, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
            }

            // ---- Tags: tri-state chips in library order, a search once the library is long.
            if (tags.isNotEmpty()) {
                FilterGroup(
                    "Etichette",
                    trailing = {
                        val n = filters.tagIds.size + filters.excludedTagIds.size
                        if (n > 0) Text("$n scelte", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    },
                ) {
                    Text("Un tocco include, il secondo esclude (barrata), il terzo toglie.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // With two or more tags included: must a file have all of them, or is one enough?
                    // Always in place (disabled below two tags): appearing with the second tag moved
                    // every chip down, so the next tap landed on a different tag.
                    val canMatch = filters.tagIds.size >= 2
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("File con", style = MaterialTheme.typography.bodyMedium,
                            color = if (canMatch) androidx.compose.ui.graphics.Color.Unspecified
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 12.dp))
                        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            SegmentedButton(
                                selected = filters.tagMatchAll, onClick = { onTagMatchAll(true) }, enabled = canMatch,
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                                icon = {}, label = { Text("tutte") },
                            )
                            SegmentedButton(
                                selected = !filters.tagMatchAll, onClick = { onTagMatchAll(false) }, enabled = canMatch,
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                                icon = {}, label = { Text("almeno una") },
                            )
                        }
                    }
                    var query by remember { mutableStateOf("") }
                    if (tags.size > 12) {
                        androidx.compose.material3.OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Cerca un'etichetta") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Svuota ricerca") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val q = query.trim()
                    val shown = if (q.isEmpty()) tags else tags.filter {
                        it.name.contains(q, ignoreCase = true) || it.alias?.contains(q, ignoreCase = true) == true
                    }
                    if (shown.isEmpty()) {
                        Text("Nessuna etichetta corrisponde a \"$q\".", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    com.cripta.app.ui.components.ChipFlowRow {
                        shown.forEach { tag ->
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
            }
        }
    }
    if (naming) {
        TextPromptDialog("Salva filtro", "Nome (es. Video da vedere)", confirmLabel = "Salva",
            onConfirm = { onSave(it); naming = false },
            onDismiss = { naming = false })
    }
}

/** A titled group of the filter sheet (title row with an optional trailing element, then content). */
@Composable
private fun FilterGroup(
    title: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

/**
 * Drag-to-reorder used by the Manual sort in BOTH grid and list. Reorder starts only after a
 * long press (so a stray tap never changes the order), then the item lifts, follows the finger and
 * the target slot is found by hit-testing the grid's layout info (works for any cell size / column
 * count). Holding it near the top/bottom edge scrolls the grid. [header] (the folders row) stays
 * above the items. TalkBack users get "Sposta prima / dopo" actions instead of the drag.
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
    header: (@Composable () -> Unit)? = null,
) {
    val list = remember { mutableStateListOf<FileWithTags>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) { list.clear(); list.addAll(items) } }

    val gridState = rememberLazyGridState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }    // finger position in the grid viewport
    var pressLocal by remember { mutableStateOf(Offset.Zero) } // grab point inside the cell
    val density = LocalDensity.current
    val itemShape = MaterialTheme.shapes.medium

    /** Move the dragged item to the slot under the finger (file cells only, never the header). */
    fun retarget() {
        val id = draggedId ?: return
        val from = list.indexOfFirst { it.file.id == id }
        val hitKey = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != FOLDERS_KEY &&
                pointer.x >= info.offset.x && pointer.x <= info.offset.x + info.size.width &&
                pointer.y >= info.offset.y && pointer.y <= info.offset.y + info.size.height
        }?.key as? String ?: return
        val target = list.indexOfFirst { it.file.id == hitKey }
        if (from >= 0 && target >= 0 && target != from) list.add(target, list.removeAt(from))
    }

    /** Accessibility reorder: one step earlier (-1) or later (+1). */
    fun moveBy(id: String, delta: Int) {
        val from = list.indexOfFirst { it.file.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        onReorder(list.map { it.file.id })
    }

    // Auto-scroll while the item is held near the top or bottom edge (faster the closer it is).
    LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (draggedId != null) {
            val h = gridState.layoutInfo.viewportSize.height.toFloat()
            val y = pointer.y
            val step = when {
                h <= 0f -> 0f
                y < edge -> -maxStep * ((edge - y) / edge).coerceIn(0f, 1f)
                y > h - edge -> maxStep * ((y - (h - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f) {
                gridState.scrollBy(step)
                retarget()
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Ordine manuale · tieni premuto un elemento e trascinalo per riordinarlo",
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
            if (header != null) {
                item(key = FOLDERS_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
            }
            itemsIndexed(list, key = { _, it -> it.file.id }) { _, fwt ->
                val isDragged = fwt.file.id == draggedId
                val loaded by produceState<android.graphics.Bitmap?>(initialValue = null, fwt.file.id, coverVersions[fwt.file.id] ?: 0) { value = thumb(fwt.file) }
                val bmp = coverOverrides[fwt.file.id] ?: loaded
                // The held item lifts: slightly larger, with a shadow.
                val lift by animateFloatAsState(if (isDragged) 1.05f else 1f, Motion.enter(Motion.SHORT), label = "lift")
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
                            scaleX = lift; scaleY = lift
                            if (isDragged) {
                                shadowElevation = 8.dp.toPx()
                                shape = itemShape
                                clip = false
                                val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId }
                                if (info != null) {
                                    translationX = pointer.x - pressLocal.x - info.offset.x
                                    translationY = pointer.y - pressLocal.y - info.offset.y
                                }
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            onClick(label = "Apri") { onOpen(fwt.file.id); true }
                            this.customActions = listOf(
                                CustomAccessibilityAction("Sposta prima") { moveBy(fwt.file.id, -1); true },
                                CustomAccessibilityAction("Sposta dopo") { moveBy(fwt.file.id, 1); true },
                            )
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
                                    retarget()
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
            // Min height, not fixed: large fonts grow the row instead of clipping it.
            modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite, emptyList(), Modifier.size(52.dp),
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
                ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite,
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
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Text("Sposta in…", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            Row(
                Modifier.fillMaxWidth().clickable { afterSheet { onPick(null) } }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Text("Radice", Modifier.padding(start = 16.dp))
            }
            folders.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { afterSheet { onPick(f.id) } }
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
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Rigenera copertina" else "Rigenera copertina ($count)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personalizza cartella") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Colore", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ColorSwatch(null, color == null, "Nessun colore") { color = null }
                    com.cripta.app.ui.components.FOLDER_COLORS.forEachIndexed { i, c ->
                        ColorSwatch(c, color == c, FOLDER_COLOR_NAMES.getOrElse(i) { "Colore ${i + 1}" }) { color = c }
                    }
                }
                Text("Emoji", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EmojiPick("✕", emoji.isBlank(), "Nessuna emoji") { emoji = "" }
                    emojis.forEach { e -> EmojiPick(e, emoji == e, "Emoji $e") { emoji = e } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color, emoji.ifBlank { null }) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Spoken names of [com.cripta.app.ui.components.FOLDER_COLORS], same order. */
private val FOLDER_COLOR_NAMES = listOf("Blu", "Verde", "Ambra", "Rosso", "Viola", "Turchese", "Rosa", "Grigio")

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
            color == null -> Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            selected -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmojiPick(label: String, selected: Boolean, description: String, onClick: () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics { this.role = Role.RadioButton },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                .clearAndSetSemantics { this.contentDescription = description })
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
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

@Composable
private fun EmptyState(
    filtering: Boolean,
    modifier: Modifier,
    onImport: () -> Unit = {},
    folderName: String? = null,
    onClearFilters: () -> Unit = {},
) {
    // Landscape: smaller badge and padding + scroll, so the import button is always reachable.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val outer = if (landscape) modifier.verticalScroll(rememberScrollState()).padding(16.dp) else modifier.padding(32.dp)
    val badge = if (landscape) 56.dp else 96.dp
    val glyph = if (landscape) 28.dp else 44.dp
    if (!filtering && folderName != null) {
        // Empty folder: say where we are and how to fill it.
        Column(outer, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(badge)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(glyph))
                }
            }
            Text("\"$folderName\" è vuota", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp),
                textAlign = TextAlign.Center)
            Text("Importa qui dei file, oppure torna indietro, tieni premuto un file e trascinalo su questa cartella.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onImport, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Filled.EnhancedEncryption, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Importa qui")
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
                    if (filtering) Icons.Filled.Search else Icons.Filled.EnhancedEncryption,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(glyph),
                )
            }
        }
        Text(if (filtering) "Nessun risultato" else "Vault vuoto",
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
        Text(if (filtering) "Nessun file in tutto il vault corrisponde ai filtri attivi." else "Importa foto e video: restano cifrati e visibili solo qui.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (filtering) {
            androidx.compose.material3.OutlinedButton(onClick = onClearFilters, modifier = Modifier.padding(top = 20.dp)) {
                Text("Azzera filtri")
            }
        } else {
            Button(onClick = onImport, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Filled.EnhancedEncryption, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Importa file")
            }
        }
    }
}

/** One labelled row in the speed-dial: a name pill next to a small FAB. */
@Composable
private fun MiniFabAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // The name pill is tappable too (bigger target); TalkBack gets a single node, the button.
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.clearAndSetSemantics { }) {
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
                    val eta = com.cripta.app.ui.components.rememberEta(state.fraction, state.active)
                    Text((eta?.replaceFirstChar { it.uppercase() }?.let { "$it. " } ?: "") +
                        "Puoi uscire dall'app: l'importazione continua in background.",
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
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Riepilogo", onClick = onOpen).padding(horizontal = 6.dp, vertical = 4.dp))
        CountPill(Icons.Filled.Movie, shown.videos, "video", type == TypeFilter.VIDEO) { onType(TypeFilter.VIDEO) }
        CountPill(Icons.Filled.Image, shown.images, "foto", type == TypeFilter.IMAGE) { onType(TypeFilter.IMAGE) }
        if (shown.others > 0 || type == TypeFilter.OTHER) {
            CountPill(Icons.AutoMirrored.Filled.InsertDriveFile, shown.others, "altri file", type == TypeFilter.OTHER) { onType(TypeFilter.OTHER) }
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
                    .then(if (last) Modifier else Modifier.clickable(onClickLabel = "Vai a $n", role = Role.Button) { onGo(i) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            )
            if (!last) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun CountPill(icon: ImageVector, count: Int, what: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics(mergeDescendants = true) { this.contentDescription = "$count $what, mostra solo $what" }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.padding(start = 3.dp))
    }
}

/** Full-screen-ish breakdown: how many videos (and other media) are loaded, filtered or not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsSheet(stats: VaultStats, filtering: Boolean, folderName: String?, subtree: com.cripta.app.data.FolderStat? = null, onDismiss: () -> Unit) {
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss, wideInLandscape = true) {
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
                // The tiles below count this folder only; show the whole subtree too when it differs.
                if (!filtering && subtree != null && subtree.count != stats.scope.total) {
                    Text("Con le sottocartelle: ${subtree.count} file · ${com.cripta.app.ui.components.formatBytes(subtree.bytes)}",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
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
fun ConvertBanner(
    state: VaultRepository.ConvertStatus,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onResolveOriginal: (originalId: String, delete: Boolean) -> Unit = { _, _ -> },
) {
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
                // "Chiedimi alla fine": the same choice as the completion notification, here too.
                val ask = state.askOriginalId
                if (!state.active && ask != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { onResolveOriginal(ask, false) }) { Text("Mantieni", color = on) }
                        androidx.compose.material3.FilledTonalButton(onClick = { onResolveOriginal(ask, true) }) {
                            Text("Elimina originale")
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
 * The vault's cover (tags on up to 3 rows, duration/quality badges, watched progress, favorite),
 * for other screens such as Home, so every thumbnail in the app looks and reads the same.
 */
@Composable
fun CoverThumb(
    file: com.cripta.app.data.db.FileEntity,
    tags: List<TagEntity>,
    display: DisplayPrefs,
    bmp: android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
) {
    val isVideo = VaultRepository.isVideo(file.mimeType)
    val icon = when {
        VaultRepository.isImage(file.mimeType) -> Icons.Filled.Image
        isVideo -> Icons.Filled.Movie
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    // Decorative: every caller shows the file name as text next to the cover.
    ThumbBox(bmp, icon, isVideo, null, selected = false, favorite = file.isFavorite,
        tags = if (display.showTagsOnCover) tags else emptyList(), modifier = modifier, file = file, display = display)
}

/**
 * Folder card with a 2x2 mosaic of its newest covers, a band in the folder's colour, its emoji or
 * glyph — recognisable at a glance instead of a flat grey tile. Count and size are in its menu.
 */
@Composable
fun FolderMosaic(
    folder: FolderEntity,
    stat: FolderStat?,
    previews: List<com.cripta.app.data.db.FileEntity>,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
    /** Item count under the name (the size is in the folder's menu). */
    showInfo: Boolean = true,
    /** Drop target under a drag-selection: the card lifts, tints and gets a primary border. */
    highlighted: Boolean = false,
    /** When set, a "more" button opens the folder's actions (so they aren't long-press only). */
    onMore: (() -> Unit)? = null,
) {
    val accent = folder.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val lift by animateFloatAsState(if (highlighted) 1.05f else 1f, Motion.enter(Motion.SHORT), label = "folderLift")
    val hl by animateFloatAsState(if (highlighted) 1f else 0f, Motion.enter(Motion.SHORT), label = "folderHl")
    val primary = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    // Scale outermost, so the caller's clip/click area scales with the card.
    Column(Modifier.graphicsLayer { scaleX = lift; scaleY = lift }.then(modifier)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .background(accent.copy(alpha = 0.16f))
                .then(if (hl > 0f) Modifier.background(primary.copy(alpha = 0.18f * hl)).border(3.dp, primary.copy(alpha = hl), shape) else Modifier),
        ) {
            if (previews.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.cripta.app.ui.components.FolderGlyph(folder.color, folder.emoji, 44.dp)
                }
            } else if (previews.size == 1) {
                // A single file fills the tile (it used to be drawn twice, side by side).
                val f = previews[0]
                val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(30.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    com.cripta.app.ui.components.FolderGlyph(folder.color, folder.emoji, 20.dp)
                }
            } else {
                // 2x2 mosaic (2-3 previews leave tinted cells).
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (r in 0 until 2) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (c in 0 until 2) {
                                val f = previews.getOrNull(r * 2 + c)
                                Box(Modifier.weight(1f).fillMaxHeight().background(accent.copy(alpha = 0.10f))) {
                                    if (f != null) {
                                        val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                                        bmp?.let {
                                            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Folder identity over the mosaic.
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(30.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    com.cripta.app.ui.components.FolderGlyph(folder.color, folder.emoji, 20.dp)
                }
            }
            // Colour band on top.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(4.dp).background(accent))
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                if (showInfo) {
                    val n = stat?.count ?: 0
                    Text(if (n == 0) "Vuota" else if (n == 1) "1 elemento" else "$n elementi",
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                }
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, "Azioni per ${folder.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private class SelAction(val icon: ImageVector, val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The bottom selection bar. When its actions don't all fit (portrait), it scrolls sideways: the
 * last visible action is cut in half, the edge fades with a chevron pointing to the rest, and on
 * appearing it nudges once to show that it moves.
 */
@Composable
private fun SelectionBar(actions: List<SelAction>) {
    androidx.compose.material3.BottomAppBar(contentPadding = PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit = maxWidth / actions.size >= 72.dp
            val scroll = rememberScrollState()
            if (fit) {
                Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    actions.forEach { SelectionAction(it.icon, it.label, Modifier.weight(1f), it.destructive, it.onClick) }
                }
            } else {
                // 4.5 per screen width: the half action at the edge says there is more.
                val itemW = maxWidth / 4.5f
                val density = LocalDensity.current
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(350)
                    val px = with(density) { (itemW * 0.6f).roundToPx() }
                    scroll.animateScrollTo(px, androidx.compose.animation.core.tween(320))
                    scroll.animateScrollTo(0, androidx.compose.animation.core.tween(380))
                }
                val fadeW = with(density) { 28.dp.toPx() }
                Row(
                    Modifier.fillMaxSize()
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val clear = androidx.compose.ui.graphics.Color.Transparent
                            val solid = androidx.compose.ui.graphics.Color.Black
                            if (scroll.canScrollBackward) drawRect(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(clear, solid), 0f, fadeW),
                                size = size.copy(width = fadeW), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                            if (scroll.canScrollForward) drawRect(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(solid, clear), size.width - fadeW, size.width),
                                topLeft = androidx.compose.ui.geometry.Offset(size.width - fadeW, 0f),
                                size = size.copy(width = fadeW), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                        .horizontalScroll(scroll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { SelectionAction(it.icon, it.label, Modifier.width(itemW), it.destructive, it.onClick) }
                }
                // Chevrons at the edges while there is more that way.
                val fwd by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, label = "selFwd")
                val back by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, label = "selBack")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, if (scroll.canScrollForward) "Altre azioni" else null,
                    Modifier.align(Alignment.CenterEnd).size(20.dp).graphicsLayer { alpha = fwd },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null,
                    Modifier.align(Alignment.CenterStart).size(20.dp).graphicsLayer { alpha = back },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One labelled action of the bottom selection bar. */
@Composable
private fun SelectionAction(icon: ImageVector, label: String, modifier: Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

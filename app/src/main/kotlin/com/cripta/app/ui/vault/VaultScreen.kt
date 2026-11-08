package com.cripta.app.ui.vault

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.layout

import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.vaultUnlocked
import androidx.compose.material3.SegmentedButton
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.drawWithContent
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
import com.cripta.app.ui.components.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    TypeFilter.ALL -> str(R.string.all)
    TypeFilter.IMAGE -> str(R.string.photo)
    TypeFilter.VIDEO -> str(R.string.video)
    TypeFilter.OTHER -> str(R.string.other)
}

/** Scroll-memory key of the vault root (folders use their id). */
private const val ROOT_KEY = -1L

/**
 * Faces of the people in each media (cover track ids) and how to load one, for [ThumbBox] (Cartelle,
 * Home). [blurred]: drawn blurred (Home, "Persone in Home: Sfocate", until the people row is touched).
 */
internal class CoverPeople(val byFile: Map<String, List<Long>>, val blurred: Boolean = false, val crop: suspend (Long) -> android.graphics.Bitmap?)
internal val LocalCoverPeople = androidx.compose.runtime.staticCompositionLocalOf<CoverPeople?> { null }

/** Grid key of the folders row (file keys are ids, so they never clash). */
private const val FOLDERS_KEY = "folders"

/** One entry of the speed-dial FAB. */
private class FabAction(val label: String, val icon: ImageVector, val caption: String? = null, val onClick: () -> Unit)

fun tagAlias(tag: TagEntity): String =
    tag.alias?.takeIf { it.isNotBlank() } ?: tag.name.take(2).uppercase(Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    onOpenFile: (String) -> Unit,
    onNewNote: (Long?) -> Unit = {},
    vm: VaultViewModel = hiltViewModel(),
) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val path by vm.path.collectAsStateWithLifecycle()
    val pendingOriginals by vm.pendingOriginals.collectAsStateWithLifecycle()
    val pendingVersions by vm.pendingVersions.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val portraitColumns by vm.gridColumns.collectAsStateWithLifecycle()
    val landscapeColumns by vm.gridColumnsLandscape.collectAsStateWithLifecycle()
    val allFolders by vm.allFolders.collectAsStateWithLifecycle()
    // The folders' names as they are now: path keeps the rows as they were when entered, so a rename
    // from the open folder's ⋮ left the old name in the title, the breadcrumb and the captions.
    val pathNames = remember(path, allFolders) { path.map { p -> allFolders.firstOrNull { it.id == p.id }?.name ?: p.name } }
    val sortKey by vm.sortKey.collectAsStateWithLifecycle()
    val sortAscending by vm.sortAscending.collectAsStateWithLifecycle()
    val folderStats by vm.folderStats.collectAsStateWithLifecycle()
    val folderPreviews by vm.folderPreviews.collectAsStateWithLifecycle()
    val display by vm.display.collectAsStateWithLifecycle()
    val filesLevelState = vm.filesLevel.collectAsStateWithLifecycle()
    val foldersLevelState = vm.foldersLevel.collectAsStateWithLifecycle()
    val coverOverrides by vm.coverOverrides.collectAsStateWithLifecycle()
    val coverVersions by vm.coverVersions.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val savedFilters by vm.savedFilters.collectAsStateWithLifecycle()
    val trashEnabled by vm.trashEnabled.collectAsStateWithLifecycle()
    val filterLocalOnly by vm.filterLocalOnly.collectAsStateWithLifecycle()
    val convertStatus by vm.convertStatus.collectAsStateWithLifecycle()
    val convertSettings by vm.convertSettings.collectAsStateWithLifecycle()
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
    /** Ids of the files on screen now; selection actions only ever act on these. A derived state
     *  (read through the delegate) so long-lived gesture handlers never see a stale set. */
    val visibleIds by remember { derivedStateOf { files.mapTo(HashSet<String>()) { it.file.id } } }
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
    /** "Confronta audio" answer, shown in a dialog (too long for a snackbar). */
    var compareResult by remember { mutableStateOf<String?>(null) }
    var compareTitle by remember { mutableStateOf(str(R.string.audio_compare)) }
    compareResult?.let { text ->
        CriptaAlertDialog(
            onDismissRequest = { compareResult = null },
            confirmButton = { com.cripta.app.ui.components.TextButton(onClick = { compareResult = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } },
            title = { Text(compareTitle) },
            text = { Text(text) },
        )
    }
    var folderMenu by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToStyle by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToCover by remember { mutableStateOf<FolderEntity?>(null) }
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
                actionLabel = if (undo != null) str(R.string.undo) else null,
                withDismissAction = undo == null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
    fun countLabel(n: Int) = plural(R.plurals.items_n, n)
    fun folderLabel(id: Long?) = if (id == null) str(R.string.root) else "\"${allFolders.firstOrNull { it.id == id }?.name ?: str(R.string.folder_lc)}\""
    fun moveWithUndo(requested: List<String>, target: Long?) {
        // Only files still on screen: the undo snapshot below is taken from them, so a hidden id
        // would be moved without any way back.
        val ids = requested.filter { it in visibleIds }
        if (ids.isEmpty()) return
        val set = ids.toSet()
        val prev = files.filter { it.file.id in set }.associate { it.file.id to it.file.folderId }
        vm.moveFiles(ids, target)
        notify(str(R.string.moved_to, plural(R.plurals.moved_items, ids.size), folderLabel(target))) {
            vm.restoreFolders(prev)
        }
    }
    fun favoriteWithUndo(requested: List<String>, fav: Boolean) {
        // Same as moveWithUndo: act only on shown files, which the undo snapshot covers.
        val ids = requested.filter { it in visibleIds }
        if (ids.isEmpty()) return
        val set = ids.toSet()
        val prev = files.filter { it.file.id in set }.associate { it.file.id to it.file.isFavorite }
        vm.setFavorite(ids, fav)
        val n = ids.size
        notify(
            if (fav) plural(R.plurals.favorited_n, n) else plural(R.plurals.unfavorited_n, n),
        ) { vm.restoreFavorites(prev) }
    }
    fun tagsOf(ids: Collection<String>): Map<String, List<String>> {
        val set = ids.toSet()
        return files.filter { it.file.id in set }.associate { fwt -> fwt.file.id to fwt.tags.map { it.name } }
    }

    // A selection belongs to the folder it was made in.
    LaunchedEffect(path) { selection = emptySet() }
    // ...and to what is on screen: when a filter, search or stats chip hides selected files they
    // leave the selection, otherwise Elimina/Sposta/Etichette would act on files the user can't see.
    LaunchedEffect(visibleIds) {
        val kept = selection intersect visibleIds
        if (kept.size != selection.size) selection = kept
    }

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

    // "Scarica": confirm (a plaintext copy leaves the vault), then pick the destination folder.
    var downloadAsk by remember { mutableStateOf<List<String>?>(null) }
    var downloadIds by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val downloadTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        val ids = downloadIds
        downloadIds = arrayListOf()
        if (tree != null && ids.isNotEmpty()) {
            vm.downloadToFolder(ids, tree)
            selection = emptySet()
            notify(str(R.string.export_of_started, countLabel(ids.size)))
        }
    }

    val inSelection = selection.isNotEmpty()

    // Landscape: let the top bar scroll away (down hides, up reveals) to reclaim the short height.
    // Portrait keeps it pinned.
    val scrollBehavior = if (landscape) TopAppBarDefaults.enterAlwaysScrollBehavior()
        else TopAppBarDefaults.pinnedScrollBehavior()

    // Keep the last count while the title crossfades out of selection mode (selection is empty by then).
    var lastSelCount by remember { mutableIntStateOf(1) }
    SideEffect { if (selection.isNotEmpty()) lastSelCount = selection.size }
    val shownSelCount = if (selection.isNotEmpty()) selection.size else lastSelCount

    val coverPeopleMap by vm.coverPeople.collectAsStateWithLifecycle()
    val coverPeople = remember(coverPeopleMap) { CoverPeople(coverPeopleMap) { vm.faceCrop(it) } }
    androidx.compose.runtime.CompositionLocalProvider(LocalCoverPeople provides coverPeople) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                scrollBehavior = scrollBehavior,
                // Same colour when the content scrolls under it (M3 otherwise switches to a tinted
                // surface-container colour, which looked light blue on this theme).
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = {
                    AnimatedContent(
                        targetState = inSelection,
                        transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
                        label = "title",
                    ) { sel ->
                        if (sel) SelectionCountTitle(shownSelCount)
                        // DS: the tab's root carries the mark and the headline; an open folder is a
                        // pushed page with its name in title-lg.
                        else if (path.isEmpty()) com.cripta.app.ui.components.HeaderTitle(str(R.string.tab_folders))
                        // The live row: path holds the entities as they were when entered, so a
                        // rename from the folder's own ⋮ left the old name here.
                        else com.cripta.app.ui.components.PageTitle(pathNames.last())
                    }
                },
                navigationIcon = {
                    if (inSelection) {
                        IconButton(onClick = { selection = emptySet() }) { Icon(CriptaIcons.Close, str(R.string.exit_selection)) }
                    } else if (path.isNotEmpty()) {
                        IconButton(onClick = { vm.goUp() }) { Icon(CriptaIcons.ArrowBack, str(R.string.up)) }
                    }
                },
                actions = {
                    if (inSelection) {
                        val allIds = files.map { it.file.id }.toSet()
                        val allSelected = allIds.isNotEmpty() && selection.containsAll(allIds)
                        IconButton(onClick = { selection = if (allSelected) emptySet() else allIds }) {
                            Icon(CriptaIcons.SelectAll, if (allSelected) str(R.string.deselect_all) else str(R.string.select_all),
                                tint = if (allSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                    } else {
                        // DS Cartella: inside a folder the search is an icon in the bar (the root keeps
                        // the field); tapping it again closes the field and clears the query.
                        if (path.isNotEmpty()) {
                            IconButton(onClick = {
                                if (searchExpanded) { searchExpanded = false; vm.setQuery("") } else searchExpanded = true
                            }) {
                                Icon(CriptaIcons.Search, if (searchExpanded) str(R.string.close_search) else str(R.string.search),
                                    tint = if (searchExpanded) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                            }
                        }
                        // One "Vista" menu: grid / list and the number of columns.
                        var viewMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { viewMenu = true }) {
                            Icon(if (viewMode == ViewMode.GRID) CriptaIcons.GridView else CriptaIcons.ViewList,
                                if (viewMode == ViewMode.GRID) str(R.string.view_grid_n, gridColumns) else str(R.string.view_list))
                        }
                        DropdownMenu(expanded = viewMenu && com.cripta.app.ui.components.vaultUnlocked(), onDismissRequest = { viewMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(str(R.string.grid)) },
                                leadingIcon = { Icon(CriptaIcons.GridView, null) },
                                trailingIcon = { if (viewMode == ViewMode.GRID) Icon(CriptaIcons.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { vm.setViewMode(ViewMode.GRID) },
                            )
                            DropdownMenuItem(
                                text = { Text(str(R.string.list)) },
                                leadingIcon = { Icon(CriptaIcons.ViewList, null) },
                                trailingIcon = { if (viewMode == ViewMode.LIST) Icon(CriptaIcons.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { vm.setViewMode(ViewMode.LIST); viewMenu = false },
                            )
                            if (viewMode == ViewMode.GRID) {
                                // Groups in the menu are told apart by space and a small heading, not lines.
                                Spacer(Modifier.height(8.dp))
                                Text(if (landscape) str(R.string.columns_landscape) else str(R.string.columns_portrait), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    columnChoices.forEach { n ->
                                        com.cripta.app.ui.components.ChoiceChip(selected = gridColumns == n, onClick = { vm.setGridColumns(n, landscape) }, label = { Text("$n") },
                                            modifier = Modifier.semantics { this.contentDescription = str(R.string.n_columns, n) })
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // A way into selection mode besides long-press.
                            DropdownMenuItem(
                                text = { Text(str(R.string.select_all)) },
                                leadingIcon = { Icon(CriptaIcons.SelectAll, null) },
                                enabled = files.isNotEmpty(),
                                onClick = { viewMenu = false; selection = files.map { it.file.id }.toSet() },
                            )
                        }
                        // DS order: the view, then filters and sort.
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(CriptaIcons.Tune, str(R.string.filters_and_sort),
                                tint = if (filters.active) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        // DS Cartella: the open folder's actions behind ⋮, last (as a long press on its card).
                        if (path.isNotEmpty()) {
                            IconButton(onClick = { folderMenu = path.last() }) { Icon(CriptaIcons.MoreVert, str(R.string.folder_actions)) }
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
                // produceState keeps its previous result while recomputing for a new selection:
                // only offer (and convert) ids that are still selected right now.
                val convertibleSel = convertibleIds intersect selection
                val convertible = convertibleSel.size
                val actions = buildList {
                    add(SelAction(CriptaIcons.Star, if (allFav) str(R.string.unfavorite_one) else str(R.string.favorite)) {
                        favoriteWithUndo(selection.toList(), !allFav); selection = emptySet()
                    })
                    add(SelAction(CriptaIcons.Label, str(R.string.tags)) {
                        if (selection.size == 1) tagTargetId = selection.first() else batchTag = true
                    })
                    add(SelAction(CriptaIcons.DriveFileMove, str(R.string.move)) { showMove = true })
                    add(SelAction(CriptaIcons.Delete, str(R.string.delete), destructive = true) { confirmMultiDelete = true })
                    add(SelAction(CriptaIcons.Download, str(R.string.export)) { downloadAsk = selection.toList() })
                    if (convertible > 0) add(SelAction(CriptaIcons.Autorenew, if (convertible == 1) str(R.string.convert_mp4_one) else str(R.string.convert_n, convertible)) {
                        // Re-read at tap time: both states may have moved on since this bar was built.
                        val ids = (convertibleIds intersect selection).toList()
                        if (ids.isNotEmpty()) {
                            if (!convertSettings.first) {
                                convertAsk = ids
                            } else {
                                vm.convertToMp4(ids)
                                notify(str(R.string.conversion_queued))
                            }
                            selection = emptySet()
                        }
                    })
                    if (videoCount > 0) add(SelAction(CriptaIcons.Image, if (videoCount == 1) str(R.string.cover) else str(R.string.covers_n, videoCount)) {
                        showRegenCover = true
                    })
                    // Intro and outro of the selected videos now, again even if analysed before.
                    if (videoCount > 0) add(SelAction(CriptaIcons.GraphicEq, if (videoCount == 1) str(R.string.analyse_intro) else str(R.string.analyse_n, videoCount)) {
                        val ids = files.filter { it.file.id in selection && VaultRepository.isVideo(it.file.mimeType) }.map { it.file.id }
                        if (ids.isNotEmpty()) {
                            com.cripta.app.work.ConversionService.startIntroScan(ctx, manual = true, fileIds = ids)
                            notify(str(R.string.intro_analysis_started))
                            selection = emptySet()
                        }
                    })
                    // Two analysed videos: how much audio their starts share (to check the recognition).
                    if (videoCount == 2 && selection.size == 2) add(SelAction(CriptaIcons.GraphicEq, str(R.string.audio_compare_short)) {
                        val (a, b) = selection.toList()
                        vm.compareAudio(a, b) { compareTitle = str(R.string.audio_compare); compareResult = it }
                    })
                    // Two photos or videos: why their faces are (not) the same person.
                    val visual = files.count { it.file.id in selection && (VaultRepository.isVideo(it.file.mimeType) || VaultRepository.isImage(it.file.mimeType)) }
                    if (visual > 0) add(SelAction(CriptaIcons.Face, if (visual == 1) str(R.string.analyse_faces) else str(R.string.analyse_faces_n, visual)) {
                        val ids = files.filter { it.file.id in selection && (VaultRepository.isVideo(it.file.mimeType) || VaultRepository.isImage(it.file.mimeType)) }
                            .map { it.file.id }
                        if (ids.isNotEmpty()) {
                            com.cripta.app.work.ConversionService.startFaceScan(ctx, manual = true, fileIds = ids)
                            selection = emptySet()
                        }
                    })
                    if (selection.size == 1) add(SelAction(CriptaIcons.Edit, str(R.string.rename)) { renameTargetId = selection.first() })
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
                    // "+" opens its actions in a column above it (adding things); "Apri a caso" is a
                    // separate small button above it, since it opens rather than adds, hidden while
                    // the actions are open (they take its place).
                    // Centred column: the small "a caso" button sits exactly over the middle of the "+".
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val here = pathNames.lastOrNull()
                        val actions = buildList {
                            add(FabAction(str(R.string.import_short), CriptaIcons.Upload,
                                here?.let { str(R.string.import_files_in, it) } ?: str(R.string.import_files_root)) { fabExpanded = false; startImport() })
                            add(FabAction(str(R.string.folder), CriptaIcons.CreateNewFolder,
                                here?.let { str(R.string.new_folder_in, it) } ?: str(R.string.new_folder_root)) { fabExpanded = false; showNewFolder = true })
                            if (display.showNoteFab) add(FabAction(str(R.string.default_note_name), CriptaIcons.Description,
                                here?.let { str(R.string.new_note_in, it) } ?: str(R.string.new_note_root)) { fabExpanded = false; onNewNote(path.lastOrNull()?.id) })
                        }
                        if (display.showRandomFab) {
                            val randomLabel = when {
                                filters.active -> str(R.string.random_in_results)
                                path.isNotEmpty() -> str(R.string.random_in_folder)
                                else -> str(R.string.random_open)
                            }
                            // Kept in place (only faded) while the actions are open: removed, the
                            // column would shrink and the "+" jump down under the finger.
                            val randomAlpha by animateFloatAsState(if (fabExpanded) 0f else 1f, Motion.enter(Motion.SHORT), label = "randomFab")
                            // DS cr-fab--small: 44dp, radius 14, surface-overlay, ink icon, the one float shadow.
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = { if (!fabExpanded) vm.randomShuffleOpen(onOpenFile) },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shape = com.cripta.app.ui.theme.RadiusMd,
                                modifier = Modifier.size(44.dp).graphicsLayer { alpha = randomAlpha }
                                    .then(if (fabExpanded) Modifier.clearAndSetSemantics { } else Modifier),
                            ) { Icon(CriptaIcons.Shuffle, randomLabel) }
                        }
                        Box {
                            // The actions stand above the "+", right edges aligned, and take no room in
                            // the layout: opening them never moves the "+". Their height no longer has
                            // to match the button's (a row beside it was taller than the "+": centred
                            // it reached the navigation bar, lifted it looked off-centre).
                            val gap = with(LocalDensity.current) { 16.dp.roundToPx() }
                            val fabW = with(LocalDensity.current) { 60.dp.roundToPx() }
                            Box(Modifier.align(Alignment.TopStart).layout { m, c ->
                                val p = m.measure(c.copy(minWidth = 0, minHeight = 0,
                                    maxWidth = androidx.compose.ui.unit.Constraints.Infinity,
                                    maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                                layout(0, 0) { p.place(fabW - p.width, -p.height - gap) }
                            }) {
                                val origin = TransformOrigin(1f, 1f)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = fabExpanded,
                                    enter = fadeIn(Motion.enter(Motion.MEDIUM)) +
                                        scaleIn(Motion.enter(Motion.MEDIUM), initialScale = 0.9f, transformOrigin = origin),
                                    exit = fadeOut(Motion.exit(Motion.SHORT)) +
                                        scaleOut(Motion.exit(Motion.SHORT), targetScale = 0.9f, transformOrigin = origin),
                                ) {
                                    FabMenu(actions)
                                }
                            }
                            // "+" turns 45 degrees into an "x" while the actions are open.
                            val fabRotation by animateFloatAsState(if (fabExpanded) 45f else 0f, Motion.enter(Motion.MEDIUM), label = "fabRot")
                            // DS cr-fab: 60dp, radius 20, accent with on-accent icon.
                            FloatingActionButton(
                                onClick = { fabExpanded = !fabExpanded },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(60.dp).semantics { this.stateDescription = if (fabExpanded) str(R.string.opened) else str(R.string.closed) },
                            ) {
                                Icon(
                                    CriptaIcons.Add,
                                    if (fabExpanded) str(R.string.close_actions) else str(R.string.add),
                                    Modifier.size(28.dp).graphicsLayer { rotationZ = fabRotation },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
      Box(Modifier.fillMaxSize().padding(pad)) {
        Column(Modifier.fillMaxSize().nestedScroll(fabScroll)) {
            // Search field: always at the root (DS Cartelle); inside a folder revealed from the
            // top-bar icon, and it stays while a query is typed.
            val showSearch = path.isEmpty() || searchExpanded || filters.query.isNotEmpty()
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
                com.cripta.app.ui.components.CriptaTextField(
                    value = filters.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text(str(R.string.search_name_or_tag)) },
                    leadingIcon = { Icon(CriptaIcons.Search, null) },
                    trailingIcon = if (filters.query.isNotEmpty()) ({
                        IconButton(onClick = { vm.setQuery("") }) { Icon(CriptaIcons.Close, str(R.string.clear_search)) }
                    }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (landscape) 2.dp else 4.dp)
                        .focusRequester(searchFocus),
                )
            }
            // Landscape: the banners/strips above the grid get a capped, scrollable area so an
            // import in progress plus active filters can't squeeze the grid down to nothing.
            Column(if (landscape) Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()) else Modifier) {
            val filterPeople by vm.filterPeople.collectAsStateWithLifecycle()
            ActiveFilterBar(filters, tags, vm::setType, { vm.setFavoritesOnly(false) },
                { vm.setUntaggedOnly(false) }, vm::toggleTag, vm::toggleExcludedTag,
                { vm.clearFilters(); searchExpanded = false }, onClearQuery = { vm.setQuery("") },
                people = filterPeople, onPerson = vm::togglePerson, tagColors = display.tagColors)
            if (path.size >= 2 && !filters.active) {
                Breadcrumb(pathNames, onGo = vm::goToDepth)
            }
            ImportBanner(importState, onDismiss = vm::dismissImportResult)
            ConvertBanner(convertStatus, onCancel = vm::cancelConversion, onDismiss = vm::dismissConvertResult,
                onResolveOriginal = vm::resolveOriginal)
            QuickChips(
                atRoot = path.isEmpty(), filters = filters, sortLabel = sortChipLabel(sortKey, sortAscending),
                onType = vm::setType, onFavorites = { vm.setFavoritesOnly(!filters.favoritesOnly) },
                onUntagged = { vm.setUntaggedOnly(!filters.untaggedOnly) }, onSort = { showFilterSheet = true },
            )
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
            // The grid's gesture handlers read these instead of being keyed on the list: an import
            // changes it once per file, and restarting a handler cancelled a drag halfway through.
            val currentOrderedIds by rememberUpdatedState(orderedIds)
            val currentIdSet by rememberUpdatedState(idSet)
            val currentInSelection by rememberUpdatedState(inSelection)

            fun toggleSel(id: String) {
                selection = if (id in selection) selection - id else selection + id
            }
            fun openFile(id: String) { vm.publishViewerQueue(); onOpenFile(id) }
            /** TalkBack actions of a file cell (everything a long-press + bottom bar offers). */
            fun fileActions(fwt: FileWithTags): List<CustomAccessibilityAction> {
                val id = fwt.file.id
                return listOf(
                    CustomAccessibilityAction(if (fwt.file.isFavorite) str(R.string.unfavorite_short) else str(R.string.favorite_add)) {
                        favoriteWithUndo(listOf(id), !fwt.file.isFavorite); true
                    },
                    CustomAccessibilityAction(str(R.string.tags)) { tagTargetId = id; true },
                    CustomAccessibilityAction(str(R.string.rename)) { renameTargetId = id; true },
                    CustomAccessibilityAction(str(R.string.move)) { selection = setOf(id); showMove = true; true },
                    CustomAccessibilityAction(str(R.string.delete)) { selection = setOf(id); confirmMultiDelete = true; true },
                )
            }
            // Folder cards live in a horizontal row; their on-screen bounds let a drag-selection
            // still be dropped onto a folder (the grid's own hit-testing can't see inside the row).
            val folderBounds = remember { androidx.compose.runtime.mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
            var gridCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

            val foldersCollapsed by vm.foldersCollapsed.collectAsStateWithLifecycle()
            val folderRow: @Composable () -> Unit = {
                val perRow = if (landscape) 4 else 2
                val anim = animationsEnabled()
                Column(
                    (if (anim) Modifier.animateContentSize(Motion.enter(Motion.MEDIUM)) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // The folders group folds down to its heading (remembered across launches).
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(onClickLabel = if (foldersCollapsed) str(R.string.show_folders) else str(R.string.hide_folders)) {
                                vm.setFoldersCollapsed(!foldersCollapsed)
                            }
                            .padding(top = 14.dp)
                            .semantics { heading() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            str(R.string.folders_count_prefix) + com.cripta.app.ui.components.formatCount(folders.size),
                            style = com.cripta.app.ui.theme.HeadingStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (foldersCollapsed) CriptaIcons.ExpandMore else CriptaIcons.ExpandLess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (!foldersCollapsed) folders.chunked(perRow).forEach { rowFolders ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowFolders.forEach { folder ->
                          androidx.compose.runtime.key(folder.id) {
                            androidx.compose.runtime.DisposableEffect(folder.id) {
                                onDispose { folderBounds.remove(folder.id) }
                            }
                            // While selecting, a folder tap would silently drop the selection: say why instead.
                            val open = {
                                if (inSelection) notify(str(R.string.leave_selection_to_open))
                                else vm.enterFolder(folder)
                            }
                            FolderMosaic(
                                folder = folder,
                                stat = folderStats[folder.id],
                                previews = folderPreviews[folder.id].orEmpty(),
                                thumb = { vm.thumb(it) },
                                showInfo = display.showFolderInfo,
                                highlighted = hoverFolder == folder.id,
                                modifier = Modifier.weight(1f)
                                    .onGloballyPositioned { folderBounds[folder.id] = it.boundsInRoot() }
                                    .combinedClickable(
                                        onClickLabel = str(R.string.open),
                                        onLongClickLabel = str(R.string.folder_actions),
                                        onClick = open,
                                        onLongClick = { folderMenu = folder },
                                    )
                                    .semantics {
                                        this.customActions = listOf(
                                            CustomAccessibilityAction(str(R.string.rename)) { folderToRename = folder; true },
                                            CustomAccessibilityAction(str(R.string.customise)) { folderToStyle = folder; true },
                                            CustomAccessibilityAction(str(R.string.choose_cover)) { folderToCover = folder; true },
                                            CustomAccessibilityAction(str(R.string.delete)) { folderToDelete = folder; true },
                                        )
                                    },
                            )
                          }
                        }
                        repeat(perRow - rowFolders.size) { Spacer(Modifier.weight(1f)) }
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
                // Hidden until both the folders and the files on screen belong to the new level: the
                // lists update one after the other, and showing in between flashed the old level
                // (root -> folder -> root on Back). Also without animations, where it just snaps.
                levelAlpha.snapTo(0f)
                val target = path.lastOrNull()?.id
                withTimeoutOrNull(250) {
                    snapshotFlow { filesLevelState.value == target && foldersLevelState.value == target }.first { it }
                }
                val restore = if (!deeper) scrollMemory[newKey] else null
                // The new level's items must be composed into the grid before scrolling: asked for
                // while the grid still held the old (shorter) level, the position was clamped to it
                // and Back landed at the top instead of where the user left.
                repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                gridState.scrollToItem(restore?.first ?: 0, restore?.second ?: 0)
                if (animOn) {
                    levelX.snapTo(with(density) { 32.dp.toPx() } * if (deeper) 1f else -1f)
                    // Quick: the new level is already loaded, it only needs to say "deeper" / "back".
                    launch { levelX.animateTo(0f, Motion.enter(Motion.MEDIUM)) }
                    levelAlpha.animateTo(1f, Motion.enter(Motion.SHORT))
                } else {
                    levelX.snapTo(0f); levelAlpha.snapTo(1f)
                }
            }

            // Before the effect above runs (the first frame after a folder change) the level is
            // already hidden, so the old content can't show for that frame either.
            val levelPending = (path.lastOrNull()?.id ?: ROOT_KEY) != shownLevel
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { translationX = levelX.value; alpha = if (levelPending) 0f else levelAlpha.value },
            ) {
            when {
                files.isEmpty() && !showFolders ->
                    EmptyState(filters.active, Modifier.fillMaxSize(),
                        onImport = { startImport() },
                        folderName = pathNames.lastOrNull(),
                        // Where the filters actually looked: the vault, this folder, or the root's own files.
                        searchedWhere = when {
                            !filterLocalOnly -> str(R.string.in_whole_vault)
                            path.isNotEmpty() -> str(R.string.in_this_folder)
                            else -> str(R.string.among_root_files)
                        },
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
                        // In selection mode a tap toggles, as in the normal grid (it used to open
                        // the viewer even after "Seleziona tutto").
                        onOpen = { if (inSelection) toggleSel(it) else openFile(it) },
                        onReorder = { vm.reorder(it) },
                        header = if (showFolders) folderRow else null,
                        inSelection = inSelection,
                        // A filter/search shows results from the whole vault: reordering that
                        // subset would renumber it 0..k-1 and scramble other folders' manual order.
                        reorderEnabled = !filters.active,
                    )
                else -> Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                        .onGloballyPositioned { gridCoords = it }
                        // Tap: open a file (or toggle it in selection mode). A held finger marks the
                        // cell as pressed after 60ms, so starting a scroll doesn't flash it.
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { off ->
                                    val key = keyAt(off, gridState) as? String
                                    if (key != null && key in currentIdSet) {
                                        val job = uiScope.launch { kotlinx.coroutines.delay(60); pressedKey = key }
                                        tryAwaitRelease()
                                        job.cancel()
                                        pressedKey = null
                                    }
                                },
                                onTap = { off ->
                                    val key = keyAt(off, gridState) as? String ?: return@detectTapGestures
                                    if (key in currentIdSet) {
                                        if (currentInSelection) toggleSel(key) else openFile(key)
                                    }
                                },
                            )
                        }
                        // Long-press a file then drag = gallery-style range select (or deselect if
                        // the anchor was already selected); release over a folder to move there.
                        .pointerInput(Unit) {
                            val onStart: (Offset) -> Unit = { off ->
                                selPointer = off
                                hoverFolder = null
                                val key = keyAt(off, gridState) as? String
                                if (key != null && key in currentIdSet) {
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
                                        val cur = curKey?.takeIf { it in currentIdSet }
                                        if (cur != null) {
                                            val ai = currentOrderedIds.indexOf(anchor)
                                            val ci = currentOrderedIds.indexOf(cur)
                                            if (ai >= 0 && ci >= 0) {
                                                val range = currentOrderedIds.subList(minOf(ai, ci), maxOf(ai, ci) + 1).toSet()
                                                selection = if (dragDeselect) dragBase - range else dragBase + range
                                            }
                                        }
                                    }
                                }
                            }
                            val onEnd: () -> Unit = {
                                val target = hoverFolder
                                // A deselecting drag never moves: ending it over a folder moved
                                // whatever was still selected there.
                                if (target != null && !dragDeselect && selection.isNotEmpty()) {
                                    val ids = selection.toList()
                                    selection = emptySet()
                                    moveWithUndo(ids, target)
                                }
                                dragAnchor = null; hoverFolder = null
                            }
                            // Long-press an item then drag to range-select; a plain long-press selects one.
                            try {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { off -> onStart(off) },
                                    onDrag = { _, amount -> onMove(amount) },
                                    onDragEnd = { onEnd() },
                                    onDragCancel = { dragAnchor = null; hoverFolder = null },
                                )
                            } finally {
                                // If the handler is torn down mid-drag (no onDragCancel then), don't
                                // leave a stale anchor / highlighted drop folder behind.
                                dragAnchor = null; hoverFolder = null
                            }
                        },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    // Grid: a little more air between rows than between columns, the name sits under each cover.
                    verticalArrangement = Arrangement.spacedBy(if (viewMode == ViewMode.GRID) 14.dp else 4.dp),
                ) {
                    if (showFolders) {
                        // Folders in their own scrollable row, so the files below stay a uniform grid.
                        item(key = FOLDERS_KEY, span = { GridItemSpan(maxLineSpan) }) { folderRow() }
                        if (files.isNotEmpty() && grouped.firstOrNull()?.first.isNullOrEmpty()) header(str(R.string.file_))
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
                                onClick(label = if (inSelection) (if (isSel) str(R.string.deselect) else str(R.string.select)) else str(R.string.open)) {
                                    if (inSelection) toggleSel(id) else openFile(id)
                                    true
                                }
                                onLongClick(label = if (isSel) str(R.string.deselect) else str(R.string.select)) { toggleSel(id); true }
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
                        if (showFolders) out += str(R.string.tab_folders)
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
                        this.contentDescription = str(R.string.close_actions_menu)
                        onClick(label = str(R.string.close)) { fabExpanded = false; true }
                    },
            )
        }
      }
    }
    }

    if (showNewFolder) {
        TextPromptDialog(str(R.string.new_folder), str(R.string.sort_name), confirmLabel = str(R.string.create),
            onConfirm = { name -> vm.createFolder(name); showNewFolder = false; notify(str(R.string.folder_created, name)) },
            onDismiss = { showNewFolder = false })
    }

    renameTargetId?.let { id ->
        val current = files.firstOrNull { it.file.id == id }?.file?.originalName ?: ""
        TextPromptDialog(str(R.string.rename_file), str(R.string.sort_name), initial = current, selectBaseName = true, confirmLabel = str(R.string.rename),
            onConfirm = { name ->
                vm.renameFile(id, name); renameTargetId = null; selection = emptySet()
                if (name != current) notify(str(R.string.renamed_to, name)) { vm.renameFile(id, current) }
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
                if (before[id].orEmpty().toSet() != names.toSet()) notify(str(R.string.tags_updated)) { vm.restoreTags(before) }
            },
            onSetAlias = { name, alias -> vm.setTagAlias(name, alias) },
            onCreateTag = { name, alias, icon -> vm.createTag(name, alias, icon) },
            onSetIcon = { name, icon -> vm.setTagIcon(name, icon) },
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
                // Shown files only (the undo snapshot from tagsOf covers exactly those).
                val ids = selection.filter { it in visibleIds }
                val before = tagsOf(ids)
                vm.addTagsToFiles(ids, names); batchTag = false; selection = emptySet()
                notify(str(R.string.tags_added_to, countLabel(ids.size))) { vm.restoreTags(before) }
            },
            onRemove = { names ->
                val ids = selection.filter { it in visibleIds }
                val before = tagsOf(ids)
                vm.removeTagsFromFiles(ids, names); batchTag = false; selection = emptySet()
                notify(str(R.string.tags_removed_from, countLabel(ids.size))) { vm.restoreTags(before) }
            },
            onCreateTag = { name, alias, icon -> vm.createTag(name, alias, icon) },
            onDismiss = { batchTag = false },
            showRecents = display.showRecentTags,
        )
    }

    folderMenu?.let { opened ->
        // Read live: the open folder's ⋮ passes path.last(), a copy taken on entering it, which kept
        // showing "Aggiungi ai preferiti" (and set it again) after the folder had become a favourite.
        val folder = allFolders.firstOrNull { it.id == opened.id } ?: opened
        com.cripta.app.ui.components.CriptaSheet(onDismissRequest = { folderMenu = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp).semantics { heading() })
                // Folder details (moved here from under the card): contents, space, creation date.
                Text(
                    folderSubtitle(folderStats[folder.id]) + str(R.string.created_on) +
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(folder.createdAt)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
                )
                // The open folder's own menu (its ⋮ in the bar) has no "Apri" nor "Elimina" (Back first).
                val current = folder.id == path.lastOrNull()?.id
                if (!current) SheetAction(CriptaIcons.Folder, str(R.string.open)) {
                    folderMenu = null
                    if (inSelection) notify(str(R.string.leave_selection_to_open)) else vm.enterFolder(folder)
                }
                SheetAction(CriptaIcons.DriveFileRenameOutline, str(R.string.rename)) { folderToRename = folder; folderMenu = null }
                SheetAction(if (folder.isFavorite) CriptaIcons.Star else CriptaIcons.StarBorder,
                    if (folder.isFavorite) str(R.string.unfavorite) else str(R.string.favorite_add)) {
                    vm.setFolderFavorite(folder.id, !folder.isFavorite); folderMenu = null
                }
                SheetAction(CriptaIcons.Casino,
                    if (folder.excludedFromRandom) str(R.string.include_in_random) else str(R.string.exclude_from_random)) {
                    vm.setFolderRandomExcluded(folder.id, !folder.excludedFromRandom); folderMenu = null
                    notify(if (folder.excludedFromRandom) str(R.string.folder_included_random) else str(R.string.folder_excluded_random))
                }
                SheetAction(CriptaIcons.Palette, str(R.string.customise_colour_icon)) { folderToStyle = folder; folderMenu = null }
                SheetAction(CriptaIcons.Image, str(R.string.choose_cover)) { folderToCover = folder; folderMenu = null }
                if (!current) SheetAction(CriptaIcons.Delete, str(R.string.delete), destructive = true) { folderToDelete = folder; folderMenu = null }
            }
        }
    }

    folderToRename?.let { folder ->
        TextPromptDialog(str(R.string.rename_folder), str(R.string.sort_name), initial = folder.name, confirmLabel = str(R.string.rename),
            onConfirm = { name ->
                vm.renameFolder(folder, name); folderToRename = null
                if (name != folder.name) notify(str(R.string.folder_renamed)) { vm.renameFolder(folder, folder.name) }
            },
            onDismiss = { folderToRename = null })
    }

    folderToCover?.let { folder ->
        FolderCoverSheet(
            folder = folder,
            load = { vm.filesInFolder(folder.id) },
            thumb = { vm.thumb(it) },
            onPick = { id ->
                vm.setFolderCover(folder.id, id); folderToCover = null
                notify(if (id == null) str(R.string.cover_automatic) else str(R.string.cover_changed))
            },
            onDismiss = { folderToCover = null },
        )
    }

    folderToStyle?.let { folder ->
        FolderStyleDialog(
            folderName = folder.name,
            initialColor = folder.color,
            initialIcon = folder.icon,
            onConfirm = { c, i -> vm.setFolderStyle(folder.id, c, i); folderToStyle = null },
            onDismiss = { folderToStyle = null },
        )
    }

    if (showFilterSheet) {
        val sheetPeople by vm.filterPeople.collectAsStateWithLifecycle()
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
            people = sheetPeople,
            onPerson = vm::togglePerson,
            faceCrop = { vm.faceCrop(it) },
        )
    }

    convertAsk?.let { ids ->
        com.cripta.app.ui.components.ConvertChoiceDialog(
            count = ids.size,
            trashDays = convertSettings.second,
            onConfirm = { after, rememberIt ->
                vm.convertToMp4(ids, after, rememberIt)
                convertAsk = null
                notify(str(R.string.conversion_queued))
            },
            onDismiss = { convertAsk = null },
        )
    }

    if (showStats) {
        StatsSheet(stats, filters.active, pathNames.lastOrNull(), subtree = path.lastOrNull()?.let { folderStats[it.id] }, onDismiss = { showStats = false })
    }

    downloadAsk?.let { ids ->
        val n = ids.size
        CriptaAlertDialog(
            onDismissRequest = { downloadAsk = null },
            title = { Text(plural(R.plurals.download_files_q, n)) },
            text = {
                Text(plural(R.plurals.export_files_text, n))
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadAsk = null
                    downloadIds = ArrayList(ids)
                    downloadTreeLauncher.launch(null)
                }) { Text(str(R.string.choose_folder)) }
            },
            dismissButton = { TextButton(onClick = { downloadAsk = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }

    if (confirmMultiDelete) {
        // Never delete a file that isn't on screen (with the trash off it is irreversible).
        val toDelete = selection.filter { it in visibleIds }
        val n = toDelete.size
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmMultiDelete = false },
            title = { Text(plural(R.plurals.delete_files_q, n)) },
            text = {
                Text(if (trashEnabled) str(R.string.files_to_trash_text)
                    else str(R.string.files_delete_secure))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFiles(toDelete); selection = emptySet(); confirmMultiDelete = false
                    notify(
                        if (trashEnabled) plural(R.plurals.files_trashed, n)
                        else plural(R.plurals.files_deleted, n),
                    )
                }) {
                    Text(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmMultiDelete = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
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
                notify(str(R.string.regenerating_covers))
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
            title = { Text(str(R.string.delete_folder_q)) },
            text = {
                Text(
                    when {
                        trashEnabled -> if (n == 0) str(R.string.folder_to_trash_empty, folder.name) else str(R.string.folder_to_trash, folder.name, plural(R.plurals.files_n, n))
                        n == 0 -> str(R.string.folder_empty_delete, folder.name)
                        else -> str(R.string.folder_delete_secure, folder.name, plural(R.plurals.files_n, n))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder); folderToDelete = null
                    notify(if (trashEnabled) str(R.string.folder_in_trash, folder.name) else str(R.string.folder_deleted, folder.name))
                }) {
                    Text(if (trashEnabled) str(R.string.move_to_trash) else if (n == 0) str(R.string.delete) else str(R.string.delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }

    if (pendingOriginals.isNotEmpty()) {
        val n = pendingOriginals.size
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { vm.clearPendingOriginals() },
            title = { Text(if (n == 1) str(R.string.n_delete_original_q) else str(R.string.n_delete_originals_q)) },
            text = {
                Text(
                    plural(R.plurals.delete_device_originals, n) +
                        str(R.string.not_secure_original),
                )
            },
            confirmButton = { TextButton(onClick = { vm.deleteOriginals(pendingOriginals) }) { com.cripta.app.ui.components.ButtonText(plural(R.plurals.delete_original_btn, n)) } },
            dismissButton = { TextButton(onClick = { vm.clearPendingOriginals() }) { com.cripta.app.ui.components.ButtonText(str(R.string.keep)) } },
        )
    }

    // Imports named like a vault file, or identical / similar to one: never replaced or removed on
    // their own. DS "Già nel vault": once the import is over (after the originals question) one page
    // lists them all, each with its choice; closing it keeps every file as it is.
    if (pendingVersions.isNotEmpty() && !importState.active && pendingOriginals.isEmpty()) {
        ImportConflictsPage(pendingVersions, trashEnabled, thumbOf = { id -> vm.fileFor(id)?.let { vm.thumb(it) } },
            onApply = { replace, keep, remove ->
                if (replace.isNotEmpty()) vm.replaceVersions(replace)
                if (remove.isNotEmpty()) vm.removeNewCopies(remove)
                if (keep.isNotEmpty()) vm.keepVersionsSeparate(keep)
            },
            onDismiss = { vm.keepVersionsSeparate(pendingVersions) })
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
        Text(if (count == 1) str(R.string.selected_suffix_one) else str(R.string.selected_suffix_many), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

private fun LazyGridScope.header(text: String, count: Int? = null) {
    item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(text, count) }
}

/** Light section label: "OGGI · 12" in small caps, instead of a big title. */
@Composable
private fun SectionLabel(text: String, count: Int? = null) {
    Text(
        text + (count?.let { " · " + com.cripta.app.ui.components.formatCount(it) } ?: ""),
        style = com.cripta.app.ui.theme.HeadingStyle,
        color = MaterialTheme.colorScheme.primary,
        // DS: 28dp above a section heading (14 here + the grid's row gap), 10 under it.
        modifier = Modifier.padding(top = 14.dp).semantics { heading() },
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
                today -> str(R.string.today)
                today.minusDays(1) -> str(R.string.yesterday)
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
    if (stat == null || stat.count == 0) return str(R.string.empty_f)
    val n = stat.count
    val items = plural(R.plurals.items_n, n)
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
    // Scroll position as a continuous 0..1: the items (rows) above the screen against those above
    // plus those below, each counting the part of its edge row already scrolled. Normalising by how
    // many items are visible made the thumb jump: that number changes as rows and full-width headers
    // (the folders, the dates) come and go.
    val listFraction by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val vis = info.visibleItemsInfo
            when {
                info.totalItemsCount <= 1 || vis.isEmpty() || !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> {
                    val first = vis.first()
                    val firstCols = vis.count { it.offset.y == first.offset.y }.coerceAtLeast(1)
                    val before = first.index + (-first.offset.y).coerceAtLeast(0).toFloat() / first.size.height.coerceAtLeast(1) * firstCols
                    val last = vis.last()
                    val lastCols = vis.count { it.offset.y == last.offset.y }.coerceAtLeast(1)
                    val hidden = (last.offset.y + last.size.height - info.viewportEndOffset).coerceAtLeast(0)
                    val after = (info.totalItemsCount - 1 - last.index) + hidden.toFloat() / last.size.height.coerceAtLeast(1) * lastCols
                    (before / (before + after).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                }
            }
        }
    }
    val fraction = if (dragging) dragFraction else listFraction
    val active = state.isScrollInProgress || dragging
    // Stays a moment after the list stops, so it can be caught: it used to fade the instant the
    // fling ended.
    var linger by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) linger = true else { kotlinx.coroutines.delay(1500); linger = false } }
    // Only for a list worth jumping through (two screens or more): on a short one it popped up for
    // nothing over the covers.
    val worth by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            info.visibleItemsInfo.isNotEmpty() && info.totalItemsCount >= info.visibleItemsInfo.size * 2 &&
                (state.canScrollForward || state.canScrollBackward)
        }
    }
    val alpha by animateFloatAsState(if ((active || linger) && (worth || dragging)) 1f else 0f, label = "fastscroll")
    // The strip only takes drags while the scroller is visible: when hidden, touches on the right
    // edge go to the grid (select, open, scroll) instead of an invisible control.
    val visible = alpha > 0.05f || dragging
    val thumbW by animateDpAsState(if (dragging) 10.dp else 6.dp, Motion.enter(Motion.SHORT), label = "thumbW")
    val density = LocalDensity.current
    val thumbH = 48.dp
    val thumbPx = with(density) { thumbH.toPx() }
    val maxOffset = (trackH - thumbPx).coerceAtLeast(0f)
    val offsetY = with(density) { (fraction * maxOffset).toDp() }
  Box(modifier.fillMaxHeight()) {
    // Bubble next to the thumb (section / position of the first visible item) only while the thumb
    // is held: shown on every swipe of the list, it covered the covers for nothing.
    val bubbleAlpha by animateFloatAsState(if (dragging) 1f else 0f, label = "fastscrollBubble")
    val label = if (bubbleAlpha > 0f) labelAt(state.firstVisibleItemIndex) else null
    if (label != null) {
        // A plain drawing, never a touch target: a Surface swallowed the touches landing on it, so a
        // new swipe started on the bubble (while the list was still moving) stopped the list dead.
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 30.dp)
                .offset(y = offsetY + 6.dp)
                .graphicsLayer {
                    this.alpha = bubbleAlpha
                    shadowElevation = 3.dp.toPx()
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    clip = true
                }
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clearAndSetSemantics { },
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
        VaultRepository.isImage(mime) -> CriptaIcons.Image
        isVideo -> CriptaIcons.Movie
        else -> CriptaIcons.InsertDriveFile
    }
    val loaded by produceState<android.graphics.Bitmap?>(initialValue = null, item.file.id, coverVersion) { value = thumb() }
    // A freshly regenerated cover (override) wins over the cached/loaded one so it shows at once.
    val bmp = coverOverride ?: loaded

    // Tap/long-press are handled by the grid container (unified gesture), not per cell.
    if (viewMode == ViewMode.LIST) {
        // A plain row on the page: no card around it, the cover and the text carry it.
        Row(modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // List mode: no tag badges on the cover — every tag is shown as a pill beside it.
            ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite,
                emptyList(), Modifier.size(76.dp), file = item.file, display = display, compact = true,
                selectionMode = selectionMode)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge)
                if (display.showFileInfo) {
                    Text(listMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
                if (item.tags.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.forEach { TagChip(it, display) }
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
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
        if (display.showFileInfo) {
            // DS cr-cover-item__meta: caption, muted, 2dp under the name.
            Text(fileMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp))
        }
    }
}

/** Size · date (the length is on the cover itself in list mode, unless the badge is off). */
private fun listMeta(f: com.cripta.app.data.db.FileEntity, withDuration: Boolean): String {
    val parts = mutableListOf<String>()
    if (withDuration) com.cripta.app.ui.components.formatDuration(f.durationMs)?.let { parts += it }
    parts += formatBytes(f.sizeBytes)
    parts += java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(f.importedAt))
    return parts.joinToString(" · ")
}

/**
 * Text a tag shows on a cover badge, per the user's style choice. Short style with an icon and no
 * alias: the icon alone (it replaces the two letters).
 */
// On a cover a tag shows only its symbol or its acronym, never the full name.
internal fun badgeText(tag: TagEntity, @Suppress("UNUSED_PARAMETER") display: DisplayPrefs?): String = when {
    tag.icon != null && tag.alias.isNullOrBlank() -> ""
    else -> tagAlias(tag)
}

/** A tag's colour on screen: its own, or the app accent for every tag when tag colours are off. */
@Composable
private fun tagHue(tag: TagEntity, display: DisplayPrefs?): Color =
    if (display?.tagColors != false) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary

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
    SelectableThumbFrame(
        selected = selected,
        shape = if (compact) com.cripta.app.ui.theme.CoverShapeSmall else com.cripta.app.ui.theme.CoverShape,
        modifier = modifier,
        selectedScale = if (compact) 0.9f else 0.92f,
        overlay = { CornerSelectionCheck(selected, selectionMode, size = if (compact) 20.dp else 24.dp) },
    ) {
        // Without a cover: the type's icon on a neutral tile (the shape tells the type; colour stays for state).
        com.cripta.app.ui.components.CoverPlaceholderBackground()
        ThumbCrossfade(bmp, contentDescription = name, modifier = Modifier.fillMaxSize()) {
            com.cripta.app.ui.components.CoverPlaceholder(fallbackIcon, if (compact) 26.dp else 32.dp)
        }
        // Play marker only when no length badge already says "this is a video".
        if (isVideo && duration == null) {
            com.cripta.app.ui.components.CoverPlayMark(Modifier.align(Alignment.Center), if (compact) 28.dp else 34.dp)
        }
        // "Resume" progress: how much of the video has already been watched.
        val watched = if (isVideo && display?.resumePlayback != false) file?.let { f ->
            val p = f.playbackPosMs; val d = f.durationMs
            if (p != null && d != null && d > 0) (p.toFloat() / d).coerceIn(0f, 1f) else null
        } else null
        val shownWatched = watched?.takeIf { it > 0.01f }
        if (tags.isNotEmpty()) {
            TagBadges(
                tags = tags,
                display = display,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(bottom = if (shownWatched != null) 4.dp else 0.dp),
            )
        }
        if (shownWatched != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(com.cripta.app.ui.theme.Glass)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(shownWatched).background(MaterialTheme.colorScheme.primary))
            }
        }
        val people = if (!compact && display?.coverPeople != false && file != null) LocalCoverPeople.current else null
        val faces = people?.byFile?.get(file?.id).orEmpty()
        if (favorite || faces.isNotEmpty()) {
            Row(Modifier.align(Alignment.TopStart).padding(if (compact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (favorite) com.cripta.app.ui.components.CoverStar(Modifier, size = if (compact) 20.dp else 28.dp)
                if (people != null && faces.isNotEmpty()) CoverFaces(faces, display?.coverPeopleSize ?: 1, people.blurred, people.crop)
            }
        }
        if (compact) {
            if (duration != null) {
                com.cripta.app.ui.components.CoverBadge(duration, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
        } else if (!selectionMode && !selected && duration != null) {
            // Top-right (the selection check's corner in selection mode), so the bottom stays free for tags.
            com.cripta.app.ui.components.CoverBadge(duration, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** Up to three faces of the people in a media, each on the dark glass, then "+N". */
@Composable
private fun CoverFaces(ids: List<Long>, sizeStep: Int, blurred: Boolean, crop: suspend (Long) -> android.graphics.Bitmap?) {
    val size = when (sizeStep) { 0 -> 20.dp; 2 -> 34.dp; else -> 26.dp }
    ids.take(3).forEach { id ->
        val bmp by produceState<android.graphics.Bitmap?>(null, id) { value = crop(id) }
        Box(Modifier.size(size).clip(CircleShape).background(com.cripta.app.ui.components.OnImageGlass).padding(1.5.dp)
            .clip(CircleShape).then(if (blurred) Modifier.blur(size / 4, androidx.compose.ui.draw.BlurredEdgeTreatment(CircleShape)) else Modifier)) {
            bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
    if (ids.size > 3) com.cripta.app.ui.components.CoverBadge("+${ids.size - 3}", Modifier)
}

/** A tag on the list row: the shared pill, small. */
@Composable
private fun TagChip(tag: TagEntity, display: DisplayPrefs) {
    com.cripta.app.ui.components.TagPill(
        com.cripta.app.ui.components.tagText(tag), tagHue(tag, display), tagIcon = tag.icon,
        size = com.cripta.app.ui.components.TagPillSize.Small,
        base = MaterialTheme.colorScheme.background,
    )
}

/**
 * Tag pills over the bottom of a cover. Each pill sits on its own dark glass, so they read on any
 * image without a gradient darkening the rest of the picture.
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
        // DS cr-cover__bl: 8dp from the edges, 4dp between pills.
        val outerPad = 8.dp
        val availDp = maxWidth - outerPad * 2
        val availPx = with(density) { availDp.toPx() }
        val hPadPx = with(density) { 16.dp.toPx() }   // 8dp padding on each side of a pill
        val dotPx = with(density) { 10.dp.toPx() }    // a pill without its own icon leads with a 6dp dot + 4dp
        val spacingPx = with(density) { 4.dp.toPx() }
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
        val iconPx = with(density) { 14.dp.toPx() }
        val iconGapPx = with(density) { 4.dp.toPx() }
        val widths = remember(texts, availPx, tags) {
            texts.mapIndexed { i, t ->
                val text = if (t.isEmpty()) 0f else measurer.measure(t, style).size.width.toFloat()
                val icon = if (tags[i].icon == null) dotPx else iconPx + if (t.isEmpty()) 0f else iconGapPx
                minOf(text + icon + hPadPx, availPx)
            }
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
        Column(
            Modifier.padding(start = outerPad, end = outerPad, bottom = outerPad),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Filled from the bottom: the full rows sit on the cover's edge and the shorter
            // last row (plus the "+N" pill) goes on top, so it hides less of the image.
            rows.asReversed().forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { i ->
                        com.cripta.app.ui.components.TagPill(
                            texts[i], tagHue(tags[i], display), onImage = true, maxWidth = availDp, tagIcon = tags[i].icon,
                        )
                    }
                    if (r == 0 && extra > 0) com.cripta.app.ui.components.MoreTagsPill(extra, onImage = true)
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
    people: List<com.cripta.app.data.face.FaceRepository.Person> = emptyList(),
    onPerson: (Long) -> Unit = {},
    tagColors: Boolean = true,
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
                if (f.query.isNotBlank()) DismissChip("“${f.query}”", str(R.string.search_x, f.query)) { onClearQuery() }
                if (f.type != TypeFilter.ALL) DismissChip(typeLabel(f.type)) { onType(TypeFilter.ALL) }
                if (f.favoritesOnly) DismissChip(str(R.string.favorites)) { onClearFav() }
                if (f.untaggedOnly) DismissChip(str(R.string.untagged)) { onClearUntagged() }
                f.personIds.forEach { id ->
                    val name = people.firstOrNull { it.id == id }?.title ?: com.cripta.app.data.face.FaceRepository.UNNAMED
                    DismissChip(name, str(R.string.person_x, name)) { onPerson(id) }
                }
                f.tagIds.forEach { id ->
                    val t = tagById[id] ?: return@forEach
                    TagDismissChip(t, excluded = false, tagColors) { onTag(id) }
                }
                f.excludedTagIds.forEach { id ->
                    val t = tagById[id] ?: return@forEach
                    TagDismissChip(t, excluded = true, tagColors) { onExcludeTag(id) }
                }
                TextButton(onClick = onClearAll) { com.cripta.app.ui.components.ButtonText(str(R.string.reset)) }
            }
            // The title still names the folder, but a filter searches everywhere: say so.
            Text(str(R.string.results_whole_vault),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
        }
    }
}

/** An active tag filter in the bar: the shared pill with a cross, announced as "Rimuovi filtro …". */
@Composable
private fun TagDismissChip(tag: TagEntity, excluded: Boolean, tagColors: Boolean, onClear: () -> Unit) {
    com.cripta.app.ui.components.TagPill(
        com.cripta.app.ui.components.tagText(tag),
        if (tagColors) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary,
        tagIcon = tag.icon,
        size = com.cripta.app.ui.components.TagPillSize.Medium,
        state = if (excluded) com.cripta.app.ui.components.TagPillState.Excluded else com.cripta.app.ui.components.TagPillState.On,
        base = MaterialTheme.colorScheme.background,
        trailingIcon = CriptaIcons.Close,
        onClick = onClear,
        onClickLabel = str(R.string.remove_filter),
        stateLabel = if (excluded) str(R.string.tag_state_excluded) else str(R.string.included_f),
    )
}

/** Removable filter chip: 48dp touch target, announced as "Rimuovi filtro …". */
@Composable
private fun DismissChip(label: String, description: String = label, onClear: () -> Unit) {
    Surface(onClick = onClear, color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1, modifier = Modifier.clearAndSetSemantics { this.contentDescription = str(R.string.remove_filter_x, description) })
            Icon(CriptaIcons.Close, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

private enum class TagFilterState { NEUTRAL, INCLUDE, EXCLUDE }

/**
 * Tri-state tag filter: the shared [TagPill]. Neutral = tint, include = full colour + tick,
 * exclude = tint + "no" sign + strike-through. The width barely changes between states (an icon
 * takes the dot's place), so the rows don't reflow under the finger.
 */
@Composable
private fun TagFilterChip(label: String, state: TagFilterState, color: Color, icon: String? = null, onClick: () -> Unit) {
    com.cripta.app.ui.components.TagPill(
        label, color, tagIcon = icon,
        size = com.cripta.app.ui.components.TagPillSize.Medium,
        state = when (state) {
            TagFilterState.INCLUDE -> com.cripta.app.ui.components.TagPillState.On
            TagFilterState.EXCLUDE -> com.cripta.app.ui.components.TagPillState.Excluded
            TagFilterState.NEUTRAL -> com.cripta.app.ui.components.TagPillState.Off
        },
        base = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        stateLabel = when (state) {
            TagFilterState.INCLUDE -> str(R.string.included_f)
            TagFilterState.EXCLUDE -> str(R.string.tag_state_excluded)
            TagFilterState.NEUTRAL -> str(R.string.not_filtered)
        },
    )
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
    people: List<com.cripta.app.data.face.FaceRepository.Person> = emptyList(),
    onPerson: (Long) -> Unit = {},
    faceCrop: suspend (Long) -> android.graphics.Bitmap? = { null },
) {
    // The shared sheet: opens at half height (drag up for the rest), one swipe down closes it.
    var naming by remember { mutableStateOf(false) }
    // The chip's X used to delete a saved filter at once (easy to hit by mistake): ask first.
    var savedToDelete by remember { mutableStateOf<com.cripta.app.data.db.SavedFilterEntity?>(null) }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss, wideInLandscape = true) {
        // ---- Header (DS): the title and "Azzera"; the result count is on the main button below.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(str(R.string.filters_and_order), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, enabled = filters.active) { com.cripta.app.ui.components.ButtonText(str(R.string.reset)) }
        }

        // DS order: what to show (type, tags, people, favourites), then the order and saved sets.
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FilterGroup(str(R.string.type)) {
                com.cripta.app.ui.components.ChipFlowRow {
                    TypeFilter.entries.forEach { t ->
                        com.cripta.app.ui.components.ChoiceChip(selected = filters.type == t, onClick = { onType(t) },
                            label = { Text(typeLabel(t)) })
                    }
                }
            }

            // ---- Tags: tri-state chips in library order, a search once the library is long.
            if (tags.isNotEmpty()) {
                FilterGroup(
                    str(R.string.tags),
                    trailing = {
                        val n = filters.tagIds.size + filters.excludedTagIds.size
                        if (n > 0) Text(str(R.string.n_chosen, n), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    },
                ) {
                    Text(str(R.string.tag_filter_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // With two or more tags included: must a file have all of them, or is one enough?
                    // Always in place (disabled below two tags): appearing with the second tag moved
                    // every chip down, so the next tap landed on a different tag.
                    val canMatch = filters.tagIds.size >= 2
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(str(R.string.files_with), style = MaterialTheme.typography.bodyMedium,
                            color = if (canMatch) androidx.compose.ui.graphics.Color.Unspecified
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 12.dp))
                        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                                selected = filters.tagMatchAll, onClick = { onTagMatchAll(true) }, enabled = canMatch,
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                                icon = {}, label = { Text(str(R.string.tag_match_all)) },
                            )
                            SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                                selected = !filters.tagMatchAll, onClick = { onTagMatchAll(false) }, enabled = canMatch,
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                                icon = {}, label = { Text(str(R.string.at_least_one)) },
                            )
                        }
                    }
                    var query by remember { mutableStateOf("") }
                    if (tags.size > 12) {
                        com.cripta.app.ui.components.CriptaTextField(
                            value = query, onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(str(R.string.search_a_tag)) },
                            leadingIcon = { Icon(CriptaIcons.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(CriptaIcons.Close, str(R.string.empty_search)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val q = query.trim()
                    val shown = if (q.isEmpty()) tags else tags.filter {
                        it.name.contains(q, ignoreCase = true) || it.alias?.contains(q, ignoreCase = true) == true
                    }
                    if (shown.isEmpty()) {
                        Text(str(R.string.tag_no_match, q), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    com.cripta.app.ui.components.ChipFlowRow {
                        shown.forEach { tag ->
                            val label = com.cripta.app.ui.components.tagText(tag)
                            val state = when {
                                tag.id in filters.tagIds -> TagFilterState.INCLUDE
                                tag.id in filters.excludedTagIds -> TagFilterState.EXCLUDE
                                else -> TagFilterState.NEUTRAL
                            }
                            TagFilterChip(
                                label = label,
                                state = state,
                                color = if (tagColors) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary,
                                icon = tag.icon,
                                // neutral -> include; include -> exclude; exclude -> neutral.
                                onClick = { if (state == TagFilterState.NEUTRAL) onTag(tag.id) else onExcludeTag(tag.id) },
                            )
                        }
                    }
                }
            }

            // ---- People: faces in a row, a tap adds or removes; a file must show everyone chosen.
            if (people.isNotEmpty()) {
                FilterGroup(
                    str(R.string.people),
                    trailing = {
                        val n = filters.personIds.size
                        if (n > 0) Text(plural(R.plurals.chosen_n, n), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    },
                ) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        people.forEach { p ->
                            val on = p.id in filters.personIds
                            Column(
                                Modifier.width(64.dp).clip(MaterialTheme.shapes.small)
                                    .toggleable(value = on, role = androidx.compose.ui.semantics.Role.Checkbox) { onPerson(p.id) }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                com.cripta.app.ui.settings.FaceAvatar(p.cover, faceCrop, 44.dp, selected = on)
                                Text(p.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Two quick narrowing switches (DS: "Solo preferiti" as a switch row).
            Column {
                com.cripta.app.ui.settings.ToggleRow(str(R.string.favorites_only), filters.favoritesOnly, icon = CriptaIcons.StarBorder) { onFav() }
                com.cripta.app.ui.settings.ToggleRow(str(R.string.untagged), filters.untaggedOnly, icon = CriptaIcons.LabelOff) { onUntagged() }
            }

            // ---- Order: key as one segmented control, direction spelled out.
            FilterGroup(str(R.string.sort_by)) {
                val keys = listOf(SortKey.DATE to str(R.string.sort_date), SortKey.NAME to str(R.string.sort_name), SortKey.SIZE to str(R.string.sort_size), SortKey.MANUAL to str(R.string.sort_manual))
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    keys.forEachIndexed { i, (k, lbl) ->
                        SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                            selected = sortKey == k,
                            onClick = { onSort(k, sortAscending) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, keys.size),
                            icon = {},
                            label = { Text(lbl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            Text(str(R.string.drag_to_reorder),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val (descLabel, ascLabel) = when (sortKey) {
                                SortKey.DATE -> str(R.string.order_newest) to str(R.string.order_oldest)
                                SortKey.NAME -> str(R.string.z_to_a) to str(R.string.a_to_z)
                                SortKey.SIZE -> str(R.string.order_largest) to str(R.string.order_smallest)
                                else -> str(R.string.descending) to str(R.string.ascending)
                            }
                            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                                    selected = !sortAscending, onClick = { onSort(sortKey, false) },
                                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                                    icon = { Icon(CriptaIcons.ArrowDownward, null, Modifier.size(18.dp)) },
                                    label = { Text(descLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                                SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                                    selected = sortAscending, onClick = { onSort(sortKey, true) },
                                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                                    icon = { Icon(CriptaIcons.ArrowUpward, null, Modifier.size(18.dp)) },
                                    label = { Text(ascLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                }
            }

            if (saved.isNotEmpty()) {
                FilterGroup(str(R.string.saved_filters)) {
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
                                    Icon(CriptaIcons.Close, str(R.string.delete_saved_filter_x, sf.name),
                                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable { savedToDelete = sf })
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---- Fixed at the bottom (DS): the result count as the one filled action, then "Salva".
        val closeSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.cripta.app.ui.components.Button(onClick = { closeSheet {} }, modifier = Modifier.fillMaxWidth()
                .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) {
                com.cripta.app.ui.components.ButtonText(when {
                    resultCount < 0 -> str(R.string.show_files)
                    resultCount == 1 -> plural(R.plurals.show_n_files, 1, com.cripta.app.ui.components.formatCount(1))
                    else -> plural(R.plurals.show_n_files, resultCount, com.cripta.app.ui.components.formatCount(resultCount))
                })
            }
            TextButton(onClick = { naming = true }, enabled = filters.active, modifier = Modifier.padding(top = 4.dp)) {
                com.cripta.app.ui.components.ButtonText(str(R.string.save_these_filters))
            }
        }
    }
    if (naming) {
        TextPromptDialog(str(R.string.save_filter), str(R.string.filter_name_hint), confirmLabel = str(R.string.save),
            onConfirm = { onSave(it); naming = false },
            onDismiss = { naming = false })
    }
    savedToDelete?.let { sf ->
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { savedToDelete = null },
            title = { Text(str(R.string.delete_saved_filter_q)) },
            text = { Text(str(R.string.delete_saved_filter_text, sf.name)) },
            confirmButton = {
                TextButton(onClick = { onDeleteSaved(sf.id); savedToDelete = null }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { savedToDelete = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
}

/** A titled group of the filter sheet (title row with an optional trailing element, then content). */
@Composable
private fun FilterGroup(
    title: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
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
    /** Selection mode: [onOpen] toggles instead of opening (only changes the spoken label here). */
    inSelection: Boolean = false,
    /** False while filtering/searching: items are shown but can't be dragged. */
    reorderEnabled: Boolean = true,
) {
    // The per-item tap handler is keyed on the file id only: read the latest callback, or a tap
    // would keep doing what it did when the cell first appeared (e.g. open while selecting).
    val currentOnOpen by rememberUpdatedState(onOpen)
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
        Text(if (reorderEnabled) str(R.string.manual_order_hint)
            else str(R.string.manual_order_blocked),
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
                            val sel = fwt.file.id in selection
                            if (inSelection) this.selected = sel
                            onClick(label = if (inSelection) (if (sel) str(R.string.deselect) else str(R.string.select)) else str(R.string.open)) {
                                currentOnOpen(fwt.file.id); true
                            }
                            if (reorderEnabled) {
                                this.customActions = listOf(
                                    CustomAccessibilityAction(str(R.string.move_earlier)) { moveBy(fwt.file.id, -1); true },
                                    CustomAccessibilityAction(str(R.string.move_later)) { moveBy(fwt.file.id, 1); true },
                                )
                            }
                        }
                        .pointerInput(fwt.file.id) { detectTapGestures(onTap = { currentOnOpen(fwt.file.id) }) }
                        .then(if (!reorderEnabled) Modifier else Modifier.pointerInput(fwt.file.id) {
                            try {
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
                            } finally {
                                // Handler removed mid-drag (e.g. a filter turned reordering off): unfreeze
                                // the list without persisting a half-done order.
                                if (draggedId == fwt.file.id) { dragging = false; draggedId = null }
                            }
                        }),
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
        VaultRepository.isImage(mime) -> CriptaIcons.Image
        isVideo -> CriptaIcons.Movie
        else -> CriptaIcons.InsertDriveFile
    }
    if (asList) {
        // A plain row like the regular list; only the row being dragged lifts onto a tonal slab.
        Surface(
            color = if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 0.dp,
            // Min height, not fixed: large fonts grow the row instead of clipping it.
            modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite, emptyList(), Modifier.size(56.dp),
                    file = item.file, display = display, compact = true)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge)
                    if (display.showFileInfo) {
                        Text(fileMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Icon(CriptaIcons.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                        .background(com.cripta.app.ui.components.OnImageGlass), contentAlignment = Alignment.Center) {
                        Icon(CriptaIcons.DragHandle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            if (display.showFileInfo) {
                Text(fileMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, top = 1.dp))
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
private fun FolderStyleDialog(
    folderName: String,
    initialColor: Int?,
    initialIcon: String?,
    onConfirm: (Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(initialColor) }
    var icon by remember { mutableStateOf(initialIcon) }
    var browsing by remember { mutableStateOf(false) }
    if (browsing) {
        com.cripta.app.ui.tagicons.TagIconPicker(folderName, icon, onPick = { icon = it; browsing = false }, onDismiss = { browsing = false })
        return
    }
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
                com.cripta.app.ui.tagicons.TagIconField(name = folderName, icon = icon, onPick = { icon = it }, onBrowse = { browsing = true })
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
    searchedWhere: String = str(R.string.in_whole_vault),
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
private fun FabMenu(actions: List<FabAction>, modifier: Modifier = Modifier) {
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
                    Text(a.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun ImportBanner(state: VaultRepository.ImportState, onDismiss: () -> Unit) {
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
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = onContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!state.active) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
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
        val lead = if (filtering || stats.filtered) str(R.string.x_of_y, shown.total, stats.scope.total)
            else plural(R.plurals.items_n, shown.total)
        Text(lead, style = MaterialTheme.typography.labelLarge,
            color = if (filtering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = str(R.string.summary), onClick = onOpen).padding(horizontal = 6.dp, vertical = 4.dp))
        CountPill(CriptaIcons.Movie, shown.videos, str(R.string.videos_lower), type == TypeFilter.VIDEO) { onType(TypeFilter.VIDEO) }
        CountPill(CriptaIcons.Image, shown.images, str(R.string.photos_lower), type == TypeFilter.IMAGE) { onType(TypeFilter.IMAGE) }
        if (shown.others > 0 || type == TypeFilter.OTHER) {
            CountPill(CriptaIcons.InsertDriveFile, shown.others, str(R.string.other_files_lower), type == TypeFilter.OTHER) { onType(TypeFilter.OTHER) }
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
            Icon(CriptaIcons.ExpandMore, str(R.string.summary), tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        val all = listOf(str(R.string.root)) + names
        all.forEachIndexed { i, n ->
            val last = i == all.lastIndex
            Text(
                n,
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.clip(MaterialTheme.shapes.small)
                    .then(if (last) Modifier else Modifier.clickable(onClickLabel = str(R.string.go_to, n), role = Role.Button) { onGo(i) })
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
            .semantics(mergeDescendants = true) { this.contentDescription = str(R.string.count_show_only, count, what, what) }
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
    ThumbBox(null, CriptaIcons.Movie, isVideo = true, name = str(R.string.preview), selected = false, favorite = true,
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
        visible = state.active || state.lastResult != null || state.pendingOriginals.isNotEmpty(),
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
                    }, modifier = Modifier.size(32.dp)) {
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
 * The vault's cover (tags on up to 3 rows, length badge, watched progress, favorite),
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
        VaultRepository.isImage(file.mimeType) -> CriptaIcons.Image
        isVideo -> CriptaIcons.Movie
        else -> CriptaIcons.InsertDriveFile
    }
    // Decorative: every caller shows the file name as text next to the cover.
    ThumbBox(bmp, icon, isVideo, null, selected = false, favorite = file.isFavorite,
        tags = if (display.showTagsOnCover) tags else emptyList(), modifier = modifier, file = file, display = display)
}

/**
 * Folder card drawn as a stack: its cover in front (the chosen one, else the newest), the next two
 * pictures peeking out above it, like an album. Nothing is drawn over the pictures: the folder's
 * glyph (its colour and emoji), its star and its count sit with the name underneath, which keeps
 * the whole width (the actions are on long-press).
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
    /** A star action on the Favorites screen; regular cards keep their actions menu. */
    onRemoveFavorite: (() -> Unit)? = null,
) {
    val accent = folder.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val lift by animateFloatAsState(if (highlighted) 1.05f else 1f, Motion.enter(Motion.SHORT), label = "folderLift")
    val hl by animateFloatAsState(if (highlighted) 1f else 0f, Motion.enter(Motion.SHORT), label = "folderHl")
    val primary = MaterialTheme.colorScheme.primary
    val shape = com.cripta.app.ui.theme.CoverShape
    val tile = MaterialTheme.colorScheme.surfaceContainer
    // Sheets without a picture of their own (folders with 1-2 files) take the folder's colour.
    val farSheet = remember(accent, tile) { accent.copy(alpha = 0.30f).compositeOver(tile) }
    val nearSheet = remember(accent, tile) { accent.copy(alpha = 0.55f).compositeOver(tile) }
    @Composable
    fun sheetPicture(f: com.cripta.app.data.db.FileEntity?, dim: Float) {
        if (f == null) return
        val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, alpha = dim, modifier = Modifier.fillMaxSize()) }
    }
    // Scale outermost, so the caller's clip/click area scales with the card.
    // DS FolderStack: the cover in front (4:3), the next two peeking above it, dimmed.
    Column(Modifier.graphicsLayer { scaleX = lift; scaleY = lift }.then(modifier)) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Box(Modifier.offset(y = (-12).dp).padding(horizontal = 24.dp).fillMaxWidth().height(40.dp).clip(shape)
                .background(farSheet)) {
                sheetPicture(previews.getOrNull(2), 0.3f)
            }
            Box(Modifier.offset(y = (-6).dp).padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).clip(shape).background(nearSheet)) {
                sheetPicture(previews.getOrNull(1), 0.45f)
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape)
                    .background(accent.copy(alpha = 0.16f).compositeOver(tile))
                    .then(if (hl > 0f) Modifier.border(3.dp, primary.copy(alpha = hl), shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val f = previews.firstOrNull()
                if (f == null) {
                    com.cripta.app.ui.components.FolderGlyph(folder.color, folder.icon, folder.emoji, 44.dp)
                } else {
                    val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                if (hl > 0f) Box(Modifier.matchParentSize().background(primary.copy(alpha = 0.18f * hl)))
            }
        }
        // DS: the folder's colour as a small square (or its own icon), the name in `title`, the star
        // after it when favourite, the count as a caption under.
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (folder.icon.isNullOrBlank() && folder.emoji.isNullOrBlank()) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            } else {
                com.cripta.app.ui.components.FolderGlyph(folder.color, folder.icon, folder.emoji, 20.dp)
            }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // DS: the name takes the row, the star sits at its end.
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    if (folder.isFavorite && onRemoveFavorite == null) {
                        Icon(CriptaIcons.Star, str(R.string.folder_favorite), tint = com.cripta.app.ui.theme.Favorite,
                            modifier = Modifier.padding(start = 4.dp).size(18.dp))
                    }
                }
            }
            if (onRemoveFavorite != null) {
                IconButton(onClick = onRemoveFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(CriptaIcons.Star, str(R.string.unfavorite_folder), tint = com.cripta.app.ui.theme.Favorite,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
        if (showInfo) {
            val n = stat?.count ?: 0
            Text(if (n == 0) str(R.string.empty_f) else plural(R.plurals.files_n, n, com.cripta.app.ui.components.formatCount(n)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * Pick the picture in front of a folder's stack: "Automatica" (the newest file) or any of the
 * folder's own files. The current choice carries the selection ring and tick.
 */
@Composable
private fun FolderCoverSheet(
    folder: FolderEntity,
    load: suspend () -> List<com.cripta.app.data.db.FileEntity>,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val files by produceState<List<com.cripta.app.data.db.FileEntity>?>(null, folder.id) { value = load() }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(str(R.string.cover_of, folder.name), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Text(str(R.string.cover_tap_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            com.cripta.app.ui.components.ChoiceChip(
                selected = folder.coverFileId == null,
                onClick = { afterSheet { onPick(null) } },
                label = { Text(str(R.string.cover_auto_latest)) },
            )
            val list = files
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                list.isEmpty() -> Text(str(R.string.folder_no_files), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.size, key = { list[it].id }) { i ->
                        val f = list[i]
                        val chosen = f.id == folder.coverFileId
                        com.cripta.app.ui.components.MediaThumb(
                            f, thumb,
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(com.cripta.app.ui.theme.CoverShape)
                                .clickable(onClickLabel = str(R.string.use_as_cover)) { afterSheet { onPick(f.id) } }
                                .semantics { contentDescription = f.originalName; selected = chosen },
                            selected = chosen,
                            showFavoriteBadge = false,
                        )
                    }
                }
            }
        }
    }
}

private class SelAction(val icon: ImageVector, val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The bottom selection bar. When its actions don't all fit (portrait), it scrolls sideways: the
 * last visible action is cut in half, the edge fades with a chevron pointing to the rest, and the
 * very first time it nudges on appearing to show that it moves.
 */
@Composable
private fun SelectionBar(actions: List<SelAction>) {
    // A tone above the navigation bar below it, so the two read as different layers: actions on the
    // selection here, destinations there.
    androidx.compose.material3.BottomAppBar(
        contentPadding = PaddingValues(0.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Every action keeps its whole name: few actions share the width, many scroll sideways.
            val minW = maxOf(72.dp, maxWidth / actions.size)
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // The nudge only the very first time the actions overflow: after that the fade and the
            // chevron are enough, and sliding on every selection was too much.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(scroll.maxValue > 0) {
                if (scroll.maxValue == 0) return@LaunchedEffect
                val prefs = ctx.getSharedPreferences("vault_hints", android.content.Context.MODE_PRIVATE)
                val shown = prefs.getInt("selection_bar_nudges", 0)
                if (shown >= 1) return@LaunchedEffect
                prefs.edit().putInt("selection_bar_nudges", shown + 1).apply()
                kotlinx.coroutines.delay(350)
                val px = with(density) { 48.dp.roundToPx() }
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
                actions.forEach { SelectionAction(it.icon, it.label, Modifier.widthIn(min = minW), it.destructive, it.onClick) }
            }
            // Chevrons at the edges while there is more that way.
            val fwd by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, label = "selFwd")
            val back by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, label = "selBack")
            Icon(CriptaIcons.KeyboardArrowRight, if (scroll.canScrollForward) str(R.string.more_actions) else null,
                Modifier.align(Alignment.CenterEnd).size(20.dp).graphicsLayer { alpha = fwd },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(CriptaIcons.KeyboardArrowLeft, null,
                Modifier.align(Alignment.CenterStart).size(20.dp).graphicsLayer { alpha = back },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One labelled action of the bottom selection bar: icon over its full name, never cut. */
@Composable
private fun SelectionAction(icon: ImageVector, label: String, modifier: Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** The sort as a chip label: "Più recenti", "A → Z"… */
private fun sortChipLabel(key: com.cripta.app.data.SortKey, ascending: Boolean): String = when (key) {
    com.cripta.app.data.SortKey.DATE -> if (ascending) str(R.string.order_oldest) else str(R.string.order_newest)
    com.cripta.app.data.SortKey.NAME -> if (ascending) str(R.string.a_to_z) else str(R.string.z_to_a)
    com.cripta.app.data.SortKey.SIZE -> if (ascending) str(R.string.order_smallest) else str(R.string.order_largest)
    com.cripta.app.data.SortKey.MANUAL -> str(R.string.manual_order)
}

/**
 * DS chip row under the search pill. The tab's root: "Solo preferiti", "Senza etichette" and the
 * sort; inside a folder: the type ("Tutti", "Foto", "Video") and "Preferiti". On = accent with a tick.
 */
@Composable
private fun QuickChips(
    atRoot: Boolean,
    filters: Filters,
    sortLabel: String,
    onType: (TypeFilter) -> Unit,
    onFavorites: () -> Unit,
    onUntagged: () -> Unit,
    onSort: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // The chips' 48dp touch area adds 6dp above and below: 12dp from the search, 20 to the grid (DS).
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    ) {
        if (atRoot) {
            item {
                com.cripta.app.ui.components.ChoiceChip(filters.favoritesOnly, onFavorites, { Text(str(R.string.favorites_only)) },
                    leadingIcon = { Icon(if (filters.favoritesOnly) CriptaIcons.Star else CriptaIcons.StarBorder, null, Modifier.size(18.dp)) })
            }
            item { com.cripta.app.ui.components.ChoiceChip(filters.untaggedOnly, onUntagged, { Text(str(R.string.untagged)) }) }
            item {
                com.cripta.app.ui.components.ActionChip(onSort, { Text(sortLabel) },
                    leadingIcon = { Icon(CriptaIcons.Sort, null, Modifier.size(18.dp)) })
            }
        } else {
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.ALL, { onType(TypeFilter.ALL) }, { Text(str(R.string.all)) }) }
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.IMAGE, { onType(TypeFilter.IMAGE) }, { Text(str(R.string.photo)) }) }
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.VIDEO, { onType(TypeFilter.VIDEO) }, { Text(str(R.string.video)) }) }
            // Notes, PDFs and other files: the sheet's fourth type, reachable here too.
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.OTHER, { onType(TypeFilter.OTHER) }, { Text(str(R.string.other)) }) }
            item {
                com.cripta.app.ui.components.ChoiceChip(filters.favoritesOnly, onFavorites, { Text(str(R.string.favorites)) },
                    leadingIcon = { Icon(if (filters.favoritesOnly) CriptaIcons.Star else CriptaIcons.StarBorder, null, Modifier.size(18.dp)) })
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
private fun ImportConflictsPage(
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

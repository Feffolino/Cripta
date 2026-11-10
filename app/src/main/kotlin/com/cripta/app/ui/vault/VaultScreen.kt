package com.cripta.app.ui.vault

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.layout
import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.vaultUnlocked
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import com.cripta.app.data.SortKey
import com.cripta.app.data.ViewMode
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.animationsEnabled
import com.cripta.app.ui.theme.pressScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
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

internal fun typeLabel(t: TypeFilter) = when (t) {
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
internal const val FOLDERS_KEY = "folders"

/** One entry of the speed-dial FAB. */
internal class FabAction(val label: String, val icon: ImageVector, val caption: String? = null, val onClick: () -> Unit)

fun tagAlias(tag: TagEntity): String =
    tag.alias?.takeIf { it.isNotBlank() } ?: tag.name.take(2).uppercase(com.cripta.app.Strings.locale)

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
    val horizontalSign = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f
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
    // Long-press drag on the grid. Started on an unselected file it is a gallery-style range
    // select; started on a selected file it carries the selection ("move drag") to a folder.
    val gridState = rememberLazyGridState()
    var dragAnchor by remember { mutableStateOf<String?>(null) }
    var dragBase by remember { mutableStateOf(setOf<String>()) }
    var hoverFolder by remember { mutableStateOf<Long?>(null) }
    /** The dragging finger, in the coordinates of the box around the grid. */
    var selPointer by remember { mutableStateOf(Offset.Zero) }
    /** Files held by a move drag, in grid order (null when no move drag is on). */
    var carried by remember { mutableStateOf<List<String>?>(null) }
    /** Each carried file's folder when the drag started: what the drop's "Annulla" restores. */
    var carriedFrom by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    /** The level (folder id, null = root) the move drag started in. */
    var carryOrigin by remember { mutableStateOf<Long?>(null) }
    /** Grid auto-scroll speed in px/s while a dragging finger is near an edge (0 = still). */
    var autoScrollSpeed by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
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
    /** Height of the floating buttons and of their open menu (the snackbar keeps above them). */
    var fabStackPx by remember { mutableIntStateOf(0) }
    var fabMenuPx by remember { mutableIntStateOf(0) }

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
        vm.moveFiles(ids, target) { moved ->
            // After the work: the number really moved, and an undo only when something moved.
            if (moved == 0) notify(str(R.string.bulk_partial, moved, ids.size))
            else notify(
                if (moved < ids.size) str(R.string.bulk_partial, moved, ids.size)
                else str(R.string.moved_to, plural(R.plurals.moved_items, moved), folderLabel(target)),
            ) { vm.restoreFolders(prev) }
        }
    }
    /** Drop of a move drag. The drag may have changed folder, so the files can be off screen:
     *  the undo snapshot is [from], taken when the drag started. */
    fun moveCarried(ids: List<String>, from: Map<String, Long?>, target: Long?) {
        val moving = ids.filter { from.containsKey(it) && from[it] != target }
        if (moving.isEmpty()) return
        vm.moveFiles(moving, target) { moved ->
            if (moved == 0) notify(str(R.string.bulk_partial, moved, moving.size))
            else notify(
                if (moved < moving.size) str(R.string.bulk_partial, moved, moving.size)
                else str(R.string.moved_to, plural(R.plurals.moved_items, moved), folderLabel(target)),
            ) { vm.restoreFolders(moving.associateWith { from[it] }) }
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
    // While a move drag holds files, Back climbs one level and the files stay in hand.
    BackHandler(enabled = carried != null && path.isNotEmpty()) { vm.goUp() }
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
    var downloadIds by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val downloadTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        val ids = downloadIds
        downloadIds = emptyList()
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
    // The bar lifts from the grid's real position: a folder reopened at its remembered place is
    // scrolled without a drag, which the scroll behaviour never sees.
    val topLift = remember { com.cripta.app.ui.components.TopBarLift() }
    com.cripta.app.ui.components.TrackTopBarLift(topLift, gridState)

    // Keep the last count while the title crossfades out of selection mode (selection is empty by then).
    var lastSelCount by remember { mutableIntStateOf(1) }
    SideEffect { if (selection.isNotEmpty()) lastSelCount = selection.size }
    val shownSelCount = if (selection.isNotEmpty()) selection.size else lastSelCount

    val coverPeopleMap by vm.coverPeople.collectAsStateWithLifecycle()
    val coverPeople = remember(coverPeopleMap) { CoverPeople(coverPeopleMap) { vm.faceCrop(it) } }
    androidx.compose.runtime.CompositionLocalProvider(LocalCoverPeople provides coverPeople) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The snackbar is placed by the screen itself (below), above whatever sits on the bottom edge.
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                scrollBehavior = scrollBehavior,
                // Same colour when the content scrolls under it (M3 otherwise switches to a tinted
                // surface-container colour, which looked light blue on this theme).
                colors = com.cripta.app.ui.components.criptaTopBarColors(topLift.lifted),
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
          // Height of the buttons on screen (0 while hidden), so the snackbar can sit above them.
          Box(Modifier.onSizeChanged { fabStackPx = it.height }) {
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
                                modifier = Modifier.minimumInteractiveComponentSize().size(44.dp).graphicsLayer { alpha = randomAlpha }
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
                                    Box(Modifier.onSizeChanged { fabMenuPx = it.height }) { FabMenu(actions) }
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
          }
        },
    ) { pad ->
      Box(Modifier.fillMaxSize().padding(pad)) {
        Column(Modifier.fillMaxSize().nestedScroll(fabScroll)) {
            // Everything above the grid (search, chips, banners, strips) is one header with the top
            // bar: when the grid scrolls under it, the whole header lifts, not the bar alone.
            com.cripta.app.ui.components.LiftedHeader(topLift.lifted) {
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
            // People matching the typed text: a tap filters by that person.
            val foundPeople by vm.searchPeople.collectAsStateWithLifecycle()
            if (filters.query.isNotBlank() && foundPeople.isNotEmpty()) {
                Text(str(R.string.people), style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() })
                Box(Modifier.padding(horizontal = 8.dp)) { PeopleFaceRow(foundPeople, filters.personIds, { vm.faceCrop(it) }, vm::pickSearchedPerson) }
            }
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
            // Folder cards live in a horizontal row; their on-screen bounds (clipped to what is
            // visible, registered as cards scroll in) are the drop targets of a move drag.
            val folderBounds = remember { androidx.compose.runtime.mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
            /** The box around the grid, whose coordinates the drag uses. */
            var dragAreaCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            /** Pointer that last went down on the grid itself (not on the fast scroller over it). */
            val gridDown = remember { arrayOfNulls<androidx.compose.ui.input.pointer.PointerId>(1) }
            val currentLevel by rememberUpdatedState(path.lastOrNull()?.id)
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val flingBehavior = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
            val dragDensity = LocalDensity.current
            val dragAnim = animationsEnabled()
            /** The move drag's visual layer (stack of covers, pick-up, drop, way back). */
            val dragFx = remember { MoveDragFx() }
            /** Files a move drag lifted out of the grid: faded, with a dashed slot, until dropped or put back. */
            var lifted by remember { mutableStateOf<Set<String>>(emptySet()) }
            /** The cell a move drag started from: where the stack goes back on cancel. */
            val moveAnchor = remember { arrayOfNulls<String>(1) }
            /** How deep the dragging finger is in an auto-scroll edge zone (see [edgeDepth]). */
            var edgeNow by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            // Landing on a folder: its card squeezes, its count ticks up in the accent colour.
            var landFolder by remember { mutableStateOf<Long?>(null) }
            var landBump by remember { mutableIntStateOf(0) }
            var landCount by remember { mutableStateOf<Int?>(null) }
            var landFlash by remember { mutableStateOf(false) }
            /** After a drop, the grid closes the gaps with a placement animation (off otherwise). */
            var closingGaps by remember { mutableStateOf(false) }

            /** A visible file's cover, in the drag area's coordinates. */
            fun coverRect(info: androidx.compose.foundation.lazy.grid.LazyGridItemInfo): androidx.compose.ui.geometry.Rect = with(dragDensity) {
                val o = Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
                if (viewMode == ViewMode.GRID) androidx.compose.ui.geometry.Rect(o, androidx.compose.ui.geometry.Size(info.size.width.toFloat(), info.size.width.toFloat()))
                else androidx.compose.ui.geometry.Rect(o + Offset(4.dp.toPx(), 6.dp.toPx()), androidx.compose.ui.geometry.Size(76.dp.toPx(), 76.dp.toPx()))
            }
            fun coverRectOf(key: String) = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { coverRect(it) }
            /** Centre of a folder card's picture, in the drag area's coordinates (null when not on screen). */
            fun folderTileCenter(id: Long): Offset? {
                val r = folderBounds[id] ?: return null
                val area = dragAreaCoords?.takeIf { it.isAttached } ?: return null
                val o = area.localToRoot(Offset.Zero)
                val tileH = with(dragDensity) { 12.dp.toPx() } + r.width * 0.375f
                return Offset(r.center.x, r.top + tileH) - o
            }

            fun folderUnder(pos: Offset): Long? {
                val root = dragAreaCoords?.takeIf { it.isAttached }?.localToRoot(pos) ?: return null
                return folderBounds.entries.firstOrNull { it.value.contains(root) }?.key
            }
            /** Re-reads the file under the finger (after it moved, or the grid scrolled under it)
             *  and selects the anchor-to-finger range. A move drag never changes the selection. */
            fun updateRange() {
                if (carried != null) return
                val anchor = dragAnchor ?: return
                val cur = (keyAt(selPointer, gridState) as? String)?.takeIf { it in currentIdSet } ?: return
                val ai = currentOrderedIds.indexOf(anchor)
                val ci = currentOrderedIds.indexOf(cur)
                if (ai >= 0 && ci >= 0) {
                    selection = dragBase + currentOrderedIds.subList(minOf(ai, ci), maxOf(ai, ci) + 1)
                }
            }
            /** Long-press landed at [pos]: starts a move drag ([carry], only on a selected file) or a
             *  range select. False if no file is there. */
            fun startDrag(pos: Offset, carry: Boolean): Boolean {
                val key = (keyAt(pos, gridState) as? String)?.takeIf { it in currentIdSet } ?: return false
                selPointer = pos
                hoverFolder = null
                if (carry && key in selection) {
                    val held = selection
                    carried = currentOrderedIds.filter { it in held }
                    carriedFrom = files.filter { it.file.id in held }.associate { it.file.id to it.file.folderId }
                    carryOrigin = currentLevel
                    moveAnchor[0] = key
                    lifted = held
                    pressedKey = null
                    // The anchor's cover tops the stack where it is; the other selected cells on
                    // screen (nearest first) fly to the finger. Off-screen ones only join the count.
                    val byId = files.filter { it.file.id in held }.associateBy { it.file.id }
                    val anchorRect = coverRectOf(key)
                    val anchorFile = byId[key]?.file
                    if (anchorRect != null && anchorFile != null) {
                        val others = gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                            val k = info.key as? String ?: return@mapNotNull null
                            if (k == key) return@mapNotNull null
                            byId[k]?.file?.let { it to coverRect(info) }
                        }.sortedBy { (it.second.center - anchorRect.center).getDistance() }
                        dragFx.start(uiScope, anchorFile, anchorRect, others, dragAnim)
                    }
                } else {
                    dragAnchor = key
                    dragBase = selection
                    selection = selection + key
                }
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                return true
            }
            /** Finger lifted ([drop]) or gesture cancelled. A move drag drops on the folder under the
             *  finger, else into the folder now open if the drag changed folder; otherwise nothing moves. */
            fun endDrag(drop: Boolean) {
                val ids = carried
                if (ids != null) {
                    val from = carriedFrom
                    val target = if (drop) moveDropTarget(hoverFolder, currentLevel, carryOrigin) else DropTarget.Stay
                    if (target is DropTarget.Into) {
                        selection = emptySet()
                        val dest = target.id
                        // Released on a folder card: the covers fly into it. Into the open folder
                        // (no card for it): the stack just fades where it is.
                        val tile = hoverFolder?.let { folderTileCenter(it) }
                        val shownCount = if (tile != null && dest != null && display.showFolderInfo) folderStats[dest]?.count ?: 0 else null
                        val moving = ids.count { from.containsKey(it) && from[it] != dest }
                        landFolder = if (tile != null) dest else null
                        dragFx.drop(uiScope, tile, dragAnim,
                            onArrive = { k, total ->
                                if (k == 0) landBump++
                                if (shownCount != null) {
                                    landFlash = true
                                    landCount = shownCount + (moving * (k + 1) + total - 1) / total
                                }
                            },
                            onDone = {
                                closingGaps = true
                                moveCarried(ids, from, dest)
                                lifted = emptySet()
                                uiScope.launch {
                                    kotlinx.coroutines.delay(if (dragAnim) Motion.LONG + 500L else 0L)
                                    closingGaps = false; landFlash = false; landCount = null; landFolder = null
                                }
                            })
                    } else {
                        // Back where it started: opening another folder had cleared the selection.
                        if (currentLevel == carryOrigin && selection.isEmpty()) selection = ids.filterTo(HashSet()) { it in visibleIds }
                        val back = if (currentLevel == carryOrigin) moveAnchor[0]?.let { coverRectOf(it)?.center } else null
                        dragFx.cancel(uiScope, back, dragAnim)
                        lifted = emptySet()
                    }
                }
                carried = null; carriedFrom = emptyMap(); dragAnchor = null; hoverFolder = null
                autoScrollSpeed = 0f; edgeNow = 0f
            }
            // The folder under a move drag's finger: followed also when the finger is still and the
            // folders move under it (second finger scrolling the row, auto-scroll, a level change).
            LaunchedEffect(Unit) {
                snapshotFlow { if (carried != null) folderUnder(selPointer) else null }.collect { over ->
                    if (carried == null) return@collect
                    if (over != null && over != hoverFolder) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    }
                    hoverFolder = over
                }
            }
            // Auto-scroll: a dragging finger near the grid's top or bottom edge scrolls it, faster the
            // deeper it is in the edge zone; the range follows the file now under the still finger.
            // It starts after a short wait and then speeds up gently (see edgeRamp).
            val autoScrolling by remember { derivedStateOf { autoScrollSpeed != 0f } }
            LaunchedEffect(autoScrolling) {
                if (!autoScrolling) return@LaunchedEffect
                val start = androidx.compose.runtime.withFrameNanos { it }
                var last = start
                while (true) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    if (autoScrollSpeed == 0f) break
                    val dy = autoScrollSpeed * edgeRamp((now - start) / 1_000_000) * (now - last) / 1_000_000_000f
                    last = now
                    if (dy == 0f) continue
                    try {
                        gridState.scrollBy(dy)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Pre-empted by another scroll (a level change restoring its position): go on.
                        ensureActive()
                    }
                    updateRange()
                }
            }

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
                        Modifier.fillMaxWidth().minimumInteractiveComponentSize()
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
                    // One folder card: the same in the grid and in the sideways row.
                    @Composable
                    fun folderCard(folder: FolderEntity, sizing: Modifier) {
                            androidx.compose.runtime.DisposableEffect(folder.id) {
                                onDispose { folderBounds.remove(folder.id) }
                            }
                            // While selecting, a folder tap would silently drop the selection: say why
                            // instead. During a move drag (a second finger) it opens the folder and the
                            // first finger keeps the files.
                            val open = {
                                if (carried != null) vm.enterFolder(folder)
                                else if (inSelection) notify(str(R.string.leave_selection_to_open))
                                else vm.enterFolder(folder)
                            }
                            FolderMosaic(
                                folder = folder,
                                stat = folderStats[folder.id],
                                previews = folderPreviews[folder.id].orEmpty(),
                                thumb = { vm.thumb(it) },
                                showInfo = display.showFolderInfo,
                                highlighted = hoverFolder == folder.id,
                                bump = if (landFolder == folder.id) landBump else 0,
                                countAtLeast = if (landFolder == folder.id) landCount else null,
                                countFlash = landFolder == folder.id && landFlash,
                                modifier = sizing
                                    .onGloballyPositioned { folderBounds[folder.id] = it.boundsInRoot() }
                                    .combinedClickable(
                                        onClickLabel = str(R.string.open),
                                        onLongClickLabel = str(R.string.folder_actions),
                                        onClick = open,
                                        onLongClick = { if (carried == null) folderMenu = folder },
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
                    if (!foldersCollapsed && display.foldersHorizontal) {
                        // Impostazioni › Aspetto › "Cartelle in una riga": one row scrolling sideways,
                        // so the files start right below however many folders there are.
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(folders.size, key = { folders[it].id }) { i ->
                                val folder = folders[i]
                                folderCard(folder, Modifier.width(if (landscape) 132.dp else 150.dp))
                            }
                        }
                    } else if (!foldersCollapsed) folders.chunked(perRow).forEach { rowFolders ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowFolders.forEach { folder ->
                          androidx.compose.runtime.key(folder.id) {
                            folderCard(folder, Modifier.weight(1f))
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
            var shownLevel by remember { mutableLongStateOf(path.lastOrNull()?.id ?: ROOT_KEY) }
            var shownDepth by remember { mutableIntStateOf(path.size) }
            LaunchedEffect(path, horizontalSign) {
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
                    levelX.snapTo(with(density) { 32.dp.toPx() } * horizontalSign * if (deeper) 1f else -1f)
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
            // The long-press drag lives on this box, not on the grid: a move drag can change folder
            // (second finger, Back), and the grid leaves the screen when the new folder is empty.
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { dragAreaCoords = it }
                    // Manual order keeps its own long-press drag (reordering).
                    .then(if (manual) Modifier else Modifier.pointerInput(Unit) {
                        var flingJob: kotlinx.coroutines.Job? = null
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (gridDown[0] != down.id) return@awaitEachGesture
                            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            val slop = viewConfiguration.touchSlop
                            val pressedKey0 = (keyAt(press.position, gridState) as? String)?.takeIf { it in currentIdSet }
                                ?: return@awaitEachGesture
                            // On a file already selected the long-press is ambiguous: dragging at once
                            // goes on with the quick selection (from this file), holding still a moment
                            // longer lifts the selection to move it.
                            var carry = false
                            if (pressedKey0 in selection) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                val moved = withTimeoutOrNull(LIFT_AFTER_MS) {
                                    while (true) {
                                        val e = awaitPointerEvent(PointerEventPass.Initial)
                                        val c = e.changes.firstOrNull { it.id == press.id } ?: return@withTimeoutOrNull false
                                        if (!c.pressed) { c.consume(); return@withTimeoutOrNull false }
                                        val far = (c.position - press.position).getDistance() > slop
                                        c.consume()
                                        if (far) return@withTimeoutOrNull true
                                    }
                                    @Suppress("UNREACHABLE_CODE") false
                                }
                                if (moved == false) return@awaitEachGesture
                                carry = moved == null
                            }
                            if (!startDrag(press.position, carry)) return@awaitEachGesture
                            val zone = 64.dp.toPx()
                            val maxSpeed = 1600.dp.toPx()
                            // Auto-scroll waits until the finger has moved: a long-press near an edge
                            // must not set the grid running by itself.
                            var armed = false
                            var dropped = false
                            var helper: PointerId? = null
                            var helperTravel = 0f
                            var helperScrolling = false
                            val tracker = VelocityTracker()
                            try {
                                while (true) {
                                    // Initial pass: the held finger belongs to this gesture alone, so
                                    // the grid neither scrolls nor taps with it.
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val main = event.changes.firstOrNull { it.id == press.id } ?: break
                                    if (!main.pressed) {
                                        // A cancelled touch stream arrives already consumed: no drop then.
                                        dropped = !main.isConsumed
                                        main.consume()
                                        break
                                    }
                                    if (main.positionChanged()) {
                                        selPointer = main.position
                                        if (!armed && (main.position - press.position).getDistance() > slop) armed = true
                                        if (armed) {
                                            val depth = edgeDepth(main.position.y, size.height.toFloat(), zone)
                                            autoScrollSpeed = edgeSpeed(depth, maxSpeed)
                                            // The band shows only where the grid scrolls (or is about to).
                                            edgeNow = if (autoScrollSpeed == 0f) 0f else depth
                                        }
                                        updateRange()
                                    }
                                    main.consume()
                                    if (carried == null) continue
                                    // Main pass, during a move drag: a second finger that nothing under
                                    // it used (the folder row scrolls sideways and folders open by
                                    // themselves) scrolls the grid, with a fling when it lets go.
                                    val after = awaitPointerEvent(PointerEventPass.Main)
                                    for (c in after.changes) {
                                        if (c.id == press.id) continue
                                        if (c.changedToDownIgnoreConsumed() && helper == null) {
                                            helper = c.id
                                            helperTravel = 0f
                                            helperScrolling = false
                                            tracker.resetTracking()
                                            flingJob?.cancel()
                                        }
                                        if (c.id != helper) continue
                                        tracker.addPosition(c.uptimeMillis, c.position)
                                        if (!c.pressed) {
                                            helper = null
                                            if (helperScrolling) {
                                                val v = tracker.calculateVelocity().y
                                                flingJob = uiScope.launch { gridState.scroll { with(flingBehavior) { performFling(-v) } } }
                                            }
                                        } else if (!c.isConsumed) {
                                            val dy = c.positionChange().y
                                            if (!helperScrolling) {
                                                helperTravel += dy
                                                if (kotlin.math.abs(helperTravel) > slop) helperScrolling = true
                                            }
                                            if (helperScrolling && dy != 0f) {
                                                gridState.dispatchRawDelta(-dy)
                                                c.consume()
                                            }
                                        }
                                    }
                                }
                            } finally {
                                endDrag(dropped)
                            }
                        }
                    }),
            ) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { translationX = levelX.value; alpha = if (levelPending) 0f else levelAlpha.value },
            ) {
            // Before the first level has been read there is nothing to say yet (not "empty").
            val firstRead = filesLevelState.value == LEVEL_UNKNOWN || foldersLevelState.value == LEVEL_UNKNOWN
            when {
                firstRead && files.isEmpty() -> VaultSkeleton(columnCount, viewMode, display.showFileInfo, Modifier.fillMaxSize())
                files.isEmpty() && !showFolders ->
                    EmptyState(filters.active, Modifier.fillMaxSize(),
                        onImport = { startImport() },
                        folderName = pathNames.lastOrNull(),
                        // Where the filters actually looked: the vault, this folder, or the root's own files.
                        searchedWhere = when {
                            !filterLocalOnly || filters.wholeVault -> str(R.string.in_whole_vault)
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
                        // Marks the pointers that land on the grid itself: the long-press drag is
                        // handled by the box around it (it must outlive a change of folder), and
                        // only these may start it (not one on the fast scroller over the grid).
                        .pointerInput(Unit) {
                            awaitEachGesture { gridDown[0] = awaitFirstDown(requireUnconsumed = false).id }
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
                            val slotOutline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
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
                                // Only right after a drop do the cells glide over the gaps it left.
                                Modifier.animateItem(placementSpec = if (closingGaps) Motion.enter(Motion.LONG) else null)
                                    .pressScale(pressedKey == id, if (viewMode == ViewMode.GRID) 0.96f else 0.98f)
                                    // Files held by a move drag stay behind faded, their slot dashed.
                                    .leftBehind(id in lifted, slotOutline)
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
                        if (showFolders) out += str(R.string.home_folders)
                        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", com.cripta.app.Strings.locale).withDecimalStyle(java.time.format.DecimalStyle.of(com.cripta.app.Strings.locale))
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
            EdgeScrollBands { edgeNow }
            // The carried files follow the finger as a stack of covers, saying where they would land.
            // The last texts stay while the stack flies off.
            val held = carried
            val stackText = remember { arrayOf(0 to "") }
            if (held != null) {
                stackText[0] = held.size to when (val t = moveDropTarget(hoverFolder, currentLevel, carryOrigin)) {
                    is DropTarget.Into -> str(R.string.drag_into, folderLabel(t.id))
                    DropTarget.Stay -> str(R.string.drag_onto_folder)
                }
            }
            dragFx.Layer(
                finger = { selPointer },
                hoverAt = { hoverFolder?.let { folderTileCenter(it) } },
                hovering = hoverFolder != null,
                caption = stackText[0].second,
                count = stackText[0].first,
                thumb = { vm.thumb(it) },
                animate = dragAnim,
                density = dragDensity,
            )
            }
        }
        // Snackbar: always above the navigation bar and above what is on the bottom edge right now
        // (the "+" and "a caso" buttons, their open menu, or the selection bar), following them as
        // they come and go.
        val snackLift by androidx.compose.animation.core.animateDpAsState(
            with(LocalDensity.current) {
                if (inSelection || fabStackPx == 0) 8.dp
                else (fabStackPx + if (fabExpanded) fabMenuPx else 0).toDp() + 16.dp + 12.dp
            },
            Motion.enter(Motion.MEDIUM), label = "snackLift",
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = snackLift))
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
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.cripta.app.Strings.locale).format(java.util.Date(folder.createdAt)),
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
                    selection = emptySet(); confirmMultiDelete = false
                    // Said after the work, with the number really done.
                    vm.deleteFiles(toDelete) { done ->
                        notify(
                            when {
                                done < n -> str(R.string.bulk_partial, done, n)
                                trashEnabled -> plural(R.plurals.files_trashed, done)
                                else -> plural(R.plurals.files_deleted, done)
                            },
                        )
                    }
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
                    folderToDelete = null
                    vm.deleteFolder(folder) { done ->
                        notify(
                            when {
                                done == 0 -> str(R.string.bulk_partial, 0, 1)
                                trashEnabled -> str(R.string.folder_in_trash, folder.name)
                                else -> str(R.string.folder_deleted, folder.name)
                            },
                        )
                    }
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
            confirmButton = { TextButton(onClick = {
                vm.deleteOriginals(pendingOriginals) { kept -> if (kept > 0) notify(str(R.string.n_originals_kept, kept)) }
            }) { com.cripta.app.ui.components.ButtonText(plural(R.plurals.delete_original_btn, n)) } },
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

internal fun LazyGridScope.header(text: String, count: Int? = null) {
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

internal fun groupByDay(files: List<FileWithTags>): List<Pair<String, List<FileWithTags>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", com.cripta.app.Strings.locale).withDecimalStyle(java.time.format.DecimalStyle.of(com.cripta.app.Strings.locale))
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


/** How long a finger must stay still after the long-press on a selected file to lift the selection
 *  (moving before that goes on with the quick selection instead). */
private const val LIFT_AFTER_MS = 300L

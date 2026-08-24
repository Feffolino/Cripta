package com.cripta.app.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    var selection by remember { mutableStateOf(setOf<String>()) }
    var batchTag by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var tagTargetId by remember { mutableStateOf<String?>(null) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var folderMenu by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var confirmMultiDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }

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
                    Text(
                        if (inSelection) "${selection.size} selezionati" else (path.lastOrNull()?.name ?: "Cripta"),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    )
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
                        IconButton(onClick = { showSort = true }) { Icon(Icons.Filled.Sort, "Ordina") }
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
                    SmallFloatingActionButton(onClick = onNewNote) {
                        Icon(Icons.Filled.Description, "Nuova nota")
                    }
                    SmallFloatingActionButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, "Nuova cartella")
                    }
                    SmallFloatingActionButton(onClick = { vm.randomPick()?.let { vm.publishViewerQueue(); onOpenFile(it) } }) {
                        Icon(Icons.Filled.Casino, "Casuale")
                    }
                    FloatingActionButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Add, "Importa")
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = vm::setQuery,
                label = { Text("Cerca nome o tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            FilterSection(filtersExpanded, { filtersExpanded = !filtersExpanded },
                filters, tags, vm::setType, { vm.setFavoritesOnly(!filters.favoritesOnly) }, vm::toggleTag)

            val columns = if (viewMode == ViewMode.GRID) GridCells.Fixed(gridColumns) else GridCells.Fixed(1)
            // Group once per file-list change, not on every recomposition of the grid.
            val grouped = remember(files) { groupByDay(files) }
            if (folders.isEmpty() && files.isEmpty())
                EmptyState(filters.active, Modifier.fillMaxSize())
            else
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (folders.isNotEmpty() && !filters.active) {
                    header("Cartelle")
                    items(folders, key = { "f-${it.id}" }, span = { GridItemSpan(1) }) { folder ->
                        FolderCell(folder, viewMode, folderStats[folder.id],
                            modifier = Modifier.animateItem(),
                            onOpen = { vm.enterFolder(folder) }, onLongPress = { folderMenu = folder })
                    }
                }
                grouped.forEach { (label, group) ->
                    header(label)
                    items(group, key = { it.file.id }, span = { GridItemSpan(1) }) { fwt ->
                        FileCell(
                            item = fwt,
                            viewMode = viewMode,
                            selected = fwt.file.id in selection,
                            selectionMode = inSelection,
                            modifier = Modifier.animateItem(),
                            thumb = { vm.thumb(fwt.file) },
                            onOpen = { vm.publishViewerQueue(); onOpenFile(fwt.file.id) },
                            onToggleSelect = {
                                selection = if (fwt.file.id in selection) selection - fwt.file.id else selection + fwt.file.id
                            },
                        )
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
        AlertDialog(
            onDismissRequest = { folderMenu = null },
            title = { Text(folder.name) },
            text = { Text("Scegli un'azione per la cartella.") },
            confirmButton = { TextButton(onClick = { folderToRename = folder; folderMenu = null }) { Text("Rinomina") } },
            dismissButton = {
                TextButton(onClick = { folderToDelete = folder; folderMenu = null }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    folderToRename?.let { folder ->
        TextPromptDialog("Rinomina cartella", "Nome", initial = folder.name,
            onConfirm = { vm.renameFolder(folder, it); folderToRename = null },
            onDismiss = { folderToRename = null })
    }

    if (showSort) {
        SortDialog(sortKey, sortAscending,
            onPick = { k, a -> vm.setSort(k, a); showSort = false },
            onDismiss = { showSort = false })
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
    modifier: Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val subtitle = folderSubtitle(stat)
    if (viewMode == ViewMode.LIST) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().aspectRatio(1f)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp))
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

    val clickMod = Modifier.combinedClickable(
        onClick = { if (selectionMode) onToggleSelect() else onOpen() },
        onLongClick = onToggleSelect,
    )

    if (viewMode == ViewMode.LIST) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth().then(clickMod)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // List mode: no tag badges on the cover — tags are shown as text below the name.
                ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite,
                    emptyList(), Modifier.size(56.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(fileMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Column(modifier.fillMaxWidth().then(clickMod)) {
        ThumbBox(bmp, fallbackIcon, isVideo, item.file.originalName, selected, item.file.isFavorite, item.tags,
            Modifier.fillMaxWidth().aspectRatio(1f))
        Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        Text(fileMeta(item.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp))
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
            FlowRow(
                Modifier.align(Alignment.BottomStart).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                tags.take(3).forEach { tag ->
                    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall) {
                        Text(tagAlias(tag), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    filters: Filters,
    tags: List<TagEntity>,
    onType: (TypeFilter) -> Unit,
    onFav: () -> Unit,
    onTag: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filtri", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (filters.active) {
                Text("attivi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 6.dp))
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Comprimi" else "Espandi")
        }
        if (expanded) {
            Text("Tipo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Single horizontally-scrollable row so type chips never wrap when space is tight.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TypeFilter.entries.forEach { t ->
                    FilterChip(selected = filters.type == t, onClick = { onType(t) }, label = { Text(typeLabel(t)) })
                }
                FilterChip(selected = filters.favoritesOnly, onClick = onFav, label = { Text("Preferiti") })
            }
            if (tags.isNotEmpty()) {
                Text("Tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        FilterChip(selected = tag.id in filters.tagIds, onClick = { onTag(tag.id) },
                            label = { Text(if (!tag.alias.isNullOrBlank()) "${tag.alias} #${tag.name}" else "#${tag.name}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun SortDialog(current: SortKey, ascending: Boolean, onPick: (SortKey, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordina") },
        text = {
            Column {
                listOf(SortKey.DATE to "Data", SortKey.NAME to "Nome", SortKey.SIZE to "Dimensione").forEach { (k, lbl) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(k, ascending) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lbl, Modifier.weight(1f),
                            color = if (k == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (k == current) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { onPick(current, !ascending) }.padding(vertical = 12.dp)) {
                    Text(if (ascending) "Crescente ↑" else "Decrescente ↓", color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
private fun MoveToFolderDialog(folders: List<FolderEntity>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val byId = remember(folders) { folders.associateBy { it.id } }
    fun depth(f: FolderEntity): Int {
        var d = 0; var p = f.parentId; var guard = 0
        while (p != null && guard < 50) { d++; p = byId[p]?.parentId; guard++ }
        return d
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sposta in…") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 10.dp)) {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text("Radice", Modifier.padding(start = 8.dp))
                }
                folders.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(f.id) }
                            .padding(vertical = 10.dp).padding(start = (12 * depth(f)).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(f.name, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
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

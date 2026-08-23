package com.cripta.app.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.ViewMode
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
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
    onSettings: () -> Unit,
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

    var selection by remember { mutableStateOf(setOf<String>()) }
    var showNewFolder by remember { mutableStateOf(false) }
    var tagTargetId by remember { mutableStateOf<String?>(null) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }

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
                title = { Text(if (inSelection) "${selection.size} selezionati" else (path.lastOrNull()?.name ?: "Cripta")) },
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
                        if (selection.size == 1) {
                            IconButton(onClick = { renameTargetId = selection.first() }) {
                                Icon(Icons.Filled.DriveFileRenameOutline, "Rinomina")
                            }
                            IconButton(onClick = { tagTargetId = selection.first() }) {
                                Icon(Icons.Filled.Label, "Etichette")
                            }
                        }
                        IconButton(onClick = { vm.deleteFiles(selection.toList()); selection = emptySet() }) {
                            Icon(Icons.Filled.Delete, "Elimina")
                        }
                    } else {
                        if (viewMode == ViewMode.GRID) {
                            IconButton(onClick = { vm.setGridColumns(if (gridColumns >= 5) 2 else gridColumns + 1) }) {
                                Icon(Icons.Filled.ViewModule, "Dimensione griglia")
                            }
                        }
                        IconButton(onClick = { vm.setViewMode(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }) {
                            Icon(if (viewMode == ViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView, "Vista")
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Impostazioni") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!inSelection) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, "Nuova cartella")
                    }
                    SmallFloatingActionButton(onClick = { vm.randomPick()?.let(onOpenFile) }) {
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
            FilterBar(filters, tags, vm::setType, { vm.setFavoritesOnly(!filters.favoritesOnly) }, vm::toggleTag)

            val columns = if (viewMode == ViewMode.GRID) GridCells.Fixed(gridColumns) else GridCells.Fixed(1)
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
                        FolderCell(folder, viewMode,
                            modifier = Modifier.animateItem(),
                            onOpen = { vm.enterFolder(folder) }, onLongPress = { folderToDelete = folder })
                    }
                }
                groupByDay(files).forEach { (label, group) ->
                    header(label)
                    items(group, key = { it.file.id }, span = { GridItemSpan(1) }) { fwt ->
                        FileCell(
                            item = fwt,
                            viewMode = viewMode,
                            selected = fwt.file.id in selection,
                            selectionMode = inSelection,
                            modifier = Modifier.animateItem(),
                            thumb = { vm.thumb(fwt.file) },
                            onOpen = { onOpenFile(fwt.file.id) },
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
            onDismiss = { tagTargetId = null },
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCell(folder: FolderEntity, viewMode: ViewMode, modifier: Modifier, onOpen: () -> Unit, onLongPress: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .then(if (viewMode == ViewMode.GRID) Modifier.aspectRatio(1f) else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
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
    val fallbackIcon = when {
        VaultRepository.isImage(mime) -> Icons.Filled.Image
        VaultRepository.isVideo(mime) -> Icons.Filled.Movie
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
                ThumbBox(bmp, fallbackIcon, item.file.originalName, selected, item.file.isFavorite, item.tags, Modifier.size(56.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
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
        ThumbBox(bmp, fallbackIcon, item.file.originalName, selected, item.file.isFavorite, item.tags,
            Modifier.fillMaxWidth().aspectRatio(1f))
        Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThumbBox(
    bmp: android.graphics.Bitmap?,
    fallbackIcon: ImageVector,
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
        if (favorite) {
            Icon(Icons.Filled.Star, "Preferito", tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp))
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, "Selezionato", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp))
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
private fun FilterBar(
    filters: Filters,
    tags: List<TagEntity>,
    onType: (TypeFilter) -> Unit,
    onFav: () -> Unit,
    onTag: (Long) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypeFilter.entries.forEach { t ->
            FilterChip(selected = filters.type == t, onClick = { onType(t) }, label = { Text(typeLabel(t)) })
        }
        FilterChip(selected = filters.favoritesOnly, onClick = onFav, label = { Text("Preferiti") })
        tags.forEach { tag ->
            FilterChip(selected = tag.id in filters.tagIds, onClick = { onTag(tag.id) }, label = { Text("#${tag.name}") })
        }
    }
}

package com.cripta.app.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity

@OptIn(ExperimentalMaterial3Api::class)
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

    var selection by remember { mutableStateOf(setOf<String>()) }
    var showNewFolder by remember { mutableStateOf(false) }
    var tagTarget by remember { mutableStateOf<FileWithTags?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importUris(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path.lastOrNull()?.name ?: "Cripta") },
                navigationIcon = {
                    if (path.isNotEmpty()) {
                        IconButton(onClick = { vm.goUp() }) {
                            Icon(Icons.Filled.ArrowBack, "Su")
                        }
                    }
                },
                actions = {
                    if (selection.isNotEmpty()) {
                        IconButton(onClick = { vm.deleteFiles(selection.toList()); selection = emptySet() }) {
                            Icon(Icons.Filled.Delete, "Elimina")
                        }
                    } else {
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Impostazioni") }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = { showNewFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, "Nuova cartella")
                }
                FloatingActionButton(onClick = {
                    vm.randomPick()?.let(onOpenFile)
                }) { Icon(Icons.Filled.Casino, "Casuale") }
                FloatingActionButton(onClick = {
                    importLauncher.launch(arrayOf("*/*"))
                }) { Icon(Icons.Filled.Add, "Importa") }
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
            FilterBar(
                filters = filters,
                tags = tags,
                onType = vm::setType,
                onFav = { vm.setFavoritesOnly(!filters.favoritesOnly) },
                onTag = vm::toggleTag,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(folders, key = { "folder-${it.id}" }) { folder ->
                    FolderRow(folder, onOpen = { vm.enterFolder(folder) }, onDelete = { vm.deleteFolder(folder) })
                }
                items(files, key = { it.file.id }) { fwt ->
                    FileRow(
                        item = fwt,
                        selected = fwt.file.id in selection,
                        selectionMode = selection.isNotEmpty(),
                        onOpen = { onOpenFile(fwt.file.id) },
                        onToggleSelect = {
                            selection = if (fwt.file.id in selection) selection - fwt.file.id
                            else selection + fwt.file.id
                        },
                        onFav = { vm.toggleFavorite(fwt.file.id, !fwt.file.isFavorite) },
                        onTags = { tagTarget = fwt },
                    )
                }
            }
        }
    }

    if (showNewFolder) {
        TextPromptDialog(
            title = "Nuova cartella",
            label = "Nome",
            onConfirm = { vm.createFolder(it); showNewFolder = false },
            onDismiss = { showNewFolder = false },
        )
    }

    tagTarget?.let { target ->
        TextPromptDialog(
            title = "Tag (separati da virgola)",
            label = "es. viaggio, 2024",
            initial = target.tags.joinToString(", ") { it.name },
            onConfirm = {
                vm.setTags(target.file.id, it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })
                tagTarget = null
            },
            onDismiss = { tagTarget = null },
        )
    }
}

@Composable
private fun FilterBar(
    filters: Filters,
    tags: List<com.cripta.app.data.db.TagEntity>,
    onType: (TypeFilter) -> Unit,
    onFav: () -> Unit,
    onTag: (Long) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypeFilter.entries.forEach { t ->
            FilterChip(selected = filters.type == t, onClick = { onType(t) }, label = { Text(t.name.lowercase()) })
        }
        FilterChip(selected = filters.favoritesOnly, onClick = onFav, label = { Text("preferiti") })
        tags.forEach { tag ->
            FilterChip(
                selected = tag.id in filters.tagIds,
                onClick = { onTag(tag.id) },
                label = { Text("#${tag.name}") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: FolderEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.name) },
        leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Elimina cartella") }
        },
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onDelete),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileWithTags,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onFav: () -> Unit,
    onTags: () -> Unit,
) {
    val mime = item.file.mimeType
    val icon: ImageVector = when {
        VaultRepository.isImage(mime) -> Icons.Filled.Image
        VaultRepository.isVideo(mime) -> Icons.Filled.Movie
        else -> Icons.Filled.InsertDriveFile
    }
    ListItem(
        headlineContent = { Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (item.tags.isNotEmpty()) {
                Text(item.tags.joinToString(" ") { "#${it.name}" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Icon(
                if (selected) Icons.Filled.Star else icon,
                null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onTags) { Icon(Icons.Filled.Label, "Tag") }
                IconButton(onClick = onFav) {
                    Icon(if (item.file.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Preferito")
                }
            }
        },
        modifier = Modifier.combinedClickable(
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            onLongClick = onToggleSelect,
        ),
    )
}

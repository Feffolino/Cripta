package com.cripta.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cripta.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.FolderStat
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.ui.components.MediaThumb
import com.cripta.app.ui.components.MediaThumbCell
import com.cripta.app.ui.components.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFile: (String) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenFavorites: () -> Unit = {},
    onLock: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val recents by vm.recents.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val folders by vm.folders.collectAsState()
    val folderStats by vm.folderStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { com.cripta.app.ui.components.HeaderTitle("Cripta") },
                actions = { IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, "Blocca") } },
            )
        },
    ) { pad ->
        if (recents.isEmpty() && folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        }
                    }
                    Text("Vault vuoto", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
                    Text(
                        "Importa foto e video: restano cifrati e visibili solo qui.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    androidx.compose.material3.Button(
                        onClick = onOpenFolders,
                        modifier = Modifier.padding(top = 20.dp),
                    ) { Text("Vai a Cartelle") }
                }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (recents.isNotEmpty()) {
                item { ShelfHeader("Recenti") }
                // Featured hero: the most recent item as a large 16:9 card, breaking the uniform row.
                item {
                    HeroCard(recents.first(), vm, onClick = { openWith(vm, recents, recents.first().id, onOpenFile) })
                }
                if (recents.size > 1) {
                    item { MediaShelf(recents.drop(1), onOpen = { openWith(vm, recents, it, onOpenFile) }, vm) }
                }
            }
            if (favorites.isNotEmpty()) {
                item { ShelfHeader("Preferiti", onClick = onOpenFavorites) }
                item { MediaShelf(favorites, onOpen = { openWith(vm, favorites, it, onOpenFile) }, vm) }
            }
            if (folders.isNotEmpty()) {
                item { ShelfHeader("Cartelle") }
                item { FolderShelf(folders, folderStats, onOpenFolders) }
            }
        }
    }
}

private fun openWith(vm: HomeViewModel, list: List<FileEntity>, id: String, onOpenFile: (String) -> Unit) {
    vm.publishQueue(list.map { it.id })
    onOpenFile(id)
}

@Composable
private fun ShelfHeader(title: String, onClick: (() -> Unit)? = null) {
    if (onClick == null) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
    } else {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("Vedi tutti", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null, tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HeroCard(file: FileEntity, vm: HomeViewModel, onClick: () -> Unit) {
    val coverVersions by vm.coverVersions.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)) {
        MediaThumb(
            file, vm::thumb,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            coverVersion = coverVersions[file.id] ?: 0,
        )
        Text(file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(fileMeta(file), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MediaShelf(items: List<FileEntity>, onOpen: (String) -> Unit, vm: HomeViewModel) {
    val coverVersions by vm.coverVersions.collectAsState()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { file ->
            MediaThumbCell(file, vm::thumb, onClick = { onOpen(file.id) }, modifier = Modifier.width(120.dp),
                coverVersion = coverVersions[file.id] ?: 0)
        }
    }
}

@Composable
private fun FolderShelf(
    folders: List<FolderEntity>,
    stats: Map<Long, FolderStat>,
    onOpenFolders: () -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(folders, key = { it.id }) { folder ->
            val stat = stats[folder.id]
            val subtitle = if (stat == null || stat.count == 0) "Vuota"
                else "${stat.count} · ${formatBytes(stat.bytes)}"
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.width(120.dp),
            ) {
                Column(Modifier.clickable(onClick = onOpenFolders).padding(12.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    com.cripta.app.ui.components.FolderGlyph(folder.color, folder.emoji, 36.dp)
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

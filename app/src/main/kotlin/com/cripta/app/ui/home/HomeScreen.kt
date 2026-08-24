package com.cripta.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.ui.components.MediaThumb
import com.cripta.app.ui.components.MediaThumbCell

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFile: (String) -> Unit,
    onOpenFolders: () -> Unit,
    onLock: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val recents by vm.recents.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val folders by vm.folders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cripta") },
                actions = { IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, "Blocca") } },
            )
        },
    ) { pad ->
        if (recents.isEmpty() && folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    Text("Vault vuoto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text("Vai su Cartelle e tocca + per importare.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recents.isNotEmpty()) {
                item { ShelfHeader("Recenti") }
                item { MediaShelf(recents, onOpen = { openWith(vm, recents, it, onOpenFile) }, vm) }
            }
            if (favorites.isNotEmpty()) {
                item { ShelfHeader("Preferiti") }
                item { MediaShelf(favorites, onOpen = { openWith(vm, favorites, it, onOpenFile) }, vm) }
            }
            if (folders.isNotEmpty()) {
                item { ShelfHeader("Cartelle") }
                item { FolderShelf(folders, onOpenFolders) }
            }
        }
    }
}

private fun openWith(vm: HomeViewModel, list: List<FileEntity>, id: String, onOpenFile: (String) -> Unit) {
    vm.publishQueue(list.map { it.id })
    onOpenFile(id)
}

@Composable
private fun ShelfHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun MediaShelf(items: List<FileEntity>, onOpen: (String) -> Unit, vm: HomeViewModel) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { file ->
            MediaThumbCell(file, vm::thumb, onClick = { onOpen(file.id) }, modifier = Modifier.width(120.dp))
        }
    }
}

@Composable
private fun FolderShelf(folders: List<FolderEntity>, onOpenFolders: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(folders, key = { it.id }) { folder ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.width(120.dp),
            ) {
                Column(Modifier.clickable(onClick = onOpenFolders).padding(12.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

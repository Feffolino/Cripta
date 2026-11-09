package com.cripta.app.ui.favorites

import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str

import com.cripta.app.ui.theme.CriptaIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.ui.components.MediaThumbCell
import com.cripta.app.data.SortKey
import com.cripta.app.ui.vault.FolderMosaic
import com.cripta.app.ui.theme.Motion
import kotlinx.coroutines.launch

/** What the favorites page shows; the crossfade is keyed on it so the grid doesn't re-animate on
 *  every list change. */
private enum class FavPhase { Loading, Empty, Content }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onOpenFile: (String) -> Unit,
    onOpenFolders: () -> Unit = {},
    onBack: () -> Unit = {},
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val favoritesOrNull by vm.favorites.collectAsStateWithLifecycle()
    val coverVersions by vm.coverVersions.collectAsStateWithLifecycle()
    val foldersOrNull by vm.folders.collectAsStateWithLifecycle()
    val folderPreviews by vm.folderPreviews.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val favGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    Scaffold(
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                colors = com.cripta.app.ui.components.criptaTopBarColors(favGrid.canScrollBackward),
                title = { com.cripta.app.ui.components.HeaderTitle(str(R.string.favorites)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CriptaIcons.ArrowBack, str(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(CriptaIcons.Sort, str(R.string.favorites_sort)) }
                        DropdownMenu(expanded = sortMenu && com.cripta.app.ui.components.vaultUnlocked(), onDismissRequest = { sortMenu = false }) {
                            listOf(SortKey.DATE to str(R.string.sort_date), SortKey.NAME to str(R.string.sort_name), SortKey.SIZE to str(R.string.sort_size), SortKey.MANUAL to str(R.string.sort_manual)).forEach { (key, label) ->
                                DropdownMenuItem(text = { Text((if (sort.first == key) "✓ " else "") + label) },
                                    onClick = { vm.setSort(key, sort.second); sortMenu = false })
                            }
                            if (sort.first != SortKey.MANUAL) DropdownMenuItem(
                                text = { Text(if (sort.second) str(R.string.sort_descending) else str(R.string.sort_ascending)) },
                                onClick = { vm.setSort(sort.first, !sort.second); sortMenu = false },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val phase = when {
            favoritesOrNull == null || foldersOrNull == null -> FavPhase.Loading
            favoritesOrNull.isNullOrEmpty() && foldersOrNull.isNullOrEmpty() -> FavPhase.Empty
            else -> FavPhase.Content
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit(Motion.MEDIUM)) },
            modifier = Modifier.fillMaxSize().padding(pad),
            label = "favoritesPhase",
        ) { p ->
            when (p) {
                FavPhase.Loading -> FavoritesSkeleton(Modifier.fillMaxSize())
                FavPhase.Empty -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(CriptaIcons.StarBorder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                        Text(str(R.string.favorites_empty), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        Text(str(R.string.favorites_empty_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FavPhase.Content -> {
                    val favorites = favoritesOrNull.orEmpty()
                    LazyVerticalGrid(
                        state = favGrid,
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(foldersOrNull.orEmpty(), key = { "folder-${it.id}" }) { folder ->
                            FolderMosaic(
                                folder = folder, stat = null,
                                previews = folderPreviews[folder.id].orEmpty(), thumb = vm::thumb,
                                showInfo = false,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    vm.openFolder(folder.id); onOpenFolders()
                                },
                                onRemoveFavorite = {
                                    vm.setFolderFavorite(folder.id, false)
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        if (snackbar.showSnackbar(str(R.string.folder_unfavorited), str(R.string.undo),
                                                duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                            vm.setFolderFavorite(folder.id, true)
                                        }
                                    }
                                },
                            )
                        }
                        items(favorites, key = { it.id }) { file ->
                            Box(
                                Modifier.animateItem(
                                    fadeInSpec = Motion.enter(Motion.MEDIUM),
                                    placementSpec = Motion.enter(Motion.LONG),
                                    fadeOutSpec = Motion.exit(Motion.MEDIUM),
                                ),
                            ) {
                                MediaThumbCell(file, vm::thumb, onClick = {
                                    vm.publishQueue(favorites.map { it.id }); onOpenFile(file.id)
                                }, coverVersion = coverVersions[file.id] ?: 0,
                                    // The remove button below is the star here: no second one on the cover.
                                    showFavoriteBadge = false)
                                // Unfavorite in place (with undo), instead of opening each file.
                                Surface(
                                    color = com.cripta.app.ui.components.OnImageGlass, shape = CircleShape,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            vm.setFavorite(file.id, false)
                                            scope.launch {
                                                snackbar.currentSnackbarData?.dismiss()
                                                val r = snackbar.showSnackbar(
                                                    str(R.string.unfavorited), actionLabel = str(R.string.undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (r == SnackbarResult.ActionPerformed) vm.setFavorite(file.id, true)
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize().size(40.dp),
                                    ) {
                                        Icon(CriptaIcons.Star, str(R.string.unfavorite),
                                            tint = com.cripta.app.ui.theme.Favorite, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The favourites grid while it is read: the same square covers, name lines and gaps as the real one. */
@Composable
private fun FavoritesSkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp), modifier = modifier, userScrollEnabled = false,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(18) {
            androidx.compose.foundation.layout.Column(Modifier.padding(bottom = 6.dp)) {
                com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), com.cripta.app.ui.theme.CoverShape)
                Box(Modifier.padding(top = 8.dp, start = 2.dp).height(20.dp), contentAlignment = Alignment.CenterStart) {
                    com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth(0.7f).height(12.dp))
                }
            }
        }
    }
}

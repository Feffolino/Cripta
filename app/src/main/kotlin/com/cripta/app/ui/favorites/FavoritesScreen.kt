package com.cripta.app.ui.favorites

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.ui.components.MediaThumbCell
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.viewer.DelayedSpinner
import kotlinx.coroutines.launch

/** What the favorites page shows; the crossfade is keyed on it so the grid doesn't re-animate on
 *  every list change. */
private enum class FavPhase { Loading, Empty, Content }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit = {},
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val favoritesOrNull by vm.favorites.collectAsState()
    val coverVersions by vm.coverVersions.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { com.cripta.app.ui.components.HeaderTitle("Preferiti") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val phase = when {
            favoritesOrNull == null -> FavPhase.Loading
            favoritesOrNull.isNullOrEmpty() -> FavPhase.Empty
            else -> FavPhase.Content
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit(Motion.MEDIUM)) },
            modifier = Modifier.fillMaxSize().padding(pad),
            label = "favoritesPhase",
        ) { p ->
            when (p) {
                FavPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DelayedSpinner()
                }
                FavPhase.Empty -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.StarBorder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                        Text("Nessun preferito", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        Text("Segna i file con la stella per trovarli qui.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FavPhase.Content -> {
                    val favorites = favoritesOrNull.orEmpty()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
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
                                    color = Color.Black.copy(alpha = 0.45f), shape = CircleShape,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            vm.setFavorite(file.id, false)
                                            scope.launch {
                                                snackbar.currentSnackbarData?.dismiss()
                                                val r = snackbar.showSnackbar(
                                                    "Rimosso dai preferiti", actionLabel = "Annulla",
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (r == SnackbarResult.ActionPerformed) vm.setFavorite(file.id, true)
                                            }
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.Filled.Star, "Rimuovi dai preferiti",
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

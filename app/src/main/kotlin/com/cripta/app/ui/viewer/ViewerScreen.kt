package com.cripta.app.ui.viewer

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.cripta.app.data.db.FileEntity
import com.cripta.app.ui.vault.TagEditorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    fileId: String,
    onBack: () -> Unit,
    vm: ViewerViewModel = hiltViewModel(),
) {
    LaunchedEffect(fileId) { vm.load(fileId) }
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(message) {
        message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearMessage() }
    }

    val file: FileEntity? = when (val s = state) {
        is ViewerState.Photo -> s.file
        is ViewerState.Video -> s.file
        is ViewerState.Other -> s.file
        else -> null
    }

    var showTags by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file?.originalName ?: "Visualizza", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (file != null) {
                        IconButton(onClick = { vm.toggleFavorite(file) }) {
                            Icon(
                                if (file.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                if (file.isFavorite) "Rimuovi preferito" else "Aggiungi preferito",
                            )
                        }
                        IconButton(onClick = { showTags = true }) { Icon(Icons.Filled.Label, "Etichette") }
                        IconButton(onClick = { confirmDownload = true }) { Icon(Icons.Filled.Download, "Scarica") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is ViewerState.Loading -> CircularProgressIndicator()
                is ViewerState.Error -> Text(s.message)
                is ViewerState.Photo -> ZoomableImage(s)
                is ViewerState.Video -> VideoPlayer(s.file, vm)
                is ViewerState.Other -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nessun viewer interno per questo tipo.")
                    Text("Usa Scarica per aprirlo con un'altra app.")
                }
            }
        }
    }

    if (showTags && file != null) {
        val initial by produceState(initialValue = emptyList<String>(), file.id) { value = vm.tagNamesOf(file.id) }
        TagEditorDialog(
            allTags = allTags,
            initialSelected = initial,
            onConfirm = { vm.setTags(file.id, it); showTags = false },
            onSetAlias = { name, alias -> vm.setTagAlias(name, alias) },
            onDismiss = { showTags = false },
        )
    }

    if (confirmDelete && file != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il file?") },
            text = { Text("\"${file.originalName}\" verrà eliminato in modo sicuro (crypto-shredding). Irreversibile.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(file.id) { onBack() } }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }

    if (confirmDownload && file != null) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Scaricare in galleria?") },
            text = { Text("Una copia in chiaro di \"${file.originalName}\" verrà salvata sul dispositivo (galleria/Download).") },
            confirmButton = { TextButton(onClick = { confirmDownload = false; vm.download(file) }) { Text("Scarica") } },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun ZoomableImage(s: ViewerState.Photo) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data(s.bytes)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build(),
        contentDescription = s.file.originalName,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(file: FileEntity, vm: ViewerViewModel) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            val factory = com.cripta.app.viewer.EncryptedDataSource.Factory(
                channelProvider = { vm.channelFor(file) },
                plaintextLength = file.sizeBytes,
            )
            val source = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri("cripta://${file.id}"))
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }
    val modes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
    )
    var modeIdx by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; resizeMode = modes[modeIdx] } },
            update = { it.resizeMode = modes[modeIdx] },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = { modeIdx = (modeIdx + 1) % modes.size },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.AspectRatio, "Adatta/riempi") }
    }
}

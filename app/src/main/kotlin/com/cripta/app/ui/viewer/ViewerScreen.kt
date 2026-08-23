package com.cripta.app.ui.viewer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.cripta.app.data.db.FileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    fileId: String,
    onBack: () -> Unit,
    vm: ViewerViewModel = hiltViewModel(),
) {
    LaunchedEffect(fileId) { vm.load(fileId) }
    val state by vm.state.collectAsState()

    val file: FileEntity? = when (val s = state) {
        is ViewerState.Photo -> s.file
        is ViewerState.Video -> s.file
        is ViewerState.Other -> s.file
        else -> null
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(file?.mimeType ?: "application/octet-stream")
    ) { uri -> if (uri != null && file != null) vm.export(file, uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file?.originalName ?: "Visualizza", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (file != null) {
                        IconButton(onClick = { exportLauncher.launch(file.originalName) }) {
                            Icon(Icons.Filled.Download, "Esporta")
                        }
                        IconButton(onClick = { vm.delete(file.id) { onBack() } }) {
                            Icon(Icons.Filled.Delete, "Elimina")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is ViewerState.Loading -> CircularProgressIndicator()
                is ViewerState.Error -> Text(s.message)
                is ViewerState.Photo -> {
                    val ctx = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(s.bytes)
                            .diskCachePolicy(CachePolicy.DISABLED)   // never persist plaintext
                            .build(),
                        contentDescription = s.file.originalName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is ViewerState.Video -> VideoPlayer(s.file, vm)
                is ViewerState.Other -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nessun viewer interno per questo tipo.")
                    Text("Usa Esporta per aprirlo con un'altra app.")
                }
            }
        }
    }
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
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}

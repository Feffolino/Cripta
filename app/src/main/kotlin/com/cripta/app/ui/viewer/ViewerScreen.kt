package com.cripta.app.ui.viewer

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    fileId: String,
    onBack: () -> Unit,
    onEditNote: (String) -> Unit = {},
    vm: ViewerViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val message by vm.message.collectAsState()
    val refresh by vm.refresh.collectAsState()
    val allTags by vm.allTags.collectAsState()

    LaunchedEffect(message) {
        message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearMessage() }
    }

    val ids = remember { vm.ids.ifEmpty { listOf(fileId) } }
    val startIndex = remember { ids.indexOf(fileId).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { ids.size }

    var chromeVisible by remember { mutableStateOf(true) }
    // Auto-hide the chrome a few seconds after it appears or the page changes.
    LaunchedEffect(chromeVisible, pagerState.currentPage) {
        if (chromeVisible) { delay(3500); chromeVisible = false }
    }

    val currentId = ids.getOrElse(pagerState.currentPage) { fileId }
    val currentFile by produceState<FileEntity?>(initialValue = null, currentId, refresh) {
        value = vm.fileById(currentId)
    }

    var showTags by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            MediaPage(
                id = ids[page],
                refreshKey = refresh,
                isCurrent = page == pagerState.currentPage,
                vm = vm,
                onToggleChrome = { chromeVisible = !chromeVisible },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = { Text(currentFile?.originalName ?: "", maxLines = 1, softWrap = false) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Indietro") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    currentFile?.let { file ->
                        if (com.cripta.app.data.VaultRepository.isNote(file.mimeType)) {
                            IconButton(onClick = { onEditNote(file.id) }) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Edit, "Modifica")
                            }
                        }
                        IconButton(onClick = { vm.toggleFavorite(file) }) {
                            Icon(if (file.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Preferito")
                        }
                        IconButton(onClick = { showTags = true }) { Icon(Icons.Filled.Label, "Etichette") }
                        IconButton(onClick = { confirmDownload = true }) { Icon(Icons.Filled.Download, "Scarica") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") }
                    }
                },
            )
        }
    }

    val file = currentFile
    if (showTags && file != null) {
        val initial by produceState(initialValue = emptyList<String>(), file.id, refresh) { value = vm.tagNamesOf(file.id) }
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
            text = { Text("\"${file.originalName}\" verrà eliminato in modo sicuro. Irreversibile.") },
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
            text = { Text("Una copia in chiaro di \"${file.originalName}\" verrà salvata sul dispositivo.") },
            confirmButton = { TextButton(onClick = { confirmDownload = false; vm.download(file) }) { Text("Scarica") } },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun MediaPage(
    id: String,
    refreshKey: Int,
    isCurrent: Boolean,
    vm: ViewerViewModel,
    onToggleChrome: () -> Unit,
) {
    val state by produceState<ViewerState>(initialValue = ViewerState.Loading, id, refreshKey) {
        value = vm.stateFor(id)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is ViewerState.Loading -> CircularProgressIndicator(color = Color.White)
            is ViewerState.Error -> Text(s.message, color = Color.White)
            is ViewerState.Photo -> ZoomableImage(s.bytes, s.file.originalName, onToggleChrome)
            is ViewerState.Video -> if (isCurrent) VideoPlayer(s.file, vm, onToggleChrome) else CircularProgressIndicator(color = Color.White)
            is ViewerState.Note -> NoteView(s.text, onToggleChrome)
            is ViewerState.Pdf -> PdfView(s.bytes)
            is ViewerState.Other -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nessun viewer interno per questo tipo.", color = Color.White)
                Text("Usa Scarica per aprirlo con un'altra app.", color = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(bytes: ByteArray, name: String, onSingleTap: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }

    AsyncImage(
        model = ImageRequest.Builder(ctx).data(bytes).diskCachePolicy(CachePolicy.DISABLED).build(),
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale.value * zoom).coerceIn(1f, 6f)
                    scope.launch { scale.snapTo(newScale) }
                    if (newScale > 1f) {
                        scope.launch { offX.snapTo(offX.value + pan.x) }
                        scope.launch { offY.snapTo(offY.value + pan.y) }
                    } else {
                        scope.launch { offX.snapTo(0f) }
                        scope.launch { offY.snapTo(0f) }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > 1f) {
                                launch { scale.animateTo(1f) }
                                launch { offX.animateTo(0f) }
                                launch { offY.animateTo(0f) }
                            } else {
                                val target = 2.5f
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                launch { scale.animateTo(target) }
                                launch { offX.animateTo((cx - tap.x) * (target - 1f)) }
                                launch { offY.animateTo((cy - tap.y) * (target - 1f)) }
                            }
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                translationX = offX.value; translationY = offY.value
            },
    )
}

@Composable
private fun NoteView(text: String, onSingleTap: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .verticalScroll(rememberScrollState())
            .pointerInput(Unit) { detectTapGestures(onTap = { onSingleTap() }) }
            .padding(20.dp),
    ) {
        Text(text.ifBlank { "(nota vuota)" }, color = Color.White)
    }
}

@Composable
private fun PdfView(bytes: ByteArray) {
    val pages by produceState<List<Bitmap>?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
                    val renderer = PDFRenderer(doc)
                    (0 until doc.numberOfPages).map { renderer.renderImageWithDPI(it, 150f) }
                }
            }.getOrDefault(emptyList())
        }
    }
    val p = pages
    when {
        p == null -> CircularProgressIndicator(color = Color.White)
        p.isEmpty() -> Text("Impossibile aprire il PDF", color = Color.White)
        else -> LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0A0C10))) {
            items(p) { bmp ->
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(file: FileEntity, vm: ViewerViewModel, onSingleTap: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(file.id) {
        ExoPlayer.Builder(ctx).build().apply {
            val factory = com.cripta.app.viewer.EncryptedDataSource.Factory(
                channelProvider = { vm.channelFor(file) },
                plaintextLength = file.sizeBytes,
            )
            setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri("cripta://${file.id}")))
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
    DisposableEffect(file.id) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onSingleTap() }) }) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    resizeMode = modes[modeIdx]
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update = { it.resizeMode = modes[modeIdx] },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = { modeIdx = (modeIdx + 1) % modes.size },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.AspectRatio, "Adatta/riempi", tint = Color.White) }
    }
}

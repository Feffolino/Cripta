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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Immersive: let media use the status- and navigation-bar areas; restore bars on exit.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, view) }
        controller?.let {
            it.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }

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
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            MediaPage(
                id = ids[page],
                refreshKey = refresh,
                isCurrent = page == pagerState.currentPage,
                chromeVisible = chromeVisible,
                vm = vm,
                setChrome = { chromeVisible = it },
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
                                Icon(Icons.Filled.Edit, "Modifica")
                            }
                        }
                        IconButton(onClick = { vm.toggleFavorite(file) }) {
                            Icon(if (file.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Preferito")
                        }
                        IconButton(onClick = { showTags = true }) { Icon(Icons.Filled.Label, "Etichette") }
                        IconButton(onClick = { showInfo = true }) { Icon(Icons.Filled.Info, "Informazioni") }
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
            onCreateTag = { name, alias -> vm.createTag(name, alias) },
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
    if (showInfo && file != null) {
        val infoTags by produceState(initialValue = emptyList<String>(), file.id, refresh) { value = vm.tagNamesOf(file.id) }
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Informazioni") },
            text = {
                Column {
                    InfoLine("Nome", file.originalName)
                    InfoLine("Tipo", file.mimeType)
                    InfoLine("Dimensione", com.cripta.app.ui.components.formatBytes(file.sizeBytes))
                    com.cripta.app.ui.components.formatDuration(file.durationMs)?.let { InfoLine("Durata", it) }
                    InfoLine(
                        "Aggiunto",
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                            .format(java.util.Date(file.createdAt)),
                    )
                    if (infoTags.isNotEmpty()) InfoLine("Tag", infoTags.joinToString(", "))
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Chiudi") } },
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
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MediaPage(
    id: String,
    refreshKey: Int,
    isCurrent: Boolean,
    chromeVisible: Boolean,
    vm: ViewerViewModel,
    setChrome: (Boolean) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val state by produceState<ViewerState>(initialValue = ViewerState.Loading, id, refreshKey) {
        value = vm.stateFor(id)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is ViewerState.Loading -> CircularProgressIndicator(color = Color.White)
            is ViewerState.Error -> Text(s.message, color = Color.White)
            is ViewerState.Photo -> ZoomableImage(s.bytes, s.file.originalName, onSingleTap = onToggleChrome)
            is ViewerState.Video -> if (isCurrent) VideoPlayer(s.file, vm, controlsVisible = chromeVisible, onControlsVisibilityChanged = setChrome) else CircularProgressIndicator(color = Color.White)
            is ViewerState.Note -> NoteView(s.text, onSingleTap = onToggleChrome)
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
                // Pinch to zoom / drag to pan, but only CONSUME the gesture while zoomed or
                // actively pinching. At 1x a single-finger horizontal drag is left unconsumed
                // so the HorizontalPager can swipe between media (fixes swipe stuck on a page).
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val newScale = (scale.value * zoom).coerceIn(1f, 6f)
                        if (newScale != scale.value) scope.launch { scale.snapTo(newScale) }
                        if (newScale > 1f) {
                            scope.launch { offX.snapTo(offX.value + pan.x) }
                            scope.launch { offY.snapTo(offY.value + pan.y) }
                        } else if (offX.value != 0f || offY.value != 0f) {
                            scope.launch { offX.snapTo(0f) }
                            scope.launch { offY.snapTo(0f) }
                        }
                        if (newScale > 1f || zoom != 1f) {
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
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

/** Keeps a PDF open and renders pages on demand (one visible page at a time), so a large
 *  document doesn't rasterize every page into RAM up front. Render calls are serialized. */
private class PdfDoc(bytes: ByteArray) {
    private val doc = PDDocument.load(ByteArrayInputStream(bytes))
    private val renderer = PDFRenderer(doc)
    private val mutex = Mutex()
    val pageCount: Int = doc.numberOfPages
    suspend fun render(index: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { renderer.renderImageWithDPI(index, 150f) }.getOrNull() }
    }
    fun close() { runCatching { doc.close() } }
}

@Composable
private fun PdfView(bytes: ByteArray) {
    val result by produceState<Result<PdfDoc>?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDoc(bytes) } }
    }
    val doc = result?.getOrNull()
    DisposableEffect(doc) { onDispose { doc?.close() } }
    when {
        result == null -> CircularProgressIndicator(color = Color.White)
        doc == null -> Text("Impossibile aprire il PDF", color = Color.White)
        else -> LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0A0C10))) {
            items(doc.pageCount) { index ->
                val bmp by produceState<Bitmap?>(initialValue = null, index, doc) { value = doc.render(index) }
                val b = bmp
                if (b != null) {
                    Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                } else {
                    Box(Modifier.fillMaxWidth().height(480.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    file: FileEntity,
    vm: ViewerViewModel,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    var buffering by remember(file.id) { mutableStateOf(true) }
    val player = remember(file.id) {
        ExoPlayer.Builder(ctx).build().apply {
            // Route audio properly and cooperate with other apps (pause on focus loss).
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            val factory = com.cripta.app.viewer.EncryptedDataSource.Factory(
                channelProvider = { vm.channelFor(file) },
                plaintextLength = file.sizeBytes,
            )
            setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri("cripta://${file.id}")))
            repeatMode = Player.REPEAT_MODE_ONE   // loop the video
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
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var seekLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekLabel) { if (seekLabel != null) { delay(650); seekLabel = null } }

    DisposableEffect(file.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration
        val max = if (dur > 0) dur else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
    }
    fun toggleController() {
        playerViewRef?.let { if (it.isControllerFullyVisible) it.hideController() else it.showController() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    resizeMode = modes[modeIdx]
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true            // don't let the screen dim during playback
                    controllerShowTimeoutMs = 2500
                    // Mirror the ExoPlayer controller's visibility onto the app chrome
                    // (top bar with the name + the aspect toggle) so a tap reveals both.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { vis -> onControlsVisibilityChanged(vis == View.VISIBLE) }
                    )
                    playerViewRef = this
                }
            },
            update = { it.resizeMode = modes[modeIdx] },
            modifier = Modifier.fillMaxSize(),
        )

        // Left / right edge zones: double-tap to jump 10s; single tap toggles the controls.
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.3f).fillMaxHeight(0.7f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { seekBy(-10_000); seekLabel = "-10s" },
                        onTap = { toggleController() },
                    )
                }
        )
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.3f).fillMaxHeight(0.7f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { seekBy(10_000); seekLabel = "+10s" },
                        onTap = { toggleController() },
                    )
                }
        )

        if (buffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        seekLabel?.let { lbl ->
            val side = if (lbl.startsWith("+")) Alignment.CenterEnd else Alignment.CenterStart
            Surface(
                color = Color.Black.copy(alpha = 0.5f), shape = CircleShape,
                modifier = Modifier.align(side).padding(horizontal = 44.dp),
            ) {
                Text(lbl, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }

        // Aspect toggle on the right edge (drawn above the seek zone), only while controls show.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        ) {
            Surface(color = Color.Black.copy(alpha = 0.45f), shape = CircleShape) {
                IconButton(onClick = { modeIdx = (modeIdx + 1) % modes.size }) {
                    Icon(Icons.Filled.AspectRatio, "Adatta/riempi", tint = Color.White)
                }
            }
        }
    }
}

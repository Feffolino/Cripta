package com.cripta.app.ui.viewer

import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
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

/** Live top+horizontal inset that keeps the player chrome clear of the camera cutout. Uses Compose's
 *  WindowInsets so it updates on rotation (a one-shot read went stale and left the bar floating in one
 *  orientation and clipped in the other). Applied as the TopAppBar's own windowInsets so the bar's
 *  background still reaches the top edge while its title/icons sit below the camera. */
private val chromeInsets: WindowInsets
    @Composable get() = WindowInsets.displayCutout.union(WindowInsets.statusBars)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

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
    val displayPrefs by vm.display.collectAsState()

    // Immersive: let media use the status- and navigation-bar areas; restore bars on exit.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, view) }
        controller?.let {
            it.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Undo player-only changes: gesture brightness and auto-rotation/lock.
            (view.context as? android.app.Activity)?.let { act ->
                act.window.attributes = act.window.attributes.apply {
                    screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            vm.rotationLocked.value = false
        }
    }

    LaunchedEffect(message) {
        message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearMessage() }
    }

    // Live list (a delete removes the page and moves on); kept in the VM so it survives a trip to
    // the note editor.
    val fallbackIds = remember { listOf(fileId) }
    val ids: List<String> = vm.liveIds.ifEmpty { fallbackIds }
    val startIndex = remember { ids.indexOf(fileId).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { ids.size }
    val playback by vm.playback.collectAsState()

    // TalkBack: never auto-hide the chrome/controls while touch exploration is on (the user could
    // not reach them before they vanish), and honour the system "Time to take action" otherwise.
    val sysA11y = remember(ctx) { ctx.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    var touchExplore by remember { mutableStateOf(sysA11y?.isTouchExplorationEnabled == true) }
    DisposableEffect(sysA11y) {
        val l = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { touchExplore = it }
        sysA11y?.addTouchExplorationStateChangeListener(l)
        onDispose { sysA11y?.removeTouchExplorationStateChangeListener(l) }
    }
    val composeA11y = androidx.compose.ui.platform.LocalAccessibilityManager.current
    val chromeTimeoutMs: Long = remember(playback.controlsTimeoutSec, composeA11y, touchExplore) {
        val base = playback.controlsTimeoutSec * 1000L
        if (touchExplore) 0L
        else (composeA11y?.calculateRecommendedTimeoutMillis(base, containsIcons = true, containsText = true, containsControls = true) ?: base)
            .coerceIn(base, Int.MAX_VALUE.toLong())
    }

    var chromeVisible by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    val currentId = ids.getOrElse(pagerState.currentPage) { fileId }
    val currentFile by produceState<FileEntity?>(initialValue = null, currentId, refresh) {
        value = vm.fileById(currentId)
    }
    // Auto-hide the chrome a few seconds after it appears or the page changes — only over photos and
    // videos (on a PDF, note or error page it would just hide the actions), not with TalkBack, and
    // not while the actions overflow menu is open, otherwise the menu closes itself under the user.
    // Bumped on quick-tag taps so the chrome stays up while tagging.
    var chromeTouch by remember { mutableIntStateOf(0) }
    val onMediaPage = currentFile?.let {
        com.cripta.app.data.VaultRepository.isImage(it.mimeType) || com.cripta.app.data.VaultRepository.isPlayable(it.mimeType)
    } == true
    LaunchedEffect(chromeVisible, pagerState.currentPage, menuOpen, chromeTouch, onMediaPage, chromeTimeoutMs) {
        if (chromeVisible && !menuOpen && onMediaPage && chromeTimeoutMs > 0) {
            delay((chromeTimeoutMs - 500L).coerceAtLeast(500L)); chromeVisible = false
        }
    }
    // Chrome show/hide: fade only (see the top bar below for why there is no slide).
    val chromeEnter = fadeIn(Motion.enter(Motion.MEDIUM))
    val chromeExit = fadeOut(Motion.exit(Motion.MEDIUM))

    var showTags by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }
    var confirmConvert by remember { mutableStateOf(false) }
    val convertedId by vm.convertedId.collectAsState()
    val converting by vm.converting.collectAsState()
    val convertProgress by vm.convertingProgress.collectAsState()
    // When true the user chose to let the conversion run in the background (notification only).
    var convertInBackground by remember { mutableStateOf(false) }
    LaunchedEffect(converting) { if (converting) convertInBackground = false }
    val pagerScope = rememberCoroutineScope()
    // Swipe-to-close can be seen by more than one gesture layer: pop the viewer only once.
    var closing by remember { mutableStateOf(false) }
    val closeOnce: () -> Unit = { if (!closing) { closing = true; onBack() } }
    // A deleted (or vanished) file leaves the list and the viewer moves on to the next one (the
    // previous, if it was the last); only the last remaining file closes the viewer.
    val removePage: (String) -> Unit = { id ->
        val i = vm.liveIds.indexOf(id)
        if (vm.liveIds.size <= 1) closeOnce()
        else if (i >= 0) {
            vm.liveIds.removeAt(i)
            val target = i.coerceAtMost(vm.liveIds.lastIndex)
            pagerScope.launch { pagerState.scrollToPage(target) }
        }
    }
    // Measured height of the top chrome (bar + quick tags): overlays below it start there instead
    // of at a guessed offset, which overlapped the title and tags in landscape.
    var topChromeH by remember { mutableStateOf(0.dp) }
    val chromeDensity = androidx.compose.ui.platform.LocalDensity.current
    var showQueue by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // Swipe up (not consumed by the page: photos at 1x, notes…) opens the details panel,
            // swipe down closes the viewer.
            // Videos consume their touches in the player, so they use the handle below instead.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Final)
                    var dx = 0f; var dy = 0f
                    var valid = true
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                        if (ev.changes.size > 1) { valid = false }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (ch.isConsumed) valid = false
                        dx += ch.position.x - ch.previousPosition.x
                        dy += ch.position.y - ch.previousPosition.y
                        if (!ch.pressed) break
                    }
                    if (valid && vm.playback.value.swipeForDetails && dy < -size.height * 0.12f && kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) showTags = true
                    // Swipe down closes the viewer.
                    if (valid && vm.playback.value.swipeToClose && dy > size.height * 0.15f && kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) closeOnce()
                }
            },
    ) {
        HorizontalPager(
            state = pagerState, modifier = Modifier.fillMaxSize(),
            // Keyed by file so removing a page never shows the neighbour's stale state.
            key = { ids.getOrNull(it) ?: it },
        ) { page ->
            val pageId = ids.getOrNull(page) ?: return@HorizontalPager
            MediaPage(
                id = pageId,
                onMissing = { removePage(pageId) },
                refreshKey = refresh,
                isCurrent = page == pagerState.currentPage,
                chromeVisible = chromeVisible,
                vm = vm,
                setChrome = { chromeVisible = it },
                onToggleChrome = { chromeVisible = !chromeVisible },
                onOpenDetails = { showTags = true },
                onClose = closeOnce,
                nextId = ids.getOrNull(page + 1),
                topInset = topChromeH,
                onNext = { pagerScope.launch { pagerState.animateScrollToPage(page + 1) } },
                controlsTimeoutMs = chromeTimeoutMs.toInt(),
            )
        }

        val inPip by com.cripta.app.viewer.PipController.inPip.collectAsState()
        // Bottom chrome: filmstrip of nearby files + the handle that opens the details panel.
        val scope = rememberCoroutineScope()
        val onVideo = currentFile?.let { com.cripta.app.data.VaultRepository.isVideo(it.mimeType) } == true
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val showStrip = ids.size > 1 && playback.filmstrip
        val pick: (Int) -> Unit = { i -> chromeTouch++; scope.launch { pagerState.scrollToPage(i) } }
        if (landscape) {
            // Landscape: the strip runs down the left edge, where a 16:9 video leaves a black band,
            // so it never sits over the picture. The details handle is not shown: swipe up does it.
            AnimatedVisibility(
                visible = chromeVisible && !inPip && showStrip,
                enter = chromeEnter,
                exit = chromeExit,
                // Between the top chrome and the seek bar, clear of the side camera.
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 8.dp + com.cripta.app.ui.LocalSideCutout.current.start,
                        top = maxOf(104.dp, topChromeH + 8.dp), bottom = 96.dp),
            ) {
                Filmstrip(ids, pagerState.currentPage, vm, vertical = true, onPick = pick)
            }
        } else {
            AnimatedVisibility(
                visible = chromeVisible && !inPip,
                enter = chromeEnter,
                exit = chromeExit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    // Above the player's seek bar for videos.
                    .padding(bottom = if (onVideo) 112.dp else 20.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showStrip) Filmstrip(ids, pagerState.currentPage, vm, onPick = pick)
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f), shape = CircleShape,
                        modifier = Modifier.padding(top = 8.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dy -> if (dy < -8f) { change.consume(); showTags = true } }
                            }
                            .clickable { showTags = true },
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ExpandLess, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(" Dettagli", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = chromeVisible && !inPip,
            // Fade only (no slide): a sliding bar moves the action icons under the finger, so a tap
            // on e.g. the tags button could miss while the bar was animating — it looked visible but
            // did nothing. Fading keeps each button in place and hittable the whole time it shows.
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
          Column(Modifier.onGloballyPositioned { topChromeH = with(chromeDensity) { it.size.height.toDp() } }) {
            TopAppBar(
                title = {
                    Column {
                        Text(currentFile?.originalName ?: "", maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (ids.size > 1) {
                            Text(
                                "${pagerState.currentPage + 1} / ${ids.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                // Bar background reaches the top edge; only its content is inset below the camera.
                windowInsets = chromeInsets.union(com.cripta.app.ui.LocalSideCutout.current.let {
                    WindowInsets(left = it.start, right = it.end)
                }),
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
                        IconButton(
                            onClick = { chromeTouch++; vm.toggleFavorite(file) },
                            modifier = Modifier.semantics {
                                stateDescription = if (file.isFavorite) "Nei preferiti" else "Non nei preferiti"
                            },
                        ) {
                            // The star pops in (0.5→1, ease-out, no overshoot) and turns gold when set.
                            AnimatedContent(
                                targetState = file.isFavorite,
                                transitionSpec = {
                                    (scaleIn(tween(Motion.MEDIUM, easing = Motion.EaseOutQuint), initialScale = 0.5f) +
                                        fadeIn(Motion.enter(Motion.SHORT))) togetherWith fadeOut(Motion.exit(Motion.SHORT))
                                },
                                label = "favoriteStar",
                            ) { fav ->
                                Icon(
                                    if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    if (fav) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                                    tint = if (fav) com.cripta.app.ui.theme.Favorite else Color.White,
                                )
                            }
                        }
                        IconButton(onClick = { showTags = true }) { Icon(Icons.AutoMirrored.Filled.Label, "Etichette") }
                        if (ids.size > 1) {
                            IconButton(onClick = { showQueue = true }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Coda")
                            }
                        }
                        // The less-frequent / destructive actions live in an overflow menu so the bar
                        // stays uncluttered and Delete is separated from the safe actions.
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (com.cripta.app.data.VaultRepository.isVideo(file.mimeType) && file.mimeType != "video/mp4") {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Converti in MP4") },
                                    leadingIcon = { Icon(Icons.Filled.Transform, null) },
                                    onClick = { menuOpen = false; confirmConvert = true },
                                )
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Etichette e dettagli") },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { menuOpen = false; showInfo = true },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Esporta sul dispositivo") },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = { menuOpen = false; confirmDownload = true },
                            )
                            androidx.compose.material3.HorizontalDivider()
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Elimina", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
            // Quick tags: pinned + recent tags, one tap toggles them on the file on screen.
            val qf = currentFile
            if (displayPrefs.viewerQuickTags && qf != null) {
                val quick = remember(allTags) {
                    (allTags.filter { it.pinned } +
                        allTags.filter { !it.pinned && it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt })
                        .distinctBy { it.id }.take(12)
                }
                val onFile by produceState(initialValue = emptyList<String>(), qf.id, refresh) { value = vm.tagNamesOf(qf.id) }
                if (quick.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        items(quick.size) { i ->
                            val t = quick[i]
                            val on = t.name in onFile
                            val c = com.cripta.app.ui.theme.tagColor(t)
                            Surface(
                                color = if (on) c else Color.White.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.clickable { chromeTouch++; vm.toggleTag(qf.id, t.id) },
                            ) {
                                Text(
                                    (if (t.pinned) "📌 " else "") + (t.alias?.takeIf { it.isNotBlank() }?.let { "$it " } ?: "") + t.name,
                                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
          }
        }

        // Queue: side panel with every file of the list (cover, name, duration); tap to jump.
        AnimatedVisibility(visible = showQueue, enter = fadeIn(Motion.enter(Motion.LONG)), exit = fadeOut(Motion.exit(Motion.LONG)), modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { showQueue = false },
            ) {
                QueuePanel(
                    ids = ids, current = pagerState.currentPage, vm = vm,
                    onPick = { i -> showQueue = false; pagerScope.launch { pagerState.scrollToPage(i) } },
                    onDismiss = { showQueue = false },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        androidx.activity.compose.BackHandler(enabled = showQueue) { showQueue = false }
    }

    val file = currentFile
    // Tags + details together in one panel (swipe up, the tags icon, or "Informazioni").
    if ((showTags || showInfo) && file != null) {
        DetailsSheet(
            file = file,
            allTags = allTags,
            refresh = refresh,
            showRecents = displayPrefs.showRecentTags,
            vm = vm,
            onDismiss = { showTags = false; showInfo = false },
        )
    }
    val trashOn by vm.trashEnabled.collectAsState()
    if (confirmDelete && file != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il file?") },
            text = {
                Text(if (trashOn) "\"${file.originalName}\" verrà spostato nel cestino (ripristinabile da Impostazioni › Archivio)."
                    else "\"${file.originalName}\" verrà eliminato in modo sicuro. Irreversibile.")
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; val id = file.id; vm.delete(id) { removePage(id) } }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
    if (confirmDownload && file != null) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Esportare sul dispositivo?") },
            text = {
                Text("Una copia NON cifrata di \"${file.originalName}\" verrà salvata in " +
                    "${ViewerViewModel.exportFolder(file.mimeType)}, visibile alle altre app. " +
                    "Il file resta anche nel vault.")
            },
            confirmButton = { TextButton(onClick = { confirmDownload = false; vm.download(file) }) { Text("Esporta") } },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Annulla") } },
        )
    }
    val convertSettings by vm.convertSettings.collectAsState()
    if (confirmConvert && file != null && !convertSettings.first) {
        // First conversion: ask what to do with the original (and offer to remember it).
        com.cripta.app.ui.components.ConvertChoiceDialog(
            count = 1,
            trashDays = convertSettings.second,
            onConfirm = { after, rememberIt -> confirmConvert = false; vm.convertToMp4(file, after, rememberIt) },
            onDismiss = { confirmConvert = false },
        )
    } else if (confirmConvert && file != null) {
        AlertDialog(
            onDismissRequest = { confirmConvert = false },
            title = { Text("Convertire in MP4?") },
            text = {
                Text(
                    "Crea una copia MP4 (H.264) scorribile, con le stesse etichette e cartella. Prosegue in " +
                        "background; la copia viene verificata prima di essere salvata e l'originale non viene " +
                        "mai distrutto (al massimo va nel cestino). Cosa fare dell'originale si sceglie in " +
                        "Impostazioni › Video."
                )
            },
            confirmButton = { TextButton(onClick = { confirmConvert = false; vm.convertToMp4(file) }) { Text("Converti") } },
            dismissButton = { TextButton(onClick = { confirmConvert = false }) { Text("Annulla") } },
        )
    }
    // Non-blocking progress pill: the conversion runs in the service (queued, one at a time);
    // the viewer stays fully usable and the pill can be hidden.
    if (converting && !convertInBackground) {
        androidx.compose.ui.window.Popup(
            alignment = Alignment.BottomCenter,
            // Above the seek bar (dp, not raw pixels, so it lands in the same place on every screen).
            offset = with(androidx.compose.ui.platform.LocalDensity.current) {
                val landscapeNow = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                androidx.compose.ui.unit.IntOffset(0, -(if (landscapeNow) 112.dp else 180.dp).roundToPx())
            },
        ) {
            // Smooth the ring between the service's progress steps.
            val shownProgress by animateFloatAsState(
                targetValue = convertProgress / 100f,
                animationSpec = tween(Motion.LONG, easing = Motion.EaseOutQuart),
                label = "convertProgress",
            )
            Surface(color = Color.Black.copy(alpha = 0.78f), shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = { shownProgress }, color = Color.White,
                        strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Conversione MP4 · $convertProgress%", color = Color.White,
                        style = MaterialTheme.typography.labelLarge)
                    // Not "Annulla": this stops the running transcode (the original stays as it is).
                    TextButton(onClick = { vm.cancelConversion() }) { Text("Interrompi", color = Color(0xFFFF8A80)) }
                    IconButton(onClick = { convertInBackground = true }) {
                        Icon(Icons.Filled.Close, "Nascondi", tint = Color.White)
                    }
                }
            }
        }
    }
    if (convertedId != null) {
        val originalId by vm.convertedOriginalId.collectAsState()
        AlertDialog(
            onDismissRequest = { vm.clearConverted() },
            title = { Text("Video convertito") },
            text = { Text("La copia MP4 scorribile è nella stessa cartella. Vuoi eliminare l'originale?") },
            confirmButton = {
                TextButton(onClick = {
                    // The original's page leaves the list (the viewer moves on instead of closing).
                    val orig = originalId
                    vm.deleteConvertedOriginal()
                    if (orig != null && orig in vm.liveIds) removePage(orig)
                }) { Text("Elimina originale", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { vm.clearConverted() }) { Text("Mantieni") } },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    // The value is what the user came for: full contrast; the label is the quieter caption.
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Source-link row of the Info dialog. With a link: tap copies it, the paste button replaces it with
 * the clipboard and the clear button removes it. Without one: a paste button adds it from the clipboard.
 */
@Composable
private fun LinkInfoLine(value: String?, onSet: (String?) -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = LocalContext.current
    val paste: () -> Unit = {
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.isBlank()) {
            android.widget.Toast.makeText(ctx, "Appunti vuoti", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            onSet(clip)
            android.widget.Toast.makeText(ctx, "Link salvato", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    if (value == null) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LINK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nessun link", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            TextButton(onClick = paste) {
                Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                Text("  Incolla")
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { CopyableInfoLine("Link", value) }
        IconButton(onClick = paste) {
            Icon(Icons.Filled.ContentPaste, "Sostituisci con il link copiato", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { onSet(null) }) {
            Icon(Icons.Filled.Close, "Rimuovi link", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Info row whose value is copied to the clipboard on a single tap (used for the source link). */
@Composable
private fun CopyableInfoLine(label: String, value: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clickable {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(value))
                android.widget.Toast.makeText(ctx, "Link copiato", android.widget.Toast.LENGTH_SHORT).show()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Filled.ContentCopy, "Copia link", tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp).size(20.dp))
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
    onOpenDetails: () -> Unit,
    onClose: () -> Unit,
    nextId: String?,
    onNext: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
    controlsTimeoutMs: Int,
    onMissing: () -> Unit,
) {
    var retry by remember(id) { mutableIntStateOf(0) }
    val state by produceState<ViewerState>(initialValue = ViewerState.Loading, id, refreshKey, retry) {
        if (value is ViewerState.Error) value = ViewerState.Loading
        value = vm.stateFor(id)
    }
    // The file was deleted meanwhile (e.g. from the note editor): drop its page.
    val latestMissing by androidx.compose.runtime.rememberUpdatedState(onMissing)
    LaunchedEffect(state, isCurrent) {
        val s = state
        if (isCurrent && s is ViewerState.Error && s.missing) latestMissing()
    }
    // Crossfade between loading and content, keyed on the kind of state only, so a refresh of the
    // same page (favorite, tags) doesn't re-animate or rebuild the player.
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit(Motion.MEDIUM)) },
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
        label = "mediaPage",
    ) { s ->
        when (s) {
            is ViewerState.Loading -> CenteredPage(onTap = onToggleChrome) { DelayedSpinner(color = Color.White) }
            is ViewerState.Error -> CenteredPage(onTap = onToggleChrome) {
                Icon(Icons.Filled.ErrorOutline, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
                Text(s.message, color = Color.White, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                if (!s.missing) {
                    OutlinedButton(
                        onClick = { retry++ },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Riprova")
                    }
                }
            }
            is ViewerState.Photo -> ZoomableImage(s.bytes, s.file.originalName, onSingleTap = onToggleChrome)
            is ViewerState.Video -> if (isCurrent) VideoPlayer(s.file, vm, controlsVisible = chromeVisible, onControlsVisibilityChanged = setChrome, onOpenDetails = onOpenDetails, onClose = onClose, nextId = nextId, onNext = onNext, topInset = topInset, controlsTimeoutMs = controlsTimeoutMs)
                else CenteredPage(onTap = onToggleChrome) { DelayedSpinner(color = Color.White) }
            is ViewerState.Note -> NoteView(s.text, onSingleTap = onToggleChrome)
            is ViewerState.Pdf -> PdfView(s.bytes, onSingleTap = onToggleChrome)
            is ViewerState.Other -> CenteredPage(onTap = onToggleChrome) {
                Text("Nessuna anteprima per questo tipo di file.", color = Color.White, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge)
                Text("Per aprirlo con un'altra app: ⋮ › Esporta sul dispositivo.", color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Centred message page (loading, error, unsupported type). A tap anywhere toggles the chrome, so
 *  the actions can always be brought back even where there is no media to tap. */
@Composable
private fun CenteredPage(onTap: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val latestTap by androidx.compose.runtime.rememberUpdatedState(onTap)
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { latestTap() }) }
            .padding(horizontal = 32.dp, vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * Spinner that appears only after [delayMs] (fading in), so fast opens never flash it. Shared by the
 * viewer, the note editor and the favorites grid.
 */
@Composable
internal fun DelayedSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    delayMs: Long = 250L,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs); show = true }
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(Motion.enter(Motion.MEDIUM)),
        exit = fadeOut(Motion.exit(Motion.SHORT)),
        label = "delayedSpinner",
    ) {
        CircularProgressIndicator(color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color)
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
        // Long-press selects text to copy; a single tap still toggles the chrome.
        SelectionContainer {
            Text(text.ifBlank { "(nota vuota)" }, color = Color.White)
        }
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
private fun PdfView(bytes: ByteArray, onSingleTap: () -> Unit) {
    val result by produceState<Result<PdfDoc>?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDoc(bytes) } }
    }
    val doc = result?.getOrNull()
    DisposableEffect(doc) { onDispose { doc?.close() } }
    when {
        result == null -> CenteredPage(onTap = onSingleTap) { DelayedSpinner(color = Color.White) }
        doc == null -> CenteredPage(onTap = onSingleTap) {
            Icon(Icons.Filled.ErrorOutline, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
            Text("Impossibile aprire il PDF: il documento potrebbe essere danneggiato o protetto.",
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            Text("Puoi esportarlo sul dispositivo (⋮) e aprirlo con un'altra app.",
                color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        else -> {
            val listState = rememberLazyListState()
            // Pinch to zoom (1x..4x), double-tap to toggle 2.5x. At 1x one-finger drags are left
            // alone, so the list scrolls vertically and the pager still swipes horizontally.
            var zoom by remember { mutableFloatStateOf(1f) }
            var panX by remember { mutableFloatStateOf(0f) }
            var panY by remember { mutableFloatStateOf(0f) }
            val latestTap by androidx.compose.runtime.rememberUpdatedState(onSingleTap)
            Box(
                Modifier.fillMaxSize().background(Color(0xFF0A0C10)).clipToBounds()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val maxX = (zoom - 1f) * size.width / 2f
                                val maxY = (zoom - 1f) * size.height / 2f
                                if (pressed >= 2) {
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                    val pan = event.calculatePan()
                                    val mx = (zoom - 1f) * size.width / 2f
                                    val my = (zoom - 1f) * size.height / 2f
                                    panX = (panX + pan.x).coerceIn(-mx, mx)
                                    panY = (panY + pan.y).coerceIn(-my, my)
                                    event.changes.forEach { it.consume() }
                                } else if (pressed == 1 && zoom > 1f) {
                                    // Zoomed: one finger pans sideways (the list still scrolls vertically).
                                    val pan = event.calculatePan()
                                    if (pan.x != 0f) {
                                        panX = (panX + pan.x).coerceIn(-maxX, maxX)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                                if (zoom <= 1f) { panX = 0f; panY = 0f }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { latestTap() },
                            onDoubleTap = {
                                if (zoom > 1f) { zoom = 1f; panX = 0f; panY = 0f } else { zoom = 2.5f }
                            },
                        )
                    },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom
                        translationX = panX; translationY = panY
                    },
                ) {
                    items(doc.pageCount) { index ->
                        val bmp by produceState<Bitmap?>(initialValue = null, index, doc) { value = doc.render(index) }
                        val b = bmp
                        val desc = "Pagina ${index + 1} di ${doc.pageCount}"
                        if (b != null) {
                            Image(b.asImageBitmap(), desc, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        } else {
                            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                                DelayedSpinner(color = Color.White)
                            }
                        }
                    }
                }
                // Page indicator: the page at the top of the screen.
                if (doc.pageCount > 1) {
                    val page by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f), shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
                    ) {
                        Text("$page / ${doc.pageCount}", color = Color.White, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    file: FileEntity,
    vm: ViewerViewModel,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    onClose: () -> Unit,
    nextId: String?,
    onNext: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
    /** Controller auto-hide delay; 0 = never hide (TalkBack touch exploration). */
    controlsTimeoutMs: Int,
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
            // Enable constant-bitrate seeking so formats without a built-in seek index
            // (e.g. MPEG program/transport streams, MP3/ADTS/AMR) can still be scrubbed by
            // estimating the byte position from time. Also broadens what plays/seeks overall.
            val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setConstantBitrateSeekingAlwaysEnabled(true)
            setMediaSource(
                ProgressiveMediaSource.Factory(factory, extractors)
                    .createMediaSource(MediaItem.fromUri("cripta://${file.id}"))
            )
            repeatMode = Player.REPEAT_MODE_ONE   // loop the video (setting applied below)
            prepare()
            playWhenReady = true
        }
    }
    // User playback preferences: loop on/off and start muted.
    var resumeEnabled by remember(file.id) { mutableStateOf(true) }
    LaunchedEffect(player) {
        val prefs = vm.playbackPrefs()
        player.repeatMode = if (prefs.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (prefs.muted) player.volume = 0f
        resumeEnabled = prefs.resume
        // Resume where it was left (skipping a position too close to either end).
        val pos = file.playbackPosMs ?: 0L
        val dur = file.durationMs ?: 0L
        if (prefs.resume && pos > 3_000 && (dur <= 0 || pos < dur - 5_000)) player.seekTo(pos)
    }
    // Resize presets cycled by the aspect button: (resizeMode, label, videoScale). The PlayerView
    // always stays full-screen so the CONTROLS never move; zoom is applied only to the video
    // surface. "Altezza (taglio ridotto)" fills more than Adatta while cropping the sides less than
    // Riempi/Altezza piena.
    val modes = listOf<Triple<Int, String, Float>>(
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Adatta", 1f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Altezza (taglio ridotto)", 1.3f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "Larghezza piena", 1f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Riempi (ritaglia)", 1f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Allarga (deforma)", 1f),
    )
    var modeIdx by remember { mutableIntStateOf(0) }
    // Pinch-to-zoom factor applied on top of the current resize mode (1x..5x). Reset when the mode
    // or the shown video changes.
    var userZoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(modeIdx, file.id) { userZoom = 1f; panX = 0f; panY = 0f }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val inPip by com.cripta.app.viewer.PipController.inPip.collectAsState()
    val prefs by vm.playback.collectAsState()

    // "Prossimo": in the last seconds of a video (loop off, option on, a next file exists) a card
    // counts down, then the viewer moves on when playback ends. The card's X cancels it.
    val autoNextOn = !prefs.loop && prefs.autoNext && nextId != null && !inPip
    var remainingMs by remember(file.id) { mutableStateOf<Long?>(null) }
    var totalMs by remember(file.id) { mutableStateOf(0L) }
    var nextCancelled by remember(file.id) { mutableStateOf(false) }
    LaunchedEffect(file.id, autoNextOn) {
        if (!autoNextOn) { remainingMs = null; return@LaunchedEffect }
        while (true) {
            val d = player.duration
            totalMs = if (d > 0 && d != C.TIME_UNSET) d else 0L
            remainingMs = if (totalMs > 0) (totalMs - player.currentPosition).coerceAtLeast(0L) else null
            delay(250)
        }
    }
    val latestOnNext by androidx.compose.runtime.rememberUpdatedState(onNext)
    DisposableEffect(player, autoNextOn, nextCancelled) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && autoNextOn && !nextCancelled) latestOnNext()
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val nextFile by produceState<FileEntity?>(null, nextId) { value = nextId?.let { vm.fileById(it) } }
    val nextThumb by produceState<Bitmap?>(null, nextId) { value = nextId?.let { vm.thumbOf(it) } }
    val rotationLocked by vm.rotationLocked.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    val vLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val sideCut = com.cripta.app.ui.LocalSideCutout.current
    val cutPx = with(androidx.compose.ui.platform.LocalDensity.current) { sideCut.start.roundToPx() to sideCut.end.roundToPx() }
    /** Overlay for the gesture in progress: "2×", "☀ 60%", "🔊 40%". */
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var videoAspect by remember(file.id) { mutableStateOf<android.util.Rational?>(null) }
    var seekLabel by remember { mutableStateOf<String?>(null) }
    var modeLabel by remember { mutableStateOf<String?>(null) }
    // Bumped on every double-tap / mode change so a repeated identical label restarts its timer.
    var seekTick by remember { mutableIntStateOf(0) }
    var modeTick by remember { mutableIntStateOf(0) }
    // Last shown text, kept through the fade-out after the label is cleared.
    var lastSeekLabel by remember { mutableStateOf("") }
    var lastModeLabel by remember { mutableStateOf("") }
    LaunchedEffect(seekLabel, seekTick) { seekLabel?.let { lastSeekLabel = it; delay(900); seekLabel = null } }
    LaunchedEffect(modeLabel, modeTick) { modeLabel?.let { lastModeLabel = it; delay(1600); modeLabel = null } }
    // One-time hint for the gestures that have no visible control (shown on the first video only).
    val hintPrefs = remember(ctx) { ctx.getSharedPreferences("viewer_hints", android.content.Context.MODE_PRIVATE) }
    var showGestureHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hintPrefs.getBoolean("gesture_hint_shown", false)) {
            hintPrefs.edit().putBoolean("gesture_hint_shown", true).apply()
            delay(800); showGestureHint = true; delay(5000); showGestureHint = false
        }
    }

    DisposableEffect(file.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width <= 0 || videoSize.height <= 0) return
                val w = videoSize.width * videoSize.pixelWidthHeightRatio
                val h = videoSize.height.toFloat()
                // Keep within the PiP limits (between 1:2.39 and 2.39:1).
                val ratio = (w / h).coerceIn(1f / 2.39f, 2.39f)
                videoAspect = android.util.Rational((ratio * 1000).toInt(), 1000)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (resumeEnabled) vm.savePosition(file.id, player.currentPosition, player.duration)
            com.cripta.app.viewer.PipController.armedAspect = null
            // Leaving this video (e.g. swiping to a photo): drop its auto-rotation unless locked.
            if (!vm.rotationLocked.value) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            player.release()
        }
    }
    // Rotation while a video plays (unless the user locked it with the button):
    // - option on: follow the video's orientation (landscape video -> landscape, either way up);
    // - option off / square video: turn freely with the phone. FULL_SENSOR, not UNSPECIFIED: with
    //   the system rotation lock on, UNSPECIFIED never rotated, so a video couldn't be turned at all.
    LaunchedEffect(videoAspect, prefs.autoRotate, rotationLocked) {
        if (activity == null || rotationLocked) return@LaunchedEffect
        val a = videoAspect
        activity.requestedOrientation = when {
            !prefs.autoRotate || a == null -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            a.toFloat() > 1.05f -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            a.toFloat() < 0.95f -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }
    // Picture-in-Picture: armed while this video is on screen and the option is on.
    LaunchedEffect(videoAspect, prefs.pip) {
        com.cripta.app.viewer.PipController.armedAspect = if (prefs.pip) (videoAspect ?: android.util.Rational(16, 9)) else null
    }
    // In PiP only the video shows: no controller.
    LaunchedEffect(inPip) { playerViewRef?.useController = !inPip }
    // One layer, not two: the ExoPlayer controls (seek bar, play/pause) follow the app chrome
    // (top bar, tags, filmstrip). The controller's own visibility changes already flow back into
    // the chrome through the visibility listener; this is the other direction, so they always
    // show and hide together — including right after opening, when they used to drift apart.
    LaunchedEffect(controlsVisible, playerViewRef, inPip) {
        val pv = playerViewRef ?: return@LaunchedEffect
        if (inPip) return@LaunchedEffect
        if (controlsVisible && !pv.isControllerFullyVisible) pv.showController()
        else if (!controlsVisible && pv.isControllerFullyVisible) pv.hideController()
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration
        val max = if (dur > 0) dur else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
    }
    fun toggleController() {
        playerViewRef?.let { if (it.isControllerFullyVisible) it.hideController() else it.showController() }
    }

    Box(
        Modifier.fillMaxSize().clipToBounds()
            // Vertical swipe anywhere on the video: up = tags & details, down = close the player.
            // Observed in the Initial pass (nothing consumed) because the PlayerView under it takes
            // every touch, which used to leave these gestures working only outside the picture.
            // Not on the brightness/volume side strips, not while zoomed, not with two fingers.
            .pointerInput(prefs.gestures, prefs.volumeGesture, inPip) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val onSide = (prefs.gestures && down.position.x < w * 0.3f) ||
                        (prefs.volumeGesture && down.position.x > w * 0.7f)
                    var dx = 0f; var dy = 0f; var multi = false
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } > 1) multi = true
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        dx += ch.position.x - ch.previousPosition.x
                        dy += ch.position.y - ch.previousPosition.y
                        if (!ch.pressed) break
                    }
                    if (inPip || multi || onSide || userZoom > 1f) return@awaitEachGesture
                    if (kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) {
                        val p = vm.playback.value
                        if (dy < -h * 0.12f) { if (p.swipeForDetails) onOpenDetails() }
                        else if (dy > h * 0.15f) { if (p.swipeToClose) onClose() }
                    }
                }
            }
            // Pinch-to-zoom, but ONLY react to two or more fingers so a single-finger horizontal
            // swipe still reaches the pager (change video) instead of being consumed here.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    fun clamp() {
                        // Keep the zoomed video within the screen bounds.
                        val total = modes[modeIdx].third * userZoom
                        val maxX = ((total - 1f).coerceAtLeast(0f)) * size.width / 2f
                        val maxY = ((total - 1f).coerceAtLeast(0f)) * size.height / 2f
                        panX = panX.coerceIn(-maxX, maxX)
                        panY = panY.coerceIn(-maxY, maxY)
                    }
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            // Pinch: zoom + pan around the fingers.
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            userZoom = (userZoom * zoom).coerceIn(1f, 5f)
                            panX += pan.x; panY += pan.y
                            clamp()
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && userZoom > 1f) {
                            // One finger while zoomed = pan to a specific part. At 1x the swipe is
                            // left to the pager (change video).
                            val pan = event.calculatePan()
                            if (pan.x != 0f || pan.y != 0f) {
                                panX += pan.x; panY += pan.y
                                clamp()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    resizeMode = modes[modeIdx].first
                    // Let the (zoomed) video surface overflow its content frame; the outer Box clips
                    // it to the screen. Without this the zoom is cut at the original video rectangle.
                    clipChildren = false
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true            // don't let the screen dim during playback
                    controllerShowTimeoutMs = 4000 // keep the top-bar actions (tags, info…) reachable longer
                    // Mirror the ExoPlayer controller's visibility onto the app chrome
                    // (top bar with the name + the aspect toggle) so a tap reveals both.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { vis -> onControlsVisibilityChanged(vis == View.VISIBLE) }
                    )
                    playerViewRef = this
                }
            },
            // The PlayerView always fills the screen so the CONTROLS never move; zoom is applied only
            // to the video surface (scaleX/scaleY), which the surrounding Box clips.
            update = { pv ->
                pv.resizeMode = modes[modeIdx].first
                pv.controllerShowTimeoutMs = controlsTimeoutMs
                // Keep the controls (time, buttons) clear of a side camera; the video stays full-bleed.
                pv.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.setPadding(cutPx.first, 0, cutPx.second, 0)
                val scale = modes[modeIdx].third * userZoom
                pv.videoSurfaceView?.let { surface ->
                    // Stop the content frame from clipping the scaled surface to the video rect.
                    (surface.parent as? android.view.ViewGroup)?.clipChildren = false
                    surface.scaleX = scale
                    surface.scaleY = scale
                    surface.translationX = panX
                    surface.translationY = panY
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Left / right edge zones: double-tap to jump 10s; single tap toggles the controls.
        // These consume the pointer-down (detectTapGestures does), so they must stay clear of
        // the bottom seek bar — otherwise, in landscape where the screen is short, they overlap
        // the time bar's ends and swallow the drag, making the slider impossible to move.
        val seekBarClearance = 96.dp
        val stepMs = prefs.seekStepSec * 1000L
        val scope = rememberCoroutineScope()
        // Hold on a side = 2x speed until release (the menu speed setting is restored after).
        suspend fun androidx.compose.foundation.gestures.PressGestureScope.holdForSpeed() {
            if (!prefs.holdForSpeed) return
            val job = scope.launch {
                delay(450)
                val before = player.playbackParameters.speed
                val sp = prefs.holdSpeed
                player.setPlaybackSpeed(sp)
                gestureLabel = (if (sp % 1f == 0f) "${sp.toInt()}" else "$sp").replace('.', ',') + "×"
                try { kotlinx.coroutines.awaitCancellation() } finally {
                    player.setPlaybackSpeed(before)
                    gestureLabel = null
                }
            }
            tryAwaitRelease()
            job.cancel()
        }
        // Vertical drag on a side: left = screen brightness, right = media volume.
        fun Modifier.sideDrag(brightness: Boolean): Modifier = if (inPip || !(if (brightness) prefs.gestures else prefs.volumeGesture)) this else pointerInput(brightness) {
            val audio = activity?.getSystemService(android.media.AudioManager::class.java)
            var level = 0f
            detectVerticalDragGestures(
                onDragStart = {
                    level = if (brightness) {
                        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                    } else {
                        val max = audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 1
                        (audio?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0).toFloat() / max
                    }
                },
                onDragEnd = { gestureLabel = null },
                onDragCancel = { gestureLabel = null },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    level = (level - dy / (size.height * 0.8f)).coerceIn(0f, 1f)
                    if (brightness) {
                        activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) } }
                        gestureLabel = "☀ ${(level * 100).toInt()}%"
                    } else {
                        val max = audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 1
                        audio?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (level * max).toInt(), 0)
                        gestureLabel = "🔊 ${(level * 100).toInt()}%"
                    }
                },
            )
        }
        Box(
            Modifier.align(Alignment.TopStart).fillMaxWidth(0.3f).fillMaxHeight()
                .padding(bottom = seekBarClearance)
                .pointerInput(stepMs) {
                    detectTapGestures(
                        onDoubleTap = { seekBy(-stepMs); seekTick++; seekLabel = "-${prefs.seekStepSec}s" },
                        onTap = { toggleController() },
                        onPress = { holdForSpeed() },
                    )
                }
                .sideDrag(brightness = true)
        )
        Box(
            Modifier.align(Alignment.TopEnd).fillMaxWidth(0.3f).fillMaxHeight()
                .padding(bottom = seekBarClearance)
                .pointerInput(stepMs) {
                    detectTapGestures(
                        onDoubleTap = { seekBy(stepMs); seekTick++; seekLabel = "+${prefs.seekStepSec}s" },
                        onTap = { toggleController() },
                        onPress = { holdForSpeed() },
                    )
                }
                .sideDrag(brightness = false)
        )
        // "Prossimo" card, bottom-right above the seek bar.
        val rem = remainingMs
        val window = minOf(prefs.autoNextSec * 1000L, totalMs / 3)
        if (autoNextOn && !nextCancelled && rem != null && window > 0 && rem <= window) {
            // Tap = play the next file now. In portrait, while the controls show, it sits above the
            // filmstrip + "Dettagli" handle instead of overlapping them.
            Surface(
                onClick = onNext,
                color = Color.Black.copy(alpha = 0.78f), shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 12.dp + sideCut.end,
                        bottom = seekBarClearance + 8.dp + if (controlsVisible && !vLandscape) 84.dp else 0.dp)
                    .widthIn(max = 300.dp),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(40.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.extraSmall)
                        .background(Color.White.copy(alpha = 0.12f))) {
                        nextThumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.padding(horizontal = 10.dp).weight(1f, fill = false)) {
                        Text("Prossimo tra ${(rem + 999) / 1000} s", color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium)
                        Text(nextFile?.originalName ?: "", color = Color.White, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        com.cripta.app.ui.components.formatDuration(nextFile?.durationMs)?.let {
                            Text(it, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { nextCancelled = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Annulla prossimo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        gestureLabel?.let { lbl ->
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (vLandscape) maxOf(168.dp, topInset + 64.dp) else maxOf(120.dp, topInset + 12.dp))) {
                Text(lbl, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }

        // Buffering spinner: appears only after 250 ms (a quick seek never flashes it).
        AnimatedVisibility(
            visible = buffering,
            enter = scaleIn(Motion.enter(Motion.MEDIUM, delay = 250), initialScale = 0.8f) +
                fadeIn(Motion.enter(Motion.MEDIUM, delay = 250)),
            exit = fadeOut(Motion.exit(Motion.LONG)),
            modifier = Modifier.align(Alignment.Center),
            label = "buffering",
        ) {
            CircularProgressIndicator(color = Color.White)
        }

        // "+10s" / "-10s" hint on the tapped side; pops in, fades out a little slower.
        val seekText = seekLabel ?: lastSeekLabel
        AnimatedVisibility(
            visible = seekLabel != null,
            enter = scaleIn(Motion.enter(Motion.SHORT), initialScale = 0.8f) + fadeIn(Motion.enter(Motion.SHORT)),
            exit = fadeOut(Motion.exit(Motion.LONG)),
            modifier = Modifier.align(if (seekText.startsWith("+")) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 44.dp),
            label = "seekHint",
        ) {
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape) {
                Text(seekText, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }

        // One-time explanation of the invisible gestures.
        AnimatedVisibility(
            visible = showGestureHint && !inPip,
            enter = fadeIn(Motion.enter(Motion.LONG)),
            exit = fadeOut(Motion.exit(Motion.LONG)),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            label = "gestureHint",
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f), shape = MaterialTheme.shapes.large,
                modifier = Modifier.clickable { showGestureHint = false },
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gesti del player", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    val lines = buildList {
                        add("Doppio tocco a sinistra/destra: −/+ ${prefs.seekStepSec} s")
                        add("Pizzica con due dita: ingrandisci")
                        if (prefs.holdForSpeed) add("Tieni premuto ai lati: velocità ${(if (prefs.holdSpeed % 1f == 0f) "${prefs.holdSpeed.toInt()}" else "${prefs.holdSpeed}").replace('.', ',')}×")
                        if (prefs.gestures) add("Scorri in verticale a sinistra: luminosità")
                        if (prefs.volumeGesture) add("Scorri in verticale a destra: volume")
                    }
                    lines.forEach {
                        Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // Aspect toggle on the right edge (drawn above the seek zone), only while controls show.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(Motion.enter(Motion.MEDIUM)),
            exit = fadeOut(Motion.exit(Motion.MEDIUM)),
            // Landscape: a row under the top chrome (a column would overlap the quick tags above
            // and the seek bar below on a short screen). Portrait: a column on the right edge.
            modifier = (if (vLandscape) Modifier.align(Alignment.TopEnd).padding(top = maxOf(112.dp, topInset + 8.dp)) else Modifier.align(Alignment.CenterEnd))
                .padding(end = 12.dp + sideCut.end),
        ) {
          val sideButtons: @Composable () -> Unit = {
                Surface(color = Color.Black.copy(alpha = 0.45f), shape = CircleShape) {
                    // Names the current mode and the one a tap switches to (TalkBack read a fixed
                    // "Adatta/riempi" across all five modes).
                    IconButton(onClick = {
                        modeIdx = (modeIdx + 1) % modes.size
                        modeTick++
                        modeLabel = modes[modeIdx].second
                    }) {
                        Icon(Icons.Filled.AspectRatio,
                            "Formato video: ${modes[modeIdx].second}. Tocca per: ${modes[(modeIdx + 1) % modes.size].second}",
                            tint = Color.White)
                    }
                }
                // Rotation lock: keeps the current orientation (auto-rotate resumes when unlocked).
                Surface(color = if (rotationLocked) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f), shape = CircleShape) {
                    IconButton(onClick = {
                        val lock = !rotationLocked
                        vm.rotationLocked.value = lock
                        activity?.requestedOrientation = if (lock) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        modeTick++
                        modeLabel = if (lock) "Rotazione bloccata" else "Rotazione libera"
                    }) {
                        Icon(if (rotationLocked) Icons.Filled.ScreenLockRotation else Icons.Filled.ScreenRotation,
                            if (rotationLocked) "Sblocca rotazione" else "Blocca rotazione", tint = Color.White)
                    }
                }
                // Picture-in-Picture on demand (the automatic one on leaving the app is a setting).
                if (activity != null &&
                    activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    Surface(color = Color.Black.copy(alpha = 0.45f), shape = CircleShape) {
                        IconButton(onClick = {
                            runCatching {
                                activity.enterPictureInPictureMode(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAspectRatio(videoAspect ?: android.util.Rational(16, 9)).build()
                                )
                            }.onFailure { modeLabel = "Picture-in-Picture non disponibile" }
                        }) {
                            Icon(Icons.Filled.PictureInPictureAlt, "Picture-in-Picture", tint = Color.White)
                        }
                    }
                }
          }
            if (vLandscape) Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) { sideButtons() }
            else Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) { sideButtons() }
        }

        // Brief overlay naming the resize mode just selected (1.6 s; the text stays through the fade).
        AnimatedVisibility(
            visible = modeLabel != null,
            enter = scaleIn(Motion.enter(Motion.MEDIUM), initialScale = 0.9f) + fadeIn(Motion.enter(Motion.MEDIUM)),
            exit = fadeOut(Motion.exit(Motion.LONG)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = if (vLandscape) maxOf(168.dp, topInset + 64.dp) else maxOf(120.dp, topInset + 12.dp)),
            label = "modeLabel",
        ) {
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape) {
                Text(modeLabel ?: lastModeLabel, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

/** Side panel listing the whole browse list: cover, name, duration/size; the current file marked. */
@Composable
private fun QueuePanel(
    ids: List<String>,
    current: Int,
    vm: ViewerViewModel,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cut = com.cripta.app.ui.LocalSideCutout.current
    val width = minOf(340.dp, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.85f)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    Surface(
        color = Color(0xF2121212),
        modifier = modifier.fillMaxHeight().width(width)
            // Taps inside the panel must not reach the scrim behind it (which closes the panel).
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(end = cut.end)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Coda · ${ids.size}", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Chiudi coda", tint = Color.White) }
            }
            androidx.compose.foundation.lazy.LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(ids.size, key = { ids[it] }) { i ->
                    val f by produceState<FileEntity?>(null, ids[i]) { value = vm.fileById(ids[i]) }
                    val bmp by produceState<Bitmap?>(null, ids[i]) { value = vm.thumbOf(ids[i]) }
                    val sel = i == current
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Transparent)
                            .clickable { onPick(i) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.height(44.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.extraSmall)
                            .background(Color.White.copy(alpha = 0.12f))) {
                            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(f?.originalName ?: "", color = Color.White, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                if (sel) "In riproduzione" else null,
                                com.cripta.app.ui.components.formatDuration(f?.durationMs),
                                f?.let { com.cripta.app.ui.components.formatBytes(it.sizeBytes) },
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact strip of the neighbouring covers (3 before, 3 after), centred on the current file; tap
 * one to jump to it. Small 16:9 thumbs, the others dimmed, so it hides little of the video.
 */
@Composable
private fun Filmstrip(ids: List<String>, current: Int, vm: ViewerViewModel, vertical: Boolean = false, onPick: (Int) -> Unit) {
    val span = if (vertical) 2 else 3
    val from = (current - span).coerceAtLeast(0)
    val to = (current + span).coerceAtMost(ids.lastIndex)
    val thumbs: @Composable () -> Unit = {
        for (i in from..to) {
            androidx.compose.runtime.key(ids[i]) {
                val bmp by produceState<Bitmap?>(null, ids[i]) { value = vm.thumbOf(ids[i]) }
                val sel = i == current
                Box(
                    Modifier.height(if (vertical) (if (sel) 28.dp else 22.dp) else (if (sel) 34.dp else 26.dp)).aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color.White.copy(alpha = 0.12f))
                        .then(if (sel) Modifier.border(1.5.dp, Color.White, MaterialTheme.shapes.extraSmall) else Modifier)
                        .clickable { onPick(i) },
                ) {
                    bmp?.let {
                        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                            alpha = if (sel) 1f else 0.6f, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
    if (vertical) {
        Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Scrolls if even 5 thumbs don't fit between the top chrome and the seek bar.
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) { thumbs() }
    } else {
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { thumbs() }
    }
}

/**
 * One panel for everything about the file: its details (size, duration, resolution, date, source
 * link, technical info) and its tags, editable in place (tap toggles, long-press edits the tag).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(
    file: FileEntity,
    allTags: List<com.cripta.app.data.db.TagEntity>,
    refresh: Int,
    showRecents: Boolean,
    vm: ViewerViewModel,
    onDismiss: () -> Unit,
) {
    val onFile by produceState(initialValue = emptyList<String>(), file.id, refresh) { value = vm.tagNamesOf(file.id) }
    val isVid = com.cripta.app.data.VaultRepository.isVideo(file.mimeType)
    val videoDiag by produceState(initialValue = "", file.id, isVid) { value = if (isVid) vm.videoInfo(file) else "" }
    var editTag by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showTech by remember { mutableStateOf(false) }
    val byName = remember(allTags) { allTags.associateBy { it.name } }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(file.originalName, style = MaterialTheme.typography.titleLarge, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    "Aggiunto il " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                        .format(java.util.Date(file.createdAt)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Stats-style tiles.
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                DetailTile("Dimensione", com.cripta.app.ui.components.formatBytes(file.sizeBytes), Modifier.weight(1f))
                com.cripta.app.ui.components.formatDuration(file.durationMs)?.let { DetailTile("Durata", it, Modifier.weight(1f)) }
                if (file.width != null && file.height != null) {
                    DetailTile("Risoluzione", "${file.width}×${file.height}" +
                        (com.cripta.app.ui.vault.qualityLabel(file.width, file.height)?.takeIf { isVid }?.let { " · $it" } ?: ""),
                        Modifier.weight(1.3f))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Etichette", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }) { Text("+ Nuova") }
            }
            Text("Tocca per aggiungere o togliere · tieni premuto per alias, colore e 📌.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.cripta.app.ui.vault.TagSections(
                allTags = allTags,
                extraNames = onFile,
                showRecents = showRecents,
                isSelected = { n -> onFile.any { it.equals(n, ignoreCase = true) } },
                onToggle = { n ->
                    val t = byName[n]
                    if (t != null) vm.toggleTag(file.id, t.id)
                    else vm.setTags(file.id, onFile + n)
                },
                onLongPress = { editTag = it },
            )

            HorizontalDividerCompat()
            LinkInfoLine(value = file.sourceUrl?.takeIf { it.isNotBlank() }, onSet = { vm.setSourceUrl(file.id, it) })
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column {
                    InfoLine("Tipo", file.mimeType)
                    if (isVid && videoDiag.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clickable { showTech = !showTech }.padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Dettagli tecnici", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (showTech) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (showTech) "Comprimi" else "Espandi")
                        }
                        if (showTech) {
                            Text(videoDiag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    editTag?.let { name ->
        val t = byName[name]
        com.cripta.app.ui.vault.LabelEditorDialog(
            title = "Modifica #$name",
            initialName = name,
            nameEditable = false,
            initialAlias = t?.alias ?: "",
            onConfirm = { _, alias -> vm.setTagAlias(name, alias); editTag = null },
            onDismiss = { editTag = null },
            onColor = if (t != null) ({ c -> vm.setTagColor(name, c) }) else null,
            initialColor = t?.color,
            onPinned = if (t != null) ({ p -> vm.setTagPinned(name, p) }) else null,
            initialPinned = t?.pinned == true,
        )
    }
    if (creating) {
        com.cripta.app.ui.vault.LabelEditorDialog(
            title = "Nuova etichetta",
            onConfirm = { name, alias ->
                vm.createTag(name, alias)
                vm.setTags(file.id, (onFile + name).distinct())
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun DetailTile(label: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HorizontalDividerCompat() = androidx.compose.material3.HorizontalDivider()

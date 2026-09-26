@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cripta.app.ui.viewer

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import androidx.compose.ui.layout.layout
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
    val currentId = ids.getOrElse(pagerState.currentPage) { fileId }
    val currentFile by produceState<FileEntity?>(initialValue = null, currentId, refresh) {
        value = vm.fileById(currentId)
    }
    // Auto-hide the chrome a few seconds after it appears or the page changes — only over photos and
    // videos (on a PDF, note or error page it would just hide the actions), not with TalkBack, and
    // not while the actions overflow menu is open, otherwise the menu closes itself under the user.
    // Bumped on quick-tag taps so the chrome stays up while tagging.
    var chromeTouch by remember { mutableIntStateOf(0) }
    // A paused (or finished) video keeps its controls up, as ExoPlayer itself does. Auto-hiding the
    // chrome anyway made the player re-show its controller right after: an endless hide/show blink.
    var videoPaused by remember { mutableStateOf(false) }
    val onMediaPage = currentFile?.let {
        com.cripta.app.data.VaultRepository.isImage(it.mimeType) || com.cripta.app.data.VaultRepository.isPlayable(it.mimeType)
    } == true
    LaunchedEffect(chromeVisible, pagerState.currentPage, chromeTouch, onMediaPage, chromeTimeoutMs, videoPaused) {
        if (chromeVisible && onMediaPage && chromeTimeoutMs > 0 && !videoPaused) {
            delay((chromeTimeoutMs - 500L).coerceAtLeast(500L)); chromeVisible = false
        }
    }
    // Chrome show/hide: fade only (see the top bar below for why there is no slide).
    val chromeEnter = fadeIn(Motion.enter(Motion.MEDIUM))
    val chromeExit = fadeOut(Motion.exit(Motion.MEDIUM))

    var showTags by remember { mutableStateOf(false) }
    // "Video sopra il pannello" (portrait): the info panel's top edge in px while open (-1 = closed);
    // the pager shrinks into the space above it and the media keeps playing.
    val portraitNow = androidx.compose.ui.platform.LocalConfiguration.current.orientation !=
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val splitDetails = playback.splitDetails && portraitNow
    var sheetTopPx by remember { mutableFloatStateOf(-1f) }
    // The split panel lives inside the viewer (not in a window above it): the media above it is
    // laid out into the free space, controls included, and stays touchable.
    val splitPanel = com.cripta.app.ui.components.rememberSplitPanelState()
    val splitDensity = androidx.compose.ui.platform.LocalDensity.current
    // Clear of the status bar / camera at the top, and a gap above the panel.
    val splitTopPx = with(splitDensity) {
        maxOf(
            WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding(),
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
        ).toPx()
    }
    val splitGapPx = with(splitDensity) { 16.dp.toPx() }
    // Split view: no title bar, quick tags, filmstrip or hint over the media while it is open (the
    // player's own controls do show, laid out in the space above the panel).
    val splitOpen = splitDetails && showTags
    // Regular details sheet (not the split view): the video's controls are switched off while it
    // is open and for a moment after it closes. The swipe, the sheet's window taking and giving back
    // focus and the layout settling made the player bring them up on opening and on closing.
    var holdControls by remember { mutableStateOf(false) }
    LaunchedEffect(showTags, splitDetails) {
        if (showTags && !splitDetails) { holdControls = true; chromeVisible = false }
        else if (holdControls) { delay(600); holdControls = false }
    }
    // Bumped each time the top chrome hides: the quick-tag bar re-sorts its recent tags only then.
    var recentsEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(chromeVisible) { if (!chromeVisible) recentsEpoch++ }
    // Bumped when the details panel closes: the player hides its controls for good (see VideoPlayer).
    var hideControlsTick by remember { mutableIntStateOf(0) }
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
    // Swipe down: closes the split panel when it is open, the viewer otherwise. Reads the state
    // when called (gesture handlers keep the lambda they started with).
    val splitDetailsNow by androidx.compose.runtime.rememberUpdatedState(splitDetails)
    val closeOrLeaveSplit: () -> Unit = { if (splitDetailsNow && showTags) splitPanel.close() else closeOnce() }
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
    // Same for the bottom chrome (filmstrip + handle): the "Prossimo" card sits right above it.
    var bottomChromeH by remember { mutableStateOf(0.dp) }
    // Distance of the player's seek bar top from the bottom edge, reported by the player (it
    // changes with orientation and font size): the filmstrip and the details hint sit above it.
    var seekClear by remember { mutableStateOf(84.dp) }
    val chromeDensity = androidx.compose.ui.platform.LocalDensity.current
    // Set while a one-finger gesture has turned out to be vertical (swipe up for details, down to
    // close, scrolling a note/PDF): the pager is frozen until the finger lifts, so the small sideways
    // drift of a swipe up no longer flings to the next video.
    var verticalLock by remember { mutableStateOf(false) }

    // One place for the details panel's wiring: the regular sheet (after the Box) and the split
    // view's panel (inside it, so it is part of the screen) both use it.
    val detailsPanel: @Composable (FileEntity, Boolean) -> Unit = { file, split ->
        // By the codec, not only the file type (an .mp4 with AV1 inside needs it too).
        val convertible by produceState(
            initialValue = com.cripta.app.data.VaultRepository.isVideo(file.mimeType) && file.mimeType != "video/mp4",
            file.id,
        ) { value = vm.canConvertToMp4(file) }
        DetailsSheet(
            file = file,
            allTags = allTags,
            refresh = refresh,
            showRecents = displayPrefs.showRecentTags,
            vm = vm,
            // Back to the media, not to its controls: the swipe that opened the panel had shown them.
            onDismiss = { showTags = false; chromeVisible = false; hideControlsTick++ },
            split = split,
            splitPanel = splitPanel,
            onSheetTop = { sheetTopPx = it },
            // The file's actions: the panel is their only home (the top bar carries just the name).
            actions = SheetActions(
                onFavorite = { vm.toggleFavorite(file) },
                onEditNote = if (com.cripta.app.data.VaultRepository.isNote(file.mimeType)) ({ onEditNote(file.id) }) else null,
                onExport = { confirmDownload = true },
                onConvert = if (convertible) ({ confirmConvert = true }) else null,
                onDelete = { confirmDelete = true },
            ),
        )
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // Seen in the Initial pass, before the pager: decides the gesture's axis as soon as it
            // passes the touch slop. Nothing is consumed, so the page below still gets every touch.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val slop = viewConfiguration.touchSlop
                    var dx = 0f; var dy = 0f
                    try {
                        while (true) {
                            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            dx += ch.position.x - ch.previousPosition.x
                            dy += ch.position.y - ch.previousPosition.y
                            val ax = kotlin.math.abs(dx); val ay = kotlin.math.abs(dy)
                            // Horizontal first: a real page swipe, leave it to the pager.
                            if (ax > slop && ax >= ay) break
                            if (ay > slop && ay > 1.5f * ax) { verticalLock = true; break }
                        }
                        // Locked: hold it until every finger is up.
                        if (verticalLock) {
                            while (true) {
                                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                if (ev.changes.none { it.pressed }) break
                            }
                        }
                    } finally {
                        verticalLock = false
                    }
                }
            }
            // Swipe up (not consumed by the page: photos at 1x, notes…) opens the details panel,
            // swipe down closes the viewer.
            // Videos consume their touches in the player, so they use the handle below instead.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Final)
                    // Split view: a gesture that starts on the panel belongs to the panel (scroll,
                    // drag to close), never to the viewer (close, change file, details).
                    val panelTop = sheetTopPx
                    if (panelTop > 0f && down.position.y >= panelTop) return@awaitEachGesture
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
                    if (valid && dy < -size.height * 0.12f && kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) showTags = true
                    // Swipe down closes the viewer.
                    if (valid && vm.playback.value.swipeToClose && dy > size.height * 0.15f && kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) closeOrLeaveSplit()
                    // A sideways swipe nobody took (it started on the title bar or on the tag bar,
                    // which sit above the pager and keep it from seeing the gesture): change file
                    // here, as the pager would have.
                    if (valid && kotlin.math.abs(dx) > size.width * 0.2f && kotlin.math.abs(dx) > 2 * kotlin.math.abs(dy)) {
                        val target = (pagerState.currentPage + if (dx < 0) 1 else -1).coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                        if (target != pagerState.currentPage) pagerScope.launch { pagerState.animateScrollToPage(target) }
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().layout { measurable, constraints ->
                // Split view: the pages are laid out in the space above the panel (full width, the
                // height left), so a video fits itself there and its controls span that whole area.
                // Read at layout time: following the panel costs a relayout, not a recomposition.
                val top = sheetTopPx
                val fullH = constraints.maxHeight.toFloat()
                if (top <= 0f || !constraints.hasBoundedHeight) {
                    val p = measurable.measure(constraints)
                    layout(p.width, p.height) { p.place(0, 0) }
                } else {
                    // The margins (top inset, gap above the panel) come in over the first third of
                    // the panel's rise, so nothing jumps when it starts or finishes moving.
                    val f = ((fullH - top) / (fullH / 3f)).coerceIn(0f, 1f)
                    val y = (f * splitTopPx).toInt()
                    val h = (top - f * (splitTopPx + splitGapPx)).coerceIn(fullH * 0.12f, fullH).toInt()
                    val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, y) }
                }
            },
            userScrollEnabled = !verticalLock,
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
                onClose = closeOrLeaveSplit,
                nextId = ids.getOrNull(page + 1),
                // Split view: no top bar or filmstrip over the media, so nothing to keep clear of.
                topInset = if (splitOpen) 0.dp else topChromeH,
                bottomInset = if (splitOpen) 0.dp else bottomChromeH,
                onSeekClear = { seekClear = it },
                pageShift = { (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction },
                onNext = { pagerScope.launch { pagerState.animateScrollToPage(page + 1) } },
                controlsTimeoutMs = chromeTimeoutMs.toInt(),
                onPausedChanged = { videoPaused = it },
                hideControlsTick = hideControlsTick,
                // Split view: the player runs its controls on its own (tap / its timer). Kept in
                // sync with the viewer's chrome both ways, the two kept re-showing and re-hiding
                // each other in the resized player: an endless blink.
                selfManagedControls = splitOpen,
                controlsHeld = holdControls,
            )
        }

        val inPip by com.cripta.app.viewer.PipController.inPip.collectAsState()
        // Bottom chrome: filmstrip of nearby files + the handle that opens the details panel.
        val scope = rememberCoroutineScope()
        val onVideo = currentFile?.let { com.cripta.app.data.VaultRepository.isVideo(it.mimeType) } == true
        // Photos, notes and PDFs don't follow a video's forced orientation (unless the user locked it).
        LaunchedEffect(currentFile?.id, onVideo) {
            if (currentFile != null && !onVideo && !vm.rotationLocked.value) {
                (ctx as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val showStrip = ids.size > 1 && playback.filmstrip
        val pick: (Int) -> Unit = { i -> chromeTouch++; scope.launch { pagerState.scrollToPage(i) } }
        // Also a button: the way in where a swipe up can't be used (a note or PDF that scrolls, TalkBack).
        val detailsHint: @Composable () -> Unit = {
            Row(
                Modifier.padding(top = 4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClickLabel = "Apri etichette, dettagli e azioni") { chromeTouch++; showTags = true }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ExpandLess, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Text(" Scorri su per i dettagli", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (landscape) {
            // Landscape: the strip runs down the left edge, where a 16:9 video leaves a black band,
            // so it never sits over the picture. The swipe-up hint sits alone above the seek bar.
            AnimatedVisibility(
                visible = chromeVisible && !inPip && !splitOpen,
                enter = chromeEnter,
                exit = chromeExit,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .then(if (onVideo) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility))
                    .padding(bottom = if (onVideo) seekClear + 6.dp else 16.dp),
            ) {
                detailsHint()
            }
            AnimatedVisibility(
                visible = chromeVisible && !inPip && !splitOpen && showStrip,
                enter = chromeEnter,
                exit = chromeExit,
                // Between the top chrome and the seek bar, clear of the side camera.
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 8.dp + com.cripta.app.ui.LocalSideCutout.current.start,
                        top = maxOf(104.dp, topChromeH + 8.dp), bottom = 88.dp),
            ) {
                Filmstrip(ids, pagerState.currentPage, vm, playback, vertical = true, onPick = pick)
            }
        } else {
            AnimatedVisibility(
                visible = chromeVisible && !inPip && !splitOpen,
                enter = chromeEnter,
                exit = chromeExit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    // Just above the player's seek bar for videos. Elsewhere clear of the system
                    // navigation bar / gesture handle, even while it is hidden (immersive): swiping it
                    // back in must not cover the hint.
                    .then(if (onVideo) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility))
                    .padding(bottom = if (onVideo) seekClear + 6.dp else 16.dp),
            ) {
                Column(
                    Modifier.onGloballyPositioned { bottomChromeH = with(chromeDensity) { it.size.height.toDp() } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showStrip) Filmstrip(ids, pagerState.currentPage, vm, playback, swipeLocked = verticalLock, onPick = pick)
                    detailsHint()
                }
            }
        }
        AnimatedVisibility(
            visible = chromeVisible && !inPip && !splitOpen,
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
                // No actions here: they all live in the details panel (swipe up), so the bar only
                // carries the name, which gets the whole width and stays readable.
            )
            // Quick tags, one tap toggles them on the file on screen. With "Etichette recenti" on:
            // pinned + the last ones used. Off: pinned first, then every other tag in library order
            // (the bar scrolls sideways), so any tag is one tap away without opening the panel.
            val qf = currentFile
            // Shown as usual; hidden only while the split view is open (the whole top chrome is),
            // since the panel below carries the tags then.
            if (displayPrefs.viewerQuickTags && qf != null) {
                val recentN = displayPrefs.recentTagsCount
                val withRecents = displayPrefs.showRecentTags
                // The recent ones keep their order while the bar is on screen (a tap marks a tag as
                // just used: re-sorting at once moved the chips under the finger). The order is
                // refreshed while the bar is hidden, so it never changes in front of the user.
                val recentIds = remember(recentN, recentsEpoch, allTags.isEmpty()) {
                    allTags.filter { !it.pinned && it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }
                        .take(recentN).map { it.id }
                }
                val quick = remember(allTags, recentIds, withRecents) {
                    val byId = allTags.associateBy { it.id }
                    val pinned = allTags.filter { it.pinned }
                    val rest = if (withRecents) recentIds.mapNotNull { byId[it] }.filter { !it.pinned }
                    else allTags.filter { !it.pinned }
                    (pinned + rest).distinctBy { it.id }
                }
                val onFile by produceState(initialValue = emptyList<String>(), qf.id, refresh) { value = vm.tagNamesOf(qf.id) }
                if (quick.isNotEmpty()) {
                    // The bar scrolls sideways only when its tags overflow, and never during a
                    // vertical swipe: otherwise it took every swipe starting on a tag (changing
                    // file, closing), which then did nothing.
                    val quickState = androidx.compose.foundation.lazy.rememberLazyListState()
                    androidx.compose.foundation.lazy.LazyRow(
                        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)),
                        state = quickState,
                        userScrollEnabled = !verticalLock && (quickState.canScrollForward || quickState.canScrollBackward),
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
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (t.pinned) {
                                        Icon(Icons.Filled.PushPin, "Fissata", tint = Color.White,
                                            modifier = Modifier.padding(end = 4.dp).size(14.dp))
                                    }
                                    Text(
                                        (t.alias?.takeIf { it.isNotBlank() }?.let { "$it " } ?: "") + t.name,
                                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
          }
        }

        // Split view: the panel is part of the screen, the media laid out above it.
        // Not in Picture-in-Picture: the small window shows the media alone.
        if (showTags && splitDetails && !inPip) currentFile?.let { detailsPanel(it, true) }
    }

    val file = currentFile
    // Tags, details and actions together in one panel (swipe up, or tap the "Scorri su" hint). The
    // split view's panel is drawn inside the viewer's Box instead (see detailsPanel above).
    if (showTags && !splitDetails && file != null) detailsPanel(file, false)
    val trashOn by vm.trashEnabled.collectAsState()
    if (confirmDelete && file != null) {
        com.cripta.app.ui.components.CriptaAlertDialog(
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
        com.cripta.app.ui.components.CriptaAlertDialog(
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
        com.cripta.app.ui.components.CriptaAlertDialog(
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
    // Its own window too: not over the lock screen.
    if (converting && !convertInBackground && com.cripta.app.ui.components.vaultUnlocked()) {
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
                    val eta = com.cripta.app.ui.components.rememberEta(convertProgress / 100f, converting)
                    Column {
                        Text("Conversione MP4 · $convertProgress%", color = Color.White,
                            style = MaterialTheme.typography.labelLarge)
                        if (eta != null) Text(eta, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    }
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
        com.cripta.app.ui.components.CriptaAlertDialog(
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
    bottomInset: androidx.compose.ui.unit.Dp,
    onSeekClear: (androidx.compose.ui.unit.Dp) -> Unit,
    pageShift: () -> Float,
    controlsTimeoutMs: Int,
    onPausedChanged: (Boolean) -> Unit,
    hideControlsTick: Int = 0,
    selfManagedControls: Boolean = false,
    controlsHeld: Boolean = false,
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
            is ViewerState.Video -> if (isCurrent) VideoPlayer(s.file, vm, controlsVisible = chromeVisible, onControlsVisibilityChanged = setChrome, onOpenDetails = onOpenDetails, onClose = onClose, nextId = nextId, onNext = onNext, topInset = topInset, bottomInset = bottomInset, onSeekClear = onSeekClear, pageShift = pageShift, controlsTimeoutMs = controlsTimeoutMs, onPausedChanged = onPausedChanged, hideControlsTick = hideControlsTick, selfManagedControls = selfManagedControls, controlsHeld = controlsHeld)
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
    /** Height of the viewer's bottom chrome (filmstrip / handle) while it shows, 0 if none. */
    bottomInset: androidx.compose.ui.unit.Dp,
    /** Reports how far the seek bar's top is from the bottom edge. */
    onSeekClear: (androidx.compose.ui.unit.Dp) -> Unit,
    /** This page's horizontal offset from its resting place, in page widths (0 = settled). */
    pageShift: () -> Float,
    /** Controller auto-hide delay; 0 = never hide (TalkBack touch exploration). */
    controlsTimeoutMs: Int,
    /** True while paused or ended: the chrome then stays visible instead of auto-hiding. */
    onPausedChanged: (Boolean) -> Unit,
    hideControlsTick: Int = 0,
    /** Split view: no sync between the player's controls and the viewer's chrome. */
    selfManagedControls: Boolean = false,
    /** Regular details sheet open (or just closed): no player controls at all. */
    controlsHeld: Boolean = false,
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
    /** Overlay for the gesture in progress: speed "2×", brightness / volume "60%", with its icon. */
    var gestureLabel by remember { mutableStateOf<GestureHud?>(null) }
    // Seeded from the stored size, so the orientation is right from the first frame instead of
    // waiting for the decoder (the screen used to turn only after the video had started).
    var videoAspect by remember(file.id) {
        val w = file.width ?: 0; val h = file.height ?: 0
        mutableStateOf(if (w > 0 && h > 0) android.util.Rational((w.toFloat() / h).coerceIn(1f / 2.39f, 2.39f).times(1000).toInt(), 1000) else null)
    }
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

    val latestPaused by androidx.compose.runtime.rememberUpdatedState(onPausedChanged)
    DisposableEffect(file.id) {
        fun reportPaused() = latestPaused(!player.playWhenReady || player.playbackState == Player.STATE_ENDED)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                reportPaused()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = reportPaused()
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
        reportPaused()
        onDispose {
            player.removeListener(listener)
            latestPaused(false)
            if (resumeEnabled) vm.savePosition(file.id, player.currentPosition, player.duration)
            com.cripta.app.viewer.PipController.armedAspect = null
            // No orientation reset here: swiping to another video must not bounce a landscape phone
            // through portrait. The viewer resets it on a non-video page and when it closes.
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
    // Leaving the app (Home, recents, screen off) pauses the video when the option is on. ON_STOP
    // is not reached in Picture-in-Picture or while visible in split screen / free-form windows
    // (the activity is only paused there), and those are checked too in case of an early stop.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val latestPrefs by androidx.compose.runtime.rememberUpdatedState(prefs)
    DisposableEffect(lifecycleOwner, player) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && latestPrefs.pauseOnLeave &&
                activity?.isInPictureInPictureMode != true && activity?.isInMultiWindowMode != true) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // In PiP only the video shows: no controller.
    LaunchedEffect(inPip, playerViewRef, controlsHeld) {
        playerViewRef?.let { pv ->
            pv.useController = !inPip && !controlsHeld
            if (controlsHeld) pv.hideController()
        }
    }
    // One layer, not two: the ExoPlayer controls (seek bar, play/pause) follow the app chrome
    // (top bar, tags, filmstrip). The controller's own visibility changes already flow back into
    // the chrome through the visibility listener; this is the other direction, so they always
    // show and hide together — including right after opening, when they used to drift apart.
    val selfManagedNow by androidx.compose.runtime.rememberUpdatedState(selfManagedControls)
    LaunchedEffect(controlsVisible, playerViewRef, inPip, selfManagedControls) {
        val pv = playerViewRef ?: return@LaunchedEffect
        if (inPip || selfManagedControls) return@LaunchedEffect
        if (controlsVisible && !pv.isControllerFullyVisible) pv.showController()
        // Not only when fully visible: a controller still fading in must be hidden too.
        else if (!controlsVisible) pv.hideController()
    }
    // Details panel closed: back to the bare video (the swipe that opens it no longer toggles the
    // controls, see the PlayerView touch filter, so there is nothing left to catch afterwards).
    LaunchedEffect(hideControlsTick) {
        if (hideControlsTick == 0 || inPip) return@LaunchedEffect
        playerViewRef?.hideController()
    }

    // While swiping to another file only the picture slides: the seek bar, play/pause and the side
    // buttons are held in place by moving them back by the page's offset.
    var boxW by remember { mutableIntStateOf(0) }
    val holdStill = Modifier.graphicsLayer { translationX = -pageShift() * boxW }
    LaunchedEffect(playerViewRef) {
        val ctl = playerViewRef?.findViewById<View>(androidx.media3.ui.R.id.exo_controller) ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { pageShift() * boxW }.collect { ctl.translationX = -it }
    }
    // Where the seek bar is (its top, from the bottom edge), for the viewer's bottom chrome.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var seekClearHere by remember { mutableStateOf(84.dp) }
    val latestSeekClear by androidx.compose.runtime.rememberUpdatedState(onSeekClear)
    DisposableEffect(playerViewRef) {
        val pv = playerViewRef
        val bar = pv?.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
            ?: pv?.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)
        val l = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pv == null || bar == null || bar.height == 0) return@OnLayoutChangeListener
            val a = IntArray(2); val b = IntArray(2)
            pv.getLocationInWindow(a); bar.getLocationInWindow(b)
            val fromBottom = (a[1] + pv.height) - b[1]
            if (fromBottom > 0) {
                // Never below 84dp: on some devices the measured view is not the visible bar (it
                // reported ~48dp while the bar's line sat at ~70dp and the hint landed on it).
                val dp = maxOf(with(density) { fromBottom.toDp() }, 84.dp)
                seekClearHere = dp; latestSeekClear(dp)
            }
        }
        bar?.addOnLayoutChangeListener(l)
        onDispose { bar?.removeOnLayoutChangeListener(l) }
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
            .onSizeChanged { boxW = it.width }
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
                        if (dy < -h * 0.12f) onOpenDetails()
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
                // Inflated for its TextureView surface (see the layout): a SurfaceView ignored the
                // close fade and vanished all at once at the end of it.
                (android.view.LayoutInflater.from(it).inflate(com.cripta.app.R.layout.cripta_player_view, null) as PlayerView).apply {
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
                        PlayerView.ControllerVisibilityListener { vis ->
                            if (!selfManagedNow) onControlsVisibilityChanged(vis == View.VISIBLE)
                        }
                    )
                    // PlayerView shows/hides its controls on every finger lift, swipes included: the
                    // swipe up for the details (and down to close, the side brightness/volume drags,
                    // a pinch) made them pop up. Only a tap toggles them now; a lift after the finger
                    // moved past the touch slop is swallowed before PlayerView sees it.
                    val slop = android.view.ViewConfiguration.get(it).scaledTouchSlop
                    var downX = 0f; var downY = 0f; var moved = false
                    @Suppress("ClickableViewAccessibility")
                    setOnTouchListener { _, e ->
                        when (e.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; moved = false }
                            android.view.MotionEvent.ACTION_POINTER_DOWN -> moved = true
                            android.view.MotionEvent.ACTION_MOVE ->
                                if (!moved && (kotlin.math.abs(e.x - downX) > slop || kotlin.math.abs(e.y - downY) > slop)) moved = true
                        }
                        e.actionMasked == android.view.MotionEvent.ACTION_UP && moved
                    }
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
                gestureLabel = GestureHud(Icons.Filled.Speed, (if (sp % 1f == 0f) "${sp.toInt()}" else "$sp").replace('.', ',') + "×", null)
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
                        gestureLabel = GestureHud(
                            when { level < 0.34f -> Icons.Filled.BrightnessLow; level < 0.67f -> Icons.Filled.BrightnessMedium; else -> Icons.Filled.BrightnessHigh },
                            "${(level * 100).toInt()}%", level,
                        )
                    } else {
                        val max = audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 1
                        audio?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (level * max).toInt(), 0)
                        gestureLabel = GestureHud(
                            when { level <= 0f -> Icons.AutoMirrored.Filled.VolumeOff; level < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown; else -> Icons.AutoMirrored.Filled.VolumeUp },
                            "${(level * 100).toInt()}%", level,
                        )
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
            // Tap = play the next file now. In portrait, while the controls show, it sits right above
            // the filmstrip (and handle) instead of overlapping them.
            Surface(
                onClick = onNext,
                color = Color.Black.copy(alpha = 0.78f), shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomEnd).then(holdStill)
                    .padding(end = 12.dp + sideCut.end,
                        bottom = if (controlsVisible && !vLandscape && bottomInset > 0.dp) seekClearHere + 6.dp + bottomInset + 8.dp
                            else maxOf(seekBarClearance, seekClearHere) + 8.dp)
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
        gestureLabel?.let { hud ->
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (vLandscape) maxOf(168.dp, topInset + 64.dp) else maxOf(120.dp, topInset + 12.dp))) {
                Row(Modifier.padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    // Level gauge for brightness / volume: reads at a glance while the finger moves.
                    hud.level?.let { lv ->
                        Box(Modifier.padding(start = 10.dp).width(72.dp).height(4.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(lv.coerceIn(0f, 1f)).background(Color.White))
                        }
                    }
                    // Fixed width for the digits so the pill doesn't wobble as the value changes.
                    Text(hud.text, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 10.dp).widthIn(min = 44.dp))
                }
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
            // and the seek bar below on a short screen). Portrait: a column on the right edge, at the
            // top just under the title bar and quick tags (centred, it sat over the middle of the
            // picture).
            modifier = (if (vLandscape) Modifier.align(Alignment.TopEnd).padding(top = maxOf(112.dp, topInset + 8.dp))
                else Modifier.align(Alignment.TopEnd).padding(top = maxOf(topInset, 24.dp) + 12.dp))
                .then(holdStill)
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

/** What the gesture overlay shows: an icon, the value, and a 0..1 gauge (brightness / volume only). */
private data class GestureHud(val icon: ImageVector, val text: String, val level: Float?)

/**
 * Strip of the covers around the current file ([ViewerViewModel.PlaybackPrefs.stripSpan] each side),
 * centred on it; tap one to jump. When the file changes the strip glides to centre the new one while
 * it grows to full size and brightens and the previous one shrinks and dims, so the jump reads as
 * movement along the list. It can also be dragged to look further ahead. Shape, size and whether
 * portrait files keep a vertical cover come from Settings › Video.
 */
@Composable
private fun Filmstrip(
    ids: List<String>,
    current: Int,
    vm: ViewerViewModel,
    prefs: ViewerViewModel.PlaybackPrefs,
    vertical: Boolean = false,
    /** A vertical swipe is under way (see verticalLock): the horizontal strip must not take it. */
    swipeLocked: Boolean = false,
    onPick: (Int) -> Unit,
) {
    val span = prefs.stripSpan.coerceIn(1, 4)
    // (other covers, current cover) height per size step; a bit smaller down the landscape edge.
    val (thumbH, selH) = when (prefs.stripSize) {
        0 -> if (vertical) 18.dp to 22.dp else 20.dp to 26.dp
        2 -> if (vertical) 28.dp to 36.dp else 34.dp to 44.dp
        else -> if (vertical) 22.dp to 28.dp else 26.dp to 34.dp
    }
    val rect = prefs.stripShape == com.cripta.app.data.StripShape.RECT
    val baseAspect = if (rect) 16f / 9f else 1f
    val shape = if (prefs.stripShape == com.cripta.app.data.StripShape.CIRCLE) CircleShape else MaterialTheme.shapes.extraSmall
    val gap = 4.dp
    // Width/height of each file's cover, learnt as the cells load (portrait files: up to 9:16).
    val aspects = remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    fun aspectOf(id: String) = if (rect && prefs.stripTrueAspect) aspects[id] ?: baseAspect else baseAspect
    // Size of a cover along the strip: its height down the landscape edge, its width across.
    fun along(h: androidx.compose.ui.unit.Dp, aspect: Float) = if (vertical) h else h * aspect
    val full = along(selH, baseAspect) + (along(thumbH, baseAspect) + gap) * (2 * span)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current)
    val animate = com.cripta.app.ui.theme.animationsEnabled()
    LaunchedEffect(current) {
        if (listState.firstVisibleItemIndex == current && listState.firstVisibleItemScrollOffset == 0) return@LaunchedEffect
        if (animate) listState.animateScrollToItem(current) else listState.scrollToItem(current)
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        if (vertical) Modifier.heightIn(max = full) else Modifier.widthIn(max = full),
    ) {
        // Padding on both ends = half the free space, so "scroll to item i" puts item i in the middle.
        val extent = (if (vertical) maxHeight else maxWidth).coerceAtMost(full)
        val curAspect = ids.getOrNull(current)?.let { aspectOf(it) } ?: baseAspect
        val edge by animateDpAsState(((extent - along(selH, curAspect)) / 2).coerceAtLeast(0.dp),
            Motion.enter(Motion.MEDIUM), label = "stripEdge")
        val cell: @Composable (Int) -> Unit = { i ->
            val id = ids[i]
            val sel = i == current
            val h by animateDpAsState(if (sel) selH else thumbH, Motion.enter(Motion.MEDIUM), label = "stripThumb")
            val dim by animateFloatAsState(if (sel) 1f else 0.6f, Motion.enter(Motion.MEDIUM), label = "stripDim")
            val ring by animateFloatAsState(if (sel) 1f else 0f, Motion.enter(Motion.MEDIUM), label = "stripRing")
            val bmp by produceState<Bitmap?>(null, id) { value = vm.thumbOf(id) }
            if (rect && prefs.stripTrueAspect) {
                LaunchedEffect(id) {
                    val f = vm.fileById(id)
                    val w = f?.width ?: 0; val fh = f?.height ?: 0
                    // Portrait keeps its shape (down to 9:16); landscape stays a uniform 16:9.
                    aspects[id] = if (w > 0 && fh > w) (w.toFloat() / fh).coerceAtLeast(9f / 16f) else 16f / 9f
                }
            }
            val aspect by animateFloatAsState(aspectOf(id), Motion.enter(Motion.MEDIUM), label = "stripAspect")
            val shown by animateFloatAsState(if (bmp != null) 1f else 0f, Motion.enter(Motion.MEDIUM), label = "stripLoad")
            Box(
                Modifier.height(h).aspectRatio(aspect)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.5.dp, Color.White.copy(alpha = ring), shape)
                    .clickable(onClickLabel = "Apri") { onPick(i) }
                    .semantics {
                        this.contentDescription = "File ${i + 1} di ${ids.size}"
                        this.selected = sel
                    },
            ) {
                bmp?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        alpha = dim * shown, modifier = Modifier.fillMaxSize())
                }
            }
        }
        if (vertical) {
            LazyColumn(
                state = listState,
                modifier = Modifier.height(extent).width(selH * baseAspect.coerceAtLeast(1f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = edge),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { items(ids.size, key = { ids[it] }) { cell(it) } }
        } else {
            // Not during a vertical swipe: the strip sits where the swipe up for the details starts,
            // and its sideways scroll took that swipe as soon as the finger drifted a little.
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                userScrollEnabled = !swipeLocked,
                modifier = Modifier.width(extent).height(selH),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = edge),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) { items(ids.size, key = { ids[it] }) { cell(it) } }
        }
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
    actions: SheetActions? = null,
    /** Split view: the panel is laid out inside the viewer ([splitPanel]), its top edge reported. */
    split: Boolean = false,
    splitPanel: com.cripta.app.ui.components.SplitPanelState? = null,
    onSheetTop: (Float) -> Unit = {},
) {
    val onFile by produceState(initialValue = emptyList<String>(), file.id, refresh) { value = vm.tagNamesOf(file.id) }
    val isVid = com.cripta.app.data.VaultRepository.isVideo(file.mimeType)
    val videoDiag by produceState(initialValue = "", file.id, isVid) { value = if (isVid) vm.videoInfo(file) else "" }
    var editTag by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showTech by remember { mutableStateOf(false) }
    val byName = remember(allTags) { allTags.associateBy { it.name } }
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // The app's shared sheet (two-step opening, one-swipe close, Back, below the camera, full width
    // in landscape); close() slides it away before an action opens a dialog or the editor.
    val sheet = com.cripta.app.ui.components.rememberCriptaSheetState(onDismiss)
    val inSplit = split && splitPanel != null
    val close: () -> Unit = if (inSplit) ({ splitPanel!!.close() }) else sheet::close

    val thumb by produceState<Bitmap?>(initialValue = null, file.id, refresh) { value = vm.thumbOf(file.id) }
    val kind = when {
        isVid -> "Video"
        com.cripta.app.data.VaultRepository.isImage(file.mimeType) -> "Foto"
        com.cripta.app.data.VaultRepository.isNote(file.mimeType) -> "Nota"
        file.mimeType == "application/pdf" -> "PDF"
        else -> "File"
    }
    val ext = file.originalName.substringAfterLast('.', "").uppercase().takeIf { it.isNotBlank() && it.length <= 5 && it != kind }
    // Header: preview + name, date and type; then the file's actions when the top bar hands them over.
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(width = 96.dp, height = 72.dp)) {
                    val b = thumb
                    if (b != null) {
                        Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (kind) {
                                    "Video" -> Icons.Filled.Movie
                                    "Foto" -> Icons.Filled.Photo
                                    "Nota" -> Icons.Filled.Description
                                    "PDF" -> Icons.Filled.PictureAsPdf
                                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                    Text(file.originalName, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                            .format(java.util.Date(file.createdAt)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(top = 2.dp)) {
                        Text(kind + (ext?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            actions?.let { a ->
                // Actions that leave the panel (a dialog, the editor) close it first.
                val leave: (() -> Unit) -> () -> Unit = { act -> { close(); act() } }
                Row(Modifier.fillMaxWidth()) {
                    SheetActionButton(
                        if (file.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (file.isFavorite) "Preferito" else "Preferiti",
                        a.onFavorite,
                        tint = if (file.isFavorite) com.cripta.app.ui.theme.Favorite else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics {
                            stateDescription = if (file.isFavorite) "Nei preferiti" else "Non nei preferiti"
                        },
                    )
                    a.onEditNote?.let { SheetActionButton(Icons.Filled.Edit, "Modifica", leave(it), modifier = Modifier.weight(1f)) }
                    SheetActionButton(Icons.Filled.Download, "Esporta", leave(a.onExport), modifier = Modifier.weight(1f))
                    a.onConvert?.let { SheetActionButton(Icons.Filled.Transform, "Converti in MP4", leave(it), modifier = Modifier.weight(1f)) }
                    SheetActionButton(Icons.Filled.Delete, "Elimina", leave(a.onDelete),
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    // Stats-style tiles.
    val tiles: @Composable () -> Unit = {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            DetailTile("Dimensione", com.cripta.app.ui.components.formatBytes(file.sizeBytes), Modifier.weight(1f))
            com.cripta.app.ui.components.formatDuration(file.durationMs)?.let { DetailTile("Durata", it, Modifier.weight(1f)) }
            if (file.width != null && file.height != null) {
                DetailTile("Risoluzione", "${file.width}×${file.height}" +
                    (com.cripta.app.ui.vault.qualityLabel(file.width, file.height)?.takeIf { isVid }?.let { " · $it" } ?: ""),
                    Modifier.weight(1.3f))
            }
        }
    }
    val tags: @Composable () -> Unit = {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Etichette", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }) { Text("+ Nuova") }
            }
            Text("Tocca per aggiungere o togliere · tieni premuto per alias, colore e per fissarla.",
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
        }
    }
    val info: @Composable () -> Unit = {
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

    // Portrait: one scrolling column.
    val column: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) {
            header(); tiles(); tags(); HorizontalDividerCompat(); info()
        }
    }
    if (inSplit) {
        // "Video sopra il pannello": part of the viewer, always the same height, the media above.
        com.cripta.app.ui.components.SplitPanel(
            state = splitPanel!!, heightFraction = 0.62f, onDismiss = onDismiss, onTop = onSheetTop,
        ) { column() }
    } else {
        com.cripta.app.ui.components.CriptaSheet(
            onDismissRequest = onDismiss, state = sheet, wideInLandscape = true,
            // Landscape: fully open, two scrolling columns; a scroll must not close it.
            contentDragCloses = !landscape,
        ) {
            if (landscape) {
                // Two columns that scroll on their own: details on the left, tags on the right, so the
                // tags are reachable without scrolling past the details on a short screen.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                    ) {
                        header(); tiles(); HorizontalDividerCompat(); info()
                    }
                    Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                        tags()
                    }
                }
            } else column()
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

/** The viewer's file actions, shown in the details panel instead of the top bar. */
private class SheetActions(
    val onFavorite: () -> Unit,
    val onEditNote: (() -> Unit)?,
    val onExport: () -> Unit,
    val onConvert: (() -> Unit)?,
    val onDelete: () -> Unit,
)

@Composable
private fun SheetActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint)
        // Up to two lines: "Converti in MP4" reads in full instead of a cryptic short label.
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
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

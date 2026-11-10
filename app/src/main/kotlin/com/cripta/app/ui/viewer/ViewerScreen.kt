@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cripta.app.ui.viewer

import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.width
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.db.FileEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val horizontalSign = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f
    val ctx = LocalContext.current
    val message by vm.message.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val displayPrefs by vm.display.collectAsStateWithLifecycle()

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
    val playback by vm.playback.collectAsStateWithLifecycle()

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
    // Keep already-visible controls up while paused/ended; never reveal hidden controls here.
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
    // Opening or closing the details (sheet or split panel) hides the video's controls, and they stay
    // hidden until the user next touches the viewer. Until then any attempt of the player to bring
    // them back (the swipe, the sheet's window taking and giving back focus, the layout settling)
    // is undone unseen. A timed hold let a late one through: the controls hid, then came back.
    var holdControls by remember { mutableStateOf(false) }
    LaunchedEffect(showTags) { if (showTags) { holdControls = true; chromeVisible = false } }
    // Bumped each time the top chrome hides: the quick-tag bar re-sorts its recent tags only then.
    var recentsEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(chromeVisible) { if (!chromeVisible) recentsEpoch++ }
    // Bumped when the details panel closes: the player hides its controls for good (see VideoPlayer).
    var hideControlsTick by remember { mutableIntStateOf(0) }
    // The file each confirmation is about, captured when its dialog opens (null = closed). Reading
    // the live current file on confirm acted on the wrong one when auto-next (or a swipe) moved the
    // pager while the dialog was up: "Elimina" deleted the NEXT file.
    var confirmDelete by remember { mutableStateOf<FileEntity?>(null) }
    var confirmDownload by remember { mutableStateOf<FileEntity?>(null) }
    var confirmConvert by remember { mutableStateOf<FileEntity?>(null) }
    val convertedId by vm.convertedId.collectAsStateWithLifecycle()
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
            val cur = pagerState.currentPage
            // The file on screen, unless it is the one going away: removing another page (e.g. a
            // delete confirmed after auto-next moved on) must not jump the viewer to a third file.
            val keepId = vm.liveIds.getOrNull(cur)?.takeIf { it != id }
            vm.liveIds.removeAt(i)
            pagerScope.launch {
                // Resolved when it runs: a page removed before the shown one shifts its index down.
                val target = keepId?.let { vm.liveIds.indexOf(it) }?.takeIf { it >= 0 }
                    ?: i.coerceAtMost(vm.liveIds.lastIndex)
                if (target != pagerState.currentPage) pagerState.scrollToPage(target)
            }
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
            onDismiss = { showTags = false; chromeVisible = false; holdControls = true; hideControlsTick++ },
            split = split,
            splitPanel = splitPanel,
            onSheetTop = { sheetTopPx = it },
            // The file's actions: the panel is their only home (the top bar carries just the name).
            actions = SheetActions(
                onFavorite = { vm.toggleFavorite(file) },
                onEditNote = if (com.cripta.app.data.VaultRepository.isNote(file.mimeType)) ({ onEditNote(file.id) }) else null,
                onExport = { confirmDownload = file },
                onConvert = if (convertible) ({ confirmConvert = file }) else null,
                onDelete = { confirmDelete = file },
            ),
        )
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // Hidden controls are out of the accessibility tree and a tap on the content brings them
            // back: an action says the same to a screen reader, which has no such tap.
            .semantics {
                if (!chromeVisible) customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction(str(R.string.viewer_show_controls)) {
                        chromeTouch++; chromeVisible = true; true
                    },
                )
            }
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
            // The user touching the viewer (not the split panel) ends the controls' hold (see
            // holdControls): from here on a tap shows them as usual.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val panelTop = sheetTopPx
                    if (!(panelTop > 0f && down.position.y >= panelTop)) holdControls = false
                }
            }
            // Swipe up (not consumed by the page: photos at 1x, notes…) opens the details panel,
            // swipe down closes the viewer.
            // Videos consume their touches in the player, so they use the handle below instead.
            .pointerInput(horizontalSign) {
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
                        dx += ch.position.x - ch.previousPosition.x
                        dy += ch.position.y - ch.previousPosition.y
                        if (!ch.pressed) break
                        // Only a consumed MOVE means the page took the drag (pager, zoom, scroll).
                        // The lift is consumed by the page's tap detector (a photo's, a message
                        // page's) even after a drag: checking it made swipe up / down dead on photos.
                        if (ch.isConsumed) valid = false
                    }
                    if (valid && dy < -size.height * 0.12f && kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) showTags = true
                    // Swipe down closes the viewer.
                    if (valid && vm.playback.value.swipeToClose && dy > size.height * 0.15f && kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) closeOrLeaveSplit()
                    // A sideways swipe nobody took (it started on the title bar or on the tag bar,
                    // which sit above the pager and keep it from seeing the gesture): change file
                    // here, as the pager would have.
                    if (valid && kotlin.math.abs(dx) > size.width * 0.2f && kotlin.math.abs(dx) > 2 * kotlin.math.abs(dy)) {
                        val target = (pagerState.currentPage + if (dx * horizontalSign < 0) 1 else -1).coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
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
                setChrome = { if (!holdControls || !it) chromeVisible = it },
                onToggleChrome = { chromeVisible = !chromeVisible },
                onOpenDetails = { showTags = true },
                onEditNote = onEditNote,
                onClose = closeOrLeaveSplit,
                nextId = ids.getOrNull(page + 1),
                // Split view: no top bar or filmstrip over the media, so nothing to keep clear of.
                topInset = if (splitOpen) 0.dp else topChromeH,
                bottomInset = if (splitOpen) 0.dp else bottomChromeH,
                onSeekClear = { seekClear = it },
                pageShift = { ((page - pagerState.currentPage) - pagerState.currentPageOffsetFraction) * horizontalSign },
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

        val inPip by com.cripta.app.viewer.PipController.inPip.collectAsStateWithLifecycle()
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
                // DS cr-hint: a glass pill, 26dp.
                Modifier.padding(top = 4.dp).minimumInteractiveComponentSize().heightIn(min = 26.dp).clip(CircleShape).background(com.cripta.app.ui.theme.Glass)
                    .clickable(onClickLabel = str(R.string.viewer_open_panel)) { chromeTouch++; showTags = true }
                    .padding(start = 8.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CriptaIcons.ExpandLess, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(str(R.string.viewer_swipe_up), color = Color.White,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
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
                                String.format(com.cripta.app.Strings.locale, "%d / %d", pagerState.currentPage + 1, ids.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } },
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
                            // The shared tag pill, large: on = full colour + tick, off = tint on the dark glass.
                            com.cripta.app.ui.components.TagPill(
                                com.cripta.app.ui.components.tagText(t),
                                if (displayPrefs.tagColors) com.cripta.app.ui.theme.tagColor(t) else MaterialTheme.colorScheme.primary,
                                size = com.cripta.app.ui.components.TagPillSize.Large,
                                state = if (on) com.cripta.app.ui.components.TagPillState.On else com.cripta.app.ui.components.TagPillState.Off,
                                onImage = true,
                                tagIcon = t.icon,
                                leadingIcon = if (t.pinned) CriptaIcons.PushPin else null,
                                onClick = { chromeTouch++; vm.toggleTag(qf.id, t.id) },
                                onClickLabel = if (on) str(R.string.remove_short) else str(R.string.add),
                                stateLabel = if (on) str(R.string.assigned) else str(R.string.not_assigned),
                            )
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
    val trashOn by vm.trashEnabled.collectAsStateWithLifecycle()
    // Each dialog names and acts on the file captured when it opened, even if the pager moved since.
    val delTarget = confirmDelete
    if (delTarget != null) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(str(R.string.delete_file_q)) },
            text = {
                Text(if (trashOn) str(R.string.delete_file_trash, delTarget.originalName)
                    else str(R.string.delete_file_secure, delTarget.originalName))
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; val id = delTarget.id; vm.delete(id) { removePage(id) } }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
    val exportTarget = confirmDownload
    if (exportTarget != null) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmDownload = null },
            title = { Text(str(R.string.export_q)) },
            text = {
                Text(str(R.string.export_text, exportTarget.originalName, ViewerViewModel.exportFolder(exportTarget.mimeType)))
            },
            confirmButton = { TextButton(onClick = { confirmDownload = null; vm.download(exportTarget) }) { com.cripta.app.ui.components.ButtonText(str(R.string.export)) } },
            dismissButton = { TextButton(onClick = { confirmDownload = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
    val convertSettings by vm.convertSettings.collectAsStateWithLifecycle()
    val convertTarget = confirmConvert
    if (convertTarget != null && !convertSettings.first) {
        // First conversion: ask what to do with the original (and offer to remember it).
        com.cripta.app.ui.components.ConvertChoiceDialog(
            count = 1,
            trashDays = convertSettings.second,
            onConfirm = { after, rememberIt -> confirmConvert = null; vm.convertToMp4(convertTarget, after, rememberIt) },
            onDismiss = { confirmConvert = null },
        )
    } else if (convertTarget != null) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmConvert = null },
            title = { Text(str(R.string.convert_q_single)) },
            text = {
                Text(
                    str(R.string.convert_text)
                )
            },
            confirmButton = { TextButton(onClick = { confirmConvert = null; vm.convertToMp4(convertTarget) }) { com.cripta.app.ui.components.ButtonText(str(R.string.convert)) } },
            dismissButton = { TextButton(onClick = { confirmConvert = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
    if (convertedId != null) {
        val originalId by vm.convertedOriginalId.collectAsStateWithLifecycle()
        val originalName by vm.convertedOriginalName.collectAsStateWithLifecycle()
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { vm.clearConverted() },
            title = { Text(str(R.string.video_converted)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The video the question is about: it may not be the one on screen.
                    originalName?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    Text(str(R.string.video_converted_text))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // The original's page leaves the list (the viewer moves on instead of closing).
                    val orig = originalId
                    vm.resolveConvertedOriginal(delete = true)
                    if (orig != null && orig in vm.liveIds) removePage(orig)
                }) { Text(str(R.string.n_delete_original), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { vm.resolveConvertedOriginal(delete = false) }) { com.cripta.app.ui.components.ButtonText(str(R.string.keep)) } },
        )
    }
}

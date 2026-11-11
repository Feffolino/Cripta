@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cripta.app.ui.viewer

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import com.cripta.app.ui.theme.Motion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.only
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.update
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cripta.app.data.db.FileEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(
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
            // Silent until "Avvia senza audio" is read below: a slow read let the first moments
            // play aloud before the mute.
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    // User playback preferences: loop on/off and start muted.
    var resumeEnabled by remember(file.id) { mutableStateOf(true) }
    // Set once the resume seek below has been applied (or found unnecessary). A fling across a video
    // composes and disposes its player before that: saving then stored 0 and wiped the resume point.
    var resumeSettled by remember(file.id) { mutableStateOf(false) }
    LaunchedEffect(player) {
        val prefs = vm.playbackPrefs()
        player.repeatMode = if (prefs.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        // A volume gesture or key may already have brought the sound in meanwhile: kept.
        if (!prefs.muted) player.volume = 1f
        resumeEnabled = prefs.resume
        // Resume where it was left (skipping a position too close to either end).
        val pos = file.playbackPosMs ?: 0L
        val dur = file.durationMs ?: 0L
        if (prefs.resume && pos > 3_000 && (dur <= 0 || pos < dur - 5_000)) player.seekTo(pos)
        vm.takeSeek(file.id)?.let { player.seekTo(it) }
        // After seekTo the player reports the target as its position even before it is ready.
        resumeSettled = true
    }
    // Resize presets cycled by the aspect button: (resizeMode, label, videoScale). The PlayerView
    // always stays full-screen so the CONTROLS never move; zoom is applied only to the video
    // surface. "Altezza (taglio ridotto)" fills more than Adatta while cropping the sides less than
    // Riempi/Altezza piena.
    val modes = listOf<Triple<Int, String, Float>>(
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, str(R.string.fit_fit), 1f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, str(R.string.fit_height), 1.3f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, str(R.string.fit_width), 1f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, str(R.string.fit_fill), 1f),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, str(R.string.fit_stretch), 1f),
    )
    var modeIdx by remember { mutableIntStateOf(0) }
    // Pinch-to-zoom factor applied on top of the current resize mode (1x..5x). Reset when the mode
    // or the shown video changes.
    var userZoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(modeIdx, file.id) { userZoom = 1f; panX = 0f; panY = 0f }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val inPip by com.cripta.app.viewer.PipController.inPip.collectAsStateWithLifecycle()
    val prefs by vm.playback.collectAsStateWithLifecycle()

    // "Prossimo": in the last seconds of a video (loop off, option on, a next file exists) a card
    // counts down, then the viewer moves on when playback ends. The card's X cancels it.
    val autoNextOn = !prefs.loop && prefs.autoNext && nextId != null && !inPip
    var remainingMs by remember(file.id) { mutableStateOf<Long?>(null) }
    var totalMs by remember(file.id) { mutableLongStateOf(0L) }
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
        // Ended while the advance was off (in Picture-in-Picture, say): no further state change
        // comes, so the end already reached counts now.
        if (player.playbackState == Player.STATE_ENDED && autoNextOn && !nextCancelled) latestOnNext()
        onDispose { player.removeListener(l) }
    }
    val nextFile by produceState<FileEntity?>(null, nextId) { value = nextId?.let { vm.fileById(it) } }
    val nextThumb by produceState<Bitmap?>(null, nextId) { value = nextId?.let { vm.thumbOf(it) } }
    // Intro / outro: "Salta intro" / "Salta finale", or the automatic skip with its "Annulla".
    val segmentRows by remember(file.id) { vm.segments(file.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val skipSegments = remember(segmentRows) {
        segmentRows.filter { it.source != "none" && it.endMs > it.startMs }.map {
            SegmentSkipper.Segment(if (it.kind == "intro") SegmentSkipper.Kind.INTRO else SegmentSkipper.Kind.OUTRO, it.startMs, it.endMs)
        }
    }
    val skipper = remember(file.id) { SegmentSkipper() }
    var skipAction by remember(file.id) { mutableStateOf<SegmentSkipper.Action>(SegmentSkipper.Action.None) }
    var skippedChip by remember(file.id) { mutableStateOf<SegmentSkipper.Segment?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    // The details panel's intro section has no player: it reads this one's place and asks it to seek.
    LaunchedEffect(file.id) {
        while (true) {
            val d = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            val spot = ViewerViewModel.PlayerSpot(player.currentPosition, d, player.isPlaying)
            vm.playerSpots.update { it + (file.id to spot) }
            delay(200)
        }
    }
    DisposableEffect(file.id) { onDispose { vm.playerSpots.update { it - file.id } } }
    // "Momenti nel lettore": marks on the progress bar where the people show up.
    LaunchedEffect(playerViewRef, file.id) {
        val view = playerViewRef ?: return@LaunchedEffect
        vm.moments(file.id).collect { m -> view.setExtraAdGroupMarkers(m, BooleanArray(m.size)) }
    }
    LaunchedEffect(file.id) {
        vm.seekRequests.collect { r ->
            if (r.fileId != file.id) return@collect
            player.seekTo(r.positionMs)
            if (r.play) player.play()
        }
    }
    var advanceAfterChip by remember(file.id) { mutableStateOf(false) }
    fun skipSegment(s: SegmentSkipper.Segment) {
        if (s.kind == SegmentSkipper.Kind.OUTRO && nextId != null && !prefs.loop) latestOnNext()
        else player.seekTo(if (s.kind == SegmentSkipper.Kind.OUTRO && player.duration > 0) player.duration else s.endMs)
    }
    LaunchedEffect(file.id, skipSegments, prefs.introAutoSkip, prefs.outroAutoSkip, inPip) {
        while (true) {
            val a = if (inPip) SegmentSkipper.Action.None
                else skipper.at(player.currentPosition, skipSegments, scrubbing, prefs.introAutoSkip, prefs.outroAutoSkip)
            if (a is SegmentSkipper.Action.AutoSkip) {
                // Moving on to the next video closes this player, and the chip with it: the advance
                // waits a few seconds behind the chip, so "Annulla" can still be tapped.
                if (a.segment.kind == SegmentSkipper.Kind.OUTRO && nextId != null && !prefs.loop) advanceAfterChip = true
                else skipSegment(a.segment)
                skippedChip = a.segment
            }
            skipAction = a
            delay(250)
        }
    }
    LaunchedEffect(skippedChip) {
        if (skippedChip != null) {
            delay(3_000)
            // Paused behind the chip: the user stopped here, the next video does not start on its own.
            if (advanceAfterChip) { advanceAfterChip = false; if (player.playWhenReady) latestOnNext() }
            skippedChip = null
        }
    }
    val rotationLocked by vm.rotationLocked.collectAsStateWithLifecycle()
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
    // Drives the PlayerView's keepScreenOn: the display may time out on a paused or finished video.
    var playing by remember(file.id) { mutableStateOf(false) }
    var paused by remember(file.id) { mutableStateOf(false) }
    DisposableEffect(file.id) {
        fun reportPaused() {
            paused = !player.playWhenReady || player.playbackState == Player.STATE_ENDED
            latestPaused(paused)
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                reportPaused()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = reportPaused()
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
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
            // Only once the resume point was applied: otherwise the saved one is left untouched.
            if (resumeEnabled && resumeSettled) vm.savePosition(file.id, player.currentPosition, player.duration)
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
    // A lock remembered from last time (or set now) holds its orientation.
    LaunchedEffect(rotationLocked) {
        if (activity != null && rotationLocked) activity.requestedOrientation = vm.lockedOrientation
    }
    val settingsRead by vm.settingsRead.collectAsStateWithLifecycle()
    LaunchedEffect(videoAspect, prefs.autoRotate, rotationLocked, settingsRead) {
        // Wait for the stored choices: the defaults would turn the screen and then undo it.
        if (activity == null || rotationLocked || !settingsRead) return@LaunchedEffect
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
            if (event != androidx.lifecycle.Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
            val inPipNow = activity?.isInPictureInPictureMode == true
            // Stopped while still flagged as in PiP with the screen on: the user closed the PiP window
            // (Android stops the activity before reporting the PiP exit). Nothing is left on screen,
            // so always pause; with the screen off the usual option below decides.
            val interactive = activity?.getSystemService(android.os.PowerManager::class.java)?.isInteractive != false
            if (inPipNow && interactive) player.pause()
            else if (latestPrefs.pauseOnLeave && !inPipNow && activity?.isInMultiWindowMode != true) player.pause()
            // In the background Android may end the process, and then nothing is disposed: the
            // resume point is kept now, not only when the player goes away.
            if (resumeEnabled && resumeSettled) vm.savePosition(file.id, player.currentPosition, player.duration)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // The documented PiP-dismissal signal, in case the ON_STOP above saw the PiP flag already cleared:
    // PiP ended while the activity is stopped (expanding back to full screen leaves it started).
    // Collected as flows (not keyed effects): composition is paused while the activity is stopped.
    LaunchedEffect(player, lifecycleOwner) {
        var wasInPip = false
        com.cripta.app.viewer.PipController.inPip.collect { now ->
            if (wasInPip && !now &&
                !lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                player.pause()
            }
            wasInPip = now
        }
    }
    // Vault locked: always pause, whatever "pause on leave" says, so nothing plays on behind the lock
    // screen (or in the background after a lock).
    LaunchedEffect(player) {
        vm.vaultLocked.collect { locked ->
            if (!locked) return@collect
            player.pause()
            // Kept under the lock screen, the player may go only once the database is closed:
            // saved now (the save waits for the next unlock if it has to).
            if (resumeEnabled && resumeSettled) vm.savePosition(file.id, player.currentPosition, player.duration)
        }
    }
    // "Avvia senza audio" mutes the player itself, and the volume keys only move the phone's media
    // volume: a change of it (keys, or the gesture) brings the sound back, as the gesture alone did.
    DisposableEffect(player) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                val value = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0)
                if (stream == android.media.AudioManager.STREAM_MUSIC && value > 0 && player.volume == 0f) player.volume = 1f
            }
        }
        val registered = runCatching {
            androidx.core.content.ContextCompat.registerReceiver(ctx, receiver,
                android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        onDispose { if (registered) runCatching { ctx.unregisterReceiver(receiver) } }
    }
    // Picture-in-Picture play / pause: the window's own button (see [PipController.params]).
    DisposableEffect(player) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                when {
                    player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED) -> player.pause()
                    player.playbackState == Player.STATE_ENDED -> { player.seekTo(0); player.play() }
                    else -> player.play()
                }
            }
        }
        val registered = runCatching {
            androidx.core.content.ContextCompat.registerReceiver(ctx, receiver,
                android.content.IntentFilter(com.cripta.app.viewer.PipController.ACTION_PLAY_PAUSE), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        onDispose { if (registered) runCatching { ctx.unregisterReceiver(receiver) } }
    }
    // The button shows what a tap does (pause while playing), in the window already open too.
    LaunchedEffect(paused, inPip, videoAspect) {
        com.cripta.app.viewer.PipController.playing = !paused
        val aspect = com.cripta.app.viewer.PipController.armedAspect
        if (inPip && activity != null && aspect != null) runCatching {
            activity.setPictureInPictureParams(com.cripta.app.viewer.PipController.params(activity, aspect, !paused))
        }
    }
    // In PiP only the video shows: no controller.
    LaunchedEffect(inPip, playerViewRef) { playerViewRef?.useController = !inPip }
    // Held (details just opened or closed, until the next touch): the controller stays enabled, but any attempt to
    // show it is undone at once (see the visibility listener) and never reaches the viewer's chrome.
    // Switching the controller off and back on made it flash a couple of times around the sheet.
    // The controller is also made transparent meanwhile, so that an attempt undone a moment later
    // is not even seen as a flash.
    val heldNow by androidx.compose.runtime.rememberUpdatedState(controlsHeld)
    LaunchedEffect(controlsHeld, playerViewRef) {
        val pv = playerViewRef ?: return@LaunchedEffect
        val ctl = pv.findViewById<View>(androidx.media3.ui.R.id.exo_controller)
        if (controlsHeld) { ctl?.alpha = 0f; pv.hideController() } else ctl?.alpha = 1f
    }
    // One layer, not two: the ExoPlayer controls (seek bar, play/pause) follow the app chrome
    // (top bar, tags, filmstrip). The controller's own visibility changes already flow back into
    // the chrome through the visibility listener; this is the other direction, so they always
    // show and hide together — including right after opening, when they used to drift apart.
    val selfManagedNow by androidx.compose.runtime.rememberUpdatedState(selfManagedControls)
    LaunchedEffect(controlsVisible, playerViewRef, inPip, selfManagedControls, controlsHeld) {
        val pv = playerViewRef ?: return@LaunchedEffect
        if (inPip || selfManagedControls || controlsHeld) return@LaunchedEffect
        pv.syncControllerVisibility(controlsVisible)
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
        val controllerAccent = MaterialTheme.colorScheme.primary.toArgb()
        AndroidView(
            factory = {
                // Inflated for its TextureView surface (see the layout): a SurfaceView ignored the
                // close fade and vanished all at once at the end of it.
                // AndroidView attaches the player and Compose provides its dimensions.
                @android.annotation.SuppressLint("InflateParams")
                val playerView = android.view.LayoutInflater.from(it)
                    .inflate(com.cripta.app.R.layout.cripta_player_view, null) as PlayerView
                playerView.apply {
                    // XML disables auto-show and native controller animations. Media3 reports
                    // VISIBLE even as its animated hide begins; echoing that back to Compose
                    // reopened the controls during the hide, causing an endless feedback loop.
                    this.player = player
                    resizeMode = modes[modeIdx].first
                    // Let the (zoomed) video surface overflow its content frame; the outer Box clips
                    // it to the screen. Without this the zoom is cut at the original video rectangle.
                    clipChildren = false
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    // keepScreenOn follows actual playback (see update below), not always on.
                    // The viewer owns the timer; only split view uses Media3's timer (see update).
                    // Mirror the ExoPlayer controller's visibility onto the app chrome
                    // (top bar with the name + the aspect toggle) so a tap reveals both.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { vis ->
                            if (heldNow) {
                                if (vis == View.VISIBLE) post { hideController() }
                                return@ControllerVisibilityListener
                            }
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
                    // Dragging the seek bar is never an intro/outro skip moment (see SegmentSkipper).
                    (findViewById<View>(androidx.media3.ui.R.id.exo_progress) as? androidx.media3.ui.TimeBar)
                        ?.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                            override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) { scrubbing = true }
                            override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {}
                            override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) { scrubbing = false }
                        })
                    styleCriptaController(controllerAccent)
                    playerViewRef = this
                }
            },
            // The PlayerView always fills the screen so the CONTROLS never move; zoom is applied only
            // to the video surface (scaleX/scaleY), which the surrounding Box clips.
            update = { pv ->
                pv.resizeMode = modes[modeIdx].first
                pv.updateControllerTimeout(selfManagedControls, paused, controlsTimeoutMs)
                // Keep the screen on only while the video actually plays (not paused/ended/buffering).
                pv.keepScreenOn = playing
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
        var speedHoldPerformed by remember(file.id) { mutableStateOf(false) }
        // Hold on a side = 2x speed until release (the menu speed setting is restored after).
        suspend fun androidx.compose.foundation.gestures.PressGestureScope.holdForSpeed() {
            speedHoldPerformed = false
            if (!latestPrefs.holdForSpeed) return
            val job = scope.launch {
                delay(450)
                speedHoldPerformed = true
                val before = player.playbackParameters.speed
                val sp = latestPrefs.holdSpeed
                player.setPlaybackSpeed(sp)
                gestureLabel = GestureHud(CriptaIcons.Speed, java.text.NumberFormat.getInstance(com.cripta.app.Strings.locale).format(sp) + str(R.string.speed_while_held), null)
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
                        // No level of its own yet (-1, "follow the system"): start from the one
                        // the user is seeing, else a small drag jumped to 50%.
                        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: systemBrightness(ctx)
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
                            when { level < 0.34f -> CriptaIcons.BrightnessLow; level < 0.67f -> CriptaIcons.BrightnessMedium; else -> CriptaIcons.BrightnessHigh },
                            String.format(com.cripta.app.Strings.locale, "%d%%", (level * 100).toInt()), level,
                        )
                    } else {
                        val max = audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 1
                        audio?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (level * max).toInt(), 0)
                        // "Avvia senza audio" mutes the player itself: the gesture is how it comes back.
                        if (level > 0f && player.volume == 0f) player.volume = 1f
                        gestureLabel = GestureHud(
                            when { level <= 0f -> CriptaIcons.VolumeOff; level < 0.5f -> CriptaIcons.VolumeDown; else -> CriptaIcons.VolumeUp },
                            String.format(com.cripta.app.Strings.locale, "%d%%", (level * 100).toInt()), level,
                        )
                    }
                },
            )
        }
        // After a double tap, each further tap on the same side seeks one more step at once and
        // never toggles the controls (a third tap used to become a single tap and show them).
        val seekSeries = remember(file.id) { SeekTapSeries() }
        fun seekStep(side: SeekTapSeries.Side) {
            val back = side == SeekTapSeries.Side.Back
            seekBy(if (back) -stepMs else stepMs)
            seekTick++
            seekLabel = (if (back) "-" else "+") + String.format(com.cripta.app.Strings.locale, "%ds", seekSeries.steps * prefs.seekStepSec)
        }
        fun Modifier.seekZone(side: SeekTapSeries.Side): Modifier = pointerInput(stepMs) {
            detectTapGestures(
                onDoubleTap = { if (seekSeries.onDoubleTap(side, android.os.SystemClock.uptimeMillis())) seekStep(side) },
                onTap = { if (!speedHoldPerformed && !seekSeries.lastPressSeeked) toggleController() },
                // A completed speed hold must not become a single tap on release.
                onLongPress = {},
                onPress = {
                    if (seekSeries.onPress(side, android.os.SystemClock.uptimeMillis())) seekStep(side)
                    else holdForSpeed()
                },
            )
        }
        Box(
            Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).fillMaxWidth(0.3f).fillMaxHeight()
                .padding(bottom = seekBarClearance)
                .seekZone(SeekTapSeries.Side.Back)
                .sideDrag(brightness = true)
        )
        Box(
            Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopRight).fillMaxWidth(0.3f).fillMaxHeight()
                .padding(bottom = seekBarClearance)
                .seekZone(SeekTapSeries.Side.Forward)
                .sideDrag(brightness = false)
        )
        // "Prossimo" card, bottom-right above the seek bar.
        val rem = remainingMs
        val window = minOf(prefs.autoNextSec * 1000L, totalMs / 3)
        val nextCardShown = autoNextOn && !nextCancelled && rem != null && window > 0 && rem <= window
        // Bottom-right cards ("Prossimo", "Salta intro"): right above the filmstrip while the controls show in portrait.
        val cardBottom = if (controlsVisible && !vLandscape && bottomInset > 0.dp) seekClearHere + 6.dp + bottomInset + 8.dp
            else maxOf(seekBarClearance, seekClearHere) + 8.dp
        if (nextCardShown) {
            // Tap = play the next file now. In portrait, while the controls show, it sits right above
            // the filmstrip (and handle) instead of overlapping them.
            Surface(
                onClick = onNext,
                // DS cr-card-glass: stage at 78%, radius-lg.
                color = Color.Black.copy(alpha = 0.78f), shape = com.cripta.app.ui.theme.RadiusLg,
                modifier = Modifier.align(Alignment.BottomEnd).then(holdStill)
                    .padding(end = 12.dp + sideCut.end, bottom = cardBottom)
                    .widthIn(max = 300.dp),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(40.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.extraSmall)
                        .background(Color.White.copy(alpha = 0.12f))) {
                        nextThumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.padding(horizontal = 10.dp).weight(1f, fill = false)) {
                        Text(str(R.string.next_in, (rem + 999) / 1000), color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium)
                        Text(nextFile?.originalName ?: "", color = Color.White, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        com.cripta.app.ui.components.formatDuration(nextFile?.durationMs)?.let {
                            Text(it, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { nextCancelled = true }, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(CriptaIcons.Close, str(R.string.cancel_next), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        // "Salta intro" / "Salta finale"; the outro button gives way to the "Prossimo" card.
        val skipSeg = (skipAction as? SegmentSkipper.Action.Show)?.segment
        if (skipSeg != null && skippedChip == null && !(nextCardShown && skipSeg.kind == SegmentSkipper.Kind.OUTRO)) {
            Surface(
                onClick = { skipSegment(skipSeg) },
                color = Color.Black.copy(alpha = 0.78f), shape = com.cripta.app.ui.theme.RadiusLg,
                modifier = Modifier.align(Alignment.BottomEnd).then(holdStill).padding(end = 12.dp + sideCut.end, bottom = cardBottom),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CriptaIcons.SkipNext, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(if (skipSeg.kind == SegmentSkipper.Kind.INTRO) str(R.string.skip_intro) else str(R.string.skip_ending),
                        color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                    IconButton(onClick = { skipper.dismiss(skipSeg.kind); skipAction = SegmentSkipper.Action.None }, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(CriptaIcons.Close, str(R.string.hide), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        skippedChip?.let { s ->
            Surface(color = Color.Black.copy(alpha = 0.78f), shape = com.cripta.app.ui.theme.RadiusLg,
                modifier = Modifier.align(Alignment.BottomEnd).then(holdStill).padding(end = 12.dp + sideCut.end, bottom = cardBottom)) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.kind == SegmentSkipper.Kind.INTRO) str(R.string.intro_skipped) else str(R.string.ending_skipped),
                        color = Color.White, style = MaterialTheme.typography.labelLarge)
                    com.cripta.app.ui.components.TextButton(onClick = { advanceAfterChip = false; skipper.undo(s.kind); player.seekTo(s.startMs); skippedChip = null }) {
                        com.cripta.app.ui.components.ButtonText(str(R.string.cancel))
                    }
                }
            }
        }
        gestureLabel?.let { hud ->
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (vLandscape) maxOf(168.dp, topInset + 64.dp) else maxOf(120.dp, topInset + 12.dp))) {
                // DS cr-hud: 8/16 padding, an 18dp icon, the value in `label`.
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    // Level gauge for brightness / volume: reads at a glance while the finger moves.
                    hud.level?.let { lv ->
                        Box(Modifier.padding(start = 8.dp).width(72.dp).height(4.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(lv.coerceIn(0f, 1f)).background(Color.White))
                        }
                    }
                    // Fixed width for the digits so the pill doesn't wobble as the value changes.
                    Text(hud.text, color = Color.White, style = MaterialTheme.typography.labelLarge,
                        textAlign = if (hud.level != null) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.padding(start = 8.dp).then(if (hud.level != null) Modifier.widthIn(min = 40.dp) else Modifier))
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
                color = Color.Black.copy(alpha = 0.78f), shape = MaterialTheme.shapes.large,
                modifier = Modifier.clickable { showGestureHint = false },
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(str(R.string.player_gestures), color = Color.White, style = MaterialTheme.typography.titleSmall)
                    val lines = buildList {
                        add(str(R.string.gesture_double_tap, prefs.seekStepSec))
                        add(str(R.string.gesture_pinch))
                        if (prefs.holdForSpeed) add(str(R.string.gesture_hold_speed, java.text.NumberFormat.getInstance(com.cripta.app.Strings.locale).format(prefs.holdSpeed)))
                        if (prefs.gestures) add(str(R.string.gesture_brightness))
                        if (prefs.volumeGesture) add(str(R.string.gesture_volume))
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
            // Never in Picture-in-Picture: only the video shows there.
            visible = controlsVisible && !inPip,
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
                Surface(color = com.cripta.app.ui.theme.Glass, shape = CircleShape) {
                    // Names the current mode and the one a tap switches to (TalkBack read a fixed
                    // "Adatta/riempi" across all five modes).
                    IconButton(onClick = {
                        modeIdx = (modeIdx + 1) % modes.size
                        modeTick++
                        modeLabel = modes[modeIdx].second
                    }) {
                        Icon(CriptaIcons.AspectRatio,
                            str(R.string.video_format_tap, modes[modeIdx].second, modes[(modeIdx + 1) % modes.size].second),
                            tint = Color.White)
                    }
                }
                // Rotation lock: keeps the current orientation (auto-rotate resumes when unlocked).
                Surface(color = if (rotationLocked) MaterialTheme.colorScheme.primary else com.cripta.app.ui.theme.Glass, shape = CircleShape) {
                    IconButton(onClick = {
                        val lock = !rotationLocked
                        // Keeps the orientation on screen now (either way up) and remembers it as a preference.
                        val landscapeNow = activity?.resources?.configuration?.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        val orientation = if (landscapeNow) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        vm.setRotationLock(if (lock) orientation else null)
                        activity?.requestedOrientation = if (lock) orientation
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        modeTick++
                        modeLabel = if (lock) str(R.string.rotation_locked) else str(R.string.rotation_free)
                    }) {
                        Icon(if (rotationLocked) CriptaIcons.ScreenLockRotation else CriptaIcons.ScreenRotation,
                            if (rotationLocked) str(R.string.rotation_unlock) else str(R.string.rotation_lock),
                            tint = if (rotationLocked) MaterialTheme.colorScheme.onPrimary else Color.White)
                    }
                }
                // Picture-in-Picture on demand (the automatic one on leaving the app is a setting).
                if (activity != null &&
                    activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    Surface(color = com.cripta.app.ui.theme.Glass, shape = CircleShape) {
                        IconButton(onClick = {
                            runCatching {
                                activity.enterPictureInPictureMode(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAspectRatio(videoAspect ?: android.util.Rational(16, 9)).build()
                                )
                            }.onFailure { modeLabel = str(R.string.pip_unavailable) }
                        }) {
                            Icon(CriptaIcons.PictureInPictureAlt, "Picture-in-Picture", tint = Color.White)
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
 * The phone's own screen brightness as a window level 0..1 (what a window with no level of its
 * own shows). The setting's scale is the system's (255 on most phones, more on some): an unknown
 * one gives the middle rather than a jump to full.
 */
private fun systemBrightness(ctx: android.content.Context): Float = runCatching {
    val v = android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
    val res = android.content.res.Resources.getSystem()
    val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
    val max = (if (id != 0) res.getInteger(id) else 255).coerceAtLeast(1)
    if (v in 0..max) v.toFloat() / max else 0.5f
}.getOrDefault(0.5f)

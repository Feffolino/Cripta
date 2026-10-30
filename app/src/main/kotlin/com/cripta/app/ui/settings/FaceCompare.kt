package com.cripta.app.ui.settings

import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.face.FaceRepository
import com.cripta.app.ui.components.MediaThumb
import com.cripta.app.viewer.EncryptedDataSource

/**
 * A small buffer for a preview player. The default one keeps up to ~130 MB of video in the Java
 * heap per player (it reads on past a clip's end): two side by side ran the app out of memory.
 */
@OptIn(UnstableApi::class)
internal fun smallBuffer(maxMs: Int = 4_000, bytes: Int = 8 shl 20) = androidx.media3.exoplayer.DefaultLoadControl.Builder()
    .setBufferDurationsMs(minOf(1_500, maxMs), maxMs, 500, minOf(1_000, maxMs))
    .setTargetBufferBytes(bytes)
    .setPrioritizeTimeOverSizeThresholds(false)
    .build()

/** The face's place in its picture (fractions: x, y, w, h), or null for older scans. */
private fun TrackCard.boxOrNull(): FloatArray? =
    box.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }?.toFloatArray()

/**
 * One moment of a face: for a video, the three seconds around it playing in a loop, silent; for a
 * photo, the photo. [zoom]: on the face (when its place is known), else the whole picture.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MomentClip(file: FileEntity, card: TrackCard, zoom: Boolean, vm: SettingsViewModel, modifier: Modifier = Modifier) {
    val box = card.boxOrNull()
    Box(modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Color.Black), contentAlignment = Alignment.Center) {
        if (!VaultRepository.isVideo(file.mimeType)) {
            if (zoom) {
                val bmp by produceState<android.graphics.Bitmap?>(null, card.id) { value = vm.faceCrop(card.id) }
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            } else MediaThumb(file, { vm.thumbOf(it) }, Modifier.fillMaxSize(), showFavoriteBadge = false)
            return@Box
        }
        val ctx = LocalContext.current
        var size by remember(file.id) { mutableStateOf<VideoSize?>(null) }
        val player = remember(file.id, card.id) {
            ExoPlayer.Builder(ctx).setLoadControl(smallBuffer()).build().apply {
                val source = ProgressiveMediaSource.Factory(
                    EncryptedDataSource.Factory(channelProvider = { vm.channelFor(file) }, plaintextLength = file.sizeBytes),
                    DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
                ).createMediaSource(MediaItem.fromUri("cripta://${file.id}"))
                val at = card.timeMs
                setMediaSource(ClippingMediaSource(source, maxOf(0L, at - 1_500) * 1000, (at + 1_500) * 1000))
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) { if (videoSize.width > 0) size = videoSize }
                })
                prepare()
                playWhenReady = true
            }
        }
        // The surface goes first: a player left without one decodes into Java buffers (out of memory).
        DisposableEffect(player) { onDispose { player.clearVideoSurface(); player.release() } }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner, player) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() else if (e == Lifecycle.Event.ON_START) player.play() }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        AndroidView(
            factory = {
                TextureView(it).also { v ->
                    player.setVideoTextureView(v)
                    // The view gets its size after the first update: apply the framing again then.
                    v.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> (view.tag as? Runnable)?.run() }
                }
            },
            update = { v ->
                val apply = Runnable { size?.let { s -> if (v.width > 0) v.setTransform(frame(v.width.toFloat(), s, if (zoom) box else null)) } }
                v.tag = apply
                apply.run()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The texture view stretches the video over its square; this maps it back: the face (1.8 times its
 * size) or the whole frame, centred, aspect kept.
 */
private fun frame(side: Float, s: VideoSize, box: FloatArray?): Matrix {
    val vw = s.width * s.pixelWidthHeightRatio; val vh = s.height.toFloat()
    val (cx, cy, span) = if (box != null) Triple((box[0] + box[2] / 2) * vw, (box[1] + box[3] / 2) * vh, maxOf(box[2] * vw, box[3] * vh) * 1.8f)
        else Triple(vw / 2, vh / 2, maxOf(vw, vh))
    val k = side / span
    return Matrix().apply { setScale(vw * k / side, vh * k / side); postTranslate(side / 2 - cx * k, side / 2 - cy * k) }
}

/** Two moments side by side, each with its caption, zoomed on the face or showing the scene. */
@Composable
internal fun FacePair(a: TrackCard, b: TrackCard, captionA: String, captionB: String, o: FaceRepository.Overview, vm: SettingsViewModel,
                      onOpen: (TrackCard) -> Unit) {
    var zoom by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for ((card, caption) in listOf(a to captionA, b to captionB)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val f = o.files[card.fileId]
                    // One clip per face: its player and its view come and go together (the view of
                    // the first face kept showing it, and the next players had nowhere to draw).
                    if (f != null) key(card.id) {
                        MomentClip(f, card, zoom, vm, Modifier.fillMaxWidth().clickable(onClickLabel = "Apri il media") { onOpen(card) })
                    }
                    Text(caption, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = zoom, onClick = { zoom = true }, label = { Text("Volto") })
            FilterChip(selected = !zoom, onClick = { zoom = false }, label = { Text("Scena") })
        }
    }
}

private fun clockOf(ms: Long) = (ms / 1000).let { s -> "%d:%02d".format(s / 60, s % 60) }

/**
 * "Da controllare": one question at a time, every kind of doubt in one place (a face that may be
 * someone, two people that may be one), the most likely first, answered with a tap.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckPage(o: FaceRepository.Overview, vm: SettingsViewModel, personId: Long?, onClose: () -> Unit, onOpenFile: (String) -> Unit) {
    // A snapshot: answering changes the people, the queue keeps its order.
    val queue by produceState<List<FaceRepository.Check>?>(null, personId) { value = vm.faceChecks(personId) }
    var at by rememberSaveable { mutableStateOf(0) }
    val byId = remember(o) { o.people.associateBy { it.id } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            val q = queue
            TopAppBar(title = { Text(if (q != null && at < q.size) "Da controllare · ${at + 1} di ${q.size}" else "Da controllare") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Chiudi") } })
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val q = queue
                if (q == null) { Text("Ricerca dei dubbi…"); return@Column }
                if (at >= q.size) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Text(if (q.isEmpty()) "Niente da controllare." else "Tutto controllato.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onClose) { Text("Chiudi") }
                    return@Column
                }
                val c = q[at]
                val person = byId[c.personId]
                val other = c.otherId?.let { byId[it] }
                fun caption(t: TrackCard, who: String) =
                    who + (o.files[t.fileId]?.takeIf { VaultRepository.isVideo(it.mimeType) }?.let { " · " + clockOf(t.timeMs) } ?: "")
                Text(if (other != null) "Sono la stessa persona?" else "È ${person?.title ?: FaceRepository.UNNAMED}?",
                    style = MaterialTheme.typography.headlineSmall)
                FacePair(c.a, c.b, caption(c.a, other?.title ?: "Questo volto"), caption(c.b, person?.title ?: FaceRepository.UNNAMED), o, vm) { t ->
                    vm.openPersonMedia(listOf(t.fileId), t.fileId, t.timeMs); onOpenFile(t.fileId)
                }
                Text("Somiglianza %.2f".format(c.similarity), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.heightIn(min = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.answerCheck(c, same = false); at++ }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Diverse") }
                    TextButton(onClick = { at++ }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Non so") }
                    Button(onClick = { vm.answerCheck(c, same = true); at++ }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Stessa") }
                }
            }
        }
    }
}

/** Two media chosen on a person's page, their faces side by side. */
@Composable
internal fun CompareDialog(a: TrackCard, b: TrackCard, o: FaceRepository.Overview, vm: SettingsViewModel, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose, title = { Text("Confronto") },
        text = { FacePair(a, b, clockOf(a.timeMs), clockOf(b.timeMs), o, vm) {} },
        confirmButton = { TextButton(onClick = onClose) { Text("Chiudi") } })
}

package com.cripta.app.ui.vault

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.cripta.app.data.db.FileEntity
import com.cripta.app.ui.components.OnImageGlass
import com.cripta.app.ui.components.formatCount
import com.cripta.app.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** Side of a cover in the move-drag stack. */
private val CoverSize = 76.dp
private val CoverHalf = CoverSize / 2
/** Gap between the finger and the stack's bottom edge. */
private val AboveFinger = 34.dp
/** Ease-in for exits (the covers leaving into a folder). */
private val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

/** A selected cell's cover flying from its slot to the finger when a move drag picks it up. */
internal class FlyingCover(val file: FileEntity, val from: Rect, val delay: Int, /** Stack slot it becomes, or -1: fades into the stack. */ val slot: Int) {
    val t = Animatable(0f)
}

/** Where one cover is drawn this frame. */
private class Pose(val center: Offset, val rotation: Float, val scale: Float, val alpha: Float)

/**
 * The visual layer of a move drag: the stack of covers that follows the finger, the pick-up
 * flight, the drop into a folder and the way back on cancel. Everything that moves per frame is
 * snapshot state read in draw/layout (graphicsLayer, placement), never in composition, so the grid
 * does not recompose while the finger moves.
 */
@Stable
internal class MoveDragFx {
    enum class Phase { Hold, Drop, Cancel }

    var visible by mutableStateOf(false); private set
    var phase by mutableStateOf(Phase.Hold); private set
    /** Stack covers, the anchor first (top). */
    var covers by mutableStateOf<List<FileEntity>>(emptyList()); private set
    var flyers by mutableStateOf<List<FlyingCover>>(emptyList()); private set
    /** Centre of the top cover, in the drag area's coordinates (follows the finger with a short lag). */
    var pos by mutableStateOf(Offset.Zero)
    /** Lean with the finger's sideways speed, degrees. */
    var tilt by mutableFloatStateOf(0f)
    /** Lean towards a hovered folder: -1 left, 1 right. */
    var leanSide by mutableFloatStateOf(0f)
    val form = Animatable(0f)
    val badge = Animatable(0f)
    val ring = Animatable(0f)
    val hover = Animatable(0f)
    val leave = Animatable(0f)
    var leaveTo by mutableStateOf<Offset?>(null); private set
    var dropTo by mutableStateOf<Offset?>(null); private set
    var drops by mutableStateOf<List<Animatable<Float, AnimationVector1D>>>(emptyList()); private set
    private var job: Job? = null
    /** Bumped by [start]: an older drop/cancel finishing late must not hide the new stack. */
    private var generation = 0

    fun start(scope: CoroutineScope, anchor: FileEntity, anchorCover: Rect, others: List<Pair<FileEntity, Rect>>, animate: Boolean) {
        job?.cancel()
        generation++
        val fly = others.take(3)
        covers = listOf(anchor) + fly.take(2).map { it.first }
        flyers = fly.mapIndexed { i, (f, r) -> FlyingCover(f, r, i * 70, if (i < 2) i + 1 else -1) }
        pos = anchorCover.center
        tilt = 0f; leanSide = 0f; leaveTo = null; dropTo = null; drops = emptyList()
        phase = Phase.Hold
        visible = true
        job = scope.launch {
            form.snapTo(0f); badge.snapTo(0f); ring.snapTo(0f); hover.snapTo(0f); leave.snapTo(0f)
            if (!animate) {
                form.snapTo(1f); badge.snapTo(1f); ring.snapTo(1f)
                flyers.forEach { it.t.snapTo(1f) }
                return@launch
            }
            launch { ring.animateTo(1f, Motion.enter(Motion.LONG)) }
            launch { form.animateTo(1f, Motion.enter(Motion.MEDIUM)) }
            flyers.forEach { f -> launch { f.t.animateTo(1f, tween(Motion.MEDIUM, f.delay, Motion.EaseOutQuart)) } }
            // The count pops in once the covers have landed.
            val landed = if (flyers.isEmpty()) Motion.MEDIUM else flyers.last().delay + Motion.MEDIUM
            badge.animateTo(1f, tween(300, landed, Motion.EaseOutQuart))
        }
    }

    /** Released over nothing: the stack goes back to [to] (the anchor's cell, null when it is not on screen) and fades. */
    fun cancel(scope: CoroutineScope, to: Offset?, animate: Boolean, onEnd: () -> Unit = {}) {
        if (!visible || phase != Phase.Hold) { onEnd(); return }
        phase = Phase.Cancel
        leaveTo = to
        job?.cancel()
        val gen = generation
        job = scope.launch {
            try {
                if (animate) leave.animateTo(1f, Motion.enter(Motion.LONG))
            } finally {
                if (gen == generation) visible = false
                onEnd()
            }
        }
    }

    /**
     * Released on a folder at [to] (null: a folder with no tile on screen, the stack just fades).
     * The covers fly in one after another; [onArrive] gets each landing (index, total), [onDone]
     * runs once all have landed (or at once without animations).
     */
    fun drop(scope: CoroutineScope, to: Offset?, animate: Boolean, onArrive: (Int, Int) -> Unit, onDone: () -> Unit) {
        if (!visible || phase != Phase.Hold) { onDone(); return }
        phase = Phase.Drop
        dropTo = to
        job?.cancel()
        // Only the covers already on the stack fly; one still on its way just fades.
        val onStack = covers.indices.filter { it == 0 || flyers.getOrNull(it - 1)?.t?.value == 1f }
        drops = covers.map { Animatable(0f) }
        val gen = generation
        job = scope.launch {
            try {
                if (!animate) {
                    onStack.indices.forEach { onArrive(it, onStack.size) }
                    return@launch
                }
                launch { badge.animateTo(0f, Motion.exit(Motion.SHORT)) }
                onStack.mapIndexed { k, i ->
                    launch {
                        drops[i].animateTo(1f, tween(450, k * 90, EaseInCubic))
                        onArrive(k, onStack.size)
                    }
                }.joinAll()
            } finally {
                if (gen == generation) visible = false
                onDone()
            }
        }
    }

    /** Stack cover [i] this frame, in drag-area pixels. */
    private fun Density.pose(i: Int): Pose {
        val h = hover.value
        val stackScale = (0.8f + 0.2f * form.value) * (1f - 0.18f * h)
        val fan = 1f - 0.6f * h
        val fanX = when (i) { 1 -> -7.dp.toPx(); 2 -> 7.dp.toPx(); else -> 0f } * fan * stackScale
        val fanAngle = when (i) { 1 -> -8f; 2 -> 8f; else -> 0f } * fan
        var center = pos + Offset(fanX, 6.dp.toPx() * h)
        var rotation = tilt + fanAngle + leanSide * 6f * h
        var scale = stackScale
        var alpha = if (i == 0) 1f else if (flyers.getOrNull(i - 1)?.t?.value == 1f) 1f else 0f
        when (phase) {
            Phase.Hold -> Unit
            Phase.Cancel -> {
                val e = leave.value
                leaveTo?.let { center = lerp(center, it, e) }
                alpha *= 1f - e
            }
            Phase.Drop -> {
                val t = drops.getOrNull(i)?.value ?: 0f
                dropTo?.let { center = lerp(center, it, t) + Offset(0f, -30.dp.toPx() * 4f * t * (1f - t)) }
                scale = lerp(stackScale, 0.22f, t)
                rotation = lerp(rotation, 0f, t)
                // Fades over the last 200 of its 450 ms.
                alpha *= if (t < 0.555f) 1f else (1f - (t - 0.555f) / 0.445f).coerceIn(0f, 1f)
                if (dropTo == null) alpha *= 1f - t
            }
        }
        return Pose(center, rotation, scale, alpha)
    }

    private fun Density.flyerPose(f: FlyingCover): Pose {
        val t = f.t.value
        val end = pose(if (f.slot > 0) f.slot else 0)
        val center = lerp(f.from.center, end.center, t) + Offset(0f, -60.dp.toPx() * 4f * t * (1f - t))
        val startScale = f.from.width / CoverSize.toPx()
        val alpha = when {
            phase != Phase.Hold || t >= 1f -> 0f
            f.slot > 0 -> 1f
            else -> (1f - (t - 0.6f) / 0.4f).coerceIn(0f, 1f)
        }
        return Pose(center, lerp(0f, end.rotation, t), lerp(startScale, end.scale, t), alpha)
    }

    /** Moves the stack one frame towards the point above [finger]: about 80 ms of lag, frame-rate independent. */
    fun Density.follow(finger: Offset, hoverAt: Offset?, dtSec: Float, animate: Boolean) {
        if (phase != Phase.Hold) return
        val target = finger - Offset(0f, AboveFinger.toPx() + CoverHalf.toPx())
        if (hoverAt != null) leanSide = sign(hoverAt.x - pos.x)
        if (!animate) { pos = target; tilt = 0f; return }
        val old = pos
        val next = old + (target - old) * (1f - exp(-dtSec / 0.045f))
        val vx = (next.x - old.x) / dtSec
        // About 12° at 1000 dp/s sideways, held within ±14°, easing back to 0 when the finger rests.
        val tiltTarget = (vx / 1000.dp.toPx() * 12f).coerceIn(-14f, 14f)
        tilt += (tiltTarget - tilt) * (1f - exp(-dtSec / 0.09f))
        if (abs(tilt) < 0.01f && tiltTarget == 0f) tilt = 0f
        pos = next
    }

    @Composable
    private fun CoverTile(file: FileEntity, thumb: suspend (FileEntity) -> Bitmap?, modifier: Modifier) {
        val bmp by produceState<Bitmap?>(null, file.id) { value = thumb(file) }
        Box(
            modifier.size(CoverSize)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                .padding(2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }

    private fun Modifier.posed(density: Density, pose: Density.() -> Pose): Modifier = graphicsLayer {
        val p = density.pose()
        translationX = p.center.x - CoverHalf.toPx()
        translationY = p.center.y - CoverHalf.toPx()
        rotationZ = p.rotation
        scaleX = p.scale
        scaleY = p.scale
        alpha = p.alpha
        shadowElevation = if (p.alpha > 0f) 10.dp.toPx() else 0f
        shape = RoundedCornerShape(14.dp)
        clip = false
    }

    /**
     * Draws the move drag over the grid. [finger] and [hoverAt] (the hovered folder tile's centre)
     * are read every frame; [caption] and [count] are the stack's texts.
     */
    @Composable
    fun Layer(
        finger: () -> Offset,
        hoverAt: () -> Offset?,
        hovering: Boolean,
        caption: String,
        count: Int,
        thumb: suspend (FileEntity) -> Bitmap?,
        animate: Boolean,
        density: Density,
    ) {
        if (!visible) return
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (visible) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                with(density) { follow(finger(), hoverAt(), dt, animate) }
            }
        }
        LaunchedEffect(hovering) {
            if (animate) hover.animateTo(if (hovering) 1f else 0f, Motion.enter(Motion.MEDIUM))
            else hover.snapTo(if (hovering) 1f else 0f)
        }
        val accent = MaterialTheme.colorScheme.primary
        Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            // Touch ring under the finger as the press is recognised (1 -> 1.5, fading).
            Box(
                Modifier.size(48.dp)
                    .graphicsLayer {
                        val f = finger()
                        val r = ring.value
                        translationX = f.x - 24.dp.toPx()
                        translationY = f.y - 24.dp.toPx()
                        scaleX = 1f + 0.5f * r
                        scaleY = 1f + 0.5f * r
                        alpha = if (phase == Phase.Hold) 0.35f * (1f - r) else 0f
                    }
                    .background(accent, CircleShape),
            )
            flyers.forEach { f ->
                CoverTile(f.file, thumb, Modifier.posed(density) { flyerPose(f) })
            }
            // Bottom cover first, so the anchor's cover stays on top.
            covers.indices.reversed().forEach { i ->
                CoverTile(covers[i], thumb, Modifier.posed(density) { pose(i) })
            }
            // Count badge at the top cover's top-right corner.
            Box(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) {
                            val top = with(density) { pose(0) }
                            val corner = top.center + Offset(CoverHalf.toPx(), -CoverHalf.toPx()) * top.scale
                            p.place((corner.x - p.width / 2f).toInt(), (corner.y - p.height / 2f).toInt())
                        }
                    }
                    .graphicsLayer {
                        val b = badge.value
                        scaleX = b; scaleY = b
                        alpha = with(density) { pose(0) }.alpha
                    }
                    .heightIn(min = 26.dp).widthIn(min = 26.dp)
                    .background(accent, RoundedCornerShape(13.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(formatCount(count), color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
            // Caption under the stack, on dark glass.
            Box(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) {
                            val top = with(density) { pose(0) }
                            val y = top.center.y + CoverHalf.toPx() * top.scale + 8.dp.toPx()
                            val x = (top.center.x - p.width / 2f).coerceIn(0f, (c.maxWidth - p.width).toFloat().coerceAtLeast(0f))
                            p.place(x.toInt(), y.toInt())
                        }
                    }
                    .graphicsLayer {
                        alpha = when (phase) {
                            Phase.Hold -> form.value
                            Phase.Cancel -> 1f - leave.value
                            Phase.Drop -> (1f - (drops.firstOrNull()?.value ?: 1f) * 4f).coerceIn(0f, 1f)
                        }
                    }
                    .widthIn(max = 240.dp)
                    .background(OnImageGlass, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(caption, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Auto-scroll feedback: a thin accent band on the edge being scrolled, stronger the deeper the
 * finger is in its zone ([depth], -1 top .. 1 bottom, see [edgeDepth]); fades when scrolling stops.
 */
@Composable
internal fun BoxScope.EdgeScrollBands(depth: () -> Float) {
    val d by animateFloatAsState(depth(), Motion.enter(Motion.SHORT), label = "edgeBand")
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(4.dp)
            .graphicsLayer { alpha = (-d).coerceIn(0f, 1f) * 0.6f }
            .background(accent),
    )
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
            .drawBehind {
                val a = d.coerceIn(0f, 1f) * 0.15f
                if (a > 0f) drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = a))))
            },
    )
}

/** Cells a move drag left behind: faded, a little smaller, their slot outlined with a dash. */
internal fun Modifier.leftBehind(lifted: Boolean, outline: Color): Modifier = composed {
    val p by animateFloatAsState(if (lifted) 1f else 0f, Motion.enter(Motion.MEDIUM), label = "leftBehind")
    this
        .drawBehind {
            if (p > 0f) {
                val w = 2.dp.toPx()
                drawRoundRect(
                    color = outline.copy(alpha = outline.alpha * p),
                    topLeft = Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = w,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
        }
        .graphicsLayer {
            alpha = 1f - 0.65f * p
            val s = 1f - 0.06f * p
            scaleX = s; scaleY = s
        }
}

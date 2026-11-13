package com.cripta.app.ui.viewer

import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.cripta.app.R
import com.cripta.app.str

import com.cripta.app.ui.theme.CriptaIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import com.cripta.app.ui.components.CriptaAlertDialog
import com.cripta.app.ui.components.vaultUnlocked
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.cripta.app.ui.components.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.ui.components.ChipFlowRow
import kotlinx.coroutines.flow.map

/**
 * "Intro e finale" in the details panel, under the tags: where the video's intro and outro sit on its
 * timeline, the manual marks (from the player's current position), a fine trim of each bound, a
 * "Prova" that jumps just before the end, and the rarer actions in a menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntroSection(file: FileEntity, vm: ViewerViewModel) {
    val segments by remember(file.id) { vm.segments(file.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val spot by remember(file.id) { vm.playerSpots.map { it[file.id] } }.collectAsStateWithLifecycle(initialValue = null)
    val status by produceState<String?>(initialValue = "ok", file.id, segments) { value = vm.introStatus(file.id) }
    val pending = remember(file.id) { vm.pendingMarks(file.id) }
    // Keyed by the video: the question was opened for this one, and goes away when the viewer moves on.
    var rejecting by remember(file.id) { mutableStateOf<Pair<SegmentKind, MediaSegmentEntity>?>(null) }
    val duration = spot?.durationMs?.takeIf { it > 0 } ?: file.durationMs ?: 0L
    val position = spot?.positionMs ?: 0L
    val introColor = MaterialTheme.colorScheme.primary
    // One accent (DESIGN.md): the outro is the same blue, lighter; where it sits tells it apart too.
    val outroColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelHeading(str(R.string.intro_and_ending), str(R.string.intro_skip_hint))
        when (status) {
            null -> StatusLine(str(R.string.faces_not_analysed), str(R.string.analyse_now)) { vm.analyzeIntro(file) }
            "ok" -> {}
            else -> StatusLine(str(R.string.not_analysable_dot, status), null) {}
        }
        Timeline(
            durationMs = duration, positionMs = position,
            intro = segments.active(SegmentKind.INTRO), outro = segments.active(SegmentKind.OUTRO),
            pending = pending.values.toList(), introColor = introColor, outroColor = outroColor,
            onSeek = { vm.seekPlayer(file.id, it) },
        )
        // Nothing found or marked: one line and the two marks, instead of two empty blocks.
        if (segments.active(SegmentKind.INTRO) == null && segments.active(SegmentKind.OUTRO) == null && pending.isEmpty()) {
            if (status == "ok") Text(str(R.string.intro_none_found), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { pending[SegmentKind.INTRO] = position }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.intro_from_here, clock(position)))
                }
                FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { pending[SegmentKind.OUTRO] = position }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.ending_from_here, clock(position)))
                }
            }
        } else for (kind in SegmentKind.entries) {
            val signature by produceState<String?>(initialValue = null, file.id, segments, kind) { value = vm.signatureName(file.id, kind) }
            SegmentBlock(
                kind = kind,
                signature = signature,
                seg = segments.firstOrNull { it.kind == kind.id },
                pendingStart = pending[kind],
                positionMs = position,
                color = if (kind == SegmentKind.INTRO) introColor else outroColor,
                onMarkStart = { pending[kind] = position },
                onMarkEnd = { start -> if (vm.markEnd(file, kind, start, position)) pending.remove(kind) },
                onCancelMark = { pending.remove(kind) },
                onNudge = { ds, de -> vm.nudgeSegment(file, kind, ds, de, duration) },
                onTry = { seg -> vm.seekPlayer(file.id, maxOf(seg.startMs, seg.endMs - TRY_LEAD_MS), play = true) },
                onSearch = { seg -> vm.searchSegment(file, kind, seg) },
                onApplyToAll = { vm.applySegmentToAll(file, kind) },
                onReject = { seg -> rejecting = kind to seg },
                onRemove = { vm.removeSegment(file, kind) },
            )
        }
    }
    rejecting?.let { (kind, seg) ->
        CriptaAlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text(if (kind == SegmentKind.INTRO) str(R.string.not_an_intro) else str(R.string.not_an_ending)) },
            text = { Text(str(R.string.discard_stretch_q)) },
            confirmButton = { TextButton(onClick = { vm.rejectSegment(file, kind, seg, true); rejecting = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.in_all_videos)) } },
            dismissButton = { TextButton(onClick = { vm.rejectSegment(file, kind, seg, false); rejecting = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.only_here)) } },
        )
    }
}

/** How far before a segment's end "Prova" starts playing: enough to hear the jump land. */
private const val TRY_LEAD_MS = 3_000L

private fun List<MediaSegmentEntity>.active(kind: SegmentKind) =
    firstOrNull { it.kind == kind.id && it.source != "none" && it.endMs > it.startMs }

@Composable
private fun StatusLine(text: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { com.cripta.app.ui.components.ButtonText(action) }
    }
}

/** Taps and accessibility actions share finite, bounded seek positions. */
internal fun seekTimeline(positionMs: Double, durationMs: Long, onSeek: (Long) -> Unit): Boolean {
    if (durationMs <= 0 || !positionMs.isFinite()) return false
    onSeek(positionMs.toLong().coerceIn(0L, durationMs))
    return true
}

/**
 * The whole video as a bar: intro and outro as coloured stretches, the player's position as a line,
 * a pending mark as a tick. A tap moves the player there.
 */
@Composable
private fun Timeline(
    durationMs: Long, positionMs: Long, intro: MediaSegmentEntity?, outro: MediaSegmentEntity?,
    pending: List<Long>, introColor: Color, outroColor: Color, onSeek: (Long) -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val head = MaterialTheme.colorScheme.onSurface
    // The tap handler lives as long as the duration: two videos of one length kept the first's seek.
    val seek by androidx.compose.runtime.rememberUpdatedState(onSeek)
    val desc = buildString {
        append(str(R.string.seek_bar_desc, clock(positionMs), clock(durationMs)))
        intro?.let { append(str(R.string.seek_bar_intro, clock(it.startMs), clock(it.endMs))) }
        outro?.let { append(str(R.string.seek_bar_ending, clock(it.startMs), clock(it.endMs))) }
        append(str(R.string.seek_bar_tap))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.fillMaxWidth().height(48.dp)
                .semantics {
                    contentDescription = desc
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat(),
                        0f..durationMs.coerceAtLeast(0L).toFloat(),
                    )
                    setProgress { value -> seekTimeline(value.toDouble(), durationMs, onSeek) }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { o -> seekTimeline(o.x.toDouble() / size.width * durationMs, durationMs, seek) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                val h = 10.dp.toPx()
                val top = (size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(track, Offset(0f, top), Size(size.width, h), r)
                if (durationMs <= 0) return@Canvas
                fun x(ms: Long) = (ms.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
                for ((seg, c) in listOf(intro to introColor, outro to outroColor)) {
                    seg ?: continue
                    val x0 = x(seg.startMs); val w = maxOf(x(seg.endMs) - x0, 3.dp.toPx())
                    drawRoundRect(c, Offset(x0, top), Size(w, h), r)
                }
                for (p in pending) drawRect(head.copy(alpha = 0.6f), Offset(x(p) - 1.dp.toPx(), top - 4.dp.toPx()), Size(2.dp.toPx(), h + 8.dp.toPx()))
                val px = x(positionMs)
                drawRect(head, Offset(px - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
            }
        }
        Row {
            Text(clock(positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(clock(durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One segment: its state, the mark button, the fine trim, "Prova" and the menu of rarer actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentBlock(
    kind: SegmentKind, signature: String?, seg: MediaSegmentEntity?, pendingStart: Long?, positionMs: Long, color: Color,
    onMarkStart: () -> Unit, onMarkEnd: (Long) -> Unit, onCancelMark: () -> Unit,
    onNudge: (Long, Long) -> Unit, onTry: (MediaSegmentEntity) -> Unit,
    onSearch: (MediaSegmentEntity) -> Unit, onApplyToAll: () -> Unit,
    onReject: (MediaSegmentEntity) -> Unit, onRemove: () -> Unit,
) {
    val name = if (kind == SegmentKind.INTRO) str(R.string.intro) else str(R.string.ending)
    // Intro is feminine, finale masculine: "trovata" / "trovato".
    val a = if (kind == SegmentKind.INTRO) "a" else "o"
    val active = seg?.takeIf { it.source != "none" && it.endMs > it.startMs }
    var menu by remember { mutableStateOf(false) }
    /** The fine trim, opened with "Regola": rarely needed, so it does not crowd the block. */
    var trimming by remember(kind) { mutableStateOf(false) }
    // No tile around it: the coloured dot and the name head the block, like the panel's other sections.
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(
                        when {
                            active != null -> "${clock(active.startMs)}–${clock(active.endMs)} · " + when {
                                active.source == "auto" && active.autoStartMs != null -> str(if (a == "a") R.string.seg_found_extended_f else R.string.seg_found_extended_m)
                                active.source == "auto" -> str(if (a == "a") R.string.seg_found_f else R.string.seg_found_m)
                                active.autoStartMs != null -> str(if (a == "a") R.string.seg_moved_f else R.string.seg_moved_m, clock(active.autoStartMs), clock(active.autoEndMs ?: 0))
                                else -> str(if (a == "a") R.string.seg_marked_f else R.string.seg_marked_m)
                            }
                            seg?.source == "none" -> str(if (a == "a") R.string.seg_none_f else R.string.seg_none_m)
                            else -> str(if (a == "a") R.string.seg_not_found_f else R.string.seg_not_found_m)
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active != null && signature != null) {
                        Text(str(R.string.signature_is, signature), style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
                if (active != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(CriptaIcons.MoreVert, str(R.string.more_actions_for, name)) }
                        DropdownMenu(expanded = menu && vaultUnlocked(), onDismissRequest = { menu = false }) {
                            // Bounds moved away from the detected sound: the same move for the whole series.
                            if (active.source == "manual" && active.autoStartMs != null && active.autoEndMs != null &&
                                (active.startMs != active.autoStartMs || active.endMs != active.autoEndMs)) DropdownMenuItem(
                                text = { Text(str(R.string.apply_same_signature)) },
                                onClick = { menu = false; onApplyToAll() })
                            DropdownMenuItem(text = { Text(str(R.string.mark_again_from, clock(positionMs))) }, onClick = { menu = false; onMarkStart() })
                            DropdownMenuItem(text = { Text(str(R.string.search_other_videos)) }, onClick = { menu = false; onSearch(active) })
                            if (active.source == "auto") DropdownMenuItem(
                                text = { Text(if (kind == SegmentKind.INTRO) str(R.string.not_an_intro_more) else str(R.string.not_an_ending_more)) },
                                onClick = { menu = false; onReject(active) })
                            DropdownMenuItem(text = { Text(str(R.string.remove)) }, onClick = { menu = false; onRemove() })
                        }
                    }
                }
            }
            // The mark walks through start → end (the ✕ drops a half-made mark); a found segment
            // offers "Prova" and "Regola", marking it again is in ⋮.
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    pendingStart != null -> {
                        FilledTonalButton(onClick = { onMarkEnd(pendingStart) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            com.cripta.app.ui.components.ButtonText(str(R.string.end_here_from, clock(pendingStart)))
                        }
                        IconButton(onClick = onCancelMark) { Icon(CriptaIcons.Close, str(R.string.cancel_mark)) }
                    }
                    active == null -> FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onMarkStart) {
                        com.cripta.app.ui.components.ButtonText(str(R.string.x_from_here, name, clock(positionMs)))
                    }
                    else -> {
                        FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { onTry(active) }) {
                            Icon(CriptaIcons.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            com.cripta.app.ui.components.ButtonText(str(R.string.try_))
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { trimming = !trimming }) {
                            com.cripta.app.ui.components.ButtonText(if (trimming) str(R.string.done_adjusting) else str(R.string.adjust))
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(active != null && pendingStart == null && trimming,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 0, com.cripta.app.ui.theme.Motion.EaseOutQuart)) +
                    androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(220, 0, com.cripta.app.ui.theme.Motion.EaseOutQuart)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                    androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(120))) {
                if (active != null) Column {
                    Trim(str(R.string.start), active.startMs) { d -> onNudge(d, 0) }
                    Trim(str(R.string.end), active.endMs) { d -> onNudge(0, d) }
                }
            }
        }
    }
}

/** A bound and its fine trim: −1 s, −0,1 s, +0,1 s, +1 s. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Trim(label: String, ms: Long, onDelta: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${clockTenths(ms)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 92.dp))
        ChipFlowRow(gap = 4.dp) {
            val tenth = String.format(com.cripta.app.Strings.locale, "%.1f", 0.1)
            for ((d, t) in listOf(-1_000L to "−1 s", -100L to "−$tenth s", 100L to "+$tenth s", 1_000L to "+1 s")) {
                TextButton(onClick = { onDelta(d) }, contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.semantics { contentDescription = str(if (d < 0) R.string.trim_back else R.string.trim_forward, label, t.trimStart('−', '+').removeSuffix(" s")) }) {
                    com.cripta.app.ui.components.ButtonText(t)
                }
            }
        }
    }
}

private fun clock(ms: Long): String = com.cripta.app.ui.components.formatDuration(ms) ?: String.format(com.cripta.app.Strings.locale, "%d:%02d", 0, 0)

/** m:ss,d: the trim moves by tenths of a second. */
private fun clockTenths(ms: Long): String {
    val s = ms / 1000
    val sep = java.text.DecimalFormatSymbols.getInstance(com.cripta.app.Strings.locale).decimalSeparator
    return "%d:%02d%c%d".format(com.cripta.app.Strings.locale, s / 60, s % 60, sep, (ms % 1000) / 100)
}

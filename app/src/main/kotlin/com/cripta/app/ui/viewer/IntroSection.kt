package com.cripta.app.ui.viewer

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    val segments by remember(file.id) { vm.segments(file.id) }.collectAsState(initial = emptyList())
    val spot by remember(file.id) { vm.playerSpots.map { it[file.id] } }.collectAsState(initial = null)
    val status by produceState<String?>(initialValue = "ok", file.id, segments) { value = vm.introStatus(file.id) }
    val pending = remember(file.id) { mutableStateMapOf<SegmentKind, Long>() }
    var rejecting by remember { mutableStateOf<Pair<SegmentKind, MediaSegmentEntity>?>(null) }
    val duration = spot?.durationMs?.takeIf { it > 0 } ?: file.durationMs ?: 0L
    val position = spot?.positionMs ?: 0L
    val introColor = MaterialTheme.colorScheme.primary
    val outroColor = MaterialTheme.colorScheme.tertiary

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Intro e finale", style = MaterialTheme.typography.titleMedium)
        when (status) {
            null -> StatusLine("Non ancora analizzato.", "Analizza ora") { vm.analyzeIntro(file) }
            "ok" -> {}
            else -> StatusLine("Non analizzabile: $status.", null) {}
        }
        Timeline(
            durationMs = duration, positionMs = position,
            intro = segments.active(SegmentKind.INTRO), outro = segments.active(SegmentKind.OUTRO),
            pending = pending.values.toList(), introColor = introColor, outroColor = outroColor,
            onSeek = { vm.seekPlayer(file.id, it) },
        )
        for (kind in SegmentKind.entries) {
            SegmentBlock(
                kind = kind,
                seg = segments.firstOrNull { it.kind == kind.id },
                pendingStart = pending[kind],
                positionMs = position,
                color = if (kind == SegmentKind.INTRO) introColor else outroColor,
                onMarkStart = { pending[kind] = position },
                onMarkEnd = { start -> if (vm.markEnd(file, kind, start, position)) pending.remove(kind) },
                onCancelMark = { pending.remove(kind) },
                onNudge = { seg, ds, de -> vm.nudgeSegment(file, kind, seg, ds, de, duration) },
                onTry = { seg -> vm.seekPlayer(file.id, maxOf(seg.startMs, seg.endMs - TRY_LEAD_MS), play = true) },
                onSearch = { seg -> vm.searchSegment(file, kind, seg) },
                onReject = { seg -> rejecting = kind to seg },
                onRemove = { vm.removeSegment(file, kind) },
            )
        }
    }
    rejecting?.let { (kind, seg) ->
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text(if (kind == SegmentKind.INTRO) "Non è un'intro" else "Non è un finale") },
            text = { Text("Scartare questo tratto solo in questo video, o in tutti i video in cui compare (anche in quelli futuri)?") },
            confirmButton = { TextButton(onClick = { vm.rejectSegment(file, kind, seg, true); rejecting = null }) { Text("In tutti i video") } },
            dismissButton = { TextButton(onClick = { vm.rejectSegment(file, kind, seg, false); rejecting = null }) { Text("Solo qui") } },
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
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
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
    val desc = buildString {
        append("Barra del video, posizione ${clock(positionMs)} di ${clock(durationMs)}")
        intro?.let { append(", intro ${clock(it.startMs)}–${clock(it.endMs)}") }
        outro?.let { append(", finale ${clock(it.startMs)}–${clock(it.endMs)}") }
        append(". Tocca per spostare il video.")
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(32.dp)
                .semantics { contentDescription = desc }
                .pointerInput(durationMs) {
                    detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong().coerceIn(0L, durationMs)) }
                },
        ) {
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
    kind: SegmentKind, seg: MediaSegmentEntity?, pendingStart: Long?, positionMs: Long, color: Color,
    onMarkStart: () -> Unit, onMarkEnd: (Long) -> Unit, onCancelMark: () -> Unit,
    onNudge: (MediaSegmentEntity, Long, Long) -> Unit, onTry: (MediaSegmentEntity) -> Unit,
    onSearch: (MediaSegmentEntity) -> Unit, onReject: (MediaSegmentEntity) -> Unit, onRemove: () -> Unit,
) {
    val name = if (kind == SegmentKind.INTRO) "Intro" else "Finale"
    val active = seg?.takeIf { it.source != "none" && it.endMs > it.startMs }
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            active != null -> "${clock(active.startMs)}–${clock(active.endMs)} · " +
                                (if (active.source == "auto") "rilevata" else "segnata da te")
                            seg?.source == "none" -> "Nessuna (scelta tua)"
                            else -> "Non rilevata"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Altre azioni per $name") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Cerca negli altri video") }, onClick = { menu = false; onSearch(active) })
                            if (active.source == "auto") DropdownMenuItem(
                                text = { Text(if (kind == SegmentKind.INTRO) "Non è un'intro…" else "Non è un finale…") },
                                onClick = { menu = false; onReject(active) })
                            DropdownMenuItem(text = { Text("Rimuovi") }, onClick = { menu = false; onRemove() })
                        }
                    }
                }
            }
            // One mark button that walks through start → end; the ✕ drops a half-made mark.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                if (pendingStart != null) {
                    FilledTonalButton(onClick = { onMarkEnd(pendingStart) }, modifier = Modifier.weight(1f)) {
                        Text("Segna fine qui · inizio ${clock(pendingStart)}")
                    }
                    IconButton(onClick = onCancelMark) { Icon(Icons.Filled.Close, "Annulla il segno") }
                } else {
                    OutlinedButton(onClick = onMarkStart, modifier = Modifier.weight(1f)) {
                        Text(if (active == null) "Segna inizio qui (${clock(positionMs)})" else "Segna di nuovo da ${clock(positionMs)}")
                    }
                    if (active != null) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { onTry(active) }) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Prova")
                        }
                    }
                }
            }
            if (active != null && pendingStart == null) {
                Trim("Inizio", active.startMs) { d -> onNudge(active, d, 0) }
                Trim("Fine", active.endMs) { d -> onNudge(active, 0, d) }
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
            for ((d, t) in listOf(-1_000L to "−1 s", -100L to "−0,1", 100L to "+0,1", 1_000L to "+1 s")) {
                TextButton(onClick = { onDelta(d) }, contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.semantics { contentDescription = "$label ${if (d < 0) "indietro" else "avanti"} di ${t.trimStart('−', '+')}" }) {
                    Text(t)
                }
            }
        }
    }
}

private fun clock(ms: Long): String = com.cripta.app.ui.components.formatDuration(ms) ?: "0:00"

/** m:ss,d: the trim moves by tenths of a second. */
private fun clockTenths(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d,%d".format(s / 60, s % 60, (ms % 1000) / 100)
}

package com.cripta.app.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.ui.components.ChipFlowRow

/**
 * "Intro e finale": what the player knows about this video's intro and outro, the manual marks
 * (from the player position when tapped) and the rejection of a wrong detection.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun IntroOutroSheet(
    segments: List<MediaSegmentEntity>,
    pendingStart: Map<SegmentKind, Long>,
    onStartHere: (SegmentKind, MediaSegmentEntity?) -> Unit,
    onEndHere: (SegmentKind, MediaSegmentEntity?) -> Unit,
    onRemove: (SegmentKind) -> Unit,
    onSearch: (SegmentKind, MediaSegmentEntity) -> Unit,
    onReject: (SegmentKind, MediaSegmentEntity, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var rejecting by remember { mutableStateOf<Pair<SegmentKind, MediaSegmentEntity>?>(null) }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Intro e finale", style = MaterialTheme.typography.titleLarge)
            for (kind in SegmentKind.entries) {
                val seg = segments.firstOrNull { it.kind == kind.id }
                val name = if (kind == SegmentKind.INTRO) "Intro" else "Finale"
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    val pending = pendingStart[kind]
                    Text(
                        when {
                            pending != null -> "Inizio segnato a ${clock(pending)}: ora vai alla fine e tocca «Fine qui»."
                            seg == null -> "Non rilevato."
                            seg.source == "none" -> "Nessuno (scelta tua)."
                            else -> "${clock(seg.startMs)}–${clock(seg.endMs)} · " + if (seg.source == "auto") "rilevato" else "segnato da te"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChipFlowRow(gap = 8.dp) {
                        OutlinedButton(onClick = { onStartHere(kind, seg) }) { Text("Inizio qui") }
                        OutlinedButton(onClick = { onEndHere(kind, seg) }) { Text("Fine qui") }
                        if (seg != null && seg.source != "none") {
                            TextButton(onClick = { onSearch(kind, seg) }) { Text("Cerca negli altri video") }
                            if (seg.source == "auto") TextButton(onClick = { rejecting = kind to seg }) {
                                Text(if (kind == SegmentKind.INTRO) "Non è un'intro" else "Non è un finale")
                            } else TextButton(onClick = { onRemove(kind) }) { Text("Rimuovi") }
                        }
                    }
                }
            }
        }
    }
    rejecting?.let { (kind, seg) ->
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text(if (kind == SegmentKind.INTRO) "Non è un'intro" else "Non è un finale") },
            text = { Text("Scartare questo tratto solo in questo video, o in tutti i video in cui compare (anche in quelli futuri)?") },
            confirmButton = { TextButton(onClick = { onReject(kind, seg, true); rejecting = null }) { Text("In tutti i video") } },
            dismissButton = { TextButton(onClick = { onReject(kind, seg, false); rejecting = null }) { Text("Solo qui") } },
        )
    }
}

private fun clock(ms: Long): String = com.cripta.app.ui.components.formatDuration(ms) ?: "0:00"

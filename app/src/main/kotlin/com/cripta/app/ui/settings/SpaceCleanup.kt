package com.cripta.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cripta.app.R
import com.cripta.app.data.SpaceKind
import com.cripta.app.data.SpaceReport
import com.cripta.app.data.db.FileEntity
import com.cripta.app.plural
import com.cripta.app.str
import com.cripta.app.ui.components.CountRow
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.theme.CriptaIcons

/** The three parts of the phone's storage, in bar order. */
@Composable
private fun spaceColors(): Triple<Color, Color, Color> = Triple(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    MaterialTheme.colorScheme.surfaceContainerHighest,
)

/**
 * "Spazio usato": the whole phone storage as one bar (Cripta, everything else, free) with a
 * legend under it. Cripta's figure leads, in bold; a sliver of Cripta stays visible however small.
 */
@Composable
internal fun DeviceSpaceBar(report: SpaceReport, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val (cripta, other, free) = spaceColors()
    val description = str(R.string.space_bar_desc, formatBytes(report.vault), formatBytes(report.other),
        formatBytes(report.deviceFree), formatBytes(report.deviceTotal))
    Column(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = str(R.string.space_open_detail), onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        CountRow(str(R.string.space_used_short), formatBytes(report.vault), of = formatBytes(report.deviceTotal))
        Canvas(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            drawRect(free)
            val minW = 3.dp.toPx()
            val wC = if (report.vault > 0) maxOf(size.width * report.share(report.vault), minW) else 0f
            val wO = size.width * report.share(report.other)
            drawRect(cripta, Offset.Zero, Size(wC, size.height))
            drawRect(other, Offset(wC, 0f), Size(minOf(wO, size.width - wC), size.height))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(cripta, str(R.string.space_cripta), formatBytes(report.vault), Modifier.weight(1f))
            LegendItem(other, str(R.string.space_other), formatBytes(report.other), Modifier.weight(1f))
            LegendItem(free, str(R.string.space_free), formatBytes(report.deviceFree), Modifier.weight(1f))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String, modifier: Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private fun kindLabel(k: SpaceKind): String = str(when (k) {
    SpaceKind.PHOTOS -> R.string.space_photos
    SpaceKind.VIDEOS -> R.string.space_videos
    SpaceKind.NOTES -> R.string.space_notes
    SpaceKind.PDF -> R.string.space_pdf
    SpaceKind.OTHER_FILES -> R.string.space_other_files
    SpaceKind.TRASH -> R.string.space_trash
    SpaceKind.CACHE -> R.string.space_cache
    SpaceKind.INDEXES -> R.string.space_indexes
    SpaceKind.APP -> R.string.space_app
})

/**
 * Impostazioni › Spazio › "Spazio occupato": the phone bar, what Cripta holds by kind (largest
 * first, empty kinds left out) and the cleanup actions.
 */
@Composable
internal fun SpaceDetails(
    report: SpaceReport?,
    trashCount: Int,
    onCleanup: () -> Unit,
    onClearCache: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    if (report == null) {
        // The rows that will be here (label and figure, then the bar), as blocks of the same size.
        repeat(4) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.cripta.app.ui.components.Skeleton(Modifier.width(110.dp).height(14.dp))
                    Spacer(Modifier.weight(1f))
                    com.cripta.app.ui.components.Skeleton(Modifier.width(60.dp).height(14.dp))
                }
                com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth().height(10.dp), CircleShape)
            }
        }
        return
    }
    DeviceSpaceBar(report)
    Column(Modifier.padding(top = 12.dp)) {
        report.byKind.entries.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (k, n) ->
            CountRow(kindLabel(k), formatBytes(n), progress = if (report.vault > 0) n.toFloat() / report.vault else 0f)
        }
    }
    com.cripta.app.ui.components.Button(onClick = onCleanup, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)) {
        com.cripta.app.ui.components.ButtonText(str(R.string.cleanup_open))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.cripta.app.ui.components.FilledTonalButton(onClick = onClearCache, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            com.cripta.app.ui.components.ButtonText(str(R.string.clear_cache))
        }
        if (trashCount > 0) com.cripta.app.ui.components.FilledTonalButton(onClick = onEmptyTrash, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            com.cripta.app.ui.components.ButtonText(str(R.string.empty_trash))
        }
    }
}

private enum class CleanupSort { SIZE, NAME, DATE }

/**
 * The vault's files heaviest first (or by name, by date), to free space: tap files to select
 * them, then delete; through the trash when it is on, with a confirmation either way.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VaultCleanupDialog(
    files: List<FileEntity>,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    trashOn: Boolean,
    trashDays: Int,
    onDelete: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var sort by remember { mutableStateOf(CleanupSort.SIZE) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val shown = remember(files, sort) {
        when (sort) {
            CleanupSort.SIZE -> files.sortedByDescending { it.sizeBytes }
            CleanupSort.NAME -> files.sortedBy { it.originalName.lowercase() }
            CleanupSort.DATE -> files.sortedBy { it.importedAt }
        }
    }
    // Files gone meanwhile (deleted here or elsewhere) leave the selection.
    val live = remember(files) { files.map { it.id }.toSet() }
    val sel = selected.intersect(live)
    val selBytes = files.filter { it.id in sel }.sumOf { it.sizeBytes }
    com.cripta.app.ui.components.CriptaDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        androidx.activity.compose.BackHandler { if (sel.isNotEmpty()) selected = emptySet() else onDismiss() }
        val safe = androidx.compose.foundation.layout.WindowInsets.safeDrawing
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.material3.Scaffold(
            contentWindowInsets = safe,
            topBar = {
                androidx.compose.material3.TopAppBar(
                    colors = com.cripta.app.ui.components.criptaTopBarColors(sel.isNotEmpty() || listState.canScrollBackward),
                    windowInsets = safe.only(
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                            androidx.compose.foundation.layout.WindowInsetsSides.Top
                    ),
                    title = { Text(if (sel.isEmpty()) str(R.string.cleanup_title) else plural(R.plurals.n_selected_m, sel.size)) },
                    navigationIcon = {
                        IconButton(onClick = { if (sel.isNotEmpty()) selected = emptySet() else onDismiss() }) {
                            Icon(if (sel.isNotEmpty()) CriptaIcons.Close else CriptaIcons.ArrowBack, str(R.string.back))
                        }
                    },
                )
            },
            bottomBar = {
                if (sel.isNotEmpty()) androidx.compose.material3.BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    windowInsets = safe.only(
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                            androidx.compose.foundation.layout.WindowInsetsSides.Bottom
                    ),
                ) {
                    com.cripta.app.ui.components.DestructiveButton(
                        str(R.string.cleanup_delete_n, formatBytes(selBytes)), onClick = { confirm = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), icon = CriptaIcons.Delete)
                }
            },
        ) { pad ->
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(str(R.string.cleanup_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.cripta.app.ui.components.ChipFlowRow {
                            listOf(CleanupSort.SIZE to R.string.sort_size, CleanupSort.NAME to R.string.sort_name, CleanupSort.DATE to R.string.sort_date)
                                .forEach { (k, label) ->
                                    com.cripta.app.ui.components.ChoiceChip(selected = sort == k, onClick = { sort = k }, label = { Text(str(label)) })
                                }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(plural(R.plurals.files_n, files.size) + " · " + formatBytes(files.sumOf { it.sizeBytes }),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (sel.isNotEmpty()) com.cripta.app.ui.components.TextButton(onClick = { selected = emptySet() }) {
                                com.cripta.app.ui.components.ButtonText(str(R.string.deselect_all))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
                items(shown.size, key = { shown[it].id }) { i ->
                    val f = shown[i]
                    val on = f.id in sel
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable { selected = if (on) sel - f.id else sel + f.id }
                            .padding(vertical = 6.dp)
                            .semantics { this.selected = on },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.cripta.app.ui.components.MediaThumb(f, thumb, Modifier.size(56.dp), selected = on, selectionMode = sel.isNotEmpty(),
                            shape = com.cripta.app.ui.theme.CoverShapeSmall)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(f.originalName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.cripta.app.Strings.locale).format(java.util.Date(f.importedAt)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        Text(formatBytes(f.sizeBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp).width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
    }
    if (confirm) {
        val n = sel.size
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (trashOn) str(R.string.cleanup_trash_q, plural(R.plurals.files_n, n)) else str(R.string.cleanup_delete_q, plural(R.plurals.files_n, n))) },
            text = { Text(if (trashOn) str(R.string.cleanup_trash_text, trashDays) else str(R.string.cleanup_delete_text, formatBytes(selBytes))) },
            confirmButton = {
                com.cripta.app.ui.components.TextButton(onClick = { onDelete(sel.toList()); selected = emptySet(); confirm = false }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete))
                }
            },
            dismissButton = { com.cripta.app.ui.components.TextButton(onClick = { confirm = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
}

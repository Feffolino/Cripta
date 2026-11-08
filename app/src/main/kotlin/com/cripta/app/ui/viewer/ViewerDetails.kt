@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cripta.app.ui.viewer

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.width
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToLong
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.cripta.app.data.db.FileEntity

@Composable
private fun InfoLine(label: String, value: String) {
    // The value is what the user came for: full contrast; the label is the quieter caption.
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * The media's source link: a single-line field like every other (write it, or paste it with the
 * button at its end; copy it when there is one). Saved on Done or when the field is left; emptied,
 * it is removed.
 */
@Composable
private fun LinkInfoLine(value: String?, onSet: (String?) -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value.orEmpty()) }
    fun save() { val t = text.trim(); if (t != value.orEmpty()) onSet(t.ifBlank { null }) }
    // DS: on the sheet the field is a well (surface-well), its end action in accent.
    androidx.compose.runtime.CompositionLocalProvider(com.cripta.app.ui.components.LocalFieldContainer provides MaterialTheme.colorScheme.surface) {
    com.cripta.app.ui.components.CriptaTextField(
        value = text, onValueChange = { text = it }, singleLine = true,
        placeholder = { Text(str(R.string.link_hint)) },
        leadingIcon = { Icon(CriptaIcons.Link, null) },
        trailingIcon = {
            if (text.isBlank()) IconButton(onClick = {
                val clip = clipboard.getText()?.text?.trim().orEmpty()
                if (clip.isBlank()) android.widget.Toast.makeText(ctx, str(R.string.clipboard_empty), android.widget.Toast.LENGTH_SHORT).show()
                else { text = clip; onSet(clip) }
            }) { Icon(CriptaIcons.ContentPaste, str(R.string.paste_copied_link), tint = MaterialTheme.colorScheme.primary) }
            else IconButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(text.trim()))
                android.widget.Toast.makeText(ctx, str(R.string.link_copied), android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(CriptaIcons.ContentCopy, str(R.string.copy_link), tint = MaterialTheme.colorScheme.primary) }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save(); focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .onFocusChanged { if (!it.isFocused) save() },
    )
    }
}

/**
 * One panel for everything about the file: its details (size, duration, resolution, date, source
 * link, technical info) and its tags, editable in place (tap toggles, long-press edits the tag).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DetailsSheet(
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
    /** The whole tag library, opened from "Mostra tutte" and remembered for the next media. */
    val allTagsOpen by vm.detailsAllTags.collectAsStateWithLifecycle()
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
        isVid -> str(R.string.video)
        com.cripta.app.data.VaultRepository.isImage(file.mimeType) -> str(R.string.photo)
        com.cripta.app.data.VaultRepository.isNote(file.mimeType) -> str(R.string.default_note_name)
        file.mimeType == "application/pdf" -> "PDF"
        else -> str(R.string.file_)
    }
    val ext = file.originalName.substringAfterLast('.', "").uppercase().takeIf { it.isNotBlank() && it.length <= 5 && it != kind }
    // Header: preview + name, date and type; then the file's actions when the top bar hands them over.
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = com.cripta.app.ui.theme.CoverShapeSmall,
                    modifier = Modifier.size(width = 96.dp, height = 72.dp)) {
                    val b = thumb
                    if (b != null) {
                        Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (kind) {
                                    str(R.string.video) -> CriptaIcons.Movie
                                    str(R.string.photo) -> CriptaIcons.Photo
                                    str(R.string.default_note_name) -> CriptaIcons.Description
                                    "PDF" -> CriptaIcons.PictureAsPdf
                                    else -> CriptaIcons.InsertDriveFile
                                },
                                null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                    // A long press copies the whole name (it may be cut here), with a short "Titolo copiato".
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val ctx = LocalContext.current
                    val copyName = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(file.originalName))
                        android.widget.Toast.makeText(ctx, str(R.string.title_copied), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    Text(file.originalName, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .pointerInput(file.originalName) { detectTapGestures(onLongPress = { copyName() }) }
                            .semantics { onLongClick(label = str(R.string.copy_title)) { copyName(); true } })
                    // Type and date on one quiet line: no chip around the type.
                    Text(
                        kind + (ext?.let { " · $it" } ?: "") + " · " +
                            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, com.cripta.app.Strings.locale)
                                .format(java.util.Date(file.createdAt)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actions?.let { a ->
                // Actions that leave the panel (a dialog, the editor) close it first.
                val leave: (() -> Unit) -> () -> Unit = { act -> { close(); act() } }
                Row(Modifier.fillMaxWidth()) {
                    SheetActionButton(
                        if (file.isFavorite) CriptaIcons.Star else CriptaIcons.StarBorder,
                        if (file.isFavorite) str(R.string.favorite) else str(R.string.favorites),
                        a.onFavorite,
                        tint = if (file.isFavorite) com.cripta.app.ui.theme.Favorite else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics {
                            stateDescription = if (file.isFavorite) str(R.string.in_favorites) else str(R.string.not_in_favorites)
                        },
                    )
                    a.onEditNote?.let { SheetActionButton(CriptaIcons.Edit, str(R.string.edit), leave(it), modifier = Modifier.weight(1f)) }
                    SheetActionButton(CriptaIcons.Download, str(R.string.export), leave(a.onExport), modifier = Modifier.weight(1f))
                    a.onConvert?.let { SheetActionButton(CriptaIcons.Autorenew, str(R.string.convert_to_mp4), leave(it), modifier = Modifier.weight(1f)) }
                    SheetActionButton(CriptaIcons.Delete, str(R.string.delete), leave(a.onDelete),
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    // Size, length, resolution as one quiet strip, like Home's summary: in equal columns, each
    // figure short (its unit smaller) with what it is under it. A video's resolution reads as its
    // name ("Full HD"), a photo's in megapixels, the exact pixels under it: "1920 × 1080" as the
    // figure took two lines in its column.
    val tiles: @Composable () -> Unit = {
        // DS cr-figures: on the sheet, a darker well (surface-well), radius-lg.
        Surface(color = MaterialTheme.colorScheme.surface, shape = com.cripta.app.ui.theme.RadiusLg,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
            Row(Modifier.padding(vertical = 4.dp)) {
                val size = com.cripta.app.ui.components.formatBytes(file.sizeBytes)
                DetailFigure(size.substringBeforeLast(' '), size.substringAfterLast(' ', ""), str(R.string.size_label),
                    CriptaIcons.SdStorage, str(R.string.size_x, size), Modifier.weight(1f))
                com.cripta.app.ui.components.formatDuration(file.durationMs)?.let {
                    DetailFigure(it, null, str(R.string.duration), CriptaIcons.Schedule, str(R.string.duration_x, it), Modifier.weight(1f))
                }
                val w = file.width; val h = file.height
                if (w != null && h != null && w > 0 && h > 0) {
                    val pixels = String.format(com.cripta.app.Strings.locale, "%d × %d", w, h)
                    if (isVid) {
                        val name = quality(w, h) ?: String.format(com.cripta.app.Strings.locale, "%dp", minOf(w, h))
                        DetailFigure(name, null, pixels, CriptaIcons.AspectRatio, str(R.string.resolution_desc, name, w, h), Modifier.weight(1f))
                    } else {
                        val mp = w.toLong() * h / 1_000_000.0
                        val value = if (mp < 10) String.format(com.cripta.app.Strings.locale, "%.1f", mp) else String.format(com.cripta.app.Strings.locale, "%d", mp.roundToLong())
                        DetailFigure(value, "MP", pixels, CriptaIcons.AspectRatio, str(R.string.resolution_mp_desc, value, w, h), Modifier.weight(1f))
                    }
                }
            }
        }
    }
    val tags: @Composable () -> Unit = {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            PanelHeading(str(R.string.tags), str(R.string.tags_panel_hint)) {
                TextButton(onClick = { creating = true }) {
                    Icon(CriptaIcons.Add, null, Modifier.size(18.dp))
                    com.cripta.app.ui.components.ButtonText(str(R.string.new_short), modifier = Modifier.padding(start = 6.dp))
                }
            }
            // Short form unless there is nothing quick to show (no tags on the file, pinned or recent).
            val hasQuick = onFile.isNotEmpty() || allTags.any { it.pinned } || (showRecents && allTags.any { it.lastUsedAt != null })
            com.cripta.app.ui.vault.TagSections(
                showLibrary = allTagsOpen || !hasQuick,
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
            if (hasQuick && allTags.isNotEmpty()) TextButton(onClick = { vm.setDetailsAllTags(!allTagsOpen) }) {
                com.cripta.app.ui.components.ButtonText(if (allTagsOpen) str(R.string.show_less) else str(R.string.show_all_n, allTags.size))
            }
        }
    }
    val info: @Composable () -> Unit = {
        Column {
            // DS: the link right under the figures, no heading of its own.
            LinkInfoLine(value = file.sourceUrl?.takeIf { it.isNotBlank() }, onSet = { vm.setSourceUrl(file.id, it) })
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column {
                    InfoLine(str(R.string.type), file.mimeType)
                    if (isVid && videoDiag.isNotBlank()) {
                        // A plain row that opens the figures in place: rarely needed, so it starts closed.
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                                .clickable(onClickLabel = if (showTech) str(R.string.collapse) else str(R.string.expand)) { showTech = !showTech },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(str(R.string.technical_details), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(if (showTech) CriptaIcons.ExpandLess else CriptaIcons.ExpandMore, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.animation.AnimatedVisibility(showTech,
                            enter = androidx.compose.animation.fadeIn(tween(220, 0, com.cripta.app.ui.theme.Motion.EaseOutQuart)) +
                                androidx.compose.animation.expandVertically(tween(220, 0, com.cripta.app.ui.theme.Motion.EaseOutQuart)),
                            exit = androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(tween(120))) {
                            Text(videoDiag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Portrait: one scrolling column.
    val column: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            // DS order: header and actions, the figures, the link, Etichette, Intro e finale; then people.
            header(); tiles(); info()
            SectionGap(); tags()
            if (isVid) { SectionGap(); IntroSection(file, vm) }
            if (isVid || com.cripta.app.data.VaultRepository.isImage(file.mimeType)) { SectionGap(); PeopleSection(file, vm) }
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
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        header(); tiles(); SectionGap(); info()
                    }
                    Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        if (isVid || com.cripta.app.data.VaultRepository.isImage(file.mimeType)) { PeopleSection(file, vm); SectionGap() }
                        tags()
                        if (isVid) { SectionGap(); IntroSection(file, vm) }
                    }
                }
            } else column()
        }
    }
    editTag?.let { name ->
        val t = byName[name]
        com.cripta.app.ui.vault.LabelEditorDialog(
            title = str(R.string.tag_edit_title, name),
            initialName = name,
            nameEditable = false,
            initialAlias = t?.alias ?: "",
            onConfirm = { _, alias -> vm.setTagAlias(name, alias); editTag = null },
            onDismiss = { editTag = null },
            onColor = if (t != null) ({ c -> vm.setTagColor(name, c) }) else null,
            initialColor = t?.color,
            onPinned = if (t != null) ({ p -> vm.setTagPinned(name, p) }) else null,
            initialPinned = t?.pinned == true,
            initialIcon = t?.icon,
            onIcon = if (t != null) ({ i -> vm.setTagIcon(name, i) }) else null,
        )
    }
    if (creating) {
        var newIcon by remember { mutableStateOf<String?>(null) }
        com.cripta.app.ui.vault.LabelEditorDialog(
            title = str(R.string.new_tag),
            onIcon = { newIcon = it },
            onConfirm = { name, alias ->
                vm.createTag(name, alias, newIcon)
                vm.setTags(file.id, (onFile + name).distinct())
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/**
 * One figure of the file (size, length, resolution): the [value] large with its [unit] smaller and
 * quieter beside it, then what it is ([label], after its [icon]) under it. Read out as [spoken].
 */
@Composable
private fun DetailFigure(value: String, unit: String?, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                         spoken: String, modifier: Modifier = Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val unitSize = MaterialTheme.typography.titleSmall.fontSize
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }.padding(horizontal = 10.dp, vertical = 12.dp)) {
        // Two lines at most, balanced, only when a large font makes the value wider than its column.
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(value)
                if (!unit.isNullOrEmpty()) {
                    withStyle(androidx.compose.ui.text.SpanStyle(fontSize = unitSize, color = quiet)) { append("\u00A0$unit") }
                }
            },
            style = MaterialTheme.typography.titleLarge.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading), maxLines = 2,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Icon(icon, null, tint = quiet, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = quiet, maxLines = 2,
                modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** The usual name of a video's resolution, by its shorter side. */
private fun quality(w: Int, h: Int): String? = when (minOf(w, h)) {
    in 2000..Int.MAX_VALUE -> "4K"
    in 1400..1999 -> "QHD"
    in 1000..1399 -> "Full HD"
    in 700..999 -> "HD"
    else -> null
}

/** The viewer's file actions, shown in the details panel instead of the top bar. */
internal class SheetActions(
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
    // DS cr-action: the icon on a 48dp surface-control disc, its name under it.
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurface else tint)
        }
        // Up to two lines, balanced: "Converti in MP4" reads in full instead of a cryptic short label.
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                lineHeight = 15.sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
            color = tint, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * Space before a section of the panel (on top of the column's own 12dp): more above a heading than
 * under it, so the sections are told apart by their headings, not by lines.
 */
@Composable
private fun SectionGap() = Spacer(Modifier.height(12.dp))

package com.cripta.app.ui.vault

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.TagEntity

/** A tag in the pickers: the shared [TagPill], assigned (tick, full colour) or not (tint). */
@Composable
private fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color,
    icon: String? = null,
) {
    com.cripta.app.ui.components.TagPill(
        label, color, tagIcon = icon,
        size = com.cripta.app.ui.components.TagPillSize.Medium,
        state = if (selected) com.cripta.app.ui.components.TagPillState.On else com.cripta.app.ui.components.TagPillState.Off,
        base = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        onLongClick = onLongClick,
        onClickLabel = if (selected) str(R.string.remove) else str(R.string.assign),
        onLongClickLabel = if (onLongClick != null) str(R.string.tag_edit_hint) else null,
        stateLabel = if (selected) str(R.string.assigned) else str(R.string.not_assigned),
    )
}

/**
 * Tag picker body shared by the tag dialogs. The full library ("Tutte") keeps a FIXED order (the
 * user's custom or alphabetical order) so each tag is always in the same spot and muscle memory
 * works. Above it, two short rows: "Fissate" (pinned, fixed order) and "Recenti" (last used) — the
 * only part that moves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagSections(
    allTags: List<TagEntity>,
    extraNames: List<String>,
    showRecents: Boolean,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    onLongPress: ((String) -> Unit)?,
    /** Optional search text: when set, only matching tags are listed (in a single section). */
    query: String = "",
    /**
     * False: the short form (the details panel) — the tags on the file, pinned and recent ones, and
     * not the whole library, which is opened on request.
     */
    showLibrary: Boolean = true,
) {
    val byName = remember(allTags) { allTags.associateBy { it.name } }
    val pinned = remember(allTags) { allTags.filter { it.pinned } }
    val recentCount = com.cripta.app.ui.theme.RecentTagsCount.value
    // Frozen while this list is on screen: tapping a tag marks it as just used, and re-sorting at
    // once moved the chips under the finger (and the rows below). Taken when the list appears
    // (again once the tags have loaded); tags deleted or pinned meanwhile drop out.
    val frozenRecents = remember(recentCount, allTags.isEmpty()) {
        allTags.filter { !it.pinned && it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }
            .take(recentCount).map { it.id }
    }
    val recents = remember(allTags, frozenRecents, recentCount) { stableRecents(allTags, frozenRecents, recentCount) }
    // Library order as given (respects the user's tag order); names typed but not yet saved go last.
    val library = remember(allTags, extraNames) {
        // Exact names: two tags that differ only in case (older vaults) are two different chips.
        allTags.map { it.name } + extraNames.filter { n -> allTags.none { it.name == n } }
    }
    @Composable
    fun row(title: String, names: List<String>) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Vertical gap congruent with the filter-sheet tag chips.
        com.cripta.app.ui.components.ChipFlowRow {
            names.forEach { name ->
                val t = byName[name]
                TagChip(
                    label = chipLabel(name, t?.alias),
                    selected = isSelected(name),
                    onClick = { onToggle(name) },
                    onLongClick = onLongPress?.let { f -> { f(name) } },
                    color = t?.let { com.cripta.app.ui.theme.tagColor(it) } ?: com.cripta.app.ui.theme.tagColor(name),
                    icon = t?.icon,
                )
            }
        }
    }
    val q = query.trim()
    if (q.isNotEmpty()) {
        val matches = library.filter { n -> byName[n]?.let { tagMatches(it, q) } ?: n.contains(q, ignoreCase = true) }
        if (matches.isEmpty()) {
            Text(str(R.string.tag_no_match, q), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else row(str(R.string.results), matches)
        return
    }
    if (!showLibrary) {
        val quick = (pinned.map { it.name } + if (showRecents) recents.map { it.name } else emptyList()).toSet()
        val onFile = library.filter { isSelected(it) && it !in quick }
        if (onFile.isNotEmpty()) row(str(R.string.on_this_file), onFile)
    }
    if (pinned.isNotEmpty()) row(str(R.string.pinned), pinned.map { it.name })
    if (showRecents && recents.isNotEmpty()) row(str(R.string.recent), recents.map { it.name })
    if (showLibrary && library.isNotEmpty()) row(if (pinned.isEmpty() && (!showRecents || recents.isEmpty())) str(R.string.tags) else str(R.string.icon_all_short), library)
}

/**
 * The "Recenti" row from the order taken when it appeared ([frozen]): those still there keep
 * their places; a slot freed by a tag deleted or pinned meanwhile is filled at the end with the
 * next most recent one, so the row never shows fewer tags than it should.
 */
internal fun stableRecents(allTags: List<TagEntity>, frozen: List<Long>, count: Int): List<TagEntity> {
    val byId = allTags.associateBy { it.id }
    val kept = frozen.mapNotNull { byId[it] }.filter { !it.pinned }.take(count)
    if (kept.size >= count) return kept
    val keptIds = kept.mapTo(HashSet()) { it.id }
    val fill = allTags.filter { !it.pinned && it.lastUsedAt != null && it.id !in keptIds && it.id !in frozen }
        .sortedByDescending { it.lastUsedAt }.take(count - kept.size)
    return kept + fill
}

private fun chipLabel(name: String, alias: String?): String =
    if (!alias.isNullOrBlank()) "$alias $name" else name

/** Everything the tag editor saves, handed over at once (never field by field). */
data class TagDraft(
    val name: String, val alias: String?, val color: Int?, val pinned: Boolean, val icon: String?,
    val onCovers: Boolean = true,
)

/**
 * Create/edit a label. Top to bottom: a live preview (the pill assigned and not, and a cover with
 * its mark), Nome and Sigla on one line, Icona, Colore, the switches; "Elimina" (when given) sits
 * apart on the left of the buttons and asks first. Nothing is reported before "Salva", and then all
 * of it together in one [TagDraft].
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LabelEditorDialog(
    title: String,
    initialName: String = "",
    initialAlias: String = "",
    onConfirm: (TagDraft) -> Unit,
    onDismiss: () -> Unit,
    /** A colour picker is shown (null = automatic). */
    showColor: Boolean = false,
    initialColor: Int? = null,
    /** A "Fissa in alto" switch is shown. */
    showPinned: Boolean = false,
    initialPinned: Boolean = false,
    /** False when the name can't be changed from here: it is shown as text instead of a field. */
    nameEditable: Boolean = true,
    /** An icon can be chosen (null = none). */
    showIcon: Boolean = false,
    initialIcon: String? = null,
    /** The names of the other tags: one of them, case aside, can't be used and is flagged at once. */
    takenNames: Collection<String> = emptyList(),
    /** A "Sulle copertine" switch is shown (off: the tag stays in filters and details only). */
    showOnCovers: Boolean = false,
    initialOnCovers: Boolean = true,
    /** How much the tag is used, for the line under the title; null = not shown (a new tag). */
    usage: com.cripta.app.data.db.TagUsage? = null,
    /** "Elimina" on the left of the buttons, after a confirmation; null = not offered. */
    onDelete: (() -> Unit)? = null,
) {
    // rememberSaveable keeps the draft only while the dialog itself stays: the flags that open it
    // are plain remember, so a recreated activity (a language change; rotations do not recreate
    // it) closes the dialog and the saved draft is not read again.
    var pinnedState by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialPinned) }
    var onCovers by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialOnCovers) }
    var icon by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialIcon) }
    var pickingIcon by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmDelete by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialName) }
    var alias by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialAlias) }
    var color by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialColor) }
    val clash = nameEditable && name.trim().let { n -> n.isNotEmpty() && takenNames.any { it.equals(n, ignoreCase = true) } }

    // "A" = the colour every uncoloured tag gets (Settings › Etichette, else one colour per name).
    val auto = com.cripta.app.ui.theme.tagColor(name.ifBlank { "?" })
    // A new colour cross-fades into the preview instead of jumping (120 ms, ease-out).
    val previewColor by androidx.compose.animation.animateColorAsState(
        color?.let { androidx.compose.ui.graphics.Color(it) } ?: auto,
        com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.SHORT), label = "tagPreviewColour",
    )

    @Composable
    fun switchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, on: Boolean, set: (Boolean) -> Unit) {
        // A plain settings row: icon, title, caption, the switch; the whole row toggles.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .clip(MaterialTheme.shapes.small)
                .toggleable(value = on, role = Role.Switch, onValueChange = set)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp).size(24.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(hint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            androidx.compose.material3.Switch(checked = on, onCheckedChange = null, colors = com.cripta.app.ui.components.criptaSwitchColors())
        }
    }

    val canSave = name.isNotBlank() && !clash
    val save = { onConfirm(TagDraft(name.trim(), alias.trim().ifEmpty { null }, color, pinnedState, icon, onCovers)) }

    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                usage?.let { u ->
                    Text(
                        str(R.string.tag_usage_line,
                            plural(R.plurals.files_n, u.files, com.cripta.app.ui.components.formatCount(u.files)),
                            plural(R.plurals.in_folders_n, u.folders, com.cripta.app.ui.components.formatCount(u.folders))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        text = {
            // No frames: each part is a heading plus space (more above a heading than under it).
            // The preview stays first, so every change below is seen as it is made.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TagEditorPreview(name = name, alias = alias, icon = icon, color = previewColor, onCovers = onCovers)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top) {
                    if (nameEditable) {
                        com.cripta.app.ui.components.CriptaTextField(
                            value = name, onValueChange = { name = limitName(it) },
                            label = { Text(str(R.string.sort_name)) }, singleLine = true,
                            isError = clash,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(str(R.string.tag_rename_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 8.dp))
                    }
                    com.cripta.app.ui.components.CriptaTextField(
                        // At most 6 visible characters, counted as graphemes.
                        value = alias, onValueChange = { alias = takeGraphemes(it, 6) },
                        label = { Text(str(R.string.tag_alias_short)) }, singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                }
                Text(
                    if (clash) str(R.string.tag_exists, name.trim()) else str(R.string.tag_alias_cover_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (clash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                )
                if (showIcon) {
                    EditorHeading(str(R.string.icon))
                    com.cripta.app.ui.tagicons.TagIconRow(name = name, icon = icon, onPick = { icon = it }, onBrowse = { pickingIcon = true })
                }
                if (showColor) {
                    EditorHeading(str(R.string.colour))
                    TagColorPicker(selected = color, autoColor = auto, autoDescription = str(R.string.default_colour)) { color = it }
                }
                if (showPinned || showOnCovers) Box(Modifier.padding(top = 14.dp))
                if (showPinned) switchRow(CriptaIcons.PushPin, str(R.string.tag_pin_top), str(R.string.tag_pin_top_hint), pinnedState) { pinnedState = it }
                if (showOnCovers) switchRow(CriptaIcons.Image, str(R.string.sec_on_covers), str(R.string.tag_on_covers_hint), onCovers) { onCovers = it }
            }
        },
        confirmButton = {
            if (onDelete != null) {
                // "Elimina" apart on the left, in the error colour; Annulla / Salva stay on the right.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { confirmDelete = true }) {
                        com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) }
                    TextButton(enabled = canSave, onClick = save) { com.cripta.app.ui.components.ButtonText(str(R.string.save)) }
                }
            } else {
                TextButton(enabled = canSave, onClick = save) { com.cripta.app.ui.components.ButtonText(str(R.string.save)) }
            }
        },
        dismissButton = if (onDelete != null) null else ({
            TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) }
        }),
    )
    if (pickingIcon) {
        com.cripta.app.ui.tagicons.TagIconPicker(tagName = name, selected = icon,
            onPick = { icon = it; pickingIcon = false }, onDismiss = { pickingIcon = false })
    }
    if (confirmDelete && onDelete != null) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.delete_tag_q)) },
            text = { Text(str(R.string.delete_tag_text, initialName)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
}

/**
 * The editor's live preview: on the left the pill as lists show it, assigned and not; on the right
 * a small cover with the mark it gets (as Impostazioni › Etichette sets it), or none when the tag
 * is kept off the covers. One TalkBack item.
 */
@Composable
private fun TagEditorPreview(name: String, alias: String, icon: String?, color: androidx.compose.ui.graphics.Color, onCovers: Boolean) {
    val shownName = name.trim().ifBlank { str(R.string.new_tag) }
    val a = alias.trim()
    val text = if (a.isNotEmpty()) "$a $shownName" else shownName
    val caption = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp)
            .clearAndSetSemantics { contentDescription = str(R.string.preview) + ": " + text },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.cripta.app.ui.components.TagPill(text, color, size = com.cripta.app.ui.components.TagPillSize.Medium,
                state = com.cripta.app.ui.components.TagPillState.On,
                base = MaterialTheme.colorScheme.surfaceContainerHigh, tagIcon = icon)
            com.cripta.app.ui.components.TagPill(text, color, size = com.cripta.app.ui.components.TagPillSize.Medium,
                state = com.cripta.app.ui.components.TagPillState.Off,
                base = MaterialTheme.colorScheme.surfaceContainerHigh, tagIcon = icon)
            Text(str(R.string.tag_states_caption), style = caption, color = muted)
        }
        Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(112.dp, 84.dp).clip(com.cripta.app.ui.theme.CoverShape)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceVariant))),
                contentAlignment = Alignment.BottomStart,
            ) {
                if (onCovers) Box(Modifier.padding(6.dp)) {
                    CoverMarkSample(TagEntity(id = -1, name = name.trim().ifBlank { "AB" }, alias = a.ifBlank { null }, icon = icon), color)
                }
            }
            Text(if (onCovers) str(R.string.sec_on_covers) else str(R.string.tag_off_covers), style = caption, color = muted)
        }
    }
}

/** A part of the tag editor: accent heading, 24dp above it and 10dp to what it names. */
@Composable
private fun EditorHeading(text: String) {
    Text(text, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp).semantics { heading() })
}

/**
 * Tag editor for a single file: tap to toggle; long-press a chip to edit its alias, colour and pin;
 * "Nuova etichetta" to create one in one step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    allTags: List<TagEntity>,
    initialSelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    /** Long-press edit of a tag: everything saved at once, by id. */
    onSaveTag: (TagEntity, TagDraft) -> Unit,
    onCreateTag: (TagDraft) -> Unit,
    onDismiss: () -> Unit,
    showRecents: Boolean = true,
    /** Each tag's use, by id, for the editor's line under its title. */
    usage: Map<Long, com.cripta.app.data.db.TagUsage> = emptyMap(),
    /** Deletes a tag from its editor (after a confirmation); null = not offered. */
    onDeleteTag: ((TagEntity) -> Unit)? = null,
) {
    val selected: SnapshotStateList<String> = remember {
        initialSelected.map { it.trim() }.filter { it.isNotEmpty() }.toMutableStateList()
    }
    var editTarget by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val byName = remember(allTags) { allTags.associateBy { it.name } }

    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.tags)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(str(R.string.tag_assign_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (allTags.size > SEARCH_THRESHOLD) TagSearchField(query) { query = it }
                TagSections(
                    query = query,
                    allTags = allTags,
                    extraNames = selected.toList(),
                    showRecents = showRecents,
                    isSelected = { n -> n in selected },
                    onToggle = { n -> if (n in selected) selected.remove(n) else selected.add(n) },
                    onLongPress = { n -> if (byName[n] != null) editTarget = n },
                )
                TextButton(onClick = { creating = true }) {
                    Icon(CriptaIcons.Add, null, modifier = Modifier.padding(end = 4.dp))
                    com.cripta.app.ui.components.ButtonText(str(R.string.new_tag))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toList()) }) { com.cripta.app.ui.components.ButtonText(str(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )

    editTarget?.let { name ->
        val t = byName[name] ?: return@let
        // Name edits on an existing tag are out of scope here (Impostazioni › Etichette): the rest is saved.
        LabelEditorDialog(
            title = str(R.string.tag_edit_title, name),
            initialName = name,
            initialAlias = t.alias ?: "",
            onConfirm = { draft -> onSaveTag(t, draft.copy(name = t.name)); editTarget = null },
            onDismiss = { editTarget = null },
            showColor = true, initialColor = t.color,
            showPinned = true, initialPinned = t.pinned,
            showIcon = true, initialIcon = t.icon,
            showOnCovers = true, initialOnCovers = t.onCovers,
            usage = usage[t.id],
            onDelete = onDeleteTag?.let { f -> { selected.remove(t.name); f(t); editTarget = null } },
            nameEditable = false,
        )
    }

    if (creating) {
        LabelEditorDialog(
            title = str(R.string.new_tag),
            showIcon = true,
            takenNames = allTags.map { it.name } + selected,
            onConfirm = { draft ->
                onCreateTag(draft)
                if (draft.name !in selected) selected.add(draft.name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/**
 * Batch tag editor: pick labels to ADD to every selected file (existing tags are kept), or switch
 * to "Rimuovi" to take the chosen labels off every selected file.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BatchTagDialog(
    count: Int,
    allTags: List<TagEntity>,
    onConfirm: (List<String>) -> Unit,
    onCreateTag: (TagDraft) -> Unit,
    onDismiss: () -> Unit,
    showRecents: Boolean = true,
    /** When set, a "Rimuovi" mode is offered and the chosen tags are reported here. */
    onRemove: ((List<TagEntity>) -> Unit)? = null,
) {
    val chosen: SnapshotStateList<String> = remember { mutableStateListOf() }
    var creating by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val items = plural(R.plurals.items_n, count)

    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.tags_for, items)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (onRemove != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.cripta.app.ui.components.ChoiceChip(
                            // Only a real switch of mode clears the choice: tapping the active one keeps it.
                            selected = !removing, onClick = { if (removing) { removing = false; chosen.clear() } },
                            label = { com.cripta.app.ui.components.OneLineText(str(R.string.add)) },
                        )
                        com.cripta.app.ui.components.ChoiceChip(
                            selected = removing, onClick = { if (!removing) { removing = true; chosen.clear() } },
                            label = { com.cripta.app.ui.components.OneLineText(str(R.string.remove)) },
                        )
                    }
                }
                Text(
                    if (removing) str(R.string.tags_remove_hint)
                    else str(R.string.tags_add_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (allTags.isEmpty() && chosen.isEmpty()) {
                    Text(str(R.string.tags_none_create),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (allTags.size > SEARCH_THRESHOLD) TagSearchField(query) { query = it }
                TagSections(
                    query = query,
                    allTags = allTags,
                    extraNames = if (removing) emptyList() else chosen.toList(),
                    showRecents = showRecents,
                    isSelected = { n -> n in chosen },
                    onToggle = { n -> if (n in chosen) chosen.remove(n) else chosen.add(n) },
                    onLongPress = null,
                )
                if (!removing) {
                    TextButton(onClick = { creating = true }) {
                        Icon(CriptaIcons.Add, null, modifier = Modifier.padding(end = 4.dp))
                        com.cripta.app.ui.components.ButtonText(str(R.string.new_tag))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                // Removal goes by the tags themselves (ids), not by names that may change meanwhile.
                if (removing) onRemove?.invoke(chosen.mapNotNull { n -> allTags.firstOrNull { it.name == n } })
                else onConfirm(chosen.toList())
            }) { com.cripta.app.ui.components.ButtonText(if (removing) str(R.string.remove) else str(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )

    if (creating) {
        LabelEditorDialog(
            title = str(R.string.new_tag),
            showIcon = true,
            takenNames = allTags.map { it.name } + chosen,
            onConfirm = { draft ->
                onCreateTag(draft)
                if (draft.name !in chosen) chosen.add(draft.name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/** Above this many tags the pickers show a search field. */
internal const val SEARCH_THRESHOLD = 12

/** Whether [tag] is found by [query]: its name or its acronym, case aside. The one rule of every tag search (pickers, filter bar, vault search). */
internal fun tagMatches(tag: TagEntity, query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || tag.name.contains(q, ignoreCase = true) || tag.alias?.contains(q, ignoreCase = true) == true
}

@Composable
internal fun TagSearchField(query: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    com.cripta.app.ui.components.CriptaTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(str(R.string.search_tag)) },
        leadingIcon = { Icon(CriptaIcons.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({
            androidx.compose.material3.IconButton(onClick = { onChange("") }) { Icon(CriptaIcons.Close, str(R.string.clear_search)) }
        }) else null,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}

/** First [max] user-perceived characters of [s] (an emoji sequence counts as one). */
internal fun takeGraphemes(s: String, max: Int): String {
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(s)
    var count = 0
    var end = 0
    while (count < max) {
        val next = it.next()
        if (next == android.icu.text.BreakIterator.DONE) return s
        end = next
        count++
    }
    return s.substring(0, end)
}

/**
 * A colour of the user's own (the round rainbow), "A" (automatic / default colour), then the shared
 * palette, on one line that scrolls sideways ([com.cripta.app.ui.components.ColorChoiceRow]); [onPick] gets null for
 * "A". It opens scrolled to the chosen colour.
 */
@Composable
internal fun TagColorPicker(
    selected: Int?,
    autoColor: androidx.compose.ui.graphics.Color,
    autoDescription: String,
    onPick: (Int?) -> Unit,
) {
    com.cripta.app.ui.components.ColorChoiceRow(
        selected = selected, noneFill = com.cripta.app.ui.theme.rememberTagTones(autoColor, colored = true).mark, noneDescription = autoDescription,
        onPick = onPick, noneLabel = "A",
    )
}

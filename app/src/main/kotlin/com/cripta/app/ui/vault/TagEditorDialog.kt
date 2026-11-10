package com.cripta.app.ui.vault

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
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
    val recents = remember(allTags, frozenRecents) {
        val byId = allTags.associateBy { it.id }
        frozenRecents.mapNotNull { byId[it] }.filter { !it.pinned }
    }
    // Library order as given (respects the user's tag order); names typed but not yet saved go last.
    val library = remember(allTags, extraNames) {
        allTags.map { it.name } + extraNames.filter { n -> allTags.none { it.name.equals(n, ignoreCase = true) } }
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
        val matches = library.filter { n ->
            n.contains(q, ignoreCase = true) || byName[n]?.alias?.contains(q, ignoreCase = true) == true
        }
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

private fun chipLabel(name: String, alias: String?): String =
    if (!alias.isNullOrBlank()) "$alias $name" else name

/**
 * Create/edit a label: its name, an icon and/or a short acronym ("sigla"), shown as a compact badge
 * on thumbnails. Icons replace the old emoji aliases.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LabelEditorDialog(
    title: String,
    initialName: String = "",
    initialAlias: String = "",
    onConfirm: (name: String, alias: String?) -> Unit,
    onDismiss: () -> Unit,
    /** When set, a colour picker is shown and the choice (null = automatic) is reported here. */
    onColor: ((Int?) -> Unit)? = null,
    initialColor: Int? = null,
    /** When set, a "Fissa in alto" switch is shown. */
    onPinned: ((Boolean) -> Unit)? = null,
    initialPinned: Boolean = false,
    /** False when the name can't be changed from here: it is shown as text instead of a field. */
    nameEditable: Boolean = true,
    /** When set, an icon can be chosen (null = none) and the choice is reported here. */
    onIcon: ((String?) -> Unit)? = null,
    initialIcon: String? = null,
) {
    var pinnedState by remember { mutableStateOf(initialPinned) }
    var icon by remember { mutableStateOf(initialIcon) }
    var pickingIcon by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialName) }
    var alias by remember { mutableStateOf(initialAlias) }
    var color by remember { mutableStateOf(initialColor) }

    // "A" = the colour every uncoloured tag gets (Settings › Etichette, else one colour per name).
    val auto = com.cripta.app.ui.theme.tagColor(name.ifBlank { "?" })
    // A new colour cross-fades into the preview instead of jumping (120 ms, ease-out).
    val previewColor by androidx.compose.animation.animateColorAsState(
        color?.let { androidx.compose.ui.graphics.Color(it) } ?: auto,
        com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.SHORT), label = "tagPreviewColour",
    )

    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // No frames: each part is a heading plus space (more above a heading than under it).
            // The preview stays first, so every change below is seen as it is made.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TagEditorPreview(name = name, alias = alias, icon = icon, color = previewColor)
                if (nameEditable) {
                    com.cripta.app.ui.components.CriptaTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(str(R.string.sort_name)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                } else {
                    Text(str(R.string.tag_rename_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                com.cripta.app.ui.components.CriptaTextField(
                    // At most 6 visible characters, counted as graphemes.
                    value = alias, onValueChange = { alias = takeGraphemes(it, 6) },
                    label = { Text(str(R.string.tag_alias)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    supportingText = { Text(str(R.string.tag_alias_hint)) },
                )
                if (onIcon != null) {
                    EditorHeading(str(R.string.icon))
                    com.cripta.app.ui.tagicons.TagIconField(name = name, icon = icon, onPick = { icon = it }, onBrowse = { pickingIcon = true },
                        showLabel = false)
                }
                if (onColor != null) {
                    EditorHeading(str(R.string.colour))
                    TagColorPicker(selected = color, autoColor = auto, autoDescription = str(R.string.default_colour)) { color = it }
                }
                if (onPinned != null) {
                    // A plain settings row: icon, title, caption, the switch; the whole row toggles.
                    Row(
                        Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 60.dp)
                            .clip(MaterialTheme.shapes.small)
                            .toggleable(value = pinnedState, role = Role.Switch, onValueChange = { pinnedState = it })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(CriptaIcons.PushPin, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp).size(24.dp))
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(str(R.string.tag_pin_top), style = MaterialTheme.typography.bodyLarge)
                            Text(str(R.string.tag_pin_top_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                        androidx.compose.material3.Switch(checked = pinnedState, onCheckedChange = null, colors = com.cripta.app.ui.components.criptaSwitchColors())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onColor?.invoke(color)
                    onPinned?.invoke(pinnedState)
                    onIcon?.invoke(icon)
                    onConfirm(name.trim(), alias.trim().ifEmpty { null })
                },
            ) { com.cripta.app.ui.components.ButtonText(str(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
    if (pickingIcon) {
        com.cripta.app.ui.tagicons.TagIconPicker(tagName = name, selected = icon,
            onPick = { icon = it; pickingIcon = false }, onDismiss = { pickingIcon = false })
    }
}

/**
 * The editor's live preview: the tag as lists show it (name, alias, icon) and, beside it, the mark
 * a cover gets (the alias, else the icon alone, else the first two letters). One TalkBack item.
 */
@Composable
private fun TagEditorPreview(name: String, alias: String, icon: String?, color: androidx.compose.ui.graphics.Color) {
    val shownName = name.trim().ifBlank { str(R.string.new_tag) }
    val a = alias.trim()
    Row(
        Modifier.fillMaxWidth()
            .clearAndSetSemantics { contentDescription = str(R.string.preview) + ": " + (if (a.isNotEmpty()) "$a $shownName" else shownName) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            com.cripta.app.ui.components.TagPill(
                if (a.isNotEmpty()) "$a $shownName" else shownName, color,
                size = com.cripta.app.ui.components.TagPillSize.Medium,
                base = MaterialTheme.colorScheme.surfaceContainerHigh,
                tagIcon = icon,
            )
        }
        com.cripta.app.ui.components.TagPill(
            a.ifBlank { if (icon != null) "" else name.trim().take(2).uppercase().ifBlank { "AB" } },
            color, tagIcon = icon,
        )
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
    onSetAlias: (String, String?) -> Unit,
    onCreateTag: (name: String, alias: String?, icon: String?) -> Unit,
    onDismiss: () -> Unit,
    showRecents: Boolean = true,
    onSetColor: ((name: String, color: Int?) -> Unit)? = null,
    onSetPinned: ((name: String, pinned: Boolean) -> Unit)? = null,
    onSetIcon: ((name: String, icon: String?) -> Unit)? = null,
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
                    isSelected = { n -> selected.any { it.equals(n, ignoreCase = true) } },
                    onToggle = { n ->
                        if (selected.any { it.equals(n, ignoreCase = true) }) selected.removeAll { it.equals(n, ignoreCase = true) }
                        else selected.add(n)
                    },
                    onLongPress = { editTarget = it },
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
        val t = byName[name]
        LabelEditorDialog(
            title = str(R.string.tag_edit_title, name),
            initialName = name,
            initialAlias = t?.alias ?: "",
            onConfirm = { _, alias ->
                // Name edits on an existing tag are out of scope here; keep the name, set the alias.
                onSetAlias(name, alias)
                editTarget = null
            },
            onDismiss = { editTarget = null },
            onColor = if (t != null && onSetColor != null) ({ c -> onSetColor(name, c) }) else null,
            initialColor = t?.color,
            onPinned = if (t != null && onSetPinned != null) ({ p -> onSetPinned(name, p) }) else null,
            initialPinned = t?.pinned == true,
            initialIcon = t?.icon,
            onIcon = if (t != null && onSetIcon != null) ({ i -> onSetIcon(name, i) }) else null,
            nameEditable = false,
        )
    }

    if (creating) {
        var newIcon by remember { mutableStateOf<String?>(null) }
        LabelEditorDialog(
            title = str(R.string.new_tag),
            onIcon = { newIcon = it },
            onConfirm = { name, alias ->
                onCreateTag(name, alias, newIcon)
                if (selected.none { it.equals(name, ignoreCase = true) }) selected.add(name)
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
    onCreateTag: (name: String, alias: String?, icon: String?) -> Unit,
    onDismiss: () -> Unit,
    showRecents: Boolean = true,
    /** When set, a "Rimuovi" mode is offered and its choice is reported here. */
    onRemove: ((List<String>) -> Unit)? = null,
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
                            label = { Text(str(R.string.add)) },
                        )
                        com.cripta.app.ui.components.ChoiceChip(
                            selected = removing, onClick = { if (!removing) { removing = true; chosen.clear() } },
                            label = { Text(str(R.string.remove)) },
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
                    isSelected = { n -> chosen.any { it.equals(n, ignoreCase = true) } },
                    onToggle = { n ->
                        if (chosen.any { it.equals(n, ignoreCase = true) }) chosen.removeAll { it.equals(n, ignoreCase = true) }
                        else chosen.add(n)
                    },
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
                if (removing) onRemove?.invoke(chosen.toList()) else onConfirm(chosen.toList())
            }) { Text(if (removing) str(R.string.remove) else str(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )

    if (creating) {
        var newIcon by remember { mutableStateOf<String?>(null) }
        LabelEditorDialog(
            title = str(R.string.new_tag),
            onIcon = { newIcon = it },
            onConfirm = { name, alias ->
                onCreateTag(name, alias, newIcon)
                if (chosen.none { it.equals(name, ignoreCase = true) }) chosen.add(name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/** Above this many tags the pickers show a search field. */
private const val SEARCH_THRESHOLD = 12

@Composable
private fun TagSearchField(query: String, onChange: (String) -> Unit) {
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
        modifier = Modifier.fillMaxWidth(),
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
        selected = selected, noneFill = com.cripta.app.ui.theme.shownTagColor(autoColor), noneDescription = autoDescription,
        onPick = onPick, noneLabel = "A",
    )
}

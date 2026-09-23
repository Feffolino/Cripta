package com.cripta.app.ui.vault

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/** Quick-pick emojis offered when creating/editing a label alias. */
private val QUICK_EMOJIS = listOf(
    "⭐", "🔒", "❤️", "📌", "🏷️", "📁", "📄", "🖼️", "🎬", "🎵",
    "📷", "🔑", "💼", "🎨", "🎁", "✅", "🔥", "💡", "🧾", "🌍",
)

/** One reusable chip used by the tag dialogs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    dot: androidx.compose.ui.graphics.Color? = null,
) {
    val shape = MaterialTheme.shapes.small
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        // 48dp touch target around the compact chip; exposed as a checkbox with its state, and the
        // long-press (alias / colour / pin) is announced and reachable as a TalkBack action.
        modifier = Modifier.minimumInteractiveComponentSize().clip(shape)
            .combinedClickable(
                role = Role.Checkbox,
                onClickLabel = if (selected) "Rimuovi" else "Assegna",
                onLongClickLabel = if (onLongClick != null) "Modifica alias, colore e fissa" else null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "Assegnata" else "Non assegnata"
            },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (dot != null) {
                androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot))
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
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
) {
    val byName = remember(allTags) { allTags.associateBy { it.name } }
    val pinned = remember(allTags) { allTags.filter { it.pinned } }
    val recentCount = com.cripta.app.ui.theme.RecentTagsCount.value
    val recents = remember(allTags, recentCount) {
        allTags.filter { !it.pinned && it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(recentCount)
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
                    dot = t?.let { com.cripta.app.ui.theme.tagColor(it) },
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
            Text("Nessuna etichetta corrisponde a \"$q\".", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else row("Risultati", matches)
        return
    }
    if (pinned.isNotEmpty()) row("Fissate", pinned.map { it.name })
    if (showRecents && recents.isNotEmpty()) row("Recenti", recents.map { it.name })
    if (library.isNotEmpty()) row(if (pinned.isEmpty() && (!showRecents || recents.isEmpty())) "Etichette" else "Tutte", library)
}

private fun chipLabel(name: String, alias: String?): String =
    if (!alias.isNullOrBlank()) "$alias #$name" else "#$name"

/**
 * Create/edit a label choosing a name and, right away, an emoji/acronym alias.
 * The alias is shown as a compact badge on thumbnails.
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
) {
    var pinnedState by remember { mutableStateOf(initialPinned) }
    var name by remember { mutableStateOf(initialName) }
    var alias by remember { mutableStateOf(initialAlias) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (nameEditable) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("#$name", style = MaterialTheme.typography.titleMedium)
                    Text("Il nome si cambia da Impostazioni › Etichette.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    // At most 6 visible characters, counted as graphemes so an emoji is never split.
                    value = alias, onValueChange = { alias = takeGraphemes(it, 6) },
                    label = { Text("Emoji o acronimo (opzionale)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Suggerimenti", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QUICK_EMOJIS.forEach { e ->
                        val shape = MaterialTheme.shapes.small
                        Surface(
                            color = if (alias == e) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = shape,
                            modifier = Modifier.minimumInteractiveComponentSize().clip(shape)
                                .selectable(selected = alias == e, role = Role.RadioButton, onClick = { alias = e })
                                .semantics { contentDescription = "Alias $e" },
                        ) {
                            Text(e, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                }
                if (onPinned != null) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = pinnedState, role = Role.Switch, onValueChange = { pinnedState = it }),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("📌 Fissa in alto")
                            Text("Sempre tra le prime, in posizione fissa.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = pinnedState, onCheckedChange = null)
                    }
                }
                if (onColor != null) {
                    Text("Colore", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // "A" = the colour every uncoloured tag gets (Settings › Copertine, else one
                    // colour per name), then the palette.
                    val auto = com.cripta.app.ui.theme.tagColor(name.ifBlank { "?" })
                    TagColorPicker(selected = color, autoColor = auto, autoDescription = "Colore predefinito") { color = it }
                    // Live preview of the badge as it will look on covers.
                    val preview = color?.let { androidx.compose.ui.graphics.Color(it) } ?: auto
                    Surface(color = preview, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                        Text(alias.ifBlank { name.take(2).uppercase() }.ifBlank { "AB" },
                            style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
                    onConfirm(name.trim(), alias.trim().ifEmpty { null })
                },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
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
    onCreateTag: (name: String, alias: String?) -> Unit,
    onDismiss: () -> Unit,
    showRecents: Boolean = true,
    onSetColor: ((name: String, color: Int?) -> Unit)? = null,
    onSetPinned: ((name: String, pinned: Boolean) -> Unit)? = null,
) {
    val selected: SnapshotStateList<String> = remember {
        initialSelected.map { it.trim() }.filter { it.isNotEmpty() }.toMutableStateList()
    }
    var editTarget by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val byName = remember(allTags) { allTags.associateBy { it.name } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etichette") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text("Tocca per assegnare. Tieni premuto per alias, colore e 📌.",
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
                    Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 4.dp))
                    Text("Nuova etichetta")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toList()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )

    editTarget?.let { name ->
        val t = byName[name]
        LabelEditorDialog(
            title = "Modifica #$name",
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
            nameEditable = false,
        )
    }

    if (creating) {
        LabelEditorDialog(
            title = "Nuova etichetta",
            onConfirm = { name, alias ->
                onCreateTag(name, alias)
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
    onCreateTag: (name: String, alias: String?) -> Unit,
    onDismiss: () -> Unit,
    showRecents: Boolean = true,
    /** When set, a "Rimuovi" mode is offered and its choice is reported here. */
    onRemove: ((List<String>) -> Unit)? = null,
) {
    val chosen: SnapshotStateList<String> = remember { mutableStateListOf() }
    var creating by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val items = if (count == 1) "1 elemento" else "$count elementi"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etichette per $items") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (onRemove != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = !removing, onClick = { removing = false; chosen.clear() },
                            label = { Text("Aggiungi") },
                        )
                        androidx.compose.material3.FilterChip(
                            selected = removing, onClick = { removing = true; chosen.clear() },
                            label = { Text("Rimuovi") },
                        )
                    }
                }
                Text(
                    if (removing) "Le etichette scelte vengono tolte da tutti gli elementi selezionati."
                    else "Le etichette scelte vengono aggiunte a tutti gli elementi selezionati.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (allTags.isEmpty() && chosen.isEmpty()) {
                    Text("Nessuna etichetta. Creane una qui sotto.",
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
                        Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 4.dp))
                        Text("Nuova etichetta")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                if (removing) onRemove?.invoke(chosen.toList()) else onConfirm(chosen.toList())
            }) { Text(if (removing) "Rimuovi" else "Aggiungi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )

    if (creating) {
        LabelEditorDialog(
            title = "Nuova etichetta",
            onConfirm = { name, alias ->
                onCreateTag(name, alias)
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
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Cerca etichetta") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({
            androidx.compose.material3.IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Cancella ricerca") }
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

/** Round colour swatch; [label] marks the special "automatic" choice. */
/** "A" (automatic / default colour) followed by the tag palette; [onPick] gets null for "A". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagColorPicker(
    selected: Int?,
    autoColor: androidx.compose.ui.graphics.Color,
    autoDescription: String,
    onPick: (Int?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorDot(autoColor, selected = selected == null, label = "A", description = autoDescription) { onPick(null) }
        com.cripta.app.ui.theme.TagPalette.forEachIndexed { i, c ->
            val argb = c.toArgb()
            ColorDot(c, selected = selected == argb, description = "Colore ${i + 1}") { onPick(argb) }
        }
    }
}

@Composable
private fun ColorDot(
    c: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    label: String? = null,
    description: String? = null,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier.minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(c)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (label != null) Text(label, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge)
        else if (selected) Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
    }
}

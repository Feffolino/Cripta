package com.cripta.app.ui.vault

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.ui.layout.layout
import com.cripta.app.ui.components.CriptaAlertDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.cripta.app.ui.components.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.cripta.app.data.SortKey
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.readableOn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.first

/**
 * Compact, at-a-glance summary of active filters (search text included) with one-tap removal, and
 * a note that results come from the whole vault. Expands/collapses when filters turn on/off.
 */
@Composable
internal fun ActiveFilterBar(
    filters: Filters,
    tags: List<TagEntity>,
    onType: (TypeFilter) -> Unit,
    onClearFav: () -> Unit,
    onClearUntagged: () -> Unit,
    onTag: (Long) -> Unit,
    onExcludeTag: (Long) -> Unit,
    onClearAll: () -> Unit,
    onClearQuery: () -> Unit,
    people: List<com.cripta.app.data.face.FaceRepository.Person> = emptyList(),
    onPerson: (Long) -> Unit = {},
    tagColors: Boolean = true,
    /** The results really come from the whole vault (see [searchesWholeVault]); else the note is not shown. */
    wholeVault: Boolean = true,
    onClearInProgress: () -> Unit = {},
) {
    // Keep drawing the last active filters while the bar collapses.
    val last = remember { mutableStateOf(filters) }
    SideEffect { if (filters.active) last.value = filters }
    val f = if (filters.active) filters else last.value
    val tagById = remember(tags) { tags.associateBy { it.id } }
    AnimatedVisibility(
        visible = filters.active,
        enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
        exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .animateContentSize(Motion.enter())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (f.query.isNotBlank()) DismissChip("“${f.query}”", str(R.string.search_x, f.query)) { onClearQuery() }
                if (f.type != TypeFilter.ALL) DismissChip(typeLabel(f.type)) { onType(TypeFilter.ALL) }
                if (f.favoritesOnly) DismissChip(str(R.string.favorites)) { onClearFav() }
                if (f.inProgress) DismissChip(str(R.string.continue_watching)) { onClearInProgress() }
                if (f.untaggedOnly) DismissChip(str(R.string.untagged)) { onClearUntagged() }
                f.personIds.forEach { id ->
                    val name = people.firstOrNull { it.id == id }?.title ?: com.cripta.app.data.face.FaceRepository.UNNAMED
                    DismissChip(name, str(R.string.person_x, name)) { onPerson(id) }
                }
                f.tagIds.forEach { id ->
                    // A tag deleted since the filter was saved still narrows the results: show it, removable.
                    val t = tagById[id] ?: run { DismissChip(str(R.string.tag_missing)) { onTag(id) }; return@forEach }
                    TagDismissChip(t, excluded = false, tagColors) { onTag(id) }
                }
                f.excludedTagIds.forEach { id ->
                    val t = tagById[id] ?: run { DismissChip(str(R.string.tag_missing)) { onExcludeTag(id) }; return@forEach }
                    TagDismissChip(t, excluded = true, tagColors) { onExcludeTag(id) }
                }
                TextButton(onClick = onClearAll) { com.cripta.app.ui.components.ButtonText(str(R.string.reset)) }
            }
            // The title still names the folder, but this filter searches everywhere: say so (only then).
            if (wholeVault) Text(str(R.string.results_whole_vault),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
        }
    }
}

/** An active tag filter in the bar: the shared pill with a cross, announced as "Rimuovi filtro …". */
@Composable
private fun TagDismissChip(tag: TagEntity, excluded: Boolean, tagColors: Boolean, onClear: () -> Unit) {
    com.cripta.app.ui.components.TagPill(
        com.cripta.app.ui.components.tagText(tag),
        if (tagColors) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary,
        tagIcon = tag.icon,
        size = com.cripta.app.ui.components.TagPillSize.Medium,
        state = if (excluded) com.cripta.app.ui.components.TagPillState.Excluded else com.cripta.app.ui.components.TagPillState.On,
        base = MaterialTheme.colorScheme.background,
        trailingIcon = CriptaIcons.Close,
        onClick = onClear,
        onClickLabel = str(R.string.remove_filter),
        stateLabel = if (excluded) str(R.string.tag_state_excluded) else str(R.string.included_f),
    )
}

/** Removable filter chip: 48dp touch target, announced as "Rimuovi filtro …". */
@Composable
private fun DismissChip(label: String, description: String = label, onClear: () -> Unit) {
    Surface(onClick = onClear, color = com.cripta.app.ui.components.tonalContainer(), shape = CircleShape) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1, modifier = Modifier.clearAndSetSemantics { this.contentDescription = str(R.string.remove_filter_x, description) })
            Icon(CriptaIcons.Close, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

private enum class TagFilterState { NEUTRAL, INCLUDE, EXCLUDE }

/**
 * Tri-state tag filter: the shared [TagPill]. Neutral = tint, include = full colour + tick,
 * exclude = tint + "no" sign + strike-through. The width barely changes between states (an icon
 * takes the dot's place), so the rows don't reflow under the finger.
 */
@Composable
private fun TagFilterChip(label: String, state: TagFilterState, color: Color, icon: String? = null, onClick: () -> Unit) {
    com.cripta.app.ui.components.TagPill(
        label, color, tagIcon = icon,
        size = com.cripta.app.ui.components.TagPillSize.Medium,
        state = when (state) {
            TagFilterState.INCLUDE -> com.cripta.app.ui.components.TagPillState.On
            TagFilterState.EXCLUDE -> com.cripta.app.ui.components.TagPillState.Excluded
            TagFilterState.NEUTRAL -> com.cripta.app.ui.components.TagPillState.Off
        },
        base = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        triState = true,
        stateLabel = when (state) {
            TagFilterState.INCLUDE -> str(R.string.included_f)
            TagFilterState.EXCLUDE -> str(R.string.tag_state_excluded)
            TagFilterState.NEUTRAL -> str(R.string.not_filtered)
        },
        // What a tap does next, not only what it is now: neither -> include -> exclude -> neither.
        onClickLabel = when (state) {
            TagFilterState.NEUTRAL -> str(R.string.include)
            TagFilterState.INCLUDE -> str(R.string.exclude)
            TagFilterState.EXCLUDE -> str(R.string.tag_filter_clear)
        },
    )
}

/**
 * Sort + filter sheet. A fixed header keeps the live result count and "Azzera" in reach even at half
 * height; below it the groups follow the order people reach for them: order, what to show (type,
 * people, switches), then the (possibly long) tag library, then saved filters. Every change applies at once; the sheet is
 * only closed by the user. Tags keep the library's fixed order so each one is always in its place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSortSheet(
    filters: Filters,
    tags: List<TagEntity>,
    sortKey: SortKey,
    sortAscending: Boolean,
    onType: (TypeFilter) -> Unit,
    onFav: () -> Unit,
    onUntagged: () -> Unit,
    onTag: (Long) -> Unit,
    onExcludeTag: (Long) -> Unit,
    onSort: (SortKey, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    tagColors: Boolean = true,
    onTagMatchAll: (Boolean) -> Unit = {},
    saved: List<com.cripta.app.data.db.SavedFilterEntity> = emptyList(),
    onApplySaved: (String) -> Unit = {},
    onDeleteSaved: (Long) -> Unit = {},
    onSave: (String) -> Unit = {},
    /** Files matching right now, shown in the header (-1 = unknown). */
    resultCount: Int = -1,
    people: List<com.cripta.app.data.face.FaceRepository.Person> = emptyList(),
    onPerson: (Long) -> Unit = {},
    faceCrop: suspend (Long) -> android.graphics.Bitmap? = { null },
) {
    // The shared sheet: opens at half height (drag up for the rest), one swipe down closes it.
    var naming by remember { mutableStateOf(false) }
    // The chip's X used to delete a saved filter at once (easy to hit by mistake): ask first.
    var savedToDelete by remember { mutableStateOf<com.cripta.app.data.db.SavedFilterEntity?>(null) }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss, wideInLandscape = true) {
        val colors = MaterialTheme.colorScheme
        val accent = readableOn(colors.primary, colors.surfaceContainerHigh)
        val textButtonColors = ButtonDefaults.textButtonColors(contentColor = accent)
        // ---- Header (DS): the title and "Azzera"; the result count is on the main button below.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(str(R.string.filters_and_order), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onClear, enabled = filters.active, colors = textButtonColors) {
                com.cripta.app.ui.components.ButtonText(str(R.string.reset))
            }
        }

        // The order first (the group people reach for most, and short), then what to show; the
        // tag library, which can be long, comes last, then the saved sets.
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---- Order: key as one segmented control, direction spelled out.
            FilterGroup(str(R.string.sort_by)) {
                val keys = listOf(SortKey.DATE to str(R.string.sort_date), SortKey.NAME to str(R.string.sort_name), SortKey.SIZE to str(R.string.sort_size), SortKey.MANUAL to str(R.string.sort_manual))
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    keys.forEachIndexed { i, (k, lbl) ->
                        SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                            selected = sortKey == k,
                            onClick = { onSort(k, sortAscending) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, keys.size),
                            icon = {},
                            label = { com.cripta.app.ui.components.OneLineText(lbl) },
                        )
                    }
                }
                // Fixed-height slot so switching to/from Manual doesn't resize (and slide) the sheet.
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = sortKey == SortKey.MANUAL,
                        transitionSpec = { fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.SHORT)) },
                        label = "sortDirection",
                    ) { manual ->
                        if (manual) {
                            Text(str(R.string.drag_to_reorder),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val (descLabel, ascLabel) = when (sortKey) {
                                SortKey.DATE -> str(R.string.order_newest) to str(R.string.order_oldest)
                                SortKey.NAME -> str(R.string.z_to_a) to str(R.string.a_to_z)
                                SortKey.SIZE -> str(R.string.order_largest) to str(R.string.order_smallest)
                                else -> str(R.string.descending) to str(R.string.ascending)
                            }
                            SortDirectionRow(descLabel, ascLabel, sortAscending) { onSort(sortKey, it) }
                        }
                    }
                }
            }

            FilterGroup(str(R.string.type)) {
                com.cripta.app.ui.components.ChipFlowRow {
                    TypeFilter.entries.forEach { t ->
                        com.cripta.app.ui.components.ChoiceChip(selected = filters.type == t, onClick = { onType(t) },
                            label = { com.cripta.app.ui.components.OneLineText(typeLabel(t)) })
                    }
                }
            }

            // ---- People: faces in a row, a tap adds or removes; a file must show everyone chosen.
            if (people.isNotEmpty()) {
                FilterGroup(
                    str(R.string.people),
                    trailing = {
                        val n = filters.personIds.size
                        if (n > 0) Text(plural(R.plurals.chosen_n, n), style = MaterialTheme.typography.labelLarge,
                            color = accent)
                    },
                ) {
                    PeopleFaceRow(people, filters.personIds, faceCrop, onPerson)
                }
            }

            // Two quick narrowing switches (DS: "Solo preferiti" as a switch row).
            Column {
                com.cripta.app.ui.settings.ToggleRow(str(R.string.favorites_only), filters.favoritesOnly, icon = CriptaIcons.StarBorder) { onFav() }
                com.cripta.app.ui.settings.ToggleRow(str(R.string.untagged), filters.untaggedOnly, icon = CriptaIcons.LabelOff) { onUntagged() }
            }

            // ---- Tags: tri-state chips in library order, a search once the library is long.
            if (tags.isNotEmpty()) {
                FilterGroup(
                    str(R.string.tags),
                    trailing = {
                        val n = filters.tagIds.size + filters.excludedTagIds.size
                        if (n > 0) Text(str(R.string.n_chosen, n), style = MaterialTheme.typography.labelLarge,
                            color = accent)
                    },
                ) {
                    Text(str(R.string.tag_filter_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // With two or more tags included: must a file have all of them, or is one enough?
                    // Always in place (disabled below two tags): appearing with the second tag moved
                    // every chip down, so the next tap landed on a different tag.
                    val canMatch = filters.tagIds.size >= 2
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(str(R.string.files_with), style = MaterialTheme.typography.bodyMedium,
                            color = if (canMatch) androidx.compose.ui.graphics.Color.Unspecified
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 12.dp))
                        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                                selected = filters.tagMatchAll, onClick = { onTagMatchAll(true) }, enabled = canMatch,
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                                icon = {}, label = { com.cripta.app.ui.components.OneLineText(str(R.string.tag_match_all)) },
                            )
                            SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
                                selected = !filters.tagMatchAll, onClick = { onTagMatchAll(false) }, enabled = canMatch,
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                                icon = {}, label = { com.cripta.app.ui.components.OneLineText(str(R.string.at_least_one)) },
                            )
                        }
                    }
                    var query by remember { mutableStateOf("") }
                    if (tags.size > 12) {
                        com.cripta.app.ui.components.CriptaTextField(
                            value = query, onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(str(R.string.search_a_tag)) },
                            leadingIcon = { Icon(CriptaIcons.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(CriptaIcons.Close, str(R.string.empty_search)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val q = query.trim()
                    val shown = tags.filter { tagMatches(it, q) }
                    if (shown.isEmpty()) {
                        Text(str(R.string.tag_no_match, q), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    com.cripta.app.ui.components.ChipFlowRow {
                        shown.forEach { tag ->
                            val label = com.cripta.app.ui.components.tagText(tag)
                            val state = when {
                                tag.id in filters.tagIds -> TagFilterState.INCLUDE
                                tag.id in filters.excludedTagIds -> TagFilterState.EXCLUDE
                                else -> TagFilterState.NEUTRAL
                            }
                            TagFilterChip(
                                label = label,
                                state = state,
                                color = if (tagColors) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary,
                                icon = tag.icon,
                                // neutral -> include; include -> exclude; exclude -> neutral.
                                onClick = { if (state == TagFilterState.NEUTRAL) onTag(tag.id) else onExcludeTag(tag.id) },
                            )
                        }
                    }
                }
            }

            if (saved.isNotEmpty()) {
                FilterGroup(str(R.string.saved_filters)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        saved.forEach { sf ->
                            androidx.compose.material3.InputChip(
                                selected = false,
                                onClick = { onApplySaved(sf.json) },
                                label = { com.cripta.app.ui.components.OneLineText(sf.name) },
                                trailingIcon = {
                                    androidx.compose.material3.IconButton(onClick = { savedToDelete = sf }) {
                                        Icon(CriptaIcons.Close, str(R.string.delete_saved_filter_x, sf.name),
                                            modifier = Modifier.size(18.dp))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---- Fixed at the bottom (DS): the result count as the one filled action, then "Salva".
        val closeSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.cripta.app.ui.components.Button(onClick = { closeSheet {} }, modifier = Modifier.fillMaxWidth()
                .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) {
                com.cripta.app.ui.components.ButtonText(when {
                    resultCount < 0 -> str(R.string.show_files)
                    resultCount == 1 -> plural(R.plurals.show_n_files, 1, com.cripta.app.ui.components.formatCount(1))
                    else -> plural(R.plurals.show_n_files, resultCount, com.cripta.app.ui.components.formatCount(resultCount))
                })
            }
            TextButton(onClick = { naming = true }, enabled = filters.active, colors = textButtonColors,
                modifier = Modifier.padding(top = 4.dp)) {
                com.cripta.app.ui.components.ButtonText(str(R.string.save_these_filters))
            }
        }
    }
    if (naming) {
        TextPromptDialog(str(R.string.save_filter), str(R.string.filter_name_hint), confirmLabel = str(R.string.save),
            onConfirm = { onSave(it); naming = false },
            onDismiss = { naming = false })
    }
    savedToDelete?.let { sf ->
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { savedToDelete = null },
            title = { Text(str(R.string.delete_saved_filter_q)) },
            text = { Text(str(R.string.delete_saved_filter_text, sf.name)) },
            confirmButton = {
                TextButton(onClick = { onDeleteSaved(sf.id); savedToDelete = null }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { savedToDelete = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
}

/** Faces in a row (big, name below), a tap on one calls [onPerson]; the chosen ones carry the accent ring. */
@Composable
internal fun PeopleFaceRow(
    people: List<com.cripta.app.data.face.FaceRepository.Person>,
    chosen: Set<Long>,
    faceCrop: suspend (Long) -> android.graphics.Bitmap?,
    onPerson: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        people.forEach { p ->
            val on = p.id in chosen
            val action = str(if (on) R.string.deselect else R.string.select)
            Column(
                Modifier.width(84.dp).clip(MaterialTheme.shapes.small)
                    .semantics { onClick(label = action, action = null) }
                    .toggleable(value = on, role = androidx.compose.ui.semantics.Role.Checkbox) { onPerson(p.id) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                com.cripta.app.ui.settings.FaceAvatar(p.cover, faceCrop, 68.dp, selected = on)
                Text(p.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** The two directions of an order as one segmented control, descending first ("Più recenti" / "Meno recenti"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortDirectionRow(descLabel: String, ascLabel: String, ascending: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
            selected = !ascending, onClick = { onChange(false) },
            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
            icon = { Icon(CriptaIcons.ArrowDownward, null, Modifier.size(18.dp)) },
            label = { com.cripta.app.ui.components.OneLineText(descLabel) },
        )
        SegmentedButton(colors = com.cripta.app.ui.components.criptaSegmentedColors(),
            selected = ascending, onClick = { onChange(true) },
            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
            icon = { Icon(CriptaIcons.ArrowUpward, null, Modifier.size(18.dp)) },
            label = { com.cripta.app.ui.components.OneLineText(ascLabel) },
        )
    }
}

/** A titled group of the filter sheet (title row with an optional trailing element, then content). */
@Composable
internal fun FilterGroup(
    title: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val colors = MaterialTheme.colorScheme
            Text(title, style = com.cripta.app.ui.theme.HeadingStyle,
                color = readableOn(colors.primary, colors.surfaceContainerHigh),
                modifier = Modifier.weight(1f).semantics { heading() })
            trailing()
        }
        content()
    }
}

/** The sort as a chip label: "Più recenti", "A → Z"… */
internal fun sortChipLabel(key: com.cripta.app.data.SortKey, ascending: Boolean): String = when (key) {
    com.cripta.app.data.SortKey.DATE -> if (ascending) str(R.string.order_oldest) else str(R.string.order_newest)
    com.cripta.app.data.SortKey.NAME -> if (ascending) str(R.string.a_to_z) else str(R.string.z_to_a)
    com.cripta.app.data.SortKey.SIZE -> if (ascending) str(R.string.order_smallest) else str(R.string.order_largest)
    com.cripta.app.data.SortKey.MANUAL -> str(R.string.manual_order)
}

/**
 * DS chip row under the search pill. The tab's root: "Solo preferiti", "Senza etichette" and the
 * sort; inside a folder: the type ("Tutti", "Foto", "Video") and "Preferiti". On = accent with a tick.
 */
@Composable
internal fun QuickChips(
    atRoot: Boolean,
    filters: Filters,
    sortLabel: String,
    onType: (TypeFilter) -> Unit,
    onFavorites: () -> Unit,
    onUntagged: () -> Unit,
    onSort: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // The chips' 48dp touch area adds 6dp above and below: 12dp from the search, 20 to the grid (DS).
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    ) {
        if (atRoot) {
            item {
                com.cripta.app.ui.components.ChoiceChip(filters.favoritesOnly, onFavorites, { com.cripta.app.ui.components.OneLineText(str(R.string.favorites_only)) },
                    leadingIcon = { Icon(if (filters.favoritesOnly) CriptaIcons.Star else CriptaIcons.StarBorder, null, Modifier.size(18.dp)) })
            }
            item { com.cripta.app.ui.components.ChoiceChip(filters.untaggedOnly, onUntagged, { com.cripta.app.ui.components.OneLineText(str(R.string.untagged)) }) }
            item {
                com.cripta.app.ui.components.ActionChip(onSort, { com.cripta.app.ui.components.OneLineText(sortLabel) },
                    leadingIcon = { Icon(CriptaIcons.Sort, null, Modifier.size(18.dp)) })
            }
        } else {
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.ALL, { onType(TypeFilter.ALL) }, { com.cripta.app.ui.components.OneLineText(str(R.string.all)) }) }
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.IMAGE, { onType(TypeFilter.IMAGE) }, { com.cripta.app.ui.components.OneLineText(str(R.string.photo)) }) }
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.VIDEO, { onType(TypeFilter.VIDEO) }, { com.cripta.app.ui.components.OneLineText(str(R.string.video)) }) }
            // Notes, PDFs and other files: the sheet's fourth type, reachable here too.
            item { com.cripta.app.ui.components.ChoiceChip(filters.type == TypeFilter.OTHER, { onType(TypeFilter.OTHER) }, { com.cripta.app.ui.components.OneLineText(str(R.string.other)) }) }
            item {
                com.cripta.app.ui.components.ChoiceChip(filters.favoritesOnly, onFavorites, { com.cripta.app.ui.components.OneLineText(str(R.string.favorites)) },
                    leadingIcon = { Icon(if (filters.favoritesOnly) CriptaIcons.Star else CriptaIcons.StarBorder, null, Modifier.size(18.dp)) })
            }
        }
    }
}

package com.cripta.app.ui.settings

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import com.cripta.app.ui.components.TextButton
import androidx.compose.foundation.layout.widthIn
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A group of options: heading, optional explanation, the options. No frame or card around it:
 * the heading and the space before the next group do the grouping.
 */
@Composable
internal fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    // The explanation sits close under its heading (it belongs to it), the options a step further.
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/**
 * The opening of a page that is about one feature (Persone, Intro e finale): what it does in a
 * sentence or two, no heading (the top bar already names the page), then its state and main action.
 */
@Composable
internal fun PageIntro(text: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Named section of a page, so a search result can find it. */
internal fun sec(key: String, content: @Composable () -> Unit): Pair<String, @Composable () -> Unit> = key to content

/** Briefly marks the section a search result landed on: a soft tint behind it that fades out. */
@Composable
internal fun SectionMark(on: Boolean, content: @Composable () -> Unit) {
    val tint by androidx.compose.animation.animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        tween(if (on) Motion.MEDIUM else Motion.LONG * 3, 0, Motion.EaseOutQuart), label = "sectionMark",
    )
    val shape = MaterialTheme.shapes.medium
    Box(Modifier.drawBehind {
        val pad = 10.dp.toPx()
        val outline = shape.createOutline(
            androidx.compose.ui.geometry.Size(size.width + 2 * pad, size.height + 2 * pad), layoutDirection, this)
        translate(-pad, -pad) { drawOutline(outline, tint) }
    }) { content() }
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    desc: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    // The whole row is one switch (label included) for touch and TalkBack; the Switch only draws.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp).size(24.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            // Title in medium weight, its caption close under it: the pair reads as one item.
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(checked = checked, onCheckedChange = null, colors = com.cripta.app.ui.components.criptaSwitchColors())
    }
}

/** A labelled single choice shown as chips, aligned with the toggle rows (icon column). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** Sub-option of the toggle above: 36dp lines it up under a toggle with an icon, 0dp in line with one without. */
    indent: androidx.compose.ui.unit.Dp = 36.dp,
    /** What the choice does, under the chips (aligned with them). */
    desc: String? = null,
    onPick: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp, top = 2.dp).size(24.dp))
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.width(indent))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            com.cripta.app.ui.components.ChipFlowRow {
                options.forEach { (v, l) -> com.cripta.app.ui.components.ChoiceChip(selected = v == selected, onClick = { onPick(v) }, label = { Text(l) }) }
            }
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** DS radio row: the ring (accent with its dot when chosen), the title, its caption under it. The whole row is the choice. */
@Composable
internal fun RadioRow(title: String, caption: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** A page entry: plain row with its icon, name and current-state summary. No frame around it. */
@Composable
internal fun PageRow(p: SettingsPage, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(p.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Icon(CriptaIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(text, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
        // DS: 32dp between groups, 10 from a heading to its rows.
        modifier = Modifier.padding(top = 32.dp, bottom = 10.dp).semantics { heading() })
}

/**
 * The language Cripta is in. On Android 13+ the row opens the system's per-app language page;
 * before that the app follows the phone's language and the row only says so.
 */
@Composable
internal fun LanguageRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val locale = com.cripta.app.Strings.locale
    val current = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    val perApp = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .then(if (perApp) Modifier.clickable(onClickLabel = str(R.string.language_pick)) {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                        android.net.Uri.fromParts("package", ctx.packageName, null)))
                }
            } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(current, style = MaterialTheme.typography.bodyLarge)
            Text(str(if (perApp) R.string.language_pick else R.string.language_follows_system),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (perApp) {
            Icon(CriptaIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The tag library in Impostazioni › Etichette. Each tag is a plain row (no frame) showing the tag
 * as it looks everywhere else. A tap edits it (name, alias, icon, colour, pin); the rare delete is
 * in the row's ⋮, in the error colour.
 *
 * With [reorderable] (the custom order) a tag is moved by dragging, like files in the vault grid:
 * the handle at the row's end drags at once, a long press anywhere on the row too. The row lifts
 * onto a tonal surface and follows the finger, the others slide out of its way (220 ms ease-out),
 * a light tick marks each new place; on release it settles into its slot and [onMoveTo] saves
 * the order. TalkBack gets "Su" / "Giù" actions on the row instead of the gesture.
 */
@Composable
internal fun TagLibraryList(
    tags: List<com.cripta.app.data.db.TagEntity>,
    reorderable: Boolean,
    colorOf: @Composable (com.cripta.app.data.db.TagEntity) -> Color,
    /** Files the tag is on (null = not known yet): shown at the row's end. */
    countOf: (com.cripta.app.data.db.TagEntity) -> Int?,
    onEdit: (com.cripta.app.data.db.TagEntity) -> Unit,
    onDelete: (com.cripta.app.data.db.TagEntity) -> Unit,
    onMoveStep: (com.cripta.app.data.db.TagEntity, up: Boolean) -> Unit,
    onMoveTo: (com.cripta.app.data.db.TagEntity, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // The order just dropped, shown until the saved one comes back (no flash of the old order).
    var pending by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Long>?>(null) }
    androidx.compose.runtime.LaunchedEffect(tags) { pending = null }
    val shown = pending?.let { ids -> val byId = tags.associateBy { it.id }; ids.mapNotNull { byId[it] } } ?: tags
    val shownNow by androidx.compose.runtime.rememberUpdatedState(shown)

    var dragId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var dragDy by androidx.compose.runtime.remember { mutableFloatStateOf(0f) }
    var rowH by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    // Each row's own height: with large text a pill can wrap and make its row taller than the others.
    val heights = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<Long, Int>() }
    fun heightOf(id: Long): Int = heights[id] ?: rowH
    /** Where a row dragged by [dy] from [fromIdx] lands: past a neighbour once over half of it. */
    fun targetOf(list: List<com.cripta.app.data.db.TagEntity>, fromIdx: Int, dy: Float): Int {
        if (fromIdx < 0 || rowH == 0) return fromIdx
        var t = fromIdx; var rest = dy
        if (dy > 0) while (t < list.size - 1) { val h = heightOf(list[t + 1].id); if (rest < h / 2f) break; rest -= h; t++ }
        else while (t > 0) { val h = heightOf(list[t - 1].id); if (-rest < h / 2f) break; rest += h; t-- }
        return t
    }
    // The dropped row glides from where the finger left it into its slot.
    var settleId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    val settle = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    // A long press that ends without moving still lets the row see a tap on release: from the
    // start of a drag until just after the drop, a tap is the end of that press, not an edit.
    var swallowTap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val from = dragId?.let { id -> shown.indexOfFirst { it.id == id } } ?: -1
    val target = targetOf(shown, from, dragDy)
    val draggedH = dragId?.let { heightOf(it) } ?: rowH
    var lastTarget by androidx.compose.runtime.remember { mutableIntStateOf(-1) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (from >= 0 && lastTarget >= 0 && target != lastTarget) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
        lastTarget = target
    }

    fun start(id: Long) {
        if (dragId != null) return
        scope.launch { settle.snapTo(0f) }
        settleId = null; dragDy = 0f; dragId = id; lastTarget = -1; swallowTap = true
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    fun drop() {
        val id = dragId ?: return
        val list = shownNow
        val f = list.indexOfFirst { it.id == id }
        val t = targetOf(list, f, dragDy)
        val passed = when {
            f < 0 || t == f -> 0
            t > f -> (f + 1..t).sumOf { heightOf(list[it].id) }
            else -> -(t until f).sumOf { heightOf(list[it].id) }
        }
        val residual = dragDy - passed
        if (f >= 0 && t != f) {
            val ids = list.map { it.id }.toMutableList()
            ids.add(t, ids.removeAt(f))
            pending = ids
            onMoveTo(list[f], t)
        }
        settleId = id
        scope.launch { kotlinx.coroutines.delay(300); swallowTap = false }
        dragId = null; dragDy = 0f
        scope.launch {
            settle.snapTo(residual)
            settle.animateTo(0f, com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.MEDIUM))
            settleId = null
        }
    }

    /** A gesture taken over by the system (or another touch): the row goes back, nothing is saved. */
    fun cancel() {
        val id = dragId ?: return
        val residual = dragDy
        settleId = id
        scope.launch { kotlinx.coroutines.delay(300); swallowTap = false }
        dragId = null; dragDy = 0f
        scope.launch {
            settle.snapTo(residual)
            settle.animateTo(0f, com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.MEDIUM))
            settleId = null
        }
    }

    Column(modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, tag ->
            androidx.compose.runtime.key(tag.id) {
                val dragging = dragId == tag.id
                // The others make room: rows between the start and the target slide by one row.
                val shift = when {
                    from < 0 || dragging -> 0f
                    index in (from + 1)..target -> -draggedH.toFloat()
                    index in target until from -> draggedH.toFloat()
                    else -> 0f
                }
                val slide by androidx.compose.animation.core.animateFloatAsState(
                    shift, com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.MEDIUM), label = "tagRowMakeRoom")
                val lift by androidx.compose.animation.core.animateFloatAsState(
                    if (dragging) 1f else 0f, com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.SHORT), label = "tagRowLift")
                val lifted = MaterialTheme.colorScheme.surfaceContainerHigh
                TagLibraryRow(
                    tag = tag,
                    color = colorOf(tag),
                    count = countOf(tag),
                    reorderable = reorderable,
                    canMoveUp = index > 0,
                    canMoveDown = index < shown.size - 1,
                    onEdit = { if (!swallowTap) onEdit(tag) },
                    onDelete = { onDelete(tag) },
                    onMoveStep = { up -> onMoveStep(tag, up) },
                    handleModifier = Modifier.pointerInput(tag.id) {
                        detectDragGestures(
                            onDragStart = { start(tag.id) },
                            onDrag = { change, d -> change.consume(); dragDy += d.y },
                            onDragEnd = { drop() }, onDragCancel = { cancel() },
                        )
                    },
                    modifier = Modifier
                        .onSizeChanged { if (it.height > 0) { rowH = it.height; heights[tag.id] = it.height } }
                        .zIndex(if (dragging || settleId == tag.id) 1f else 0f)
                        .graphicsLayer {
                            // Read the drag state here (not through an effect) so a drop never
                            // shows one frame of the old offsets.
                            translationY = when {
                                dragging -> dragDy
                                settleId == tag.id -> settle.value
                                dragId == null -> 0f
                                else -> slide
                            }
                            val sc = 1f + 0.02f * lift
                            scaleX = sc; scaleY = sc
                        }
                        .drawBehind {
                            if (lift > 0f) drawRoundRect(lifted.copy(alpha = lifted.alpha * lift),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
                        }
                        .then(if (reorderable) Modifier.pointerInput(tag.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start(tag.id) },
                                onDrag = { change, d -> change.consume(); dragDy += d.y },
                                onDragEnd = { drop() }, onDragCancel = { cancel() },
                            )
                        } else Modifier),
                )
            }
        }
    }
}

/**
 * One row of [TagLibraryList]: the pill, a pin when the tag is pinned, how many files it is on
 * (tabular figures, grouped), the ⋮ (Modifica, Elimina) and, in the custom order, the drag handle.
 */
@Composable
private fun TagLibraryRow(
    tag: com.cripta.app.data.db.TagEntity,
    color: Color,
    count: Int?,
    reorderable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveStep: (up: Boolean) -> Unit,
    handleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val upLabel = str(R.string.up)
    val downLabel = str(R.string.down)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = str(R.string.edit_name_alias_colour), onClick = onEdit)
            .semantics {
                if (reorderable) customActions = listOfNotNull(
                    if (canMoveUp) androidx.compose.ui.semantics.CustomAccessibilityAction(upLabel) { onMoveStep(true); true } else null,
                    if (canMoveDown) androidx.compose.ui.semantics.CustomAccessibilityAction(downLabel) { onMoveStep(false); true } else null,
                )
            }
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(end = 8.dp)) {
            com.cripta.app.ui.components.TagPill(
                com.cripta.app.ui.components.tagText(tag), color,
                size = com.cripta.app.ui.components.TagPillSize.Medium,
                base = MaterialTheme.colorScheme.background,
                tagIcon = tag.icon,
            )
        }
        if (tag.pinned) {
            Icon(CriptaIcons.PushPin, str(R.string.tag_pinned), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp).size(18.dp))
        }
        if (count != null) {
            val n = com.cripta.app.ui.components.formatCount(count)
            Text(n, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.widthIn(min = 40.dp).semantics { contentDescription = plural(R.plurals.files_n, count, n) })
        }
        Box {
            androidx.compose.material3.IconButton(onClick = { menu = true }) {
                Icon(CriptaIcons.MoreVert, str(R.string.more_actions_for, tag.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.DropdownMenu(menu && com.cripta.app.ui.components.vaultUnlocked(), { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(str(R.string.edit)) },
                    leadingIcon = { Icon(CriptaIcons.Edit, null) },
                    onClick = { menu = false; onEdit() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(str(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(CriptaIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
        if (reorderable) {
            // The handle: a 48dp target that drags at once (no long press needed). TalkBack uses
            // the row's Su / Giù actions, so the handle itself is not announced.
            Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(CriptaIcons.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * "Unisci due etichette": pick the tag that goes, then the one that takes its files; a last line
 * says what will happen before "Unisci". The first tag's files get the second, the first is deleted.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MergeTagsDialog(
    tags: List<com.cripta.app.data.db.TagEntity>,
    colorOf: @Composable (com.cripta.app.data.db.TagEntity) -> Color,
    onMerge: (from: com.cripta.app.data.db.TagEntity, into: com.cripta.app.data.db.TagEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromId by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var intoId by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    val from = tags.firstOrNull { it.id == fromId }
    val into = tags.firstOrNull { it.id == intoId && it.id != fromId }
    @Composable
    fun choices(title: String, chosen: Long?, skip: Long?, pick: (Long) -> Unit) {
        Text(title, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
        com.cripta.app.ui.components.ChipFlowRow {
            tags.filter { it.id != skip }.forEach { t ->
                val on = t.id == chosen
                com.cripta.app.ui.components.TagPill(
                    com.cripta.app.ui.components.tagText(t), colorOf(t),
                    size = com.cripta.app.ui.components.TagPillSize.Medium,
                    state = if (on) com.cripta.app.ui.components.TagPillState.On else com.cripta.app.ui.components.TagPillState.Off,
                    base = MaterialTheme.colorScheme.surfaceContainerHigh, tagIcon = t.icon,
                    onClick = { pick(t.id) }, stateLabel = if (on) str(R.string.chosen) else str(R.string.not_chosen),
                )
            }
        }
    }
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.merge_tags)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                choices(str(R.string.merge_pick_from), fromId, null) { fromId = it; if (intoId == it) intoId = null }
                if (from != null) {
                    Spacer(Modifier.height(24.dp))
                    choices(str(R.string.merge_pick_into), intoId, from.id) { intoId = it }
                }
                if (from != null && into != null) {
                    Text(str(R.string.merge_confirm_text, from.name, into.name), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 20.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = from != null && into != null, onClick = { if (from != null && into != null) onMerge(from, into) }) {
                com.cripta.app.ui.components.ButtonText(str(R.string.merge))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

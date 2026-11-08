package com.cripta.app.ui.settings

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

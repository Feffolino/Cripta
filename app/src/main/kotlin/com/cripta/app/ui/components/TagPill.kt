package com.cripta.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.theme.readableOn

/** Size of a [TagPill]: on covers and list rows, in pickers and filters, in the viewer's bar. */
enum class TagPillSize { Small, Medium, Large }

/**
 * What a [TagPill] says. [Plain] only shows a tag; [Off] / [On] are a choice (assigned or not,
 * filtered or not); [Excluded] is a filter that hides the tag. On and Excluded carry an icon too:
 * the state is never told by colour alone.
 */
enum class TagPillState { Plain, Off, On, Excluded }

/** Dark glass behind tags and badges drawn over an image: readable on any picture, in either theme. */
val OnImageGlass = Color(0xEB14171E)

/** A tag's text: its alias (emoji/acronym) before the name, when it has one. */
fun tagText(tag: TagEntity): String =
    if (!tag.alias.isNullOrBlank()) "${tag.alias} ${tag.name}" else tag.name

/**
 * The one tag component of the app: a round pill tinted with the tag's colour. Used on covers, in
 * list rows, filters, tag pickers, the viewer's quick bar, Home and the downloader, in three sizes.
 *
 * - Plain / Off: the colour at 20% over [base], the name in the same hue pushed to >= 4.5:1.
 * - On: the full colour with a tick, the text white or black, whichever reads on it.
 * - Excluded: tinted, struck through, with a "no" sign.
 *
 * [onImage] draws it over a picture (covers, viewer): the tint sits on a dark glass instead of
 * the page surface. [base] is the colour behind the pill, for the contrast maths.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: TagPillSize = TagPillSize.Small,
    state: TagPillState = TagPillState.Plain,
    onImage: Boolean = false,
    base: Color = MaterialTheme.colorScheme.surface,
    /** Icon before the name when Off/Plain (e.g. a pin); the colour dot otherwise (Medium/Large). */
    leadingIcon: ImageVector? = null,
    /** Icon after the name, e.g. a close cross on a removable filter. */
    trailingIcon: ImageVector? = null,
    maxWidth: Dp = Dp.Unspecified,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    /** TalkBack text for the state; defaults to Italian names of [state]. */
    stateLabel: String? = null,
) {
    val under = if (onImage) OnImageGlass else base
    val on = state == TagPillState.On
    val bg = remember(color, under, on) { if (on) color else color.copy(alpha = 0.20f).compositeOver(under) }
    val fg = remember(color, bg, on) { if (on) readableOn(Color.White, bg) else readableOn(color, bg, 4.8) }
    val style = when (size) {
        TagPillSize.Small -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.labelLarge
    }
    val hPad = when (size) { TagPillSize.Small -> 7.dp; TagPillSize.Medium -> 12.dp; TagPillSize.Large -> 14.dp }
    val vPad = when (size) { TagPillSize.Small -> 2.dp; TagPillSize.Medium -> 6.dp; TagPillSize.Large -> 8.dp }
    val minH = when (size) { TagPillSize.Small -> 0.dp; TagPillSize.Medium -> 32.dp; TagPillSize.Large -> 36.dp }
    val iconSize = if (size == TagPillSize.Small) 12.dp else 16.dp

    var m = modifier
    if (onClick != null) {
        val toggle = state == TagPillState.On || state == TagPillState.Off
        m = m.minimumInteractiveComponentSize().clip(CircleShape)
            .combinedClickable(
                role = if (toggle) Role.Checkbox else Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics {
                if (toggle) selected = on
                stateDescription = stateLabel ?: when (state) {
                    TagPillState.On -> "Attiva"
                    TagPillState.Off -> "Non attiva"
                    TagPillState.Excluded -> "Esclusa"
                    TagPillState.Plain -> ""
                }
            }
    }
    if (maxWidth != Dp.Unspecified) m = m.widthIn(max = maxWidth)
    Row(
        m.heightIn(min = minH).clip(CircleShape).background(bg).padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lead: ImageVector? = when (state) {
            TagPillState.On -> Icons.Filled.Check
            TagPillState.Excluded -> Icons.Filled.Block
            else -> leadingIcon
        }
        if (lead != null) {
            Icon(lead, null, tint = fg, modifier = Modifier.padding(end = 4.dp).size(iconSize))
        } else if (size != TagPillSize.Small) {
            // The tag's colour at full strength, so every tag is recognisable even when unselected.
            Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(fg))
        }
        Text(
            text,
            style = style,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (state == TagPillState.Excluded) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, null, tint = fg, modifier = Modifier.padding(start = 4.dp).size(iconSize))
        }
    }
}

/** Neutral "+N" pill in the same shape, for tags that didn't fit. */
@Composable
fun MoreTagsPill(count: Int, onImage: Boolean, modifier: Modifier = Modifier) {
    val bg = if (onImage) OnImageGlass else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (onImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.clip(CircleShape).background(bg).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text("+$count", style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

package com.cripta.app.ui.components

import com.cripta.app.ui.theme.popOn
import com.cripta.app.ui.theme.Motion
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import com.cripta.app.R
import com.cripta.app.str

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.cripta.app.data.db.TagEntity

/** Size of a [TagPill]: on covers and list rows, in pickers and filters, in the viewer's bar. */
enum class TagPillSize { Small, Medium, Large }

/**
 * What a [TagPill] says. [Plain] only shows a tag; [Off] / [On] are a choice (assigned or not,
 * filtered or not); [Excluded] is a filter that hides the tag. On and Excluded carry an icon too:
 * the state is never told by colour alone.
 */
enum class TagPillState { Plain, Off, On, Excluded }

/**
 * DS `glass`: see-through black behind tags and badges drawn over an image. The DS draws 40% black
 * over a blur; without the blur 55% keeps white text >= 4.5:1 even over a white picture.
 */
val OnImageGlass = Color(0x8C000000)

/** How much of its colours a tag's icon keeps on an Off pill. */
private const val OFF_ICON_ALPHA = 0.55f

/** A tag's text: its acronym before the name, when it has one. */
fun tagText(tag: TagEntity): String =
    if (!tag.alias.isNullOrBlank()) "${tag.alias} ${tag.name}" else tag.name

/**
 * The one tag component of the app: a round pill in the tag's colour. Used on covers, in list
 * rows, filters, tag pickers, the viewer's quick bar, Home and the downloader, in three sizes.
 *
 * Colour means "this tag is on the file / in the filter"; neutral means "not":
 * - Plain (a tag shown on a file): the tag's colour in full, as the user chose it, black or white
 *   text (whichever reads at 4.5:1). Over a picture the same colour at 90% on the dark glass,
 *   with a rim of the same colour.
 * - On (assigned / filtered): the same full colour and text, plus a tick. Told apart from Off at a
 *   glance even when every tag has one colour (tag colours off).
 * - Off (not assigned): a neutral pill, muted text, only a small dot in the tag's colour.
 * - Excluded: neutral, struck through, with a "no" sign.
 *
 * On, Off and Excluded take the same width (the tick takes the room of the dot, the pin or the
 * tag's icon): a tap never moves the tags after it, nor wraps them to another line.
 *
 * [onImage] draws it over a picture (covers, viewer). [base] is the colour behind the pill, for
 * the contrast maths.
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
    /** The tag's own icon ([com.cripta.app.ui.tagicons.TagIcons] name), before the text. */
    tagIcon: String? = null,
) {
    val resolved = com.cripta.app.ui.tagicons.rememberResolvedTagIcon(tagIcon)
    // With tag colours off (Settings › Etichette) every tag takes the accent, here for every screen.
    val chosen = if (com.cripta.app.ui.theme.TagColorsEnabled.on) color else MaterialTheme.colorScheme.primary
    // On a page of the light theme a pastel takes a fuller tone of its hue; over a picture it stays.
    val color = if (onImage) chosen else com.cripta.app.ui.theme.shownTagColor(chosen)
    val on = state == TagPillState.On
    val colored = on || state == TagPillState.Plain
    val neutral = if (onImage) OnImageGlass else tonalContainer()
    val muted = if (onImage) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    // The tag's colour as the user chose it, never darkened or tinted down; over a picture it
    // lets the picture through a little (90% on the dark glass).
    val bg = when {
        !colored -> neutral
        onImage -> color.copy(alpha = 0.9f).compositeOver(OnImageGlass)
        else -> color.compositeOver(base)
    }
    val rim = if (colored && onImage) color.copy(alpha = 0.85f) else null
    val fg = remember(bg, colored, muted) {
        // Black or white, whichever reads at 4.5:1 on the colour (black on every preset).
        if (!colored) muted else if (bg.luminance() >= 0.175f) Color.Black else Color.White
    }
    // DS cr-tag: sm 22dp / 11sp bold, md 32dp / 13sp, lg 40dp / 14sp.
    val style = when (size) {
        TagPillSize.Small -> MaterialTheme.typography.labelSmall
        TagPillSize.Medium -> MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
        TagPillSize.Large -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    }
    val hPad = when (size) { TagPillSize.Small -> 8.dp; TagPillSize.Medium -> 12.dp; TagPillSize.Large -> 16.dp }
    val vPad = when (size) { TagPillSize.Small -> 2.dp; TagPillSize.Medium -> 6.dp; TagPillSize.Large -> 8.dp }
    val minH = when (size) { TagPillSize.Small -> 22.dp; TagPillSize.Medium -> 32.dp; TagPillSize.Large -> 40.dp }
    val iconSize = if (size == TagPillSize.Small) 12.dp else 16.dp

    // A pill that toggles eases between Off and On (colours over SHORT, the tick pops) so the change
    // is seen under the finger; pills that only show a tag skip the animation state entirely.
    val toggles = onClick != null
    val shownBg = if (toggles) animateColorAsState(bg, tween(Motion.SHORT, 0, Motion.EaseOutQuart), label = "tagBg").value else bg
    val shownFg = if (toggles) animateColorAsState(fg, tween(Motion.SHORT, 0, Motion.EaseOutQuart), label = "tagFg").value else fg
    val markPop = if (toggles) Modifier.popOn(on) else Modifier

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
                    TagPillState.On -> str(R.string.tag_state_on)
                    TagPillState.Off -> str(R.string.tag_state_off)
                    TagPillState.Excluded -> str(R.string.tag_state_excluded)
                    TagPillState.Plain -> ""
                }
            }
    }
    if (maxWidth != Dp.Unspecified) m = m.widthIn(max = maxWidth)
    Row(
        m.heightIn(min = minH).clip(CircleShape).background(shownBg)
            .then(if (rim != null) Modifier.border(1.dp, rim, CircleShape) else Modifier)
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tick (or the "no" sign) takes the place of what leads when off (the pin, else the
        // tag's icon, else the dot), in a slot of one size: a tap never changes the pill's width.
        val mark = when (state) { TagPillState.On -> CriptaIcons.Check; TagPillState.Excluded -> CriptaIcons.Block; else -> null }
        val markOnIcon = mark != null && resolved != null && leadingIcon == null
        val lead: ImageVector? = if (markOnIcon) null else mark ?: leadingIcon
        if (lead != null) {
            Icon(lead, null, tint = shownFg, modifier = Modifier.padding(end = 4.dp).size(iconSize).then(if (mark != null) markPop else Modifier))
        } else if (resolved == null) {
            // The tag's own colour as a dot (DS cr-tag__dot: 6dp small, 8dp otherwise), so every
            // tag is recognisable even when it is off.
            Box(Modifier.padding(end = 4.dp).size(if (size == TagPillSize.Small) 6.dp else iconSize), contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (size == TagPillSize.Small) 6.dp else 8.dp).clip(CircleShape).background(if (colored) shownFg else color))
            }
        }
        if (resolved != null) {
            // Its room is kept while the icon loads, so the pill never grows under the finger.
            val drawn = com.cripta.app.ui.tagicons.rememberTagIcon(resolved)
            Box(Modifier.padding(end = if (text.isEmpty()) 0.dp else 4.dp).size(iconSize + 2.dp)) {
                if (markOnIcon && mark != null) Icon(mark, null, tint = shownFg, modifier = Modifier.matchParentSize().then(markPop))
                // The icon keeps its own colours (it is not tinted); Off, the only state that shows it
                // beside a neutral pill, dims it.
                else if (drawn != null) Icon(drawn, null, tint = Color.Unspecified,
                    modifier = Modifier.matchParentSize().alpha(if (state == TagPillState.Off) OFF_ICON_ALPHA else 1f))
            }
        }
        if (text.isNotEmpty()) Text(
            text,
            style = style,
            color = shownFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (state == TagPillState.Excluded) TextDecoration.LineThrough else null,
            // No weight: inside a LazyRow (unbounded width) a weighted text was measured to 0 and
            // the viewer's quick bar showed empty pills. [maxWidth] bounds it where needed.
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, null, tint = shownFg, modifier = Modifier.padding(start = 4.dp).size(iconSize))
        }
    }
}

/** Neutral "+N" pill in the same shape, for tags that didn't fit. */
@Composable
fun MoreTagsPill(count: Int, onImage: Boolean, modifier: Modifier = Modifier) {
    val bg = if (onImage) OnImageGlass else tonalContainer()
    val fg = if (onImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.heightIn(min = 22.dp).clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text("+$count", style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

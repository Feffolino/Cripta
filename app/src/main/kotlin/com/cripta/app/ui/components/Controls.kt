package com.cripta.app.ui.components

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top bar colours of every page: the bar is the page itself (same background, no band) until the
 * content scrolls under it, then it lifts one tier so the edge stays readable.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun criptaTopBarColors(): androidx.compose.material3.TopAppBarColors =
    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

/**
 * Top bar colours driven by where the content really is ([lifted]: something is scrolled under
 * the bar), not by the scroll behaviour's sum of scroll deltas. That sum starts at zero on every
 * page, so a page opening already scrolled (a remembered folder, a search result's section, a
 * list restored on return) showed the flat bar until the first drag, and a page change kept the
 * previous page's colour.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun criptaTopBarColors(lifted: Boolean): androidx.compose.material3.TopAppBarColors {
    val color by androidx.compose.animation.animateColorAsState(
        if (lifted) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
        androidx.compose.animation.core.tween(com.cripta.app.ui.theme.Motion.SHORT), label = "topBarLift",
    )
    return androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = color, scrolledContainerColor = color)
}

/**
 * Everything fixed between a page's top bar and its scrolling content (search field, chips,
 * banners, strips) as one header with the bar: when the content scrolls under it, the whole
 * header lifts one tier together with the bar (pass the same [lifted] as [criptaTopBarColors]),
 * so the bar never lifts alone and looks detached from what sits under it.
 *
 * The tonal pieces inside move one tier up too, so they keep standing out on the lifted header:
 * fields and cover tiles (normally `surfaceContainer`, the lifted header's own colour) take
 * `surfaceContainerHigh`, and everything filled with [tonalContainer] (chips, removable filters,
 * neutral tag pills, progress banners) gets a touch of `onSurface`, the way the surface tiers step
 * (lighter in dark, darker in light).
 */
@Composable
fun LiftedHeader(
    lifted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spec = androidx.compose.animation.core.tween<Color>(com.cripta.app.ui.theme.Motion.SHORT)
    val background by androidx.compose.animation.animateColorAsState(
        if (lifted) scheme.surfaceContainer else scheme.background, spec, label = "headerLift")
    val field by androidx.compose.animation.animateColorAsState(
        if (lifted) scheme.surfaceContainerHigh else scheme.surfaceContainer, spec, label = "headerField")
    val tonal by androidx.compose.animation.animateColorAsState(
        if (lifted) scheme.onSurface.copy(alpha = 0.08f).compositeOver(scheme.secondaryContainer) else scheme.secondaryContainer,
        spec, label = "headerTonal")
    CompositionLocalProvider(
        LocalFieldContainer provides field,
        LocalTileContainer provides field,
        LocalTonalContainer provides tonal,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier.fillMaxWidth().background(background),
            content = content,
        )
    }
}

/**
 * The fill of the app's tonal pieces (off chips, removable filters, neutral tag pills, progress
 * banners) where the surface behind them is not the page; null = `secondaryContainer`. On a
 * [LiftedHeader] the plain fill would sit almost on the header's own colour (light theme).
 */
val LocalTonalContainer = androidx.compose.runtime.compositionLocalOf<Color?> { null }

/**
 * The neutral tile behind a cover without its picture ([CoverPlaceholder]); null =
 * `surfaceContainer`. A [LiftedHeader] is itself `surfaceContainer`, so a cover drawn in it (the
 * live preview of Impostazioni › Etichette) moves one tier up to keep its edge.
 */
val LocalTileContainer = androidx.compose.runtime.compositionLocalOf<Color?> { null }

/** See [LocalTonalContainer]. */
@Composable
fun tonalContainer(): Color = LocalTonalContainer.current ?: MaterialTheme.colorScheme.secondaryContainer

/** Whether a page's content is scrolled under its top bar; written by [TrackTopBarLift]. */
@androidx.compose.runtime.Stable
class TopBarLift {
    var lifted by androidx.compose.runtime.mutableStateOf(false)
}

/** Keeps [lift] in step with [state], from the first frame and in both directions. */
@Composable
fun TrackTopBarLift(lift: TopBarLift, state: androidx.compose.foundation.gestures.ScrollableState) {
    androidx.compose.runtime.LaunchedEffect(lift, state) {
        androidx.compose.runtime.snapshotFlow { state.canScrollBackward }.collect { lift.lifted = it }
    }
}

/**
 * Insets of every top bar outside the player: the status bar's height even while it is hidden.
 * The player hides the system bars, and a bar following the visible inset jumped up while the
 * page under the opening player was still on screen (and could stay up after closing it).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
val criptaTopBarInsets: androidx.compose.foundation.layout.WindowInsets
    @Composable get() = androidx.compose.foundation.layout.WindowInsets.systemBarsIgnoringVisibility.only(
        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Top,
    )

/** Shape of multi-line text fields: a soft filled slab (radius-md), no outline. */
val FieldShape = RoundedCornerShape(14.dp)

/** Shape of every single-line field (search, link, name): a full pill, the same everywhere. */
val PillFieldShape = RoundedCornerShape(28.dp)

/**
 * Filled field colours: a tonal slab one tier above the page, no outline and no underline (the
 * app draws no borders). Focus is told by the accent caret and label; an error by the error label
 * and the message under the field.
 */
@Composable
fun criptaFieldColors(): TextFieldColors = criptaFieldColors(LocalFieldContainer.current ?: MaterialTheme.colorScheme.surfaceContainer)

/**
 * The fill of [CriptaTextField] where the surface behind it is not the page: a dialog is itself
 * `surfaceContainerHigh`, so a field there took the dialog's colour and read as plain text.
 * [CriptaAlertDialog] provides a well (`surface`) instead.
 */
val LocalFieldContainer = androidx.compose.runtime.compositionLocalOf<Color?> { null }

@Composable
private fun criptaFieldColors(container: Color): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    disabledContainerColor = container.copy(alpha = 0.6f),
    errorContainerColor = container,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/** The app's text field (replaces Material's outlined one): see [criptaFieldColors]. */
@Composable
fun CriptaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = FieldTextStyle,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier.heightIn(min = if (singleLine) 52.dp else 56.dp), enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = if (singleLine) PillFieldShape else FieldShape, colors = criptaFieldColors(),
    )
}

/** DS `cr-field` text: 15sp medium, in fields and their placeholders. */
val FieldTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)

/**
 * [CriptaTextField] for a [TextFieldValue] (keeps the selection, e.g. a pre-selected name): the
 * same parameters and the same height as the [String] one, so a field looks the same with both.
 */
@Composable
fun CriptaTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = FieldTextStyle,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier.heightIn(min = if (singleLine) 52.dp else 56.dp), enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = if (singleLine) PillFieldShape else FieldShape, colors = criptaFieldColors(),
    )
}

/**
 * The app's choice chip (replaces Material's outlined filter chip): a round pill, neutral tonal
 * when off, accent with a tick when on, never a border. Same shape as [TagPill], so every chip in
 * the app reads as one family.
 */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else tonalContainer()
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .alpha(if (enabled) 1f else 0.5f)
            .padding(start = if (selected || leadingIcon != null) 10.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (selected) {
                Icon(CriptaIcons.Check, null, tint = fg, modifier = Modifier.padding(end = 6.dp).size(18.dp))
            } else if (leadingIcon != null) {
                Row(Modifier.padding(end = 6.dp)) { leadingIcon() }
            }
            ProvideTextStyle(ChipTextStyle.copy(color = fg)) { label() }
        }
    }
}

/** Chip labels (DS `cr-chip`): 13sp bold. */
private val ChipTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp)

/** A one-shot action in chip form (e.g. "Salva questi filtri", a time to jump to): tonal, round. */
@Composable
fun ActionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val fg = MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier.minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(tonalContainer())
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (leadingIcon != null) Row(Modifier.padding(end = 6.dp)) { leadingIcon() }
            ProvideTextStyle(ChipTextStyle.copy(color = fg)) { label() }
        }
    }
}

/**
 * A label that has one line of room (a segmented button, a chip, a tab of the bottom bar). With a
 * large font or display size, or a long translation, it shrinks a little (down to 80 %) to fit,
 * then ends with "…": it never wraps, so a word's last letter can't drop onto a second line.
 */
@Composable
fun OneLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
) {
    val size = style.fontSize.takeIf { it.isSp } ?: 14.sp
    androidx.compose.material3.Text(
        text, modifier, color = color, fontWeight = fontWeight, maxLines = 1, softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = style,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = size * 0.8f, maxFontSize = size, stepSize = 0.5.sp),
    )
}

/**
 * A button's label: up to two lines so longer translations keep the action readable.
 */
@Composable
fun ButtonText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
               style: TextStyle = androidx.compose.material3.LocalTextStyle.current) =
    androidx.compose.material3.Text(text, modifier, color = color, maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = style)

/**
 * A button that throws something away (delete, empty, start over): still a button, never bare
 * text. Neutral tonal like [androidx.compose.material3.FilledTonalButton], the label and icon in
 * the error colour; never filled red, so the view keeps its one filled button. Dialog confirms
 * stay text buttons (see DESIGN.md).
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    com.cripta.app.ui.components.FilledTonalButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(contentColor = MaterialTheme.colorScheme.error),
        contentPadding = if (icon != null) androidx.compose.material3.ButtonDefaults.ButtonWithIconContentPadding
            else androidx.compose.material3.ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(androidx.compose.material3.ButtonDefaults.IconSize))
            androidx.compose.foundation.layout.Spacer(Modifier.size(androidx.compose.material3.ButtonDefaults.IconSpacing))
        }
        ButtonText(text)
    }
}

/**
 * DS `cr-switch`: off = surface-control track with a 2dp ink-muted ring and a muted thumb (a
 * control boundary, 3:1); on = accent track, on-accent thumb.
 */
@Composable
fun criptaSwitchColors(): androidx.compose.material3.SwitchColors = androidx.compose.material3.SwitchDefaults.colors(
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Segmented choices without outlines (DS: no borders as structure): the chosen segment is the
 * accent with on-accent text and its tick, the others surface-control.
 */
@Composable
fun criptaSegmentedColors(): androidx.compose.material3.SegmentedButtonColors = androidx.compose.material3.SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary,
    activeContentColor = MaterialTheme.colorScheme.onPrimary,
    activeBorderColor = Color.Transparent,
    inactiveContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
    inactiveBorderColor = Color.Transparent,
)

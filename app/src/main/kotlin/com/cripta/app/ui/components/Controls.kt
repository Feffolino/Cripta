package com.cripta.app.ui.components

import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

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
 * Insets of every top bar outside the player: the status bar's height even while it is hidden.
 * The player hides the system bars, and a bar following the visible inset jumped up while the
 * page under the opening player was still on screen (and could stay up after closing it).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
val criptaTopBarInsets: androidx.compose.foundation.layout.WindowInsets
    @Composable get() = androidx.compose.foundation.layout.WindowInsets.systemBarsIgnoringVisibility.only(
        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Top,
    )

/** Shape of multi-line text fields: a soft filled slab, no outline. */
val FieldShape = RoundedCornerShape(18.dp)

/** Shape of every single-line field (search, link, name): a full pill, the same everywhere. */
val PillFieldShape = RoundedCornerShape(28.dp)

/**
 * Filled field colours: a tonal slab one tier above the page, no outline and no underline (the
 * app draws no borders). Focus is told by the accent caret and label; an error by the error label
 * and the message under the field.
 */
@Composable
fun criptaFieldColors(): TextFieldColors = criptaFieldColors(LocalFieldContainer.current ?: MaterialTheme.colorScheme.surfaceContainerHigh)

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
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
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
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = if (singleLine) PillFieldShape else FieldShape, colors = criptaFieldColors(),
    )
}

/** [CriptaTextField] for a [TextFieldValue] (keeps the selection, e.g. a pre-selected name). */
@Composable
fun CriptaTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        supportingText = supportingText, isError = isError, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine,
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
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.minimumInteractiveComponentSize()
            .heightIn(min = 34.dp)
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
                Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            } else if (leadingIcon != null) {
                Row(Modifier.padding(end = 6.dp)) { leadingIcon() }
            }
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = fg)) { label() }
        }
    }
}

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
            .heightIn(min = 34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (leadingIcon != null) Row(Modifier.padding(end = 6.dp)) { leadingIcon() }
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = fg)) { label() }
        }
    }
}

/**
 * A button's label: one line, cut with "…" when the button is narrower than its words, never
 * broken mid-phrase. A label that would often be cut gets a shorter wording instead.
 */
@Composable
fun ButtonText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
               style: TextStyle = androidx.compose.material3.LocalTextStyle.current) =
    androidx.compose.material3.Text(text, modifier, color = color, maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = style)

package com.cripta.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import com.cripta.app.ui.theme.Hsv
import com.cripta.app.ui.theme.TagPalette
import com.cripta.app.ui.theme.colorHex
import com.cripta.app.ui.theme.parseColorHex
import com.cripta.app.ui.theme.readableOn

/** Spoken names of [TagPalette], same order. */
private val PALETTE_NAMES = listOf(
    R.string.colour_blue, R.string.colour_indigo, R.string.colour_purple, R.string.colour_pink,
    R.string.colour_red, R.string.colour_orange, R.string.colour_amber, R.string.colour_lime,
    R.string.colour_green, R.string.colour_teal, R.string.colour_cyan, R.string.colour_grey,
)

private val SWATCH_SIZE = 36.dp
private const val SWATCH_SLOT_DP = 56 // the 48dp touch target plus the 8dp gap

/**
 * The colour choice of tags and folders on one line: first, always in view, "Altro", a round
 * rainbow that opens [CustomColorDialog] for a colour of the user's own (once one is chosen it sits
 * inside the rainbow, ticked); beside it, scrolling sideways, the "none / automatic" choice
 * ([noneFill], with [noneLabel] drawn on it or a cross) and the shared [TagPalette]. [onPick] gets
 * null for the "none / automatic" choice. The scrolling part opens with the chosen colour in view.
 */
@Composable
fun ColorChoiceRow(
    selected: Int?,
    noneFill: Color,
    noneDescription: String,
    onPick: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
) {
    val presets = remember { TagPalette.map { it.toArgb() } }
    val presetIndex = if (selected == null) -1 else presets.indexOf(selected)
    val custom = selected != null && presetIndex < 0
    var editing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        // In the scrolling part slot 0 is "none", 1..n the presets; the chosen one stays second.
        val slot = if (selected == null) 0 else presetIndex + 1
        if (slot > 1) scroll.scrollTo(with(density) { ((slot - 1) * SWATCH_SLOT_DP).dp.roundToPx() })
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val customName = str(R.string.colour_custom)
        Swatch(null, custom, if (custom) customName + " " + colorHex(selected!!) else customName,
            own = if (custom) Color(selected!!) else null) { editing = true }
        Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(noneFill, selected == null, noneDescription, label = noneLabel, cross = noneLabel == null) { onPick(null) }
            presets.forEachIndexed { i, argb ->
                Swatch(Color(argb), selected == argb, str(PALETTE_NAMES[i])) { onPick(argb) }
            }
        }
    }
    if (editing) CustomColorDialog(
        initial = selected ?: presets.first(),
        onConfirm = { editing = false; onPick(it) },
        onDismiss = { editing = false },
    )
}

/**
 * One round choice: 48dp touch target, radio semantics with its name, a tick (never only a ring)
 * when chosen. [fill] null draws the round rainbow of "pick your own", with the colour of the
 * user's own ([own]) inside it once one is chosen.
 */
@Composable
private fun Swatch(
    fill: Color?,
    selected: Boolean,
    description: String,
    label: String? = null,
    cross: Boolean = false,
    own: Color? = null,
    onClick: () -> Unit,
) {
    val base = fill ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier.minimumInteractiveComponentSize()
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .then(if (fill == null) Modifier.background(Brush.sweepGradient(WHEEL)) else Modifier.background(base))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // White or black, whichever reads on this colour (light colours took white before).
        val ink = readableOn(Color.White, base)
        when {
            // The rainbow, as colour pickers usually draw "a colour of your own"; the chosen one inside.
            fill == null -> if (own != null) Box(
                Modifier.size(22.dp).clip(CircleShape).background(own),
                contentAlignment = Alignment.Center,
            ) { Icon(CriptaIcons.Check, null, tint = readableOn(Color.White, own), modifier = Modifier.size(14.dp)) }
            label != null && !selected -> Text(label, color = ink, style = MaterialTheme.typography.labelLarge)
            cross && !selected -> Icon(CriptaIcons.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            selected -> Icon(CriptaIcons.Check, null, tint = ink, modifier = Modifier.size(18.dp))
        }
    }
}

private val WHEEL = (0..6).map { Hsv((it * 60f) % 360f, 0.7f, 0.95f).toColor() }

/**
 * A colour of the user's own: hue, saturation and brightness sliders on gradient tracks, or the
 * code ("#RRGGBB") typed. The sample on top is drawn with the text colour the app will use on it
 * ([readableOn]), so a colour that is hard to read is seen to be handled before it is saved.
 */
@Composable
fun CustomColorDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var hsv by remember { mutableStateOf(Hsv.of(initial)) }
    var hex by remember { mutableStateOf(colorHex(initial)) }
    val parsed = parseColorHex(hex)
    val shown = hsv.toColor()
    CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.colour_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(shown),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(colorHex(shown.toArgb()), color = readableOn(Color.White, shown), style = MaterialTheme.typography.titleMedium)
                }
                TrackSlider(str(R.string.colour_hue), hsv.h, 360f, (0..6).map { Hsv(it * 60f, 1f, 1f).toColor() }) {
                    hsv = hsv.copy(h = it); hex = colorHex(hsv.toArgb())
                }
                TrackSlider(str(R.string.colour_saturation), hsv.s, 1f, listOf(Hsv(hsv.h, 0f, hsv.v).toColor(), Hsv(hsv.h, 1f, hsv.v).toColor())) {
                    hsv = hsv.copy(s = it); hex = colorHex(hsv.toArgb())
                }
                TrackSlider(str(R.string.colour_brightness), hsv.v, 1f, listOf(Color.Black, Hsv(hsv.h, hsv.s, 1f).toColor())) {
                    hsv = hsv.copy(v = it); hex = colorHex(hsv.toArgb())
                }
                CriptaTextField(
                    value = hex,
                    onValueChange = { t ->
                        hex = t.take(7)
                        parseColorHex(hex)?.let { hsv = Hsv.of(it) }
                    },
                    label = { Text(str(R.string.colour_code)) },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { onConfirm(parsed ?: hsv.toArgb()) }) {
                ButtonText(str(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { ButtonText(str(R.string.cancel)) } },
    )
}

/** A heading and a slider whose track is the gradient of the values it picks. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrackSlider(label: String, value: Float, max: Float, stops: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = 0f..max,
            modifier = Modifier.semantics { contentDescription = label },
            track = { Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Brush.horizontalGradient(stops))) },
        )
    }
}

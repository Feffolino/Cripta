package com.cripta.app.ui.components

import androidx.compose.ui.semantics.semantics
import com.cripta.app.R
import com.cripta.app.str

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A count with thousands separators in the user's locale: 1234 -> "1.234". */
fun formatCount(n: Number): String = java.text.NumberFormat.getIntegerInstance(com.cripta.app.Strings.locale).format(n)

/**
 * One counted thing as a full-width line: what it is on the left, the figure on the right
 * ("1.234 di 5.678"), optionally a rounded bar under it. Replaces the side-by-side tiles that
 * cut or wrapped 4-digit numbers: here any figure fits, and a long one wraps under the label
 * instead of being truncated.
 */
@Composable
fun CountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    of: String? = null,
    /** Said after the figure as it is (" file · 2,1 GB"), in place of " di [of]". */
    after: String? = null,
    icon: ImageVector? = null,
    progress: Float? = null,
) {
    Column(modifier.semantics(mergeDescendants = true) { }.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // DS cr-count: the label in `body`, the figure beside it; under it when both do not fit
        // (large fonts, a narrow window, a long figure). In a Row the figure was measured first and
        // could take the whole width, leaving the label none at all.
        LabelAndValue(gap = 16.dp, label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp).size(24.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }) {
            // DS cr-count__value: the figure bold in ink, " di <total>" muted.
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) { append(value) }
                    if (of != null) { append(str(R.string.count_of_sep)); append(of) }
                    if (after != null) append(after)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

/**
 * [label] and [value] on one line, the value at the end, when the value fits beside a fair share of
 * the label (its whole text, or two fifths of the width); else the value on a line of its own under
 * the label, at the end. Neither is ever squeezed to nothing.
 */
@Composable
private fun LabelAndValue(gap: androidx.compose.ui.unit.Dp, label: @Composable () -> Unit, value: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = { label(); value() }, modifier = Modifier.fillMaxWidth()) { measurables, c ->
        val l = measurables[0]
        val v = measurables[1]
        val inf = androidx.compose.ui.unit.Constraints.Infinity
        val space = gap.roundToPx()
        val valueWide = v.maxIntrinsicWidth(inf)
        val labelWide = l.maxIntrinsicWidth(inf)
        val width = if (c.hasBoundedWidth) c.maxWidth else labelWide + space + valueWide
        val labelNeed = minOf(labelWide, width * 2 / 5)
        if (valueWide + space + labelNeed <= width) {
            val vp = v.measure(androidx.compose.ui.unit.Constraints(maxWidth = valueWide))
            val lp = l.measure(androidx.compose.ui.unit.Constraints(maxWidth = (width - vp.width - space).coerceAtLeast(0)))
            val h = maxOf(lp.height, vp.height)
            layout(width, h) {
                lp.placeRelative(0, (h - lp.height) / 2)
                vp.placeRelative(width - vp.width, (h - vp.height) / 2)
            }
        } else {
            val lp = l.measure(androidx.compose.ui.unit.Constraints(maxWidth = width))
            val vp = v.measure(androidx.compose.ui.unit.Constraints(maxWidth = width))
            val between = 4.dp.roundToPx()
            layout(width, lp.height + between + vp.height) {
                lp.placeRelative(0, 0)
                vp.placeRelative(width - vp.width, lp.height + between)
            }
        }
    }
}

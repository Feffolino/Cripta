package com.cripta.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

/**
 * Wrapping row of chips (filters, tags, choices). A 32dp chip normally sits in an invisible 48dp
 * touch box, so wrapped rows looked twice as far apart as the chips beside each other. Here the
 * touch box is 40dp (still well above the 24dp WCAG 2.2 minimum) and rows get no extra spacing:
 * the visible gap between rows equals the gap between chips on a row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlowRow(
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy((gap - 8.dp).coerceAtLeast(0.dp)),
            content = content,
        )
    }
}

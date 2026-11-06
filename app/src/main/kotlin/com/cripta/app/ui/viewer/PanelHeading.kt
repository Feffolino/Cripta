package com.cripta.app.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The head of a section of the details panel: the heading in the accent, an optional action at its
 * end (a 48dp row then) and an optional line of help under it. Sections are told apart by headings
 * and space, never by frames or lines.
 */
@Composable
internal fun PanelHeading(title: String, description: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().then(if (action != null) Modifier.heightIn(min = 48.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).semantics { heading() })
            action?.invoke()
        }
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (action != null) 0.dp else 4.dp))
        }
    }
}

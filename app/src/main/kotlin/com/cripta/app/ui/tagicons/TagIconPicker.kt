package com.cripta.app.ui.tagicons

import com.cripta.app.R
import com.cripta.app.str

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One icon in a picker: a round cell, ringed in the accent when chosen. */
@Composable
private fun IconCell(name: String?, selected: Boolean, size: Dp = 48.dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = rememberTagIcon(name)
    val title by produceState(name?.replace('-', ' '), name, context) {
        value = name?.let { TagIcons.title(context, it) }
    }
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = title ?: str(R.string.icon_none) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            name == null -> Icon(CriptaIcons.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.45f))
            icon != null -> Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.58f))
        }
    }
}

/**
 * The editor's "Icona" row: the icon chosen, a few fitting the tag's name (localized or English) or
 * common ones, and "Tutte le icone" for the full searchable list.
 */
@Composable
fun TagIconField(
    name: String, icon: String?, onPick: (String?) -> Unit, onBrowse: () -> Unit,
    /** False where the caller already puts a heading over it (the tag editor). */
    showLabel: Boolean = true,
) {
    val context = LocalContext.current
    val resolved = rememberResolvedTagIcon(icon)
    val suggested by produceState(emptyList<String>(), name) {
        value = TagIcons.suggest(context, name, 10).ifEmpty { TagIcons.search(context, "", 10) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabel) Text(str(R.string.icon), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconCell(null, resolved == null) { onPick(null) }
            // The chosen one stays first even when the name suggests others.
            (listOfNotNull(resolved) + suggested.filter { it != resolved }).forEach { n -> IconCell(n, n == resolved) { onPick(n) } }
        }
        TextButton(onClick = onBrowse, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            com.cripta.app.ui.components.ButtonText(str(R.string.icon_all))
        }
    }
}

/** The label editor's standalone icon picker. */
@Composable
fun TagIconPicker(tagName: String, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.icon)) },
        text = { TagIconChoices(tagName, selected, 380.dp, onPick) },
        confirmButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.close)) } },
    )
}

/** Search the shared catalog, or browse suggestions for the label or folder name. */
@Composable
internal fun TagIconChoices(name: String, selected: String?, gridHeight: Dp, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val resolved = rememberResolvedTagIcon(selected)
    var query by remember { mutableStateOf("") }
    val all by produceState(emptyList<String>()) { value = TagIcons.names(context) }
    val common = remember(all) { TagIcons.DEFAULTS.filter { it in all } }
    val forName by produceState(emptyList<String>(), name) { value = TagIcons.suggest(context, name, 18) }
    val found by key(query) {
        produceState<List<String>?>(null) {
            if (query.isNotBlank()) value = TagIcons.search(context, query)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.cripta.app.ui.components.CriptaTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(CriptaIcons.Search, null) },
            placeholder = { Text(str(R.string.icon_search_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
        key(query) {
            LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.fillMaxWidth().height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                fun header(text: String) = item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() })
                }
                if (query.isNotBlank()) {
                    val results = found
                    if (results != null) {
                        if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(str(R.string.icon_none_for, query.trim()),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(results, key = { "s-$it" }) { n -> IconCell(n, n == resolved) { onPick(n) } }
                    }
                } else {
                    item(key = "none") { IconCell(null, resolved == null) { onPick(null) } }
                    if (forName.isNotEmpty()) {
                        header(str(R.string.icon_for, name.trim()))
                        items(forName, key = { "n-$it" }) { n -> IconCell(n, n == resolved) { onPick(n) } }
                    }
                    header(str(R.string.icon_most_used))
                    items(common, key = { "d-$it" }) { n -> IconCell(n, n == resolved) { onPick(n) } }
                    header(str(R.string.icon_all_short))
                    items(all, key = { "a-$it" }) { n -> IconCell(n, n == resolved) { onPick(n) } }
                }
            }
        }
        Text(str(R.string.icon_credit), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

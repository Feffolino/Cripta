package com.cripta.app.ui.tagicons

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** An icon's name for TalkBack: Italian ("cuore rosso") once the names are loaded. */
private fun spoken(name: String) = TagIcons.title(name)

/** One icon in a picker: a round cell, ringed in the accent when chosen. */
@Composable
private fun IconCell(name: String?, selected: Boolean, size: Dp = 48.dp, onClick: () -> Unit) {
    val icon = rememberTagIcon(name)
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = name?.let { spoken(it) } ?: "Nessuna icona" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            name == null -> Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.45f))
            icon != null -> Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.58f))
        }
    }
}

/**
 * The editor's "Icona" row: the icon chosen, a few fitting the tag's name (Italian or English) or
 * common ones, and "Tutte le icone" for the full searchable list.
 */
@Composable
fun TagIconField(name: String, icon: String?, onPick: (String?) -> Unit, onBrowse: () -> Unit) {
    val context = LocalContext.current
    val suggested by produceState(emptyList<String>(), name) {
        value = TagIcons.suggest(context, name, 10).ifEmpty { TagIcons.DEFAULTS.take(10) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Icona", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconCell(null, icon == null) { onPick(null) }
            // The chosen one stays first even when the name suggests others.
            (listOfNotNull(icon) + suggested.filter { it != icon }).forEach { n -> IconCell(n, n == icon) { onPick(n) } }
        }
        TextButton(onClick = onBrowse, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            com.cripta.app.ui.components.ButtonText("Tutte le icone")
        }
    }
}

/**
 * All the icons: searchable (Italian or English words), with those fitting [tagName] first and the
 * common ones next, then every icon to browse.
 */
@Composable
fun TagIconPicker(tagName: String, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val all by produceState(emptyList<String>()) { value = TagIcons.names(context) }
    val forName by produceState(emptyList<String>(), tagName) { value = TagIcons.suggest(context, tagName, 18) }
    val found by produceState<List<String>?>(null, query) {
        value = if (query.isBlank()) null else TagIcons.search(context, query)
    }
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.cripta.app.ui.components.CriptaTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Cerca: cuore, mare, gatto, pizza…") }, modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.fillMaxWidth().height(380.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    fun header(text: String) = item(span = { GridItemSpan(maxLineSpan) }, contentType = "h") {
                        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() })
                    }
                    val f = found
                    if (f != null) {
                        if (f.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Nessuna icona per «${query.trim()}». Prova un'altra parola.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(f, key = { "s-$it" }) { n -> IconCell(n, n == selected) { onPick(n) } }
                    } else {
                        item(key = "none") { IconCell(null, selected == null) { onPick(null) } }
                        if (forName.isNotEmpty()) {
                            header("Per «${tagName.trim()}»")
                            items(forName, key = { "n-$it" }) { n -> IconCell(n, n == selected) { onPick(n) } }
                        }
                        header("Le più usate")
                        items(TagIcons.DEFAULTS, key = { "d-$it" }) { n -> IconCell(n, n == selected) { onPick(n) } }
                        header("Tutte")
                        items(all, key = { "a-$it" }) { n -> IconCell(n, n == selected) { onPick(n) } }
                    }
                }
                Text("Fluent Emoji di Microsoft, licenza MIT.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText("Chiudi") } },
    )
}

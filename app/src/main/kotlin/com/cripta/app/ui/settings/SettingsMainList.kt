package com.cripta.app.ui.settings

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** First level: search + preference pages + tools, each with a live summary. */
@Composable
internal fun MainSettingsList(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier,
    columns: Int,
    query: String,
    onQuery: (String) -> Unit,
    summaries: Map<SettingsPage, String>,
    /** Page, plus the section to land on when coming from a search result. */
    onOpen: (SettingsPage, String?) -> Unit,
    onLock: () -> Unit,
    space: com.cripta.app.data.SpaceReport?,
    onOpenSpace: () -> Unit,
) {
    // Two columns of page rows in landscape; search, group labels and the lock button span the width.
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan =
        { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = full) {
            // The app's single-line field: the same filled pill as every other search.
            com.cripta.app.ui.components.CriptaTextField(
                value = query, onValueChange = onQuery,
                placeholder = { Text(str(R.string.search_setting)) },
                leadingIcon = { Icon(CriptaIcons.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(CriptaIcons.Close, str(R.string.clear_short)) } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            val hits = SEARCH_INDEX.filter { e -> e.label.lowercase().contains(q) || e.keywords.contains(q) }
            if (hits.isEmpty()) {
                item(span = full) { Text(str(R.string.no_setting_found), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)) }
            }
            items(hits.size) { i ->
                val e = hits[i]
                PageRow(e.page, e.label, "${e.page.label} › ${e.section}") { onOpen(e.page, e.section) }
            }
        } else {
            SettingsGroup.entries.forEach { g ->
                item(span = full) { GroupLabel(g.label) }
                val pages = SettingsPage.entries.filter { it.group == g }
                items(pages.size) { i ->
                    val p = pages[i]
                    PageRow(p, p.label, summaries[p].orEmpty()) { onOpen(p, null) }
                }
                // DS Impostazioni: the space used, as a counted row, closes "I tuoi dati".
                if (g == SettingsGroup.DATA) item(span = full) {
                    // The whole phone storage: Cripta, the rest, free. Shown from the last reading
                    // (no jump on every visit); before the very first one, nothing.
                    space?.let { DeviceSpaceBar(it, Modifier.padding(top = 8.dp), onClick = onOpenSpace) }
                }
            }
        }
    }
}

package com.cripta.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.DeleteOriginalPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Blocco automatico") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 5, 15).forEach { m ->
                        FilterChip(
                            selected = s.autoLockMinutes == m,
                            onClick = { vm.setAutoLock(m) },
                            label = { Text(if (m == 0) "Subito" else "$m min") },
                        )
                    }
                }
            }

            Section("Originale dopo import") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeleteOriginalPolicy.entries.forEach { p ->
                        FilterChip(
                            selected = s.deleteOriginalPolicy == p,
                            onClick = { vm.setDeletePolicy(p) },
                            label = {
                                Text(when (p) {
                                    DeleteOriginalPolicy.ASK -> "Chiedi"
                                    DeleteOriginalPolicy.ALWAYS -> "Elimina"
                                    DeleteOriginalPolicy.NEVER -> "Mantieni"
                                })
                            },
                        )
                    }
                }
            }

            Button(onClick = { vm.lockNow(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Blocca ora")
            }

            Section("Sicurezza — limiti") {
                Text(
                    "Cripta protegge da curiosi occasionali. Non è pensato contro analisi forense o " +
                        "dispositivi con root. L'eliminazione usa crypto-shredding (distrugge la chiave del file); " +
                        "la cancellazione fisica su memoria flash non è garantita dal sistema. Nessun permesso di rete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

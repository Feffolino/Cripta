package com.cripta.app.ui.components

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cripta.app.data.ConvertAfter

/**
 * Asked at the first MP4 conversion (instead of hiding the choice in Settings): what to do with
 * the original once the verified MP4 is ready. "Ricorda la scelta" saves it; it can be changed
 * later in Impostazioni › Video.
 */
@Composable
fun ConvertChoiceDialog(
    count: Int,
    trashDays: Int,
    initial: ConvertAfter = ConvertAfter.REPLACE,
    onConfirm: (choice: ConvertAfter, remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(initial) }
    var rememberIt by remember { mutableStateOf(true) }
    val options = listOf(
        ConvertAfter.REPLACE to ("Sostituisci l'originale" to
            "L'MP4 prende il suo posto (cartella, etichette, copertina); l'originale va nel cestino per $trashDays giorni."),
        ConvertAfter.KEEP_BOTH to ("Tieni entrambi" to "Restano l'originale e la copia MP4."),
        ConvertAfter.ASK to ("Chiedimi alla fine" to "A conversione finita scegli se eliminare l'originale."),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Convertire in MP4?" else "Convertire $count video in MP4?") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Crea una copia MP4 scorribile, verificata prima di essere salvata. Prosegue in background. Dopo la conversione:",
                    style = MaterialTheme.typography.bodyMedium)
                options.forEach { (v, t) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = choice == v, role = Role.RadioButton, onClick = { choice = v })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = choice == v, onClick = null, modifier = Modifier.padding(12.dp))
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(t.first, style = MaterialTheme.typography.bodyLarge)
                            Text(t.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(value = rememberIt, role = Role.Checkbox, onValueChange = { rememberIt = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberIt, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text("Ricorda la scelta (modificabile in Impostazioni › Video)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice, rememberIt) }) { Text("Converti") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

package com.cripta.app.ui.components

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

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
import com.cripta.app.ui.components.TextButton
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
 * later in Impostazioni › Import e conversione.
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
        ConvertAfter.REPLACE to (str(R.string.convert_replace) to
            str(R.string.convert_replace_desc, trashDays)),
        ConvertAfter.KEEP_BOTH to (str(R.string.convert_keep_both) to str(R.string.convert_keep_both_desc)),
        ConvertAfter.ASK to (str(R.string.convert_ask_at_end) to str(R.string.convert_ask_at_end_desc)),
    )
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plural(R.plurals.convert_q, count)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(str(R.string.convert_intro),
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
                    Text(str(R.string.convert_remember), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice, rememberIt) }) { com.cripta.app.ui.components.ButtonText(str(R.string.convert)) } },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

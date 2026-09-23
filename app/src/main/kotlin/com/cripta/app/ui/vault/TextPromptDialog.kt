package com.cripta.app.ui.vault

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Single-line text prompt (new folder, rename, save filter). The field is focused on open; an
 * initial value is pre-selected so typing replaces it. With [selectBaseName] (file rename) only the
 * name before the extension is selected, so "foto.jpg" keeps its ".jpg" unless the user edits it.
 * OK is disabled while the text is blank; the keyboard's Done key confirms.
 */
@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    selectBaseName: Boolean = false,
    confirmLabel: String = "OK",
) {
    var value by remember {
        val dot = initial.lastIndexOf('.')
        val end = if (selectBaseName && dot > 0) dot else initial.length
        mutableStateOf(TextFieldValue(initial, TextRange(0, end)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The dialog window must be attached before focus (and the keyboard) can be requested.
        kotlinx.coroutines.delay(80)
        runCatching { focus.requestFocus() }
    }
    val canConfirm = value.text.isNotBlank()
    val confirm: () -> Unit = { if (canConfirm) onConfirm(value.text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

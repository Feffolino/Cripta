package com.cripta.app.ui.vault

import com.cripta.app.R
import com.cripta.app.str

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
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

/** Longest name typed for a folder, a file, a saved filter or a tag (a pasted page is cut there). */
const val MAX_NAME_LENGTH = 255

/** [s] cut to [MAX_NAME_LENGTH], never through the middle of a character made of two chars (emoji). */
fun limitName(s: String): String {
    if (s.length <= MAX_NAME_LENGTH) return s
    val end = if (Character.isHighSurrogate(s[MAX_NAME_LENGTH - 1])) MAX_NAME_LENGTH - 1 else MAX_NAME_LENGTH
    return s.substring(0, end)
}

/**
 * Single-line text prompt (new folder, rename, save filter). The field is focused on open; an
 * initial value is pre-selected so typing replaces it. With [selectBaseName] (file rename) only the
 * name before the extension is selected, so "foto.jpg" keeps its ".jpg" unless the user edits it.
 * OK is disabled while the text is blank; the keyboard's Done key confirms. At most
 * [MAX_NAME_LENGTH] characters.
 */
@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    selectBaseName: Boolean = false,
    confirmLabel: String = str(R.string.done),
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
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            com.cripta.app.ui.components.CriptaTextField(
                value = value,
                onValueChange = { v ->
                    val text = limitName(v.text)
                    value = if (text == v.text) v else TextFieldValue(text, TextRange(minOf(v.selection.start, text.length), minOf(v.selection.end, text.length)))
                },
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
            TextButton(onClick = { confirm() }, enabled = canConfirm) { com.cripta.app.ui.components.ButtonText(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

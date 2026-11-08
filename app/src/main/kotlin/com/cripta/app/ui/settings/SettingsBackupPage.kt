package com.cripta.app.ui.settings

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Restoring: what older versions accepted. */
private const val MIN_PASSPHRASE = 6

/**
 * Creating: a backup copied off the phone can be attacked offline with no limit on attempts,
 * where only the passphrase's length stands in the way (6 characters fell quickly).
 */
private const val MIN_NEW_PASSPHRASE = 10

/**
 * Passphrase entry. Creating a backup asks for it twice (a typo would make the archive
 * unrecoverable); restoring asks once and explains that files are added, not replaced.
 */
@Composable
internal fun PassphraseDialog(title: String, creating: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val minLen = if (creating) MIN_NEW_PASSPHRASE else MIN_PASSPHRASE
    val tooShort = pass.length < minLen
    val mismatch = creating && confirm.isNotEmpty() && confirm != pass
    val valid = !tooShort && (!creating || confirm == pass)
    val transformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(
                if (visible) CriptaIcons.VisibilityOff else CriptaIcons.Visibility,
                if (visible) str(R.string.hide_passphrase) else str(R.string.show_passphrase),
            )
        }
    }
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (creating) str(R.string.passphrase_create_hint)
                    else str(R.string.passphrase_restore_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.cripta.app.ui.components.CriptaTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text(str(R.string.passphrase)) },
                    singleLine = true,
                    visualTransformation = transformation,
                    trailingIcon = toggle,
                    supportingText = {
                        Text(if (tooShort) str(R.string.passphrase_too_short, minLen, pass.length, minLen) else str(R.string.length_ok))
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = if (creating) androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (valid) onConfirm(pass) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (creating) {
                    com.cripta.app.ui.components.CriptaTextField(
                        value = confirm, onValueChange = { confirm = it },
                        label = { Text(str(R.string.repeat_passphrase)) },
                        singleLine = true,
                        visualTransformation = transformation,
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text(str(R.string.passphrase_mismatch)) }) else null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (valid) onConfirm(pass) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = valid) { com.cripta.app.ui.components.ButtonText(if (creating) str(R.string.create_backup) else str(R.string.restore)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

/** Date/time stamp for the backup file name, so successive backups don't overwrite each other. */
internal fun backupStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.ROOT).format(java.util.Date())

/**
 * What the next backup carries: one switch per group, and for the files the top-level folders
 * (each with everything under it) and the files in no folder, folded until asked for.
 */
@Composable
internal fun BackupScopeChoice(
    scope: com.cripta.app.data.BackupScope,
    folders: List<com.cripta.app.data.db.FolderEntity>,
    onChange: (com.cripta.app.data.BackupScope) -> Unit,
) {
    var showFolders by remember { mutableStateOf(false) }
    ToggleRow(str(R.string.files_cap), scope.files, desc = str(R.string.backup_files_desc)) {
        onChange(scope.copy(files = it))
    }
    if (scope.files && folders.isNotEmpty()) {
        val ids = folders.map { it.id }.toSet()
        val chosen = ids.count { it !in scope.excludedFolders }
        val all = chosen == ids.size && scope.rootFiles
        TextButton(onClick = { showFolders = !showFolders }) {
            com.cripta.app.ui.components.ButtonText(
                when {
                    showFolders -> str(R.string.hide_folders)
                    all -> str(R.string.choose_folders_all)
                    else -> str(R.string.choose_folders_n, chosen, ids.size)
                }
            )
        }
        if (showFolders) {
            Column(Modifier.padding(start = 16.dp)) {
                folders.forEach { f ->
                    ToggleRow(f.name, f.id !in scope.excludedFolders) { on ->
                        // Ids of folders gone since are dropped on the way.
                        val ex = scope.excludedFolders.filter { it in ids }.toSet()
                        onChange(scope.copy(excludedFolders = if (on) ex - f.id else ex + f.id))
                    }
                }
                ToggleRow(str(R.string.files_outside_folders), scope.rootFiles) { onChange(scope.copy(rootFiles = it)) }
                Text(str(R.string.folder_with_subfolders),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (scope.files) {
        ToggleRow(str(R.string.covers_by_hand), scope.covers, desc = str(R.string.covers_by_hand_desc)) {
            onChange(scope.copy(covers = it))
        }
    }
    ToggleRow(str(R.string.tags_saved_filters), scope.tags, desc = str(R.string.tags_saved_filters_desc)) {
        onChange(scope.copy(tags = it))
    }
    ToggleRow(str(R.string.people_and_faces), scope.people, desc = str(R.string.people_and_faces_desc)) {
        onChange(scope.copy(people = it))
    }
    ToggleRow(str(R.string.analysis_backup), scope.analysis, desc = str(R.string.analysis_backup_desc)) {
        onChange(scope.copy(analysis = it))
    }
    ToggleRow(str(R.string.tab_settings), scope.settings,
        desc = str(R.string.settings_backup_desc)) {
        onChange(scope.copy(settings = it))
    }
    Text(str(R.string.trash_never_backup),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (scope.isEmpty) {
        Text(str(R.string.choose_one_thing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

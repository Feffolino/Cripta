package com.cripta.app.ui.settings

import com.cripta.app.R
import com.cripta.app.plural
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
                // Fills up to the minimum length: how far the passphrase is from being long enough.
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (pass.length.toFloat() / minLen).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

/** "N file · 1,2 GB": a folder's (or the loose files') weight in the backup. */
private fun statLine(s: com.cripta.app.data.FolderStat?): String {
    val n = s?.count ?: 0
    return plural(R.plurals.files_n, n, com.cripta.app.ui.components.formatCount(n)) + " · " +
        com.cripta.app.ui.components.formatBytes(s?.bytes ?: 0L)
}

/** The page's state: when the last backup on this device was made and what it held, or none yet. */
@Composable
internal fun LastBackupRow(last: com.cripta.app.data.LastBackup?) {
    val title: String
    val caption: String
    if (last == null) {
        title = str(R.string.last_backup_none)
        caption = str(R.string.last_backup_none_desc)
    } else {
        val ctx = com.cripta.app.Strings.context
        val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
            last.at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
        ).toString().replaceFirstChar { it.lowercase() }
        title = str(R.string.last_backup_ago, ago)
        val f = android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME or
            android.text.format.DateUtils.FORMAT_ABBREV_MONTH
        caption = listOfNotNull(
            android.text.format.DateUtils.formatDateTime(ctx, last.at, f),
            plural(R.plurals.files_n, last.files, com.cripta.app.ui.components.formatCount(last.files)),
            last.bytes?.let { com.cripta.app.ui.components.formatBytes(it) },
        ).joinToString(" · ")
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        Icon(CriptaIcons.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * What the next backup carries: one switch per group, each with its icon and a one-line caption;
 * the folders are a row that opens [BackupFoldersSheet] with the weight of each.
 */
@Composable
internal fun BackupScopeChoice(
    scope: com.cripta.app.data.BackupScope,
    folders: List<com.cripta.app.data.db.FolderEntity>,
    stats: Map<Long, com.cripta.app.data.FolderStat>,
    root: com.cripta.app.data.FolderStat,
    onChange: (com.cripta.app.data.BackupScope) -> Unit,
) {
    var showFolders by remember { mutableStateOf(false) }
    ToggleRow(str(R.string.files_cap), scope.files, desc = str(R.string.backup_files_desc), icon = CriptaIcons.Photo) {
        onChange(scope.copy(files = it))
    }
    if (scope.files && folders.isNotEmpty()) {
        val ids = folders.map { it.id }.toSet()
        val chosen = ids.count { it !in scope.excludedFolders }
        val summary = (if (chosen == ids.size) str(R.string.backup_folders_all)
            else plural(R.plurals.backup_folders_n, ids.size, com.cripta.app.ui.components.formatCount(chosen), com.cripta.app.ui.components.formatCount(ids.size))) +
            ", " + (if (scope.rootFiles) str(R.string.backup_root_in) else str(R.string.backup_root_out))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
                .clickable { showFolders = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CriptaIcons.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(str(R.string.backup_folders), style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Icon(CriptaIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        if (showFolders) {
            BackupFoldersSheet(scope, folders, stats, root, onChange) { showFolders = false }
        }
    }
    if (scope.files) {
        ToggleRow(str(R.string.covers_by_hand), scope.covers, desc = str(R.string.covers_by_hand_desc), icon = CriptaIcons.Image) {
            onChange(scope.copy(covers = it))
        }
    }
    ToggleRow(str(R.string.tags_saved_filters), scope.tags, desc = str(R.string.tags_saved_filters_desc), icon = CriptaIcons.Label) {
        onChange(scope.copy(tags = it))
    }
    ToggleRow(str(R.string.people_and_faces), scope.people, desc = str(R.string.people_and_faces_desc), icon = CriptaIcons.Face) {
        onChange(scope.copy(people = it))
    }
    ToggleRow(str(R.string.analysis_backup), scope.analysis, desc = str(R.string.analysis_backup_desc), icon = CriptaIcons.GraphicEq) {
        onChange(scope.copy(analysis = it))
    }
    ToggleRow(str(R.string.tab_settings), scope.settings, desc = str(R.string.settings_backup_desc), icon = CriptaIcons.Settings) {
        onChange(scope.copy(settings = it))
    }
    if (scope.isEmpty) {
        Text(str(R.string.choose_one_thing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * The top-level folders (each with everything under it) and the loose files, each with its weight:
 * a tap puts it in or out (accent name and a tick when in), "Tutte" / "Nessuna" do all at once.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BackupFoldersSheet(
    scope: com.cripta.app.data.BackupScope,
    folders: List<com.cripta.app.data.db.FolderEntity>,
    stats: Map<Long, com.cripta.app.data.FolderStat>,
    root: com.cripta.app.data.FolderStat,
    onChange: (com.cripta.app.data.BackupScope) -> Unit,
    onDismiss: () -> Unit,
) {
    val ids = folders.map { it.id }.toSet()
    // Ids of folders gone since are dropped on the way.
    val ex = scope.excludedFolders.filter { it in ids }.toSet()
    val allIn = ex.isEmpty() && scope.rootFiles
    val noneIn = ex.size == ids.size && !scope.rootFiles
    val chosen = (ids.size - ex.size) + (if (scope.rootFiles) 1 else 0)
    val bytes = folders.filter { it.id !in ex }.sumOf { stats[it.id]?.bytes ?: 0L } + (if (scope.rootFiles) root.bytes else 0L)
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.backup_folders_sheet), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.done)) }
            }
            Text(str(R.string.folder_with_subfolders), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            com.cripta.app.ui.components.ChipFlowRow(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                com.cripta.app.ui.components.ChoiceChip(selected = allIn,
                    onClick = { onChange(scope.copy(excludedFolders = emptySet(), rootFiles = true)) },
                    label = { com.cripta.app.ui.components.OneLineText(str(R.string.backup_choose_all)) })
                com.cripta.app.ui.components.ChoiceChip(selected = noneIn,
                    onClick = { onChange(scope.copy(excludedFolders = ids, rootFiles = false)) },
                    label = { com.cripta.app.ui.components.OneLineText(str(R.string.backup_choose_none)) })
            }
            folders.forEach { f ->
                PickRow(CriptaIcons.Folder, f.name, statLine(stats[f.id]), f.id !in ex) { on ->
                    onChange(scope.copy(excludedFolders = if (on) ex - f.id else ex + f.id))
                }
            }
            PickRow(CriptaIcons.InsertDriveFile, str(R.string.files_outside_folders), statLine(root), scope.rootFiles) {
                onChange(scope.copy(rootFiles = it))
            }
            Text(
                str(R.string.backup_folders_total, com.cripta.app.ui.components.formatCount(chosen),
                    com.cripta.app.ui.components.formatCount(ids.size + 1), com.cripta.app.ui.components.formatBytes(bytes)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One choice of [BackupFoldersSheet]: chosen reads as the accent name in bold and a tick, never colour alone. */
@Composable
private fun PickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    caption: String,
    chosen: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .toggleable(value = chosen, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (chosen) FontWeight.Bold else null)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (chosen) Icon(CriptaIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

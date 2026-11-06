package com.cripta.app.ui.note

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.viewer.DelayedSpinner

/**
 * Note editor. Leaving it (system Back or the arrow) saves the draft when it changed, so a Back
 * press never discards typed text. [folderId] is where a NEW note is created (null = the "folder"
 * route argument, else the vault root). [onDeleted] runs after the note is deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    fileId: String?,
    onBack: () -> Unit,
    folderId: Long? = null,
    onDeleted: () -> Unit = onBack,
    vm: NoteEditorViewModel = hiltViewModel(),
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(fileId) { vm.load(fileId) }
    LaunchedEffect(vm.error) {
        vm.error?.let { snackbar.showSnackbar(it); vm.error = null }
    }

    // Save on the way out; an unchanged draft (or an empty new note) just closes.
    val leave: () -> Unit = {
        when {
            vm.saving -> Unit
            // A note that failed to load is read-only: leaving never saves over the original.
            !vm.loaded || vm.loadFailed || !vm.dirty -> onBack()
            fileId == null && vm.name.isBlank() && vm.text.isBlank() -> onBack()
            else -> vm.save(fileId, folderId) { onBack() }
        }
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
                // DS Nota: no title in the bar (the note's own title leads the page); the rarer
                // actions in the menu, "Fatto" as the floating button.
                title = {},
                navigationIcon = { IconButton(onClick = leave) { Icon(CriptaIcons.ArrowBack, str(R.string.note_save_close)) } },
                actions = {
                    if (fileId != null) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = vm.loaded && !vm.saving) {
                                Icon(CriptaIcons.MoreVert, str(R.string.more_actions))
                            }
                            androidx.compose.material3.DropdownMenu(expanded = menu && com.cripta.app.ui.components.vaultUnlocked(), onDismissRequest = { menu = false }) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(str(R.string.note_delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(CriptaIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // DS cr-fab "Fatto": saves and closes. While saving, the tick turns into progress.
            if (vm.loaded && !vm.loadFailed) androidx.compose.material3.FloatingActionButton(
                onClick = { if (!vm.saving) vm.save(fileId, folderId) { onBack() } },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                // Above the keyboard: the padding goes OUTSIDE the 60dp (inside it, it squeezed the
                // icon and left the button under the keyboard while typing). The Scaffold already
                // keeps the FAB above the navigation bar, so only the keyboard's extra height counts.
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                    .size(60.dp),
            ) {
                if (vm.saving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(CriptaIcons.Check, str(R.string.done), Modifier.size(28.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
    ) { pad ->
        AnimatedContent(
            targetState = vm.loaded,
            transitionSpec = { fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit(Motion.MEDIUM)) },
            modifier = Modifier.fillMaxSize().padding(pad),
            label = "noteLoaded",
        ) { ready ->
            if (!ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedSpinner() }
            } else {
                // imePadding: the window is edge-to-edge, so without it the keyboard (tall in landscape)
                // would cover the text being typed. consumeWindowInsets first, so the navigation-bar
                // inset already applied by the Scaffold isn't added a second time above the keyboard.
                // Borderless fields: the page is the note, no boxes around it. In a short window (landscape
                // with the keyboard up) the title folds away while the content is being typed, so the
                // text and its cursor keep the room left between the app bar and the keyboard.
                BoxWithConstraints(Modifier.fillMaxSize().consumeWindowInsets(pad).imePadding()) {
                    val compact = maxHeight < 240.dp
                    var contentFocused by remember { mutableStateOf(false) }
                    val plain = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    )
                    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                        if (!(compact && contentFocused)) {
                            TextField(
                                value = vm.name, onValueChange = { vm.name = it },
                                placeholder = { Text(str(R.string.note_title)) }, singleLine = true,
                                // DS Nota: the title in headline, the word count under it.
                                textStyle = MaterialTheme.typography.headlineSmall,
                                colors = plain,
                                // Not editable when the note couldn't be read (nothing typed could be saved).
                                enabled = !vm.loadFailed,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = str(R.string.note_title) },
                            )
                            val words = vm.text.split(Regex("\\s+")).count { it.isNotBlank() }
                            Text(plural(R.plurals.note_words, words, com.cripta.app.ui.components.formatCount(words)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp))
                        }
                        // The text with its selection, for the formatting bar; vm.text follows it.
                        var body by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(vm.text)) }
                        LaunchedEffect(vm.text) { if (vm.text != body.text) body = androidx.compose.ui.text.input.TextFieldValue(vm.text) }
                        fun edit(v: androidx.compose.ui.text.input.TextFieldValue) { body = v; vm.text = v.text }
                        // Markdown styled as it is typed: headings larger, bold, lists, the markers dimmed.
                        val palette = NoteMarkdown.Palette(
                            muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            accent = MaterialTheme.colorScheme.primary,
                            codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        val styled = remember(palette) {
                            androidx.compose.ui.text.input.VisualTransformation { t ->
                                androidx.compose.ui.text.input.TransformedText(NoteMarkdown.render(t.text, palette, keepMarkers = true),
                                    androidx.compose.ui.text.input.OffsetMapping.Identity)
                            }
                        }
                        TextField(
                            value = body,
                            // Enter on a list item starts the next one (or ends an empty list).
                            onValueChange = { edit(NoteMarkdown.continueList(body, it)) },
                            placeholder = { Text(str(R.string.note_placeholder)) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                            visualTransformation = styled,
                            colors = plain,
                            enabled = !vm.loadFailed,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .onFocusChanged { contentFocused = it.isFocused }
                                .semantics { contentDescription = str(R.string.note_content) },
                        )
                        // The formatting bar, above the keyboard; the right end left to "Fatto".
                        if (!vm.loadFailed) Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 84.dp, bottom = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            @Composable
                            fun tool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, act: () -> Unit) =
                                IconButton(onClick = act) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            tool(CriptaIcons.FormatHeading, str(R.string.note_title)) { edit(NoteMarkdown.linePrefix(body, null)) }
                            tool(CriptaIcons.FormatBold, str(R.string.fmt_bold)) { edit(NoteMarkdown.wrap(body, "**")) }
                            tool(CriptaIcons.FormatItalic, str(R.string.fmt_italic)) { edit(NoteMarkdown.wrap(body, "*")) }
                            tool(CriptaIcons.FormatChecklist, str(R.string.fmt_todo)) { edit(NoteMarkdown.linePrefix(body, "- [ ] ")) }
                            tool(CriptaIcons.ViewList, str(R.string.fmt_bullets)) { edit(NoteMarkdown.linePrefix(body, "- ")) }
                            tool(CriptaIcons.FormatNumbered, str(R.string.fmt_numbered)) { edit(NoteMarkdown.linePrefix(body, "1. ")) }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && fileId != null) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.note_delete_q)) },
            text = { Text(str(R.string.note_delete_text, vm.name.ifBlank { str(R.string.default_note_name) })) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(fileId) { onDeleted() } }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
}

package com.cripta.app.ui.auth

import com.cripta.app.ui.theme.shakeOn
import com.cripta.app.R
import com.cripta.app.str

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.AlertDialog
import com.cripta.app.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.animationsEnabled
import kotlinx.coroutines.launch

/**
 * What the lock screen has to say beyond "tap to unlock".
 *
 * @param firstRun no vault yet: explain what creating one means before the system prompt.
 * @param error last authentication problem, in Italian (null = nothing to show). Cancelling the
 *   prompt is not an error and never shows up here.
 * @param noDeviceCredential the device has no screen lock / biometrics, so the vault can't be
 *   protected: explain and offer a shortcut to the security settings.
 * @param pin where the app PIN comes in (see [PinStep]).
 * @param pinBusy a PIN is being checked (the key derivation takes a moment).
 */
data class AuthUiState(
    val firstRun: Boolean = false,
    val error: String? = null,
    val noDeviceCredential: Boolean = false,
    val pin: PinStep = PinStep.NONE,
    val pinBusy: Boolean = false,
    val secretKind: com.cripta.app.security.SecretKind = com.cripta.app.security.SecretKind.PIN,
)

/** The app PIN on the lock screen. */
enum class PinStep {
    /** No app PIN: the system prompt only. */
    NONE,
    /** The system prompt, or the app PIN instead. */
    OPTIONAL,
    /** The app PIN only. */
    REQUIRED,
    /** The system prompt succeeded; now the app PIN (two-step unlock). */
    SECOND,
}

@Composable
fun AuthScreen(
    onAuthenticate: () -> Unit,
    state: AuthUiState = AuthUiState(),
    onOpenSecuritySettings: () -> Unit = {},
    onSubmitPin: (CharArray) -> Unit = {},
) {
    // Prompt immediately on entering the locked screen (never delayed by the entrance animation).
    // Not on first run (the explanation must be readable first) nor without a device credential
    // (there is nothing to prompt with).
    LaunchedEffect(Unit) {
        if (!state.firstRun && !state.noDeviceCredential) onAuthenticate()
    }
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Quiet staggered entrance: badge, then title + subtitle, then the actions. Pure ease-out.
    val animate = animationsEnabled()
    val badgeIn = remember { Animatable(if (animate) 0f else 1f) }
    val textIn = remember { Animatable(if (animate) 0f else 1f) }
    val actionsIn = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        launch { badgeIn.animateTo(1f, tween(Motion.LONG, 0, Motion.EaseOutQuint)) }
        launch { textIn.animateTo(1f, tween(Motion.LONG, 60, Motion.EaseOutQuint)) }
        launch { actionsIn.animateTo(1f, tween(Motion.LONG, 120, Motion.EaseOutQuint)) }
    }

    Scaffold { pad ->
        // Flat background, no glow: the logo and the type carry the screen.
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(pad).padding(horizontal = if (landscape) 24.dp else 28.dp, vertical = if (landscape) 12.dp else 24.dp),
        ) {
            val screenH = maxHeight
            // The app's own mark (keyhole in a circle) in the accent, instead of a padlock.
            val logo: @Composable (Int) -> Unit = { sizeDp ->
                Icon(
                    androidx.compose.ui.res.painterResource(com.cripta.app.R.drawable.ic_cripta_logo), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizeDp.dp).entrance({ badgeIn.value }, fromScale = 0.92f),
                )
            }
            val pinOnly = !state.firstRun && (state.pin == PinStep.REQUIRED || state.pin == PinStep.SECOND)
            val hasCode = !state.firstRun && !state.noDeviceCredential && state.pin != PinStep.NONE
            val code: @Composable (Boolean) -> Unit = { compact ->
                CodeEntry(kind = state.secretKind, busy = state.pinBusy, failed = state.error != null, alternative = !pinOnly,
                    compact = compact, onSubmit = onSubmitPin)
            }
            // Name and what is needed to get in.
            val header: @Composable (Boolean) -> Unit = { compact ->
                val align = if (compact) TextAlign.Start else TextAlign.Center
                Text(
                    if (state.firstRun) str(R.string.auth_create_title) else str(R.string.app_name),
                    // DS: the name on the lock screen in `display`.
                    style = if (state.firstRun) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
                    textAlign = align,
                    modifier = Modifier.padding(top = if (compact) 0.dp else 24.dp).semantics { heading() }.entrance({ textIn.value }),
                )
                Column(Modifier.entrance({ textIn.value })) {
                    when {
                        state.noDeviceCredential -> Text(
                            str(R.string.auth_need_lock),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = align,
                            modifier = Modifier.padding(top = 6.dp).widthIn(max = 420.dp),
                        )
                        state.firstRun -> FirstRunExplanation(compact)
                        else -> Text(
                            when (state.pin) {
                                PinStep.REQUIRED -> str(R.string.auth_locked_enter, state.secretKind.noun)
                                PinStep.SECOND -> str(R.string.auth_confirmed_enter, state.secretKind.noun)
                                PinStep.OPTIONAL -> str(R.string.auth_locked_optional, state.secretKind.noun)
                                PinStep.NONE -> str(R.string.auth_locked)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = align,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            // What the thumb reaches: the error (persistent, read out), then the way in.
            val actions: @Composable (Boolean, Boolean) -> Unit = { compact, withCode ->
                Column(
                    Modifier.fillMaxWidth().entrance({ actionsIn.value }),
                    horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    state.error?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = if (compact) TextAlign.Start else TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp).widthIn(max = 420.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.noDeviceCredential) {
                        Button(onClick = onOpenSecuritySettings, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 52.dp)) {
                            Icon(CriptaIcons.Settings, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            com.cripta.app.ui.components.ButtonText(str(R.string.auth_open_security))
                        }
                        TextButton(onClick = onAuthenticate, modifier = Modifier.padding(top = 4.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.retry)) }
                    } else {
                        // DS Sblocco: the one filled action first, the app code under it as a text button.
                        if (!pinOnly) {
                            Button(onClick = onAuthenticate, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                                if (!state.firstRun) {
                                    Icon(CriptaIcons.Fingerprint, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.size(8.dp))
                                }
                                com.cripta.app.ui.components.ButtonText(if (state.firstRun) str(R.string.auth_create_vault) else str(R.string.unlock))
                            }
                        }
                        if (withCode && hasCode) {
                            if (!pinOnly) Spacer(Modifier.size(8.dp))
                            code(compact)
                        }
                        if (!compact) Spacer(Modifier.size(24.dp))
                    }
                }
            }
            if (landscape && hasCode) {
                // Short screen with the app code: logo and text on the left, keypad / field on the right.
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 24.dp)) {
                        logo(56)
                        Spacer(Modifier.size(16.dp))
                        header(true)
                        Spacer(Modifier.size(20.dp))
                        actions(true, false)
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance({ actionsIn.value }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { code(true) }
                    }
                }
            } else if (landscape) {
                Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    logo(80)
                    Column(Modifier.padding(start = 32.dp)) {
                        header(true)
                        Spacer(Modifier.size(20.dp))
                        actions(true, true)
                    }
                }
            } else {
                // Portrait: the mark and the name in the upper part, the way in at the bottom, in
                // reach of the thumb. Scrolls only when a tall keypad or text does not fit.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = screenH),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // DS position (a quarter down) only when the way in is one button: with the app
                    // code's keypad or field below, that much room pushed the keypad off the screen.
                    Column(Modifier.padding(top = screenH * (if (hasCode) 0.12f else 0.24f)), horizontalAlignment = Alignment.CenterHorizontally) {
                        logo(72)
                        header(false)
                    }
                    Spacer(Modifier.size(32.dp))
                    actions(false, true)
                }
            }
        }
    }
}

/**
 * The app code on the lock screen: a keypad for a PIN, a masked field for a password. In the
 * "fingerprint or code" mode it starts folded behind a button, so the screen stays short.
 */
@Composable
private fun CodeEntry(
    kind: com.cripta.app.security.SecretKind,
    busy: Boolean,
    failed: Boolean,
    alternative: Boolean,
    compact: Boolean,
    onSubmit: (CharArray) -> Unit,
) {
    var open by remember(alternative) { mutableStateOf(!alternative) }
    if (!open) {
        TextButton(onClick = { open = true }) { com.cripta.app.ui.components.ButtonText(str(R.string.auth_use_code, kind.noun)) }
        return
    }
    // A check that ends with an error (busy -> idle, error set) is a refused code: shake once.
    var fails by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var wasBusy by remember { mutableStateOf(busy) }
    LaunchedEffect(busy, failed) {
        if (wasBusy && !busy && failed) fails++
        wasBusy = busy
    }
    androidx.compose.foundation.layout.Box(Modifier.shakeOn(fails)) {
        if (kind == com.cripta.app.security.SecretKind.PIN) PinKeypad(busy, compact, onSubmit)
        else PasswordEntry(busy, autoFocus = true, compact = compact, onSubmit = onSubmit)
    }
}

/** Numeric keypad: dots for the digits typed, ⌫ (hold to clear), ✓ to unlock. */
@Composable
private fun PinKeypad(busy: Boolean, compact: Boolean, onSubmit: (CharArray) -> Unit) {
    var digits by remember { mutableStateOf("") }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Compact (landscape): five rows in about 270dp, within a phone's short side.
    val key = if (compact) 52.dp else 72.dp
    val gap = if (compact) 8.dp else 16.dp
    val ready = digits.length >= com.cripta.app.security.PinCrypto.MIN_LENGTH && !busy
    fun tap() = haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    fun add(d: Char) {
        if (!busy && digits.length < com.cripta.app.security.PinCrypto.MAX_PIN_LENGTH) { tap(); digits += d }
    }
    fun submit() {
        if (ready) {
            onSubmit(digits.toCharArray())
            digits = "" // never kept around, and cleared for the next try after a wrong PIN
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(width = 240.dp, height = 28.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (busy) str(R.string.auth_checking_pin) else str(R.string.auth_digits_entered, digits.length)
            },
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                digits.isEmpty() -> Text(str(R.string.auth_enter_pin), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(digits.length) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
            }
        }
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { d -> KeypadKey(key, enabled = !busy, onClick = { add(d) }) { Text(d.toString(), style = MaterialTheme.typography.headlineMedium) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            KeypadKey(key, enabled = !busy && digits.isNotEmpty(), tonal = false,
                onClick = { tap(); digits = digits.dropLast(1) }, onLongClick = { tap(); digits = "" },
                label = str(R.string.auth_delete_digit)) {
                Icon(CriptaIcons.Backspace, null)
            }
            KeypadKey(key, enabled = !busy, onClick = { add('0') }) { Text("0", style = MaterialTheme.typography.headlineMedium) }
            KeypadKey(key, enabled = ready, primary = true, onClick = { submit() }, label = str(R.string.unlock)) {
                Icon(CriptaIcons.Check, null)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    tonal: Boolean = true,
    primary: Boolean = false,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        primary && enabled -> cs.primary
        tonal -> cs.surfaceVariant
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = if (primary && enabled) cs.onPrimary else cs.onSurface
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(bg)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick,
                role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label)
            .graphicsLayer { alpha = if (enabled || primary) 1f else 0.38f }
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) { content() }
    }
}

/** A password: masked text field, submitted with the keyboard's Done or the button. */
@Composable
private fun PasswordEntry(busy: Boolean, autoFocus: Boolean, compact: Boolean, onSubmit: (CharArray) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    val kind = com.cripta.app.security.SecretKind.PASSWORD
    val ready = text.length >= com.cripta.app.security.PinCrypto.MIN_LENGTH && !busy
    val submit = {
        if (ready) {
            onSubmit(text.toCharArray())
            text = ""
        }
    }
    Column(horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally) {
        com.cripta.app.ui.components.CriptaTextField(
            value = text,
            onValueChange = { v -> if (com.cripta.app.security.PinCrypto.accepts(v, kind)) text = v },
            label = { Text(str(R.string.auth_password_label)) },
            singleLine = true,
            enabled = !busy,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
            modifier = Modifier.widthIn(max = 280.dp).focusRequester(focus),
        )
        com.cripta.app.ui.components.FilledTonalButton(onClick = submit, enabled = ready, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            }
            Text(if (busy) str(R.string.auth_checking) else str(R.string.unlock))
        }
    }
}

/** First launch: what "creating the vault" means, before the system prompt appears. */
@Composable
private fun FirstRunExplanation(compact: Boolean) {
    val points = listOf(
        str(R.string.auth_info_encryption),
        str(R.string.auth_info_loss),
        str(R.string.auth_info_backup),
    )
    Column(
        Modifier.padding(top = 12.dp).widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        points.forEach { p ->
            Row {
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(top = 7.dp).size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(Modifier.size(12.dp))
                Text(p, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Entrance of one element: fade + 12dp rise (+ optional scale), read at draw time only. */
private fun Modifier.entrance(progress: () -> Float, fromScale: Float = 1f): Modifier = graphicsLayer {
    val p = progress()
    alpha = p
    translationY = (1f - p) * 12.dp.toPx()
    if (fromScale != 1f) {
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
}

/**
 * Shown when the Keystore key was permanently invalidated (e.g. an older install whose key was
 * tied to biometric enrollment, or the device lock was removed). The data behind it can no longer
 * be decrypted, so the only way forward is to reset the vault and start over.
 */
@Composable
fun KeyInvalidatedDialog(onReset: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.auth_cannot_unlock)) },
        text = {
            Text(
                str(R.string.auth_key_invalid)
            )
        },
        confirmButton = {
            TextButton(onClick = onReset) {
                com.cripta.app.ui.components.ButtonText(str(R.string.auth_reset_vault), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

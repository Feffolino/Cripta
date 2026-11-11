package com.cripta.app.ui.settings

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.cripta.app.security.BiometricAuth
import com.cripta.app.security.KeyVault
import com.cripta.app.security.PinCrypto
import com.cripta.app.security.SecretKind
import com.cripta.app.security.UnlockMode
import kotlinx.coroutines.launch

/**
 * The unlock mode choice (Impostazioni › Sicurezza e privacy). A change is confirmed with the current way in
 * (the system prompt, or the current app PIN in PIN-only mode) and asks for a new PIN whenever the
 * chosen mode uses one; the vault key is then re-wrapped for the new mode in one step.
 */
@Composable
internal fun UnlockModeOptions(vm: SettingsViewModel) {
    val mode by vm.unlockMode.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<UnlockMode?>(null) }
    var askCurrentPin by remember { mutableStateOf(false) }
    var askNewPin by remember { mutableStateOf(false) }
    /** SYSTEM_AND_PIN: what the system step opened, to check the current PIN against. */
    var systemLayer by remember { mutableStateOf<ByteArray?>(null) }

    /** The current-PIN check under way: "Annulla" stops it, so a late "right" goes nowhere. */
    var pinCheck by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun reset() {
        pinCheck?.cancel(); pinCheck = null
        target = null; askCurrentPin = false; askNewPin = false; systemLayer?.fill(0); systemLayer = null
    }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    // Last step: the system prompt when either the old or the new mode involves it, then apply.
    fun confirmAndApply(m: UnlockMode, pin: CharArray?, kind: SecretKind) {
        if (!mode.usesSystem && !m.usesSystem) {
            vm.applyUnlockMode(m, pin, kind, null); reset(); return
        }
        val cipher = vm.cipherForModeChange()
        if (activity == null || cipher == null) {
            pin?.fill('0'); reset(); toast(str(R.string.keystore_prepare_failed)); return
        }
        BiometricAuth.authenticate(
            activity = activity,
            cipher = cipher,
            title = str(R.string.confirm_change),
            subtitle = str(R.string.unlock_with, m.label),
            onSuccess = { c -> vm.applyUnlockMode(m, pin, kind, if (m.usesSystem) c else null); reset() },
            onError = { msg -> pin?.fill('0'); reset(); msg?.let(::toast) },
        )
    }
    fun afterCurrentCheck(m: UnlockMode) {
        if (m.usesPin) askNewPin = true else confirmAndApply(m, null, SecretKind.PIN)
    }
    fun start(m: UnlockMode) {
        target = m
        when {
            // Without the system prompt in the current mode, the current PIN confirms the change.
            !mode.usesSystem -> askCurrentPin = true
            // Both factors: the system step, then the current app PIN. With the prompt alone, a
            // finger enrolled on the phone could remove the app code (or change it) for good.
            mode == UnlockMode.SYSTEM_AND_PIN -> {
                val cipher = vm.cipherForSystemStep()
                if (activity == null || cipher == null) {
                    reset(); toast(str(R.string.keystore_prepare_failed)); return
                }
                BiometricAuth.authenticate(
                    activity = activity,
                    cipher = cipher,
                    title = str(R.string.confirm_change),
                    subtitle = str(R.string.then_current_code),
                    onSuccess = { c ->
                        val layer = vm.unwrapSystemLayer(c)
                        if (layer == null) { reset(); toast(str(R.string.verify_failed)) }
                        else { systemLayer = layer; askCurrentPin = true }
                    },
                    onError = { msg -> reset(); msg?.let(::toast) },
                )
            }
            else -> afterCurrentCheck(m)
        }
    }

    Column(Modifier.selectableGroup()) {
        UnlockMode.entries.forEach { m ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                    .selectable(selected = m == mode, role = Role.RadioButton, onClick = { if (m != mode) start(m) })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = m == mode, onClick = null)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(m.label, style = MaterialTheme.typography.bodyLarge)
                    Text(m.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (mode.usesPin) {
            TextButton(onClick = { start(mode) }) { com.cripta.app.ui.components.ButtonText(str(R.string.change_code)) }
        }
    }

    val t = target
    if (askCurrentPin && t != null) {
        CurrentPinDialog(
            kind = vm.secretKind,
            onCheck = { pin, onWrong ->
                pinCheck?.cancel()
                pinCheck = scope.launch {
                    val r = vm.verifyCurrentPin(pin, systemLayer)
                    // Cancelled meanwhile, or the dialog closed: nothing follows.
                    if (!askCurrentPin || target !== t) return@launch
                    when (r) {
                        KeyVault.PinResult.Ok -> {
                            askCurrentPin = false; systemLayer?.fill(0); systemLayer = null
                            afterCurrentCheck(t)
                        }
                        is KeyVault.PinResult.Wrong -> {
                            val what = if (vm.secretKind == SecretKind.PIN) str(R.string.pin_wrong) else str(R.string.password_wrong)
                            onWrong(if (r.waitSeconds > 0) str(R.string.wrong_retry_in, what, waitText(r.waitSeconds)) else str(R.string.wrong_dot, what))
                        }
                        is KeyVault.PinResult.Wait -> onWrong(str(R.string.unlock_wait, waitText(r.seconds)))
                    }
                }
            },
            onDismiss = { reset() },
        )
    }
    if (askNewPin && t != null) {
        NewPinDialog(
            changing = t == mode,
            onlyPin = !t.usesSystem,
            initialKind = if (mode.usesPin) vm.secretKind else SecretKind.PIN,
            onConfirm = { pin, kind -> askNewPin = false; confirmAndApply(t, pin, kind) },
            onDismiss = { reset() },
        )
    }
}

private fun waitText(seconds: Long): String = if (seconds < 60) plural(R.plurals.wait_seconds, seconds.toInt()) else plural(R.plurals.wait_minutes, ((seconds + 59) / 60).toInt())

private tailrec fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
private fun PinField(value: String, onValue: (String) -> Unit, label: String, kind: SecretKind,
                     isError: Boolean = false, supporting: String? = null) {
    com.cripta.app.ui.components.CriptaTextField(
        value = value,
        onValueChange = { v -> if (PinCrypto.accepts(v, kind)) onValue(v) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = if (supporting != null) ({ Text(supporting) }) else null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (kind == SecretKind.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CurrentPinDialog(kind: SecretKind, onCheck: (CharArray, (String) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val noun = kind.noun
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == SecretKind.PIN) str(R.string.current_pin) else str(R.string.current_password)) },
        text = {
            // Scrolls on a short (landscape) screen with the keyboard open.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(str(R.string.enter_code_to_confirm, noun))
                PinField(pin, { pin = it; error = null }, if (kind == SecretKind.PIN) str(R.string.current_pin) else str(R.string.current_password),
                    kind, isError = error != null, supporting = error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCheck(pin.toCharArray()) { error = it }; pin = "" },
                enabled = pin.length >= PinCrypto.MIN_LENGTH,
            ) { com.cripta.app.ui.components.ButtonText(str(R.string.continue_)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

/** New app code, PIN or password, typed twice (a typo here would lock the user out). */
@Composable
private fun NewPinDialog(
    changing: Boolean,
    onlyPin: Boolean,
    initialKind: SecretKind,
    onConfirm: (CharArray, SecretKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val isPin = kind == SecretKind.PIN
    val tooShort = pin.length < PinCrypto.MIN_LENGTH
    val mismatch = again.isNotEmpty() && again != pin
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (changing) str(R.string.new_code) else str(R.string.choose_code)) },
        text = {
            // Scrolls on a short (landscape) screen with the keyboard open.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // PIN: a keypad on the lock screen. Password: letters, digits and symbols, keyboard.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecretKind.entries.forEach { k ->
                        com.cripta.app.ui.components.ChoiceChip(
                            selected = kind == k,
                            onClick = { if (kind != k) { kind = k; pin = ""; again = "" } },
                            label = { Text(if (k == SecretKind.PIN) str(R.string.numeric_pin) else str(R.string.password_cap)) },
                        )
                    }
                }
                Text(
                    (if (isPin) str(R.string.pin_rules, PinCrypto.MIN_LENGTH, PinCrypto.MAX_PIN_LENGTH)
                    else str(R.string.password_rules, PinCrypto.MIN_LENGTH, PinCrypto.MAX_PASSWORD_LENGTH)) +
                        if (onlyPin) str(R.string.code_forget_warning)
                        else str(R.string.code_wait_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField(pin, { pin = it }, if (isPin) "PIN" else str(R.string.password_cap), kind,
                    supporting = if (tooShort) str(if (isPin) R.string.pin_too_short else R.string.password_too_short, PinCrypto.MIN_LENGTH, pin.length) else null)
                PinField(again, { again = it }, if (isPin) str(R.string.repeat_pin) else str(R.string.repeat_password), kind,
                    isError = mismatch, supporting = if (mismatch) str(R.string.no_match) else null)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin.toCharArray(), kind); pin = ""; again = "" },
                enabled = !tooShort && again == pin,
            ) { com.cripta.app.ui.components.ButtonText(str(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

package com.cripta.app.ui.settings

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
 * The unlock mode choice (Impostazioni › Sicurezza). A change is confirmed with the current way in
 * (the system prompt, or the current app PIN in PIN-only mode) and asks for a new PIN whenever the
 * chosen mode uses one; the vault key is then re-wrapped for the new mode in one step.
 */
@Composable
internal fun UnlockModeOptions(vm: SettingsViewModel) {
    val mode by vm.unlockMode.collectAsState()
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<UnlockMode?>(null) }
    var askCurrentPin by remember { mutableStateOf(false) }
    var askNewPin by remember { mutableStateOf(false) }

    fun reset() { target = null; askCurrentPin = false; askNewPin = false }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    // Last step: the system prompt when either the old or the new mode involves it, then apply.
    fun confirmAndApply(m: UnlockMode, pin: CharArray?, kind: SecretKind) {
        if (!mode.usesSystem && !m.usesSystem) {
            vm.applyUnlockMode(m, pin, kind, null); reset(); return
        }
        val cipher = vm.cipherForModeChange()
        if (activity == null || cipher == null) {
            pin?.fill('0'); reset(); toast("Impossibile preparare la chiave di sicurezza. Riprova."); return
        }
        BiometricAuth.authenticate(
            activity = activity,
            cipher = cipher,
            title = "Conferma la modifica",
            subtitle = "Sblocco: ${m.label}",
            onSuccess = { c -> vm.applyUnlockMode(m, pin, kind, if (m.usesSystem) c else null); reset() },
            onError = { msg -> pin?.fill('0'); reset(); msg?.let(::toast) },
        )
    }
    fun afterCurrentCheck(m: UnlockMode) {
        if (m.usesPin) askNewPin = true else confirmAndApply(m, null, SecretKind.PIN)
    }
    fun start(m: UnlockMode) {
        target = m
        // Without the system prompt in the current mode, the current PIN confirms the change.
        if (!mode.usesSystem) askCurrentPin = true else afterCurrentCheck(m)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (mode.usesPin) {
            TextButton(onClick = { start(mode) }) { Text("Cambia codice (PIN o password)") }
        }
    }

    val t = target
    if (askCurrentPin && t != null) {
        CurrentPinDialog(
            kind = vm.secretKind,
            onCheck = { pin, onWrong ->
                scope.launch {
                    when (val r = vm.verifyCurrentPin(pin)) {
                        KeyVault.PinResult.Ok -> { askCurrentPin = false; afterCurrentCheck(t) }
                        is KeyVault.PinResult.Wrong -> {
                            val what = if (vm.secretKind == SecretKind.PIN) "PIN errato" else "Password errata"
                            onWrong(if (r.waitSeconds > 0) "$what. Riprova tra ${waitText(r.waitSeconds)}." else "$what.")
                        }
                        is KeyVault.PinResult.Wait -> onWrong("Troppi tentativi: riprova tra ${waitText(r.seconds)}.")
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

private fun waitText(seconds: Long): String = if (seconds < 60) "$seconds secondi" else "${(seconds + 59) / 60} minuti"

private tailrec fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
private fun PinField(value: String, onValue: (String) -> Unit, label: String, kind: SecretKind,
                     isError: Boolean = false, supporting: String? = null) {
    OutlinedTextField(
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
        title = { Text(if (kind == SecretKind.PIN) "PIN attuale" else "Password attuale") },
        text = {
            // Scrolls on a short (landscape) screen with the keyboard open.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Inserisci il $noun di Cripta per confermare la modifica.")
                PinField(pin, { pin = it; error = null }, if (kind == SecretKind.PIN) "PIN attuale" else "Password attuale",
                    kind, isError = error != null, supporting = error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCheck(pin.toCharArray()) { error = it }; pin = "" },
                enabled = pin.length >= PinCrypto.MIN_LENGTH,
            ) { Text("Continua") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
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
        title = { Text(if (changing) "Nuovo codice" else "Scegli il codice di Cripta") },
        text = {
            // Scrolls on a short (landscape) screen with the keyboard open.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // PIN: a keypad on the lock screen. Password: letters, digits and symbols, keyboard.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecretKind.entries.forEach { k ->
                        androidx.compose.material3.FilterChip(
                            selected = kind == k,
                            onClick = { if (kind != k) { kind = k; pin = ""; again = "" } },
                            label = { Text(if (k == SecretKind.PIN) "PIN numerico" else "Password") },
                        )
                    }
                }
                Text(
                    (if (isPin) "Da ${PinCrypto.MIN_LENGTH} a ${PinCrypto.MAX_PIN_LENGTH} cifre, diverso da quello del telefono. "
                    else "Da ${PinCrypto.MIN_LENGTH} a ${PinCrypto.MAX_PASSWORD_LENGTH} caratteri: lettere, numeri e simboli. ") +
                        if (onlyPin) "Se lo dimentichi il vault non si apre più: resta solo un backup cifrato."
                        else "Dopo 5 tentativi errati serve un'attesa, che cresce a ogni errore.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField(pin, { pin = it }, if (isPin) "PIN" else "Password", kind,
                    supporting = if (tooShort) "Almeno ${PinCrypto.MIN_LENGTH} ${if (isPin) "cifre" else "caratteri"} " +
                        "(${pin.length}/${PinCrypto.MIN_LENGTH})" else null)
                PinField(again, { again = it }, if (isPin) "Ripeti il PIN" else "Ripeti la password", kind,
                    isError = mismatch, supporting = if (mismatch) "Non coincidono" else null)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin.toCharArray(), kind); pin = ""; again = "" },
                enabled = !tooShort && again == pin,
            ) { Text("Conferma") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

package com.cripta.app.ui.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 */
data class AuthUiState(
    val firstRun: Boolean = false,
    val error: String? = null,
    val noDeviceCredential: Boolean = false,
)

@Composable
fun AuthScreen(
    onAuthenticate: () -> Unit,
    state: AuthUiState = AuthUiState(),
    onOpenSecuritySettings: () -> Unit = {},
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
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.background,
                        ),
                        radius = 900f,
                    )
                )
                .padding(pad).padding(if (landscape) 16.dp else 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val badge: @Composable (Int) -> Unit = { sizeDp ->
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(sizeDp.dp).entrance({ badgeIn.value }, fromScale = 0.9f),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Lock, contentDescription = null,
                            modifier = Modifier.size((sizeDp * 0.46f).dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            val texts: @Composable (Boolean) -> Unit = { compact ->
                val align = if (compact) TextAlign.Start else TextAlign.Center
                Text(
                    if (state.firstRun) "Crea il tuo vault" else "Cripta",
                    style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                    textAlign = align,
                    modifier = Modifier.semantics { heading() }.entrance({ textIn.value }),
                )
                Column(Modifier.entrance({ textIn.value })) {
                    when {
                        state.noDeviceCredential -> Text(
                            "Per proteggere il vault Cripta usa il blocco schermo del telefono. " +
                                "Imposta un PIN, una sequenza o una password (e, se vuoi, l'impronta o il " +
                                "volto) nelle impostazioni di sicurezza, poi torna qui.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = align,
                            modifier = Modifier.padding(top = 8.dp, bottom = if (compact) 16.dp else 28.dp),
                        )
                        state.firstRun -> FirstRunExplanation(compact)
                        else -> Text(
                            "Vault cifrato. Autenticati per accedere.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = align,
                            modifier = Modifier.padding(top = 8.dp, bottom = if (compact) 16.dp else 28.dp),
                        )
                    }
                }
                Column(
                    horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
                    modifier = Modifier.entrance({ actionsIn.value }),
                ) {
                    if (state.noDeviceCredential) {
                        Button(
                            onClick = onOpenSecuritySettings,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Icon(Icons.Filled.Settings, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Apri impostazioni di sicurezza")
                        }
                        TextButton(onClick = onAuthenticate, modifier = Modifier.padding(top = 4.dp)) {
                            Text("Riprova")
                        }
                    } else {
                        Button(
                            onClick = onAuthenticate,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                        ) {
                            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(if (state.firstRun) "Crea vault" else "Sblocca")
                        }
                    }
                    // Persistent (not a Toast): stays until the next attempt, read out by TalkBack.
                    state.error?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = align,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .widthIn(max = 420.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            if (landscape) {
                // Short screen: badge beside the text, so the unlock button is never cut off.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    badge(88)
                    Column(Modifier.padding(start = 28.dp)) { texts(true) }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    badge(112)
                    Spacer(Modifier.size(24.dp))
                    texts(false)
                }
            }
        }
    }
}

/** First launch: what "creating the vault" means, before the system prompt appears. */
@Composable
private fun FirstRunExplanation(compact: Boolean) {
    val points = listOf(
        "I file vengono cifrati sul telefono con una chiave protetta dal blocco schermo " +
            "(impronta, volto, PIN o sequenza). Nessuno, nemmeno Cripta, può leggerli senza di te.",
        "Se rimuovi il blocco schermo, ripristini il telefono o disinstalli l'app, i file del " +
            "vault non saranno più recuperabili.",
        "Crea regolarmente un backup cifrato da Impostazioni: è l'unico modo per ritrovare i " +
            "file su un altro telefono.",
    )
    Column(
        Modifier.padding(top = 12.dp, bottom = if (compact) 16.dp else 28.dp).widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        points.forEach { p ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
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
        title = { Text("Impossibile sbloccare") },
        text = {
            Text(
                "La chiave di sicurezza non è più valida perché la biometria del dispositivo è " +
                    "cambiata o il blocco schermo è stato rimosso. I dati protetti non sono più " +
                    "recuperabili. Puoi reimpostare il vault per ricominciare da capo. " +
                    "L'operazione è irreversibile ed elimina tutti i file cifrati.\n\n" +
                    "Se avevi creato un backup cifrato, dopo la reimpostazione potrai ripristinarlo " +
                    "da Impostazioni → Backup con la sua passphrase."
            )
        },
        confirmButton = {
            TextButton(onClick = onReset) {
                Text("Reimposta vault", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

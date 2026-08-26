package com.cripta.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(onAuthenticate: () -> Unit) {
    // Prompt immediately on entering the locked screen.
    LaunchedEffect(Unit) { onAuthenticate() }

    Scaffold { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Cripta",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Vault cifrato. Autenticati per accedere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(onClick = onAuthenticate) { Text("Sblocca") }
        }
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
                    "L'operazione è irreversibile ed elimina tutti i file cifrati."
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

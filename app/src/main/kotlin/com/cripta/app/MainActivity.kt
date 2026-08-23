package com.cripta.app

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.cripta.app.data.SettingsStore
import com.cripta.app.security.BiometricAuth
import com.cripta.app.security.KeyVault
import com.cripta.app.security.SessionManager
import com.cripta.app.ui.AppRoot
import com.cripta.app.ui.theme.CriptaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var keyVault: KeyVault
    @Inject lateinit var session: SessionManager
    @Inject lateinit var settings: SettingsStore

    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots and hide content in the recents switcher.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            CriptaTheme {
                AppRoot(
                    session = session,
                    onAuthenticate = { authenticate() },
                )
            }
        }
    }

    /** Runs setup on first launch, otherwise unlock. */
    private fun authenticate() {
        if (!BiometricAuth.canAuthenticate(this)) {
            Toast.makeText(this, "Nessuna biometria/credenziale impostata sul dispositivo", Toast.LENGTH_LONG).show()
            return
        }
        val setup = !keyVault.isInitialized
        val cipher = runCatching {
            if (setup) keyVault.cipherForSetup() else keyVault.cipherForUnlock()
        }.getOrElse {
            Toast.makeText(this, "Errore chiave: ${it.message}", Toast.LENGTH_LONG).show()
            return
        }
        BiometricAuth.authenticate(
            activity = this,
            cipher = cipher,
            title = if (setup) "Configura Cripta" else "Sblocca Cripta",
            subtitle = if (setup) "Conferma per creare il vault" else "Autenticati per accedere",
            onSuccess = { authed ->
                lifecycleScope.launch {
                    runCatching {
                        if (setup) keyVault.completeSetup(authed) else keyVault.completeUnlock(authed)
                    }.onFailure {
                        Toast.makeText(this@MainActivity, "Sblocco fallito: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            },
        )
    }

    override fun onStop() {
        super.onStop()
        if (session.isUnlocked) backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (session.isUnlocked && backgroundedAt > 0L) {
            lifecycleScope.launch {
                val minutes = settings.settings.first().autoLockMinutes
                val elapsed = System.currentTimeMillis() - backgroundedAt
                if (elapsed >= minutes * 60_000L) session.lock()
            }
        }
    }
}

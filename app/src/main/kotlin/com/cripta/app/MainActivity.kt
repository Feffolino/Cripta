package com.cripta.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.cripta.app.data.SettingsStore
import com.cripta.app.security.BiometricAuth
import com.cripta.app.security.KeyVault
import com.cripta.app.security.SessionManager
import com.cripta.app.ui.AppRoot
import com.cripta.app.ui.auth.KeyInvalidatedDialog
import com.cripta.app.ui.theme.CriptaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var keyVault: KeyVault
    @Inject lateinit var session: SessionManager
    @Inject lateinit var settings: SettingsStore

    private var backgroundedAt = 0L

    /** Set when the Keystore key is permanently invalidated; drives the reset-vault dialog. */
    private val keyInvalidated = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification permission (Android 13+) so the conversion progress notification can show.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42) }
        }
        // Block screenshots and hide content in the recents switcher.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        // Edge-to-edge with transparent system bars (Compose Scaffolds handle the insets).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        // Extend the window into the display cutout so that strip shows the app background (tinted
        // below) instead of a black system letterbox; content is kept out of it via insets.
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val set by settings.settings.collectAsState(initial = com.cripta.app.data.Settings())
            // Honour the "allow screenshots" setting: FLAG_SECURE on unless the user opted out.
            LaunchedEffect(set.allowScreenshots) {
                if (set.allowScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val invalidated by keyInvalidated.collectAsState()
            CriptaTheme(themeMode = set.themeMode, dynamicColor = set.dynamicColor) {
                // System bars stay transparent so the content drawn behind them shows through and
                // the bar / camera-cutout strips ADAPT to the current screen — the app surface on
                // the library/settings, black in the video player — instead of a fixed colour.
                val lightBars = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                androidx.compose.runtime.SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = lightBars
                    controller.isAppearanceLightNavigationBars = lightBars
                }
                AppRoot(
                    session = session,
                    onAuthenticate = { authenticate() },
                )
                if (invalidated) {
                    KeyInvalidatedDialog(
                        onReset = {
                            lifecycleScope.launch {
                                runCatching { keyVault.resetVault() }
                                keyInvalidated.value = false
                                authenticate() // key gone -> this now runs first-time setup
                            }
                        },
                        onDismiss = { keyInvalidated.value = false },
                    )
                }
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
            if (keyVault.isKeyInvalidated(it)) {
                keyInvalidated.value = true
            } else {
                Toast.makeText(this, "Errore chiave: ${it.message}", Toast.LENGTH_LONG).show()
            }
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
                        if (keyVault.isKeyInvalidated(it)) {
                            keyInvalidated.value = true
                        } else {
                            Toast.makeText(this@MainActivity, "Sblocco fallito: ${it.message}", Toast.LENGTH_LONG).show()
                        }
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
                // minutes < 0 means "Mai" (never auto-lock).
                if (minutes >= 0 && elapsed >= minutes * 60_000L) session.lock()
            }
        }
    }
}

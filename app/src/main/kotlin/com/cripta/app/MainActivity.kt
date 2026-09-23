package com.cripta.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.security.BiometricAuth
import com.cripta.app.security.KeyVault
import com.cripta.app.security.SessionManager
import com.cripta.app.ui.AppRoot
import com.cripta.app.ui.auth.AuthUiState
import com.cripta.app.ui.auth.KeyInvalidatedDialog
import com.cripta.app.ui.theme.CriptaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var keyVault: KeyVault
    @Inject lateinit var session: SessionManager
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var repo: VaultRepository
    @Inject lateinit var sharedLinks: com.cripta.app.data.SharedLinkStore
    @Inject lateinit var sharedFiles: com.cripta.app.data.SharedFilesStore
    @Inject lateinit var dupStore: com.cripta.app.data.dedup.DupScanStore

    companion object {
        /** Intent extra from the "scan finished" notification: open the duplicate results. */
        const val EXTRA_OPEN_DUPLICATES = "open_duplicates"
        private const val REQ_NOTIFICATIONS = 42
        private const val UI_PREFS = "cripta_ui"
        private const val PREF_NOTIF_ASKED = "notif_permission_asked"
        /** Longest time in a system picker that still doesn't count as "leaving the app". */
        private const val PICKER_GRACE_MS = 5 * 60_000L
    }

    private fun handleOpenDuplicates(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DUPLICATES, false) == true) {
            intent.removeExtra(EXTRA_OPEN_DUPLICATES)
            dupStore.requestOpen()
        }
    }

    private var backgroundedAt = 0L

    /**
     * True while a screen of ours is waiting for a result from a system activity (file picker,
     * "save as" dialog, delete confirmation…). Returning from it is not "coming back to the app",
     * so it must not trigger the auto-lock — with "Subito" it locked on every picked file.
     */
    @Volatile private var awaitingResult = false

    /** Set when the Keystore key is permanently invalidated; drives the reset-vault dialog. */
    private val keyInvalidated = MutableStateFlow(false)

    /** What the lock screen shows (first run, last error, missing device credential). */
    private val authUi = MutableStateFlow(AuthUiState())
    /** A BiometricPrompt is on screen: don't stack a second one on a double tap. */
    private var authInFlight = false

    /** Drives the one-time notification-permission rationale (asked at the first background job). */
    private val askNotifications = MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
        handleOpenDuplicates(intent)
    }

    /**
     * Something was shared into Cripta.
     *
     * Files (EXTRA_STREAM, one or many) are kept in [SharedFilesStore] and encrypted into the vault
     * as soon as it is unlocked (AppRoot starts the import and shows Cartelle with its progress).
     *
     * A link (text/plain) goes to the Download screen (AppRoot navigates there) so the user can pick
     * a resolution instead of downloading blind. It is remembered even when the vault is locked:
     * the download needs an unlocked vault, but there is no reason to make the user re-share it.
     */
    private fun handleShare(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val streams = sharedStreams(intent)
        if (streams.isNotEmpty()) {
            sharedFiles.add(streams)
            // Consume the share: a later onNewIntent / recreation must not import it twice.
            intent.removeExtra(Intent.EXTRA_STREAM)
            intent.action = null
            val n = streams.size
            val what = if (n == 1) "il file" else "i $n file"
            if (session.locked.value) {
                Toast.makeText(this, "Sblocca Cripta per cifrare $what nel vault", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, if (n == 1) "Importazione di 1 file nel vault…" else "Importazione di $n file nel vault…", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return
        val url = text.split(Regex("\\s+")).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?: return
        sharedLinks.set(url)
        intent.action = null
        if (session.locked.value) {
            Toast.makeText(this, "Sblocca Cripta per preparare il download", Toast.LENGTH_LONG).show()
        }
    }

    /** The content uris of a SEND / SEND_MULTIPLE intent (EXTRA_STREAM, falling back to ClipData). */
    private fun sharedStreams(intent: Intent): List<Uri> {
        val out = ArrayList<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val list: List<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            list?.let { out += it }
        } else {
            val one: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            one?.let { out += it }
        }
        if (out.isEmpty()) {
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { out += it }
            }
        }
        // Only content:// uris: a file:// uri would bypass the sender's permissions.
        return out.filter { it.scheme == "content" }.distinct()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                // ALWAYS (not SHORT_EDGES): lets the window extend into the cutout on every edge and
                // rotation, so the cutout inset is reported consistently — including reverse
                // landscape (180° flip) where SHORT_EDGES let the system letterbox instead.
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        authUi.value = AuthUiState(
            firstRun = !keyVault.isInitialized,
            noDeviceCredential = !BiometricAuth.canAuthenticate(this),
        )
        // A recreated activity gets its original intent back: don't import a share twice.
        if (savedInstanceState == null) handleShare(intent)
        handleOpenDuplicates(intent)
        watchBackgroundWorkForNotificationPermission()
        setContent {
            val set by settings.settings.collectAsState(initial = com.cripta.app.data.Settings())
            // Honour the "allow screenshots" setting: FLAG_SECURE on unless the user opted out.
            LaunchedEffect(set.allowScreenshots) {
                if (set.allowScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val invalidated by keyInvalidated.collectAsState()
            val auth by authUi.collectAsState()
            val askNotif by askNotifications.collectAsState()
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
                    authState = auth,
                    onOpenSecuritySettings = { openSecuritySettings() },
                )
                if (invalidated) {
                    KeyInvalidatedDialog(
                        onReset = {
                            lifecycleScope.launch {
                                // Closes and deletes the database: never on the main thread.
                                withContext(Dispatchers.IO) { runCatching { keyVault.resetVault() } }
                                keyInvalidated.value = false
                                authUi.value = authUi.value.copy(firstRun = !keyVault.isInitialized, error = null)
                                authenticate() // key gone -> this now runs first-time setup
                            }
                        },
                        onDismiss = { keyInvalidated.value = false },
                    )
                }
                if (askNotif) {
                    AlertDialog(
                        onDismissRequest = { notificationRationaleAnswered(allow = false) },
                        title = { Text("Avanzamento in background") },
                        text = {
                            Text(
                                "Consenti le notifiche per seguire importazioni, download e conversioni " +
                                    "anche quando Cripta è in background, e sapere quando sono finite. " +
                                    "Sulla schermata di blocco non compaiono i nomi dei file."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { notificationRationaleAnswered(allow = true) }) { Text("Consenti") }
                        },
                        dismissButton = {
                            TextButton(onClick = { notificationRationaleAnswered(allow = false) }) { Text("Non ora") }
                        },
                    )
                }
            }
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Ask for the notification permission (Android 13+) the first time a background job starts —
     * that is when the progress notification becomes useful — with a short explanation first,
     * instead of a bare system dialog at the very first launch.
     */
    private fun watchBackgroundWorkForNotificationPermission() {
        val prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
        if (!needsNotificationPermission() || prefs.getBoolean(PREF_NOTIF_ASKED, false)) return
        lifecycleScope.launch {
            combine(repo.importState, repo.downloads, repo.convertStatus) { imp, dl, conv ->
                imp.active || conv.active || dl.any { !it.finished }
            }.distinctUntilChanged().collect { busy ->
                if (busy && needsNotificationPermission() && !prefs.getBoolean(PREF_NOTIF_ASKED, false)) {
                    askNotifications.value = true
                }
            }
        }
    }

    private fun notificationRationaleAnswered(allow: Boolean) {
        askNotifications.value = false
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putBoolean(PREF_NOTIF_ASKED, true).apply()
        if (allow && needsNotificationPermission()) {
            runCatching { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS) }
        }
    }

    /** No screen lock / biometrics: open the system screen where the user can set one up. */
    private fun openSecuritySettings() {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= 30 &&
                BiometricAuth.status(this@MainActivity) == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            ) {
                add(Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                    android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricAuth.AUTHENTICATORS,
                ))
            }
            add(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
            add(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
        for (i in candidates) {
            if (runCatching { startActivity(i) }.isSuccess) return
        }
    }

    /** Runs setup on first launch, otherwise unlock. */
    private fun authenticate() {
        if (authInFlight) return
        if (!BiometricAuth.canAuthenticate(this)) {
            // Explained on the lock screen itself, with a shortcut to the security settings.
            authUi.value = authUi.value.copy(noDeviceCredential = true, error = null)
            return
        }
        val setup = !keyVault.isInitialized
        authUi.value = authUi.value.copy(firstRun = setup, noDeviceCredential = false, error = null)
        val cipher = runCatching {
            if (setup) keyVault.cipherForSetup() else keyVault.cipherForUnlock()
        }.getOrElse {
            android.util.Log.e("MainActivity", "cipher init failed", it)
            if (keyVault.isKeyInvalidated(it)) {
                keyInvalidated.value = true
            } else {
                authUi.value = authUi.value.copy(
                    error = "Impossibile preparare la chiave di sicurezza. Riprova; se il problema " +
                        "continua, riavvia il telefono.",
                )
            }
            return
        }
        authInFlight = true
        BiometricAuth.authenticate(
            activity = this,
            cipher = cipher,
            title = if (setup) "Configura Cripta" else "Sblocca Cripta",
            subtitle = if (setup) "Conferma per creare il vault" else "Autenticati per accedere",
            onSuccess = { authed ->
                authInFlight = false
                lifecycleScope.launch {
                    runCatching {
                        if (setup) keyVault.completeSetup(authed) else keyVault.completeUnlock(authed)
                    }.onSuccess {
                        authUi.value = authUi.value.copy(firstRun = false, error = null)
                    }.onFailure {
                        android.util.Log.e("MainActivity", "unlock failed", it)
                        if (keyVault.isKeyInvalidated(it)) {
                            keyInvalidated.value = true
                        } else {
                            authUi.value = authUi.value.copy(
                                error = if (setup) "Creazione del vault non riuscita. Riprova."
                                else "Sblocco non riuscito. Riprova.",
                            )
                        }
                    }
                }
            },
            onError = { msg ->
                authInFlight = false
                // null = the user dismissed the prompt: nothing to report.
                authUi.value = authUi.value.copy(error = msg)
            },
        )
    }

    /** Leaving the app while a video plays: continue it in Picture-in-Picture if the user enabled it. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val aspect = com.cripta.app.viewer.PipController.armedAspect ?: return
        runCatching {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder().setAspectRatio(aspect).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.cripta.app.viewer.PipController.setInPip(isInPictureInPictureMode)
    }

    // Every ActivityResult launcher (pickers, CreateDocument, permission-free system screens) ends
    // up here; startActivity() goes through it too, with requestCode -1 (not a result: ignored).
    @Deprecated("Deprecated in ComponentActivity; overridden only to observe launches")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (requestCode >= 0) awaitingResult = true
        super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Deprecated in ComponentActivity; overridden only to observe launches")
    @Suppress("DEPRECATION")
    override fun startIntentSenderForResult(
        intent: android.content.IntentSender,
        requestCode: Int,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?,
    ) {
        if (requestCode >= 0) awaitingResult = true
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
    }

    override fun onResume() {
        super.onResume()
        // Back from the security settings with a screen lock now set up: offer the prompt again.
        if (authUi.value.noDeviceCredential && BiometricAuth.canAuthenticate(this)) {
            authUi.value = authUi.value.copy(noDeviceCredential = false, error = null)
            if (session.locked.value) authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        if (session.isUnlocked) backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val fromPicker = awaitingResult
        awaitingResult = false
        if (session.isUnlocked && backgroundedAt > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundedAt
            // Coming back from a picker/dialog we opened ourselves is not leaving the app (within
            // a sane limit, so a picker left open for ages still locks).
            if (fromPicker && elapsed < PICKER_GRACE_MS) return
            lifecycleScope.launch {
                val minutes = settings.settings.first().autoLockMinutes
                // minutes < 0 means "Mai" (never auto-lock). Background work keeps running: the
                // session keeps its keys until it ends (see SessionManager.lock).
                if (minutes >= 0 && elapsed >= minutes * 60_000L) session.lock()
            }
        }
    }
}

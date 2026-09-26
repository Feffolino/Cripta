package com.cripta.app.update

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.io.File

/**
 * Invisible step between "Installa" (update notification, Hyper Island) and the system installer.
 * Handed straight to the notification, the installer (another app, reading the APK through a
 * content uri) did not open on HyperOS; started from Cripta's own activity, as the button in
 * Impostazioni does, it does. No screen of its own, not in the recents, and it doesn't bring up
 * the vault (a task of its own).
 */
class InstallUpdateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apk = File(cacheDir, AppUpdater.APK_NAME)
        val opened = apk.exists() && runCatching { startActivity(AppUpdater.installerIntent(this, apk)) }.isSuccess
        if (!opened) {
            Toast.makeText(this, "Aggiornamento non disponibile: scaricalo di nuovo da Impostazioni", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}

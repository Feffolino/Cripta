package com.cripta.app.security

import com.cripta.app.R
import com.cripta.app.str

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** Thin wrapper around BiometricPrompt that authorizes a Keystore [Cipher]. */
object BiometricAuth {

    const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        status(activity) == BiometricManager.BIOMETRIC_SUCCESS

    /** Raw [BiometricManager] status (e.g. BIOMETRIC_ERROR_NONE_ENROLLED = no screen lock set up). */
    fun status(activity: FragmentActivity): Int =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)

    /**
     * Show the system prompt. [onError] receives a short Italian message, or null when the user
     * simply dismissed the prompt (cancel / back / "Annulla"): that is not an error to report.
     */
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String?) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher
                    if (c != null) onSuccess(c) else onError(str(R.string.bio_failed))
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    onError(messageFor(code))
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** Italian text for a BiometricPrompt error code; null for a plain user cancel. */
    fun messageFor(code: Int): String? = when (code) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED -> null
        BiometricPrompt.ERROR_LOCKOUT ->
            str(R.string.bio_lockout)
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            str(R.string.bio_lockout_permanent)
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
            str(R.string.bio_no_lock)
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_HW_NOT_PRESENT ->
            str(R.string.bio_unavailable)
        BiometricPrompt.ERROR_TIMEOUT -> str(R.string.bio_timeout)
        BiometricPrompt.ERROR_NO_SPACE -> str(R.string.bio_no_space)
        else -> str(R.string.bio_failed)
    }
}

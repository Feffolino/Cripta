package com.cripta.app.security

import androidx.annotation.StringRes
import com.cripta.app.R
import com.cripta.app.str

/**
 * How the vault is unlocked. "Sistema" is the system prompt: fingerprint/face or the phone's own
 * PIN, pattern or password.
 */
enum class UnlockMode(@StringRes private val labelRes: Int, @StringRes private val descriptionRes: Int) {
    SYSTEM(R.string.unlock_mode_system, R.string.unlock_mode_system_desc),
    SYSTEM_OR_PIN(R.string.unlock_mode_system_or_pin, R.string.unlock_mode_system_or_pin_desc),
    SYSTEM_AND_PIN(R.string.unlock_mode_system_and_pin, R.string.unlock_mode_system_and_pin_desc),
    PIN(R.string.unlock_mode_pin, R.string.unlock_mode_pin_desc);

    val label: String get() = str(labelRes)
    val description: String get() = str(descriptionRes)

    val usesSystem: Boolean get() = this != PIN
    val usesPin: Boolean get() = this != SYSTEM

    companion object {
        fun from(name: String?): UnlockMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** The app's own secret: a numeric PIN (keypad on the lock screen) or an alphanumeric password. */
enum class SecretKind(@StringRes private val nounRes: Int) {
    PIN(R.string.secret_pin),
    PASSWORD(R.string.secret_password);

    val noun: String get() = str(nounRes)

    companion object {
        fun from(name: String?): SecretKind = entries.firstOrNull { it.name == name } ?: PIN
    }
}

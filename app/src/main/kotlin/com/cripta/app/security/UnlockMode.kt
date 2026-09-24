package com.cripta.app.security

/**
 * How the vault is unlocked. "Sistema" is the system prompt: fingerprint/face or the phone's own
 * PIN, pattern or password.
 */
enum class UnlockMode(val label: String, val description: String) {
    SYSTEM(
        "Impronta o blocco del telefono",
        "Impronta, volto o il PIN/sequenza del telefono. Come finora.",
    ),
    SYSTEM_OR_PIN(
        "Impronta oppure codice dell'app",
        "Basta l'impronta; in alternativa un PIN o una password solo di Cripta (utile quando l'impronta non va).",
    ),
    SYSTEM_AND_PIN(
        "Impronta e codice dell'app",
        "Due passaggi: prima impronta o blocco del telefono, poi il PIN o la password di Cripta. Chi ha " +
            "un dito registrato sul telefono non entra senza il codice.",
    ),
    PIN(
        "Solo codice dell'app",
        "Solo il PIN o la password di Cripta: impronte e blocco del telefono non aprono il vault.",
    );

    val usesSystem: Boolean get() = this != PIN
    val usesPin: Boolean get() = this != SYSTEM

    companion object {
        fun from(name: String?): UnlockMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** The app's own secret: a numeric PIN (keypad on the lock screen) or an alphanumeric password. */
enum class SecretKind(val noun: String) {
    PIN("PIN"),
    PASSWORD("password");

    companion object {
        fun from(name: String?): SecretKind = entries.firstOrNull { it.name == name } ?: PIN
    }
}

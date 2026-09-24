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
        "Impronta oppure PIN dell'app",
        "Come sopra, e in alternativa un PIN solo di Cripta (utile quando l'impronta non va).",
    ),
    SYSTEM_AND_PIN(
        "Impronta e PIN dell'app",
        "Due passaggi: prima impronta o blocco del telefono, poi il PIN di Cripta. Chi ha un dito " +
            "registrato sul telefono non entra senza il PIN.",
    ),
    PIN(
        "Solo PIN dell'app",
        "Solo il PIN di Cripta: impronte e PIN del telefono non aprono il vault.",
    );

    val usesSystem: Boolean get() = this != PIN
    val usesPin: Boolean get() = this != SYSTEM

    companion object {
        fun from(name: String?): UnlockMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

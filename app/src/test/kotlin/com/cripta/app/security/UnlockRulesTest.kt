package com.cripta.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of the unlock that need no Keystore: what a PIN or password may be, stored modes, the PIN pauses. */
class UnlockRulesTest {
    @Test
    fun aPinIsFourToSixteenDigits() {
        assertFalse(PinCrypto.isValid("123".toCharArray(), SecretKind.PIN))
        assertTrue(PinCrypto.isValid("1234".toCharArray(), SecretKind.PIN))
        assertTrue(PinCrypto.isValid("1".repeat(16).toCharArray(), SecretKind.PIN))
        assertFalse(PinCrypto.isValid("1".repeat(17).toCharArray(), SecretKind.PIN))
        assertFalse(PinCrypto.isValid("12a4".toCharArray(), SecretKind.PIN))
        assertFalse(PinCrypto.isValid("١٢٣٤".toCharArray(), SecretKind.PIN))   // other digits than 0-9
    }

    @Test
    fun aPasswordIsFourToSixtyFourOfAnything() {
        assertFalse(PinCrypto.isValid("abc".toCharArray(), SecretKind.PASSWORD))
        assertTrue(PinCrypto.isValid("ab c!".toCharArray(), SecretKind.PASSWORD))
        assertTrue(PinCrypto.isValid("x".repeat(64).toCharArray(), SecretKind.PASSWORD))
        assertFalse(PinCrypto.isValid("x".repeat(65).toCharArray(), SecretKind.PASSWORD))
    }

    @Test
    fun typingStopsAtTheLimitAndAtNonDigitsForAPin() {
        assertTrue(PinCrypto.accepts("", SecretKind.PIN))
        assertTrue(PinCrypto.accepts("12", SecretKind.PIN))   // short is fine while typing
        assertFalse(PinCrypto.accepts("12.", SecretKind.PIN))
        assertFalse(PinCrypto.accepts("1".repeat(17), SecretKind.PIN))
        assertTrue(PinCrypto.accepts("a.b", SecretKind.PASSWORD))
        assertFalse(PinCrypto.accepts("x".repeat(65), SecretKind.PASSWORD))
        assertEquals(16, PinCrypto.maxLength(SecretKind.PIN))
        assertEquals(64, PinCrypto.maxLength(SecretKind.PASSWORD))
    }

    @Test
    fun storedNamesReadBackAndUnknownOnesFallBackToTheSystemPrompt() {
        for (m in UnlockMode.entries) assertEquals(m, UnlockMode.from(m.name))
        assertEquals(UnlockMode.SYSTEM, UnlockMode.from(null))
        assertEquals(UnlockMode.SYSTEM, UnlockMode.from("BIOMETRIC"))
        for (k in SecretKind.entries) assertEquals(k, SecretKind.from(k.name))
        assertEquals(SecretKind.PIN, SecretKind.from(null))
        assertEquals(SecretKind.PIN, SecretKind.from("pin"))
    }

    @Test
    fun whichStepsEachModeAsksFor() {
        assertEquals(listOf(true, true, true, false), UnlockMode.entries.map { it.usesSystem })
        assertEquals(listOf(false, true, true, true), UnlockMode.entries.map { it.usesPin })
        assertEquals(listOf(UnlockMode.SYSTEM, UnlockMode.SYSTEM_OR_PIN, UnlockMode.SYSTEM_AND_PIN, UnlockMode.PIN), UnlockMode.entries)
    }

    @Test
    fun thePauseAfterWrongPinsGrowsFromTheFifth() {
        assertEquals(listOf(0L, 0L, 0L, 0L, 30L, 60L, 300L, 900L, 900L, 900L), (1..10).map { KeyVault.pinWaitAfter(it) })
    }
}

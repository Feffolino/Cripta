package com.cripta.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

/** The path KeyVault takes: fresh keyset bytes, wrapped by the Keystore, rebuilt after unlock. */
class DekManagerTest {
    @Before fun setUp() {
        TinkInit.ensureInitialized()
    }

    @Test
    fun aDekRebuiltFromItsBytesReadsWhatTheFirstOneWrote() {
        val bytes = DekManager.newDekKeysetBytes()
        val ct = DekManager.dekAeadFromBytes(bytes).encrypt("x".toByteArray(), AAD)
        val plain = DekManager.dekAeadFromBytes(bytes.copyOf()).decrypt(ct, AAD)
        assertArrayEquals("x".toByteArray(), plain)
    }

    @Test
    fun everyNewDekIsADifferentKey() {
        val first = DekManager.newDekKeysetBytes()
        val second = DekManager.newDekKeysetBytes()
        assertFalse(first.contentEquals(second))
        val ct = DekManager.dekAeadFromBytes(first).encrypt("x".toByteArray(), AAD)
        try {
            DekManager.dekAeadFromBytes(second).decrypt(ct, AAD)
            throw AssertionError("another DEK must not decrypt")
        } catch (expected: GeneralSecurityException) {
        }
    }

    @Test(expected = GeneralSecurityException::class)
    fun theAssociatedDataIsPartOfTheSeal() {
        val dek = DekManager.dekAeadFromBytes(DekManager.newDekKeysetBytes())
        dek.decrypt(dek.encrypt("x".toByteArray(), AAD), "other".toByteArray())
    }

    private companion object {
        val AAD = "cripta-db-key".toByteArray()
    }
}

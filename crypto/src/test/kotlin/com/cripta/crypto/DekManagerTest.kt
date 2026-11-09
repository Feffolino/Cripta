package com.cripta.crypto

import com.google.crypto.tink.Aead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DekManagerTest {
    private lateinit var kek: KekProvider

    @Before fun setUp() {
        TinkInit.ensureInitialized()
        kek = InMemoryKekProvider.generate()
    }

    @Test
    fun createWrappedDek_thenLoad_yieldsUsableAead() {
        val wrapped = DekManager.createWrappedDek(kek)
        val aead: Aead = DekManager.loadDekAead(wrapped, kek)
        assertNotNull(aead)
        val msg = "hello".toByteArray()
        val ct = aead.encrypt(msg, null)
        assertArrayEquals(msg, aead.decrypt(ct, null))
    }

    @Test
    fun wrappingAndLoadingWipeTheirTemporaryKeysetBytes() {
        lateinit var wrappingInput: ByteArray
        lateinit var unwrappedOutput: ByteArray
        val recordingKek = object : KekProvider {
            override fun wrap(plaintext: ByteArray): ByteArray {
                assertTrue(plaintext.any { it != 0.toByte() })
                wrappingInput = plaintext
                return kek.wrap(plaintext)
            }

            override fun unwrap(ciphertext: ByteArray): ByteArray =
                kek.unwrap(ciphertext).also { unwrappedOutput = it }
        }
        val wrapped = DekManager.createWrappedDek(recordingKek)
        assertArrayEquals(ByteArray(wrappingInput.size), wrappingInput)
        val aead = DekManager.loadDekAead(wrapped, recordingKek)
        assertArrayEquals(ByteArray(unwrappedOutput.size), unwrappedOutput)
        val message = "payload".toByteArray()
        assertArrayEquals(message, aead.decrypt(aead.encrypt(message, null), null))
    }

    @Test
    fun sameWrappedDek_decryptsCiphertextFromAnotherLoad() {
        val wrapped = DekManager.createWrappedDek(kek)
        val ct = DekManager.loadDekAead(wrapped, kek).encrypt("x".toByteArray(), null)
        val plain = DekManager.loadDekAead(wrapped, kek).decrypt(ct, null)
        assertArrayEquals("x".toByteArray(), plain)
    }
}

package com.cripta.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InMemoryKekProviderTest {
    @Test
    fun wrapThenUnwrap_returnsOriginalPlaintext() {
        val kek = InMemoryKekProvider.generate()
        val secret = "master-key-material".toByteArray()
        val wrapped = kek.wrap(secret)
        assertFalse(wrapped.contentEquals(secret))
        val unwrapped = kek.unwrap(wrapped)
        assertArrayEquals(secret, unwrapped)
    }

    @Test(expected = Exception::class)
    fun unwrap_withWrongKey_fails() {
        val wrapped = InMemoryKekProvider.generate().wrap("x".toByteArray())
        InMemoryKekProvider.generate().unwrap(wrapped)
    }
}

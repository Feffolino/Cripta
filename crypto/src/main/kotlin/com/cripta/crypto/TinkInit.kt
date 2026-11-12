package com.cripta.crypto

import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig

/** Registers Tink primitives exactly once per process. */
object TinkInit {
    private val registration = Once {
        AeadConfig.register()
        StreamingAeadConfig.register()
    }

    fun ensureInitialized() = registration.run()
}

/**
 * Runs [action] until it has succeeded once. A caller arriving while it runs waits for it, and one
 * that failed leaves it to be tried again: the flag was set before the registration, so a second
 * thread could go on with Tink not registered yet, and a failed registration was never retried.
 */
internal class Once(private val action: () -> Unit) {
    @Volatile private var done = false

    fun run() {
        if (done) return
        synchronized(this) {
            if (done) return
            action()
            done = true
        }
    }
}

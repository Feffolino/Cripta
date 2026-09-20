package com.cripta.crypto

import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.util.concurrent.atomic.AtomicBoolean

/** Registers Tink primitives exactly once per process. */
object TinkInit {
    private val done = AtomicBoolean(false)

    fun ensureInitialized() {
        if (done.compareAndSet(false, true)) {
            AeadConfig.register()
            StreamingAeadConfig.register()
        }
    }
}

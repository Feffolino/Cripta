package com.cripta.app.media.face

import ai.onnxruntime.OrtEnvironment

/**
 * ONNX Runtime's Android library (1.31; 1.20 had none) sends usage telemetry to Microsoft through its 1DS
 * SDK (mobile.events.data.microsoft.com), on by default: device make, model and OS, the app's id,
 * version and language, model hashes, run times and errors. Nothing of the vault leaves the phone.
 *
 * Two locks keep it off: the manifest removes the library's start-up provider, so its HTTP client is
 * never created and the runtime has nothing to upload through; and here the runtime's own opt-out
 * is set before it is loaded, which makes it skip the telemetry set-up for the whole process.
 */
internal object OrtTelemetry {
    /** Read once by the runtime when it loads: a true value turns telemetry off for the process. */
    const val DISABLE_VAR = "ORT_DISABLE_TELEMETRY"

    /** The shared ONNX environment, created with telemetry off. */
    @Synchronized
    fun environment(): OrtEnvironment {
        // Before the first touch of OrtEnvironment, whose class loads the native runtime. On the
        // desktop JVM of the unit tests android.system.Os is a stub, and that runtime has no telemetry.
        runCatching { android.system.Os.setenv(DISABLE_VAR, "1", true) }
        return OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }
    }
}

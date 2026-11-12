package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * ONNX Runtime's Android library ships a content provider that starts Microsoft's usage telemetry
 * when the app starts: the merged manifest must not have it.
 */
class OrtTelemetryTest {
    private val manifest: String by lazy {
        val props = Properties()
        javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties")!!.use(props::load)
        File(props.getProperty("android_merged_manifest")).readText()
    }

    @Test
    fun mergedManifestHasNoTelemetryProvider() {
        assertTrue("merged manifest not found", manifest.contains("<application"))
        assertFalse("ai.onnxruntime.TelemetryInitializer is still in the merged manifest",
            manifest.contains("ai.onnxruntime.TelemetryInitializer"))
    }

    @Test
    fun theEnvironmentVariableIsTheOneOnnxRuntimeReads() {
        assertEquals("ORT_DISABLE_TELEMETRY", OrtTelemetry.DISABLE_VAR)
    }
}

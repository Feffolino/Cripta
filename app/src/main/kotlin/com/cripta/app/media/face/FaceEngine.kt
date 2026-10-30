package com.cripta.app.media.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.sqrt

/** Why a detected face is not used for recognition; shown in the diagnostics. */
enum class FaceReject(val label: String) {
    LOW_SCORE("incerti"), SMALL("troppo piccoli"), PROFILE("di profilo"), BLURRY("sfocati");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name }
    }
}

/**
 * Faces in a picture: YuNet finds them on a copy of at most [DETECT_SIDE] px, each usable one is
 * aligned from the full picture and turned into a normalised 128-value SFace embedding.
 * The ONNX sessions are thread-safe: one engine serves every worker.
 */
class FaceEngine(detector: ByteArray, recognizer: ByteArray, private val threads: Int = 2) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private fun options() = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setSessionLogLevel(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val det = env.createSession(detector, options())
    private val rec = env.createSession(recognizer, options())

    /** A face found in a picture: [embedding] and [aligned] are set when it passed the quality checks. */
    class Face(val det: Detection, val reject: FaceReject?, val embedding: FloatArray?, val aligned: FaceImage?) {
        val yaw get() = FaceAligner.yaw(det.landmarks)
        /** How good an example of its person the face is: confident, large, frontal. */
        val quality get() = det.score * minOf(1f, det.w / FaceAligner.SIZE) * (1f - minOf(yaw, 0.9f))
    }

    fun analyze(img: FaceImage): List<Face> {
        val small = img.fit(DETECT_SIDE)
        val pw = YuNet.padded(small.width); val ph = YuNet.padded(small.height)
        val outputs = OnnxTensor.createTensor(env, FloatBuffer.wrap(YuNet.input(small)), longArrayOf(1, 3, ph.toLong(), pw.toLong())).use { t ->
            det.run(mapOf("input" to t)).use { r -> r.associate { it.key to floats(it.value as OnnxTensor) } }
        }
        val kx = img.width.toFloat() / small.width; val ky = img.height.toFloat() / small.height
        return YuNet.decode(outputs, pw, ph, MIN_DETECT).map { d0 ->
            val d = d0.scaled(kx, ky)
            val reject = when {
                d.score < MIN_SCORE -> FaceReject.LOW_SCORE
                d.w < MIN_WIDTH -> FaceReject.SMALL
                FaceAligner.yaw(d.landmarks) > MAX_YAW -> FaceReject.PROFILE
                else -> null
            }
            if (reject != null) return@map Face(d, reject, null, null)
            val aligned = FaceAligner.align(img, d.landmarks)
            if (FaceAligner.sharpness(aligned) < MIN_SHARPNESS) Face(d, FaceReject.BLURRY, null, null)
            else Face(d, null, embed(aligned), aligned)
        }
    }

    /** The normalised embedding of an aligned face. */
    fun embed(aligned: FaceImage): FloatArray {
        val e = OnnxTensor.createTensor(env, FloatBuffer.wrap(FaceAligner.input(aligned)), longArrayOf(1, 3, 112, 112)).use { t ->
            rec.run(mapOf("data" to t)).use { r -> floats(r.get(0) as OnnxTensor) }
        }
        val n = sqrt(e.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        for (i in e.indices) e[i] /= n
        return e
    }

    private fun floats(t: OnnxTensor): FloatArray = t.floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }

    override fun close() { det.close(); rec.close() }

    companion object {
        /** Detection runs on a copy this large: faces down to ~10 px of it are found. */
        const val DETECT_SIDE = 640
        /** Below this the detector reports nothing; between it and [MIN_SCORE] the face is counted as uncertain. */
        const val MIN_DETECT = 0.6f
        const val MIN_SCORE = 0.8f
        /** Narrower faces give unreliable embeddings (measured: false matches mostly came from them). */
        const val MIN_WIDTH = 36f
        const val MAX_YAW = 0.6f
        const val MIN_SHARPNESS = 20.0
    }
}

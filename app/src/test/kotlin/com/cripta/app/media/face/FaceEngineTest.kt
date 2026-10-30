package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The Kotlin pipeline against OpenCV's own FaceDetectorYN / FaceRecognizerSF on the same photos. */
class FaceEngineTest {
    @Test
    fun matchesOpenCvDetectionAndEmbedding() {
        val rows = TestFaces.golden()
        val byImage = rows.groupBy { it[0] }
        for ((name, faces) in byImage) {
            val found = TestFaces.engine.analyze(TestFaces.image(name))
            for (g in faces) {
                val d = g[1].toFloat()
                val v = g.subList(2, 17).map { it.toFloat() / d }
                val mine = found.minByOrNull { abs(it.det.x - v[0]) + abs(it.det.y - v[1]) }
                assertNotNull("$name: face at ${v[0]},${v[1]} not found", mine)
                mine!!
                val tol = maxOf(3f, v[2] * 0.06f)
                for (k in 0 until 4) assertEquals("$name box $k", v[k], listOf(mine.det.x, mine.det.y, mine.det.w, mine.det.h)[k], tol)
                for (k in 0 until 10) assertEquals("$name landmark $k", v[4 + k], mine.det.landmarks[k], tol)
                assertEquals("$name score", g[16].toFloat(), mine.det.score, 0.03f)
                if (mine.embedding != null) {
                    val ref = FloatArray(128) { g[17 + it].toFloat() }
                    val c = TestFaces.cos(ref, mine.embedding!!)
                    assertTrue("$name embedding differs from OpenCV's: cos $c", c > 0.95f)
                }
            }
            assertEquals("$name: same number of faces as OpenCV", faces.size, found.size)
        }
    }

    @Test
    fun alignmentPutsTheEyesOnTheTemplate() {
        // A pure scale + shift: the similarity must be exact.
        val src = floatArrayOf(38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f)
            .mapIndexed { i, v -> v * 2 + if (i % 2 == 0) 10 else 30 }.toFloatArray()
        val (a, b, tx, ty) = FaceAligner.similarity(src)
        assertEquals(0.5, a, 1e-6); assertEquals(0.0, b, 1e-6); assertEquals(-5.0, tx, 1e-4); assertEquals(-15.0, ty, 1e-4)
    }

    @Test
    fun overlappingDetectionsKeepTheBest() {
        fun d(x: Float, s: Float) = Detection(x, 0f, 100f, 100f, FloatArray(10), s)
        val kept = YuNet.suppress(listOf(d(0f, 0.8f), d(10f, 0.9f), d(300f, 0.7f)), 0.3f)
        assertEquals(listOf(0.9f, 0.7f), kept.map { it.score })
    }
}

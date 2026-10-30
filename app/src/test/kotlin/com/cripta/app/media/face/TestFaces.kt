package com.cripta.app.media.face

import java.io.File

/**
 * Test data: the real models from the app's assets and public-domain photos (US government works
 * from Wikimedia Commons) in `src/test/resources/faces`, with hand-checked identities in labels.csv.
 */
object TestFaces {
    private val dir = File(javaClass.classLoader!!.getResource("faces/labels.csv")!!.toURI()).parentFile!!

    val engine: FaceEngine by lazy {
        val assets = listOf(File("src/main/assets/faces"), File("app/src/main/assets/faces")).first { it.isDirectory }
        FaceEngine(File(assets, "yunet.onnx").readBytes(), File(assets, "sface.onnx").readBytes())
    }

    fun names(): List<String> = dir.listFiles { f -> f.extension == "jpg" }!!.map { it.nameWithoutExtension }.sorted()

    fun image(name: String): FaceImage = load(File(dir, "$name.jpg"))

    /** Decoded with the JDK's ImageIO, by reflection: the unit tests compile against android.jar, which lacks it. */
    fun load(f: File): FaceImage {
        val bi = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, f)
        val c = bi.javaClass
        val w = c.getMethod("getWidth").invoke(bi) as Int
        val h = c.getMethod("getHeight").invoke(bi) as Int
        val px = c.getMethod("getRGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java, IntArray::class.java, Int::class.java, Int::class.java)
            .invoke(bi, 0, 0, w, h, null, 0, w) as IntArray
        return FaceImage(w, h, px)
    }

    /** A labelled face: its nose at ([x], [y]) in [image]. */
    class Label(val image: String, val x: Float, val y: Float, val who: String)

    val labels: List<Label> by lazy {
        File(dir, "labels.csv").readLines().filter { it.isNotBlank() }.map { l ->
            val p = l.split(','); Label(p[0], p[1].toFloat(), p[2].toFloat(), p[3])
        }
    }

    /** Who [face] of [image] is, when labelled (its nose within a few pixels of the label's). */
    fun who(image: String, face: Detection): String? = labels.filter { it.image == image }
        .minByOrNull { (it.x - face.landmarks[4]).let { d -> d * d } + (it.y - face.landmarks[5]).let { d -> d * d } }
        ?.takeIf { kotlin.math.hypot(it.x - face.landmarks[4], it.y - face.landmarks[5]) < maxOf(6f, face.w / 6) }?.who

    fun golden(): List<List<String>> = File(dir, "opencv_golden.csv").readLines().filter { it.isNotBlank() }.map { it.split(',') }

    fun cos(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }
}

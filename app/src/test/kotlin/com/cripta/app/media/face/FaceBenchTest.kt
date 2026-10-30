package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * The face bench (resources/faces/bench, see its README): public-domain photos of many people and
 * frames of public-domain videos, each a square around one labelled face. Photos also come
 * "video-like": shrunk to a 40-75 px face in a 640 x 360 frame, softened and noisy, the way most
 * faces of a phone's videos are. The whole pipeline runs on it as the app does; a report with
 * every number goes to build/face-bench.txt.
 */
class FaceBenchTest {
    private class Item(val file: String, val who: String, val media: String, val video: Boolean, val time: Long)
    private class Seen(val item: Item, val variant: String, val face: FaceEngine.Face)

    private val dir = File(javaClass.classLoader!!.getResource("faces/bench/bench.csv")!!.toURI()).parentFile!!
    private val report = StringBuilder()

    private val items by lazy {
        File(dir, "bench.csv").readLines().drop(1).filter { it.isNotBlank() }.map { l ->
            val p = l.split(','); Item(p[0], p[1], p[3], p[2] == "video", p[4].toLong() * 1000)
        }
    }

    /** A video-like copy: the face about [faceWidth] px in a 640 x 360 frame, softened, with noise. */
    private fun degrade(img: FaceImage, faceWidth: Int, rnd: Random): FaceImage {
        val side = maxOf(8, img.width * faceWidth / (img.width / 3))
        val small = img.resized(side, side)
        val frame = IntArray(640 * 360) { 0xFF5A5A5A.toInt() }
        val x0 = rnd.nextInt(640 - side); val y0 = rnd.nextInt(360 - side)
        for (y in 0 until side) for (x in 0 until side) {
            // 3 x 3 box blur, then noise: a soft, grainy video face.
            var r = 0; var g = 0; var b = 0; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, side - 1); val yy = (y + dy).coerceIn(0, side - 1)
                val c = small.argb[yy * side + xx]; r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF; n++
            }
            val e = (rnd.nextGaussian() * 5).toInt()
            frame[(y0 + y) * 640 + x0 + x] = (0xFF shl 24) or ((r / n + e).coerceIn(0, 255) shl 16) or ((g / n + e).coerceIn(0, 255) shl 8) or (b / n + e).coerceIn(0, 255)
        }
        return FaceImage(640, 360, frame)
    }

    /** The labelled face: the widest one found, used only when the app would use it. */
    private fun usable(img: FaceImage) = TestFaces.engine.analyze(img).maxByOrNull { it.det.w }?.takeIf { it.reject == null }

    private val seen: List<Seen> by lazy {
        val rnd = Random(7)
        items.flatMap { it ->
            val img = TestFaces.load(File(dir, it.file + ".jpg"))
            val variants = listOf("clean" to img) + if (it.video) emptyList() else listOf(40, 55, 75).map { w -> "w$w" to degrade(img, w, rnd) }
            variants.mapNotNull { (v, im) -> usable(im)?.let { f -> Seen(it, v, f) } }
        }
    }

    /** Each source photo once, as one of its variants at random; each video once, all its frames. */
    private fun media(seed: Long): List<List<Seen>> {
        val rnd = Random(seed)
        return seen.groupBy { it.item.media }.values.map { l -> if (l.first().item.video) l else listOf(l[rnd.nextInt(l.size)]) }
            .shuffled(rnd)
    }

    private fun tracksOf(m: List<Seen>, firstId: Long): List<Pair<TrackInfo, String>> {
        val sightings = m.mapIndexed { i, s -> Sighting(i, s.item.time, s.face.embedding!!, s.face.quality, s, s.face.det.w) }
        return FaceTracker.build(sightings).mapIndexed { k, d ->
            TrackInfo(firstId + k, m.first().item.media, d.frames, d.embeddings, d.quality, d.width) to (d.best.first().payload as Seen).item.who
        }
    }

    /** The app's clustering: media in groups of [group], placed best first, then unnamed people merged. */
    private fun cluster(order: List<List<Seen>>, group: Int): Map<Long, MutableList<String>> {
        val index = PeopleIndex()
        val who = HashMap<Long, MutableList<String>>()
        var nextTrack = 1L; var nextPerson = 1L
        for (chunk in order.chunked(group)) {
            val touched = HashSet<Long>()
            val tracks = chunk.flatMap { m -> tracksOf(m, nextTrack).also { nextTrack += it.size + 1 } }.sortedByDescending { it.first.quality }
            for ((t, w) in tracks) {
                val p = index.place(t)
                val pid = if (p.personId != null && !p.suggested) p.personId else nextPerson++.also { index.person(it) }
                index.add(pid, t); touched += pid
                who.getOrPut(pid) { ArrayList() } += w
            }
            for ((keep, drop) in index.merges(touched)) who.remove(drop)?.let { who.getValue(keep) += it }
        }
        return who
    }

    @Test
    fun benchIsClusteredWithoutMixingPeople() = try { check() } finally {
        File("build").mkdirs(); File("build/face-bench.txt").writeText(report.toString())
    }

    private fun check() {
        report.appendLine("bench: ${items.size} pictures of ${items.map { it.who }.toSet().size} people, ${seen.size} faces used " +
            seen.groupingBy { it.variant }.eachCount())
        // Similarities of faces of different media, by the smaller face's width.
        val bands = listOf(0f, 50f, 70f, 100f, 1e9f)
        for (b in 0 until 4) {
            val same = ArrayList<Float>(); val diff = ArrayList<Float>()
            for (i in seen.indices) for (j in i + 1 until seen.size) {
                val x = seen[i]; val y = seen[j]
                if (x.item.media == y.item.media) continue
                if (minOf(x.face.det.w, y.face.det.w).let { it < bands[b] || it >= bands[b + 1] }) continue
                (if (x.item.who == y.item.who) same else diff) += similarity(x.face.embedding!!, y.face.embedding!!)
            }
            same.sort(); diff.sort()
            if (same.isNotEmpty() && diff.isNotEmpty()) report.appendLine("width %.0f-%.0f: same person p10 %.3f median %.3f (%d), strangers max %.3f, over %.2f: %d of %d".format(
                bands[b], minOf(bands[b + 1], 999f), same[same.size / 10], same[same.size / 2], same.size, diff.last(),
                FaceThresholds.auto(bands[b]), diff.count { it >= FaceThresholds.auto(bands[b]) }, diff.size))
        }
        var pieces = 0.0
        for (seed in 1L..4L) {
            val people = cluster(media(seed), group = 12)
            val mixed = people.values.filter { it.toSet().size > 1 }
            val byWho = people.values.flatten().toSet().associateWith { w -> people.values.count { w in it } }
            pieces += byWho.values.average() / 4
            report.appendLine("order $seed: ${people.size} people, mixed ${mixed.map { it.groupingBy { w -> w }.eachCount() }}, " +
                "pieces per person %.2f".format(byWho.values.average()))
            assertEquals("order $seed: people mixing two identities", 0, mixed.size)
        }
        // Splits come from faces too small or blurred to be sure: most people stay whole.
        assertTrue("too many splits: %.2f pieces per person".format(pieces), pieces <= 1.35)
    }

    /** One video showing two people in turn: never one track for both (no drifting by single faces). */
    @Test
    fun aVideoWithTwoPeopleGivesTwoPureTracks() {
        val byWho = seen.filter { it.variant == "clean" }.groupBy { it.item.who }.values.filter { it.size >= 3 }
        var checked = 0
        for (k in 0 until byWho.size - 1 step 2) {
            val mixed = (byWho[k].take(3) + byWho[k + 1].take(3)).shuffled(Random(k.toLong()))
            val tracks = FaceTracker.build(mixed.mapIndexed { i, s -> Sighting(i, 0, s.face.embedding!!, s.face.quality, s, s.face.det.w) })
            for (t in tracks) assertEquals("one track, one person", 1, t.sightings.map { (it.payload as Seen).item.who }.toSet().size)
            checked++
        }
        assertTrue(checked > 3)
    }
}

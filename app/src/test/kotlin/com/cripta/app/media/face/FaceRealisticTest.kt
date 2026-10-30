package com.cripta.app.media.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The whole pipeline on 30 real photos (portraits, candids, group shots) with hand-labelled
 * identities, as the app runs it: one media per photo, analysed in groups, people placed as they
 * come. A report with every number goes to build/face-report.txt.
 */
class FaceRealisticTest {
    private class Media(val id: String, val faces: List<FaceEngine.Face>)

    private val report = StringBuilder()

    private fun analyse(name: String, img: FaceImage) = Media(name, TestFaces.engine.analyze(img))

    /** Photos plus "video frames": the portraits shrunk to a third and blown up again (soft, low detail). */
    private val media: List<Media> by lazy {
        TestFaces.names().map { analyse(it, TestFaces.image(it)) } +
            TestFaces.names().filter { it.endsWith("_portrait") || it.endsWith("_2013") || it.endsWith("_vp") }.map { n ->
                val img = TestFaces.image(n)
                analyse("$n~soft", img.resized(img.width / 3, img.height / 3).resized(img.width, img.height))
            }
    }

    private fun label(m: Media, f: FaceEngine.Face) = TestFaces.who(m.id.substringBefore('~'), f.det)

    /** Runs the clustering over [order] in groups of [group], as the background scan does. */
    private fun cluster(order: List<Media>, group: Int): Pair<Map<Long, Placement>, Map<Long, Pair<Media, FaceEngine.Face>>> {
        val index = PeopleIndex()
        val placed = HashMap<Long, Placement>()
        val owner = HashMap<Long, Pair<Media, FaceEngine.Face>>()
        val personOf = HashMap<Long, Long>()
        var nextTrack = 1L; var nextPerson = 1L
        for (chunk in order.chunked(group)) {
            val touched = HashSet<Long>()
            val tracks = chunk.flatMap { m ->
                val sightings = m.faces.filter { it.reject == null }.map { Sighting(0, 0, it.embedding!!, it.quality, it) }
                FaceTracker.build(sightings).map { d ->
                    val t = TrackInfo(nextTrack++, m.id, d.frames, d.embeddings, d.quality)
                    owner[t.id] = m to (d.best.first().payload as FaceEngine.Face)
                    t
                }
            }.sortedByDescending { it.quality }
            for (t in tracks) {
                val p = index.place(t)
                val pid = if (p.personId != null && !p.suggested) p.personId else nextPerson++.also { index.person(it) }
                if (p.suggested) placed[t.id] = p
                personOf[t.id] = pid
                index.add(pid, t)
                touched += pid
            }
            for ((keep, drop) in index.merges(touched)) personOf.replaceAll { _, v -> if (v == drop) keep else v }
        }
        return personOf.mapValues { (t, pid) -> placed[t] ?: Placement(pid, false, 1f) } to owner
    }

    @Test
    fun everyoneEndsInOnePersonAndNoPersonMixesTwo() = try { check() } finally { File("build").mkdirs(); File("build/face-report.txt").writeText(report.toString()) }

    private fun check() {
        // What was rejected, and why.
        val all = media.flatMap { m -> m.faces.map { m to it } }
        report.appendLine("faces ${all.size}: " + all.groupingBy { it.second.reject?.name ?: "OK" }.eachCount())
        for ((m, f) in all) label(m, f)?.let { who ->
            if (f.reject != null) report.appendLine("labelled but rejected: $who in ${m.id} (${f.reject}, w ${f.det.w.toInt()}, yaw %.2f)".format(f.yaw))
        }
        // Pairwise similarities of the accepted labelled faces.
        val ok = all.filter { it.second.reject == null }.mapNotNull { (m, f) -> label(m, f)?.let { Triple(m, f, it) } }
        var minSame = 1f; var maxDiff = -1f
        for (i in ok.indices) for (j in i + 1 until ok.size) {
            val s = similarity(ok[i].second.embedding!!, ok[j].second.embedding!!)
            if (ok[i].third == ok[j].third) minSame = minOf(minSame, s) else maxDiff = maxOf(maxDiff, s)
        }
        report.appendLine("accepted labelled ${ok.size}: same person min %.3f, different people max %.3f".format(minSame, maxDiff))

        for ((seed, group) in listOf(1 to 12, 2 to 5, 3 to 1)) {
            val order = media.shuffled(java.util.Random(seed.toLong()))
            val (placed, owner) = cluster(order, group)
            // Labelled faces per person, joined (not merely suggested).
            val joined = placed.filter { !it.value.suggested }
            val byPerson = joined.entries.groupBy({ it.value.personId!! }, { label(owner.getValue(it.key).first, owner.getValue(it.key).second) })
            report.appendLine("order $seed, groups of $group: ${byPerson.size} people")
            for ((pid, who) in byPerson) {
                val named = who.filterNotNull().toSet()
                assertTrue("order $seed: person $pid mixes $named", named.size <= 1)
                if (named.isNotEmpty() && who.contains(null)) report.appendLine("  person $pid ($named) also has ${who.count { it == null }} unlabelled faces")
            }
            for (who in ok.map { it.third }.toSet()) {
                val homes = byPerson.filter { who in it.value }.keys
                val suggested = placed.filter { it.value.suggested && label(owner.getValue(it.key).first, owner.getValue(it.key).second) == who }
                report.appendLine("  $who: in ${homes.size} people, ${suggested.size} suggestions" +
                    suggested.values.joinToString("") { " (%.2f to %s)".format(it.similarity, if (it.personId in homes) "right" else "WRONG") })
                assertEquals("order $seed: $who split", 1, homes.size)
                assertTrue("order $seed: $who suggested elsewhere", suggested.values.all { it.personId in homes })
            }
        }
    }
}

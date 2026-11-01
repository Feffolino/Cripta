package com.cripta.app.media.face

import kotlin.math.sqrt

/** Cosine similarity of two normalised embeddings. */
fun similarity(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}

private fun normalised(v: FloatArray): FloatArray {
    val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
    return FloatArray(v.size) { v[it] / n }
}

/** A face seen in one frame of a media (frame 0 for a photo), already accepted by the quality checks. */
class Sighting(val frame: Int, val timeMs: Long, val embedding: FloatArray, val quality: Float, val payload: Any? = null)

/** One person in one media: the frames they show in ([frames] bit n = frame n) and their best faces. */
class TrackDraft(val sightings: List<Sighting>) {
    val frames: Long = sightings.fold(0L) { m, s -> m or (1L shl s.frame.coerceIn(0, 63)) }
    /** Best first: the face shown and the embeddings kept. */
    val best: List<Sighting> = sightings.sortedByDescending { it.quality }
    /**
     * The mean of all its faces (steadier than any single frame) and its best ones; a single face
     * is kept as it is.
     */
    val embeddings: List<FloatArray>
        get() = if (sightings.size < 2) listOf(best.first().embedding)
            else listOf(mean(sightings.map { it.embedding })) + best.take(FaceTracker.KEPT - 1).map { it.embedding }
    /** Every moment it shows in (ms). */
    val times: List<Long> get() = sightings.map { it.timeMs }.distinct().sorted()
    val quality get() = best.first().quality

    private fun mean(list: List<FloatArray>): FloatArray {
        val m = FloatArray(list.first().size)
        for (e in list) for (i in m.indices) m[i] += e[i]
        return normalised(m)
    }
}

/**
 * Groups the faces of one media into tracks, one per person, so that the details of a media list
 * each person once with every moment they show in: never two faces of the same frame, and a face
 * joins a track by the mean of its faces, so a track never drifts from one person to another.
 * A second pass ([groups]) joins the tracks of one person that a change of angle or light split.
 */
object FaceTracker {
    /** Embeddings a track keeps (its best faces). */
    const val KEPT = 3
    /** Two faces of one media at least this alike are the same person (same light, same day). */
    const val SAME = 0.45f
    /**
     * Two tracks of one media that never share a frame are one person when their means are this
     * alike: a mean of several faces is steadier than a single face, so the bar is lower than [SAME].
     */
    const val JOIN = 0.40f

    fun build(sightings: List<Sighting>): List<TrackDraft> {
        val tracks = ArrayList<MutableList<Sighting>>()
        val sums = ArrayList<FloatArray>()
        for (s in sightings.sortedByDescending { it.quality }) {
            var home = -1; var best = SAME
            for ((i, t) in tracks.withIndex()) {
                if (t.any { it.frame == s.frame }) continue
                val sim = similarity(normalised(sums[i]), s.embedding)
                if (sim >= best) { best = sim; home = i }
            }
            if (home < 0) { tracks += mutableListOf(s); sums += s.embedding.copyOf() }
            else { tracks[home] += s; val m = sums[home]; for (i in m.indices) m[i] += s.embedding[i] }
        }
        val drafts = tracks.map { TrackDraft(it) }
        return groups(drafts.map { it.frames }, drafts.map { it.embeddings.first() }, drafts.map { it.sightings.size })
            .map { g -> if (g.size == 1) drafts[g[0]] else TrackDraft(g.flatMap { drafts[it].sightings }) }
    }

    /**
     * Which tracks of one media are the same person: [frames] (bit n = frame n), [means] (each
     * track's mean embedding) and [counts] (its faces, the weight of its mean). The most alike pair
     * at least [JOIN] that shares no frame is joined first, then the next, on the joined means.
     * Returns the groups as indices, each group in input order.
     */
    fun groups(frames: List<Long>, means: List<FloatArray>, counts: List<Int>): List<List<Int>> {
        val members = means.indices.map { mutableListOf(it) }.toMutableList()
        val sums = means.mapIndexed { i, m -> FloatArray(m.size) { m[it] * counts[i].coerceAtLeast(1) } }.toMutableList()
        val masks = frames.toMutableList()
        while (members.size > 1) {
            val unit = sums.map { normalised(it) }
            var a = -1; var b = -1; var best = JOIN
            for (i in members.indices) for (j in i + 1 until members.size) {
                if (masks[i] and masks[j] != 0L) continue
                val sim = similarity(unit[i], unit[j])
                if (sim >= best) { best = sim; a = i; b = j }
            }
            if (a < 0) break
            members[a].addAll(members[b]); members[a].sort()
            masks[a] = masks[a] or masks[b]
            for (k in sums[a].indices) sums[a][k] += sums[b][k]
            members.removeAt(b); sums.removeAt(b); masks.removeAt(b)
        }
        return members
    }

    /** One stored track's part in a join: its embeddings (mean first when [count] > 1), faces and best quality. */
    class Part(val embeddings: List<FloatArray>, val count: Int, val quality: Float)

    /**
     * The embeddings of tracks joined into one: the mean of all their faces (each mean weighted by
     * its faces), then the best faces kept, from the best track first.
     */
    fun combine(parts: List<Part>): List<FloatArray> {
        if (parts.size == 1) return parts[0].embeddings
        val sum = FloatArray(parts.first().embeddings.first().size)
        for (p in parts) { val m = p.embeddings.first(); val w = p.count.coerceAtLeast(1); for (i in sum.indices) sum[i] += m[i] * w }
        val best = parts.sortedByDescending { it.quality }.flatMap { if (it.count > 1) it.embeddings.drop(1) else it.embeddings.take(1) }
        return listOf(normalised(sum)) + best.take(KEPT - 1)
    }
}

/** A stored track: its media, frames and embeddings (to recognise it when the media is analysed again). */
class TrackInfo(val id: Long, val fileId: String, val frames: Long, val embeddings: List<FloatArray>, val quality: Float)

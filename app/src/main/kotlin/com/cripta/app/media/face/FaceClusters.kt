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
 */
object FaceTracker {
    /** Embeddings a track keeps (its best faces). */
    const val KEPT = 3
    /** Two faces of one media at least this alike are the same person (same light, same day). */
    const val SAME = 0.45f

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
        return tracks.map { TrackDraft(it) }
    }
}

/** A stored track: its media, frames and embeddings (to recognise it when the media is analysed again). */
class TrackInfo(val id: Long, val fileId: String, val frames: Long, val embeddings: List<FloatArray>, val quality: Float)

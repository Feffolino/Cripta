package com.cripta.app.media.face

/** Cosine similarity of two normalised embeddings. */
fun similarity(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}

/** Similarity thresholds, calibrated on real photos (see the design doc). */
object FaceThresholds {
    /** At or above: the same person, joined automatically. */
    const val AUTO = 0.45f
    /** From here to [AUTO]: "Forse è X?". */
    const val SUGGEST = 0.36f
}

/** A face seen in one frame of a media (frame 0 for a photo), already accepted by the quality checks. */
class Sighting(val frame: Int, val timeMs: Long, val embedding: FloatArray, val quality: Float, val payload: Any? = null)

/** One person in one media: the frames they show in ([frames] bit n = frame n) and their best faces. */
class TrackDraft(val sightings: List<Sighting>) {
    val frames: Long = sightings.fold(0L) { m, s -> m or (1L shl s.frame.coerceIn(0, 63)) }
    /** Best first: the face shown and the embeddings kept. */
    val best: List<Sighting> = sightings.sortedByDescending { it.quality }
    val embeddings: List<FloatArray> get() = best.take(FaceTracker.KEPT).map { it.embedding }
    val quality get() = best.first().quality
}

/** Groups the faces of one media into tracks: alike faces, never two of the same frame. */
object FaceTracker {
    /** Embeddings a track keeps (its best faces). */
    const val KEPT = 3

    fun build(sightings: List<Sighting>): List<TrackDraft> {
        val tracks = ArrayList<MutableList<Sighting>>()
        for (s in sightings.sortedByDescending { it.quality }) {
            val home = tracks.filter { t -> t.none { it.frame == s.frame } }
                .map { t -> t to t.maxOf { similarity(it.embedding, s.embedding) } }
                .filter { it.second >= FaceThresholds.AUTO }
                .maxByOrNull { it.second }?.first
            if (home != null) home += s else tracks += mutableListOf(s)
        }
        return tracks.map { TrackDraft(it) }
    }
}

/** A stored track as the clustering sees it. */
class TrackInfo(val id: Long, val fileId: String, val frames: Long, val embeddings: List<FloatArray>, val quality: Float)

/** What to do with a track: join [personId], suggest it to [personId], or start a new person (null). */
class Placement(val personId: Long?, val suggested: Boolean, val similarity: Float)

/** Why a track is (not) with a person, for "Confronta". */
class Verdict(val personId: Long, val similarity: Float, val reason: String)

/**
 * The people known so far, each by its best faces (up to [MAX_EXEMPLARS]); places new tracks among
 * them. Pure logic: the repository loads it from the database and stores its decisions.
 */
class PeopleIndex {
    class Person(val id: Long, var named: Boolean, var ignored: Boolean) {
        val exemplars = ArrayList<Pair<TrackInfo, FloatArray>>()
        /** Frames per media of every member track: two faces of one frame are never the same person. */
        val frames = HashMap<String, Long>()
    }

    val people = LinkedHashMap<Long, Person>()
    /** (track, person) pairs the user said no to. */
    val rejected = HashSet<Pair<Long, Long>>()

    fun person(id: Long, named: Boolean = false, ignored: Boolean = false) = people.getOrPut(id) { Person(id, named, ignored) }

    fun add(personId: Long, t: TrackInfo) {
        val p = people.getValue(personId)
        p.frames.merge(t.fileId, t.frames) { a, b -> a or b }
        for (e in t.embeddings) {
            if (p.exemplars.size < MAX_EXEMPLARS) p.exemplars += t to e
            else p.exemplars.withIndex().minByOrNull { it.value.first.quality }?.takeIf { it.value.first.quality < t.quality }
                ?.let { p.exemplars[it.index] = t to e }
        }
    }

    fun remove(personId: Long) { people.remove(personId) }

    /** How much [t] looks like [p]: the mean of the two best face pairs (one lucky pair is not enough). */
    fun score(t: TrackInfo, p: Person): Float {
        var first = -1f; var second = -1f
        for (e in t.embeddings) for ((_, x) in p.exemplars) {
            val s = similarity(e, x)
            if (s > first) { second = first; first = s } else if (s > second) second = s
        }
        return if (second < -0.5f) first else (first + second) / 2
    }

    /** Why [t] can never be [p] whatever the similarity, or null. */
    fun blocked(t: TrackInfo, p: Person): String? = when {
        t.id to p.id in rejected -> "rifiutato da te"
        (p.frames[t.fileId] ?: 0L) and t.frames != 0L -> "nello stesso fotogramma di un altro suo volto"
        else -> null
    }

    fun place(t: TrackInfo): Placement {
        val ranked = people.values.filter { it.exemplars.isNotEmpty() && blocked(t, it) == null }
            .map { it to score(t, it) }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return Placement(null, false, 0f)
        if (best.second >= FaceThresholds.AUTO) return Placement(best.first.id, false, best.second)
        // An ignored person only absorbs certain matches; it never gets suggestions.
        val hint = ranked.firstOrNull { !it.first.ignored && it.second >= FaceThresholds.SUGGEST }
        return if (hint != null) Placement(hint.first.id, true, hint.second) else Placement(null, false, best.second)
    }

    /** The [n] people [t] looks most like, each with the verdict the clustering would give. */
    fun explain(t: TrackInfo, n: Int = 5): List<Verdict> = people.values.filter { it.exemplars.isNotEmpty() }
        .map { it to score(t, it) }.sortedByDescending { it.second }.take(n).map { (p, s) ->
            val why = blocked(t, p) ?: when {
                s >= FaceThresholds.AUTO -> "abbastanza simile: stessa persona"
                s >= FaceThresholds.SUGGEST -> "somiglianza incerta: proposto come suggerimento"
                else -> "troppo diverso (serve %.2f)".format(FaceThresholds.AUTO)
            }
            Verdict(p.id, s, why)
        }

    /**
     * Unnamed people that turned out to be the same (two of their faces alike, no conflict): pairs
     * (kept, merged), the older kept. Only [touched] people are compared with the rest.
     */
    fun merges(touched: Collection<Long>): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        val gone = HashSet<Long>()
        for (id in touched.sorted()) {
            val a = people[id]?.takeIf { !it.named && !it.ignored && it.exemplars.isNotEmpty() } ?: continue
            if (a.id in gone) continue
            for (b in people.values.toList()) {
                if (b.id == a.id || b.id in gone || b.ignored || b.exemplars.isEmpty()) continue
                if (b.frames.any { (f, m) -> (a.frames[f] ?: 0L) and m != 0L }) continue
                if (a.exemplars.any { (t, _) -> t.id to b.id in rejected } || b.exemplars.any { (t, _) -> t.id to a.id in rejected }) continue
                var first = -1f; var second = -1f
                for ((_, x) in a.exemplars) for ((_, y) in b.exemplars) {
                    val s = similarity(x, y)
                    if (s > first) { second = first; first = s } else if (s > second) second = s
                }
                val score = if (second < -0.5f) first else (first + second) / 2
                if (score < FaceThresholds.AUTO) continue
                // Into the named one, else into the older one.
                val (keep, drop) = if (b.named || b.id < a.id) b to a else a to b
                out += keep.id to drop.id
                gone += drop.id
                for ((t, e) in drop.exemplars) { if (keep.exemplars.size < MAX_EXEMPLARS) keep.exemplars += t to e }
                drop.frames.forEach { (f, m) -> keep.frames.merge(f, m) { x, y -> x or y } }
                people.remove(drop.id)
                if (drop === a) break
            }
        }
        return out
    }

    companion object {
        const val MAX_EXEMPLARS = 20
    }
}

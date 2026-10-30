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

/**
 * Similarity thresholds, measured on the face bench (public-domain photos and video frames, made
 * video-like: see the face tests). A face is compared with the mean of its person's faces, never
 * with their single most alike face: with hundreds of people and faces, some pair of strangers
 * always gets alike by chance, and a mean does not.
 */
object FaceThresholds {
    /** At or above (for a face at least [FULL_WIDTH] px wide): the same person, joined automatically. */
    const val AUTO = 0.42f
    /** A smaller face looks more like anyone: it needs up to this much more. */
    const val SMALL_EXTRA = 0.05f
    const val FULL_WIDTH = 90f
    /** From here to the join threshold: a doubt for "Da controllare". */
    const val SUGGEST = 0.36f
    /** Two faces of one media are the same person (same light, same day: alike). */
    const val TRACK = 0.45f

    /** What a face [width] px wide needs to join a person. */
    fun auto(width: Float) = AUTO + SMALL_EXTRA * maxOf(0f, 1f - width / FULL_WIDTH)
}

/** A face seen in one frame of a media (frame 0 for a photo), already accepted by the quality checks. */
class Sighting(val frame: Int, val timeMs: Long, val embedding: FloatArray, val quality: Float, val payload: Any? = null,
               val width: Float = FaceThresholds.FULL_WIDTH)

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
    /** Its widest face (px): how much its embeddings can be trusted. */
    val width get() = sightings.maxOf { it.width }

    private fun mean(list: List<FloatArray>): FloatArray {
        val m = FloatArray(list.first().size)
        for (e in list) for (i in m.indices) m[i] += e[i]
        return normalised(m)
    }
}

/**
 * Groups the faces of one media into tracks: never two of the same frame, and a face joins a track
 * by the mean of its faces, so a track never drifts from one person to another one face at a time.
 */
object FaceTracker {
    /** Embeddings a track keeps (its best faces). */
    const val KEPT = 3

    fun build(sightings: List<Sighting>): List<TrackDraft> {
        val tracks = ArrayList<MutableList<Sighting>>()
        val sums = ArrayList<FloatArray>()
        for (s in sightings.sortedByDescending { it.quality }) {
            var home = -1; var best = FaceThresholds.TRACK
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

/** A stored track as the clustering sees it. [width]: its widest face in px (unknown: full trust). */
class TrackInfo(val id: Long, val fileId: String, val frames: Long, val embeddings: List<FloatArray>, val quality: Float,
                val width: Float = FaceThresholds.FULL_WIDTH)

/** What to do with a track: join [personId], suggest it to [personId], or start a new person (null). */
class Placement(val personId: Long?, val suggested: Boolean, val similarity: Float)

/** Why a track is (not) with a person, for "Confronta". */
class Verdict(val personId: Long, val similarity: Float, val reason: String)

/**
 * The people known so far, each by the mean of all its faces (its centre) and its best faces (up to
 * [MAX_EXEMPLARS], to show and explain); places new tracks among them. Pure logic: the repository
 * loads it from the database and stores its decisions.
 */
class PeopleIndex {
    class Person(val id: Long, var named: Boolean, var ignored: Boolean) {
        val exemplars = ArrayList<Pair<TrackInfo, FloatArray>>()
        /** Frames per media of every member track: two faces of one frame are never the same person. */
        val frames = HashMap<String, Long>()
        /** Every member track: the centre is their mean, weighed by how good each face is. */
        val members = ArrayList<TrackInfo>()
        private var centre: FloatArray? = null
        /** Its widest face: a person seen only small is compared like a small face. */
        val width get() = members.maxOfOrNull { it.width } ?: FaceThresholds.FULL_WIDTH

        fun centre(): FloatArray? {
            centre?.let { return it }
            if (members.isEmpty()) return null
            val sum = FloatArray(members.first().embeddings.first().size)
            for (t in members) {
                val w = weight(t); val e = t.embeddings.first()
                for (i in sum.indices) sum[i] += w * e[i]
            }
            return normalised(sum).also { centre = it }
        }

        internal fun changed() { centre = null }
    }

    val people = LinkedHashMap<Long, Person>()
    /** (track, person) pairs the user said no to. */
    val rejected = HashSet<Pair<Long, Long>>()

    fun person(id: Long, named: Boolean = false, ignored: Boolean = false) = people.getOrPut(id) { Person(id, named, ignored) }

    fun add(personId: Long, t: TrackInfo) {
        val p = people.getValue(personId)
        p.frames.merge(t.fileId, t.frames) { a, b -> a or b }
        p.members += t
        p.changed()
        for (e in t.embeddings) {
            if (p.exemplars.size < MAX_EXEMPLARS) p.exemplars += t to e
            else p.exemplars.withIndex().minByOrNull { it.value.first.quality }?.takeIf { it.value.first.quality < t.quality }
                ?.let { p.exemplars[it.index] = t to e }
        }
    }

    fun remove(personId: Long) { people.remove(personId) }

    /** [fileIds] were analysed again: the faces they had are gone, and so are the frames they took. */
    fun forget(fileIds: Set<String>) {
        for (p in people.values) {
            if (p.members.removeAll { it.fileId in fileIds }) p.changed()
            p.exemplars.removeAll { it.first.fileId in fileIds }
            p.frames.keys.removeAll(fileIds)
        }
    }

    /** How much [t] looks like [p]: its mean face against the centre of all theirs. */
    fun score(t: TrackInfo, p: Person): Float = p.centre()?.let { similarity(t.embeddings.first(), it) } ?: -1f

    /** Why [t] can never be [p] whatever the similarity, or null. */
    fun blocked(t: TrackInfo, p: Person): String? = when {
        t.id to p.id in rejected -> "rifiutato da te"
        (p.frames[t.fileId] ?: 0L) and t.frames != 0L -> "nello stesso fotogramma di un altro suo volto"
        else -> null
    }

    fun place(t: TrackInfo): Placement {
        val ranked = people.values.filter { it.members.isNotEmpty() && blocked(t, it) == null }
            .map { it to score(t, it) }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return Placement(null, false, 0f)
        if (best.second >= FaceThresholds.auto(t.width)) return Placement(best.first.id, false, best.second)
        // An ignored person only absorbs certain matches; it never gets suggestions.
        val hint = ranked.firstOrNull { !it.first.ignored && it.second >= FaceThresholds.SUGGEST }
        return if (hint != null) Placement(hint.first.id, true, hint.second) else Placement(null, false, best.second)
    }

    /** The [n] people [t] looks most like, each with the verdict the clustering would give. */
    fun explain(t: TrackInfo, n: Int = 5): List<Verdict> = people.values.filter { it.members.isNotEmpty() }
        .map { it to score(t, it) }.sortedByDescending { it.second }.take(n).map { (p, s) ->
            val need = FaceThresholds.auto(t.width)
            val why = blocked(t, p) ?: when {
                s >= need -> "abbastanza simile: stessa persona"
                s >= FaceThresholds.SUGGEST -> "somiglianza incerta: da controllare"
                else -> "troppo diverso (serve %.2f)".format(need)
            }
            Verdict(p.id, s, why)
        }

    /** How much two people look alike: their centres. */
    fun pairScore(a: Person, b: Person): Float {
        val x = a.centre() ?: return -1f; val y = b.centre() ?: return -1f
        return similarity(x, y)
    }

    /** Why [a] and [b] can never be one person, or null. */
    fun pairBlocked(a: Person, b: Person): String? = when {
        b.frames.any { (f, m) -> (a.frames[f] ?: 0L) and m != 0L } -> "compaiono insieme nello stesso fotogramma"
        a.members.any { it.id to b.id in rejected } || b.members.any { it.id to a.id in rejected } -> "separati da te"
        else -> null
    }

    /** People alike [id] (most alike first), each with its score, never blocked pairs. */
    fun similarTo(id: Long, min: Float = FaceThresholds.SUGGEST): List<Pair<Long, Float>> {
        val a = people[id]?.takeIf { it.members.isNotEmpty() } ?: return emptyList()
        return people.values.filter { it.id != id && !it.ignored && it.members.isNotEmpty() }
            .map { it to pairScore(a, it) }.filter { it.second >= min && pairBlocked(a, it.first) == null }
            .sortedByDescending { it.second }.map { it.first.id to it.second }
    }

    /**
     * Unnamed people that turned out to be the same (their centres alike, no conflict): (kept,
     * merged, similarity), the older kept. Only [touched] people are compared with the rest. People
     * with a name are never merged automatically: they become a doubt in "Da controllare".
     */
    fun merges(touched: Collection<Long>): List<Triple<Long, Long, Float>> {
        val out = ArrayList<Triple<Long, Long, Float>>()
        val gone = HashSet<Long>()
        for (id in touched.sorted()) {
            val a = people[id]?.takeIf { !it.named && !it.ignored && it.members.isNotEmpty() } ?: continue
            if (a.id in gone) continue
            for (b in people.values.toList()) {
                if (b.id == a.id || b.id in gone || b.ignored || b.named || b.members.isEmpty()) continue
                val score = pairScore(a, b)
                if (score < FaceThresholds.auto(minOf(a.width, b.width)) || pairBlocked(a, b) != null) continue
                val (keep, drop) = if (b.id < a.id) b to a else a to b
                out += Triple(keep.id, drop.id, score)
                gone += drop.id
                for (t in drop.members) add(keep.id, t)
                people.remove(drop.id)
                if (drop === a) break
            }
        }
        return out
    }

    companion object {
        const val MAX_EXEMPLARS = 20

        /** How much a face counts in its person's centre: small, blurred or turned ones less. */
        fun weight(t: TrackInfo) = 0.25f + t.quality
    }
}

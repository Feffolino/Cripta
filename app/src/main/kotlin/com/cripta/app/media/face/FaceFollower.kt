package com.cripta.app.media.face

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A face found in one frame of the closer reading ([FaceFollower]): where it is and, when usable, its embedding. */
class SeenFace(
    /** x, y, w, h as fractions of the frame. */
    val box: FloatArray,
    val embedding: FloatArray?,
    /** It passed the analysis' checks (sure, large, frontal, sharp): its embedding can be trusted. */
    val usable: Boolean,
)

/**
 * One frame of the closer reading and its faces: read for the moment [askedMs], it really is at
 * [timeMs] (null when the reader cannot tell: a key frame read through the platform's retriever).
 */
class SeenFrame(val askedMs: Long, val timeMs: Long?, val faces: List<SeenFace>)

/** A person the user confirmed in this video: their confirmed faces of this video only. */
class KnownHere(val personId: Long, val embeddings: List<FloatArray>, val frames: Long)

/** A face of this video nobody was given: each of its faces as stored ([StoredFace]). */
class LooseFace(val id: Long, val frames: Long, val faces: List<StoredFace>)

/**
 * The closer look at a video where the user confirmed someone: the confirmed person is found in
 * many more frames (by likeness to their own faces of this video, then followed from frame to
 * frame by where their face is), and each face nobody was given is judged moment by moment. A face
 * joins the confirmed one only when every moment of it that could be checked is that person, at
 * the same place at the same moment: nothing else is ever given to anyone. Faces the closer
 * reading does not find again (no face there, or a blurred or tiny one) are set aside.
 */
object FaceFollower {
    /** A face this alike (closest pair) to the person's faces of this video is them: same light, same day. */
    const val SEED = 0.45f
    /** ... and at least this alike to their mean, so that a chain of faces never drifts to someone else. */
    const val SEED_MEAN = 0.40f
    /** Two confirmed people must differ by this much for a face to be one of them. */
    const val MARGIN = 0.05f
    /** A face followed by its place must not look like someone else: at least this alike to the person. */
    const val FOLLOW = 0.28f
    /** Frames further apart than this are not followed by place (the face may have moved anywhere). */
    const val LINK_MS = 1_200L
    /** A stored face and a face of the closer reading are the same moment within this. */
    const val SAME_MOMENT_MS = 150L

    /** What to do with a [LooseFace]. */
    sealed interface Verdict {
        /** The confirmed person [personId], at the same place in every moment checked. */
        data class Join(val personId: Long) : Verdict
        /** Not found again by the closer reading, or only blurred or tiny: hardly a real face. */
        data object Aside : Verdict
        /** Someone else, or not sure: left as it is. */
        data object Keep : Verdict
    }

    /** Who each face of each frame is: an index into the people, or -1. [direct]: found by likeness, not by place. */
    class Labels(val who: List<IntArray>, val direct: List<BooleanArray>)

    private fun best(e: FloatArray, refs: List<FloatArray>): Float {
        var b = -1f
        for (r in refs) b = max(b, similarity(e, r))
        return b
    }

    private fun mean(list: List<FloatArray>): FloatArray {
        val m = FloatArray(list.first().size)
        for (e in list) for (i in m.indices) m[i] += e[i]
        var n = 0f
        for (v in m) n += v * v
        val k = 1f / kotlin.math.sqrt(n).coerceAtLeast(1e-6f)
        for (i in m.indices) m[i] *= k
        return m
    }

    /** Two boxes of nearby frames hold the same face: they overlap and are about as large. */
    fun near(a: FloatArray, b: FloatArray): Boolean {
        val ratio = max(a[2], b[2]) / min(a[2], b[2]).coerceAtLeast(1e-4f)
        if (ratio > 1.6f) return false
        val dx = abs(a[0] + a[2] / 2 - (b[0] + b[2] / 2)); val dy = abs(a[1] + a[3] / 2 - (b[1] + b[3] / 2))
        return dx <= 0.5f * max(a[2], b[2]) && dy <= 0.5f * max(a[3], b[3])
    }

    /**
     * Find [people] in [frames] (any order): first by likeness, a few rounds, each round
     * learning the faces just found; then from frame to frame by place. One face per person per
     * frame, one person per face.
     */
    fun label(frames: List<SeenFrame>, people: List<KnownHere>): Labels {
        val who = frames.map { f -> IntArray(f.faces.size) { -1 } }
        val direct = frames.map { f -> BooleanArray(f.faces.size) }
        val refs = people.map { it.embeddings.toMutableList() }
        for (round in 0 until 3) {
            val means = refs.map { if (it.isEmpty()) null else mean(it) }
            class Pick(val k: Int, val i: Int, val p: Int, val s: Float)
            val picks = ArrayList<Pick>()
            for ((k, f) in frames.withIndex()) for ((i, face) in f.faces.withIndex()) {
                if (who[k][i] >= 0 || !face.usable) continue
                val e = face.embedding ?: continue
                val sims = refs.map { if (it.isEmpty()) -1f else best(e, it) }
                val p = sims.indices.maxByOrNull { sims[it] } ?: continue
                val s = sims[p]
                val m = means[p] ?: continue
                if (s < SEED || similarity(e, m) < SEED_MEAN) continue
                if (sims.indices.any { it != p && sims[it] > s - MARGIN }) continue
                picks += Pick(k, i, p, s)
            }
            var added = false
            for (c in picks.sortedByDescending { it.s }) {
                if (who[c.k][c.i] >= 0 || who[c.k].any { it == c.p }) continue
                who[c.k][c.i] = c.p; direct[c.k][c.i] = true
                refs[c.p] += frames[c.k].faces[c.i].embedding!!
                added = true
            }
            if (!added) break
        }
        // By place: a face where the person's face was a moment before (or after) is them, unless it
        // looks like someone else. Forward and back until nothing changes.
        // Only frames whose real moment is known, in time order.
        val timed = frames.indices.filter { frames[it].timeMs != null }.sortedBy { frames[it].timeMs }
        var changed = true
        while (changed) {
            changed = false
            for (x in timed.indices) for (d in intArrayOf(-1, 1)) {
                val k = timed[x]; val j = timed.getOrNull(x + d) ?: continue
                if (abs(frames[j].timeMs!! - frames[k].timeMs!!) > LINK_MS) continue
                for ((i, p) in who[k].withIndex()) {
                    if (p < 0 || who[j].any { it == p }) continue
                    val from = frames[k].faces[i].box
                    val candidates = frames[j].faces.indices.filter { b ->
                        who[j][b] < 0 && near(from, frames[j].faces[b].box)
                    }
                    // Two faces there: not sure which, none followed.
                    val b = candidates.singleOrNull() ?: continue
                    val e = frames[j].faces[b].embedding
                    if (e != null) {
                        val sims = refs.map { if (it.isEmpty()) -1f else best(e, it) }
                        if (sims[p] < FOLLOW || sims.indices.any { it != p && sims[it] >= SEED }) continue
                    }
                    who[j][b] = p
                    changed = true
                }
            }
        }
        return Labels(who, direct)
    }

    /**
     * [face]'s fate: each of its moments is looked up in [frames] (the face at the same place).
     * Joined to a person only when every moment found again is them, one at least found by
     * likeness, none shows them elsewhere in the frame, and it shares no sampled frame with them.
     */
    fun judge(face: LooseFace, frames: List<SeenFrame>, labels: Labels, people: List<KnownHere>): Verdict {
        if (face.faces.isEmpty()) return Verdict.Keep
        var ghost = 0; var poor = 0; var checked = 0
        val yes = IntArray(people.size); val sure = BooleanArray(people.size); val no = IntArray(people.size)
        for (s in face.faces) {
            // The moment read again both ways (its key frame, as the analysis may have read it, and
            // the exact frame): the face is wherever one of them shows a face at its place.
            val ks = frames.indices.filter { abs(frames[it].askedMs - s.timeMs) <= SAME_MOMENT_MS }
            if (ks.isEmpty()) continue
            var k = -1; var at: Int? = null
            for (c in ks) {
                val f = frames[c]
                val i = f.faces.indices.filter { near(s.box, f.faces[it].box) }
                    .minByOrNull { (f.faces[it].box[0] - s.box[0]).let { d -> d * d } + (f.faces[it].box[1] - s.box[1]).let { d -> d * d } }
                    ?: continue
                // A face someone was found in wins: that is the frame the analysis saw.
                if (at == null || labels.who[c][i] >= 0 && labels.who[k][at] < 0) { k = c; at = i }
            }
            if (at == null) { ghost++; continue }
            val f = frames[k]
            checked++
            val p = labels.who[k][at]
            if (p >= 0) { yes[p]++; if (labels.direct[k][at]) sure[p] = true; continue }
            if (!f.faces[at].usable) poor++
            // Someone confirmed elsewhere in this frame: this face is another person.
            for (q in labels.who[k]) if (q >= 0) no[q]++
        }
        val n = face.faces.size
        for (p in people.indices) {
            if (checked > 0 && yes[p] == checked && sure[p] && no[p] == 0 && face.frames and people[p].frames == 0L) {
                return Verdict.Join(people[p].personId)
            }
        }
        if (yes.any { it > 0 } || no.any { it > 0 }) return Verdict.Keep
        return if (2 * ghost > n || 2 * (ghost + poor) > n) Verdict.Aside else Verdict.Keep
    }

    /**
     * The moments of the closer reading of a video [durationMs] long: about one a second (at most
     * [grid]), and around each moment of the faces to judge ([moments]), those moments too. Sorted,
     * at most [cap], the moments to judge kept first (the reader reads each of those twice: its key
     * frame and its exact frame).
     */
    fun plan(durationMs: Long, moments: Collection<Long>, grid: Int = 120, cap: Int = 200): List<Long> {
        if (durationMs <= 0) return emptyList()
        val must = sortedSetOf<Long>()
        val around = sortedSetOf<Long>()
        for (t in moments) {
            if (t in 0 until durationMs) must += t
            for (d in longArrayOf(-1_000, -500, 500, 1_000)) (t + d).takeIf { it in 0 until durationMs }?.let { around += it }
        }
        val n = (durationMs / 1_000).toInt().coerceIn(1, grid)
        val even = List(n) { i -> durationMs * (2 * i + 1) / (2L * n) }
        val out = ArrayList<Long>()
        fun add(list: Collection<Long>) {
            for (t in list) {
                if (out.size >= cap) return
                if (out.none { abs(it - t) < 100 }) out += t
            }
        }
        add(must); add(around); add(even)
        return out.sorted()
    }
}

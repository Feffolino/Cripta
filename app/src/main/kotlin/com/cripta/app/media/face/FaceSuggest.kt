package com.cripta.app.media.face

import kotlin.math.ln
import kotlin.math.min

/**
 * Which unassigned faces are offered as a person's ("Forse è …") and how alike they are. Free of
 * Android types (unit tests): the repository gives it embeddings and gets back the faces to offer.
 *
 * A person is the faces the user gave them, media by media: faces of one media count once (two of
 * one video, or the faces the closer look joined into one, are nearly the same example). The old
 * score, the mean of a face's three best matches among all of them, let every new media be one
 * more chance for a stranger to match three of them by luck, so the suggestions grew looser the
 * more the user confirmed. Here the share of media that must agree grows with them
 * ([agreeing]) and so does the bar ([threshold]), and what the user said no to counts against.
 */
object FaceSuggest {
    /** An unassigned face to judge. [frames]: the sampled frames it was seen in (bit mask; 0: a photo, or seen at no known frame). */
    class Candidate(
        val id: Long,
        val media: String,
        val frames: Long,
        val embeddings: List<FloatArray>,
        /** Among "poco chiari" (blurry, or seen in a single frame of a video): nearly always a mistake, so held to a higher bar. */
        val uncertain: Boolean = false,
    )

    /**
     * The faces of [candidates] to offer as the person's, most alike first, with their score.
     * [person]: the person's embeddings, per media. Faces of the person's own media ([ownMedia])
     * are never offered (they are judged in that media, by the closer look). [negatives]: faces
     * the user said are not them (refused, or kept apart from one of theirs): a face more like one
     * of those than like the person is left out.
     */
    fun pick(
        person: Map<String, List<FloatArray>>,
        candidates: List<Candidate>,
        ownMedia: Set<String> = person.keys,
        negatives: List<FloatArray> = emptyList(),
    ): List<Pair<Candidate, Float>> {
        val media = person.values.filter { it.isNotEmpty() }
        if (media.isEmpty()) return emptyList()
        val bar = threshold(media.size)
        val scored = ArrayList<Pair<Candidate, Float>>()
        for (c in candidates) {
            if (c.media in ownMedia || c.embeddings.isEmpty()) continue
            val s = score(media, c.embeddings)
            if (s < bar + if (c.uncertain) UNCERTAIN else 0f) continue
            if (negatives.isNotEmpty()) {
                val neg = best(negatives, c.embeddings)
                if (neg >= NEGATIVE && neg > s) continue
            }
            scored += c to s
        }
        // Two faces seen in one frame (or in one photo) are never both the person: the more alike one is offered.
        val out = ArrayList<Pair<Candidate, Float>>()
        val taken = HashMap<String, MutableList<Candidate>>()
        for (pair in scored.sortedByDescending { it.second }) {
            val c = pair.first
            val same = taken.getOrPut(c.media) { ArrayList() }
            if (same.any { it.frames == 0L || c.frames == 0L || it.frames and c.frames != 0L }) continue
            same += c
            out += pair
        }
        return out
    }

    /**
     * How alike [emb] is to a person whose faces are [media] (embeddings per media): each media
     * gives its closest match, and the score is the mean of the best [agreeing] of them. With one
     * media in eight agreeing, a stranger's lucky match in a few media weighs less and less.
     */
    fun score(media: List<List<FloatArray>>, emb: List<FloatArray>): Float {
        val sims = media.map { best(it, emb) }.sortedDescending()
        val k = agreeing(sims.size)
        var sum = 0f
        for (i in 0 until k) sum += sims[i]
        return sum / k
    }

    /** How many of a person's [n] media must look like a face: one up to 7, then one more every 8. */
    fun agreeing(n: Int) = min(n, 1 + n / 8).coerceAtLeast(1)

    /** The score a face needs with a person who has faces in [n] media: [BASE], a little higher with each doubling, at most [BASE] + [RISE]. */
    fun threshold(n: Int): Float = BASE + min(RISE, STEP * (ln(n.coerceAtLeast(1).toDouble()) / ln(2.0)).toFloat())

    private fun best(a: List<FloatArray>, b: List<FloatArray>): Float {
        var m = -1f
        for (x in a) for (y in b) { val s = similarity(x, y); if (s > m) m = s }
        return m
    }

    /** The score a face needs with a person seen in a single media (cosine of SFace embeddings). */
    const val BASE = 0.36f
    private const val STEP = 0.015f
    private const val RISE = 0.05f
    /** Extra score a face among "poco chiari" needs. */
    const val UNCERTAIN = 0.06f
    /** A face this alike to one the user said is not the person, and more than to the person, is not offered. */
    const val NEGATIVE = 0.45f
}

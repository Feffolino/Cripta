package com.cripta.app.data.face

import com.cripta.app.data.db.FaceApartEntity

/** The "Dividi il gruppo" pairs across a new analysis of a media. */
internal object FaceApart {
    /**
     * Where [pairs] go once the faces in [moved] were analysed again: `moved[old]` is the face it
     * became, or null when the analysis did not find it again. Faces not in [moved] stay as they
     * are (another media's faces: the pair follows them when their own media is analysed again).
     * A pair losing a side is dropped, and so is one whose two faces became the same face.
     *
     * Kept by track id, a pair outlived its faces by nothing: every new analysis gives new ids, so
     * "Ricomincia" or "Analizza di nuovo" put split faces back in one group.
     */
    fun remap(pairs: Collection<FaceApartEntity>, moved: Map<Long, Long?>): List<FaceApartEntity> =
        pairs.mapNotNull { p ->
            val a = if (p.a in moved) moved[p.a] ?: return@mapNotNull null else p.a
            val b = if (p.b in moved) moved[p.b] ?: return@mapNotNull null else p.b
            if (a == b) null else FaceApartEntity(minOf(a, b), maxOf(a, b))
        }.distinct()
}

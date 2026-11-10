package com.cripta.app.media.face

/** Per-media analysis numbers, stored with the scan and summed up in "Statistiche". */
object FaceStats {
    /** Upper bounds of the sharpness bands (the last band has none). */
    val SHARP_BANDS = doubleArrayOf(10.0, 20.0, 40.0, 80.0, 160.0)

    fun band(sharpness: Double): Int = SHARP_BANDS.indexOfFirst { sharpness < it }.let { if (it < 0) SHARP_BANDS.size else it }

    fun encode(sharp: IntArray, duplicates: Int, extra: Int) = "sharp=${sharp.joinToString(",")};dup=$duplicates;extra=$extra"

    class Parsed(val sharp: IntArray, val duplicates: Int, val extra: Int)

    fun parse(s: String): Parsed {
        val m = s.split(';').mapNotNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val sharp = IntArray(SHARP_BANDS.size + 1)
        m["sharp"]?.split(',')?.forEachIndexed { i, v -> if (i < sharp.size) sharp[i] = v.toIntOrNull() ?: 0 }
        return Parsed(sharp, m["dup"]?.toIntOrNull() ?: 0, m["extra"]?.toIntOrNull() ?: 0)
    }

    /**
     * What the closer look at a video did ([com.cripta.app.data.face.FaceRepository.refine]):
     * [signature] the confirmed faces it learnt from ("track:person", sorted), the faces it joined
     * to them and set aside, the frames it read.
     */
    class Refine(val signature: String, val joined: Int, val aside: Int, val frames: Int)

    private fun entries(s: String) = s.split(';').mapNotNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }

    /** The closer look kept in [s], or null when there was none since the media was analysed. */
    fun refineOf(s: String): Refine? {
        val m = entries(s).toMap()
        val sig = m["refined"] ?: return null
        return Refine(sig, m["rjoined"]?.toIntOrNull() ?: 0, m["raside"]?.toIntOrNull() ?: 0, m["rframes"]?.toIntOrNull() ?: 0)
    }

    /** [s] with [r] in place of the closer look it held. */
    fun withRefine(s: String, r: Refine): String {
        val mine = setOf("refined", "rjoined", "raside", "rframes")
        val kept = entries(s).filter { it.first !in mine }.map { "${it.first}=${it.second}" }
        return (kept + listOf("refined=${r.signature}", "rjoined=${r.joined}", "raside=${r.aside}", "rframes=${r.frames}")).joinToString(";")
    }

    /** "10–20" style label of band [i]. */
    fun bandLabel(i: Int): String = when (i) {
        0 -> "< %.0f".format(com.cripta.app.Strings.locale, SHARP_BANDS[0])
        SHARP_BANDS.size -> "≥ %.0f".format(com.cripta.app.Strings.locale, SHARP_BANDS.last())
        else -> "%.0f–%.0f".format(com.cripta.app.Strings.locale, SHARP_BANDS[i - 1], SHARP_BANDS[i])
    }

    /**
     * A cheap fingerprint of a frame (8 x 8 grey means, one bit each against their average), to skip
     * the same keyframe returned for two sampled moments.
     */
    fun frameHash(img: FaceImage): Long {
        val cells = DoubleArray(64)
        val cw = img.width / 8.0; val ch = img.height / 8.0
        for (cy in 0 until 8) for (cx in 0 until 8) {
            var sum = 0.0; var n = 0
            var y = (cy * ch).toInt()
            while (y < ((cy + 1) * ch).toInt()) {
                var x = (cx * cw).toInt()
                while (x < ((cx + 1) * cw).toInt()) {
                    val c = img.argb[y * img.width + x]
                    sum += (c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF); n++
                    x += 4
                }
                y += 4
            }
            cells[cy * 8 + cx] = if (n == 0) 0.0 else sum / n
        }
        val mean = cells.average()
        var h = 0L
        for (i in 0 until 64) if (cells[i] > mean) h = h or (1L shl i)
        return h
    }

    fun sameFrame(a: Long, b: Long) = java.lang.Long.bitCount(a xor b) <= 2

    /**
     * The frame just read is the one before it: an alike picture ([sameFrame]) from the same moment
     * (a key frame returned for two requested times). Alike pictures far apart in time are not
     * duplicates: on a fixed camera a small face entering the scene barely changes the hash.
     */
    fun isSameMoment(hash: Long, last: Long, realMs: Long, lastRealMs: Long?): Boolean =
        sameFrame(hash, last) && lastRealMs != null && kotlin.math.abs(realMs - lastRealMs) <= SAME_MOMENT_MS

    private const val SAME_MOMENT_MS = 1_000L
}

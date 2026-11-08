package com.cripta.app.data

/** What the vault's bytes are, in the order Impostazioni › Spazio lists them. */
enum class SpaceKind { PHOTOS, VIDEOS, NOTES, PDF, OTHER_FILES, TRASH, CACHE, INDEXES, APP }

/** The kind a file's blob counts as: the trash first (it is going away), then its type. */
fun spaceKindOf(mime: String, trashed: Boolean): SpaceKind = when {
    trashed -> SpaceKind.TRASH
    VaultRepository.isVideo(mime) -> SpaceKind.VIDEOS
    VaultRepository.isImage(mime) -> SpaceKind.PHOTOS
    VaultRepository.isNote(mime) -> SpaceKind.NOTES
    VaultRepository.isPdf(mime) -> SpaceKind.PDF
    else -> SpaceKind.OTHER_FILES
}

/**
 * The space Cripta takes, split by kind, and the phone's storage around it (the data partition).
 * The kinds add up to [vault]; whatever the phone uses besides Cripta is [other].
 */
data class SpaceReport(
    val byKind: Map<SpaceKind, Long>,
    val deviceTotal: Long,
    val deviceFree: Long,
) {
    val vault: Long get() = byKind.values.sum()

    /** Used by everything else on the phone; never negative (the sizes are read a moment apart). */
    val other: Long get() = (deviceTotal - deviceFree - vault).coerceAtLeast(0)

    /** Share of the whole phone storage, for the bar. */
    fun share(bytes: Long): Float = if (deviceTotal > 0) (bytes.toDouble() / deviceTotal).toFloat().coerceIn(0f, 1f) else 0f

    companion object {
        /**
         * Builds the report from what was measured: each blob with its kind, the caches, the
         * database (indexes of faces, intros and similar pictures live in it) and the app's whole
         * footprint. What the app takes beyond the named kinds (the download engine, leftovers)
         * is [SpaceKind.APP], so the kinds always add up to the footprint.
         */
        fun of(
            blobs: List<Pair<SpaceKind, Long>>,
            cache: Long,
            database: Long,
            appTotal: Long,
            deviceTotal: Long,
            deviceFree: Long,
        ): SpaceReport {
            val map = java.util.EnumMap<SpaceKind, Long>(SpaceKind::class.java)
            SpaceKind.entries.forEach { map[it] = 0L }
            blobs.forEach { (k, n) -> map[k] = map.getValue(k) + n }
            map[SpaceKind.CACHE] = map.getValue(SpaceKind.CACHE) + cache
            map[SpaceKind.INDEXES] = map.getValue(SpaceKind.INDEXES) + database
            val named = map.values.sum()
            map[SpaceKind.APP] = map.getValue(SpaceKind.APP) + (appTotal - named).coerceAtLeast(0)
            return SpaceReport(map, deviceTotal, deviceFree)
        }
    }
}

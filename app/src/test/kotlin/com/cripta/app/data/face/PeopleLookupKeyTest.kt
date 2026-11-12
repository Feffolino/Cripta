package com.cripta.app.data.face

import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The people's key the vault and Home filters re-run on: only what a lookup by person reads. */
class PeopleLookupKeyTest {
    private fun file(id: String) = FileEntity(
        id = id, originalName = "$id.jpg", mimeType = "image/jpeg", sizeBytes = 1, folderId = null,
        createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0),
    )

    private fun overview(name: String = "Anna", ignored: Boolean = false, media: List<String> = listOf("a", "b"), coverTrack: Long? = null, scans: Int = 0) =
        FaceRepository.Overview(
            scans = emptyList(), media = scans,
            people = listOf(FaceRepository.Person(PersonEntity(id = 1, name = name, ignored = ignored, coverTrackId = coverTrack, createdAt = 0),
                media.map { file(it) }, null, emptyList())),
            files = emptyMap(),
        )

    @Test
    fun aRebuildThatChangesNoLookupKeepsTheKey() {
        // Another cover, more media analysed, the same media in another order: nothing to look up again.
        assertEquals(overview().lookupKey, overview(coverTrack = 7, scans = 40, media = listOf("b", "a")).lookupKey)
    }

    @Test
    fun aNameAMediaOrNotAPersonChangesTheKey() {
        assertNotEquals(overview().lookupKey, overview(name = "Anna B").lookupKey)
        assertNotEquals(overview().lookupKey, overview(media = listOf("a")).lookupKey)
        assertNotEquals(overview().lookupKey, overview(ignored = true).lookupKey)
    }
}

package com.cripta.app.data.face

import com.cripta.app.data.db.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which old faces a face analysis that stopped part way (a timeout) replaces. */
class PartialReadFacesTest {
    private fun track(id: Long, times: String, personId: Long? = null, state: String = "auto") =
        TrackRow(id = id, fileId = "v", frames = 1, quality = 1f, embeddings = ByteArray(0),
            personId = personId, state = state, similarity = 0f, times = times)

    private val old = listOf(
        track(1, "1000,2000"),                              // found again
        track(2, "3000,4000"),                              // in the part read, not found again
        track(3, "9000,12000"),                             // runs past what was read
        track(4, "15000"),                                  // beyond what was read
        track(5, ""),                                       // moments unknown
        track(6, "2500", personId = 7, state = "manual"),   // confirmed by the user
        track(7, "2600", personId = 7, state = "carried"),  // confirmed earlier, carried
        track(8, "2700"),                                   // the user said "no" (rejection)
        track(9, "2800"),                                   // split by the user
        track(10, "2900", personId = 7, state = "suggested"),
    )

    @Test
    fun theReadPartIsReplacedTheRestAndTheUsersFacesStay() {
        val gone = FaceRepository.replacedByPartialRead(old, found = setOf(1L), readUpTo = 10_000, userIds = setOf(8L, 9L))
        assertEquals(listOf(1L, 2L, 10L), gone)
    }

    @Test
    fun nothingReadReplacesOnlyWhatWasFoundAgain() {
        assertEquals(listOf(1L), FaceRepository.replacedByPartialRead(old, found = setOf(1L), readUpTo = null, userIds = emptySet()))
    }
}

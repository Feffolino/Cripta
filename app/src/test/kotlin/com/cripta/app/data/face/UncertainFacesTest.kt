package com.cripta.app.data.face

import com.cripta.app.data.db.TrackCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UncertainFacesTest {
    private fun card(count: Int, quality: Float, personId: Long? = null, state: String = "auto") =
        TrackCard(1, "f", 0, 80, count, personId, state, 0f, quality = quality)

    @Test
    fun onlyUnassignedFacesOfOneFrameOrLowQualityFold() {
        assertTrue(FaceRepository.uncertain(card(1, 0.9f), video = true))
        assertFalse(FaceRepository.uncertain(card(1, 0.9f), video = false))
        assertTrue(FaceRepository.uncertain(card(5, 0.1f), video = false))
        assertFalse(FaceRepository.uncertain(card(5, 0.9f), video = true))
        // Faces someone was given, or added by hand, always show.
        assertFalse(FaceRepository.uncertain(card(1, 0.1f, personId = 3, state = "manual"), video = true))
        assertFalse(FaceRepository.uncertain(card(1, 0.1f, state = "manual"), video = true))
    }
}

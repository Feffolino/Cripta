package com.cripta.app.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultDragTest {
    @Test
    fun `edge depth is zero in the middle and grows towards each edge`() {
        assertEquals(0f, edgeDepth(500f, 1000f, 100f), 0f)
        assertEquals(-0.5f, edgeDepth(50f, 1000f, 100f), 1e-6f)
        assertEquals(0.5f, edgeDepth(950f, 1000f, 100f), 1e-6f)
        assertEquals(-1f, edgeDepth(0f, 1000f, 100f), 0f)
        assertEquals(1f, edgeDepth(1000f, 1000f, 100f), 0f)
    }

    @Test
    fun `a finger past the grid's edge scrolls at full speed, not faster`() {
        assertEquals(-1f, edgeDepth(-300f, 1000f, 100f), 0f)
        assertEquals(1f, edgeDepth(1400f, 1000f, 100f), 0f)
    }

    @Test
    fun `a short grid keeps a still middle`() {
        assertEquals(0f, edgeDepth(60f, 120f, 100f), 0f)
        assertTrue(edgeDepth(10f, 120f, 100f) < 0f)
        assertTrue(edgeDepth(110f, 120f, 100f) > 0f)
    }

    @Test
    fun `speed keeps the direction and is gentler near the zone's edge`() {
        assertEquals(0f, edgeSpeed(0f, 1000f), 0f)
        assertEquals(-1000f, edgeSpeed(-1f, 1000f), 0f)
        assertEquals(1000f, edgeSpeed(1f, 1000f), 0f)
        assertEquals(250f, edgeSpeed(0.65f, 1000f), 1e-2f)
        assertEquals(-250f, edgeSpeed(-0.65f, 1000f), 1e-2f)
        assertTrue(edgeSpeed(0.5f, 1000f) < 0.5f * 1000f / 4f)
    }

    @Test
    fun `a finger grazing the edge zone does not scroll`() {
        assertEquals(0f, edgeSpeed(0.1f, 1000f), 0f)
        assertEquals(0f, edgeSpeed(-EDGE_DEAD, 1000f), 0f)
        assertEquals(0f, edgeSpeed(EDGE_DEAD, 1000f), 0f)
        assertTrue(edgeSpeed(EDGE_DEAD + 0.05f, 1000f) > 0f)
        // On a 1000 px grid with a 100 px zone, the last 30 px are still at rest.
        assertEquals(0f, edgeSpeed(edgeDepth(925f, 1000f, 100f), 1000f), 0f)
        assertTrue(edgeSpeed(edgeDepth(960f, 1000f, 100f), 1000f) > 0f)
    }

    @Test
    fun `auto-scroll waits a moment, then speeds up gently`() {
        assertEquals(0f, edgeRamp(0L), 0f)
        assertEquals(0f, edgeRamp(EDGE_DELAY_MS), 0f)
        assertEquals(0.5f, edgeRamp(EDGE_DELAY_MS + EDGE_RAMP_MS / 2), 1e-2f)
        assertEquals(1f, edgeRamp(EDGE_DELAY_MS + EDGE_RAMP_MS), 0f)
        assertEquals(1f, edgeRamp(10_000L), 0f)
    }

    @Test
    fun `the folder under the finger wins`() {
        assertEquals(DropTarget.Into(7L), moveDropTarget(hover = 7L, here = 3L, origin = 3L))
        assertEquals(DropTarget.Into(7L), moveDropTarget(hover = 7L, here = 9L, origin = 3L))
    }

    @Test
    fun `released elsewhere after changing folder moves into the open folder`() {
        assertEquals(DropTarget.Into(9L), moveDropTarget(hover = null, here = 9L, origin = 3L))
        // Root is a folder too (null): carried up from a folder and released on the root's files.
        assertEquals(DropTarget.Into(null), moveDropTarget(hover = null, here = null, origin = 3L))
    }

    @Test
    fun `released elsewhere in the starting folder moves nothing`() {
        assertEquals(DropTarget.Stay, moveDropTarget(hover = null, here = 3L, origin = 3L))
        assertEquals(DropTarget.Stay, moveDropTarget(hover = null, here = null, origin = null))
    }
}

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
        assertEquals(250f, edgeSpeed(0.5f, 1000f), 1e-3f)
        assertTrue(edgeSpeed(0.2f, 1000f) < 0.2f * 1000f)
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

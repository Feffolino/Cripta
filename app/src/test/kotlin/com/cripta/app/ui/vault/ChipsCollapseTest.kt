package com.cripta.app.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipsCollapseTest {
    @Test fun `a drag up folds by exactly the drag and uses all of it`() {
        val (f, used) = collapseStep(0f, -10f, 100f, canHide = true)
        assertEquals(0.1f, f, 1e-4f)
        assertEquals(-10f, used, 1e-4f)
    }

    @Test fun `a drag down unfolds first, then hands the rest to the grid`() {
        val (f, used) = collapseStep(0.1f, 30f, 100f, canHide = true)
        assertEquals(0f, f, 1e-4f)
        assertEquals(10f, used, 1e-4f) // 20px left over scroll the grid
    }

    @Test fun `folded chips leave the whole drag to the grid`() {
        assertEquals(1f to 0f, collapseStep(1f, -5f, 100f, canHide = true))
        assertEquals(0f to 0f, collapseStep(0f, 5f, 100f, canHide = true))
    }

    @Test fun `nothing more below keeps the chips`() {
        assertEquals(0f to 0f, collapseStep(0f, -10f, 100f, canHide = false))
    }

    @Test fun `no chips means nothing to fold`() {
        assertEquals(0f to 0f, collapseStep(0f, -10f, 0f, canHide = true))
    }
}

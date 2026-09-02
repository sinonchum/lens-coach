package com.lenscoach.android.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensInventoryTest {
    @Test
    fun `wide zoom range builds ultra-wide, wide, tele and 5x steps`() {
        val steps = LensInventory.stepsForZoomRange(0.5f, 8f, emptyList())
        assertEquals(listOf(0.5f, 1f, 3.2f, 5f), steps.map { it.zoom })
        assertEquals(LensRole.ULTRA_WIDE, steps[0].role)
        assertEquals(LensRole.WIDE, steps[1].role)
        assertEquals(LensRole.TELE, steps[2].role)
        assertEquals(LensRole.TELE, steps[3].role)
        assertTrue(steps[0].optical)
        assertTrue(steps[1].optical)
        assertTrue(steps[2].optical)
        assertFalse(steps[3].optical)
    }

    @Test
    fun `narrow range builds wide and 2x tele`() {
        val steps = LensInventory.stepsForZoomRange(1f, 2f, emptyList())
        assertEquals(listOf(1f, 2f), steps.map { it.zoom })
        assertEquals(LensRole.WIDE, steps[0].role)
        assertEquals(LensRole.TELE, steps[1].role)
        assertTrue(steps[1].optical)
    }

    @Test
    fun `single-range camera collapses to one wide step`() {
        val steps = LensInventory.stepsForZoomRange(1f, 1f, emptyList())
        assertEquals(listOf(1f), steps.map { it.zoom })
        assertEquals(LensRole.WIDE, steps[0].role)
    }

    @Test
    fun `invalid range returns the fallback`() {
        val fallback = listOf(LensStep("wide", "1x", 1f, LensRole.WIDE, optical = true))
        assertEquals(fallback, LensInventory.stepsForZoomRange(0f, 5f, fallback))
        assertEquals(fallback, LensInventory.stepsForZoomRange(5f, 1f, fallback))
    }

    @Test
    fun `nearest picks the closest step`() {
        val steps = LensInventory.stepsForZoomRange(1f, 8f, emptyList())
        assertEquals("tele", steps.nearest(2.5f)?.id)
        assertEquals("wide", steps.nearest(1.04f)?.id)
        assertEquals("super", steps.nearest(7f)?.id)
    }

    @Test
    fun `nearest on empty list returns null`() {
        assertNull(emptyList<LensStep>().nearest(1f))
    }
}

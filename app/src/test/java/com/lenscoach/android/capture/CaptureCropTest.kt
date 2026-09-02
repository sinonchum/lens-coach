package com.lenscoach.android.capture

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureCropTest {
    // FILL_CENTER of an 800x600 image inside a 400x800 view:
    // scale = 4/3, shown 1066.7x800, originX = -333.3, originY = 0.
    @Test
    fun `maps fill-center guide rect into image coordinates`() {
        val rect = CaptureCrop.guideRect(
            guide = Rect(100f, 100f, 300f, 700f),
            viewWidth = 400f,
            viewHeight = 800f,
            imageWidth = 800f,
            imageHeight = 600f,
            mirrored = false,
        )!!
        assertEquals(325f, rect.left, 0.6f)
        assertEquals(75f, rect.top, 0.01f)
        assertEquals(150f, rect.width, 0.6f)
        assertEquals(450f, rect.height, 0.01f)
    }

    @Test
    fun `mirrored guide flips the x axis`() {
        val rect = CaptureCrop.guideRect(
            guide = Rect(50f, 100f, 150f, 700f),
            viewWidth = 400f,
            viewHeight = 800f,
            imageWidth = 800f,
            imageHeight = 600f,
            mirrored = true,
        )!!
        assertEquals(437.5f, rect.left, 0.6f)
        assertEquals(75f, rect.width, 0.01f)
    }

    @Test
    fun `guide covering the whole view of a matching image returns null`() {
        val rect = CaptureCrop.guideRect(
            guide = Rect(0f, 0f, 400f, 800f),
            viewWidth = 400f,
            viewHeight = 800f,
            imageWidth = 400f,
            imageHeight = 800f,
            mirrored = false,
        )
        assertNull(rect)
    }

    @Test
    fun `degenerate inputs return null`() {
        assertNull(
            CaptureCrop.guideRect(
                guide = Rect(0f, 0f, 200f, 400f),
                viewWidth = 4f,
                viewHeight = 800f,
                imageWidth = 800f,
                imageHeight = 600f,
                mirrored = false,
            ),
        )
        assertNull(
            CaptureCrop.guideRect(
                guide = Rect(0f, 0f, 4f, 4f),
                viewWidth = 400f,
                viewHeight = 800f,
                imageWidth = 800f,
                imageHeight = 600f,
                mirrored = false,
            ),
        )
    }
}

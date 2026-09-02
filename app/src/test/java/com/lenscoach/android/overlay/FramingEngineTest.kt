package com.lenscoach.android.overlay

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class FramingEngineTest {
    @Test
    fun `portrait letterbox is 4 by 5 with horizon offset`() {
        val frame = FramingEngine.letterbox(1080f, 1920f, SceneRecipe.PORTRAIT)
        assertEquals(1080f, frame.width, 0.01f)
        assertEquals(1350f, frame.height, 0.01f)
        assertEquals(188.1f, frame.top, 0.5f)
    }

    @Test
    fun `landscape letterbox is 16 by 9`() {
        val frame = FramingEngine.letterbox(1080f, 1920f, SceneRecipe.LANDSCAPE)
        assertEquals(1080f, frame.width, 0.01f)
        assertEquals(607.5f, frame.height, 0.01f)
        assertEquals(840f, frame.top, 0.5f)
    }

    @Test
    fun `street letterbox is 3 by 2`() {
        val frame = FramingEngine.letterbox(1080f, 1920f, SceneRecipe.STREET)
        assertEquals(1080f, frame.width, 0.01f)
        assertEquals(720f, frame.height, 0.01f)
    }

    @Test
    fun `compose honors subject bias when the frame is narrower than the view`() {
        // Landscape view: 4:5 frame fits vertically, so its width (800) < view width (2000).
        val subject = Rect(1100f, 100f, 1300f, 220f)
        val frame = FramingEngine.compose(2000f, 1000f, SceneRecipe.PORTRAIT, subject)
        assertEquals(800f, frame.width, 0.01f)
        assertEquals(1000f, frame.height, 0.01f)
        // left = subjectX(1200) - 800 * 0.38 = 896, unclamped
        assertEquals(896f, frame.left, 0.01f)
        // top clamps to 0: eyeY(145.6) - 1000 * 0.33 < 0
        assertEquals(0f, frame.top, 0.01f)
    }

    @Test
    fun `full-width frame clamps to the left edge`() {
        // On a tall phone view the 4:5 frame spans the full width, so left must be 0.
        val subject = Rect(400f, 400f, 600f, 500f)
        val frame = FramingEngine.compose(1000f, 2000f, SceneRecipe.PORTRAIT, subject)
        assertEquals(1000f, frame.width, 0.01f)
        assertEquals(0f, frame.left, 0.01f)
        // top = eyeY(438) - 1250 * 0.33 = 25.5
        assertEquals(25.5f, frame.top, 0.01f)
    }

    @Test
    fun `compose without subject falls back to the default frame`() {
        val frame = FramingEngine.compose(1080f, 1920f, SceneRecipe.PORTRAIT, null)
        assertEquals(FramingEngine.letterbox(1080f, 1920f, SceneRecipe.PORTRAIT), frame)
    }

    @Test
    fun `landscape compose ignores the subject`() {
        val subject = Rect(400f, 400f, 600f, 500f)
        val frame = FramingEngine.compose(1080f, 1920f, SceneRecipe.LANDSCAPE, subject)
        assertEquals(FramingEngine.letterbox(1080f, 1920f, SceneRecipe.LANDSCAPE), frame)
    }

    @Test
    fun `frame stays inside the view for edge subjects`() {
        val subject = Rect(0f, 0f, 50f, 50f)
        val frame = FramingEngine.compose(1080f, 1920f, SceneRecipe.PORTRAIT, subject)
        assertEquals(0f, frame.left, 0.01f)
        assertEquals(0f, frame.top, 0.01f)
    }
}

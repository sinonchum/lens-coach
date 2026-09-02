package com.lenscoach.android.camera

import androidx.compose.ui.geometry.Rect
import com.lenscoach.android.overlay.SceneRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneDirectorTest {
    private val steps = LensInventory.stepsForZoomRange(0.5f, 8f, emptyList())

    private fun decide(
        faces: List<Rect> = emptyList(),
        labels: List<SceneLabel> = emptyList(),
        objects: List<SceneObject> = emptyList(),
        front: Boolean = false,
    ): DirectorDecision = SceneDirector.decide(
        steps = steps,
        currentZoom = 1f,
        viewWidth = 400f,
        viewHeight = 800f,
        faces = faces,
        objects = objects,
        labels = labels,
        horizonDegrees = 0f,
        front = front,
    )

    @Test
    fun `single face classifies portrait and picks the face as subject`() {
        val face = Rect(100f, 200f, 200f, 320f)
        val decision = decide(faces = listOf(face))
        assertEquals(SceneKind.PORTRAIT, decision.scene)
        assertEquals(SceneRecipe.PORTRAIT, decision.recipe)
        assertEquals(face, decision.subject)
    }

    @Test
    fun `two faces classify as group`() {
        val decision = decide(
            faces = listOf(
                Rect(40f, 100f, 120f, 220f),
                Rect(240f, 100f, 320f, 220f),
            ),
        )
        assertEquals(SceneKind.GROUP, decision.scene)
        assertEquals(SceneRecipe.STREET, decision.recipe)
    }

    @Test
    fun `food labels classify food and skip the face as subject`() {
        val decision = decide(
            faces = listOf(Rect(100f, 200f, 200f, 320f)),
            labels = listOf(SceneLabel("Food", 0.9f)),
        )
        assertEquals(SceneKind.FOOD, decision.scene)
        assertEquals(SceneRecipe.PORTRAIT, decision.recipe)
        assertNull(decision.subject)
    }

    @Test
    fun `landscape labels without faces classify landscape`() {
        val decision = decide(
            labels = listOf(SceneLabel("Mountain", 0.9f), SceneLabel("Sky", 0.8f)),
        )
        assertEquals(SceneKind.LANDSCAPE, decision.scene)
        assertEquals(SceneRecipe.LANDSCAPE, decision.recipe)
        assertNull(decision.subject)
    }

    @Test
    fun `no signals fall back to unknown street`() {
        val decision = decide()
        assertEquals(SceneKind.UNKNOWN, decision.scene)
        assertEquals(SceneRecipe.STREET, decision.recipe)
    }

    @Test
    fun `small portrait subject zooms toward target fill capped at 2_4x`() {
        val decision = decide(faces = listOf(Rect(190f, 300f, 210f, 380f)))
        assertEquals(SceneKind.PORTRAIT, decision.scene)
        assertEquals(2.4f, decision.zoom, 0.01f)
    }

    @Test
    fun `front camera keeps zoom near unity`() {
        val decision = decide(front = true, faces = listOf(Rect(150f, 250f, 250f, 400f)))
        assertEquals(1f, decision.zoom, 0.01f)
    }
}

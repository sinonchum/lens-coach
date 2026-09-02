package com.lenscoach.android.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.lenscoach.android.R
import com.lenscoach.android.overlay.SceneRecipe
import com.lenscoach.android.ui.UiText
import kotlin.math.abs
import kotlin.math.max

enum class SceneKind {
    PORTRAIT,
    GROUP,
    FOOD,
    LANDSCAPE,
    ARCHITECTURE,
    STREET,
    PET,
    MACRO,
    UNKNOWN,
}

/** Directional guidance drawn on the viewfinder while the frame is acquiring. */
enum class SceneCue {
    NONE,
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_CLOSER,
    MOVE_BACK,
}

data class SceneLabel(
    val text: String,
    val confidence: Float,
)

data class SceneObject(
    val box: Rect,
    val category: String?,
)

data class DirectorDecision(
    val scene: SceneKind,
    val recipe: SceneRecipe,
    val sceneLabel: UiText,
    val lens: LensStep?,
    val zoom: Float,
    val subject: Rect?,
    val focus: Offset?,
    val hint: UiText,
    val why: UiText,
    val cue: SceneCue,
)

object SceneDirector {
    fun decide(
        steps: List<LensStep>,
        currentZoom: Float,
        viewWidth: Float,
        viewHeight: Float,
        faces: List<Rect>,
        objects: List<SceneObject>,
        labels: List<SceneLabel>,
        horizonDegrees: Float,
        front: Boolean,
    ): DirectorDecision {
        val scene = classify(faces, objects, labels)
        val recipe = SceneRecipe.from(scene)
        val subject = pickSubject(faces, objects, scene, viewWidth, viewHeight)
        val lens = if (front) {
            steps.minByOrNull { abs(it.zoom - 1f) } ?: steps.firstOrNull()
        } else {
            pickLens(scene, recipe, steps, subject, viewHeight)
        }
        val zoom = refineZoom(
            base = lens?.zoom ?: currentZoom.coerceAtLeast(1f),
            scene = scene,
            recipe = recipe,
            subject = subject,
            viewHeight = viewHeight,
            minZoom = steps.minOfOrNull { it.zoom } ?: 1f,
            maxZoom = steps.maxOfOrNull { it.zoom } ?: currentZoom,
            front = front,
        )
        val (hint, why, cue) = coach(scene, recipe, lens, subject, viewWidth, viewHeight, horizonDegrees)
        return DirectorDecision(
            scene = scene,
            recipe = recipe,
            sceneLabel = scene.displayName,
            lens = lens,
            zoom = zoom,
            subject = subject,
            focus = subject?.center,
            hint = hint,
            why = why,
            cue = cue,
        )
    }

    private fun classify(
        faces: List<Rect>,
        objects: List<SceneObject>,
        labels: List<SceneLabel>,
    ): SceneKind {
        if (faces.size >= 2) return SceneKind.GROUP
        val names = labels.map { it.text.lowercase() }
        fun has(vararg keys: String) = names.any { name -> keys.any { key -> name.contains(key) } }
        val objectHit = objects.mapNotNull { it.category?.lowercase() }
        fun objHas(vararg keys: String) = objectHit.any { c -> keys.any { key -> c.contains(key) } }
        return when {
            has("food", "cuisine", "dish", "dessert", "drink", "coffee", "meal", "fruit") ||
                objHas("food") -> SceneKind.FOOD
            has("dog", "cat", "pet", "animal") -> SceneKind.PET
            has("flower", "insect") || objHas("plant") && faces.isEmpty() -> SceneKind.MACRO
            has("mountain", "lake", "beach", "valley", "horizon", "sunset", "sky", "landscape") &&
                faces.isEmpty() -> SceneKind.LANDSCAPE
            has("building", "skyscraper", "architecture", "bridge", "tower") -> SceneKind.ARCHITECTURE
            has("street", "downtown", "city", "road", "traffic") -> SceneKind.STREET
            faces.isNotEmpty() || has("person", "people", "portrait", "face", "human") ||
                objHas("fashion") -> SceneKind.PORTRAIT
            else -> SceneKind.UNKNOWN
        }
    }

    private fun pickSubject(
        faces: List<Rect>,
        objects: List<SceneObject>,
        scene: SceneKind,
        viewWidth: Float,
        viewHeight: Float,
    ): Rect? {
        val face = faces.maxByOrNull { it.width * it.height }
        if (face != null && scene != SceneKind.FOOD && scene != SceneKind.LANDSCAPE) return face
        val ranked = objects.maxByOrNull { obj ->
            val area = obj.box.width * obj.box.height
            val prefer = when (scene) {
                SceneKind.FOOD -> if (obj.category?.contains("food", true) == true) 2.4f else 1f
                SceneKind.MACRO -> if (obj.category?.contains("plant", true) == true) 2f else 1f
                else -> 1f
            }
            area * prefer
        }?.box
        return ranked?.takeIf { it.width * it.height > viewWidth * viewHeight * 0.02f }
    }

    private fun pickLens(
        scene: SceneKind,
        recipe: SceneRecipe,
        steps: List<LensStep>,
        subject: Rect?,
        viewHeight: Float,
    ): LensStep? {
        if (steps.isEmpty()) return null
        val fill = if (subject != null && viewHeight > 1f) subject.height / viewHeight else 0f
        val want = when (scene) {
            SceneKind.PORTRAIT -> if (fill > 0.42f) LensRole.WIDE else recipe.preferredLens
            SceneKind.GROUP -> LensRole.ULTRA_WIDE.takeIf { steps.any { it.role == LensRole.ULTRA_WIDE } }
                ?: LensRole.WIDE
            SceneKind.LANDSCAPE, SceneKind.ARCHITECTURE -> recipe.preferredLens
            SceneKind.STREET, SceneKind.UNKNOWN -> LensRole.WIDE
            SceneKind.FOOD, SceneKind.MACRO -> LensRole.WIDE
            SceneKind.PET -> if (fill < 0.28f) LensRole.TELE else LensRole.WIDE
        }
        return steps.firstOrNull { it.role == want && it.optical } ?: steps.nearestByRole(want)
            ?: steps.firstOrNull { it.role == LensRole.WIDE } ?: steps.first()
    }

    private fun refineZoom(
        base: Float,
        scene: SceneKind,
        recipe: SceneRecipe,
        subject: Rect?,
        viewHeight: Float,
        minZoom: Float,
        maxZoom: Float,
        front: Boolean,
    ): Float {
        if (front || subject == null || viewHeight <= 1f) {
            return base.coerceIn(minZoom, maxZoom)
        }
        val targetFill = when (scene) {
            SceneKind.PORTRAIT, SceneKind.PET -> recipe.fillRatio.coerceAtLeast(0.28f)
            SceneKind.FOOD, SceneKind.MACRO -> 0.46f
            SceneKind.GROUP -> 0.34f
            else -> return base.coerceIn(minZoom, maxZoom)
        }
        val currentFill = (subject.height / viewHeight).coerceAtLeast(0.04f)
        val adjusted = base * (targetFill / currentFill)
        val ceiling = when (scene) {
            SceneKind.FOOD, SceneKind.MACRO -> minOf(maxZoom, 1.6f)
            SceneKind.GROUP, SceneKind.LANDSCAPE, SceneKind.ARCHITECTURE -> minOf(maxZoom, 1.15f)
            else -> minOf(maxZoom, 2.4f)
        }
        return adjusted.coerceIn(minZoom, ceiling)
    }

    private data class CoachLine(
        val hint: UiText,
        val why: UiText,
        val cue: SceneCue,
    )

    private fun coach(
        scene: SceneKind,
        recipe: SceneRecipe,
        lens: LensStep?,
        subject: Rect?,
        viewWidth: Float,
        viewHeight: Float,
        horizonDegrees: Float,
    ): CoachLine {
        val lensWhy = UiText(
            when (lens?.role) {
                LensRole.TELE -> R.string.why_tele
                LensRole.ULTRA_WIDE -> R.string.why_ultra_wide
                LensRole.WIDE -> R.string.why_wide
                else -> R.string.why_default
            },
        )
        if (abs(horizonDegrees) > 3.5f) {
            return CoachLine(UiText(R.string.hint_level_first), lensWhy, SceneCue.NONE)
        }
        if (subject != null && viewWidth > 1f && recipe != SceneRecipe.LANDSCAPE) {
            val targetX = viewWidth * recipe.subjectBias
            val dx = subject.center.x - targetX
            if (abs(dx) > viewWidth * 0.08f) {
                val left = dx > 0f
                return CoachLine(
                    UiText(
                        if (left) R.string.hint_subject_third_left else R.string.hint_subject_third_right,
                    ),
                    lensWhy,
                    if (left) SceneCue.MOVE_LEFT else SceneCue.MOVE_RIGHT,
                )
            }
            val fill = subject.height / max(viewHeight, 1f)
            if (scene == SceneKind.PORTRAIT || scene == SceneKind.PET) {
                val target = recipe.fillRatio.coerceAtLeast(0.28f)
                if (fill < target * 0.72f) {
                    return CoachLine(UiText(R.string.hint_subject_small), lensWhy, SceneCue.MOVE_CLOSER)
                }
                if (fill > target * 1.35f) {
                    return CoachLine(UiText(R.string.hint_too_full), lensWhy, SceneCue.MOVE_BACK)
                }
            }
        }
        val ready = UiText(
            when (scene) {
                SceneKind.PORTRAIT, SceneKind.PET -> R.string.hint_portrait_ready
                SceneKind.LANDSCAPE -> R.string.hint_landscape
                SceneKind.ARCHITECTURE -> R.string.hint_architecture
                SceneKind.FOOD -> R.string.hint_food
                SceneKind.GROUP -> R.string.hint_group
                SceneKind.STREET, SceneKind.UNKNOWN -> R.string.hint_street
                SceneKind.MACRO -> R.string.hint_ready_generic
            },
        )
        return CoachLine(ready, lensWhy, SceneCue.NONE)
    }
}

private val SceneKind.displayName: UiText
    get() = UiText(
        when (this) {
            SceneKind.PORTRAIT -> R.string.scene_portrait
            SceneKind.GROUP -> R.string.scene_group
            SceneKind.FOOD -> R.string.scene_food
            SceneKind.LANDSCAPE -> R.string.scene_landscape
            SceneKind.ARCHITECTURE -> R.string.scene_architecture
            SceneKind.STREET -> R.string.scene_street
            SceneKind.PET -> R.string.scene_pet
            SceneKind.MACRO -> R.string.scene_macro
            SceneKind.UNKNOWN -> R.string.scene_unknown
        },
    )

private fun List<LensStep>.nearestByRole(role: LensRole): LensStep? {
    firstOrNull { it.role == role }?.let { return it }
    val order = listOf(LensRole.WIDE, LensRole.TELE, LensRole.ULTRA_WIDE)
    for (candidate in order) {
        firstOrNull { it.role == candidate }?.let { return it }
    }
    return firstOrNull()
}

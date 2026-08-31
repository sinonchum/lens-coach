package com.lenscoach.android.overlay

import androidx.compose.ui.geometry.Rect
import com.lenscoach.android.R
import com.lenscoach.android.ui.UiText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CoachGuide(
    val frame: Rect,
    val subject: Rect?,
    val hint: UiText,
    val aligned: Boolean,
)

object FramingEngine {
    fun suggest(
        viewWidth: Float,
        viewHeight: Float,
        faces: List<Rect>,
        objects: List<Rect> = emptyList(),
        recipe: SceneRecipe?,
        horizonDegrees: Float,
    ): CoachGuide {
        if (viewWidth <= 1f || viewHeight <= 1f) {
            return CoachGuide(Rect.Zero, null, UiText(R.string.hint_open_camera), false)
        }
        if (recipe == null) {
            val full = Rect(0f, 0f, viewWidth, viewHeight)
            return CoachGuide(full, null, UiText(R.string.hint_aim_subject), false)
        }
        val subject = faces.maxByOrNull { it.width * it.height }
            ?: objects.maxByOrNull { it.width * it.height }
        val frame = if (subject != null && recipe != SceneRecipe.LANDSCAPE) {
            frameAroundSubject(subject, viewWidth, viewHeight, recipe)
        } else {
            defaultFrame(viewWidth, viewHeight, recipe)
        }
        val (hint, aligned) = hintFor(subject, frame, recipe, horizonDegrees)
        return CoachGuide(frame, subject, hint, aligned)
    }

    private fun defaultFrame(viewW: Float, viewH: Float, recipe: SceneRecipe): Rect {
        val (width, height) = fitSize(viewW, viewH, recipe.frameAspect)
        val left = (viewW - width) / 2f
        val top = (viewH - height) * recipe.horizonY.coerceIn(0.2f, 0.8f)
        return place(left, top, width, height, viewW, viewH)
    }

    private fun frameAroundSubject(
        subject: Rect,
        viewW: Float,
        viewH: Float,
        recipe: SceneRecipe,
    ): Rect {
        val (width, height) = fitSize(viewW, viewH, recipe.frameAspect)
        val eyeY = subject.top + subject.height * 0.38f
        val top = eyeY - height * recipe.horizonY
        val subjectX = subject.left + subject.width * 0.5f
        val left = subjectX - width * recipe.subjectBias
        return place(left, top, width, height, viewW, viewH)
    }

    private fun fitSize(maxW: Float, maxH: Float, aspect: Float): Pair<Float, Float> {
        val safeAspect = aspect.coerceAtLeast(0.2f)
        var width = maxW.coerceAtLeast(8f)
        var height = width / safeAspect
        if (height > maxH) {
            height = maxH.coerceAtLeast(8f)
            width = height * safeAspect
        }
        return width to height
    }

    private fun place(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        viewW: Float,
        viewH: Float,
    ): Rect {
        val w = width.coerceIn(8f, viewW.coerceAtLeast(8f))
        val h = height.coerceIn(8f, viewH.coerceAtLeast(8f))
        val l = left.safeCoerce(0f, (viewW - w).coerceAtLeast(0f))
        val t = top.safeCoerce(0f, (viewH - h).coerceAtLeast(0f))
        return Rect(l, t, l + w, t + h)
    }

    private fun Float.safeCoerce(lo: Float, hi: Float): Float {
        if (!lo.isFinite() || !hi.isFinite() || !isFinite()) return 0f
        if (hi <= lo) return lo
        return coerceIn(lo, hi)
    }

    private fun hintFor(
        subject: Rect?,
        frame: Rect,
        recipe: SceneRecipe,
        horizonDegrees: Float,
    ): Pair<UiText, Boolean> {
        if (abs(horizonDegrees) > 3.5f) {
            return UiText(R.string.hint_level) to false
        }
        if (recipe == SceneRecipe.LANDSCAPE) {
            return UiText(R.string.hint_landscape) to (abs(horizonDegrees) < 1.6f)
        }
        if (subject == null) {
            return UiText(R.string.hint_find_subject) to false
        }
        val slotX = frame.left + frame.width * recipe.subjectBias
        val dx = subject.center.x - slotX
        val xThreshold = frame.width * 0.08f
        if (abs(dx) > xThreshold) {
            val hint = if (dx > 0f) R.string.hint_subject_third_left else R.string.hint_subject_third_right
            return UiText(hint) to false
        }
        val faceRatio = subject.height / max(frame.height, 1f)
        val target = recipe.fillRatio.coerceAtLeast(0.18f)
        if (faceRatio < target * 0.62f) return UiText(R.string.hint_closer) to false
        if (faceRatio > target * 1.55f) return UiText(R.string.hint_step_back) to false
        val topGap = subject.top - frame.top
        if (recipe == SceneRecipe.PORTRAIT && topGap < frame.height * 0.08f) {
            return UiText(R.string.hint_headroom) to false
        }
        val overlap = intersectionArea(subject, frame) / max(subject.width * subject.height, 1f)
        val aligned = overlap > 0.72f && abs(dx) < xThreshold * 0.7f
        val ready = when (recipe) {
            SceneRecipe.PORTRAIT -> R.string.hint_portrait_ready
            SceneRecipe.STREET -> R.string.hint_street
            SceneRecipe.LANDSCAPE -> R.string.hint_landscape
        }
        return UiText(if (aligned) ready else R.string.hint_align_frame) to aligned
    }

    private fun intersectionArea(a: Rect, b: Rect): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val w = (right - left).coerceAtLeast(0f)
        val h = (bottom - top).coerceAtLeast(0f)
        return w * h
    }
}

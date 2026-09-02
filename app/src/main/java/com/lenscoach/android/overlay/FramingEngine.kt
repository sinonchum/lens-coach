package com.lenscoach.android.overlay

import androidx.compose.ui.geometry.Rect
import com.lenscoach.android.camera.LensRole
import kotlin.math.max
import kotlin.math.min

object FramingEngine {
    fun letterbox(viewW: Float, viewH: Float, recipe: SceneRecipe): Rect {
        return defaultFrame(viewW, viewH, recipe)
    }

    fun compose(viewW: Float, viewH: Float, recipe: SceneRecipe, subject: Rect?): Rect {
        if (subject == null || recipe == SceneRecipe.LANDSCAPE) {
            return defaultFrame(viewW, viewH, recipe)
        }
        return frameAroundSubject(subject, viewW, viewH, recipe)
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
}

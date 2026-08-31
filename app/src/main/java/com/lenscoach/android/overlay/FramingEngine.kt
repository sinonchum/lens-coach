package com.lenscoach.android.overlay

import androidx.compose.ui.geometry.Rect
import com.lenscoach.android.R
import com.lenscoach.android.style.StylePack
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
        style: StylePack,
        horizonDegrees: Float,
    ): CoachGuide {
        if (viewWidth <= 1f || viewHeight <= 1f) {
            return CoachGuide(Rect.Zero, null, UiText(R.string.hint_open_camera), false)
        }
        val subject = faces.maxByOrNull { it.width * it.height }
            ?: objects.maxByOrNull { it.width * it.height }
        val frame = if (subject != null) {
            frameAroundSubject(subject, viewWidth, viewHeight, style)
        } else {
            defaultFrame(viewWidth, viewHeight, style)
        }
        val (hint, aligned) = hintFor(subject, frame, horizonDegrees)
        return CoachGuide(frame, subject, hint, aligned)
    }

    private fun defaultFrame(viewW: Float, viewH: Float, style: StylePack): Rect {
        val (width, height) = fitSize(viewW * 0.78f, viewH * 0.62f, style.frameAspect)
        val bias = style.subjectBias.coerceIn(0.2f, 0.8f)
        val left = (viewW - width) * bias
        val top = (viewH - height) * 0.36f
        return place(left, top, width, height, viewW, viewH)
    }

    private fun frameAroundSubject(
        subject: Rect,
        viewW: Float,
        viewH: Float,
        style: StylePack,
    ): Rect {
        val ratio = style.faceHeightRatio.coerceAtLeast(0.15f)
        val bias = style.subjectBias.coerceIn(0.2f, 0.8f)
        var height = subject.height / ratio
        var width = height * style.frameAspect
        val (fittedW, fittedH) = fitSize(
            width.coerceAtMost(viewW * 0.92f),
            height.coerceAtMost(viewH * 0.76f),
            style.frameAspect,
        )
        width = fittedW
        height = fittedH
        val subjectX = subject.left + subject.width * bias
        val left = subjectX - width * bias
        val top = subject.top - height * style.headroom
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

    private fun hintFor(subject: Rect?, frame: Rect, horizonDegrees: Float): Pair<UiText, Boolean> {
        if (abs(horizonDegrees) > 3.5f) {
            return UiText(R.string.hint_level) to false
        }
        if (subject == null) {
            return UiText(R.string.hint_find_subject) to false
        }
        val slotX = frame.left + frame.width * 0.5f
        val dx = subject.center.x - slotX
        val xThreshold = frame.width * 0.08f
        if (abs(dx) > xThreshold) {
            val hint = if (dx > 0f) R.string.hint_subject_left else R.string.hint_subject_right
            return UiText(hint) to false
        }
        val faceRatio = subject.height / max(frame.height, 1f)
        if (faceRatio < 0.26f) return UiText(R.string.hint_closer) to false
        if (faceRatio > 0.56f) return UiText(R.string.hint_step_back) to false
        val topGap = subject.top - frame.top
        if (topGap < frame.height * 0.08f) return UiText(R.string.hint_headroom) to false
        val overlap = intersectionArea(subject, frame) / max(subject.width * subject.height, 1f)
        val aligned = overlap > 0.72f && abs(dx) < xThreshold * 0.7f
        return UiText(if (aligned) R.string.hint_ready else R.string.hint_align_frame) to aligned
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

package com.lenscoach.android.capture

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

object CaptureCrop {
    fun cropToGuide(
        bitmap: Bitmap,
        guide: Rect,
        viewWidth: Float,
        viewHeight: Float,
        mirrored: Boolean,
    ): Bitmap {
        if (viewWidth < 8f || viewHeight < 8f || guide.width < 8f || guide.height < 8f) {
            return bitmap
        }
        val imageW = bitmap.width.toFloat()
        val imageH = bitmap.height.toFloat()
        val scale = max(viewWidth / imageW, viewHeight / imageH)
        val shownW = imageW * scale
        val shownH = imageH * scale
        val originX = (viewWidth - shownW) / 2f
        val originY = (viewHeight - shownH) / 2f
        fun toImageX(previewX: Float): Float {
            val x = (previewX - originX) / scale
            return if (mirrored) imageW - x else x
        }
        fun toImageY(previewY: Float): Float = (previewY - originY) / scale
        val x0 = toImageX(guide.left)
        val x1 = toImageX(guide.right)
        val left = min(x0, x1).coerceIn(0f, imageW - 1f)
        val right = max(x0, x1).coerceIn(left + 1f, imageW)
        val top = toImageY(guide.top).coerceIn(0f, imageH - 1f)
        val bottom = toImageY(guide.bottom).coerceIn(top + 1f, imageH)
        val x = left.toInt()
        val y = top.toInt()
        val width = (right - left).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - x)
        val height = (bottom - top).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - y)
        if (width >= bitmap.width - 2 && height >= bitmap.height - 2) return bitmap
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
}

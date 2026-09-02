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
        val rect = guideRect(
            guide = guide,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = bitmap.width.toFloat(),
            imageHeight = bitmap.height.toFloat(),
            mirrored = mirrored,
        ) ?: return bitmap
        return Bitmap.createBitmap(
            bitmap,
            rect.left.toInt(),
            rect.top.toInt(),
            rect.width.toInt(),
            rect.height.toInt(),
        )
    }

    /**
     * Maps the on-screen guide rect into sensor image coordinates, assuming the
     * preview is drawn FILL_CENTER. Returns null when there is nothing meaningful
     * to crop (degenerate input, or the guide already covers the whole image).
     */
    fun guideRect(
        guide: Rect,
        viewWidth: Float,
        viewHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        mirrored: Boolean,
    ): Rect? {
        if (viewWidth < 8f || viewHeight < 8f || guide.width < 8f || guide.height < 8f) {
            return null
        }
        val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
        val shownW = imageWidth * scale
        val shownH = imageHeight * scale
        val originX = (viewWidth - shownW) / 2f
        val originY = (viewHeight - shownH) / 2f
        fun toImageX(previewX: Float): Float {
            val x = (previewX - originX) / scale
            return if (mirrored) imageWidth - x else x
        }
        fun toImageY(previewY: Float): Float = (previewY - originY) / scale
        val x0 = toImageX(guide.left)
        val x1 = toImageX(guide.right)
        val left = min(x0, x1).coerceIn(0f, imageWidth - 1f)
        val right = max(x0, x1).coerceIn(left + 1f, imageWidth)
        val top = toImageY(guide.top).coerceIn(0f, imageHeight - 1f)
        val bottom = toImageY(guide.bottom).coerceIn(top + 1f, imageHeight)
        val x = left.toInt()
        val y = top.toInt()
        val width = (right - left).toInt().coerceAtLeast(1).coerceAtMost(imageWidth.toInt() - x)
        val height = (bottom - top).toInt().coerceAtLeast(1).coerceAtMost(imageHeight.toInt() - y)
        if (width >= imageWidth - 2 && height >= imageHeight - 2) return null
        return Rect(
            left = x.toFloat(),
            top = y.toFloat(),
            right = (x + width).toFloat(),
            bottom = (y + height).toFloat(),
        )
    }
}

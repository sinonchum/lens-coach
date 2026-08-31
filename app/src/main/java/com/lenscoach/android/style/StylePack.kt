package com.lenscoach.android.style

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.annotation.StringRes
import com.lenscoach.android.R

enum class StylePack(
    @StringRes val labelRes: Int,
    val frameAspect: Float,
    val subjectBias: Float,
    val faceHeightRatio: Float,
    val headroom: Float,
) {
    NIKKEI(
        labelRes = R.string.style_nikkei,
        frameAspect = 4f / 5f,
        subjectBias = 0.38f,
        faceHeightRatio = 0.36f,
        headroom = 0.22f,
    ),
    CINEMA(
        labelRes = R.string.style_cinema,
        frameAspect = 2.39f / 1f,
        subjectBias = 0.50f,
        faceHeightRatio = 0.42f,
        headroom = 0.18f,
    ),
    DOCUMENTARY(
        labelRes = R.string.style_documentary,
        frameAspect = 3f / 2f,
        subjectBias = 0.50f,
        faceHeightRatio = 0.40f,
        headroom = 0.16f,
    );

    fun grade(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix())
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun colorMatrix(): ColorMatrix {
        val contrast: Float
        val brightness: Float
        val saturation: Float
        val warm: Float
        when (this) {
            NIKKEI -> {
                contrast = 0.86f
                brightness = 18f
                saturation = 0.72f
                warm = 1.06f
            }
            CINEMA -> {
                contrast = 1.18f
                brightness = -8f
                saturation = 0.88f
                warm = 1.04f
            }
            DOCUMENTARY -> {
                contrast = 1.06f
                brightness = 2f
                saturation = 0.96f
                warm = 1.0f
            }
        }
        val scale = ColorMatrix(
            floatArrayOf(
                contrast * warm, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast / warm, 0f, brightness,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val sat = ColorMatrix().apply { setSaturation(saturation) }
        sat.postConcat(scale)
        if (this == CINEMA) {
            val tealOrange = ColorMatrix(
                floatArrayOf(
                    1.05f, 0f, 0.04f, 0f, 6f,
                    0f, 0.98f, 0.02f, 0f, 0f,
                    0.02f, 0.04f, 1.08f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            sat.postConcat(tealOrange)
        }
        return sat
    }
}

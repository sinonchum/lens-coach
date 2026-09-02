package com.lenscoach.android.style

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.lenscoach.android.R

enum class FilterLook(
    @StringRes val labelRes: Int,
    val swatch: Color,
) {
    NEUTRAL(R.string.filter_neutral, Color(0xFFDADDE1)),
    SOFT(R.string.filter_soft, Color(0xFFE9CDB0)),
    CINEMA(R.string.filter_cinema, Color(0xFF2F5D66)),
    DOCUMENTARY(R.string.filter_documentary, Color(0xFF9C8A63)),
    HIGH_CONTRAST(R.string.filter_high_contrast, Color(0xFF0F1113));

    fun grade(source: Bitmap): Bitmap {
        if (this == NEUTRAL) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix())
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    fun colorMatrix(): ColorMatrix {
        val contrast: Float
        val brightness: Float
        val saturation: Float
        val warm: Float
        when (this) {
            NEUTRAL -> {
                contrast = 1f
                brightness = 0f
                saturation = 1f
                warm = 1f
            }
            SOFT -> {
                contrast = 0.88f
                brightness = 12f
                saturation = 0.82f
                warm = 1.02f
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
            HIGH_CONTRAST -> {
                contrast = 1.28f
                brightness = -6f
                saturation = 0.92f
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
            sat.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.05f, 0f, 0.04f, 0f, 6f,
                        0f, 0.98f, 0.02f, 0f, 0f,
                        0.02f, 0.04f, 1.08f, 0f, 8f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        return sat
    }
}

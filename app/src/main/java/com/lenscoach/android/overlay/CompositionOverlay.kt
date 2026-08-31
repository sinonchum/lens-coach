package com.lenscoach.android.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

@Composable
fun CompositionOverlay(
    frame: Rect,
    faces: List<Rect>,
    objects: List<Rect> = emptyList(),
    aligned: Boolean,
    focusPoint: Offset?,
    horizonDegrees: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val thirds = Color.White.copy(alpha = 0.18f)
        drawLine(thirds, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.2f)
        drawLine(thirds, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.2f)
        drawLine(thirds, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.2f)
        drawLine(thirds, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.2f)

        faces.forEach { face ->
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = face.topLeft,
                size = Size(face.width, face.height),
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 1.5f),
            )
        }
        objects.forEach { box ->
            drawRoundRect(
                color = Color(0xFFF4E6C3).copy(alpha = 0.28f),
                topLeft = box.topLeft,
                size = Size(box.width, box.height),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 1.2f),
            )
        }

        if (frame.width > 8f && frame.height > 8f) {
            val color = if (aligned) Color(0xFFC8F59B) else Color(0xFFF4E6C3)
            val stroke = if (aligned) 4.5f else 3f
            val arm = min(frame.width, frame.height) * 0.16f
            drawCornerBrackets(frame, color, stroke, arm)
            drawRect(
                color = color.copy(alpha = 0.12f),
                topLeft = frame.topLeft,
                size = Size(frame.width, frame.height),
                style = Stroke(width = 1.2f),
            )
        }

        if (kotlin.math.abs(horizonDegrees) > 0.8f) {
            rotate(horizonDegrees, pivot = center) {
                val y = size.height * 0.5f
                drawLine(
                    color = Color(0xFFFF8A7A),
                    start = Offset(size.width * 0.18f, y),
                    end = Offset(size.width * 0.82f, y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }

        focusPoint?.let { point ->
            drawCircle(
                color = Color(0xFFF4E6C3),
                radius = 42f,
                center = point,
                style = Stroke(width = 2.4f),
            )
            drawCircle(
                color = Color(0xFFF4E6C3).copy(alpha = 0.5f),
                radius = 6f,
                center = point,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerBrackets(
    frame: Rect,
    color: Color,
    stroke: Float,
    arm: Float,
) {
    val cap = StrokeCap.Square
    val corners = listOf(
        Offset(frame.left, frame.top) to listOf(Offset(1f, 0f), Offset(0f, 1f)),
        Offset(frame.right, frame.top) to listOf(Offset(-1f, 0f), Offset(0f, 1f)),
        Offset(frame.left, frame.bottom) to listOf(Offset(1f, 0f), Offset(0f, -1f)),
        Offset(frame.right, frame.bottom) to listOf(Offset(-1f, 0f), Offset(0f, -1f)),
    )
    corners.forEach { (origin, dirs) ->
        dirs.forEach { dir ->
            drawLine(
                color = color,
                start = origin,
                end = Offset(origin.x + dir.x * arm, origin.y + dir.y * arm),
                strokeWidth = stroke,
                cap = cap,
            )
        }
    }
}

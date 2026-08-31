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
import com.lenscoach.android.ui.Viewfinder
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
        val hasCrop = frame.width > 8f &&
            frame.height > 8f &&
            (frame.width < size.width - 8f || frame.height < size.height - 8f)
        if (hasCrop) {
            val dim = Color.Black.copy(alpha = 0.58f)
            drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, frame.top.coerceAtLeast(0f)))
            drawRect(
                dim,
                topLeft = Offset(0f, frame.bottom),
                size = Size(size.width, (size.height - frame.bottom).coerceAtLeast(0f)),
            )
            drawRect(
                dim,
                topLeft = Offset(0f, frame.top),
                size = Size(frame.left.coerceAtLeast(0f), frame.height),
            )
            drawRect(
                dim,
                topLeft = Offset(frame.right, frame.top),
                size = Size((size.width - frame.right).coerceAtLeast(0f), frame.height),
            )
        }

        val thirds = Color.White.copy(alpha = 0.16f)
        val thirdsRect = if (hasCrop) frame else Rect(0f, 0f, size.width, size.height)
        drawLine(
            thirds,
            Offset(thirdsRect.left + thirdsRect.width / 3f, thirdsRect.top),
            Offset(thirdsRect.left + thirdsRect.width / 3f, thirdsRect.bottom),
            1.2f,
        )
        drawLine(
            thirds,
            Offset(thirdsRect.left + thirdsRect.width * 2f / 3f, thirdsRect.top),
            Offset(thirdsRect.left + thirdsRect.width * 2f / 3f, thirdsRect.bottom),
            1.2f,
        )
        drawLine(
            thirds,
            Offset(thirdsRect.left, thirdsRect.top + thirdsRect.height / 3f),
            Offset(thirdsRect.right, thirdsRect.top + thirdsRect.height / 3f),
            1.2f,
        )
        drawLine(
            thirds,
            Offset(thirdsRect.left, thirdsRect.top + thirdsRect.height * 2f / 3f),
            Offset(thirdsRect.right, thirdsRect.top + thirdsRect.height * 2f / 3f),
            1.2f,
        )

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
                color = Viewfinder.Accent.copy(alpha = 0.28f),
                topLeft = box.topLeft,
                size = Size(box.width, box.height),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 1.2f),
            )
        }

        if (hasCrop) {
            val color = if (aligned) Viewfinder.Accent else Color.White
            val stroke = if (aligned) 4.5f else 3f
            val arm = min(frame.width, frame.height) * 0.16f
            drawCornerBrackets(frame, color, stroke, arm)
            drawRect(
                color = color.copy(alpha = 0.18f),
                topLeft = frame.topLeft,
                size = Size(frame.width, frame.height),
                style = Stroke(width = 1.2f),
            )
        }

        if (kotlin.math.abs(horizonDegrees) > 0.8f) {
            rotate(horizonDegrees, pivot = center) {
                val y = size.height * 0.5f
                drawLine(
                    color = Viewfinder.Hazard,
                    start = Offset(size.width * 0.18f, y),
                    end = Offset(size.width * 0.82f, y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }

        focusPoint?.let { point ->
            drawCircle(
                color = Viewfinder.Accent,
                radius = 42f,
                center = point,
                style = Stroke(width = 2.4f),
            )
            drawCircle(
                color = Viewfinder.Accent.copy(alpha = 0.5f),
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

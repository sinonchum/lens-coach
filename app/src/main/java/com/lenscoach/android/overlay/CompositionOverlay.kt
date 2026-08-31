package com.lenscoach.android.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

@Composable
fun CompositionOverlay(
    frame: Rect,
    faces: List<Rect>,
    objects: List<Rect> = emptyList(),
    aligned: Boolean,
    lockEpoch: Long = 0L,
    focusPoint: Offset?,
    horizonDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val displayed = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var booted by remember { mutableStateOf(false) }
    val bracket = remember { Animatable(1f) }
    val pulse = remember { Animatable(1f) }
    val scan = remember { Animatable(0f) }
    val scanAlpha = remember { Animatable(0f) }
    val frameColor by animateColorAsState(
        targetValue = if (aligned) Viewfinder.Accent else Color.White,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "frameColor",
    )
    val dimAlpha by animateFloatAsState(
        targetValue = if (frame.width > 8f) 0.58f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "dimAlpha",
    )

    LaunchedEffect(frame) {
        if (frame.width < 8f || frame.height < 8f) return@LaunchedEffect
        if (!booted) {
            displayed.snapTo(frame)
            booted = true
        } else {
            displayed.animateTo(
                frame,
                spring(
                    dampingRatio = 0.86f,
                    stiffness = 380f,
                    visibilityThreshold = Rect(0.6f, 0.6f, 0.6f, 0.6f),
                ),
            )
        }
    }

    LaunchedEffect(lockEpoch) {
        if (lockEpoch == 0L) return@LaunchedEffect
        bracket.snapTo(0.12f)
        pulse.snapTo(1f)
        scan.snapTo(0f)
        scanAlpha.snapTo(0.9f)
        launch { bracket.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
        launch {
            pulse.animateTo(1.03f, tween(150, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
        launch {
            scan.animateTo(1f, tween(360, easing = LinearEasing))
            scanAlpha.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
        }
    }

    val crop = displayed.value
    val bracketProgress = bracket.value
    val pulseScale = pulse.value
    val scanT = scan.value
    val scanA = scanAlpha.value
    Canvas(modifier = modifier.fillMaxSize()) {
        val hasCrop = crop.width > 8f &&
            crop.height > 8f &&
            (crop.width < size.width - 8f || crop.height < size.height - 8f)
        if (hasCrop && dimAlpha > 0.01f) {
            val dim = Color.Black.copy(alpha = dimAlpha)
            drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, crop.top.coerceAtLeast(0f)))
            drawRect(
                dim,
                topLeft = Offset(0f, crop.bottom),
                size = Size(size.width, (size.height - crop.bottom).coerceAtLeast(0f)),
            )
            drawRect(
                dim,
                topLeft = Offset(0f, crop.top),
                size = Size(crop.left.coerceAtLeast(0f), crop.height),
            )
            drawRect(
                dim,
                topLeft = Offset(crop.right, crop.top),
                size = Size((size.width - crop.right).coerceAtLeast(0f), crop.height),
            )
        }

        val thirds = Color.White.copy(alpha = 0.14f)
        val thirdsRect = if (hasCrop) crop else Rect(0f, 0f, size.width, size.height)
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
                color = Color.White.copy(alpha = 0.18f),
                topLeft = face.topLeft,
                size = Size(face.width, face.height),
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 1.4f),
            )
        }
        objects.forEach { box ->
            drawRoundRect(
                color = Viewfinder.Accent.copy(alpha = 0.16f),
                topLeft = box.topLeft,
                size = Size(box.width, box.height),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 1.1f),
            )
        }

        if (hasCrop) {
            val cx = crop.center.x
            val cy = crop.center.y
            val scale = pulseScale
            val drawn = Rect(
                cx - crop.width * 0.5f * scale,
                cy - crop.height * 0.5f * scale,
                cx + crop.width * 0.5f * scale,
                cy + crop.height * 0.5f * scale,
            )
            val stroke = if (aligned) 4.4f else 3f
            val arm = min(drawn.width, drawn.height) * 0.16f * bracketProgress
            drawCornerBrackets(drawn, frameColor, stroke, arm)
            drawRect(
                color = frameColor.copy(alpha = 0.16f),
                topLeft = drawn.topLeft,
                size = Size(drawn.width, drawn.height),
                style = Stroke(width = 1.2f),
            )
            if (scanA > 0.02f) {
                val y = drawn.top + drawn.height * scanT
                drawLine(
                    color = frameColor.copy(alpha = scanA),
                    start = Offset(drawn.left + 8f, y),
                    end = Offset(drawn.right - 8f, y),
                    strokeWidth = 1.6f,
                    cap = StrokeCap.Round,
                )
            }
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
                color = Viewfinder.Accent.copy(alpha = 0.95f),
                radius = 38f,
                center = point,
                style = Stroke(width = 2.2f),
            )
            drawCircle(
                color = Viewfinder.Accent.copy(alpha = 0.45f),
                radius = 5f,
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

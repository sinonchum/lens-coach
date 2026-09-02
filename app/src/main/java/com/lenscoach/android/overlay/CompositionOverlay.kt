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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lenscoach.android.ui.Viewfinder
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * Canvas drawing works in pixels; keep every stroke in dp so the overlay looks
 * the same across densities.
 */
private data class OverlayMetrics(
    val thirdsStroke: Float,
    val boxStroke: Float,
    val frameStroke: Float,
    val frameStrokeAligned: Float,
    val scanStroke: Float,
    val horizonStroke: Float,
    val focusRadius: Float,
    val focusStroke: Float,
    val focusDot: Float,
    val faceCorner: CornerRadius,
)

@Composable
fun CompositionOverlay(
    frame: Rect,
    faces: List<Rect>,
    objects: List<Rect> = emptyList(),
    aligned: Boolean,
    frameLocked: Boolean = false,
    lockEpoch: Long = 0L,
    focusPoint: Offset?,
    horizonDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val metrics = with(LocalDensity.current) {
        OverlayMetrics(
            thirdsStroke = 1.dp.toPx(),
            boxStroke = 1.dp.toPx(),
            frameStroke = 1.6.dp.toPx(),
            frameStrokeAligned = 2.4.dp.toPx(),
            scanStroke = 1.dp.toPx(),
            horizonStroke = 1.6.dp.toPx(),
            focusRadius = 16.dp.toPx(),
            focusStroke = 2.dp.toPx(),
            focusDot = 2.5.dp.toPx(),
            faceCorner = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
    }
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

    LaunchedEffect(frame, frameLocked) {
        if (frame.width < 8f || frame.height < 8f) return@LaunchedEffect
        if (!booted || frameLocked) {
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

    LaunchedEffect(lockEpoch, frameLocked) {
        if (!frameLocked || lockEpoch == 0L) return@LaunchedEffect
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
            metrics.thirdsStroke,
        )
        drawLine(
            thirds,
            Offset(thirdsRect.left + thirdsRect.width * 2f / 3f, thirdsRect.top),
            Offset(thirdsRect.left + thirdsRect.width * 2f / 3f, thirdsRect.bottom),
            metrics.thirdsStroke,
        )
        drawLine(
            thirds,
            Offset(thirdsRect.left, thirdsRect.top + thirdsRect.height / 3f),
            Offset(thirdsRect.right, thirdsRect.top + thirdsRect.height / 3f),
            metrics.thirdsStroke,
        )
        drawLine(
            thirds,
            Offset(thirdsRect.left, thirdsRect.top + thirdsRect.height * 2f / 3f),
            Offset(thirdsRect.right, thirdsRect.top + thirdsRect.height * 2f / 3f),
            metrics.thirdsStroke,
        )

        if (!frameLocked) {
            faces.forEach { face ->
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = face.topLeft,
                    size = Size(face.width, face.height),
                    cornerRadius = metrics.faceCorner,
                    style = Stroke(width = metrics.boxStroke),
                )
            }
            objects.forEach { box ->
                drawRoundRect(
                    color = Viewfinder.Accent.copy(alpha = 0.16f),
                    topLeft = box.topLeft,
                    size = Size(box.width, box.height),
                    cornerRadius = metrics.faceCorner,
                    style = Stroke(width = metrics.boxStroke),
                )
            }
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
            val stroke = if (aligned) metrics.frameStrokeAligned else metrics.frameStroke
            val arm = min(drawn.width, drawn.height) * 0.16f * bracketProgress
            drawCornerBrackets(drawn, frameColor, stroke, arm)
            drawRect(
                color = frameColor.copy(alpha = 0.16f),
                topLeft = drawn.topLeft,
                size = Size(drawn.width, drawn.height),
                style = Stroke(width = metrics.thirdsStroke),
            )
            if (scanA > 0.02f) {
                val y = drawn.top + drawn.height * scanT
                drawLine(
                    color = frameColor.copy(alpha = scanA),
                    start = Offset(drawn.left + 8f, y),
                    end = Offset(drawn.right - 8f, y),
                    strokeWidth = metrics.scanStroke,
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
                    strokeWidth = metrics.horizonStroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        focusPoint?.let { point ->
            drawCircle(
                color = Viewfinder.Accent.copy(alpha = 0.95f),
                radius = metrics.focusRadius,
                center = point,
                style = Stroke(width = metrics.focusStroke),
            )
            drawCircle(
                color = Viewfinder.Accent.copy(alpha = 0.45f),
                radius = metrics.focusDot,
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

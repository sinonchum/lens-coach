package com.lenscoach.android.camera

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import com.lenscoach.android.R
import com.lenscoach.android.overlay.FramingEngine
import com.lenscoach.android.overlay.SceneRecipe
import com.lenscoach.android.style.FilterLook
import com.lenscoach.android.ui.UiText
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val filter: FilterLook = FilterLook.NEUTRAL,
    val scene: SceneKind = SceneKind.UNKNOWN,
    val faces: List<Rect> = emptyList(),
    val objects: List<Rect> = emptyList(),
    val frame: Rect = Rect.Zero,
    val hint: UiText = UiText(R.string.hint_aim_subject),
    val why: UiText? = null,
    val sceneLabel: UiText? = null,
    val aligned: Boolean = false,
    val focusPoint: Offset? = null,
    val horizonDegrees: Float = 0f,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    val capturing: Boolean = false,
    val reviewBitmap: Bitmap? = null,
    val saveMessage: UiText? = null,
    val aiEnabled: Boolean = true,
    val inventory: CameraInventory? = null,
    val lensSteps: List<LensStep> = emptyList(),
    val activeLensId: String? = null,
    val suggestedLensId: String? = null,
    val currentZoom: Float = 1f,
    val requestedZoom: Float? = null,
    val requestedFocus: Offset? = null,
    val zoomToken: Long = 0L,
    val focusToken: Long = 0L,
)

class CameraViewModel : ViewModel() {
    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state

    private var lastDecideAt = 0L
    private var stableLensId: String? = null
    private var stableFrames = 0
    private var lastFocusAt = 0L
    private var lastFocusPoint: Offset? = null
    private var lastAppliedZoom = 1f
    private var userLensUntil = 0L
    private var latestLabels: List<SceneLabel> = emptyList()
    private var latestObjects: List<SceneObject> = emptyList()

    fun loadInventory(inventory: CameraInventory) {
        _state.update {
            it.copy(
                inventory = inventory,
                lensSteps = inventory.backSteps,
                activeLensId = inventory.backSteps.nearest(it.currentZoom)?.id,
            )
        }
    }

    fun onLiveZoom(zoom: Float, minZoom: Float, maxZoom: Float) {
        val current = _state.value
        if (current.lensFacing != CameraSelector.LENS_FACING_BACK) {
            _state.update { it.copy(currentZoom = zoom) }
            return
        }
        val steps = LensInventory.stepsForZoomRange(minZoom, maxZoom, current.lensSteps)
        val active = steps.nearest(zoom)
        _state.update {
            it.copy(
                currentZoom = zoom,
                lensSteps = if (steps.isNotEmpty()) steps else it.lensSteps,
                activeLensId = active?.id ?: it.activeLensId,
            )
        }
    }

    fun setFilter(filter: FilterLook) {
        _state.update { it.copy(filter = filter) }
    }

    fun toggleAi() {
        val enable = !_state.value.aiEnabled
        if (enable) userLensUntil = 0L
        _state.update { it.copy(aiEnabled = enable) }
        lastDecideAt = 0L
        recompute()
    }

    fun selectLens(step: LensStep) {
        userLensUntil = SystemClock.uptimeMillis() + 15_000L
        lastAppliedZoom = step.zoom
        _state.update {
            it.copy(
                activeLensId = step.id,
                requestedZoom = step.zoom,
                zoomToken = it.zoomToken + 1,
            )
        }
    }

    fun toggleFacing() {
        userLensUntil = 0L
        _state.update {
            val next = if (it.lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            it.copy(
                lensFacing = next,
                faces = emptyList(),
                objects = emptyList(),
                aligned = false,
                requestedZoom = if (next == CameraSelector.LENS_FACING_FRONT) 1f else null,
                zoomToken = it.zoomToken + 1,
            )
        }
        recompute()
    }

    fun cycleFlash() {
        _state.update {
            val next = when (it.flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
            it.copy(flashMode = next)
        }
    }

    fun onPreviewSize(width: Float, height: Float) {
        if (width == _state.value.viewWidth && height == _state.value.viewHeight) return
        _state.update { it.copy(viewWidth = width, viewHeight = height) }
        recompute()
    }

    fun onHorizon(degrees: Float) {
        val snapped = if (abs(degrees) < 0.8f) 0f else degrees
        if (abs(snapped - _state.value.horizonDegrees) < 0.4f) return
        _state.update { it.copy(horizonDegrees = snapped) }
        recompute()
    }

    fun onScene(
        faces: List<Rect>,
        objects: List<SceneObject>,
        labels: List<SceneLabel>,
    ) {
        latestLabels = labels
        latestObjects = objects
        _state.update {
            it.copy(
                faces = faces,
                objects = objects.map { obj -> obj.box },
            )
        }
        recompute()
        maybeDirect()
    }

    fun showFocus(point: Offset) {
        _state.update { it.copy(focusPoint = point) }
    }

    fun clearFocus() {
        _state.update { it.copy(focusPoint = null) }
    }

    fun consumeActuation() {
        _state.update { it.copy(requestedFocus = null) }
    }

    fun setCapturing(capturing: Boolean) {
        _state.update { it.copy(capturing = capturing) }
    }

    fun showReview(bitmap: Bitmap) {
        _state.value.reviewBitmap?.takeUnless { it.isRecycled }?.recycle()
        _state.update { it.copy(reviewBitmap = bitmap, capturing = false, saveMessage = null) }
    }

    fun discardReview() {
        _state.value.reviewBitmap?.takeUnless { it.isRecycled }?.recycle()
        _state.update { it.copy(reviewBitmap = null, capturing = false, saveMessage = null) }
    }

    fun onSaved(ok: Boolean) {
        if (ok) {
            _state.value.reviewBitmap?.takeUnless { it.isRecycled }?.recycle()
            _state.update { it.copy(reviewBitmap = null, saveMessage = UiText(R.string.saved_ok)) }
        } else {
            _state.update { it.copy(saveMessage = UiText(R.string.save_failed)) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(saveMessage = null) }
    }

    private fun maybeDirect() {
        val now = SystemClock.uptimeMillis()
        if (now - lastDecideAt < 240L) return
        lastDecideAt = now
        val current = _state.value
        if (current.reviewBitmap != null || current.capturing) return
        val front = current.lensFacing == CameraSelector.LENS_FACING_FRONT
        val steps = current.lensSteps
        val decision = SceneDirector.decide(
            steps = steps,
            currentZoom = current.currentZoom,
            viewWidth = current.viewWidth,
            viewHeight = current.viewHeight,
            faces = current.faces,
            objects = latestObjects,
            labels = latestLabels,
            horizonDegrees = current.horizonDegrees,
            front = front,
        )
        val suggestedId = decision.lens?.id
        if (suggestedId == stableLensId) {
            stableFrames += 1
        } else {
            stableLensId = suggestedId
            stableFrames = 1
        }
        val userLocked = now < userLensUntil
        val canAutoLens = current.aiEnabled && !userLocked && !front && stableFrames >= 5
        var zoomToken = current.zoomToken
        var requestedZoom = current.requestedZoom
        if (canAutoLens && decision.lens != null) {
            val target = decision.zoom
            if (abs(target - lastAppliedZoom) >= 0.16f) {
                lastAppliedZoom = target
                requestedZoom = target
                zoomToken += 1
            }
        }
        var focusToken = current.focusToken
        var requestedFocus = current.requestedFocus
        val focus = decision.focus
        if (current.aiEnabled && focus != null && now - lastFocusAt > 1300L) {
            val moved = lastFocusPoint?.let { hypot(it.x - focus.x, it.y - focus.y) } ?: 999f
            if (moved > 46f) {
                lastFocusAt = now
                lastFocusPoint = focus
                requestedFocus = focus
                focusToken += 1
            }
        }
        _state.update {
            it.copy(
                scene = decision.scene,
                sceneLabel = decision.sceneLabel,
                why = decision.why,
                hint = if (current.aiEnabled) decision.hint else it.hint,
                suggestedLensId = suggestedId,
                activeLensId = if (canAutoLens) suggestedId else it.activeLensId,
                requestedZoom = requestedZoom,
                zoomToken = zoomToken,
                requestedFocus = requestedFocus,
                focusToken = focusToken,
                focusPoint = requestedFocus ?: it.focusPoint,
            )
        }
        recompute()
    }

    private fun recompute() {
        val current = _state.value
        val recipe = if (current.aiEnabled) SceneRecipe.from(current.scene) else null
        val guide = runCatching {
            FramingEngine.suggest(
                viewWidth = current.viewWidth,
                viewHeight = current.viewHeight,
                faces = current.faces,
                objects = current.objects,
                recipe = recipe,
                horizonDegrees = current.horizonDegrees,
            )
        }.getOrElse {
            FramingEngine.suggest(
                viewWidth = current.viewWidth,
                viewHeight = current.viewHeight,
                faces = emptyList(),
                objects = emptyList(),
                recipe = recipe,
                horizonDegrees = 0f,
            )
        }
        _state.update {
            it.copy(
                frame = guide.frame,
                aligned = guide.aligned,
                hint = if (it.aiEnabled && it.sceneLabel != null) it.hint else guide.hint,
            )
        }
    }

    override fun onCleared() {
        _state.value.reviewBitmap?.takeUnless { it.isRecycled }?.recycle()
        super.onCleared()
    }
}

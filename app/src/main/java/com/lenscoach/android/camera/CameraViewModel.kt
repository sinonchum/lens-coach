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
    val lockEpoch: Long = 0L,
    val lensSwitchEpoch: Long = 0L,
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
    private var pendingScene: SceneKind = SceneKind.UNKNOWN
    private var sceneStreak = 0
    private var alignedStreak = 0
    private var unalignedStreak = 0
    private var lastHintAt = 0L

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
        sceneStreak = 0
        alignedStreak = 0
        unalignedStreak = 0
        _state.update {
            it.copy(
                aiEnabled = enable,
                lockEpoch = if (enable) it.lockEpoch + 1 else it.lockEpoch,
            )
        }
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
        sceneStreak = 0
        alignedStreak = 0
        unalignedStreak = 0
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
        if (decision.scene == pendingScene) {
            sceneStreak += 1
        } else {
            pendingScene = decision.scene
            sceneStreak = 1
        }
        val holdNeeded = if (current.scene == SceneKind.UNKNOWN) 2 else 3
        val committedScene = if (sceneStreak >= holdNeeded) pendingScene else current.scene
        val sceneChanged = committedScene != current.scene
        val recipeChanged = SceneRecipe.from(committedScene) != SceneRecipe.from(current.scene)
        val userLocked = now < userLensUntil
        val canAutoLens = current.aiEnabled && !userLocked && !front && stableFrames >= 5
        var zoomToken = current.zoomToken
        var requestedZoom = current.requestedZoom
        var lensSwitchEpoch = current.lensSwitchEpoch
        val previousActive = current.activeLensId
        if (canAutoLens && decision.lens != null) {
            val target = decision.zoom
            if (abs(target - lastAppliedZoom) >= 0.16f) {
                lastAppliedZoom = target
                requestedZoom = target
                zoomToken += 1
                if (decision.lens.id != previousActive) {
                    lensSwitchEpoch += 1
                }
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
        val refreshHint = sceneChanged || now - lastHintAt > 720L
        if (refreshHint) lastHintAt = now
        var lockEpoch = current.lockEpoch
        if (recipeChanged) lockEpoch += 1
        _state.update {
            it.copy(
                scene = committedScene,
                sceneLabel = if (sceneStreak >= holdNeeded) decision.sceneLabel else it.sceneLabel,
                why = if (sceneStreak >= holdNeeded) decision.why else it.why,
                hint = if (current.aiEnabled && refreshHint) decision.hint else it.hint,
                suggestedLensId = suggestedId,
                activeLensId = if (canAutoLens) suggestedId else it.activeLensId,
                requestedZoom = requestedZoom,
                zoomToken = zoomToken,
                requestedFocus = requestedFocus,
                focusToken = focusToken,
                focusPoint = requestedFocus ?: it.focusPoint,
                lockEpoch = lockEpoch,
                lensSwitchEpoch = lensSwitchEpoch,
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
        val nextFrame = if (
            current.frame.width < 8f ||
            guide.frame.aspectShifted(current.frame) ||
            guide.frame.movedEnough(current.frame)
        ) {
            guide.frame
        } else {
            current.frame
        }
        if (guide.aligned) {
            alignedStreak += 1
            unalignedStreak = 0
        } else {
            unalignedStreak += 1
            alignedStreak = 0
        }
        val nextAligned = when {
            current.aligned && unalignedStreak < 4 -> true
            !current.aligned && alignedStreak >= 3 -> true
            else -> guide.aligned && alignedStreak >= 3
        }
        val acquired = nextAligned && !current.aligned
        _state.update {
            it.copy(
                frame = nextFrame,
                aligned = nextAligned,
                hint = if (it.aiEnabled && it.sceneLabel != null) it.hint else guide.hint,
                lockEpoch = if (acquired) it.lockEpoch + 1 else it.lockEpoch,
            )
        }
    }

    override fun onCleared() {
        _state.value.reviewBitmap?.takeUnless { it.isRecycled }?.recycle()
        super.onCleared()
    }
}

private fun Rect.movedEnough(other: Rect): Boolean {
    val pos = hypot(left - other.left, top - other.top)
    val size = hypot(width - other.width, height - other.height)
    return pos > 22f || size > 28f
}

private fun Rect.aspectShifted(other: Rect): Boolean {
    val a = width / height.coerceAtLeast(1f)
    val b = other.width / other.height.coerceAtLeast(1f)
    return abs(a - b) > 0.08f
}

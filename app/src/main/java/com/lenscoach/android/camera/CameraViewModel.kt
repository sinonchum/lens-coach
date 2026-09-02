package com.lenscoach.android.camera

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
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

data class SavedShot(val uri: Uri, val token: Long)

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
    val cue: SceneCue = SceneCue.NONE,
    val cueSubject: Rect? = null,
    val focusPoint: Offset? = null,
    val horizonDegrees: Float = 0f,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    val capturing: Boolean = false,
    val saving: Boolean = false,
    val reviewBitmap: Bitmap? = null,
    val savedShot: SavedShot? = null,
    val latestUri: Uri? = null,
    val thumbnail: Bitmap? = null,
    val notice: UiText? = null,
    val noticeToken: Long = 0L,
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
    val frameLocked: Boolean = false,
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(
        CameraUiState(
            flashMode = prefs.getInt(KEY_FLASH, ImageCapture.FLASH_MODE_OFF).coerceIn(0, 2),
            filter = prefs.getString(KEY_FILTER, null)
                ?.let { name -> FilterLook.entries.firstOrNull { it.name == name } }
                ?: FilterLook.NEUTRAL,
            aiEnabled = prefs.getBoolean(KEY_AI, true),
        ),
    )
    val state: StateFlow<CameraUiState> = _state

    private var lastAppliedZoom = 1f
    private var userLensUntil = 0L
    private var latestLabels: List<SceneLabel> = emptyList()
    private var latestObjects: List<SceneObject> = emptyList()
    private var pendingRecipe: SceneRecipe? = null
    private var recipeStreak = 0
    private var committedRecipe: SceneRecipe? = null
    private var acquireStreak = 0
    private var unlockSceneStreak = 0
    private var unlockPanStreak = 0
    private var subjectMissingSince = 0L
    private var lastHintAt = 0L
    private var lockedSubjectCenter: Offset? = null
    private var zoomForRecipe: SceneRecipe? = null
    private var savedToken = 0L

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
        prefs.edit().putString(KEY_FILTER, filter.name).apply()
        _state.update { it.copy(filter = filter) }
    }

    fun toggleAi() {
        val enable = !_state.value.aiEnabled
        if (enable) userLensUntil = 0L
        resetAcquire(unlock = true)
        prefs.edit().putBoolean(KEY_AI, enable).apply()
        _state.update {
            it.copy(
                aiEnabled = enable,
                frameLocked = false,
                aligned = false,
                cue = SceneCue.NONE,
                cueSubject = null,
            )
        }
        if (!enable) {
            val current = _state.value
            _state.update {
                it.copy(frame = Rect(0f, 0f, current.viewWidth, current.viewHeight))
            }
        }
    }

    fun selectLens(step: LensStep) {
        userLensUntil = SystemClock.uptimeMillis() + USER_LENS_MS
        lastAppliedZoom = step.zoom
        _state.update {
            it.copy(
                activeLensId = step.id,
                requestedZoom = step.zoom,
                zoomToken = it.zoomToken + 1,
            )
        }
    }

    fun onUserZoom() {
        userLensUntil = SystemClock.uptimeMillis() + USER_LENS_MS
    }

    fun toggleFacing() {
        userLensUntil = 0L
        resetAcquire(unlock = true)
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
                frameLocked = false,
                requestedZoom = if (next == CameraSelector.LENS_FACING_FRONT) 1f else null,
                zoomToken = it.zoomToken + 1,
            )
        }
    }

    fun cycleFlash() {
        val next = when (_state.value.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        prefs.edit().putInt(KEY_FLASH, next).apply()
        _state.update { it.copy(flashMode = next) }
    }

    fun onPreviewSize(width: Float, height: Float) {
        if (width == _state.value.viewWidth && height == _state.value.viewHeight) return
        val locked = _state.value.frameLocked
        _state.update { it.copy(viewWidth = width, viewHeight = height) }
        if (!locked) resetAcquire(unlock = false)
    }

    fun onHorizon(degrees: Float) {
        val snapped = if (abs(degrees) < 1.2f) 0f else degrees
        if (abs(snapped - _state.value.horizonDegrees) < 1.5f) return
        _state.update { it.copy(horizonDegrees = snapped) }
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
        directFromScene()
    }

    fun showFocus(point: Offset) {
        _state.update { it.copy(focusPoint = point) }
    }

    fun clearFocus() {
        _state.update { it.copy(focusPoint = null) }
    }

    fun unlockFraming() {
        resetAcquire(unlock = true)
        _state.update {
            it.copy(
                frameLocked = false,
                aligned = false,
                cue = SceneCue.NONE,
                cueSubject = null,
                frame = Rect(0f, 0f, it.viewWidth, it.viewHeight),
            )
        }
    }

    fun consumeActuation() {
        _state.update { it.copy(requestedFocus = null) }
    }

    fun setCapturing(capturing: Boolean) {
        _state.update { it.copy(capturing = capturing) }
    }

    fun setSaving(saving: Boolean) {
        _state.update { it.copy(saving = saving) }
    }

    fun showReview(bitmap: Bitmap) {
        val old = _state.value.reviewBitmap
        _state.update { it.copy(reviewBitmap = bitmap, capturing = false) }
        recycleSoon(old)
    }

    fun discardReview() {
        val old = _state.value.reviewBitmap
        _state.update { it.copy(reviewBitmap = null, capturing = false) }
        recycleSoon(old)
    }

    fun setThumbnail(bitmap: Bitmap) {
        val old = _state.value.thumbnail
        _state.update { it.copy(thumbnail = bitmap) }
        recycleSoon(old)
    }

    fun onSaved(uri: Uri?, thumbnail: Bitmap?) {
        val previous = _state.value.reviewBitmap
        val shot = uri?.let { value -> SavedShot(value, ++savedToken) }
        _state.update {
            it.copy(
                saving = false,
                reviewBitmap = if (uri != null) null else it.reviewBitmap,
                thumbnail = thumbnail ?: it.thumbnail,
                latestUri = uri ?: it.latestUri,
                savedShot = shot ?: it.savedShot,
            )
        }
        if (uri != null) {
            recycleSoon(previous)
        } else {
            postNotice(UiText(R.string.save_failed))
        }
    }

    fun captureFailed(message: UiText) {
        _state.update { it.copy(capturing = false) }
        postNotice(message)
    }

    fun postNotice(message: UiText) {
        _state.update { it.copy(notice = message, noticeToken = it.noticeToken + 1) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeSaved() {
        _state.update { it.copy(savedShot = null) }
    }

    private fun directFromScene() {
        val current = _state.value
        if (current.reviewBitmap != null || current.capturing) return
        if (!current.aiEnabled) return
        if (current.viewWidth < 8f || current.viewHeight < 8f) return
        val now = SystemClock.uptimeMillis()
        val front = current.lensFacing == CameraSelector.LENS_FACING_FRONT
        val decision = SceneDirector.decide(
            steps = current.lensSteps,
            currentZoom = current.currentZoom,
            viewWidth = current.viewWidth,
            viewHeight = current.viewHeight,
            faces = current.faces,
            objects = latestObjects,
            labels = latestLabels,
            horizonDegrees = current.horizonDegrees,
            front = front,
        )
        val recipe = SceneRecipe.from(decision.scene)
        if (recipe == pendingRecipe) {
            recipeStreak += 1
        } else {
            pendingRecipe = recipe
            recipeStreak = 1
        }
        if (current.frameLocked) {
            maybeUnlock(decision, recipe, now)
            maybeHint(decision, now, updateScene = false)
            return
        }
        if (recipeStreak >= RECIPE_HOLD) {
            if (committedRecipe != recipe) {
                committedRecipe = recipe
                acquireStreak = 0
                zoomForRecipe = null
            }
        }
        val held = committedRecipe ?: return
        acquire(decision, held, now, front)
    }

    private fun acquire(
        decision: DirectorDecision,
        recipe: SceneRecipe,
        now: Long,
        front: Boolean,
    ) {
        val current = _state.value
        val letterbox = FramingEngine.letterbox(current.viewWidth, current.viewHeight, recipe)
        if (SceneRecipe.from(decision.scene) == recipe) {
            acquireStreak += 1
        } else {
            acquireStreak = 0
        }
        val subject = decision.subject
        val needsSubject = recipe != SceneRecipe.LANDSCAPE
        val subjectOk = !needsSubject || subject != null
        if (current.frame.width < 8f || current.frame.aspectShifted(letterbox)) {
            _state.update {
                it.copy(
                    frame = letterbox,
                    scene = decision.scene,
                    sceneLabel = decision.sceneLabel,
                    why = decision.why,
                )
            }
        }
        maybePickLensOnce(decision, recipe, front, current)
        maybeHint(decision, now, updateScene = true)
        if (subjectOk && acquireStreak >= ACQUIRE_HOLD && recipeStreak >= RECIPE_HOLD) {
            val freeze = FramingEngine.compose(
                current.viewWidth,
                current.viewHeight,
                recipe,
                subject,
            )
            lockedSubjectCenter = subject?.center
            subjectMissingSince = 0L
            unlockSceneStreak = 0
            unlockPanStreak = 0
            _state.update {
                it.copy(
                    frame = freeze,
                    frameLocked = true,
                    aligned = true,
                    scene = decision.scene,
                    sceneLabel = decision.sceneLabel,
                    why = decision.why,
                    hint = decision.hint,
                    cue = SceneCue.NONE,
                    cueSubject = null,
                    lockEpoch = it.lockEpoch + 1,
                )
            }
        }
    }

    private fun maybeUnlock(decision: DirectorDecision, liveRecipe: SceneRecipe, now: Long) {
        val current = _state.value
        val locked = committedRecipe ?: return
        if (liveRecipe != locked) {
            unlockSceneStreak += 1
        } else {
            unlockSceneStreak = 0
        }
        if (unlockSceneStreak >= UNLOCK_SCENE_HOLD) {
            unlockAndRestart(liveRecipe)
            return
        }
        val subject = decision.subject
        val needsSubject = locked != SceneRecipe.LANDSCAPE
        if (needsSubject && subject == null) {
            if (subjectMissingSince == 0L) subjectMissingSince = now
            if (now - subjectMissingSince >= SUBJECT_LOST_MS) {
                unlockAndRestart(liveRecipe)
            }
            return
        }
        subjectMissingSince = 0L
        val origin = lockedSubjectCenter
        if (subject != null && origin != null && current.viewWidth > 1f) {
            val moved = hypot(subject.center.x - origin.x, subject.center.y - origin.y)
            if (moved > current.viewWidth * PAN_FRACTION) {
                unlockPanStreak += 1
            } else {
                unlockPanStreak = 0
            }
            if (unlockPanStreak >= UNLOCK_PAN_HOLD) {
                unlockAndRestart(liveRecipe)
            }
        }
    }

    private fun unlockAndRestart(recipe: SceneRecipe) {
        resetAcquire(unlock = true)
        committedRecipe = recipe
        pendingRecipe = recipe
        recipeStreak = RECIPE_HOLD
        val current = _state.value
        val letterbox = FramingEngine.letterbox(current.viewWidth, current.viewHeight, recipe)
        _state.update {
            it.copy(
                frame = letterbox,
                frameLocked = false,
                aligned = false,
            )
        }
    }

    private fun maybePickLensOnce(
        decision: DirectorDecision,
        recipe: SceneRecipe,
        front: Boolean,
        current: CameraUiState,
    ) {
        val now = SystemClock.uptimeMillis()
        val userLocked = now < userLensUntil
        if (front || userLocked || current.frameLocked) return
        if (zoomForRecipe == recipe) return
        val lens = decision.lens ?: return
        zoomForRecipe = recipe
        lastAppliedZoom = lens.zoom
        val switched = lens.id != current.activeLensId
        _state.update {
            it.copy(
                suggestedLensId = lens.id,
                activeLensId = lens.id,
                requestedZoom = lens.zoom,
                zoomToken = it.zoomToken + 1,
                lensSwitchEpoch = if (switched) it.lensSwitchEpoch + 1 else it.lensSwitchEpoch,
            )
        }
    }

    private fun maybeHint(decision: DirectorDecision, now: Long, updateScene: Boolean) {
        val current = _state.value
        if (!current.aiEnabled) return
        val refresh = now - lastHintAt > 900L || current.sceneLabel == null
        if (!refresh) return
        lastHintAt = now
        _state.update {
            it.copy(
                hint = decision.hint,
                why = decision.why ?: it.why,
                sceneLabel = if (updateScene) decision.sceneLabel else it.sceneLabel,
                scene = if (updateScene) decision.scene else it.scene,
                cue = decision.cue,
                cueSubject = decision.subject,
            )
        }
    }

    private fun resetAcquire(unlock: Boolean) {
        acquireStreak = 0
        unlockSceneStreak = 0
        unlockPanStreak = 0
        subjectMissingSince = 0L
        lockedSubjectCenter = null
        zoomForRecipe = null
        if (unlock) {
            recipeStreak = 0
            pendingRecipe = null
            committedRecipe = null
        }
    }

    // Compose may still draw a replaced bitmap on the frame in flight; recycling
    // immediately risks "trying to use a recycled bitmap", so defer it briefly.
    private fun recycleSoon(bitmap: Bitmap?) {
        bitmap ?: return
        mainHandler.postDelayed({
            if (!bitmap.isRecycled) bitmap.recycle()
        }, RECYCLE_DELAY_MS)
    }

    override fun onCleared() {
        recycleSoon(_state.value.reviewBitmap)
        super.onCleared()
    }

    private companion object {
        const val RECIPE_HOLD = 8
        const val ACQUIRE_HOLD = 12
        const val UNLOCK_SCENE_HOLD = 8
        const val UNLOCK_PAN_HOLD = 5
        const val PAN_FRACTION = 0.22f
        const val SUBJECT_LOST_MS = 1100L
        const val USER_LENS_MS = 15_000L
        const val RECYCLE_DELAY_MS = 800L
        const val PREFS_NAME = "lens_coach"
        const val KEY_FLASH = "flash_mode"
        const val KEY_FILTER = "filter"
        const val KEY_AI = "ai_enabled"
    }
}

private fun Rect.aspectShifted(other: Rect): Boolean {
    val a = width / height.coerceAtLeast(1f)
    val b = other.width / other.height.coerceAtLeast(1f)
    return abs(a - b) > 0.08f
}

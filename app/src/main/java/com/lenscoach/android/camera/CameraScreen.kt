package com.lenscoach.android.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RenderEffect as AndroidRenderEffect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.ZoomState
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.lenscoach.android.R
import com.lenscoach.android.capture.CaptureCrop
import com.lenscoach.android.capture.CaptureStore
import com.lenscoach.android.overlay.CompositionOverlay
import com.lenscoach.android.style.FilterLook
import com.lenscoach.android.ui.UiText
import com.lenscoach.android.ui.Viewfinder
import com.lenscoach.android.ui.asString
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.max
import kotlinx.coroutines.delay

private const val THUMBNAIL_PX = 144

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (!ok) {
            val activity = context as? Activity
            permanentlyDenied = activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            if (!permanentlyDenied) {
                Toast.makeText(context, context.getString(R.string.permission_rationale), Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(granted) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    if (!granted) {
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onOpenSettings = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }
            },
        )
        return
    }
    LiveCamera(viewModel)
}

@Composable
private fun PermissionScreen(permanentlyDenied: Boolean, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Viewfinder.Chrome)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.PhotoCamera,
            contentDescription = null,
            tint = Viewfinder.Muted,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.permission_needed),
            color = Viewfinder.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (permanentlyDenied) R.string.permission_permanent else R.string.permission_rationale),
            color = Viewfinder.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .selectable(selected = false, role = Role.Button, onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.open_settings),
                    color = Viewfinder.OnAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Viewfinder.Accent)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveCamera(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build(),
        )
    }
    val labeler = remember {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.62f).build(),
        )
    }
    val objectDetector = remember {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .build(),
        )
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        runCatching { viewModel.loadInventory(LensInventory.probe(context)) }
        captureExecutor.execute {
            runCatching { CaptureStore.loadLatest(context, THUMBNAIL_PX) }
                .getOrNull()
                ?.let { latest -> viewModel.setThumbnail(latest.bitmap) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            faceDetector.close()
            labeler.close()
            objectDetector.close()
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, cameraController) {
        val observer = Observer<ZoomState> { zoom ->
            viewModel.onLiveZoom(zoom.zoomRatio, zoom.minZoomRatio, zoom.maxZoomRatio)
        }
        cameraController.zoomState.observe(lifecycleOwner, observer)
        onDispose { cameraController.zoomState.removeObserver(observer) }
    }

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val degrees = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                viewModel.onHorizon(degrees)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LaunchedEffect(state.lensFacing) {
        cameraController.cameraSelector = CameraSelector.Builder()
            .requireLensFacing(state.lensFacing)
            .build()
        cameraController.bindToLifecycle(lifecycleOwner)
        previewView.controller = cameraController
        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraController.setImageAnalysisAnalyzer(
            analysisExecutor,
            MlKitAnalyzer(
                listOf(faceDetector, objectDetector, labeler),
                CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor,
            ) { result ->
                runCatching {
                    val faces = result.getValue(faceDetector).orEmpty().map { face ->
                        face.boundingBox.toComposeRect()
                    }
                    val objects = result.getValue(objectDetector).orEmpty().map { obj ->
                        SceneObject(
                            box = obj.boundingBox.toComposeRect(),
                            category = obj.labels.maxByOrNull { it.confidence }?.text,
                        )
                    }
                    val labels = result.getValue(labeler).orEmpty().map { label ->
                        SceneLabel(label.text, label.confidence)
                    }
                    viewModel.onScene(faces, objects, labels)
                }
            },
        )
    }

    // Flash mode is a plain use-case field; changing it must not rebind the camera.
    LaunchedEffect(state.flashMode) {
        cameraController.imageCaptureFlashMode = state.flashMode
    }

    LaunchedEffect(state.zoomToken) {
        val zoom = state.requestedZoom ?: return@LaunchedEffect
        runCatching { cameraController.setZoomRatio(zoom) }
    }

    LaunchedEffect(state.focusToken) {
        val point = state.requestedFocus ?: return@LaunchedEffect
        focusAt(previewView, cameraController, point)
    }

    LaunchedEffect(state.focusPoint) {
        if (state.focusPoint != null) {
            delay(900)
            viewModel.clearFocus()
        }
    }

    LaunchedEffect(state.lockEpoch) {
        if (state.lockEpoch > 0L) vibrate(context, durationMs = 22, amplitude = 80)
    }

    LaunchedEffect(state.noticeToken) {
        val message = state.notice ?: return@LaunchedEffect
        Toast.makeText(context, message.resolve(context), Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    LaunchedEffect(state.savedShot) {
        val shot = state.savedShot ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.saved_ok),
            actionLabel = context.getString(R.string.view_action),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) openSavedPhoto(context, shot.uri)
        viewModel.consumeSaved()
    }

    val shutterFlash = remember { Animatable(0f) }
    LaunchedEffect(state.capturing) {
        if (state.capturing) {
            shutterFlash.snapTo(0.85f)
            shutterFlash.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Viewfinder.Chrome),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = lookRenderEffect(state.filter)
                    }
                }
                .onSizeChanged { size ->
                    viewModel.onPreviewSize(size.width.toFloat(), size.height.toFloat())
                },
        )
        CompositionOverlay(
            frame = state.frame,
            faces = state.faces,
            objects = state.objects,
            aligned = state.aligned,
            frameLocked = state.frameLocked,
            lockEpoch = state.lockEpoch,
            focusPoint = state.focusPoint,
            horizonDegrees = state.horizonDegrees,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom == 1f) return@detectTransformGestures
                        val current = viewModel.state.value
                        if (current.reviewBitmap != null || current.capturing) return@detectTransformGestures
                        val live = cameraController.zoomState.value ?: return@detectTransformGestures
                        val next = (live.zoomRatio * zoom).coerceIn(live.minZoomRatio, live.maxZoomRatio)
                        runCatching { cameraController.setZoomRatio(next) }
                        viewModel.onUserZoom()
                    }
                }
                .pointerInput(state.capturing, state.reviewBitmap) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (state.reviewBitmap != null || state.capturing) return@detectTapGestures
                            val frame = state.frame
                            if (state.frameLocked && !frame.contains(offset)) {
                                viewModel.unlockFraming()
                            }
                            focusAt(previewView, cameraController, offset)
                            viewModel.showFocus(offset)
                        },
                        onDoubleTap = { offset ->
                            if (state.reviewBitmap != null || state.capturing) return@detectTapGestures
                            focusAt(previewView, cameraController, offset)
                            viewModel.showFocus(offset)
                            capture(context, cameraController, viewModel, viewModel.state.value, captureExecutor)
                        },
                    )
                },
        )
        if (shutterFlash.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = shutterFlash.value)),
            )
        }
        TopBar(
            flashMode = state.flashMode,
            aiEnabled = state.aiEnabled,
            onFlash = viewModel::cycleFlash,
            onAi = viewModel::toggleAi,
            onSwitch = viewModel::toggleFacing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
        CoachBanner(
            scene = state.sceneLabel?.asString().orEmpty(),
            hint = state.hint.asString(),
            why = state.why?.asString().orEmpty(),
            summary = inventorySummary(state.inventory),
            aligned = state.aligned,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
        )
        BottomBar(
            filter = state.filter,
            showFilters = showFilters,
            capturing = state.capturing,
            frameLocked = state.frameLocked,
            showLenses = state.lensFacing == CameraSelector.LENS_FACING_BACK && state.lensSteps.isNotEmpty(),
            steps = state.lensSteps,
            activeLensId = state.activeLensId,
            suggestedLensId = state.suggestedLensId,
            lensSwitchEpoch = state.lensSwitchEpoch,
            thumbnail = state.thumbnail,
            latestUri = state.latestUri,
            onViewLatest = { uri -> openSavedPhoto(context, uri) },
            onLens = viewModel::selectLens,
            onFilter = viewModel::setFilter,
            onToggleFilters = { showFilters = !showFilters },
            onShutter = {
                if (!state.capturing && state.reviewBitmap == null) {
                    capture(context, cameraController, viewModel, viewModel.state.value, captureExecutor)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 172.dp),
        )
        state.reviewBitmap?.let { bitmap ->
            val reviewAlpha = remember(bitmap) { Animatable(0f) }
            LaunchedEffect(reviewAlpha) { reviewAlpha.animateTo(1f, tween(220)) }
            ReviewOverlay(
                bitmap = bitmap,
                filter = state.filter,
                saving = state.saving,
                onRetake = viewModel::discardReview,
                onSave = {
                    if (!state.saving) {
                        viewModel.setSaving(true)
                        captureExecutor.execute {
                            val uri = CaptureStore.save(context, bitmap, state.filter.name)
                            val thumb = thumbnailFrom(bitmap, THUMBNAIL_PX)
                            viewModel.onSaved(uri, thumb)
                        }
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = reviewAlpha.value },
            )
        }
    }
}

@Composable
private fun TopBar(
    flashMode: Int,
    aiEnabled: Boolean,
    onFlash: () -> Unit,
    onAi: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flashIcon = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
        ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
        else -> Icons.Outlined.FlashOff
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onFlash) {
            Icon(flashIcon, contentDescription = stringResource(R.string.flash), tint = Viewfinder.Text)
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .selectable(selected = aiEnabled, role = Role.Button, onClick = onAi),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (aiEnabled) R.string.ai_on else R.string.ai_off),
                color = if (aiEnabled) Viewfinder.OnAccent else Viewfinder.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (aiEnabled) Viewfinder.Accent else Viewfinder.Dim)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        IconButton(onClick = onSwitch) {
            Icon(
                Icons.Outlined.Cameraswitch,
                contentDescription = stringResource(R.string.switch_camera),
                tint = Viewfinder.Text,
            )
        }
    }
}

@Composable
private fun CoachBanner(
    scene: String,
    hint: String,
    why: String,
    summary: String,
    aligned: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val banner by animateColorAsState(
        targetValue = if (aligned) Viewfinder.Accent.copy(alpha = 0.92f) else Viewfinder.Dim,
        animationSpec = tween(durationMillis = 240),
        label = "banner",
    )
    val titleColor by animateColorAsState(
        targetValue = if (aligned) Viewfinder.OnAccent else Viewfinder.Text,
        animationSpec = tween(durationMillis = 240),
        label = "bannerTitle",
    )
    val bodyColor by animateColorAsState(
        targetValue = if (aligned) Viewfinder.OnAccent else Viewfinder.Muted,
        animationSpec = tween(durationMillis = 240),
        label = "bannerBody",
    )
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(banner)
            .animateContentSize(tween(200))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.banner_details),
            ) { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (scene.isNotBlank()) {
            Text(
                scene,
                color = titleColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedContent(
            targetState = hint,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(120))
            },
            label = "hintChip",
        ) { line ->
            Text(
                line,
                color = bodyColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (why.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        why,
                        color = bodyColor.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        color = bodyColor.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun inventorySummary(inventory: CameraInventory?): String {
    if (inventory == null) return stringResource(R.string.inventory_detecting)
    val labels = mutableListOf<String>()
    for (part in inventory.parts) {
        labels += stringResource(part.role.captionRes(), part.spec)
    }
    return stringResource(R.string.inventory_back, inventory.backCount, labels.joinToString(" · "))
}

@Composable
private fun BottomBar(
    filter: FilterLook,
    showFilters: Boolean,
    capturing: Boolean,
    frameLocked: Boolean,
    showLenses: Boolean,
    steps: List<LensStep>,
    activeLensId: String?,
    suggestedLensId: String?,
    lensSwitchEpoch: Long,
    thumbnail: Bitmap?,
    latestUri: Uri?,
    onViewLatest: (Uri) -> Unit,
    onLens: (LensStep) -> Unit,
    onFilter: (FilterLook) -> Unit,
    onToggleFilters: () -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = showFilters,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            Column {
                FilterSheet(filter = filter, onFilter = onFilter)
                Spacer(Modifier.height(12.dp))
            }
        }
        AnimatedVisibility(
            visible = showLenses,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    steps.forEach { step ->
                        LensChip(
                            label = if (step.id == suggestedLensId && step.id != activeLensId) {
                                stringResource(R.string.lens_suggested, step.label)
                            } else {
                                step.label
                            },
                            active = step.id == activeLensId,
                            suggested = step.id == suggestedLensId && step.id != activeLensId,
                            confirmEpoch = lensSwitchEpoch,
                            onClick = { onLens(step) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterButton(open = showFilters, onClick = onToggleFilters)
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .border(3.dp, Viewfinder.Text, CircleShape)
                    .clickable(
                        enabled = !capturing,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.shoot),
                        onClick = onShutter,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (capturing) Viewfinder.Accent else Viewfinder.Text),
                )
            }
            GalleryThumb(
                bitmap = thumbnail,
                onClick = { latestUri?.let(onViewLatest) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (frameLocked) R.string.shutter_hint_locked else R.string.shutter_hint),
            color = Viewfinder.Muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun GalleryThumb(bitmap: Bitmap?, onClick: () -> Unit) {
    val description = stringResource(R.string.gallery_latest)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Viewfinder.Dim)
            .border(1.dp, Viewfinder.Muted.copy(alpha = 0.4f), CircleShape)
            .then(
                if (bitmap == null) {
                    Modifier
                } else {
                    Modifier
                        .semantics { contentDescription = description }
                        .clickable(role = Role.Button, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = description,
                tint = Viewfinder.Muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LensChip(
    label: String,
    active: Boolean,
    suggested: Boolean,
    confirmEpoch: Long,
    onClick: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirmEpoch, active) {
        if (!active || confirmEpoch == 0L) return@LaunchedEffect
        confirm = true
        delay(340)
        confirm = false
    }
    val scale by animateFloatAsState(
        targetValue = if (confirm) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "lensScale",
    )
    val background by animateColorAsState(
        targetValue = if (active) Viewfinder.Accent else Viewfinder.Dim,
        animationSpec = tween(180),
        label = "lensBg",
    )
    val foreground by animateColorAsState(
        targetValue = if (active) Viewfinder.OnAccent else Viewfinder.Text,
        animationSpec = tween(180),
        label = "lensFg",
    )
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = active, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 13.sp,
            fontWeight = if (suggested || active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .border(
                    width = if (suggested) 1.dp else 0.dp,
                    color = Viewfinder.Accent,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FilterButton(open: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = open, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (open) Viewfinder.Accent else Viewfinder.Dim)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.filters),
                tint = if (open) Viewfinder.OnAccent else Viewfinder.Text,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(R.string.filters),
                color = if (open) Viewfinder.OnAccent else Viewfinder.Text,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun FilterSheet(
    filter: FilterLook,
    onFilter: (FilterLook) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Viewfinder.Surface.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            stringResource(R.string.filters),
            color = Viewfinder.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterLook.entries.forEach { look ->
                val selected = look == filter
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .selectable(selected = selected, role = Role.Button) { onFilter(look) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Viewfinder.Accent else Viewfinder.Dim)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(look.swatch),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(look.labelRes),
                            color = if (selected) Viewfinder.OnAccent else Viewfinder.Text,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.filter_preview_unsupported),
                color = Viewfinder.Muted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ReviewOverlay(
    bitmap: Bitmap,
    filter: FilterLook,
    saving: Boolean,
    onRetake: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Viewfinder.Chrome),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.review_photo),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = stringResource(filter.labelRes),
            color = Viewfinder.Text,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = !saving, role = Role.Button, onClick = onRetake),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.retake),
                    color = Viewfinder.Text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Viewfinder.Dim)
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = !saving, role = Role.Button, onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.save),
                    color = Viewfinder.OnAccent,
                    modifier = Modifier
                        .alpha(if (saving) 0.5f else 1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Viewfinder.Accent)
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private fun android.graphics.Rect.toComposeRect(): Rect = Rect(
    left = left.toFloat(),
    top = top.toFloat(),
    right = right.toFloat(),
    bottom = bottom.toFloat(),
)

private fun focusAt(
    previewView: PreviewView,
    controller: LifecycleCameraController,
    offset: Offset,
) {
    val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
    val action = FocusMeteringAction.Builder(
        point,
        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
    )
        .setAutoCancelDuration(3, TimeUnit.SECONDS)
        .build()
    controller.cameraControl?.startFocusAndMetering(action)
}

private fun capture(
    context: android.content.Context,
    controller: LifecycleCameraController,
    viewModel: CameraViewModel,
    state: CameraUiState,
    executor: Executor,
) {
    viewModel.setCapturing(true)
    vibrate(context)
    controller.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotated = image.toRotatedBitmap(
                        state.lensFacing == CameraSelector.LENS_FACING_FRONT,
                    )
                    val cropped = CaptureCrop.cropToGuide(
                        bitmap = rotated,
                        guide = state.frame,
                        viewWidth = state.viewWidth,
                        viewHeight = state.viewHeight,
                        mirrored = false,
                    )
                    if (cropped !== rotated) rotated.recycle()
                    val graded = state.filter.grade(cropped)
                    if (graded !== cropped) cropped.recycle()
                    viewModel.showReview(graded)
                } catch (error: Exception) {
                    viewModel.captureFailed(
                        UiText(R.string.grade_failed, listOf(error.message ?: "")),
                    )
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.captureFailed(
                    UiText(R.string.capture_failed, listOf(exception.message ?: "")),
                )
            }
        },
    )
}

private fun openSavedPhoto(context: android.content.Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}

private fun thumbnailFrom(bitmap: Bitmap, targetPx: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= targetPx) return bitmap
    val scale = targetPx.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

private fun ImageProxy.toRotatedBitmap(mirror: Boolean): Bitmap {
    val source = toBitmap()
    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (rotated !== source) source.recycle()
    return rotated
}

private fun vibrate(context: android.content.Context, durationMs: Long = 35L, amplitude: Int = 70) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
}

@RequiresApi(Build.VERSION_CODES.S)
private fun lookRenderEffect(look: FilterLook): ComposeRenderEffect? {
    if (look == FilterLook.NEUTRAL) return null
    return AndroidRenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(look.colorMatrix()),
    ).asComposeRenderEffect()
}

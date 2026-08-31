package com.lenscoach.android.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.lenscoach.android.R
import com.lenscoach.android.capture.CaptureStore
import com.lenscoach.android.overlay.CompositionOverlay
import com.lenscoach.android.style.StylePack
import com.lenscoach.android.ui.asString
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (!ok) {
            Toast.makeText(context, context.getString(R.string.permission_rationale), Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(granted) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    if (!granted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.permission_needed), color = Color(0xFFF4E6C3))
        }
        return
    }
    LiveCamera(viewModel)
}

@Composable
private fun LiveCamera(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
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

    LaunchedEffect(Unit) {
        runCatching { viewModel.loadInventory(LensInventory.probe(context)) }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            faceDetector.close()
            labeler.close()
            objectDetector.close()
            analysisExecutor.shutdown()
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

    LaunchedEffect(state.lensFacing, state.flashMode) {
        cameraController.cameraSelector = CameraSelector.Builder()
            .requireLensFacing(state.lensFacing)
            .build()
        cameraController.imageCaptureFlashMode = state.flashMode
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

    LaunchedEffect(state.saveMessage) {
        val message = state.saveMessage ?: return@LaunchedEffect
        Toast.makeText(context, message.resolve(context), Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewModel.onPreviewSize(size.width.toFloat(), size.height.toFloat())
                },
        )
        CompositionOverlay(
            frame = state.frame,
            faces = state.faces,
            objects = state.objects,
            aligned = state.aligned,
            focusPoint = state.focusPoint,
            horizonDegrees = state.horizonDegrees,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.capturing, state.reviewBitmap) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (state.reviewBitmap != null || state.capturing) return@detectTapGestures
                            focusAt(previewView, cameraController, offset)
                            viewModel.showFocus(offset)
                        },
                        onDoubleTap = { offset ->
                            if (state.reviewBitmap != null || state.capturing) return@detectTapGestures
                            focusAt(previewView, cameraController, offset)
                            viewModel.showFocus(offset)
                            capture(context, cameraController, viewModel, state)
                        },
                    )
                },
        )
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
                .padding(top = 52.dp),
        )
        BottomBar(
            style = state.style,
            capturing = state.capturing,
            showLenses = state.lensFacing == CameraSelector.LENS_FACING_BACK && state.lensSteps.isNotEmpty(),
            steps = state.lensSteps,
            activeLensId = state.activeLensId,
            suggestedLensId = state.suggestedLensId,
            onLens = viewModel::selectLens,
            onStyle = viewModel::setStyle,
            onShutter = {
                if (!state.capturing && state.reviewBitmap == null) {
                    capture(context, cameraController, viewModel, state)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
        state.reviewBitmap?.let { bitmap ->
            ReviewOverlay(
                bitmap = bitmap,
                style = state.style,
                onRetake = viewModel::discardReview,
                onSave = {
                    val uri = CaptureStore.save(context, bitmap, state.style.name)
                    viewModel.onSaved(uri != null)
                },
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
            Icon(flashIcon, contentDescription = stringResource(R.string.flash), tint = Color(0xFFF4E6C3))
        }
        Text(
            text = stringResource(if (aiEnabled) R.string.ai_on else R.string.ai_off),
            color = if (aiEnabled) Color(0xFF111318) else Color(0xFFF4E6C3),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (aiEnabled) Color(0xFFF4E6C3) else Color(0x66000000))
                .clickable(onClick = onAi)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        IconButton(onClick = onSwitch) {
            Icon(Icons.Outlined.Cameraswitch, contentDescription = stringResource(R.string.switch_camera), tint = Color(0xFFF4E6C3))
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
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (aligned) Color(0xFFC8F59B) else Color(0xCC000000))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val titleColor = if (aligned) Color(0xFF111318) else Color(0xFFF4E6C3)
        val bodyColor = if (aligned) Color(0xFF111318) else Color(0xFFF4F1EA)
        if (scene.isNotBlank()) {
            Text(scene, color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(hint, color = bodyColor, fontSize = 13.sp)
        if (why.isNotBlank()) {
            Text(why, color = bodyColor.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        Text(summary, color = bodyColor.copy(alpha = 0.55f), fontSize = 10.sp)
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
    style: StylePack,
    capturing: Boolean,
    showLenses: Boolean,
    steps: List<LensStep>,
    activeLensId: String?,
    suggestedLensId: String?,
    onLens: (LensStep) -> Unit,
    onStyle: (StylePack) -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showLenses) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEach { step ->
                    val active = step.id == activeLensId
                    val suggested = step.id == suggestedLensId
                    Text(
                        text = if (suggested && !active) {
                            stringResource(R.string.lens_suggested, step.label)
                        } else {
                            step.label
                        },
                        color = if (active) Color(0xFF1A1408) else Color(0xFFF4E6C3),
                        fontSize = 13.sp,
                        fontWeight = if (suggested) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) Color(0xFFF4E6C3) else Color(0x66000000))
                            .border(
                                width = if (suggested && !active) 1.dp else 0.dp,
                                color = Color(0xFFF4E6C3),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { onLens(step) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StylePack.entries.forEach { pack ->
                val selected = pack == style
                Text(
                    text = stringResource(pack.labelRes),
                    color = if (selected) Color(0xFF1A1408) else Color(0xFFF4E6C3),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color(0xFFF4E6C3) else Color(0x66000000))
                        .clickable { onStyle(pack) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .clickable(enabled = !capturing, onClick = onShutter),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(if (capturing) Color(0xFFF4E6C3) else Color.White),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.shutter_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun ReviewOverlay(
    bitmap: Bitmap,
    style: StylePack,
    onRetake: () -> Unit,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.review_photo),
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = stringResource(style.labelRes),
            color = Color(0xFFF4E6C3),
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
            Text(
                stringResource(R.string.retake),
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x66FFFFFF))
                    .clickable(onClick = onRetake)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
            Text(
                stringResource(R.string.save),
                color = Color(0xFF1A1408),
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF4E6C3))
                    .clickable(onClick = onSave)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
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
) {
    viewModel.setCapturing(true)
    vibrate(context)
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toRotatedBitmap(state.lensFacing == CameraSelector.LENS_FACING_FRONT)
                    val graded = state.style.grade(bitmap)
                    if (graded !== bitmap) bitmap.recycle()
                    viewModel.showReview(graded)
                } catch (error: Exception) {
                    viewModel.setCapturing(false)
                    Toast.makeText(
                        context,
                        context.getString(R.string.grade_failed, error.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.setCapturing(false)
                Toast.makeText(
                    context,
                    context.getString(R.string.capture_failed, exception.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
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

private fun vibrate(context: android.content.Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator.vibrate(VibrationEffect.createOneShot(35, 70))
}

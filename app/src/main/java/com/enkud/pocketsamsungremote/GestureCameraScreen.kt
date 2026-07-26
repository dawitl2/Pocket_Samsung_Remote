package com.enkud.pocketsamsungremote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val GESTURE_MODEL = "gesture_recognizer.task"
private const val TRAIL_LIFETIME_MS = 2200L
private const val HANDWRITING_PREVIEW_MS = 220L
private const val POINTER_TRAIL_BREAK_MS = 280L
private const val MAX_PERSISTENT_INK_POINTS = 4000
private const val MIN_USABLE_HAND_SCALE = 0.05f
private const val ADVANCED_KEY_DWELL_MS = 1350L

private data class CameraOption(
    val id: String,
    val lensFacing: Int,
    val label: String
)

private data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f
)

private data class GestureOverlayState(
    val hands: List<List<LandmarkPoint>> = emptyList(),
    val sourceWidth: Int = 1,
    val sourceHeight: Int = 1
)

private data class GestureAction(
    val label: String,
    val commands: List<RemoteCommand>,
    val delayBetweenCommandsMs: Long = 140L
)

private data class PointerDelta(val x: Int, val y: Int)

private data class TrailPoint(
    val point: AirInkPoint,
    val strokeId: Int
)

private data class GestureInterpretation(
    val detected: String,
    val action: GestureAction? = null,
    val isLocked: Boolean = false,
    val lockChanged: Boolean = false,
    val cursorActive: Boolean = false,
    val cursorChanged: Boolean = false,
    val pointerDelta: PointerDelta? = null,
    val pointerPoint: AirInkPoint? = null,
    val keyboardPoint: AirInkPoint? = null,
    val writingPoint: AirInkPoint? = null,
    val finishWritingStroke: Boolean = false,
    val commitWriting: Boolean = false,
    val backspace: Boolean = false
)

private data class GestureObservation(
    val overlay: GestureOverlayState,
    val detected: String,
    val action: GestureAction?,
    val isLocked: Boolean,
    val lockChanged: Boolean,
    val cursorActive: Boolean,
    val cursorChanged: Boolean,
    val pointerDelta: PointerDelta?,
    val pointerPoint: AirInkPoint?,
    val keyboardPoint: AirInkPoint?,
    val writingPoint: AirInkPoint?,
    val finishWritingStroke: Boolean,
    val commitWriting: Boolean,
    val backspace: Boolean,
    val inferenceMs: Long
)

@Composable
fun GestureCameraScreen(
    onBack: () -> Unit,
    imeActive: Boolean,
    pointerEnabled: Boolean,
    onCommand: (RemoteCommand) -> Unit,
    onPointerMove: (Int, Int) -> Unit,
    onText: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val gestureScope = rememberCoroutineScope()
    val cameraPreferences = remember(context) {
        context.getSharedPreferences("gesture_camera", Context.MODE_PRIVATE)
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraOptions by remember { mutableStateOf<List<CameraOption>>(emptyList()) }
    var selectedCameraId by rememberSaveable {
        mutableStateOf(cameraPreferences.getString("selected_camera_id", null))
    }
    var cameraMenuExpanded by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    var blackoutActive by rememberSaveable { mutableStateOf(false) }
    var advancedMode by rememberSaveable { mutableStateOf(false) }
    var manualAdvancedKeyboard by rememberSaveable { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf(GestureOverlayState()) }
    var detectedText by remember { mutableStateOf("Starting hand tracker…") }
    var lastActionText by remember { mutableStateOf("No command sent yet") }
    var inferenceMs by remember { mutableStateOf(0L) }
    var isLocked by remember { mutableStateOf(false) }
    var cursorActive by remember { mutableStateOf(false) }
    var keyboardFingerPoint by remember { mutableStateOf<AirInkPoint?>(null) }
    var advancedTypedText by rememberSaveable { mutableStateOf("") }
    var handwritingStatus by remember { mutableStateOf("Preparing handwriting model…") }
    var handwrittenText by remember { mutableStateOf("") }
    var letterGuesses by remember { mutableStateOf<List<String>>(emptyList()) }
    var trailPoints by remember { mutableStateOf<List<TrailPoint>>(emptyList()) }
    var cursorTrailPoints by remember { mutableStateOf<List<TrailPoint>>(emptyList()) }
    var trailClock by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    var trailStrokeId by remember { mutableStateOf(0) }
    var cursorStrokeId by remember { mutableStateOf(0) }
    var lastCursorPointAt by remember { mutableStateOf(0L) }
    var lastPreviewRequestAt by remember { mutableStateOf(0L) }
    val airWriter = remember(context) {
        AirWritingRecognizer(context) { status ->
            mainExecutor.execute { handwritingStatus = status }
        }
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val gestureAnalyzer = remember(context) {
        runCatching {
            GestureFrameAnalyzer(
                context = context,
                mirrorInput = true,
                onObservation = { observation ->
                    mainExecutor.execute {
                        overlay = observation.overlay
                        detectedText = observation.detected
                        inferenceMs = observation.inferenceMs
                        isLocked = observation.isLocked
                        cursorActive = observation.cursorActive
                        cameraError = null
                        if (observation.cursorChanged) {
                            lastActionText = if (observation.cursorActive) {
                                "Browser cursor on"
                            } else {
                                "Browser cursor off"
                            }
                            if (!observation.cursorActive) {
                                airWriter.clear()
                                trailPoints = emptyList()
                                cursorTrailPoints = emptyList()
                                handwrittenText = ""
                                letterGuesses = emptyList()
                                lastCursorPointAt = 0L
                                lastPreviewRequestAt = 0L
                            }
                        }
                        if (observation.lockChanged) {
                            lastActionText = if (observation.isLocked) {
                                "Controls locked"
                            } else {
                                "Controls unlocked"
                            }
                            if (observation.isLocked) {
                                airWriter.clear()
                            }
                        }
                        observation.pointerDelta?.let { delta ->
                            onPointerMove(delta.x, delta.y)
                        }
                        observation.pointerPoint?.let { point ->
                            if (lastCursorPointAt == 0L ||
                                point.timeMs - lastCursorPointAt > POINTER_TRAIL_BREAK_MS
                            ) {
                                cursorStrokeId += 1
                            }
                            lastCursorPointAt = point.timeMs
                            cursorTrailPoints = (
                                cursorTrailPoints + TrailPoint(point, cursorStrokeId)
                            ).filter {
                                point.timeMs - it.point.timeMs < TRAIL_LIFETIME_MS
                            }
                            trailClock = point.timeMs
                        }
                        keyboardFingerPoint = observation.keyboardPoint
                        observation.writingPoint?.let { point ->
                            airWriter.addPoint(point)
                            trailPoints = (
                                trailPoints + TrailPoint(point, trailStrokeId)
                            ).takeLast(MAX_PERSISTENT_INK_POINTS)
                            trailClock = point.timeMs
                            if (point.timeMs - lastPreviewRequestAt >=
                                HANDWRITING_PREVIEW_MS
                            ) {
                                lastPreviewRequestAt = point.timeMs
                                airWriter.preview(
                                    preContext = handwrittenText,
                                    onCandidates = { candidates ->
                                        mainExecutor.execute {
                                            letterGuesses = candidates
                                                .mapNotNull { candidate ->
                                                    candidate.firstOrNull {
                                                        it.isLetterOrDigit()
                                                    }?.toString()
                                                }
                                                .distinct()
                                                .take(3)
                                        }
                                    },
                                    onError = { status ->
                                        mainExecutor.execute { handwritingStatus = status }
                                    }
                                )
                            }
                        }
                        if (observation.finishWritingStroke) {
                            airWriter.endStroke()
                            trailStrokeId += 1
                        }
                        if (observation.commitWriting) {
                            lastPreviewRequestAt = 0L
                            val fallbackGuess = letterGuesses.firstOrNull()
                            airWriter.recognize(
                                preContext = handwrittenText,
                                onResult = { rawText ->
                                    mainExecutor.execute {
                                        val recognized = if (rawText == " ") {
                                            " "
                                        } else {
                                            rawText.firstOrNull {
                                                it.isLetterOrDigit()
                                            }?.toString() ?: fallbackGuess.orEmpty()
                                        }
                                        if (recognized.isNotEmpty()) {
                                            handwrittenText += recognized
                                            lastActionText = if (recognized == " ") {
                                                "Wrote a space"
                                            } else {
                                                "Wrote “$recognized”"
                                            }
                                            letterGuesses = emptyList()
                                            onText(handwrittenText)
                                        }
                                    }
                                },
                                onError = { status ->
                                    mainExecutor.execute {
                                        if (fallbackGuess != null) {
                                            handwrittenText += fallbackGuess
                                            lastActionText = "Wrote \"$fallbackGuess\""
                                            letterGuesses = emptyList()
                                            onText(handwrittenText)
                                        } else {
                                            handwritingStatus = status
                                        }
                                    }
                                }
                            )
                        }
                        if (observation.backspace) {
                            lastPreviewRequestAt = 0L
                            airWriter.clear()
                            handwrittenText = handwrittenText.dropLast(1)
                            letterGuesses = emptyList()
                            lastActionText = "Backspace"
                            onCommand(RemoteCommand.KEY_BACK)
                        }
                        observation.action?.let { action ->
                            lastActionText = action.label
                            gestureScope.launch {
                                action.commands.forEachIndexed { index, command ->
                                    if (index > 0) delay(action.delayBetweenCommandsMs)
                                    onCommand(command)
                                }
                            }
                        }
                    }
                },
                onError = { message ->
                    mainExecutor.execute {
                        cameraError = message
                        detectedText = "Hand tracker unavailable"
                    }
                }
            )
        }.getOrElse {
            cameraError = it.message ?: "Hand tracker could not start."
            null
        }
    }

    DisposableEffect(gestureAnalyzer) {
        onDispose {
            gestureAnalyzer?.let { analyzer ->
                analyzerExecutor.execute { analyzer.close() }
            }
            analyzerExecutor.shutdown()
        }
    }

    DisposableEffect(airWriter) {
        onDispose {
            airWriter.close()
        }
    }

    LaunchedEffect(
        imeActive,
        pointerEnabled,
        advancedMode,
        manualAdvancedKeyboard,
        gestureAnalyzer
    ) {
        gestureAnalyzer?.writingMode =
            imeActive || (advancedMode && manualAdvancedKeyboard)
        gestureAnalyzer?.pointerMode = pointerEnabled
        gestureAnalyzer?.advancedMode = advancedMode
        gestureAnalyzer?.resetTracking()
    }

    DisposableEffect(blackoutActive, context) {
        val activity = context as? Activity
        val window = activity?.window
        val insetsController = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        if (blackoutActive) {
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (blackoutActive) {
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(cursorTrailPoints.isNotEmpty()) {
        while (cursorTrailPoints.isNotEmpty()) {
            delay(50L)
            trailClock = SystemClock.uptimeMillis()
            cursorTrailPoints = cursorTrailPoints.filter {
                trailClock - it.point.timeMs < TRAIL_LIFETIME_MS
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) {
            onDispose { }
        } else {
            var disposed = false
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    if (!disposed) {
                        runCatching { future.get() }
                            .onSuccess { provider ->
                                val options = cameraOptions(context, provider.availableCameraInfos)
                                cameraProvider = provider
                                cameraOptions = options
                                if (selectedCameraId !in options.map { it.id }) {
                                    selectedCameraId =
                                        options.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }?.id
                                            ?: options.firstOrNull()?.id
                                }
                                cameraError = if (options.isEmpty()) "No usable camera was found." else null
                            }
                            .onFailure { cameraError = it.message ?: "Could not open the camera system." }
                    }
                },
                mainExecutor
            )
            onDispose { disposed = true }
        }
    }

    DisposableEffect(cameraProvider, selectedCameraId, hasPermission, lifecycleOwner) {
        val provider = cameraProvider
        val cameraId = selectedCameraId
        if (!hasPermission || provider == null || cameraId == null) {
            onDispose { }
        } else {
            val selectedOption = cameraOptions.firstOrNull { it.id == cameraId }
            val isFront = selectedOption?.lensFacing == CameraSelector.LENS_FACING_FRONT
            gestureAnalyzer?.mirrorInput = isFront
            gestureAnalyzer?.resetTracking()

            var preview: Preview? = null
            var analysis: ImageAnalysis? = null
            if (gestureAnalyzer != null) {
                runCatching {
                    val selector = cameraSelectorFor(cameraId)
                    preview = Preview.Builder()
                        .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    analysis = ImageAnalysis.Builder()
                        .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { it.setAnalyzer(analyzerExecutor, gestureAnalyzer) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    cameraError = null
                }.onFailure {
                    cameraError = it.message ?: "This camera could not be opened."
                }
            }

            onDispose {
                analysis?.clearAnalyzer()
                preview?.let { provider.unbind(it) }
                analysis?.let { provider.unbind(it) }
                overlay = GestureOverlayState()
            }
        }
    }

    val advancedKeyboardVisible = advancedMode &&
        (imeActive || manualAdvancedKeyboard) &&
        !isLocked

    LaunchedEffect(advancedKeyboardVisible) {
        if (advancedKeyboardVisible) {
            advancedTypedText = ""
        } else {
            keyboardFingerPoint = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            HandLandmarkOverlay(
                state = overlay,
                accentColor = if (advancedMode) Color(0xFFA78BFA) else Color(0xFF55D6FF),
                modifier = Modifier.fillMaxSize()
            )
            AirWritingTrail(
                points = cursorTrailPoints,
                clock = trailClock,
                state = overlay,
                color = Color(0xFF55D6FF),
                fade = true,
                modifier = Modifier.fillMaxSize()
            )
            AirWritingTrail(
                points = trailPoints,
                clock = trailClock,
                state = overlay,
                color = Color(0xFFFFD54F),
                fade = false,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CameraPermissionPanel(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack
            )
        }

        if (hasPermission) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        if (advancedMode) {
                            Color(0xFF2E1065).copy(alpha = 0.82f)
                        } else {
                            Color.Black.copy(alpha = 0.45f)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoundOverlayButton(text = "‹", description = "Back", onClick = onBack)
                Text(
                    text = if (advancedMode) "ADVANCED" else "STANDARD",
                    color = if (advancedMode) Color(0xFFE9D5FF) else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                MoonOverlayButton(
                    onClick = {
                        cameraMenuExpanded = false
                        showInstructions = false
                        blackoutActive = true
                    }
                )
                AdvancedModeButton(
                    active = advancedMode,
                    onClick = {
                        advancedMode = !advancedMode
                        if (!advancedMode) manualAdvancedKeyboard = false
                    }
                )
                InfoOverlayButton(onClick = { showInstructions = true })
                Box {
                    Surface(
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .widthIn(max = 110.dp)
                            .clickable { cameraMenuExpanded = true }
                    ) {
                        Text(
                            text = cameraOptions.firstOrNull { it.id == selectedCameraId }?.label
                                ?: "Camera",
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = cameraMenuExpanded,
                        onDismissRequest = { cameraMenuExpanded = false }
                    ) {
                        cameraOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedCameraId = option.id
                                    cameraPreferences.edit()
                                        .putString("selected_camera_id", option.id)
                                        .apply()
                                    cameraMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                color = if (isLocked) {
                    Color(0xFFD32F2F).copy(alpha = 0.90f)
                } else {
                    Color(0xFF15803D).copy(alpha = 0.90f)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 74.dp)
            ) {
                Text(
                    text = if (isLocked) {
                        "LOCKED • TRACKING ONLY"
                    } else {
                        "UNLOCKED • TV CONTROL ACTIVE"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if ((pointerEnabled || imeActive) && !isLocked) {
                Surface(
                    color = if (cursorActive) {
                        Color(0xFF0369A1).copy(alpha = 0.92f)
                    } else {
                        Color.Black.copy(alpha = 0.68f)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 112.dp)
                ) {
                    Text(
                        text = if (cursorActive) {
                            "BROWSER CURSOR • ON"
                        } else {
                            "BROWSER CURSOR • OFF"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (cursorActive && !isLocked && !advancedKeyboardVisible) {
                Surface(
                    color = Color.Black.copy(alpha = 0.66f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 150.dp)
                        .widthIn(max = 360.dp)
                ) {
                    Text(
                        text = buildString {
                            append(
                                if (handwrittenText.isBlank()) {
                                    "WRITE MODE"
                                } else {
                                    "Writing: $handwrittenText"
                                }
                            )
                            if (letterGuesses.isNotEmpty()) {
                                append("  •  Guess: ")
                                append(letterGuesses.joinToString(" / "))
                            } else if (handwrittenText.isBlank()) {
                                append("  •  $handwritingStatus")
                            }
                        },
                        color = Color(0xFFFFD54F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            if (!advancedKeyboardVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    cameraError?.let { error ->
                        Text(
                            text = error,
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = detectedText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Last command: $lastActionText",
                        color = if (advancedMode) Color(0xFFC4B5FD) else Color(0xFF55D6FF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (advancedKeyboardVisible) {
                AdvancedHoverKeyboard(
                    fingerPoint = keyboardFingerPoint,
                    cameraState = overlay,
                    typedText = advancedTypedText,
                    onKey = { key ->
                        when (key) {
                            "BACKSPACE" -> {
                                if (advancedTypedText.isNotEmpty()) {
                                    advancedTypedText = advancedTypedText.dropLast(1)
                                    onCommand(RemoteCommand.KEY_BACK)
                                }
                            }
                            "SPACE" -> {
                                advancedTypedText += " "
                                onText(advancedTypedText)
                            }
                            "ENTER" -> onCommand(RemoteCommand.KEY_ENTER)
                            else -> {
                                advancedTypedText += key.lowercase()
                                onText(advancedTypedText)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (advancedMode) {
                Surface(
                    color = if (advancedKeyboardVisible) {
                        Color(0xFF7C3AED).copy(alpha = 0.92f)
                    } else {
                        Color(0xFF2E1065).copy(alpha = 0.82f)
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 76.dp)
                        .clickable {
                            manualAdvancedKeyboard = !advancedKeyboardVisible
                        }
                ) {
                    Text(
                        text = if (advancedKeyboardVisible) "\u2328 ON" else "\u2328",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0xFFA78BFA).copy(alpha = 0.52f))
                )
            }
        }

        if (showInstructions) {
            GestureInstructionOverlay(
                advancedMode = advancedMode,
                onDismiss = { showInstructions = false }
            )
        }

        if (blackoutActive) {
            BlackoutCurtain(
                isLocked = isLocked,
                onWake = { blackoutActive = false }
            )
        }
    }
}

@Composable
private fun RoundOverlayButton(
    text: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun InfoOverlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Color.Black.copy(alpha = 0.48f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "i",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MoonOverlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Color.Black.copy(alpha = 0.48f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u263E",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AdvancedModeButton(
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                if (active) Color(0xFF7C3AED) else Color.Black.copy(alpha = 0.48f),
                CircleShape
            )
            .border(
                1.dp,
                if (active) Color(0xFFE9D5FF) else Color.White.copy(alpha = 0.3f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "A",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private data class AdvancedKeyboardKey(
    val id: String,
    val label: String,
    val weight: Float = 1f
)

@Composable
private fun AdvancedHoverKeyboard(
    fingerPoint: AirInkPoint?,
    cameraState: GestureOverlayState,
    typedText: String,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember {
        listOf(
            "QWERTYUIOP".map { AdvancedKeyboardKey(it.toString(), it.toString()) },
            "ASDFGHJKL".map { AdvancedKeyboardKey(it.toString(), it.toString()) },
            "ZXCVBNM".map { AdvancedKeyboardKey(it.toString(), it.toString()) } +
                AdvancedKeyboardKey("BACKSPACE", "\u232B", 1.35f),
            listOf(
                AdvancedKeyboardKey("SPACE", "SPACE", 4.5f),
                AdvancedKeyboardKey("ENTER", "ENTER", 1.7f)
            )
        )
    }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var keyBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var hoveredKey by remember { mutableStateOf<String?>(null) }
    var hoverStartedAt by remember { mutableStateOf(0L) }
    var hoverProgress by remember { mutableStateOf(0f) }
    var latchedKey by remember { mutableStateOf<String?>(null) }
    var mappedFingerPoint by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(fingerPoint, cameraState, rootSize, keyBounds) {
        val point = fingerPoint
        if (point == null ||
            rootSize == IntSize.Zero ||
            cameraState.sourceWidth <= 1 ||
            cameraState.sourceHeight <= 1
        ) {
            hoveredKey = null
            hoverStartedAt = 0L
            hoverProgress = 0f
            latchedKey = null
            mappedFingerPoint = null
            return@LaunchedEffect
        }

        val scale = max(
            rootSize.width.toFloat() / cameraState.sourceWidth,
            rootSize.height.toFloat() / cameraState.sourceHeight
        )
        val mapped = Offset(
            x = (rootSize.width - cameraState.sourceWidth * scale) / 2f +
                point.x * cameraState.sourceWidth * scale,
            y = (rootSize.height - cameraState.sourceHeight * scale) / 2f +
                point.y * cameraState.sourceHeight * scale
        )
        mappedFingerPoint = mapped
        val hit = keyBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(mapped)
        }?.key

        if (hit == null) {
            hoveredKey = null
            hoverStartedAt = 0L
            hoverProgress = 0f
            latchedKey = null
        } else if (hit != hoveredKey) {
            hoveredKey = hit
            hoverStartedAt = point.timeMs
            hoverProgress = 0f
            if (latchedKey != hit) latchedKey = null
        } else if (latchedKey != hit) {
            hoverProgress = (
                (point.timeMs - hoverStartedAt).toFloat() / ADVANCED_KEY_DWELL_MS
            ).coerceIn(0f, 1f)
            if (hoverProgress >= 1f) {
                latchedKey = hit
                onKey(hit)
            }
        }
    }

    Box(
        modifier = modifier.onSizeChanged { rootSize = it }
    ) {
        Surface(
            color = Color(0xFF120B24).copy(alpha = 0.43f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 132.dp, start = 5.dp, end = 5.dp)
                .fillMaxWidth()
                .border(
                    1.dp,
                    Color(0xFFA78BFA).copy(alpha = 0.58f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = typedText.ifBlank { "Point at a key and hold through 1 \u2022 2 \u2022 3" },
                    color = if (typedText.isBlank()) Color(0xFFC4B5FD) else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (index == 1) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        row.forEach { key ->
                            AdvancedKeyboardKeyView(
                                key = key,
                                hovered = hoveredKey == key.id,
                                progress = if (hoveredKey == key.id) hoverProgress else 0f,
                                onBounds = { bounds ->
                                    if (keyBounds[key.id] != bounds) {
                                        keyBounds = keyBounds + (key.id to bounds)
                                    }
                                }
                            )
                        }
                    }
                    if (index != rows.lastIndex) Spacer(Modifier.height(5.dp))
                }
            }
        }
        mappedFingerPoint?.let { mapped ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.34f),
                    radius = 24.dp.toPx(),
                    center = mapped
                )
                drawCircle(
                    color = Color(0xFFE9D5FF),
                    radius = 17.dp.toPx(),
                    center = mapped,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = mapped
                )
            }
        }
    }
}

@Composable
private fun RowScope.AdvancedKeyboardKeyView(
    key: AdvancedKeyboardKey,
    hovered: Boolean,
    progress: Float,
    onBounds: (Rect) -> Unit
) {
    val stage = min(3, (progress * 3f).toInt() + 1)
    Surface(
        color = if (hovered) {
            Color(0xFF6D28D9).copy(alpha = 0.76f)
        } else {
            Color(0xFF2E2344).copy(alpha = 0.48f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .weight(key.weight)
            .height(64.dp)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (hovered) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    drawArc(
                        color = Color(0xFFE9D5FF),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = stage.toString(),
                    color = Color(0xFFE9D5FF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 5.dp)
                )
            }
            Text(
                text = key.label,
                color = Color.White,
                fontSize = if (key.label.length > 2) 12.sp else 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BlackoutCurtain(
    isLocked: Boolean,
    onWake: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onWake)
    ) {
        Surface(
            color = if (isLocked) {
                Color(0xFFD32F2F).copy(alpha = 0.20f)
            } else {
                Color(0xFF15803D).copy(alpha = 0.18f)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Text(
                text = if (isLocked) {
                    "LOCKED \u2022 TRACKING ONLY"
                } else {
                    "UNLOCKED \u2022 TV CONTROL ACTIVE"
                },
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun GestureInstructionOverlay(
    advancedMode: Boolean,
    onDismiss: () -> Unit
) {
    val guides = listOf(
        R.drawable.gesture_left to "Left gesture",
        R.drawable.gesture_right to "Right gesture",
        R.drawable.gesture_back to "Back gesture",
        R.drawable.gesture_home to "Home gesture"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val landscape = maxWidth > maxHeight
        Surface(
            color = Color(0xFF11151B),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = if (landscape) 720.dp else 420.dp)
                .clickable { }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (advancedMode) "Advanced gesture lab" else "Gesture guide",
                        color = if (advancedMode) Color(0xFFE9D5FF) else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "×",
                        color = Color.White,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp)
                    )
                }

                if (advancedMode) {
                    listOf(
                        "\u25CC  CLOSED-GRIP KNOB" to
                            "Close all fingers around an imaginary dial, hold briefly, then twist clockwise for volume up or counter-clockwise for volume down.",
                        "\u2194  THUMB RUB" to
                            "Keep thumb and index in contact, then rub laterally for repeated left/right navigation.",
                        "\u270C  UP / THREE DOWN" to
                            "Hold a peace sign for repeated Up, or hold up index, middle, and ring fingers for repeated Down. Both work in either mode.",
                        "\u2328  HOVER KEYBOARD" to
                            "Point at the large transparent QWERTY keyboard near the top and hold through the three-stage ring to type."
                    ).forEach { (title, detail) ->
                        AdvancedGuideCard(title, detail)
                        Spacer(Modifier.height(7.dp))
                    }
                } else if (landscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        guides.forEach { (image, description) ->
                            GestureGuideImage(image, description, Modifier.weight(1f))
                        }
                    }
                } else {
                    guides.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (image, description) ->
                                GestureGuideImage(image, description, Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedGuideCard(
    title: String,
    detail: String
) {
    Surface(
        color = Color(0xFF2E1065).copy(alpha = 0.74f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Text(
                text = title,
                color = Color(0xFFE9D5FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GestureGuideImage(
    image: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        modifier = modifier.aspectRatio(1f)
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CameraPermissionPanel(
    onRequest: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera access is needed to see and track your hand.",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack) { Text("Back to remote") }
    }
}

@Composable
private fun HandLandmarkOverlay(
    state: GestureOverlayState,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (state.hands.isEmpty() || state.sourceWidth <= 1 || state.sourceHeight <= 1) return@Canvas
        val scale = max(size.width / state.sourceWidth, size.height / state.sourceHeight)
        val offsetX = (size.width - state.sourceWidth * scale) / 2f
        val offsetY = (size.height - state.sourceHeight * scale) / 2f

        fun map(point: LandmarkPoint): Offset = Offset(
            x = offsetX + point.x * state.sourceWidth * scale,
            y = offsetY + point.y * state.sourceHeight * scale
        )

        state.hands.forEach { hand ->
            HAND_CONNECTIONS.forEach { (start, end) ->
                if (start < hand.size && end < hand.size) {
                    drawLine(
                        color = accentColor,
                        start = map(hand[start]),
                        end = map(hand[end]),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            hand.forEachIndexed { index, point ->
                drawCircle(
                    color = if (index == 4 || index == 8) Color(0xFFFFD54F) else Color.White,
                    radius = if (index == 4 || index == 8) 5.dp.toPx() else 3.5.dp.toPx(),
                    center = map(point)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f),
                    radius = if (index == 4 || index == 8) 5.dp.toPx() else 3.5.dp.toPx(),
                    center = map(point),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun AirWritingTrail(
    points: List<TrailPoint>,
    clock: Long,
    state: GestureOverlayState,
    color: Color,
    fade: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (points.size < 2 || state.sourceWidth <= 1 || state.sourceHeight <= 1) {
            return@Canvas
        }
        val scale = max(size.width / state.sourceWidth, size.height / state.sourceHeight)
        val offsetX = (size.width - state.sourceWidth * scale) / 2f
        val offsetY = (size.height - state.sourceHeight * scale) / 2f
        fun map(point: AirInkPoint): Offset = Offset(
            x = offsetX + point.x * state.sourceWidth * scale,
            y = offsetY + point.y * state.sourceHeight * scale
        )

        points.zipWithNext().forEach { (start, end) ->
            if (start.strokeId != end.strokeId) return@forEach
            val age = (clock - end.point.timeMs).coerceAtLeast(0L)
            if (fade && age >= TRAIL_LIFETIME_MS) return@forEach
            val alpha = if (fade) {
                1f - age.toFloat() / TRAIL_LIFETIME_MS
            } else {
                1f
            }
            drawLine(
                color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                start = map(start.point),
                end = map(end.point),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@ExperimentalCamera2Interop
private fun cameraOptions(context: Context, cameraInfos: List<CameraInfo>): List<CameraOption> {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    return cameraInfos.mapNotNull { info ->
        runCatching {
            val id = Camera2CameraInfo.from(info).cameraId
            val facing = info.lensFacing ?: CameraSelector.LENS_FACING_BACK
            val characteristics = manager.getCameraCharacteristics(id)
            val focal = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()
            val direction = if (facing == CameraSelector.LENS_FACING_FRONT) "Front" else "Rear"
            val detail = focal?.let { String.format(Locale.US, " • %.1f mm", it) }.orEmpty()
            CameraOption(id, facing, "$direction$detail • Camera $id")
        }.getOrNull()
    }.distinctBy { it.id }
        .sortedWith(
            compareBy<CameraOption> { it.lensFacing != CameraSelector.LENS_FACING_FRONT }
                .thenBy { it.id }
        )
}

@ExperimentalCamera2Interop
private fun cameraSelectorFor(cameraId: String): CameraSelector =
    CameraSelector.Builder()
        .addCameraFilter { infos ->
            infos.filter { info ->
                runCatching { Camera2CameraInfo.from(info).cameraId == cameraId }.getOrDefault(false)
            }
        }
        .build()

private class GestureFrameAnalyzer(
    context: Context,
    @Volatile var mirrorInput: Boolean,
    private val onObservation: (GestureObservation) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val interpreter = GestureInterpreter()
    private val landmarkStabilizer = LandmarkStabilizer()
    @Volatile
    var writingMode: Boolean = false
    @Volatile
    var pointerMode: Boolean = false
    @Volatile
    var advancedMode: Boolean = false
    private var lastTimestampMs = 0L
    private var recognizer: GestureRecognizer? = GestureRecognizer.createFromOptions(
        context,
        GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(GESTURE_MODEL).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.60f)
            .setMinHandPresenceConfidence(0.58f)
            .setMinTrackingConfidence(0.62f)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        val started = SystemClock.uptimeMillis()
        try {
            val bitmap = image.toRotatedBitmap(mirrorInput)
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestamp = max(SystemClock.uptimeMillis(), lastTimestampMs + 1)
            lastTimestampMs = timestamp
            val result = recognizer?.recognizeForVideo(mpImage, timestamp)
            if (result != null) {
                val inference = SystemClock.uptimeMillis() - started
                onObservation(
                    result.toObservation(
                        bitmap.width,
                        bitmap.height,
                        inference,
                        interpreter,
                        landmarkStabilizer,
                        writingMode,
                        pointerMode,
                        advancedMode
                    )
                )
            }
        } catch (error: Throwable) {
            onError(error.message ?: "Gesture analysis failed.")
        } finally {
            image.close()
        }
    }

    override fun close() {
        recognizer?.close()
        recognizer = null
    }

    fun resetTracking() {
        interpreter.reset()
        landmarkStabilizer.reset()
    }
}

private fun ImageProxy.toRotatedBitmap(mirror: Boolean): Bitmap {
    val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    planes[0].buffer.rewind()
    source.copyPixelsFromBuffer(planes[0].buffer)

    val rotated = if (imageInfo.rotationDegrees == 0) {
        source
    } else {
        Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) },
            true
        )
    }
    if (!mirror) return rotated
    return Bitmap.createBitmap(
        rotated,
        0,
        0,
        rotated.width,
        rotated.height,
        Matrix().apply { preScale(-1f, 1f) },
        true
    )
}

private fun GestureRecognizerResult.toObservation(
    width: Int,
    height: Int,
    inferenceMs: Long,
    interpreter: GestureInterpreter,
    landmarkStabilizer: LandmarkStabilizer,
    writingMode: Boolean,
    pointerMode: Boolean,
    advancedMode: Boolean
): GestureObservation {
    val allHands = landmarks().map { hand ->
        hand.map { landmark ->
            LandmarkPoint(landmark.x(), landmark.y(), landmark.z())
        }
    }
    val allCategories = gestures().map { candidates ->
        candidates.maxByOrNull { it.score() }
            ?.takeIf { it.score() >= 0.50f }
            ?.categoryName()
            .orEmpty()
    }
    val usableIndices = allHands.indices.filter { index ->
        val hand = allHands[index]
        hand.size >= 21 &&
            hypot(hand[0].x - hand[9].x, hand[0].y - hand[9].y) >=
            MIN_USABLE_HAND_SCALE
    }
    val rawHands = usableIndices.map { allHands[it] }
    val hands = landmarkStabilizer.stabilize(rawHands, advancedMode)
    val categories = usableIndices.map { allCategories.getOrNull(it).orEmpty() }
    val interpretation = interpreter.interpret(
        categories = categories,
        hands = hands,
        now = SystemClock.uptimeMillis(),
        writingMode = writingMode,
        pointerMode = pointerMode,
        advancedMode = advancedMode
    )
    return GestureObservation(
        overlay = GestureOverlayState(hands, width, height),
        detected = interpretation.detected,
        action = interpretation.action,
        isLocked = interpretation.isLocked,
        lockChanged = interpretation.lockChanged,
        cursorActive = interpretation.cursorActive,
        cursorChanged = interpretation.cursorChanged,
        pointerDelta = interpretation.pointerDelta,
        pointerPoint = interpretation.pointerPoint,
        keyboardPoint = interpretation.keyboardPoint,
        writingPoint = interpretation.writingPoint,
        finishWritingStroke = interpretation.finishWritingStroke,
        commitWriting = interpretation.commitWriting,
        backspace = interpretation.backspace,
        inferenceMs = inferenceMs
    )
}

/**
 * Adaptive landmark filtering keeps a still hand quiet enough for fine rub/knob
 * motion while allowing large pointer movements through with very little lag.
 * Hands are paired frame-to-frame by their wrist position, so two-hand order
 * changes from MediaPipe do not make the overlay or gesture state jump.
 */
private class LandmarkStabilizer {
    private var previousHands: List<List<LandmarkPoint>> = emptyList()

    @Synchronized
    fun stabilize(
        currentHands: List<List<LandmarkPoint>>,
        advanced: Boolean
    ): List<List<LandmarkPoint>> {
        if (currentHands.isEmpty()) {
            previousHands = emptyList()
            return emptyList()
        }

        val unusedPrevious = previousHands.indices.toMutableSet()
        val result = currentHands.map { current ->
            val match = unusedPrevious.minByOrNull { previousIndex ->
                val previous = previousHands[previousIndex]
                if (current.isEmpty() || previous.isEmpty()) {
                    Float.MAX_VALUE
                } else {
                    hypot(
                        current[0].x - previous[0].x,
                        current[0].y - previous[0].y
                    )
                }
            }?.takeIf { previousIndex ->
                val previous = previousHands[previousIndex]
                current.isNotEmpty() && previous.isNotEmpty() &&
                    hypot(
                        current[0].x - previous[0].x,
                        current[0].y - previous[0].y
                    ) < 0.28f
            }
            if (match == null) {
                current
            } else {
                unusedPrevious.remove(match)
                val previous = previousHands[match]
                current.mapIndexed { index, point ->
                    val old = previous.getOrNull(index) ?: return@mapIndexed point
                    val motion = hypot(point.x - old.x, point.y - old.y)
                    val alpha = if (advanced) {
                        when {
                            motion > 0.035f -> 0.84f
                            motion > 0.012f -> 0.64f
                            else -> 0.40f
                        }
                    } else {
                        when {
                            motion > 0.035f -> 0.88f
                            else -> 0.68f
                        }
                    }
                    LandmarkPoint(
                        x = old.x + (point.x - old.x) * alpha,
                        y = old.y + (point.y - old.y) * alpha,
                        z = old.z + (point.z - old.z) * alpha
                    )
                }
            }
        }
        previousHands = result
        return result
    }

    @Synchronized
    fun reset() {
        previousHands = emptyList()
    }
}

private class GestureInterpreter {
    private var poseName = ""
    private var poseStartedAt = 0L
    private var poseLatched = false
    private var nextRepeatAt = 0L
    private var openOrigin: LandmarkPoint? = null
    private var openStartedAt = 0L
    private var cooldownUntil = 0L
    private var controlsLocked = false
    private var twoHandsStartedAt = 0L
    private var twoHandsLatched = false
    private var twoHandsReleasedAt = 0L
    private var lastPointerPoint: LandmarkPoint? = null
    private var lastPointerAt = 0L
    private var lastPointerSentAt = 0L
    private var smoothedPointerX = 0f
    private var smoothedPointerY = 0f
    private var wasWriting = false
    private var browserCursorActive = false
    private var cursorToggleStartedAt = 0L
    private var cursorToggleLatched = false
    private var cursorToggleReleasedAt = 0L
    private var commitStartedAt = 0L
    private var commitLatched = false
    private var backspaceStartedAt = 0L
    private var backspaceLatched = false
    private var hasPendingInk = false
    private var suppressCursorToggleUntilRelease = false
    private var knobLastAngle: Float? = null
    private var knobGripStartedAt = 0L
    private var knobAccumulatedAngle = 0f
    private var knobLastSentAt = 0L
    private var rubLastPosition: Float? = null
    private var rubAccumulatedDistance = 0f
    private var rubLastSentAt = 0L

    @Synchronized
    fun interpret(
        categories: List<String>,
        hands: List<List<LandmarkPoint>>,
        now: Long,
        writingMode: Boolean,
        pointerMode: Boolean,
        advancedMode: Boolean
    ): GestureInterpretation {
        val bothHandsExtended = hands.size >= 2 &&
            hands.take(2).withIndex().all { (index, hand) ->
                hand.size >= 21 &&
                    isOpenHand(hand, categories.getOrNull(index).orEmpty())
            }

        var lockChanged = false
        if (bothHandsExtended) {
            twoHandsReleasedAt = 0L
            if (twoHandsStartedAt == 0L) twoHandsStartedAt = now
            if (!twoHandsLatched && now - twoHandsStartedAt >= LOCK_HOLD_MS) {
                controlsLocked = !controlsLocked
                twoHandsLatched = true
                lockChanged = true
                clearHeldPose()
                resetPointer()
            }
        } else {
            twoHandsStartedAt = 0L
            if (twoHandsLatched) {
                if (twoHandsReleasedAt == 0L) twoHandsReleasedAt = now
                if (now - twoHandsReleasedAt >= LOCK_RELEASE_MS) {
                    twoHandsLatched = false
                    twoHandsReleasedAt = 0L
                }
            }
        }

        val browserInteractionAvailable = pointerMode || writingMode
        var cursorChanged = false
        if (!browserInteractionAvailable && browserCursorActive) {
            browserCursorActive = false
            cursorChanged = true
            wasWriting = false
            hasPendingInk = false
            resetPointer()
        }
        if (advancedMode && browserInteractionAvailable && !browserCursorActive) {
            browserCursorActive = true
            cursorChanged = true
            wasWriting = false
            hasPendingInk = false
            resetPointer()
        }

        val pinchHandIndices = hands.indices.filter { index ->
            hands[index].size >= 21 && isIndexPinch(hands[index])
        }
        fun hasPinchSupport(handIndex: Int): Boolean =
            pinchHandIndices.any { it != handIndex }

        val writingHandIndex = hands.indices.firstOrNull { index ->
            hands[index].size >= 21 &&
                isIndexOnly(hands[index]) &&
                hasPinchSupport(index)
        }
        val openHandWithPinchIndex = hands.indices.firstOrNull { index ->
            hands[index].size >= 21 &&
                isOpenHand(hands[index], categories.getOrNull(index).orEmpty()) &&
                hasPinchSupport(index)
        }
        val middleHandWithPinchIndex = hands.indices.firstOrNull { index ->
            hands[index].size >= 21 &&
                isMiddleOnly(hands[index]) &&
                hasPinchSupport(index)
        }

        val rawCursorTogglePose = !advancedMode &&
            browserInteractionAvailable &&
            !controlsLocked &&
            openHandWithPinchIndex != null
        val finishingLetterPose = browserCursorActive &&
            hasPendingInk &&
            openHandWithPinchIndex != null
        if (!rawCursorTogglePose) {
            suppressCursorToggleUntilRelease = false
        }
        val cursorTogglePose = rawCursorTogglePose &&
            !finishingLetterPose &&
            !suppressCursorToggleUntilRelease
        if (cursorTogglePose) {
            cursorToggleReleasedAt = 0L
            if (cursorToggleStartedAt == 0L) cursorToggleStartedAt = now
            if (!cursorToggleLatched &&
                now - cursorToggleStartedAt >= CURSOR_TOGGLE_HOLD_MS
            ) {
                browserCursorActive = !browserCursorActive
                cursorToggleLatched = true
                cursorChanged = true
                wasWriting = false
                hasPendingInk = false
                clearHeldPose()
                resetPointer()
            }
        } else {
            cursorToggleStartedAt = 0L
            if (cursorToggleLatched) {
                if (cursorToggleReleasedAt == 0L) cursorToggleReleasedAt = now
                if (now - cursorToggleReleasedAt >= CURSOR_TOGGLE_RELEASE_MS) {
                    cursorToggleLatched = false
                    cursorToggleReleasedAt = 0L
                }
            }
        }

        if (lockChanged) {
            val finishedStroke = wasWriting
            wasWriting = false
            return GestureInterpretation(
                detected = if (controlsLocked) {
                    "Controls locked — tracking remains active"
                } else {
                    "Controls unlocked — TV commands are active"
                },
                isLocked = controlsLocked,
                lockChanged = true,
                cursorActive = browserCursorActive,
                finishWritingStroke = finishedStroke
            )
        }

        if (cursorChanged) {
            return GestureInterpretation(
                detected = if (browserCursorActive) {
                    "Browser cursor enabled"
                } else {
                    "Browser cursor disabled — screen writing cleared"
                },
                isLocked = controlsLocked,
                cursorActive = browserCursorActive,
                cursorChanged = true
            )
        }

        if (controlsLocked) {
            val finishedStroke = wasWriting
            wasWriting = false
            clearHeldPose()
            resetPointer()
            return GestureInterpretation(
                detected = if (bothHandsExtended) {
                    "Locked — move both open hands away before toggling again"
                } else {
                    "Locked — hands tracked, TV commands paused"
                },
                isLocked = true,
                cursorActive = browserCursorActive,
                finishWritingStroke = finishedStroke
            )
        }

        val primary = hands.firstOrNull()
        val category = categories.firstOrNull().orEmpty()
        if (primary == null || primary.size < 21) {
            val finishedStroke = wasWriting
            wasWriting = false
            resetPointer()
            val fallback = interpretSingle(category, null, now)
            return GestureInterpretation(
                detected = fallback.first,
                action = fallback.second,
                isLocked = false,
                cursorActive = browserCursorActive,
                finishWritingStroke = finishedStroke
            )
        }

        interpretSharedUpDownSigns(category, primary, now)?.let { direction ->
            return GestureInterpretation(
                detected = direction.first,
                action = direction.second,
                isLocked = false,
                cursorActive = browserCursorActive
            )
        }

        if (advancedMode && writingMode) {
            resetPointer()
            val keyboardHand = hands.firstOrNull { hand ->
                hand.size >= 21 && isPointingIndex(hand)
            }
            return GestureInterpretation(
                detected = if (keyboardHand != null) {
                    "Advanced keyboard - hold your index over a key"
                } else {
                    "Advanced keyboard - point one index finger at the phone"
                },
                isLocked = false,
                cursorActive = true,
                keyboardPoint = keyboardHand?.let { hand ->
                    AirInkPoint(hand[8].x, hand[8].y, now)
                }
            )
        }

        if (advancedMode) {
            interpretAdvanced(category, primary, now)?.let { advanced ->
                return GestureInterpretation(
                    detected = advanced.first,
                    action = advanced.second,
                    isLocked = false,
                    cursorActive = browserCursorActive
                )
            }
        } else {
            resetAdvancedGestures()
        }

        val twoHandWritingActive = writingHandIndex != null
        if (!advancedMode &&
            browserCursorActive &&
            (writingMode || hasPendingInk || twoHandWritingActive)
        ) {
            resetPointer()
            val openCommit = hasPendingInk && openHandWithPinchIndex != null
            if (openCommit) {
                backspaceStartedAt = 0L
                backspaceLatched = false
                if (commitStartedAt == 0L) commitStartedAt = now
                val shouldCommit = !commitLatched &&
                    now - commitStartedAt >= LETTER_COMMIT_HOLD_MS
                if (shouldCommit) {
                    commitLatched = true
                    hasPendingInk = false
                    suppressCursorToggleUntilRelease = true
                }
                val finishedStroke = wasWriting
                wasWriting = false
                clearHeldPose()
                return GestureInterpretation(
                    detected = if (shouldCommit) {
                        "Letter committed — ready for the next one"
                    } else {
                        "Open palm detected — hold briefly to commit letter"
                    },
                    isLocked = false,
                    cursorActive = true,
                    finishWritingStroke = finishedStroke,
                    commitWriting = shouldCommit
                )
            } else {
                commitStartedAt = 0L
                commitLatched = false
            }

            val middleBackspace = middleHandWithPinchIndex != null
            if (middleBackspace) {
                if (backspaceStartedAt == 0L) backspaceStartedAt = now
                val shouldBackspace = !backspaceLatched &&
                    now - backspaceStartedAt >= BACKSPACE_HOLD_MS
                if (shouldBackspace) {
                    backspaceLatched = true
                    hasPendingInk = false
                }
                wasWriting = false
                clearHeldPose()
                return GestureInterpretation(
                    detected = if (shouldBackspace) {
                        "Backspace"
                    } else {
                        "Middle finger detected — hold briefly for Backspace"
                    },
                    isLocked = false,
                    cursorActive = true,
                    backspace = shouldBackspace
                )
            } else {
                backspaceStartedAt = 0L
                backspaceLatched = false
            }

            val writer = writingHandIndex?.let(hands::get)
            if (writer != null) {
                wasWriting = true
                hasPendingInk = true
                clearHeldPose()
                return GestureInterpretation(
                    detected = "Drawing letter — live guesses are shown above",
                    isLocked = false,
                    cursorActive = true,
                    writingPoint = AirInkPoint(
                        x = writer[8].x,
                        y = writer[8].y,
                        timeMs = now
                    )
                )
            }
            if (wasWriting) {
                wasWriting = false
                clearHeldPose()
                return GestureInterpretation(
                    detected = "Stroke complete — pause for recognition",
                    isLocked = false,
                    cursorActive = true,
                    finishWritingStroke = true
                )
            }
            clearHeldPose()
            return GestureInterpretation(
                detected = "Write a letter with your index, then open your palm to commit",
                isLocked = false,
                cursorActive = true
            )
        } else {
            commitStartedAt = 0L
            commitLatched = false
            backspaceStartedAt = 0L
            backspaceLatched = false
            if (wasWriting) {
                wasWriting = false
                clearHeldPose()
                return GestureInterpretation(
                    detected = "Handwriting finished",
                    isLocked = false,
                    cursorActive = browserCursorActive,
                    finishWritingStroke = true
                )
            }
            if (pointerMode && browserCursorActive &&
                if (advancedMode) isPointingIndex(primary) else isIndexOnly(primary)
            ) {
                val tip = primary[8]
                val pointer = pointerDelta(tip, now, advancedMode)
                clearHeldPose()
                return GestureInterpretation(
                    detected = "Browser pointer tracking",
                    isLocked = false,
                    cursorActive = true,
                    pointerDelta = pointer,
                    pointerPoint = AirInkPoint(tip.x, tip.y, now)
                )
            }
            resetPointer()
        }

        val result = interpretSingle(category, primary, now)
        return GestureInterpretation(
            detected = result.first,
            action = result.second,
            isLocked = false,
            cursorActive = browserCursorActive
        )
    }

    private fun interpretSharedUpDownSigns(
        category: String,
        landmarks: List<LandmarkPoint>,
        now: Long
    ): Pair<String, GestureAction?>? {
        val fingers = fingerState(landmarks)
        val isThree = fingers.index && fingers.middle && fingers.ring && !fingers.pinky
        val isPeace = !isThree && (
            category == "Victory" ||
                (fingers.index && fingers.middle && !fingers.ring && !fingers.pinky)
            )
        if (!isPeace && !isThree) {
            return null
        }

        resetAdvancedGestures()
        return if (isPeace) {
            repeatingPose(
                pose = "PeaceUp",
                now = now,
                holdMs = 220L,
                repeatMs = 310L,
                waitingText = "Peace sign detected - hold for Up",
                action = GestureAction("Up", listOf(RemoteCommand.KEY_UP))
            )
        } else {
            repeatingPose(
                pose = "ThreeDown",
                now = now,
                holdMs = 220L,
                repeatMs = 310L,
                waitingText = "Three-finger sign detected - hold for Down",
                action = GestureAction("Down", listOf(RemoteCommand.KEY_DOWN))
            )
        }
    }

    private fun interpretAdvanced(
        category: String,
        landmarks: List<LandmarkPoint>,
        now: Long
    ): Pair<String, GestureAction?>? {
        val fingers = fingerState(landmarks)
        val palmSize = distance(landmarks[0], landmarks[9]).coerceAtLeast(0.01f)
        val thumbOnIndex = distanceToSegment(
            point = landmarks[4],
            start = landmarks[6],
            end = landmarks[8]
        ) / palmSize
        val rubPose = fingers.index && !fingers.middle && !fingers.ring &&
            !fingers.pinky && thumbOnIndex < 0.38f
        if (rubPose) {
            clearHeldPose()
            resetKnob()
            val axisX = landmarks[8].x - landmarks[6].x
            val axisY = landmarks[8].y - landmarks[6].y
            val axisLength = hypot(axisX, axisY).coerceAtLeast(0.01f)
            val thumbX = landmarks[4].x - landmarks[6].x
            val thumbY = landmarks[4].y - landmarks[6].y
            val rubPosition = (axisX * thumbY - axisY * thumbX) /
                (axisLength * palmSize)
            val previousRub = rubLastPosition
            rubLastPosition = rubPosition
            if (previousRub == null) {
                return "Index rub ready - move thumb left or right" to null
            }
            val change = rubPosition - previousRub
            if (abs(change) < 0.32f) {
                rubAccumulatedDistance =
                    rubAccumulatedDistance * 0.70f + change
            }
            if (abs(rubAccumulatedDistance) >= RUB_STEP_DISTANCE &&
                now - rubLastSentAt >= ADVANCED_GESTURE_INTERVAL_MS
            ) {
                val movedRight = rubAccumulatedDistance > 0f
                rubAccumulatedDistance = 0f
                rubLastSentAt = now
                val action = if (movedRight) {
                    GestureAction("Index rub right", listOf(RemoteCommand.KEY_RIGHT))
                } else {
                    GestureAction("Index rub left", listOf(RemoteCommand.KEY_LEFT))
                }
                return "${action.label} - keep rubbing to repeat" to action
            }
            return "Index rub tracking - slide the thumb across the index" to null
        }

        // Keep the original thumb volume gestures available in Advanced mode.
        // They must fall through to interpretSingle instead of being mistaken
        // for a closed volume-knob grip.
        val thumbOnlyPose = fingers.thumb && !fingers.index && !fingers.middle &&
            !fingers.ring && !fingers.pinky
        if (thumbOnlyPose || category == "Thumb_Up" || category == "Thumb_Down") {
            resetAdvancedGestures()
            return null
        }

        val closedGrip = category == "Closed_Fist" ||
            (!fingers.thumb && !fingers.index && !fingers.middle &&
                !fingers.ring && !fingers.pinky)
        if (closedGrip) {
            clearHeldPose()
            resetRub()
            val palmAngle = atan2(
                landmarks[17].y - landmarks[5].y,
                landmarks[17].x - landmarks[5].x
            )
            if (knobGripStartedAt == 0L) {
                knobGripStartedAt = now
                knobLastAngle = palmAngle
                return "Volume knob grip detected - hold, then twist" to null
            }
            val previousAngle = knobLastAngle
            knobLastAngle = palmAngle
            if (previousAngle == null || now - knobGripStartedAt < KNOB_GRIP_SETTLE_MS) {
                return "Volume knob calibrated - twist naturally" to null
            }
            val change = normalizeAngle(palmAngle - previousAngle)
            if (abs(change) < 0.42f) {
                knobAccumulatedAngle =
                    knobAccumulatedAngle * 0.84f + change
            }
            if (abs(knobAccumulatedAngle) >= KNOB_STEP_RADIANS &&
                now - knobLastSentAt >= ADVANCED_GESTURE_INTERVAL_MS
            ) {
                val clockwise = knobAccumulatedAngle > 0f
                knobAccumulatedAngle = 0f
                knobLastSentAt = now
                val action = if (clockwise) {
                    GestureAction("Knob clockwise - volume up", listOf(RemoteCommand.KEY_VOLUP))
                } else {
                    GestureAction(
                        "Knob counter-clockwise - volume down",
                        listOf(RemoteCommand.KEY_VOLDOWN)
                    )
                }
                return "${action.label} - keep twisting to repeat" to action
            }
            return "Volume knob tracking - twist the closed grip" to null
        }

        resetAdvancedGestures()
        return null
    }

    private fun resetAdvancedGestures() {
        resetKnob()
        resetRub()
    }

    private fun resetKnob() {
        knobLastAngle = null
        knobGripStartedAt = 0L
        knobAccumulatedAngle = 0f
    }

    private fun resetRub() {
        rubLastPosition = null
        rubAccumulatedDistance = 0f
    }

    private fun interpretSingle(
        category: String,
        landmarks: List<LandmarkPoint>?,
        now: Long
    ): Pair<String, GestureAction?> {
        if (landmarks == null || landmarks.size < 21) {
            reset()
            return "No hand detected — hold one hand in view" to null
        }

        val pinchRatio = distance(landmarks[4], landmarks[8]) /
            distance(landmarks[0], landmarks[9]).coerceAtLeast(0.01f)
        if (pinchRatio < 0.38f) {
            return heldPose(
                pose = "Pinch",
                now = now,
                holdMs = 180L,
                waitingText = "Pinch detected — hold briefly for OK",
                action = GestureAction("OK / click", listOf(RemoteCommand.KEY_ENTER))
            )
        }

        val pinkyPinchRatio = distance(landmarks[4], landmarks[20]) /
            distance(landmarks[0], landmarks[9]).coerceAtLeast(0.01f)
        if (pinkyPinchRatio < 0.40f) {
            return heldPose(
                pose = "PinkyPinch",
                now = now,
                holdMs = 180L,
                waitingText = "Thumb + pinky pinch detected — hold for double OK",
                action = GestureAction(
                    label = "Double OK / double click",
                    commands = listOf(RemoteCommand.KEY_ENTER, RemoteCommand.KEY_ENTER),
                    delayBetweenCommandsMs = 1000L
                )
            )
        }

        val fingers = fingerState(landmarks)
        val palmWidth = distance(landmarks[5], landmarks[17]).coerceAtLeast(0.01f)
        val thumbSpreadRatio = distance(landmarks[4], landmarks[9]) / palmWidth
        val thumbOpenForHome = thumbSpreadRatio > 0.72f

        if (thumbOpenForHome && fingers.index && fingers.pinky &&
            !fingers.middle && !fingers.ring
        ) {
            return heldPose(
                pose = "Home",
                now = now,
                holdMs = 240L,
                waitingText = "Home gesture detected — hold briefly",
                action = GestureAction("Home", listOf(RemoteCommand.KEY_HOME))
            )
        }

        if (!thumbOpenForHome && fingers.index && fingers.pinky &&
            !fingers.middle && !fingers.ring
        ) {
            return heldPose(
                pose = "Back",
                now = now,
                holdMs = 240L,
                waitingText = "Back gesture detected — hold briefly",
                action = GestureAction("Back", listOf(RemoteCommand.KEY_RETURN))
            )
        }

        if (fingers.thumb && fingers.index &&
            !fingers.middle && !fingers.ring && !fingers.pinky
        ) {
            return repeatingPose(
                pose = "Left",
                now = now,
                holdMs = 300L,
                waitingText = "Left gesture detected — keep holding",
                action = GestureAction("Left", listOf(RemoteCommand.KEY_LEFT))
            )
        }

        if (fingers.thumb && fingers.pinky &&
            !fingers.index && !fingers.middle && !fingers.ring
        ) {
            return repeatingPose(
                pose = "Right",
                now = now,
                holdMs = 300L,
                waitingText = "Right gesture detected — keep holding",
                action = GestureAction("Right", listOf(RemoteCommand.KEY_RIGHT))
            )
        }

        if (fingers.thumb && !fingers.index && !fingers.middle &&
            !fingers.ring && !fingers.pinky
        ) {
            val thumbDirection = direction(landmarks[2], landmarks[4])
            if (thumbDirection == PointDirection.LEFT ||
                thumbDirection == PointDirection.RIGHT
            ) {
                return repeatingPose(
                    pose = "SideThumbVolumeDown",
                    now = now,
                    holdMs = 300L,
                    waitingText = "Sideways thumb detected — keep holding for volume down",
                    action = GestureAction(
                        "Volume down",
                        listOf(RemoteCommand.KEY_VOLDOWN)
                    )
                )
            }
            if (thumbDirection == PointDirection.UP || category == "Thumb_Up") {
                return repeatingPose(
                    pose = "ThumbUpVolumeUp",
                    now = now,
                    holdMs = 300L,
                    waitingText = "Thumbs up detected — keep holding for volume up",
                    action = GestureAction(
                        "Volume up",
                        listOf(RemoteCommand.KEY_VOLUP)
                    )
                )
            }
        }

        val indexDirection = direction(landmarks[6], landmarks[8])
        if ((category == "Pointing_Up" ||
                (fingers.index && !fingers.thumb && !fingers.middle && !fingers.ring && !fingers.pinky)) &&
            indexDirection == PointDirection.UP
        ) {
            return heldPose(
                pose = "PointUp",
                now = now,
                holdMs = 200L,
                waitingText = "Index pointing up detected — hold briefly",
                action = GestureAction("Up", listOf(RemoteCommand.KEY_UP))
            )
        }

        val pinkyDirection = direction(landmarks[18], landmarks[20])
        if (fingers.pinky && !fingers.thumb && !fingers.index && !fingers.middle && !fingers.ring &&
            pinkyDirection == PointDirection.UP
        ) {
            return heldPose(
                pose = "PinkyUp",
                now = now,
                holdMs = 200L,
                waitingText = "Pinky pointing up detected — hold briefly",
                action = GestureAction("Down", listOf(RemoteCommand.KEY_DOWN))
            )
        }

        if (category == "ILoveYou" && thumbOpenForHome) {
            return heldPose(
                pose = "Home",
                now = now,
                holdMs = 240L,
                waitingText = "Home gesture detected — hold briefly",
                action = GestureAction("Home", listOf(RemoteCommand.KEY_HOME))
            )
        }

        when (category) {
            "Thumb_Up" -> {
                return repeatingPose(
                    pose = category,
                    now = now,
                    holdMs = 300L,
                    waitingText = "Thumbs up detected — keep holding for volume up",
                    action = GestureAction("Volume up", listOf(RemoteCommand.KEY_VOLUP))
                )
            }

            "Thumb_Down" -> {
                return repeatingPose(
                    pose = category,
                    now = now,
                    holdMs = 300L,
                    waitingText = "Thumbs down detected — keep holding for volume down",
                    action = GestureAction("Volume down", listOf(RemoteCommand.KEY_VOLDOWN))
                )
            }

            "Open_Palm" -> {
                clearHeldPose()
                val center = palmCenter(landmarks)
                val origin = openOrigin
                if (origin == null || now - openStartedAt > 1800L) {
                    openOrigin = center
                    openStartedAt = now
                    return "Open palm detected — swipe up, down, left, or right" to null
                }
                val dx = center.x - origin.x
                val dy = center.y - origin.y
                val palmSize = distance(landmarks[0], landmarks[9])
                val threshold = max(0.105f, palmSize * 0.9f)
                if (now >= cooldownUntil && max(abs(dx), abs(dy)) >= threshold) {
                    val action = if (abs(dx) > abs(dy)) {
                        if (dx > 0f) GestureAction("Right", listOf(RemoteCommand.KEY_RIGHT))
                        else GestureAction("Left", listOf(RemoteCommand.KEY_LEFT))
                    } else {
                        if (dy > 0f) GestureAction("Down", listOf(RemoteCommand.KEY_DOWN))
                        else GestureAction("Up", listOf(RemoteCommand.KEY_UP))
                    }
                    openOrigin = center
                    openStartedAt = now
                    cooldownUntil = now + 650L
                    return "Open-palm swipe detected: ${action.label}" to action
                }
                return "Open palm detected — move farther to swipe" to null
            }
        }

        clearHeldPose()
        openOrigin = null
        val friendly = category
            .takeUnless { it.isBlank() || it == "None" }
            ?.replace('_', ' ')
            ?: "Hand"
        return "$friendly detected — use a supported gesture" to null
    }

    private fun heldPose(
        pose: String,
        now: Long,
        holdMs: Long,
        waitingText: String,
        action: GestureAction
    ): Pair<String, GestureAction?> {
        openOrigin = null
        if (poseName != pose) {
            poseName = pose
            poseStartedAt = now
            poseLatched = false
        }
        if (!poseLatched && now - poseStartedAt >= holdMs && now >= cooldownUntil) {
            poseLatched = true
            cooldownUntil = now + 650L
            return "${action.label} gesture recognized" to action
        }
        return if (poseLatched) {
            "${action.label} sent — release your hand to repeat"
        } else {
            waitingText
        } to null
    }

    private fun repeatingPose(
        pose: String,
        now: Long,
        holdMs: Long,
        repeatMs: Long = 420L,
        waitingText: String,
        action: GestureAction
    ): Pair<String, GestureAction?> {
        openOrigin = null
        if (poseName != pose) {
            poseName = pose
            poseStartedAt = now
            poseLatched = false
            nextRepeatAt = now + holdMs
        }
        if (now >= nextRepeatAt) {
            poseLatched = true
            nextRepeatAt = now + repeatMs
            return "${action.label} — keep holding to repeat" to action
        }
        return if (poseLatched) {
            "${action.label} — holding, next step coming"
        } else {
            waitingText
        } to null
    }

    private fun pointerDelta(
        point: LandmarkPoint,
        now: Long,
        advanced: Boolean
    ): PointerDelta? {
        val previous = lastPointerPoint
        val previousAt = lastPointerAt
        lastPointerPoint = point
        lastPointerAt = now
        if (previous == null || now - previousAt > POINTER_RESET_MS) {
            smoothedPointerX = 0f
            smoothedPointerY = 0f
            return null
        }

        val xGain = if (advanced) ADVANCED_POINTER_X_GAIN else POINTER_X_GAIN
        val yGain = if (advanced) ADVANCED_POINTER_Y_GAIN else POINTER_Y_GAIN
        val smoothing = if (advanced) 0.82f else 0.68f
        val rawX = (point.x - previous.x) * xGain
        val rawY = (point.y - previous.y) * yGain
        smoothedPointerX = smoothedPointerX * (1f - smoothing) + rawX * smoothing
        smoothedPointerY = smoothedPointerY * (1f - smoothing) + rawY * smoothing
        val sendInterval = if (advanced) {
            ADVANCED_POINTER_SEND_INTERVAL_MS
        } else {
            POINTER_SEND_INTERVAL_MS
        }
        if (now - lastPointerSentAt < sendInterval) return null

        val maxStep = if (advanced) ADVANCED_POINTER_MAX_STEP else POINTER_MAX_STEP
        var deltaX = smoothedPointerX.roundToInt().coerceIn(-maxStep, maxStep)
        var deltaY = smoothedPointerY.roundToInt().coerceIn(-maxStep, maxStep)
        if (abs(deltaX) < POINTER_DEAD_ZONE) deltaX = 0
        if (abs(deltaY) < POINTER_DEAD_ZONE) deltaY = 0
        if (deltaX == 0 && deltaY == 0) return null
        lastPointerSentAt = now
        return PointerDelta(deltaX, deltaY)
    }

    private fun resetPointer() {
        lastPointerPoint = null
        lastPointerAt = 0L
        lastPointerSentAt = 0L
        smoothedPointerX = 0f
        smoothedPointerY = 0f
    }

    private fun clearHeldPose() {
        poseName = ""
        poseStartedAt = 0L
        poseLatched = false
        nextRepeatAt = 0L
    }

    @Synchronized
    fun reset() {
        clearHeldPose()
        openOrigin = null
        openStartedAt = 0L
        cooldownUntil = 0L
        resetPointer()
        wasWriting = false
        browserCursorActive = false
        cursorToggleStartedAt = 0L
        cursorToggleLatched = false
        cursorToggleReleasedAt = 0L
        hasPendingInk = false
        resetAdvancedGestures()
    }

    private fun palmCenter(points: List<LandmarkPoint>): LandmarkPoint {
        val indices = intArrayOf(0, 5, 9, 13, 17)
        return LandmarkPoint(
            x = indices.sumOf { points[it].x.toDouble() }.toFloat() / indices.size,
            y = indices.sumOf { points[it].y.toDouble() }.toFloat() / indices.size
        )
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float =
        hypot(a.x - b.x, a.y - b.y)

    private fun distanceToSegment(
        point: LandmarkPoint,
        start: LandmarkPoint,
        end: LandmarkPoint
    ): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.000001f) return distance(point, start)
        val projection = (
            ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
        ).coerceIn(0f, 1f)
        return hypot(
            point.x - (start.x + projection * dx),
            point.y - (start.y + projection * dy)
        )
    }

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > Math.PI) normalized -= (Math.PI * 2.0).toFloat()
        while (normalized < -Math.PI) normalized += (Math.PI * 2.0).toFloat()
        return normalized
    }

    private fun isIndexOnly(points: List<LandmarkPoint>): Boolean {
        val fingers = fingerState(points)
        return fingers.index && !fingers.thumb && !fingers.middle &&
            !fingers.ring && !fingers.pinky
    }

    private fun isPointingIndex(points: List<LandmarkPoint>): Boolean {
        val fingers = fingerState(points)
        return fingers.index && !fingers.middle && !fingers.ring && !fingers.pinky
    }

    private fun isMiddleOnly(points: List<LandmarkPoint>): Boolean {
        val fingers = fingerState(points)
        return fingers.middle && !fingers.thumb && !fingers.index &&
            !fingers.ring && !fingers.pinky
    }

    private fun isOpenHand(points: List<LandmarkPoint>, category: String): Boolean {
        if (category == "Open_Palm") return true
        val fingers = fingerState(points)
        return fingers.thumb && fingers.index && fingers.middle &&
            fingers.ring && fingers.pinky
    }

    private fun isIndexPinch(points: List<LandmarkPoint>): Boolean {
        val palmSize = distance(points[0], points[9]).coerceAtLeast(0.01f)
        return distance(points[4], points[8]) / palmSize < 0.40f
    }

    private fun fingerState(points: List<LandmarkPoint>): FingerState {
        fun extended(tip: Int, pip: Int): Boolean =
            distance(points[tip], points[0]) > distance(points[pip], points[0]) * 1.14f

        val thumbExtended =
            distance(points[4], points[5]) > distance(points[3], points[5]) * 1.12f &&
                distance(points[4], points[0]) > distance(points[2], points[0]) * 1.08f
        return FingerState(
            thumb = thumbExtended,
            index = extended(8, 6),
            middle = extended(12, 10),
            ring = extended(16, 14),
            pinky = extended(20, 18)
        )
    }

    private fun direction(base: LandmarkPoint, tip: LandmarkPoint): PointDirection {
        val dx = tip.x - base.x
        val dy = tip.y - base.y
        return when {
            abs(dx) > abs(dy) * 1.15f -> {
                if (dx < 0f) PointDirection.LEFT else PointDirection.RIGHT
            }
            abs(dy) > abs(dx) * 0.70f -> {
                if (dy < 0f) PointDirection.UP else PointDirection.DOWN
            }
            else -> PointDirection.UNKNOWN
        }
    }

    private companion object {
        const val LOCK_HOLD_MS = 650L
        const val LOCK_RELEASE_MS = 450L
        const val CURSOR_TOGGLE_HOLD_MS = 380L
        const val CURSOR_TOGGLE_RELEASE_MS = 350L
        const val LETTER_COMMIT_HOLD_MS = 260L
        const val BACKSPACE_HOLD_MS = 240L
        const val POINTER_SEND_INTERVAL_MS = 42L
        const val POINTER_RESET_MS = 260L
        const val POINTER_X_GAIN = 2600f
        const val POINTER_Y_GAIN = 2200f
        const val POINTER_MAX_STEP = 180
        const val POINTER_DEAD_ZONE = 3
        const val ADVANCED_POINTER_SEND_INTERVAL_MS = 30L
        const val ADVANCED_POINTER_X_GAIN = 5200f
        const val ADVANCED_POINTER_Y_GAIN = 4600f
        const val ADVANCED_POINTER_MAX_STEP = 320
        const val ADVANCED_GESTURE_INTERVAL_MS = 125L
        const val KNOB_GRIP_SETTLE_MS = 180L
        const val KNOB_STEP_RADIANS = 0.115f
        const val RUB_STEP_DISTANCE = 0.060f
    }
}

private data class FingerState(
    val thumb: Boolean,
    val index: Boolean,
    val middle: Boolean,
    val ring: Boolean,
    val pinky: Boolean
)

private enum class PointDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    UNKNOWN
}

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20,
    0 to 17
)

package com.focuswinecellars.kiosk.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.focuswinecellars.kiosk.CameraUiState
import com.focuswinecellars.kiosk.camera.CaptureManager
import com.focuswinecellars.kiosk.camera.ImageAnalyzer
import com.focuswinecellars.kiosk.camera.QualityStatus
import com.focuswinecellars.kiosk.camera.CameraController
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

@Composable
fun CameraScreen(
    state: CameraUiState,
    onImageCaptured: (ByteArray) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var qualityStatus by remember {
        mutableStateOf(
            QualityStatus(
                brightness = 0f,
                motion = 0f,
                focus = 0f,
                isBright = false,
                isStable = false,
                isFocused = false
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        val permission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val autoCaptureRef = remember { AtomicReference<() -> Unit>({}) }
    val recognizing by rememberUpdatedState(state.isRecognizing)

    val analyzer = remember {
        ImageAnalyzer(
            onQualityUpdate = { status ->
                mainExecutor.execute { qualityStatus = status }
            },
            onAutoCapture = { autoCaptureRef.get().invoke() }
        )
    }

    val cameraController = remember {
        CameraController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            analyzer = analyzer,
            onCameraError = { message -> cameraError = message }
        )
    }

    val frameWidthRatio = 0.72f
    val frameHeightRatio = 0.42f

    val captureManager = remember {
        CaptureManager(
            context = context,
            imageCaptureProvider = { cameraController.imageCapture },
            frameWidthRatio = frameWidthRatio,
            frameHeightRatio = frameHeightRatio,
            onImageCaptured = onImageCaptured,
            onError = { ex -> cameraError = ex.message ?: "Image capture failed." }
        )
    }

    SideEffect {
        autoCaptureRef.set {
            if (!recognizing) {
                captureManager.requestCapture()
            }
        }
    }

    LaunchedEffect(state.isRecognizing) {
        analyzer.setActive(!state.isRecognizing)
        if (!state.isRecognizing) {
            analyzer.reset()
            captureManager.reset()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            cameraError = null
            cameraController.bind(previewView)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.unbind()
            cameraController.shutdown()
            captureManager.shutdown()
        }
    }

    val guidanceText = when {
        state.isRecognizing -> "Recognizing label..."
        !qualityStatus.isBright -> "Increase lighting"
        !qualityStatus.isStable -> "Hold the device steady"
        !qualityStatus.isFocused -> "Move closer to the label"
        else -> "Capturing..."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            !hasPermission -> {
                PermissionRequest(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
            cameraError != null -> {
                CameraErrorMessage(
                    message = cameraError.orEmpty(),
                    onRetry = {
                        cameraError = null
                        analyzer.reset()
                        captureManager.reset()
                        scope.launch { cameraController.bind(previewView) }
                        onRetry()
                    }
                )
            }
            else -> {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                AlignmentOverlay(frameWidthRatio, frameHeightRatio)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hold the wine label inside the frame",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.isRecognizing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = guidanceText,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Text(
                            text = guidanceText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (state.errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.errorMessage,
                            color = Color(0xFFFFB4A9),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (state.isRecognizing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}

@Composable
private fun AlignmentOverlay(frameWidthRatio: Float, frameHeightRatio: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = 0.4f))

                val frameWidth = size.width * frameWidthRatio
                val frameHeight = size.height * frameHeightRatio
                val left = (size.width - frameWidth) / 2f
                val top = (size.height - frameHeight) / 2f
                val cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(frameWidth, frameHeight),
                    cornerRadius = cornerRadius,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(frameWidth, frameHeight),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
    )
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Camera permission is required.",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(text = "Grant Camera Access")
        }
    }
}

@Composable
private fun CameraErrorMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Text(text = "Retry Camera")
        }
    }
}

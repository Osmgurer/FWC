package com.focuswinecellars.kiosk.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val analyzer: ImageAnalysis.Analyzer,
    private val onCameraError: (String) -> Unit
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    var imageCapture: ImageCapture? = null
        private set

    suspend fun bind(previewView: PreviewView) {
        try {
            val provider = getCameraProvider(context)
            cameraProvider = provider

            if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                onCameraError("Front camera not available on this device.")
                return
            }

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                capture,
                analysis
            )
            imageCapture = capture
        } catch (ex: Exception) {
            onCameraError("Camera failed to start.")
        }
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        imageCapture = null
    }

    fun shutdown() {
        analysisExecutor.shutdown()
    }

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        continuation.resume(cameraProviderFuture.get())
                    } catch (ex: Exception) {
                        continuation.resumeWithException(ex)
                    }
                },
                mainExecutor(context)
            )
        }
    }

    private fun mainExecutor(context: Context): Executor {
        return ContextCompat.getMainExecutor(context)
    }
}

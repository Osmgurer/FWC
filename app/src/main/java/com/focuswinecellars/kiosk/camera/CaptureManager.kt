package com.focuswinecellars.kiosk.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class CaptureManager(
    context: Context,
    private val imageCaptureProvider: () -> ImageCapture?,
    private val frameWidthRatio: Float,
    private val frameHeightRatio: Float,
    private val onImageCaptured: (ByteArray) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val isCapturing = AtomicBoolean(false)
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val processingExecutor = Executors.newSingleThreadExecutor()

    fun requestCapture() {
        if (!isCapturing.compareAndSet(false, true)) return
        val capture = imageCaptureProvider()
        if (capture == null) {
            isCapturing.set(false)
            onError(IllegalStateException("ImageCapture is not ready."))
            return
        }
        capture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processingExecutor.execute {
                        try {
                            val bytes = processImage(image)
                            mainExecutor.execute { onImageCaptured(bytes) }
                        } catch (ex: Exception) {
                            mainExecutor.execute { onError(ex) }
                        } finally {
                            image.close()
                            isCapturing.set(false)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing.set(false)
                    onError(exception)
                }
            }
        )
    }

    fun reset() {
        isCapturing.set(false)
    }

    fun shutdown() {
        processingExecutor.shutdown()
    }

    private fun processImage(image: ImageProxy): ByteArray {
        val bitmap = imageProxyToBitmap(image)
        val rotated = if (image.imageInfo.rotationDegrees != 0) {
            bitmap.rotate(image.imageInfo.rotationDegrees)
        } else {
            bitmap
        }
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        val cropped = rotated.centerCrop(frameWidthRatio, frameHeightRatio)
        if (cropped !== rotated) {
            rotated.recycle()
        }
        val bytes = cropped.toJpegBytes()
        cropped.recycle()
        return bytes
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, outputStream)
        val jpegBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)
        var outputOffset = 0

        val yRow = ByteArray(yRowStride)
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            if (yPixelStride == 1) {
                yBuffer.get(nv21, outputOffset, width)
                outputOffset += width
            } else {
                yBuffer.get(yRow, 0, yRowStride)
                var col = 0
                var yIndex = 0
                while (col < width) {
                    nv21[outputOffset] = yRow[yIndex]
                    outputOffset += 1
                    col += 1
                    yIndex += yPixelStride
                }
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uRow = ByteArray(uRowStride)
        val vRow = ByteArray(vRowStride)
        var uvOffset = width * height

        for (row in 0 until chromaHeight) {
            uBuffer.position(row * uRowStride)
            vBuffer.position(row * vRowStride)
            uBuffer.get(uRow, 0, uRowStride)
            vBuffer.get(vRow, 0, vRowStride)

            var col = 0
            var uIndex = 0
            var vIndex = 0
            while (col < chromaWidth) {
                nv21[uvOffset] = vRow[vIndex]
                nv21[uvOffset + 1] = uRow[uIndex]
                uvOffset += 2
                col += 1
                uIndex += uPixelStride
                vIndex += vPixelStride
            }
        }

        return nv21
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.centerCrop(widthRatio: Float, heightRatio: Float): Bitmap {
        val cropWidth = (width * widthRatio).roundToInt().coerceIn(1, width)
        val cropHeight = (height * heightRatio).roundToInt().coerceIn(1, height)
        val left = (width - cropWidth) / 2
        val top = (height - cropHeight) / 2
        return Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.use {
            compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        return stream.toByteArray()
    }
}

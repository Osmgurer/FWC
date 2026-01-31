package com.focuswinecellars.kiosk.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.min

data class AnalyzerSettings(
    val brightnessThreshold: Float = 0.35f,
    val motionThreshold: Float = 6.0f,
    val focusThreshold: Float = 12.0f,
    val requiredGoodFrames: Int = 5,
    val sampleStep: Int = 8
)

data class QualityStatus(
    val brightness: Float,
    val motion: Float,
    val focus: Float,
    val isBright: Boolean,
    val isStable: Boolean,
    val isFocused: Boolean
) {
    val isGood: Boolean
        get() = isBright && isStable && isFocused
}

class ImageAnalyzer(
    private val settings: AnalyzerSettings = AnalyzerSettings(),
    private val onQualityUpdate: (QualityStatus) -> Unit,
    private val onAutoCapture: () -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    private var isActive: Boolean = true
    private var stableFrameCount: Int = 0
    private var captureTriggered: Boolean = false
    private var lastFrame: SampledFrame? = null

    fun setActive(active: Boolean) {
        isActive = active
        if (!active) {
            reset()
        }
    }

    fun reset() {
        stableFrameCount = 0
        captureTriggered = false
        lastFrame = null
    }

    override fun analyze(image: ImageProxy) {
        if (!isActive) {
            image.close()
            return
        }

        val sampled = sampleLuma(image, settings.sampleStep)
        val brightness = computeBrightness(sampled)
        val motion = lastFrame?.let { computeMotion(sampled, it) } ?: 0f
        val focus = computeFocus(sampled)

        val isBright = brightness >= settings.brightnessThreshold
        val isStable = motion <= settings.motionThreshold
        val isFocused = focus >= settings.focusThreshold

        val status = QualityStatus(
            brightness = brightness,
            motion = motion,
            focus = focus,
            isBright = isBright,
            isStable = isStable,
            isFocused = isFocused
        )
        onQualityUpdate(status)

        if (status.isGood) {
            stableFrameCount += 1
        } else {
            stableFrameCount = 0
        }

        if (!captureTriggered && stableFrameCount >= settings.requiredGoodFrames) {
            captureTriggered = true
            onAutoCapture()
        }

        lastFrame = sampled
        image.close()
    }

    private fun computeBrightness(frame: SampledFrame): Float {
        var sum = 0L
        for (value in frame.data) {
            sum += value
        }
        val average = if (frame.data.isEmpty()) 0f else sum.toFloat() / frame.data.size
        return average / 255f
    }

    private fun computeMotion(current: SampledFrame, previous: SampledFrame): Float {
        val count = min(current.data.size, previous.data.size)
        if (count == 0) return 0f
        var sum = 0L
        for (index in 0 until count) {
            sum += abs(current.data[index] - previous.data[index])
        }
        return sum.toFloat() / count
    }

    private fun computeFocus(frame: SampledFrame): Float {
        if (frame.width < 3 || frame.height < 3) return 0f
        var sum = 0L
        var count = 0
        for (y in 1 until frame.height - 1) {
            for (x in 1 until frame.width - 1) {
                val center = frame.data[y * frame.width + x]
                val laplacian = -4 * center +
                    frame.data[y * frame.width + x - 1] +
                    frame.data[y * frame.width + x + 1] +
                    frame.data[(y - 1) * frame.width + x] +
                    frame.data[(y + 1) * frame.width + x]
                sum += abs(laplacian)
                count += 1
            }
        }
        return if (count == 0) 0f else sum.toFloat() / count
    }

    private fun sampleLuma(image: ImageProxy, step: Int): SampledFrame {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val sampleWidth = (width + step - 1) / step
        val sampleHeight = (height + step - 1) / step
        val samples = IntArray(sampleWidth * sampleHeight)
        var index = 0

        for (y in 0 until height step step) {
            val rowOffset = y * rowStride
            for (x in 0 until width step step) {
                val offset = rowOffset + x * pixelStride
                samples[index] = buffer.get(offset).toInt() and 0xFF
                index += 1
            }
        }
        return SampledFrame(
            data = samples,
            width = sampleWidth,
            height = sampleHeight
        )
    }

    private data class SampledFrame(
        val data: IntArray,
        val width: Int,
        val height: Int
    )
}

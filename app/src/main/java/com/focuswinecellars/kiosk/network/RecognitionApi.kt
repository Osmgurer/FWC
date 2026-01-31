package com.focuswinecellars.kiosk.network

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class RecognitionResult(
    val wineName: String,
    val producer: String,
    val vintage: String?,
    val confidence: ConfidenceLevel
)

interface RecognitionApi {
    suspend fun recognize(imageBytes: ByteArray): RecognitionResult
}

fun ConfidenceLevel.displayLabel(): String {
    return when (this) {
        ConfidenceLevel.HIGH -> "High"
        ConfidenceLevel.MEDIUM -> "Medium"
        ConfidenceLevel.LOW -> "Low"
    }
}

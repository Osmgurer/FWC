package com.focuswinecellars.kiosk.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MockRecognitionService(
    private val delayMs: Long = 650L
) : RecognitionApi {
    override suspend fun recognize(imageBytes: ByteArray): RecognitionResult {
        return withContext(Dispatchers.IO) {
            delay(delayMs)
            val json = JSONObject(MOCK_RESPONSE)
            val confidence = ConfidenceLevel.valueOf(json.getString("confidence"))
            RecognitionResult(
                wineName = json.getString("wine_name"),
                producer = json.getString("producer"),
                vintage = json.optString("vintage").takeIf { it.isNotBlank() },
                confidence = confidence
            )
        }
    }

    private companion object {
        const val MOCK_RESPONSE = """
            {
              "wine_name": "Château Margaux",
              "producer": "Margaux",
              "vintage": "2015",
              "confidence": "HIGH"
            }
        """
    }
}

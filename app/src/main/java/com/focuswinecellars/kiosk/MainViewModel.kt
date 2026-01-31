package com.focuswinecellars.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focuswinecellars.kiosk.network.MockRecognitionService
import com.focuswinecellars.kiosk.network.RecognitionApi
import com.focuswinecellars.kiosk.network.RecognitionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Operation {
    ADD,
    REMOVE
}

data class CameraUiState(
    val operation: Operation,
    val isRecognizing: Boolean = false,
    val errorMessage: String? = null
)

sealed interface UiState {
    data object Idle : UiState
    data class Camera(val state: CameraUiState) : UiState
    data class Result(val operation: Operation, val result: RecognitionResult) : UiState
    data class Success(val operation: Operation) : UiState
}

class KioskViewModel(
    private val recognitionApi: RecognitionApi
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var recognitionJob: Job? = null
    private var resetJob: Job? = null

    fun startOperation(operation: Operation) {
        cancelJobs()
        _uiState.value = UiState.Camera(CameraUiState(operation = operation))
    }

    fun onImageCaptured(imageBytes: ByteArray) {
        val current = _uiState.value as? UiState.Camera ?: return
        if (current.state.isRecognizing) return

        _uiState.value = UiState.Camera(
            current.state.copy(isRecognizing = true, errorMessage = null)
        )
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            try {
                val result = recognitionApi.recognize(imageBytes)
                _uiState.value = UiState.Result(current.state.operation, result)
            } catch (ex: Exception) {
                _uiState.value = UiState.Camera(
                    current.state.copy(
                        isRecognizing = false,
                        errorMessage = "Recognition failed. Please retake."
                    )
                )
            }
        }
    }

    fun retake() {
        val operation = when (val state = _uiState.value) {
            is UiState.Camera -> state.state.operation
            is UiState.Result -> state.operation
            else -> return
        }
        cancelJobs()
        _uiState.value = UiState.Camera(CameraUiState(operation = operation))
    }

    fun confirmResult() {
        val current = _uiState.value as? UiState.Result ?: return
        _uiState.value = UiState.Success(current.operation)
        scheduleReset()
    }

    fun onSuccessShown() {
        scheduleReset()
    }

    private fun scheduleReset() {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(3000)
            _uiState.value = UiState.Idle
        }
    }

    private fun cancelJobs() {
        recognitionJob?.cancel()
        resetJob?.cancel()
    }
}

object KioskViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KioskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KioskViewModel(MockRecognitionService()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

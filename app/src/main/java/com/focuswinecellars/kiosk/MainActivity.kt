package com.focuswinecellars.kiosk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuswinecellars.kiosk.kiosk.KioskController
import com.focuswinecellars.kiosk.ui.CameraScreen
import com.focuswinecellars.kiosk.ui.IdleScreen
import com.focuswinecellars.kiosk.ui.ResultScreen
import com.focuswinecellars.kiosk.ui.SuccessScreen
import com.focuswinecellars.kiosk.ui.theme.FocusWineCellarsTheme

class MainActivity : ComponentActivity() {
    private lateinit var kioskController: KioskController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kioskController = KioskController(this)
        onBackPressedDispatcher.addCallback(this) {}

        setContent {
            FocusWineCellarsTheme {
                KioskApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        kioskController.enterKioskMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            kioskController.enterKioskMode()
        }
    }

    override fun onDestroy() {
        kioskController.exitKioskMode()
        super.onDestroy()
    }
}

@Composable
fun KioskApp(viewModel: KioskViewModel = viewModel(factory = KioskViewModelFactory)) {
    BackHandler(enabled = true) {}

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is UiState.Idle -> {
                IdleScreen(
                    onAdd = { viewModel.startOperation(Operation.ADD) },
                    onRemove = { viewModel.startOperation(Operation.REMOVE) }
                )
            }
            is UiState.Camera -> {
                CameraScreen(
                    state = state.state,
                    onImageCaptured = viewModel::onImageCaptured,
                    onRetry = viewModel::retake
                )
            }
            is UiState.Result -> {
                ResultScreen(
                    result = state.result,
                    onConfirm = viewModel::confirmResult,
                    onRetake = viewModel::retake
                )
            }
            is UiState.Success -> {
                SuccessScreen(
                    operation = state.operation,
                    onDisplayed = viewModel::onSuccessShown
                )
            }
        }
    }
}

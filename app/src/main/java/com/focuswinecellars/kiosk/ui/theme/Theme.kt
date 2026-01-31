package com.focuswinecellars.kiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = KioskPrimary,
    onPrimary = KioskOnPrimary,
    background = KioskBackground,
    surface = KioskSurface,
    onSurface = KioskOnSurface
)

@Composable
fun FocusWineCellarsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

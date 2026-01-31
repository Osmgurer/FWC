package com.focuswinecellars.kiosk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuswinecellars.kiosk.network.RecognitionResult
import com.focuswinecellars.kiosk.network.displayLabel

@Composable
fun ResultScreen(
    result: RecognitionResult,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Recognition Result",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(24.dp))
            ResultRow(label = "Wine Name", value = result.wineName)
            ResultRow(label = "Producer", value = result.producer)
            ResultRow(label = "Vintage", value = result.vintage ?: "N/A")
            ResultRow(label = "Confidence", value = result.confidence.displayLabel())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Retake")
            }
            Spacer(modifier = Modifier.width(24.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Confirm")
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

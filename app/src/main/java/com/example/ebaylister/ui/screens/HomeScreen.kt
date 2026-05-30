package com.example.ebaylister.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ebaylister.R
import com.example.ebaylister.ui.EbayListerUiState

@Composable
fun HomeScreen(
    state: EbayListerUiState,
    onPhotoCaptured: (String) -> Unit,
    onCameraStatus: (String) -> Unit,
    onAnalyzeItem: () -> Unit,
    onSaveDraft: () -> Unit,
    onBackendUrlChanged: (String) -> Unit,
    onBackendApiTokenChanged: (String) -> Unit,
    onSwitchBackendMode: (String) -> Unit,
    onTestConnection: () -> Unit,
    contentPadding: PaddingValues,
) {
    val scrollState = rememberScrollState()
    var backendUrlInput by remember(state.backendBaseUrl) { mutableStateOf(state.backendBaseUrl) }
    var apiTokenInput by remember(state.backendApiToken) { mutableStateOf(state.backendApiToken) }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .verticalScroll(scrollState)
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "eBay Lister logo",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit,
            )
        }

        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Snap a photo. Generate a draft. Maximize profit.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Main is for quick capture + analyze. Save keepers to Draft Library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                CameraCaptureCard(
                    onPhotoCaptured = onPhotoCaptured,
                    onCameraStatus = onCameraStatus,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!state.capturedPhotoPath.isNullOrBlank()) {
                    CapturedPhotoThumbnail(photoPath = state.capturedPhotoPath)
                }

                Button(
                    onClick = onAnalyzeItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("Analyze", style = MaterialTheme.typography.titleMedium)
                }

                AssistChip(onClick = onSaveDraft, label = { Text("Save Draft") })

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = "Detected", style = MaterialTheme.typography.labelMedium)
                        Text(text = state.detectedItem, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(text = "Median: ${state.medianSoldPrice}  |  Source: ${state.compsSource.ifBlank { "unknown" }}", style = MaterialTheme.typography.bodySmall)
                        Text(text = state.statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        ElevatedCard(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Connection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onSwitchBackendMode("local") },
                        label = { Text(if (state.backendMode == "local") "Local selected" else "Use local") },
                    )
                    AssistChip(
                        onClick = { onSwitchBackendMode("cloud") },
                        label = { Text(if (state.backendMode == "cloud") "Cloud selected" else "Use cloud") },
                    )
                    AssistChip(onClick = onTestConnection, label = { Text("Test") })
                }

                OutlinedTextField(
                    value = backendUrlInput,
                    onValueChange = {
                        backendUrlInput = it
                        onBackendUrlChanged(it)
                    },
                    label = { Text("Backend URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = apiTokenInput,
                    onValueChange = {
                        apiTokenInput = it
                        onBackendApiTokenChanged(it)
                    },
                    label = { Text("Backend API Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CapturedPhotoThumbnail(photoPath: String) {
    val bitmap = remember(photoPath) { BitmapFactory.decodeFile(photoPath) }

    if (bitmap != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Last capture",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Last captured photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
        }
    }
}

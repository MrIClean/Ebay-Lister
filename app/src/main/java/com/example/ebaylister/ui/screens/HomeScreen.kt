package com.example.ebaylister.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ebaylister.ui.EbayListerUiState

@Composable
fun HomeScreen(
    state: EbayListerUiState,
    onPhotoCaptured: (String) -> Unit,
    onCameraStatus: (String) -> Unit,
    onAnalyzeItem: () -> Unit,
    onBackendUrlChanged: (String) -> Unit,
    onBackendApiTokenChanged: (String) -> Unit,
    onSwitchBackendMode: (String) -> Unit,
    onTestConnection: () -> Unit,
    onApplyCorrection: (String) -> Unit,
    onToggleDebug: () -> Unit,
    onClearDebug: () -> Unit,
    onConnectEbay: () -> Unit,
    onCreateListing: () -> Unit,
    contentPadding: PaddingValues,
) {
    val scrollState = rememberScrollState()
    var correctionInput by remember(state.rawDetectedItem) { mutableStateOf("") }
    var backendUrlInput by remember(state.backendBaseUrl) { mutableStateOf(state.backendBaseUrl) }
    var apiTokenInput by remember(state.backendApiToken) { mutableStateOf(state.backendApiToken) }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .verticalScroll(scrollState)
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "Item Scout",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Capture, identify, and price items faster.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Simple photo capture, bubble-style status blocks, and clean market data at a glance.",
                    style = MaterialTheme.typography.bodyLarge,
                )

                OutlinedTextField(
                    value = backendUrlInput,
                    onValueChange = {
                        backendUrlInput = it
                        onBackendUrlChanged(it)
                    },
                    label = { Text("Backend URL") },
                    supportingText = { Text("For phone testing use your computer LAN IP, e.g. http://192.168.1.10:8000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = apiTokenInput,
                    onValueChange = {
                        apiTokenInput = it
                        onBackendApiTokenChanged(it)
                    },
                    label = { Text("Backend API Token (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onSwitchBackendMode("local") },
                        label = { Text(if (state.backendMode == "local") "Local: Selected" else "Use Local") },
                    )
                    AssistChip(
                        onClick = { onSwitchBackendMode("cloud") },
                        label = { Text(if (state.backendMode == "cloud") "Cloud: Selected" else "Use Cloud") },
                    )
                    AssistChip(
                        onClick = onTestConnection,
                        label = { Text("Test connection") },
                    )
                }

                CameraCaptureCard(
                    onPhotoCaptured = onPhotoCaptured,
                    onCameraStatus = onCameraStatus,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!state.capturedPhotoPath.isNullOrBlank()) {
                    CapturedPhotoThumbnail(photoPath = state.capturedPhotoPath)
                }
            }
        }

        StatusBubbleSection(
            title = "Item",
            bubbles = listOf(
                BubbleData(label = "Photo", value = state.photoStatus, accent = MaterialTheme.colorScheme.secondary),
                BubbleData(label = "Detected", value = state.detectedItem, accent = MaterialTheme.colorScheme.primary),
                BubbleData(label = "Confidence", value = state.detectedConfidence, accent = MaterialTheme.colorScheme.tertiary),
                BubbleData(
                    label = "Vision",
                    value = "${state.visionProvider} (${state.visionModelName})",
                    accent = MaterialTheme.colorScheme.secondary,
                ),
            ),
        )

        if (state.candidateItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick corrections",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.candidateItems.take(3).forEach { candidate ->
                        AssistChip(
                            onClick = { onApplyCorrection(candidate) },
                            label = { Text(candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }) },
                        )
                    }
                }

                OutlinedTextField(
                    value = correctionInput,
                    onValueChange = { correctionInput = it },
                    label = { Text("Correct item name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        onApplyCorrection(correctionInput)
                        correctionInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save correction")
                }
            }
        }

        StatusBubbleSection(
            title = "Market Snapshot",
            bubbles = listOf(
                BubbleData(label = "Active listed", value = state.listedCount.toString(), accent = MaterialTheme.colorScheme.secondary),
                BubbleData(label = "Sold", value = state.soldCount.toString(), accent = MaterialTheme.colorScheme.primary),
                BubbleData(label = "Average sold", value = state.averageSoldPrice, accent = MaterialTheme.colorScheme.primary),
                BubbleData(label = "Median sold price", value = state.medianSoldPrice, accent = MaterialTheme.colorScheme.tertiary),
                BubbleData(label = "Low / High", value = "${state.lowSoldPrice} / ${state.highSoldPrice}", accent = MaterialTheme.colorScheme.primary),
                BubbleData(label = "Data source", value = state.compsSource.ifBlank { "unknown" }, accent = MaterialTheme.colorScheme.secondary),
                BubbleData(label = "Keywords", value = state.normalizedKeywords.ifBlank { "n/a" }, accent = MaterialTheme.colorScheme.tertiary),
            ),
        )

        StatusBubbleSection(
            title = "eBay",
            bubbles = listOf(
                BubbleData(label = "Connection", value = state.ebayConnectionStatus, accent = MaterialTheme.colorScheme.primary),
                BubbleData(label = "Backend", value = state.connectionStatus, accent = MaterialTheme.colorScheme.primary),
                BubbleData(label = "Draft", value = state.listingDraftStatus, accent = MaterialTheme.colorScheme.secondary),
                BubbleData(label = "Status", value = state.statusMessage, accent = MaterialTheme.colorScheme.tertiary),
            ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Debug",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onToggleDebug,
                    label = { Text(if (state.debugEnabled) "Debug: On" else "Debug: Off") },
                )
                AssistChip(
                    onClick = onClearDebug,
                    label = { Text("Clear log") },
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.debugLog,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AssistChip(
                onClick = onAnalyzeItem,
                label = { Text("Analyze") },
            )
            AssistChip(
                onClick = onConnectEbay,
                label = { Text("Connect eBay") },
            )
        }

        Button(
            onClick = onCreateListing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create listing draft")
        }

        OutlinedButton(
            onClick = onAnalyzeItem,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh market data")
        }
    }
}

@Composable
private fun StatusBubbleSection(
    title: String,
    bubbles: List<BubbleData>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bubbles.forEach { bubble ->
                BubbleBlock(bubble = bubble)
            }
        }
    }
}

@Composable
private fun BubbleBlock(bubble: BubbleData) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = bubble.accent.copy(alpha = 0.09f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = bubble.label,
                style = MaterialTheme.typography.labelMedium,
                color = bubble.accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = bubble.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private data class BubbleData(
    val label: String,
    val value: String,
    val accent: androidx.compose.ui.graphics.Color,
)

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

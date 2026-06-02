package com.example.ebaylister.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xAA0B1220),
                        Color(0xEE070B14),
                    ),
                ),
            )
            .padding(contentPadding)
            .verticalScroll(scrollState)
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF41E2FF),
                            Color(0xFFFF4FD8),
                        ),
                    ),
                    shape = RoundedCornerShape(28.dp),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "eBay Lister logo",
                    modifier = Modifier.size(54.dp),
                    contentScale = ContentScale.Fit,
                )

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "PixelProfit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Cyberpunk scan deck for items, comps, and drafts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = "Capture Deck", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                Text(
                    text = "Snap a photo. Generate a draft. Maximize profit.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "Main is for quick capture + analyze. Save keepers to Draft Library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    enabled = !state.isAnalyzing && !state.capturedPhotoPath.isNullOrBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = if (state.isAnalyzing) "Analyzing..." else "Analyze",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (state.isAnalyzing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Button(
                    onClick = onSaveDraft,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text("Save Draft")
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = "Detected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = state.detectedItem,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MetricGrid(
                            metrics = listOf(
                                MetricTile("Median", state.medianSoldPrice),
                                MetricTile("Avg", state.averageSoldPrice),
                                MetricTile("Low", state.lowSoldPrice),
                                MetricTile("High", state.highSoldPrice),
                            ),
                        )
                        Text(
                            text = "Source: ${state.compsSource.ifBlank { "unknown" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        ElevatedCard(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Connection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "USB local mode is prefilled; cloud stays empty until you add a tunnel URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
                    placeholder = { Text("http://127.0.0.1:8000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                Text(
                    text = "Tip: if you enter only host:port, the app automatically prepends http://",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = state.connectionStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                OutlinedTextField(
                    value = apiTokenInput,
                    onValueChange = {
                        apiTokenInput = it
                        onBackendApiTokenChanged(it)
                    },
                    label = { Text("Backend API Token") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.secondary,
                        cursorColor = MaterialTheme.colorScheme.secondary,
                    ),
                )

                Text(
                    text = "API token is optional unless your backend sets BACKEND_API_TOKEN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
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

package com.example.ebaylister.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ebaylister.ui.screens.DraftsScreen
import com.example.ebaylister.ui.screens.HomeScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun EbayListerApp(
    viewModel: EbayListerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var screen by rememberSaveable { mutableStateOf("main") }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                AssistChip(
                    onClick = { screen = "main" },
                    label = { androidx.compose.material3.Text(if (screen == "main") "Main: Selected" else "Main") },
                )
                AssistChip(
                    onClick = { screen = "drafts" },
                    label = { androidx.compose.material3.Text(if (screen == "drafts") "Drafts: Selected" else "Drafts") },
                )
            }

            if (screen == "main") {
                HomeScreen(
                    state = state,
                    onPhotoCaptured = viewModel::onPhotoCaptured,
                    onCameraStatus = viewModel::onCameraStatus,
                    onAnalyzeItem = viewModel::analyzeCurrentItem,
                    onSaveDraft = {
                        val saved = viewModel.saveCurrentAsDraft()
                        if (saved) {
                            screen = "drafts"
                        }
                    },
                    onBackendUrlChanged = viewModel::updateBackendBaseUrl,
                    onBackendApiTokenChanged = viewModel::updateBackendApiToken,
                    onSwitchBackendMode = viewModel::switchBackendMode,
                    onTestConnection = viewModel::testBackendConnection,
                    contentPadding = PaddingValues(24.dp),
                )
            } else {
                DraftsScreen(
                    drafts = state.savedDrafts,
                    onRemoveDraft = viewModel::removeDraft,
                    onSendToEbay = { draft ->
                        val shareText = buildString {
                            appendLine("Title: ${draft.title}")
                            appendLine("Suggested Price: ${draft.medianPrice}")
                            appendLine("Price Range: ${draft.lowPrice} - ${draft.highPrice}")
                            appendLine("Average Sold: ${draft.averagePrice}")
                            appendLine("Source: ${draft.source}")
                            appendLine("Market Activity: ${draft.soldCount} sold / ${draft.listedCount} active")
                            appendLine("Confidence: ${draft.confidence}")
                            if (draft.normalizedKeywords.isNotBlank()) {
                                appendLine("Keywords: ${draft.normalizedKeywords}")
                            }
                            if (draft.suggestedKeywords.isNotEmpty()) {
                                appendLine("Suggested Keywords: ${draft.suggestedKeywords.joinToString(", ")}")
                            }
                            append("Notes: ${draft.listingNotes}")
                        }

                        val ebayShareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, draft.title)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            setPackage("com.ebay.mobile")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        val encoded = URLEncoder.encode(draft.title, StandardCharsets.UTF_8.toString())
                        val fallbackSellUrl = "https://www.ebay.com/sl/sell?query=$encoded"
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackSellUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        if (ebayShareIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(ebayShareIntent)
                        } else {
                            context.startActivity(fallbackIntent)
                        }
                    },
                    contentPadding = PaddingValues(24.dp),
                )
            }
        }
    }
}

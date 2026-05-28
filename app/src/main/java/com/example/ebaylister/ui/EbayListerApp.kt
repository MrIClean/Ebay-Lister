package com.example.ebaylister.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ebaylister.ui.screens.HomeScreen

@Composable
fun EbayListerApp(
    viewModel: EbayListerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            HomeScreen(
                state = state,
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onCameraStatus = viewModel::onCameraStatus,
                onAnalyzeItem = viewModel::analyzeCurrentItem,
                onBackendUrlChanged = viewModel::updateBackendBaseUrl,
                onBackendApiTokenChanged = viewModel::updateBackendApiToken,
                onSwitchBackendMode = viewModel::switchBackendMode,
                onTestConnection = viewModel::testBackendConnection,
                onApplyCorrection = viewModel::applyManualCorrection,
                onToggleDebug = viewModel::toggleDebugLogging,
                onClearDebug = viewModel::clearDebugLog,
                onConnectEbay = viewModel::connectEbayAccount,
                onCreateListing = viewModel::createListingDraft,
                contentPadding = PaddingValues(24.dp),
            )
        }
    }
}

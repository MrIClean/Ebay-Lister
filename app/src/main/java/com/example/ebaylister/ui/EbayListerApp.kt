package com.example.ebaylister.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ebaylister.ui.screens.DraftsScreen
import com.example.ebaylister.ui.screens.HomeScreen
import com.example.ebaylister.ui.screens.ListingDetailsScreen

@Composable
fun EbayListerApp(
    viewModel: EbayListerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var screen by rememberSaveable { mutableStateOf("main") }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Surface(
                color = Color(0xFF151923),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = screen == "main",
                        onClick = { screen = "main" },
                        icon = { NavDot(selected = screen == "main", accent = Color(0xFF2D6BFF)) },
                        label = { Text("Main") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE4E4E7),
                            selectedTextColor = Color(0xFFE4E4E7),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0x332D6BFF),
                        ),
                    )
                    NavigationBarItem(
                        selected = screen == "drafts",
                        onClick = { screen = "drafts" },
                        icon = { NavDot(selected = screen == "drafts", accent = Color(0xFF14B8A6)) },
                        label = { Text("Drafts") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE4E4E7),
                            selectedTextColor = Color(0xFFE4E4E7),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0x3314B8A6),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF050812),
                            Color(0xFF0A1020),
                            Color(0xFF060B14),
                        ),
                    ),
                )
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    )
                } else if (screen == "drafts") {
                    DraftsScreen(
                        drafts = state.savedDrafts,
                        onRemoveDraft = viewModel::removeDraft,
                        onEditDraft = { draft ->
                            viewModel.openListingEditor(draft.id)
                            screen = "listing"
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    )
                } else {
                    val editor = state.listingEditor
                    if (editor == null) {
                        screen = "drafts"
                    } else {
                        ListingDetailsScreen(
                            editor = editor,
                            latestCapturePath = state.capturedPhotoPath,
                            fulfillmentPolicies = state.fulfillmentPolicies,
                            returnPolicies = state.returnPolicies,
                            accountOptionsStatus = state.accountOptionsStatus,
                            isLoadingAccountOptions = state.isLoadingAccountOptions,
                            onClose = {
                                viewModel.closeListingEditor()
                                screen = "drafts"
                            },
                            onTitleChange = viewModel::updateListingEditorTitle,
                            onDescriptionChange = viewModel::updateListingEditorDescription,
                            onPriceChange = viewModel::updateListingEditorPrice,
                            onConditionChange = viewModel::updateListingEditorCondition,
                            onCategoryChange = viewModel::updateListingEditorCategory,
                            onShippingProfileChange = viewModel::updateListingEditorShippingProfile,
                            onShippingPolicySelected = viewModel::selectShippingPolicy,
                            onReturnPolicyChange = viewModel::updateListingEditorReturnPolicy,
                            onReturnPolicySelected = viewModel::selectReturnPolicy,
                            onQuantityChange = viewModel::updateListingEditorQuantity,
                            onRefreshAccountOptions = viewModel::refreshEbayAccountOptions,
                            onAddPhotoPath = viewModel::addListingEditorPhoto,
                            onRemovePhotoPath = viewModel::removeListingEditorPhoto,
                            onPublishToEbay = viewModel::publishListingEditorToEbay,
                            onViewPublishedListing = { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            onSharePublishedListing = { url ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, editor.title)
                                    putExtra(Intent.EXTRA_TEXT, "${editor.title}\n$url")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share listing"))
                            },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavDot(selected: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .background(
                color = if (selected) accent else Color(0xFF334155),
                shape = CircleShape,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

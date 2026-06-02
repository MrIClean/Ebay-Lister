package com.example.ebaylister.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebaylister.data.AnalyzerCorrectionStore
import com.example.ebaylister.data.BackendAnalyzeResult
import com.example.ebaylister.data.BackendListingPipelineClient
import com.example.ebaylister.data.FakeEbayListingPublisher
import com.example.ebaylister.data.FakeEbayMarketplaceRepository
import com.example.ebaylister.data.MlKitItemAnalyzer
import com.example.ebaylister.domain.ListingDraft
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class EbayListerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val DEFAULT_LOCAL_BACKEND_URL = "http://127.0.0.1:8000"
    }

    private val itemAnalyzer = MlKitItemAnalyzer(application.applicationContext)
    private val backendClient = BackendListingPipelineClient()
    private val correctionStore = AnalyzerCorrectionStore(application.applicationContext)
    private val marketplaceRepository = FakeEbayMarketplaceRepository()
    private val listingPublisher = FakeEbayListingPublisher()

    private val _uiState = MutableStateFlow(EbayListerUiState())
    val uiState: StateFlow<EbayListerUiState> = _uiState.asStateFlow()
    private val timestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val logTag = "EbayListerDebug"
    private val prefs = application.getSharedPreferences("ebay_lister_prefs", Context.MODE_PRIVATE)

    init {
        loadSavedSettings()
        appendDebug("ViewModel initialized. Debug logging is active.")
    }

    fun onPhotoCaptured(photoPath: String) {
        val fileName = File(photoPath).name
        _uiState.value = _uiState.value.copy(
            photoStatus = "Photo captured: $fileName",
            capturedPhotoPath = photoPath,
            statusMessage = "Photo captured. Tap Analyze item.",
        )
        appendDebug("Photo captured at path: $photoPath")
    }

    fun onCameraStatus(message: String) {
        _uiState.value = _uiState.value.copy(statusMessage = message)
        appendDebug("Camera status: $message")
    }

    fun updateBackendBaseUrl(url: String) {
        val trimmed = normalizeBackendUrl(url)
        val mode = _uiState.value.backendMode
        val connectionStatus = if (trimmed.isBlank()) "Not configured" else "Not tested"
        if (mode == "cloud") {
            _uiState.value = _uiState.value.copy(
                backendBaseUrl = trimmed,
                cloudBackendUrl = trimmed,
                connectionStatus = connectionStatus,
            )
            prefs.edit().putString("cloud_backend_url", trimmed).apply()
        } else {
            _uiState.value = _uiState.value.copy(
                backendBaseUrl = trimmed,
                localBackendUrl = trimmed,
                connectionStatus = connectionStatus,
            )
            prefs.edit().putString("local_backend_url", trimmed).apply()
        }
        appendDebug("Backend URL updated for $mode mode: $trimmed")
    }

    fun updateBackendApiToken(token: String) {
        _uiState.value = _uiState.value.copy(
            backendApiToken = token,
            connectionStatus = if (_uiState.value.backendBaseUrl.isBlank()) "Not configured" else "Not tested",
        )
        prefs.edit().putString("backend_api_token", token).apply()
        appendDebug("Backend API token updated.")
    }

    fun switchBackendMode(mode: String) {
        val chosenMode = if (mode == "cloud") "cloud" else "local"
        val chosenUrl = if (chosenMode == "cloud") _uiState.value.cloudBackendUrl else _uiState.value.localBackendUrl
        _uiState.value = _uiState.value.copy(
            backendMode = chosenMode,
            backendBaseUrl = chosenUrl,
            connectionStatus = if (chosenUrl.isBlank()) "Not configured" else "Not tested",
        )
        prefs.edit().putString("backend_mode", chosenMode).apply()
        appendDebug("Switched backend mode to $chosenMode using URL $chosenUrl")
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            val backendUrl = normalizeBackendUrl(_uiState.value.backendBaseUrl)
            val token = _uiState.value.backendApiToken.trim()

            if (backendUrl.isBlank()) {
                val status = "Connection failed: enter a backend URL first"
                _uiState.value = _uiState.value.copy(connectionStatus = status, statusMessage = status)
                appendDebug("Health skipped: backend URL is blank.")
                return@launch
            }

            appendDebug("Testing backend connection: $backendUrl")

            val result = runCatching { backendClient.health(backendUrl, token) }
            if (result.isSuccess) {
                val health = result.getOrThrow()
                val authLabel = if (health.authRequired) "token required" else "token optional"
                val status = "Connected (${health.status}) vision=${health.visionMode} ebayReal=${health.useRealEbay} $authLabel"
                _uiState.value = _uiState.value.copy(connectionStatus = status, statusMessage = status)
                appendDebug("Health success: authRequired=${health.authRequired}")
            } else {
                val error = result.exceptionOrNull()?.message ?: "unknown connection error"
                val status = "Connection failed: $error"
                _uiState.value = _uiState.value.copy(connectionStatus = status, statusMessage = status)
                appendDebug(status)
            }
        }
    }

    fun toggleDebugLogging() {
        val enabled = !_uiState.value.debugEnabled
        _uiState.value = _uiState.value.copy(debugEnabled = enabled)
    }

    fun clearDebugLog() {
        _uiState.value = _uiState.value.copy(debugLog = "Debug log cleared.")
    }

    fun analyzeCurrentItem() {
        viewModelScope.launch {
            val photoPath = _uiState.value.capturedPhotoPath
            if (photoPath.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Capture a photo first, then tap Analyze.",
                )
                appendDebug("Analyze skipped: no captured photo path.")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                statusMessage = "Analyzing item...",
            )
            appendDebug("Analyze started: preparing request.")

            try {

                val backendUrl = normalizeBackendUrl(_uiState.value.backendBaseUrl)
                val token = _uiState.value.backendApiToken.trim()

                if (backendUrl.isBlank()) {
                    appendDebug("Backend not configured. Skipping remote analysis and using local fallback.")
                    val analysis = runCatching { itemAnalyzer.analyze(photoPath) }
                        .getOrElse {
                            _uiState.value = _uiState.value.copy(
                                statusMessage = "Backend not configured and local analysis failed.",
                            )
                            appendDebug("Local fallback failed: ${it.message ?: "unknown local error"}")
                            return@launch
                        }

                    val marketStats = marketplaceRepository.lookupMarketStats(analysis.itemName)
                    val correctedItem = correctionStore.getCorrection(analysis.itemName) ?: analysis.itemName

                    _uiState.value = _uiState.value.copy(
                        detectedItem = correctedItem,
                        rawDetectedItem = analysis.itemName,
                        candidateItems = analysis.candidates,
                        detectedConfidence = "Confidence: ${analysis.confidenceLabel}",
                        visionProvider = "local-fallback",
                        visionModelName = "ml-kit-labeler",
                        listedCount = marketStats.listedCount,
                        soldCount = marketStats.soldCount,
                        averageSoldPrice = "$0.00",
                        medianSoldPrice = marketStats.medianSoldPrice,
                        lowSoldPrice = "$0.00",
                        highSoldPrice = "$0.00",
                        compsSource = "local-fallback",
                        connectionStatus = "Not configured",
                        statusMessage = "Backend not configured. Used local on-device fallback.",
                    )
                    return@launch
                }

                appendDebug("Analyze started. backend=$backendUrl image=$photoPath")
                val backendStartMs = System.currentTimeMillis()
                val backendResult = runCatching { backendClient.analyze(backendUrl, photoPath, token) }
                val backendDurationMs = System.currentTimeMillis() - backendStartMs

                if (backendResult.isSuccess) {
                    var result = backendResult.getOrThrow()
                    if (shouldRetryWithLocalKeywords(result)) {
                        val localGuess = runCatching { itemAnalyzer.analyze(photoPath) }.getOrNull()
                        val localKeywords = localGuess?.itemName?.trim().orEmpty()
                        if (isUsefulKeywordSeed(localKeywords)) {
                            appendDebug("Backend returned low-confidence item. Retrying with local keywords: $localKeywords")
                            val retry = runCatching {
                                backendClient.analyze(
                                    baseUrl = backendUrl,
                                    imagePath = photoPath,
                                    apiToken = token,
                                    overrideKeywords = localKeywords,
                                )
                            }
                            if (retry.isSuccess) {
                                result = retry.getOrThrow()
                                appendDebug("Retry with local keywords succeeded. source=${result.source} median=${result.medianSoldPrice}")
                            } else {
                                appendDebug("Retry with local keywords failed: ${retry.exceptionOrNull()?.message ?: "unknown"}")
                            }
                        } else {
                            appendDebug("Skipped retry: local keyword seed is too generic ('$localKeywords').")
                        }
                    }
                    val correctedItem = correctionStore.getCorrection(result.model) ?: result.model
                    appendDebug(
                        "Backend success in ${backendDurationMs}ms: vision=${result.visionProvider}/${result.visionModelName}, " +
                            "active=${result.activeCount}, sold=${result.soldTotalCount}, median=${result.medianSoldPrice}, source=${result.source}"
                    )

                    _uiState.value = _uiState.value.copy(
                        detectedItem = correctedItem,
                        rawDetectedItem = result.model,
                        listingDescription = result.listingDescription,
                        candidateItems = result.suggestedKeywords,
                        detectedConfidence = "Confidence: ${result.confidencePercent}%",
                        visionProvider = result.visionProvider,
                        visionModelName = result.visionModelName,
                        normalizedKeywords = result.normalizedKeywords,
                        listedCount = result.activeCount,
                        soldCount = result.soldTotalCount,
                        averageSoldPrice = formatUsd(result.averageSoldPrice),
                        medianSoldPrice = formatUsd(result.medianSoldPrice),
                        lowSoldPrice = formatUsd(result.lowSoldPrice),
                        highSoldPrice = formatUsd(result.highSoldPrice),
                        compsSource = result.source,
                        statusMessage = "Backend analysis complete via ${result.visionProvider} (${result.visionModelName}). Sold comps sampled: ${result.soldCompsCount}.",
                    )
                    return@launch
                }

                val backendError = backendResult.exceptionOrNull()?.message ?: "unknown backend error"
                appendDebug("Backend failed in ${backendDurationMs}ms: $backendError")

                // Fallback keeps the app functional when backend is unavailable.
                val analysis = runCatching { itemAnalyzer.analyze(photoPath) }
                    .getOrElse {
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Backend and local analysis failed. Check backend URL and retry.",
                        )
                        appendDebug("Local fallback failed: ${it.message ?: "unknown local error"}")
                        return@launch
                    }

                val marketStats = marketplaceRepository.lookupMarketStats(analysis.itemName)
                val correctedItem = correctionStore.getCorrection(analysis.itemName) ?: analysis.itemName

                _uiState.value = _uiState.value.copy(
                    detectedItem = correctedItem,
                    rawDetectedItem = analysis.itemName,
                    candidateItems = analysis.candidates,
                    detectedConfidence = "Confidence: ${analysis.confidenceLabel}",
                    visionProvider = "local-fallback",
                    visionModelName = "ml-kit-labeler",
                    listedCount = marketStats.listedCount,
                    soldCount = marketStats.soldCount,
                    averageSoldPrice = "$0.00",
                    medianSoldPrice = marketStats.medianSoldPrice,
                    lowSoldPrice = "$0.00",
                    highSoldPrice = "$0.00",
                    compsSource = "local-fallback",
                    statusMessage = "Backend unreachable. Used local on-device fallback.",
                )
                appendDebug("Using local fallback analyzer with ML Kit.")
            } finally {
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
            }
        }
    }

    private fun shouldRetryWithLocalKeywords(result: BackendAnalyzeResult): Boolean {
        val modelUnknown = isUnknownLabel(result.model)
        val lowConfidence = result.confidencePercent < 35
        val mockVision = result.visionProvider.contains("mock", ignoreCase = true)
        return modelUnknown || (mockVision && lowConfidence)
    }

    private fun normalizeBackendUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""

        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        return if (hasScheme) trimmed else "http://$trimmed"
    }

    private fun isUnknownLabel(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.isBlank() || normalized == "unknown" || normalized == "unknown item"
    }

    private fun isUsefulKeywordSeed(value: String): Boolean {
        val normalized = value.trim().lowercase()
        if (isUnknownLabel(normalized)) return false

        val genericTerms = setOf(
            "food",
            "book",
            "item",
            "object",
            "thing",
            "product",
            "device",
            "electronics",
            "clothes",
            "clothing",
            "toy",
        )
        if (normalized in genericTerms) return false

        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        if (tokens.size >= 2) return true

        // Allow single-token seeds only if they look like a specific model-ish token.
        return tokens.firstOrNull()?.any { it.isDigit() } == true
    }

    fun applyManualCorrection(correctedName: String) {
        val corrected = correctedName.trim()
        if (corrected.isBlank()) return

        val raw = _uiState.value.rawDetectedItem
        if (raw.isNotBlank()) {
            correctionStore.saveCorrection(raw, corrected)
            appendDebug("Saved local correction: '$raw' -> '$corrected'")
            val backendUrl = normalizeBackendUrl(_uiState.value.backendBaseUrl)
            if (backendUrl.isNotBlank()) {
                viewModelScope.launch {
                    runCatching {
                        backendClient.saveCorrection(
                            baseUrl = backendUrl,
                            predicted = raw,
                            corrected = corrected,
                            apiToken = _uiState.value.backendApiToken.trim(),
                        )
                    }.onSuccess {
                        appendDebug("Synced correction to backend.")
                    }.onFailure {
                        appendDebug("Failed syncing correction to backend: ${it.message ?: "unknown"}")
                    }
                }
            } else {
                appendDebug("Skipped backend correction sync: backend URL is blank.")
            }
        }

        _uiState.value = _uiState.value.copy(
            detectedItem = corrected,
            statusMessage = "Saved. Future '${raw.ifBlank { "predictions" }}' results will map to '$corrected'.",
        )
    }

    fun connectEbayAccount() {
        _uiState.value = _uiState.value.copy(
            ebayConnectionStatus = "Ready to connect to eBay OAuth",
            statusMessage = "Add the eBay OAuth flow and account linking here.",
        )
        appendDebug("Connect eBay tapped. OAuth flow not wired yet.")
    }

    fun createListingDraft() {
        viewModelScope.launch {
            val draft = ListingDraft(
                title = "Vintage Sony Walkman - Prebuilt Draft",
                description = "Placeholder draft listing created from the identified item.",
                price = "$58.00",
            )
            val result = listingPublisher.publishDraft(draft)

            _uiState.value = _uiState.value.copy(
                listingDraftStatus = result.message,
                statusMessage = "Listing publishing is scaffolded. Wire in your draft listing workflow next.",
            )
            appendDebug("Create listing draft tapped. Publisher response: ${result.message}")
        }
    }

    fun openListingEditor(draftId: String) {
        val draft = _uiState.value.savedDrafts.firstOrNull { it.id == draftId } ?: run {
            _uiState.value = _uiState.value.copy(statusMessage = "Draft not found.")
            return
        }

        val initialPhotos = buildList {
            if (draft.photoPath.isNotBlank()) add(draft.photoPath)
            addAll(draft.additionalPhotoPaths)
        }.distinct()

        _uiState.value = _uiState.value.copy(
            listingEditor = ListingEditorState(
                draftId = draft.id,
                title = draft.title,
                description = draft.draftDescription.ifBlank { draft.listingNotes },
                price = draft.targetPrice.ifBlank { draft.medianPrice },
                photoPaths = initialPhotos,
                condition = draft.condition,
                category = draft.category,
                shippingProfile = draft.shippingProfile,
                returnPolicy = draft.returnPolicy,
                quantity = draft.quantity,
                publishedListingUrl = draft.publishedListingUrl,
                publishStatus = if (draft.publishedListingUrl.isBlank()) "Not published yet." else "Published."
            ),
            statusMessage = "Listing editor opened for ${draft.title}",
        )
        appendDebug("Listing editor opened for draft: ${draft.id}")
    }

    fun closeListingEditor() {
        _uiState.value = _uiState.value.copy(listingEditor = null)
    }

    fun updateListingEditorTitle(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(title = value))
    }

    fun updateListingEditorDescription(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(description = value))
    }

    fun updateListingEditorPrice(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(price = value))
    }

    fun updateListingEditorCondition(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(condition = value))
    }

    fun updateListingEditorCategory(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(category = value))
    }

    fun updateListingEditorShippingProfile(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(shippingProfile = value))
    }

    fun updateListingEditorReturnPolicy(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(returnPolicy = value))
    }

    fun updateListingEditorQuantity(value: String) {
        val editor = _uiState.value.listingEditor ?: return
        val quantity = value.trim().toIntOrNull() ?: 1
        _uiState.value = _uiState.value.copy(listingEditor = editor.copy(quantity = quantity.coerceAtLeast(1)))
    }

    fun addListingEditorPhoto(path: String) {
        val editor = _uiState.value.listingEditor ?: return
        val trimmed = path.trim()
        if (trimmed.isBlank()) return
        if (editor.photoPaths.contains(trimmed)) return
        _uiState.value = _uiState.value.copy(
            listingEditor = editor.copy(photoPaths = editor.photoPaths + trimmed)
        )
    }

    fun removeListingEditorPhoto(path: String) {
        val editor = _uiState.value.listingEditor ?: return
        val updated = editor.photoPaths.filterNot { it == path }
        _uiState.value = _uiState.value.copy(
            listingEditor = editor.copy(photoPaths = updated)
        )
    }

    fun publishListingEditorToEbay() {
        val editor = _uiState.value.listingEditor ?: return
        val validationErrors = validateListingEditor(editor)
        if (validationErrors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                listingEditor = editor.copy(
                    validationErrors = validationErrors,
                    publishStatus = "Fix ${validationErrors.size} listing detail(s) before publishing.",
                ),
                statusMessage = "Listing needs required details before publishing.",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                listingEditor = editor.copy(
                    isPublishing = true,
                    validationErrors = emptyList(),
                    publishStatus = "Validating listing package...",
                ),
                statusMessage = "Publishing listing...",
            )

            val draft = ListingDraft(
                title = editor.title,
                description = editor.description,
                price = editor.price,
                photoPaths = editor.photoPaths,
                condition = editor.condition,
                category = editor.category,
                shippingProfile = editor.shippingProfile,
                returnPolicy = editor.returnPolicy,
                quantity = editor.quantity,
                channel = "ebay",
            )
            val backendUrl = normalizeBackendUrl(_uiState.value.backendBaseUrl)
            val token = _uiState.value.backendApiToken.trim()
            val publishResult = runCatching {
                if (backendUrl.isNotBlank()) {
                    backendClient.publishDraft(backendUrl, draft, token)
                } else {
                    listingPublisher.publishDraft(draft)
                }
            }

            val latestEditor = _uiState.value.listingEditor ?: return@launch
            if (publishResult.isSuccess) {
                val result = publishResult.getOrThrow()
                if (!result.success) {
                    _uiState.value = _uiState.value.copy(
                        listingEditor = latestEditor.copy(
                            isPublishing = false,
                            publishStatus = result.message,
                            validationErrors = result.validationErrors,
                        ),
                        statusMessage = "Listing package needs more details.",
                    )
                    appendDebug("Publish validation failed for draft ${latestEditor.draftId}: ${result.validationErrors.joinToString()}")
                    return@launch
                }

                val updatedEditor = latestEditor.copy(
                    isPublishing = false,
                    publishStatus = result.message,
                    publishedListingUrl = result.listingUrl,
                    validationErrors = emptyList(),
                )
                _uiState.value = _uiState.value.copy(
                    listingEditor = updatedEditor,
                    listingDraftStatus = if (result.listingUrl.isBlank()) "Listing package ready" else "Listing published",
                    statusMessage = if (result.listingUrl.isBlank()) {
                        "Listing package validated by backend."
                    } else {
                        "Listing published from in-app editor."
                    },
                )
                updateSavedDraftFromEditor(updatedEditor)
                appendDebug("Publish flow completed for draft ${latestEditor.draftId}: ${result.message}")
            } else {
                val message = publishResult.exceptionOrNull()?.message ?: "unknown publish error"
                _uiState.value = _uiState.value.copy(
                    listingEditor = latestEditor.copy(isPublishing = false, publishStatus = "Publish failed: $message"),
                    statusMessage = "Publish failed. Review details and retry.",
                )
                appendDebug("Publish failed for draft ${latestEditor.draftId}: $message")
            }
        }
    }

    private fun updateSavedDraftFromEditor(editor: ListingEditorState) {
        val updatedDrafts = _uiState.value.savedDrafts.map { draft ->
            if (draft.id != editor.draftId) return@map draft

            val primary = editor.photoPaths.firstOrNull().orEmpty()
            val extras = if (editor.photoPaths.size > 1) editor.photoPaths.drop(1) else emptyList()
            draft.copy(
                title = editor.title,
                targetPrice = editor.price,
                draftDescription = editor.description,
                photoPath = primary.ifBlank { draft.photoPath },
                additionalPhotoPaths = extras,
                publishedListingUrl = editor.publishedListingUrl,
                condition = editor.condition,
                category = editor.category,
                shippingProfile = editor.shippingProfile,
                returnPolicy = editor.returnPolicy,
                quantity = editor.quantity,
            )
        }
        _uiState.value = _uiState.value.copy(savedDrafts = updatedDrafts)
        saveDrafts(updatedDrafts)
    }

    private fun validateListingEditor(editor: ListingEditorState): List<String> {
        val errors = mutableListOf<String>()
        val numericPrice = editor.price.trim().removePrefix("$").toDoubleOrNull()
        if (editor.title.trim().length < 10) errors += "Title should be at least 10 characters."
        if (editor.description.trim().length < 20) errors += "Description should be at least 20 characters."
        if (numericPrice == null || numericPrice <= 0.0) errors += "Price must be a valid positive number."
        if (editor.photoPaths.isEmpty()) errors += "Add at least one photo."
        if (editor.condition.isBlank()) errors += "Choose an item condition."
        if (editor.category.isBlank()) errors += "Add an eBay category."
        if (editor.shippingProfile.isBlank()) errors += "Add a shipping profile."
        if (editor.returnPolicy.isBlank()) errors += "Add a return policy."
        if (editor.quantity < 1) errors += "Quantity must be at least 1."
        return errors
    }

    fun saveCurrentAsDraft(): Boolean {
        val photoPath = _uiState.value.capturedPhotoPath
        val itemTitle = _uiState.value.detectedItem
        val keyBase = _uiState.value.rawDetectedItem.ifBlank { itemTitle }

        if (photoPath.isNullOrBlank() || keyBase.isBlank() || itemTitle.contains("Tap analyze", ignoreCase = true)) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Analyze an item first, then save it as a draft.",
            )
            return false
        }

        val key = keyBase.trim().lowercase()
        val current = _uiState.value.savedDrafts
        val existing = current.firstOrNull { it.id == key }
        val newDraft = SavedDraftItem(
            id = key,
            title = itemTitle,
            medianPrice = _uiState.value.medianSoldPrice,
            averagePrice = _uiState.value.averageSoldPrice,
            lowPrice = _uiState.value.lowSoldPrice,
            highPrice = _uiState.value.highSoldPrice,
            source = _uiState.value.compsSource.ifBlank { "unknown" },
            normalizedKeywords = _uiState.value.normalizedKeywords,
            suggestedKeywords = _uiState.value.candidateItems,
            listedCount = _uiState.value.listedCount,
            soldCount = _uiState.value.soldCount,
            confidence = _uiState.value.detectedConfidence,
            listingNotes = "Condition: auto-detected. Verify brand/model and complete item specifics before publishing.",
            photoPath = photoPath,
            targetPrice = _uiState.value.medianSoldPrice,
            draftDescription = _uiState.value.listingDescription.ifBlank {
                buildString {
                    appendLine("Condition: auto-detected. Verify model, cosmetic grade, and accessories.")
                    if (_uiState.value.normalizedKeywords.isNotBlank()) {
                        append("Keywords: ${_uiState.value.normalizedKeywords}")
                    }
                }.trim()
            },
            additionalPhotoPaths = emptyList(),
            publishedListingUrl = "",
            condition = "",
            category = "",
            shippingProfile = "",
            returnPolicy = "",
            quantity = 1,
        )

        val updated = if (existing == null) {
            listOf(newDraft) + current
        } else {
            listOf(newDraft) + current.filterNot { it.id == key }
        }

        _uiState.value = _uiState.value.copy(
            savedDrafts = updated,
            listingDraftStatus = if (existing == null) "Draft saved" else "Draft updated",
            statusMessage = if (existing == null) "Draft saved to library. Ready for next item." else "Draft updated with latest analysis. Ready for next item.",
            photoStatus = "No photo captured yet",
            capturedPhotoPath = null,
            detectedItem = "Tap analyze to identify an item",
            rawDetectedItem = "",
            listingDescription = "",
            candidateItems = emptyList(),
            detectedConfidence = "Confidence: unknown",
            listedCount = 0,
            soldCount = 0,
            averageSoldPrice = "$0.00",
            medianSoldPrice = "$0.00",
            lowSoldPrice = "$0.00",
            highSoldPrice = "$0.00",
            normalizedKeywords = "",
            compsSource = "",
            visionProvider = "unknown",
            visionModelName = "unknown",
        )
        saveDrafts(updated)
        appendDebug("Draft ${if (existing == null) "saved" else "updated"}: ${newDraft.title}")
        return true
    }

    fun removeDraft(draftId: String) {
        val updated = _uiState.value.savedDrafts.filterNot { it.id == draftId }
        val clearEditor = _uiState.value.listingEditor?.draftId == draftId
        _uiState.value = _uiState.value.copy(
            savedDrafts = updated,
            listingEditor = if (clearEditor) null else _uiState.value.listingEditor,
            statusMessage = "Draft removed.",
        )
        saveDrafts(updated)
        appendDebug("Draft removed: $draftId")
    }

    private fun formatUsd(value: Double): String = "$" + String.format("%.2f", value)

    private fun appendDebug(message: String) {
        Log.d(logTag, message)
        if (!_uiState.value.debugEnabled) return
        val timestamp = LocalTime.now().format(timestampFormatter)
        val existing = _uiState.value.debugLog
        val updated = if (existing.isBlank()) {
            "[$timestamp] $message"
        } else {
            "$existing\n[$timestamp] $message"
        }
        _uiState.value = _uiState.value.copy(debugLog = updated.takeLast(6000))
    }

    private fun saveDrafts(drafts: List<SavedDraftItem>) {
        val json = JSONArray()
        drafts.forEach { draft ->
            json.put(
                JSONObject()
                    .put("id", draft.id)
                    .put("title", draft.title)
                    .put("medianPrice", draft.medianPrice)
                    .put("averagePrice", draft.averagePrice)
                    .put("lowPrice", draft.lowPrice)
                    .put("highPrice", draft.highPrice)
                    .put("source", draft.source)
                    .put("normalizedKeywords", draft.normalizedKeywords)
                    .put("suggestedKeywords", JSONArray(draft.suggestedKeywords))
                    .put("listedCount", draft.listedCount)
                    .put("soldCount", draft.soldCount)
                    .put("confidence", draft.confidence)
                    .put("listingNotes", draft.listingNotes)
                    .put("photoPath", draft.photoPath)
                    .put("targetPrice", draft.targetPrice)
                    .put("draftDescription", draft.draftDescription)
                    .put("additionalPhotoPaths", JSONArray(draft.additionalPhotoPaths))
                    .put("publishedListingUrl", draft.publishedListingUrl)
                    .put("condition", draft.condition)
                    .put("category", draft.category)
                    .put("shippingProfile", draft.shippingProfile)
                    .put("returnPolicy", draft.returnPolicy)
                    .put("quantity", draft.quantity)
            )
        }
        prefs.edit().putString("saved_drafts", json.toString()).apply()
    }

    private fun loadDrafts(): List<SavedDraftItem> {
        val raw = prefs.getString("saved_drafts", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").trim()
                    if (id.isBlank()) continue
                    add(
                        SavedDraftItem(
                            id = id,
                            title = item.optString("title", "Untitled draft"),
                            medianPrice = item.optString("medianPrice", "$0.00"),
                            averagePrice = item.optString("averagePrice", "$0.00"),
                            lowPrice = item.optString("lowPrice", "$0.00"),
                            highPrice = item.optString("highPrice", "$0.00"),
                            source = item.optString("source", "unknown"),
                            normalizedKeywords = item.optString("normalizedKeywords", ""),
                            suggestedKeywords = buildList {
                                val keywords = item.optJSONArray("suggestedKeywords")
                                if (keywords != null) {
                                    for (k in 0 until keywords.length()) {
                                        val keyword = keywords.optString(k).trim()
                                        if (keyword.isNotBlank()) add(keyword)
                                    }
                                }
                            },
                            listedCount = item.optInt("listedCount", 0),
                            soldCount = item.optInt("soldCount", 0),
                            confidence = item.optString("confidence", "Confidence: unknown"),
                            listingNotes = item.optString(
                                "listingNotes",
                                "Condition: auto-detected. Verify brand/model and complete item specifics before publishing.",
                            ),
                            photoPath = item.optString("photoPath", ""),
                            targetPrice = item.optString("targetPrice", item.optString("medianPrice", "$0.00")),
                            draftDescription = item.optString(
                                "draftDescription",
                                item.optString(
                                    "listingNotes",
                                    "Condition: auto-detected. Verify brand/model and complete item specifics before publishing.",
                                )
                            ),
                            additionalPhotoPaths = buildList {
                                val photos = item.optJSONArray("additionalPhotoPaths")
                                if (photos != null) {
                                    for (p in 0 until photos.length()) {
                                        val path = photos.optString(p).trim()
                                        if (path.isNotBlank()) add(path)
                                    }
                                }
                            },
                            publishedListingUrl = item.optString("publishedListingUrl", ""),
                            condition = item.optString("condition", ""),
                            category = item.optString("category", ""),
                            shippingProfile = item.optString("shippingProfile", ""),
                            returnPolicy = item.optString("returnPolicy", ""),
                            quantity = item.optInt("quantity", 1).coerceAtLeast(1),
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun loadSavedSettings() {
        val savedMode = prefs.getString("backend_mode", "local") ?: "local"
        val savedLocalUrl = normalizeBackendUrl(prefs.getString("local_backend_url", "").orEmpty())
        val savedCloudUrl = normalizeBackendUrl(prefs.getString("cloud_backend_url", "").orEmpty())
        val savedToken = prefs.getString("backend_api_token", "").orEmpty().trim()
        val drafts = loadDrafts()
        val localUrl = if (savedLocalUrl.isBlank()) DEFAULT_LOCAL_BACKEND_URL else savedLocalUrl
        val mode = if (savedMode == "cloud" && savedCloudUrl.isBlank()) "local" else savedMode
        val selectedUrl = if (mode == "cloud") savedCloudUrl else localUrl
        val connectionStatus = if (selectedUrl.isBlank()) "Not configured" else "Not tested"

        if (savedLocalUrl != localUrl || savedMode != mode) {
            prefs.edit()
                .putString("local_backend_url", localUrl)
                .putString("backend_mode", mode)
                .apply()
        }

        _uiState.value = _uiState.value.copy(
            backendMode = mode,
            localBackendUrl = localUrl,
            cloudBackendUrl = savedCloudUrl,
            backendBaseUrl = selectedUrl,
            backendApiToken = savedToken,
            connectionStatus = connectionStatus,
            statusMessage = if (selectedUrl.isBlank()) {
                "Set a backend URL to use cloud analysis, or capture a photo to use the local fallback."
            } else {
                "Ready to test backend connection. Tap Test in the Connection card."
            },
            savedDrafts = drafts,
        )
    }
}

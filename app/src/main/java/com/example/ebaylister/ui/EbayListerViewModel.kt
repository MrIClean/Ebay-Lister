package com.example.ebaylister.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebaylister.data.AnalyzerCorrectionStore
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
        val trimmed = url.trim()
        val mode = _uiState.value.backendMode
        if (mode == "cloud") {
            _uiState.value = _uiState.value.copy(backendBaseUrl = trimmed, cloudBackendUrl = trimmed)
            prefs.edit().putString("cloud_backend_url", trimmed).apply()
        } else {
            _uiState.value = _uiState.value.copy(backendBaseUrl = trimmed, localBackendUrl = trimmed)
            prefs.edit().putString("local_backend_url", trimmed).apply()
        }
        appendDebug("Backend URL updated for $mode mode: $trimmed")
    }

    fun updateBackendApiToken(token: String) {
        _uiState.value = _uiState.value.copy(backendApiToken = token)
        prefs.edit().putString("backend_api_token", token).apply()
        appendDebug("Backend API token updated.")
    }

    fun switchBackendMode(mode: String) {
        val chosenMode = if (mode == "cloud") "cloud" else "local"
        val chosenUrl = if (chosenMode == "cloud") _uiState.value.cloudBackendUrl else _uiState.value.localBackendUrl
        _uiState.value = _uiState.value.copy(
            backendMode = chosenMode,
            backendBaseUrl = chosenUrl,
        )
        prefs.edit().putString("backend_mode", chosenMode).apply()
        appendDebug("Switched backend mode to $chosenMode using URL $chosenUrl")
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            val backendUrl = _uiState.value.backendBaseUrl.trim().trimEnd('/')
            val token = _uiState.value.backendApiToken.trim()
            appendDebug("Testing backend connection: $backendUrl")

            val result = runCatching { backendClient.health(backendUrl, token) }
            if (result.isSuccess) {
                val health = result.getOrThrow()
                val status = "Connected (${health.status}) vision=${health.visionMode} ebayReal=${health.useRealEbay}"
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

            val backendUrl = _uiState.value.backendBaseUrl.trim().trimEnd('/')
            val token = _uiState.value.backendApiToken.trim()
            appendDebug("Analyze started. backend=$backendUrl image=$photoPath")
            val backendStartMs = System.currentTimeMillis()
            val backendResult = runCatching { backendClient.analyze(backendUrl, photoPath, token) }
            val backendDurationMs = System.currentTimeMillis() - backendStartMs

            if (backendResult.isSuccess) {
                val result = backendResult.getOrThrow()
                val correctedItem = correctionStore.getCorrection(result.model) ?: result.model
                appendDebug(
                    "Backend success in ${backendDurationMs}ms: vision=${result.visionProvider}/${result.visionModelName}, " +
                        "active=${result.activeCount}, sold=${result.soldTotalCount}, median=${result.medianSoldPrice}"
                )

                _uiState.value = _uiState.value.copy(
                    detectedItem = correctedItem,
                    rawDetectedItem = result.model,
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
        }
    }

    fun applyManualCorrection(correctedName: String) {
        val corrected = correctedName.trim()
        if (corrected.isBlank()) return

        val raw = _uiState.value.rawDetectedItem
        if (raw.isNotBlank()) {
            correctionStore.saveCorrection(raw, corrected)
            appendDebug("Saved local correction: '$raw' -> '$corrected'")
            viewModelScope.launch {
                runCatching {
                    backendClient.saveCorrection(
                        baseUrl = _uiState.value.backendBaseUrl.trim().trimEnd('/'),
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
        _uiState.value = _uiState.value.copy(savedDrafts = updated, statusMessage = "Draft removed.")
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
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun loadSavedSettings() {
        val savedMode = prefs.getString("backend_mode", "local") ?: "local"
        val savedLocalUrl = prefs.getString("local_backend_url", "http://192.168.68.170:8000") ?: "http://192.168.68.170:8000"
        val savedCloudUrl = prefs.getString("cloud_backend_url", "https://dean-exclusively-approved-receptors.trycloudflare.com")
            ?: "https://dean-exclusively-approved-receptors.trycloudflare.com"
        val savedToken = prefs.getString("backend_api_token", "f7b2c9fa7ae14a4abde984dd502cd420be4380ab2aeb4c4e894912814d445500")
            ?: "f7b2c9fa7ae14a4abde984dd502cd420be4380ab2aeb4c4e894912814d445500"
        val selectedUrl = if (savedMode == "cloud") savedCloudUrl else savedLocalUrl
        val drafts = loadDrafts()

        _uiState.value = _uiState.value.copy(
            backendMode = savedMode,
            localBackendUrl = savedLocalUrl,
            cloudBackendUrl = savedCloudUrl,
            backendBaseUrl = selectedUrl,
            backendApiToken = savedToken,
            savedDrafts = drafts,
        )
    }
}

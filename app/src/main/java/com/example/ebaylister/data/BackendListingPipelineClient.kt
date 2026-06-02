package com.example.ebaylister.data

import android.util.Base64
import com.example.ebaylister.domain.DraftPublishResult
import com.example.ebaylister.domain.ListingDraft
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class BackendListingPipelineClient {
    private val httpClient = OkHttpClient()

    suspend fun analyze(
        baseUrl: String,
        imagePath: String,
        apiToken: String = "",
        overrideKeywords: String = "",
    ): BackendAnalyzeResult = withContext(Dispatchers.IO) {
        val imageFile = File(imagePath)
        val imageBytes = imageFile.readBytes()
        val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val imageMimeType = when (imageFile.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val payloadJson = JSONObject()
            .put("image_path", imagePath)
            .put("image_base64", imageBase64)
            .put("image_mime_type", imageMimeType)
        if (overrideKeywords.isNotBlank()) {
            payloadJson.put("override_keywords", overrideKeywords.trim())
        }
        val payload = payloadJson.toString()

        val requestBuilder = Request.Builder()
            .url("$baseUrl/analyze")
            .post(payload.toRequestBody("application/json".toMediaType()))
        if (apiToken.isNotBlank()) {
            requestBuilder.header("X-Api-Key", apiToken)
        }
        val request = requestBuilder.build()

        withRetry {
            httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty().take(300)
                throw RuntimeException("Backend analyze failed: ${response.code} $errorBody")
            }

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val vision = root.getJSONObject("vision")
            val comps = root.getJSONObject("comps")

            val keywordsArray = vision.optJSONArray("suggested_keywords")
            val keywords = mutableListOf<String>()
            if (keywordsArray != null) {
                for (i in 0 until keywordsArray.length()) {
                    keywords += keywordsArray.optString(i)
                }
            }

            BackendAnalyzeResult(
                model = vision.optString("model", "Unknown item"),
                confidencePercent = (vision.optDouble("confidence", 0.0) * 100).toInt(),
                visionProvider = vision.optString("provider", "unknown"),
                visionModelName = vision.optString("model_name", "unknown"),
                listingDescription = vision.optString("listing_description", ""),
                suggestedKeywords = keywords,
                normalizedKeywords = root.optString("normalized_keywords", ""),
                soldCompsCount = comps.optInt("sold_count", 0),
                soldTotalCount = comps.optInt("sold_total_count", comps.optInt("sold_count", 0)),
                activeCount = comps.optInt("active_count", 0),
                averageSoldPrice = comps.optDouble("average_price", 0.0),
                medianSoldPrice = comps.optDouble("median_price", 0.0),
                lowSoldPrice = comps.optDouble("low_price", 0.0),
                highSoldPrice = comps.optDouble("high_price", 0.0),
                source = comps.optString("source", "unknown"),
            )
            }
        }
    }

    suspend fun saveCorrection(baseUrl: String, predicted: String, corrected: String, apiToken: String = "") = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("predicted", predicted)
            .put("corrected", corrected)
            .toString()

        val requestBuilder = Request.Builder()
            .url("$baseUrl/corrections")
            .post(payload.toRequestBody("application/json".toMediaType()))
        if (apiToken.isNotBlank()) {
            requestBuilder.header("X-Api-Key", apiToken)
        }
        val request = requestBuilder.build()

        withRetry {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty().take(300)
                    throw RuntimeException("Backend correction save failed: ${response.code} $errorBody")
                }
            }
        }
    }

    suspend fun publishDraft(baseUrl: String, draft: ListingDraft, apiToken: String = ""): DraftPublishResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("title", draft.title)
            .put("description", draft.description)
            .put("price", draft.price)
            .put("photo_paths", JSONArray(draft.photoPaths))
            .put("condition", draft.condition)
            .put("category", draft.category)
            .put("shipping_profile", draft.shippingProfile)
            .put("shipping_policy_id", draft.shippingPolicyId)
            .put("return_policy", draft.returnPolicy)
            .put("return_policy_id", draft.returnPolicyId)
            .put("quantity", draft.quantity)
            .put("channel", draft.channel)
            .toString()

        val requestBuilder = Request.Builder()
            .url("$baseUrl/publish")
            .post(payload.toRequestBody("application/json".toMediaType()))
        if (apiToken.isNotBlank()) {
            requestBuilder.header("X-Api-Key", apiToken)
        }
        val request = requestBuilder.build()

        withRetry {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw RuntimeException("Backend publish failed: ${response.code} ${body.take(300)}")
                }

                val root = JSONObject(body)
                val errorsArray = root.optJSONArray("validation_errors")
                val errors = mutableListOf<String>()
                if (errorsArray != null) {
                    for (i in 0 until errorsArray.length()) {
                        val error = errorsArray.optString(i).trim()
                        if (error.isNotBlank()) errors += error
                    }
                }

                DraftPublishResult(
                    success = root.optBoolean("success", false),
                    message = root.optString("message", "Publish response received."),
                    listingUrl = root.optString("listing_url", ""),
                    validationErrors = errors,
                )
            }
        }
    }

    suspend fun accountOptions(baseUrl: String, apiToken: String = ""): BackendAccountOptionsResult = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/account/options")
            .get()
        if (apiToken.isNotBlank()) {
            requestBuilder.header("X-Api-Key", apiToken)
        }
        val request = requestBuilder.build()

        withRetry {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw RuntimeException("Backend account options failed: ${response.code} ${body.take(300)}")
                }

                val root = JSONObject(body)
                BackendAccountOptionsResult(
                    connected = root.optBoolean("connected", false),
                    marketplaceId = root.optString("marketplace_id", "EBAY_US"),
                    source = root.optString("source", "unknown"),
                    message = root.optString("message", ""),
                    fulfillmentPolicies = parsePolicyOptions(root.optJSONArray("fulfillment_policies")),
                    returnPolicies = parsePolicyOptions(root.optJSONArray("return_policies")),
                )
            }
        }
    }

    suspend fun health(baseUrl: String, apiToken: String = ""): BackendHealthResult = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/health")
            .get()
        if (apiToken.isNotBlank()) {
            requestBuilder.header("X-Api-Key", apiToken)
        }
        val request = requestBuilder.build()

        withRetry {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty().take(300)
                    throw RuntimeException("Backend health failed: ${response.code} $errorBody")
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                BackendHealthResult(
                    status = root.optString("status", "unknown"),
                    visionMode = root.optString("vision_mode", "unknown"),
                    useRealEbay = root.optBoolean("use_real_ebay", false),
                    authRequired = root.optBoolean("auth_required", false),
                )
            }
        }
    }

    private suspend fun <T> withRetry(block: () -> T): T {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                return block()
            } catch (t: Throwable) {
                lastError = t
                if (attempt < 2) {
                    delay(if (attempt == 0) 250 else 700)
                }
            }
        }
        throw RuntimeException(lastError?.message ?: "Request failed after retries", lastError)
    }

    private fun parsePolicyOptions(array: JSONArray?): List<BackendPolicyOption> {
        if (array == null) return emptyList()
        val options = mutableListOf<BackendPolicyOption>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            val name = item.optString("name", "").trim()
            if (id.isNotBlank() && name.isNotBlank()) {
                options += BackendPolicyOption(
                    id = id,
                    name = name,
                    description = item.optString("description", ""),
                )
            }
        }
        return options
    }
}

data class BackendHealthResult(
    val status: String,
    val visionMode: String,
    val useRealEbay: Boolean,
    val authRequired: Boolean,
)

data class BackendAnalyzeResult(
    val model: String,
    val confidencePercent: Int,
    val visionProvider: String,
    val visionModelName: String,
    val listingDescription: String = "",
    val suggestedKeywords: List<String>,
    val normalizedKeywords: String,
    val soldCompsCount: Int,
    val soldTotalCount: Int,
    val activeCount: Int,
    val averageSoldPrice: Double,
    val medianSoldPrice: Double,
    val lowSoldPrice: Double,
    val highSoldPrice: Double,
    val source: String,
)

data class BackendPolicyOption(
    val id: String,
    val name: String,
    val description: String,
)

data class BackendAccountOptionsResult(
    val connected: Boolean,
    val marketplaceId: String,
    val source: String,
    val message: String,
    val fulfillmentPolicies: List<BackendPolicyOption>,
    val returnPolicies: List<BackendPolicyOption>,
)

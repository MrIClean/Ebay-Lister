package com.example.ebaylister.data

import android.util.Base64
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BackendListingPipelineClient {
    private val httpClient = OkHttpClient()

    suspend fun analyze(baseUrl: String, imagePath: String, apiToken: String = ""): BackendAnalyzeResult = withContext(Dispatchers.IO) {
        val imageFile = File(imagePath)
        val imageBytes = imageFile.readBytes()
        val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val imageMimeType = when (imageFile.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val payload = JSONObject()
            .put("image_path", imagePath)
            .put("image_base64", imageBase64)
            .put("image_mime_type", imageMimeType)
            .toString()

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

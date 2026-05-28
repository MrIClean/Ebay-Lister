package com.example.ebaylister.data

import android.content.Context
import android.net.Uri
import com.example.ebaylister.domain.ItemAnalysis
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.tasks.await

class MlKitItemAnalyzer(private val context: Context) {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.35f)
            .build(),
    )

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build(),
    )

    private val stopWords = setOf(
        "space", "sky", "atmosphere", "pattern", "line", "font", "text", "darkness", "light",
        "floor", "wall", "room", "indoor", "outdoor", "material", "design", "shape", "object",
    )

    private val materialWords = setOf(
        "metal", "plastic", "wood", "steel", "iron", "glass", "paper", "fabric",
    )

    private val productRules = listOf(
        ProductRule("Broom", listOf("broom", "mop", "sweeper", "brush")),
        ProductRule("Handheld game console", listOf("game console", "video game", "joystick", "controller", "handheld")),
        ProductRule("Headphones", listOf("headphones", "earphones", "headset")),
        ProductRule("Keyboard", listOf("keyboard", "keypad")),
        ProductRule("Computer mouse", listOf("computer mouse", "mouse")),
    )

    suspend fun analyze(photoPath: String): ItemAnalysis {
        val inputImage = InputImage.fromFilePath(context, Uri.fromFile(File(photoPath)))
        val imageLabels = runCatching { labeler.process(inputImage).await() }.getOrDefault(emptyList())
        val detectedObjects = runCatching { objectDetector.process(inputImage).await() }.getOrDefault(emptyList())

        val candidates = mutableListOf<RankedCandidate>()

        imageLabels.forEach { label ->
            val cleaned = normalize(label.text)
            if (cleaned.isNotBlank()) {
                candidates += RankedCandidate(cleaned, label.confidence, sourceBoost = 1.0f)
            }
        }

        detectedObjects.forEach { detectedObject ->
            detectedObject.labels.forEach { label ->
                val cleaned = normalize(label.text)
                if (cleaned.isNotBlank()) {
                    candidates += RankedCandidate(cleaned, label.confidence, sourceBoost = 1.2f)
                }
            }
        }

        val ruleMatch = matchProductRule(candidates)
        if (ruleMatch != null) {
            return ItemAnalysis(
                itemName = ruleMatch.name,
                confidenceLabel = "${(ruleMatch.confidence * 100).roundToInt()}%",
                candidates = candidates.map { it.name }.distinct().take(6),
            )
        }

        val best = candidates
            .map { it.copy(score = score(it)) }
            .filter { it.score > 0.18f }
            .sortedByDescending { it.score }
            .firstOrNull()

        if (best == null) {
            return ItemAnalysis(
                itemName = "Unknown item",
                confidenceLabel = "0%",
                candidates = candidates.map { it.name }.distinct().take(6),
            )
        }

        return ItemAnalysis(
            itemName = best.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            confidenceLabel = "${(best.score * 100).roundToInt()}%",
            candidates = candidates.map { it.name }.distinct().take(6),
        )
    }

    private fun matchProductRule(candidates: List<RankedCandidate>): RuleMatch? {
        val bestByRule = productRules.mapNotNull { rule ->
            val matched = candidates
                .filter { candidate ->
                    rule.keywords.any { keyword -> candidate.name.contains(keyword, ignoreCase = true) }
                }
                .maxByOrNull { it.confidence }

            matched?.let { RuleMatch(rule.displayName, it.confidence) }
        }

        return bestByRule.maxByOrNull { it.confidence }
    }

    private fun score(candidate: RankedCandidate): Float {
        var score = candidate.confidence * candidate.sourceBoost
        if (candidate.name in stopWords) score *= 0.1f
        if (candidate.name in materialWords) score *= 0.5f
        if (candidate.name.length <= 3) score *= 0.75f
        if (candidate.name.contains(" ")) score *= 1.1f
        return score.coerceIn(0f, 0.99f)
    }

    private fun normalize(raw: String): String {
        return raw.trim().lowercase()
    }

    private data class RankedCandidate(
        val name: String,
        val confidence: Float,
        val sourceBoost: Float,
        val score: Float = 0f,
    )

    private data class ProductRule(
        val displayName: String,
        val keywords: List<String>,
    )

    private data class RuleMatch(
        val name: String,
        val confidence: Float,
    )
}
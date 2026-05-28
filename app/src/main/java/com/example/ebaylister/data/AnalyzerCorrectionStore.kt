package com.example.ebaylister.data

import android.content.Context

class AnalyzerCorrectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("analyzer_corrections", Context.MODE_PRIVATE)

    fun getCorrection(predicted: String): String? {
        return prefs.getString(predicted.normalizeKey(), null)
    }

    fun saveCorrection(predicted: String, corrected: String) {
        if (predicted.isBlank() || corrected.isBlank()) return
        prefs.edit().putString(predicted.normalizeKey(), corrected.trim()).apply()
    }

    private fun String.normalizeKey(): String = trim().lowercase()
}

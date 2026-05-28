package com.example.ebaylister.data

import com.example.ebaylister.domain.ItemAnalysis

class FakeItemAnalyzer {
    suspend fun analyze(): ItemAnalysis {
        return ItemAnalysis(
            itemName = "Vintage Sony Walkman",
            confidenceLabel = "high",
        )
    }
}
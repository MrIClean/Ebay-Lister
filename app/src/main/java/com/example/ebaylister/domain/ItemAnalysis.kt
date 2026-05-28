package com.example.ebaylister.domain

data class ItemAnalysis(
    val itemName: String,
    val confidenceLabel: String,
    val candidates: List<String> = emptyList(),
)
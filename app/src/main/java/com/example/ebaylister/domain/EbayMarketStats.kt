package com.example.ebaylister.domain

data class EbayMarketStats(
    val listedCount: Int,
    val soldCount: Int,
    val medianSoldPrice: String,
)
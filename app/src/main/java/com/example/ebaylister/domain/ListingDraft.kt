package com.example.ebaylister.domain

data class ListingDraft(
    val title: String,
    val description: String,
    val price: String,
    val photoPaths: List<String> = emptyList(),
    val condition: String = "",
    val category: String = "",
    val shippingProfile: String = "",
    val returnPolicy: String = "",
    val quantity: Int = 1,
    val channel: String = "ebay",
)

package com.example.ebaylister.domain

data class DraftPublishResult(
    val success: Boolean,
    val message: String,
    val listingUrl: String = "",
    val validationErrors: List<String> = emptyList(),
)

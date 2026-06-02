package com.example.ebaylister.data

import com.example.ebaylister.domain.DraftPublishResult
import com.example.ebaylister.domain.ListingDraft

class FakeEbayListingPublisher {
    suspend fun publishDraft(draft: ListingDraft): DraftPublishResult {
        val errors = validateDraft(draft)
        if (errors.isNotEmpty()) {
            return DraftPublishResult(
                success = false,
                message = "Finish required listing details before publishing.",
                validationErrors = errors,
            )
        }

        val itemId = (draft.title + draft.price + draft.photoPaths.joinToString("|"))
            .hashCode()
            .toUInt()
            .toString()
        return DraftPublishResult(
            success = true,
            message = "Listing published to eBay (mock): ${draft.title}",
            listingUrl = "https://www.ebay.com/itm/$itemId",
        )
    }

    private fun validateDraft(draft: ListingDraft): List<String> {
        val errors = mutableListOf<String>()
        if (draft.title.trim().length < 10) errors += "Title should be at least 10 characters."
        if (draft.description.trim().length < 20) errors += "Description should be at least 20 characters."
        if (draft.price.trim().removePrefix("$").toDoubleOrNull() == null) errors += "Price must be a valid number."
        if (draft.photoPaths.isEmpty()) errors += "Add at least one photo."
        if (draft.condition.isBlank()) errors += "Choose an item condition."
        if (draft.category.isBlank()) errors += "Add an eBay category."
        if (draft.shippingProfile.isBlank()) errors += "Add a shipping profile."
        if (draft.returnPolicy.isBlank()) errors += "Add a return policy."
        if (draft.quantity < 1) errors += "Quantity must be at least 1."
        return errors
    }
}

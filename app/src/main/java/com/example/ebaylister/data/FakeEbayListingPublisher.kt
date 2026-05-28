package com.example.ebaylister.data

import com.example.ebaylister.domain.DraftPublishResult
import com.example.ebaylister.domain.ListingDraft

class FakeEbayListingPublisher {
    suspend fun publishDraft(draft: ListingDraft): DraftPublishResult {
        return DraftPublishResult(
            success = true,
            message = "Draft prepared for: ${draft.title}",
        )
    }
}
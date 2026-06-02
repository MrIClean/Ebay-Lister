from __future__ import annotations

import re

from app.models import PublishListingRequest, PublishListingResponse


class ListingPublishService:
    def prepare(self, draft: PublishListingRequest, real_publish_enabled: bool = False) -> PublishListingResponse:
        errors = self.validate(draft)
        if errors:
            return PublishListingResponse(
                success=False,
                message="Finish required listing details before publishing.",
                validation_errors=errors,
            )

        if not real_publish_enabled:
            return PublishListingResponse(
                success=True,
                message="Listing package validated. Real eBay submission is not enabled yet.",
                listing_url="",
                validation_errors=[],
            )

        return PublishListingResponse(
            success=True,
            message="Listing package validated. eBay Sell API submission can run here next.",
            listing_url="",
            validation_errors=[],
        )

    def validate(self, draft: PublishListingRequest) -> list[str]:
        errors: list[str] = []
        if len(draft.title.strip()) < 10:
            errors.append("Title should be at least 10 characters.")
        if len(draft.description.strip()) < 20:
            errors.append("Description should be at least 20 characters.")
        if self._price_value(draft.price) <= 0:
            errors.append("Price must be a valid positive number.")
        if not draft.photo_paths:
            errors.append("Add at least one photo.")
        if not draft.condition.strip():
            errors.append("Choose an item condition.")
        if not draft.category.strip():
            errors.append("Add an eBay category.")
        if not draft.shipping_profile.strip() and not draft.shipping_policy_id.strip():
            errors.append("Add a shipping profile.")
        if not draft.return_policy.strip() and not draft.return_policy_id.strip():
            errors.append("Add a return policy.")
        if draft.quantity < 1:
            errors.append("Quantity must be at least 1.")
        return errors

    def _price_value(self, price: str) -> float:
        cleaned = re.sub(r"[^0-9.]", "", price.strip())
        try:
            return float(cleaned)
        except ValueError:
            return 0.0

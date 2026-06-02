import unittest

from app.models import PublishListingRequest
from app.services.listing_publish import ListingPublishService


class ListingPublishServiceTest(unittest.TestCase):
    def test_publish_validation_rejects_incomplete_listing(self) -> None:
        service = ListingPublishService()
        result = service.prepare(
            PublishListingRequest(
                title="Toy",
                description="Short",
                price="$0",
                photo_paths=[],
            )
        )

        self.assertFalse(result.success)
        self.assertIn("Title should be at least 10 characters.", result.validation_errors)
        self.assertIn("Add at least one photo.", result.validation_errors)

    def test_publish_validation_accepts_complete_listing_package(self) -> None:
        service = ListingPublishService()
        result = service.prepare(
            PublishListingRequest(
                title="Sony Walkman WM-FX101 Portable Cassette Player",
                description="Tested portable cassette player with visible cosmetic wear.",
                price="$58.00",
                photo_paths=["C:/tmp/walkman.jpg"],
                condition="Pre-owned",
                category="Portable Audio & Headphones",
                shipping_profile="USPS Ground Advantage",
                return_policy="30 day returns",
                quantity=1,
            )
        )

        self.assertTrue(result.success)
        self.assertEqual(result.validation_errors, [])
        self.assertIn("validated", result.message.lower())

    def test_publish_validation_accepts_policy_ids(self) -> None:
        service = ListingPublishService()
        result = service.prepare(
            PublishListingRequest(
                title="Sony Walkman WM-FX101 Portable Cassette Player",
                description="Tested portable cassette player with visible cosmetic wear.",
                price="$58.00",
                photo_paths=["C:/tmp/walkman.jpg"],
                condition="Pre-owned",
                category="Portable Audio & Headphones",
                shipping_policy_id="fulfillment-123",
                return_policy_id="return-123",
                quantity=1,
            )
        )

        self.assertTrue(result.success)
        self.assertEqual(result.validation_errors, [])


if __name__ == "__main__":
    unittest.main()

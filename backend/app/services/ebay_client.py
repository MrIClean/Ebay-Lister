from __future__ import annotations

import hashlib
from dataclasses import dataclass

import httpx

from app.models import SoldListing


class EbayClientError(Exception):
    pass


class EbayClient:
    def source_name(self) -> str:
        return "unknown"

    async def sold_listings(self, keywords: str, limit: int = 10) -> list[SoldListing]:
        raise NotImplementedError

    async def market_counts(self, keywords: str) -> tuple[int, int]:
        raise NotImplementedError


@dataclass
class EbayCredentials:
    client_id: str
    client_secret: str
    refresh_token: str
    environment: str = "sandbox"
    marketplace_id: str = "EBAY_US"


class MockEbayClient(EbayClient):
    def source_name(self) -> str:
        return "Estimated Market Value"

    async def sold_listings(self, keywords: str, limit: int = 10) -> list[SoldListing]:
        seed = int(hashlib.sha256(keywords.encode("utf-8")).hexdigest(), 16)
        base = 20 + (seed % 120)
        results: list[SoldListing] = []
        for i in range(limit):
            price = round(base * (0.8 + ((i % 5) * 0.07)), 2)
            results.append(
                SoldListing(
                    title=f"{keywords} - sold comp {i + 1}",
                    price=price,
                    currency="USD",
                    url="",
                    sold_date="",
                )
            )
        return results

    async def market_counts(self, keywords: str) -> tuple[int, int]:
        seed = int(hashlib.sha256((keywords + "counts").encode("utf-8")).hexdigest(), 16)
        sold_total = 20 + (seed % 280)
        active_total = sold_total + 15 + (seed % 120)
        return active_total, sold_total


class RealEbayClient(EbayClient):
    def __init__(self, creds: EbayCredentials) -> None:
        self.creds = creds
        env = (creds.environment or "sandbox").strip().lower()
        if env not in {"sandbox", "production"}:
            raise EbayClientError("EBAY_ENVIRONMENT must be 'sandbox' or 'production'")
        self.environment = env
        self.api_host = "https://api.sandbox.ebay.com" if env == "sandbox" else "https://api.ebay.com"
        self.marketplace_id = (creds.marketplace_id or "EBAY_US").strip() or "EBAY_US"

    def source_name(self) -> str:
        return "eBay Sold Comps"

    async def sold_listings(self, keywords: str, limit: int = 10) -> list[SoldListing]:
        items, _total = await self._search_items(keywords=keywords, sold_only=True, limit=limit)
        listings: list[SoldListing] = []
        for item in items[:limit]:
            price_info = item.get("price", {})
            price = float(price_info.get("value", 0))
            currency = price_info.get("currency", "USD")
            listings.append(
                SoldListing(
                    title=item.get("title", "Unknown listing"),
                    price=price,
                    currency=currency,
                    url=item.get("itemWebUrl", ""),
                    sold_date=item.get("itemEndDate", ""),
                )
            )

        return listings

    async def market_counts(self, keywords: str) -> tuple[int, int]:
        _sold_items, sold_total = await self._search_items(keywords=keywords, sold_only=True, limit=1)
        _active_items, active_total = await self._search_items(keywords=keywords, sold_only=False, limit=1)
        return active_total, sold_total

    async def _search_items(self, keywords: str, sold_only: bool, limit: int) -> tuple[list[dict], int]:
        token = await self._access_token()
        endpoint = f"{self.api_host}/buy/browse/v1/item_summary/search"
        filter_value = "buyingOptions:{FIXED_PRICE|AUCTION},itemLocationCountry:US"
        if sold_only:
            filter_value += ",soldItemsOnly:true"

        params = {
            "q": keywords,
            "limit": str(limit),
            "filter": filter_value,
        }

        headers = {
            "Authorization": f"Bearer {token}",
            "X-EBAY-C-MARKETPLACE-ID": self.marketplace_id,
        }

        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.get(endpoint, params=params, headers=headers)
            if response.status_code >= 400:
                raise EbayClientError(f"eBay search failed: {response.status_code} {response.text}")

        data = response.json()
        items = data.get("itemSummaries", [])
        total = int(data.get("total", len(items)) or len(items))
        return items, total

    async def _access_token(self) -> str:
        if not self.creds.client_id or not self.creds.client_secret or not self.creds.refresh_token:
            raise EbayClientError("Missing eBay OAuth credentials")

        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(
                f"{self.api_host}/identity/v1/oauth2/token",
                data={
                    "grant_type": "refresh_token",
                    "refresh_token": self.creds.refresh_token,
                    "scope": "https://api.ebay.com/oauth/api_scope",
                },
                auth=(self.creds.client_id, self.creds.client_secret),
                headers={"Content-Type": "application/x-www-form-urlencoded"},
            )
            if response.status_code >= 400:
                raise EbayClientError(f"eBay token failed: {response.status_code} {response.text}")

        payload = response.json()
        token = payload.get("access_token")
        if not token:
            raise EbayClientError("eBay token response missing access_token")
        return token

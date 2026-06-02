from __future__ import annotations

import hashlib
from dataclasses import dataclass
import re

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

    async def search_by_image(self, image_base64: str, limit: int = 20) -> "ImageSearchResult":
        raise EbayClientError("Image search is not available for this client")

    async def catalog_by_barcode(self, barcode: str) -> "CatalogProductResult":
        raise EbayClientError("Barcode catalog lookup is not available for this client")


@dataclass
class EbayCredentials:
    client_id: str
    client_secret: str
    refresh_token: str
    environment: str = "sandbox"
    marketplace_id: str = "EBAY_US"


@dataclass
class ImageSearchResult:
    top_title: str
    category: str
    keywords: list[str]
    active_count: int
    listings: list[SoldListing]


@dataclass
class CatalogProductResult:
    title: str
    brand: str
    category: str
    epid: str
    product_web_url: str
    image_url: str
    gtins: list[str]


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

    async def search_by_image(self, image_base64: str, limit: int = 20) -> ImageSearchResult:
        raise EbayClientError("Image search requires USE_REAL_EBAY=true")

    async def catalog_by_barcode(self, barcode: str) -> CatalogProductResult:
        raise EbayClientError("Barcode catalog lookup requires USE_REAL_EBAY=true")


class RealEbayClient(EbayClient):
    def __init__(self, creds: EbayCredentials) -> None:
        self.creds = creds
        env = (creds.environment or "sandbox").strip().lower()
        if env not in {"sandbox", "production"}:
            raise EbayClientError("EBAY_ENVIRONMENT must be 'sandbox' or 'production'")
        self.environment = env
        self.api_host = "https://api.sandbox.ebay.com" if env == "sandbox" else "https://api.ebay.com"
        self.marketplace_id = (creds.marketplace_id or "EBAY_US").strip() or "EBAY_US"
        self.browse_scope = "https://api.ebay.com/oauth/api_scope"
        self.catalog_scope = "https://api.ebay.com/oauth/api_scope/commerce.catalog.readonly"

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

    async def search_by_image(self, image_base64: str, limit: int = 20) -> ImageSearchResult:
        if self.environment == "sandbox":
            raise EbayClientError("Browse search_by_image is not supported in sandbox; use production credentials")

        token = await self._access_token()
        endpoint = f"{self.api_host}/buy/browse/v1/item_summary/search_by_image"
        headers = {
            "Authorization": f"Bearer {token}",
            "X-EBAY-C-MARKETPLACE-ID": self.marketplace_id,
            "Content-Type": "application/json",
        }
        payload = {"image": image_base64}
        params = {"limit": str(min(max(limit, 1), 200))}

        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(endpoint, json=payload, params=params, headers=headers)
            if response.status_code >= 400:
                raise EbayClientError(f"eBay image search failed: {response.status_code} {response.text}")

        data = response.json()
        items = data.get("itemSummaries", [])
        if not items:
            raise EbayClientError("eBay image search returned no matching listings")

        listings: list[SoldListing] = []
        keywords: list[str] = []
        top_title = "Unknown item"
        category = "General"

        for index, item in enumerate(items[:limit]):
            title = (item.get("title") or "").strip()
            if index == 0 and title:
                top_title = title[:120]

            categories = item.get("categories") or []
            if category == "General" and categories:
                category_name = (categories[-1].get("categoryName") or "").strip()
                if category_name:
                    category = category_name[:80]

            price_raw = (item.get("price") or {}).get("value", 0)
            try:
                price = float(price_raw)
            except (TypeError, ValueError):
                price = 0.0

            currency = (item.get("price") or {}).get("currency", "USD")
            if price > 0:
                listings.append(
                    SoldListing(
                        title=title or "Unknown listing",
                        price=price,
                        currency=currency,
                        url=item.get("itemWebUrl", ""),
                        sold_date=item.get("itemEndDate", ""),
                    )
                )

            extracted = self._title_to_keyword(title)
            if extracted:
                keywords.append(extracted)

        if not listings:
            raise EbayClientError("eBay image search returned listings without usable prices")

        deduped_keywords = list(dict.fromkeys(k for k in keywords if k))[:6]
        if not deduped_keywords and top_title != "Unknown item":
            deduped_keywords = [self._title_to_keyword(top_title)]

        active_count = int(data.get("total", len(items)) or len(items))
        return ImageSearchResult(
            top_title=top_title,
            category=category,
            keywords=[k for k in deduped_keywords if k],
            active_count=active_count,
            listings=listings,
        )

    async def catalog_by_barcode(self, barcode: str) -> CatalogProductResult:
        code = "".join(ch for ch in barcode.strip() if ch.isdigit())
        if not code:
            raise EbayClientError("Barcode must contain digits")

        token = await self._access_token(scope=self.catalog_scope)
        endpoint = f"{self.api_host}/commerce/catalog/v1_beta/product_summary/search"
        headers = {
            "Authorization": f"Bearer {token}",
            "X-EBAY-C-MARKETPLACE-ID": self.marketplace_id,
        }
        params = {
            "gtin": code,
            "limit": "5",
        }

        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.get(endpoint, params=params, headers=headers)
            if response.status_code >= 400:
                raise EbayClientError(f"eBay barcode catalog lookup failed: {response.status_code} {response.text}")

        payload = response.json()
        products = payload.get("productSummaries") or []
        if not products:
            raise EbayClientError("No eBay catalog products matched that barcode")

        product = products[0]
        gtins = [str(value) for value in (product.get("gtin") or []) if value]
        category = ""
        for aspect in product.get("aspects", []) or []:
            aspect_name = (aspect.get("localizedName") or "").strip().lower()
            if aspect_name in {"type", "model", "product", "category"}:
                values = aspect.get("localizedValues") or []
                if values:
                    category = str(values[0])
                    break

        image = product.get("image") or {}
        return CatalogProductResult(
            title=(product.get("title") or "Unknown product").strip(),
            brand=(product.get("brand") or "").strip(),
            category=category.strip(),
            epid=(product.get("epid") or "").strip(),
            product_web_url=(product.get("productWebUrl") or "").strip(),
            image_url=(image.get("imageUrl") or "").strip(),
            gtins=gtins,
        )

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

    async def _access_token(self, scope: str | None = None) -> str:
        if not self.creds.client_id or not self.creds.client_secret:
            raise EbayClientError("Missing eBay OAuth credentials")

        requested_scope = scope or self.browse_scope

        async with httpx.AsyncClient(timeout=20.0) as client:
            if self.creds.refresh_token:
                refresh_response = await client.post(
                    f"{self.api_host}/identity/v1/oauth2/token",
                    data={
                        "grant_type": "refresh_token",
                        "refresh_token": self.creds.refresh_token,
                        "scope": requested_scope,
                    },
                    auth=(self.creds.client_id, self.creds.client_secret),
                    headers={"Content-Type": "application/x-www-form-urlencoded"},
                )
                if refresh_response.status_code < 400:
                    payload = refresh_response.json()
                    token = payload.get("access_token")
                    if token:
                        return token

            # Fallback: obtain an app token using client credentials.
            app_response = await client.post(
                f"{self.api_host}/identity/v1/oauth2/token",
                data={
                    "grant_type": "client_credentials",
                    "scope": requested_scope,
                },
                auth=(self.creds.client_id, self.creds.client_secret),
                headers={"Content-Type": "application/x-www-form-urlencoded"},
            )
            if app_response.status_code >= 400:
                raise EbayClientError(f"eBay token failed: {app_response.status_code} {app_response.text}")

        payload = app_response.json()
        token = payload.get("access_token")
        if not token:
            raise EbayClientError("eBay token response missing access_token")
        return token

    def _title_to_keyword(self, title: str) -> str:
        if not title:
            return ""
        normalized = re.sub(r"[^a-zA-Z0-9\s]", " ", title).strip().lower()
        return " ".join(normalized.split())[:80]

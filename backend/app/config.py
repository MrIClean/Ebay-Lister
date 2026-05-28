from dataclasses import dataclass
import os


@dataclass
class Settings:
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
    use_google_lens: bool = os.getenv("USE_GOOGLE_LENS", "false").lower() == "true"
    serpapi_api_key: str = os.getenv("SERPAPI_API_KEY", "")
    backend_api_token: str = os.getenv("BACKEND_API_TOKEN", "")
    ebay_client_id: str = os.getenv("EBAY_CLIENT_ID", "")
    ebay_client_secret: str = os.getenv("EBAY_CLIENT_SECRET", "")
    ebay_redirect_uri: str = os.getenv("EBAY_REDIRECT_URI", "")
    ebay_refresh_token: str = os.getenv("EBAY_REFRESH_TOKEN", "")
    use_real_ebay: bool = os.getenv("USE_REAL_EBAY", "false").lower() == "true"
    allow_mock_fallback: bool = os.getenv("ALLOW_MOCK_FALLBACK", "true").lower() == "true"
    cache_ttl_hours: int = int(os.getenv("CACHE_TTL_HOURS", "24"))


def get_settings() -> Settings:
    return Settings()

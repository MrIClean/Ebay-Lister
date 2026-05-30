from dataclasses import dataclass
from dataclasses import field
import os


def _env_bool(name: str, default: str = "false") -> bool:
    return os.getenv(name, default).lower() == "true"


def _env_str(name: str, default: str = "") -> str:
    return os.getenv(name, default)


def _env_int(name: str, default: str) -> int:
    return int(os.getenv(name, default))


@dataclass
class Settings:
    gemini_api_key: str = field(default_factory=lambda: _env_str("GEMINI_API_KEY"))
    gemini_model: str = field(default_factory=lambda: _env_str("GEMINI_MODEL", "gemini-2.5-flash"))
    use_google_lens: bool = field(default_factory=lambda: _env_bool("USE_GOOGLE_LENS"))
    serpapi_api_key: str = field(default_factory=lambda: _env_str("SERPAPI_API_KEY"))
    backend_api_token: str = field(default_factory=lambda: _env_str("BACKEND_API_TOKEN"))
    ebay_client_id: str = field(default_factory=lambda: _env_str("EBAY_CLIENT_ID"))
    ebay_client_secret: str = field(default_factory=lambda: _env_str("EBAY_CLIENT_SECRET"))
    ebay_redirect_uri: str = field(default_factory=lambda: _env_str("EBAY_REDIRECT_URI"))
    ebay_refresh_token: str = field(default_factory=lambda: _env_str("EBAY_REFRESH_TOKEN"))
    ebay_environment: str = field(default_factory=lambda: _env_str("EBAY_ENVIRONMENT", "sandbox"))
    ebay_marketplace_id: str = field(default_factory=lambda: _env_str("EBAY_MARKETPLACE_ID", "EBAY_US"))
    use_real_ebay: bool = field(default_factory=lambda: _env_bool("USE_REAL_EBAY"))
    allow_mock_fallback: bool = field(default_factory=lambda: _env_bool("ALLOW_MOCK_FALLBACK", "true"))
    cache_ttl_hours: int = field(default_factory=lambda: _env_int("CACHE_TTL_HOURS", "24"))


def get_settings() -> Settings:
    return Settings()

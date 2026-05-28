from pydantic import BaseModel, Field


class VisionResult(BaseModel):
    brand: str
    model: str
    category: str
    condition_guess: str
    suggested_keywords: list[str] = Field(default_factory=list)
    draft_title: str
    confidence: float
    provider: str = "unknown"
    model_name: str = "unknown"


class SoldListing(BaseModel):
    title: str
    price: float
    currency: str = "USD"
    url: str = ""
    sold_date: str = ""


class CompsSummary(BaseModel):
    sold_count: int
    sold_total_count: int = 0
    active_count: int = 0
    average_price: float
    median_price: float
    low_price: float
    high_price: float
    source: str
    cached: bool = False
    listings: list[SoldListing] = Field(default_factory=list)


class AnalyzeRequest(BaseModel):
    image_path: str | None = None
    image_base64: str | None = None
    image_mime_type: str | None = None
    override_keywords: str | None = None


class AnalyzeResponse(BaseModel):
    vision: VisionResult
    normalized_keywords: str
    comps: CompsSummary


class CorrectionRequest(BaseModel):
    predicted: str
    corrected: str

from statistics import median

from app.models import CompsSummary, SoldListing


class ValuationService:
    def summarize(
        self,
        listings: list[SoldListing],
        source: str,
        cached: bool = False,
        sold_total_count: int | None = None,
        active_count: int | None = None,
    ) -> CompsSummary:
        sold_total = sold_total_count if sold_total_count is not None else len(listings)
        active_total = active_count if active_count is not None else 0

        if not listings:
            return CompsSummary(
                sold_count=0,
                sold_total_count=sold_total,
                active_count=active_total,
                average_price=0.0,
                median_price=0.0,
                low_price=0.0,
                high_price=0.0,
                source=source,
                cached=cached,
                listings=[],
            )

        prices = [item.price for item in listings if item.price > 0]
        filtered = self._remove_outliers(prices)
        if not filtered:
            filtered = prices

        avg = sum(filtered) / len(filtered)
        med = median(filtered)

        return CompsSummary(
            sold_count=len(listings),
            sold_total_count=sold_total,
            active_count=active_total,
            average_price=round(avg, 2),
            median_price=round(float(med), 2),
            low_price=round(min(filtered), 2),
            high_price=round(max(filtered), 2),
            source=source,
            cached=cached,
            listings=listings,
        )

    def _remove_outliers(self, values: list[float]) -> list[float]:
        if len(values) < 4:
            return values

        ordered = sorted(values)
        q1 = ordered[len(ordered) // 4]
        q3 = ordered[(len(ordered) * 3) // 4]
        iqr = q3 - q1
        lower = q1 - 1.5 * iqr
        upper = q3 + 1.5 * iqr
        return [v for v in values if lower <= v <= upper]

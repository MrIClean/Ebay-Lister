package com.example.ebaylister.data

import com.example.ebaylister.domain.EbayMarketStats

class FakeEbayMarketplaceRepository {
    suspend fun lookupMarketStats(itemName: String): EbayMarketStats {
        return when {
            itemName.contains("Walkman", ignoreCase = true) -> {
                EbayMarketStats(
                    listedCount = 124,
                    soldCount = 87,
                    medianSoldPrice = "$58.00",
                )
            }

            else -> {
                EbayMarketStats(
                    listedCount = 0,
                    soldCount = 0,
                    medianSoldPrice = "$0.00",
                )
            }
        }
    }
}
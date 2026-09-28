package com.example.engine

import com.example.data.model.CryptoAsset
import com.example.data.model.LevelMap
import com.example.data.model.LevelZone
import com.example.data.model.ZoneType
import kotlin.math.max

class StructureEngine {

    /**
     * Builds dynamically computed level map for any asset based on current price and ATR
     */
    fun buildLevelMap(asset: CryptoAsset): LevelMap {
        val p = asset.lastPrice
        val atr = asset.atr5m
        val zoneWidth = max(atr * 0.40, p * 0.003)

        // Generate dynamic clustered zones around current price
        val htfResistanceHigh = p + (atr * 4.2)
        val htfResistanceLow = htfResistanceHigh - zoneWidth

        val reclaimHigh = p + (atr * 1.5)
        val reclaimLow = reclaimHigh - (zoneWidth * 0.7)

        val pivot = p - (atr * 0.2)

        val htfSupportHigh = p - (atr * 2.8)
        val htfSupportLow = htfSupportHigh - zoneWidth

        val breakdownZoneHigh = p - (atr * 4.5)
        val breakdownZoneLow = breakdownZoneHigh - (zoneWidth * 0.8)

        val stopPoolAbove = p + (atr * 5.8)

        val zones = listOf(
            LevelZone(
                id = "${asset.symbol}_STOP_POOL_UP",
                zoneType = ZoneType.STOP_POOL,
                priceLow = stopPoolAbove - (zoneWidth * 0.5),
                priceHigh = stopPoolAbove + (zoneWidth * 0.5),
                timeframe = "1d",
                confidence = 88,
                touches = 4,
                freshnessScore = 92
            ),
            LevelZone(
                id = "${asset.symbol}_HTF_RESIST",
                zoneType = ZoneType.HTF_RESISTANCE,
                priceLow = htfResistanceLow,
                priceHigh = htfResistanceHigh,
                timeframe = "4h",
                confidence = 94,
                touches = 5,
                freshnessScore = 85
            ),
            LevelZone(
                id = "${asset.symbol}_RECLAIM_ZONE",
                zoneType = ZoneType.RECLAIM_ZONE,
                priceLow = reclaimLow,
                priceHigh = reclaimHigh,
                timeframe = "1h",
                confidence = 78,
                touches = 3,
                freshnessScore = 95
            ),
            LevelZone(
                id = "${asset.symbol}_LTF_PIVOT",
                zoneType = ZoneType.LTF_PIVOT,
                priceLow = pivot - (zoneWidth * 0.3),
                priceHigh = pivot + (zoneWidth * 0.3),
                timeframe = "15m",
                confidence = 82,
                touches = 6,
                freshnessScore = 90
            ),
            LevelZone(
                id = "${asset.symbol}_HTF_SUPP",
                zoneType = ZoneType.HTF_SUPPORT,
                priceLow = htfSupportLow,
                priceHigh = htfSupportHigh,
                timeframe = "4h",
                confidence = 91,
                touches = 4,
                freshnessScore = 88
            ),
            LevelZone(
                id = "${asset.symbol}_BREAKDOWN",
                zoneType = ZoneType.BREAKDOWN_ZONE,
                priceLow = breakdownZoneLow,
                priceHigh = breakdownZoneHigh,
                timeframe = "1d",
                confidence = 85,
                touches = 2,
                freshnessScore = 79
            )
        )

        val damageScore = if (asset.priceChange24h < -3.0) 72 else 28

        return LevelMap(
            asset = asset.symbol,
            zones = zones,
            dominantPivot = pivot,
            damageScore = damageScore,
            confidenceSummary = 88,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Freshness decay: as time lapses, older zones decay in freshness score
     */
    fun decayZoneFreshness(zone: LevelZone, elapsedMinutes: Long): LevelZone {
        val decayRate = when (zone.timeframe) {
            "5m" -> 1.5
            "15m" -> 0.8
            "1h" -> 0.3
            "4h" -> 0.08
            else -> 0.02
        }
        val newFreshness = (zone.freshnessScore - (elapsedMinutes * decayRate)).toInt().coerceIn(10, 100)
        return zone.copy(freshnessScore = newFreshness)
    }
}

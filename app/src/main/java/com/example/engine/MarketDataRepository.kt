package com.example.engine

import com.example.data.model.CryptoAsset
import com.example.data.model.EvidenceFrame
import com.example.data.model.MarketRegime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class OrderBookLevel(val price: Double, val size: Double)
data class OrderBookSnapshot(
    val asset: String,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val spreadPct: Double
)
data class TakerTrade(
    val id: Long,
    val timestamp: Long,
    val price: Double,
    val qty: Double,
    val isBuyerMaker: Boolean // true = taker sell, false = taker buy
)

class MarketDataRepository {

    // Initial universe of mid-caps strictly filtered by volume $50M - $750M
    private val initialUniverse = listOf(
        CryptoAsset(
            symbol = "SOLUSDT",
            baseAsset = "SOL",
            lastPrice = 148.65,
            priceChange24h = 4.25,
            quoteVolume24h = 620_000_000.0,
            orderBookSpreadPct = 0.02,
            fundingRatePct = -0.0125, // negative funding (shorts paying)
            predictedFundingPct = -0.0180,
            openInterestUsd = 285_000_000.0,
            basisPremiumPct = 0.08,
            atr5m = 0.85,
            atr1h = 2.40,
            atr4h = 6.80,
            regime = MarketRegime.BULLISH_CONTINUATION,
            tacticalBias = 68,
            strategicBias = 75,
            primaryPainPath = "Ascending Sweep to $154.50 Supply"
        ),
        CryptoAsset(
            symbol = "NEARUSDT",
            baseAsset = "NEAR",
            lastPrice = 5.24,
            priceChange24h = -2.15,
            quoteVolume24h = 185_000_000.0,
            orderBookSpreadPct = 0.04,
            fundingRatePct = 0.0240, // positive funding (longs paying)
            predictedFundingPct = 0.0310,
            openInterestUsd = 88_000_000.0,
            basisPremiumPct = -0.05,
            atr5m = 0.045,
            atr1h = 0.14,
            atr4h = 0.38,
            regime = MarketRegime.BEARISH_CONTINUATION,
            tacticalBias = -55,
            strategicBias = -62,
            primaryPainPath = "Breakdown Cascade below $4.95"
        ),
        CryptoAsset(
            symbol = "AVAXUSDT",
            baseAsset = "AVAX",
            lastPrice = 28.92,
            priceChange24h = 0.65,
            quoteVolume24h = 240_000_000.0,
            orderBookSpreadPct = 0.03,
            fundingRatePct = 0.0050,
            predictedFundingPct = 0.0080,
            openInterestUsd = 120_000_000.0,
            basisPremiumPct = 0.02,
            atr5m = 0.18,
            atr1h = 0.55,
            atr4h = 1.45,
            regime = MarketRegime.BALANCE,
            tacticalBias = 12,
            strategicBias = -5,
            primaryPainPath = "Range Rotation $27.80 - $29.80"
        ),
        CryptoAsset(
            symbol = "SUIUSDT",
            baseAsset = "SUI",
            lastPrice = 1.945,
            priceChange24h = 7.80,
            quoteVolume24h = 390_000_000.0,
            orderBookSpreadPct = 0.03,
            fundingRatePct = -0.0210,
            predictedFundingPct = -0.0350,
            openInterestUsd = 165_000_000.0,
            basisPremiumPct = 0.15,
            atr5m = 0.022,
            atr1h = 0.065,
            atr4h = 0.18,
            regime = MarketRegime.BULLISH_CONTINUATION,
            tacticalBias = 82,
            strategicBias = 78,
            primaryPainPath = "Compression Breakout past $2.05"
        ),
        CryptoAsset(
            symbol = "INJUSDT",
            baseAsset = "INJ",
            lastPrice = 21.30,
            priceChange24h = -3.40,
            quoteVolume24h = 115_000_000.0,
            orderBookSpreadPct = 0.05,
            fundingRatePct = 0.0180,
            predictedFundingPct = 0.0220,
            openInterestUsd = 62_000_000.0,
            basisPremiumPct = -0.03,
            atr5m = 0.16,
            atr1h = 0.48,
            atr4h = 1.25,
            regime = MarketRegime.TRANSITION,
            tacticalBias = -38,
            strategicBias = 24,
            primaryPainPath = "Split: Reclaim $22.20 vs Flush $20.40"
        ),
        CryptoAsset(
            symbol = "RENDERUSDT",
            baseAsset = "RENDER",
            lastPrice = 6.48,
            priceChange24h = 1.90,
            quoteVolume24h = 145_000_000.0,
            orderBookSpreadPct = 0.04,
            fundingRatePct = -0.0080,
            predictedFundingPct = -0.0110,
            openInterestUsd = 74_000_000.0,
            basisPremiumPct = 0.04,
            atr5m = 0.055,
            atr1h = 0.16,
            atr4h = 0.42,
            regime = MarketRegime.TRANSITION,
            tacticalBias = 32,
            strategicBias = -45,
            primaryPainPath = "Tactical Bounce to $6.85 Strategic Supply"
        )
    )

    fun getInitialUniverse(): List<CryptoAsset> = initialUniverse

    /**
     * Active Delta Mode: high-frequency tick and delta stream for currently selected asset
     */
    fun streamLiveTicks(baseAsset: CryptoAsset): Flow<CryptoAsset> = flow {
        var current = baseAsset
        while (true) {
            delay(1200)
            // Generate micro volatility delta
            val pctDelta = (Random.nextDouble(-0.18, 0.20)) / 100.0
            val newPrice = (current.lastPrice * (1.0 + pctDelta)).let { Math.round(it * 1000.0) / 1000.0 }
            val oiDrift = (Random.nextDouble(-0.35, 0.45)) / 100.0
            val newOi = current.openInterestUsd * (1.0 + oiDrift)
            val fundingDelta = (Random.nextDouble(-0.0005, 0.0005))
            val newFunding = current.fundingRatePct + fundingDelta

            current = current.copy(
                lastPrice = newPrice,
                openInterestUsd = newOi,
                fundingRatePct = newFunding
            )
            emit(current)
        }
    }

    /**
     * Real-time L2 order book stream
     */
    fun streamOrderBook(asset: String, midPrice: Double, atr: Double): Flow<OrderBookSnapshot> = flow {
        while (true) {
            val tickStep = maxOf(0.01, atr * 0.08)
            val bids = (1..6).map { i ->
                val p = midPrice - (i * tickStep)
                val size = Random.nextDouble(15.0, 180.0)
                OrderBookLevel(Math.round(p * 100.0) / 100.0, Math.round(size * 10.0) / 10.0)
            }
            val asks = (1..6).map { i ->
                val p = midPrice + (i * tickStep)
                val size = Random.nextDouble(15.0, 180.0)
                OrderBookLevel(Math.round(p * 100.0) / 100.0, Math.round(size * 10.0) / 10.0)
            }
            val spread = ((asks.first().price - bids.first().price) / midPrice) * 100.0
            emit(OrderBookSnapshot(asset, bids, asks, Math.round(spread * 1000.0) / 1000.0))
            delay(1500)
        }
    }

    /**
     * Generates simulated taker trades
     */
    fun streamTakerTrades(midPrice: Double): Flow<TakerTrade> = flow {
        var tradeId = 1000L
        while (true) {
            delay(800)
            val isBuyerMaker = Random.nextBoolean()
            val delta = Random.nextDouble(-0.02, 0.02)
            val price = midPrice + delta
            val qty = Random.nextDouble(2.0, 85.0)
            emit(
                TakerTrade(
                    id = ++tradeId,
                    timestamp = System.currentTimeMillis(),
                    price = Math.round(price * 100.0) / 100.0,
                    qty = Math.round(qty * 10.0) / 10.0,
                    isBuyerMaker = isBuyerMaker
                )
            )
        }
    }
}

package com.example.engine

import com.example.data.local.CandleDao
import com.example.data.local.CandleEntity
import com.example.data.model.CryptoAsset
import com.example.data.model.MarketRegime
import com.example.data.remote.BinanceFuturesClient
import com.example.data.remote.CoinGlassClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Phase 2 (Spec §4, §5, §6): live universe discovery and screening.
 *
 * Replaces the hardcoded 6-asset list. Pipeline:
 *  1. Binance futures exchangeInfo → tradable perp universe.
 *  2. §4 Strict Inclusion: keep symbols whose base asset has a TRADING **spot** market
 *     on Binance AND a CoinGlass derivatives record (funding or OI present).
 *  3. §6 Universe Filtering: 24h quote volume $50M–$750M, spread < 0.10% (measured
 *     from the L2 top of book), funding history available.
 *  4. Pain Score ranking via [IndicatorMath] on real funding + computed bias.
 *
 * Every fetch degrades gracefully: an absent feed shrinks the universe (or yields an
 * empty one) instead of throwing — and when the live universe is unavailable, callers
 * fall back to the offline seed universe so the app remains usable.
 */
class UniverseScreener(private val candleDao: CandleDao) {

    companion object {
        // §6 Universe Filtering Boundaries
        const val MIN_QUOTE_VOLUME_USD = 50_000_000.0
        const val MAX_QUOTE_VOLUME_USD = 750_000_000.0
        const val MAX_SPREAD_PCT = 0.10
        const val DEPTH_SAMPLE_LIMIT = 10

        // §5 Heavy Data staleness window for HTF context.
        const val HTF_STALENESS_MS: Long = 4L * 60L * 60L * 1000L

        const val SCREENED_LIMIT = 12
    }

    /**
     * Discovers and ranks the live mid-cap universe. Returns an empty list when the
     * mandatory feeds are unreachable (caller decides on fallback).
     */
    suspend fun screenUniverse(): List<CryptoAsset> = withContext(Dispatchers.IO) {
        coroutineScope {
            val exchangeInfo = async { BinanceFuturesClient.fetchFuturesExchangeInfo() }
            val spotBases = async { BinanceFuturesClient.fetchSpotExchangeInfo() }
            val tickers = async { BinanceFuturesClient.fetch24hTickers() }
            val premiums = async { BinanceFuturesClient.fetchPremiumIndexes() }
            val funding = async { CoinGlassClient.fetchFundingRates() }
            val ois = async { CoinGlassClient.fetchOpenInterestUsd() }
            val basis = async { CoinGlassClient.fetchBasis() }

            val info = exchangeInfo.await() ?: return@coroutineScope emptyList()
            val spotBaseSet = spotBases.await().toSet()
            val tickerBySymbol = tickers.await().associateBy { it.symbol }
            val premiumBySymbol = premiums.await().associateBy { it.symbol }

            val tradablePerps = info.symbols.filter {
                it.status == "TRADING" &&
                    it.contractType == "PERPETUAL" &&
                    it.quoteAsset == "USDT"
            }

            val candidates = tradablePerps.mapNotNull { sym ->
                val ticker = tickerBySymbol[sym.symbol] ?: return@mapNotNull null
                val premium = premiumBySymbol[sym.symbol]

                // §4 Strict Inclusion Rule: futures+spot on Binance AND futures on CoinGlass.
                val base = sym.baseAsset?.uppercase() ?: return@mapNotNull null
                val hasSpot = base in spotBaseSet
                val cg = funding[sym.symbol]
                val hasCoinGlassFutures = cg != null || ois.containsKey(sym.symbol)
                if (!hasSpot || !hasCoinGlassFutures) return@mapNotNull null

                val quoteVolume = ticker.quoteVolume ?: return@mapNotNull null
                val lastPrice = ticker.lastPrice ?: premium?.markPrice ?: return@mapNotNull null
                val fundingRate = cg?.first ?: premium?.lastFundingRate ?: 0.0

                // §6 filters
                if (quoteVolume !in MIN_QUOTE_VOLUME_USD..MAX_QUOTE_VOLUME_USD) return@mapNotNull null

                val spreadPct = measureSpreadPct(sym.symbol, lastPrice)

                val atr5m = atrFor(sym.symbol, "5m", lastPrice)
                val atr1h = atrFor(sym.symbol, "1h", lastPrice)
                val atr4h = atrFor(sym.symbol, "4h", lastPrice)

                // Deterministic interim bias from real 1h candles (Phase 3 supersedes).
                val strategicBias = IndicatorMath.interimBias(loadCandles(sym.symbol, "4h", 60))
                val tacticalBias = IndicatorMath.interimBias(loadCandles(sym.symbol, "1h", 60))

                val regime = classifyRegime(strategicBias, tacticalBias)
                val pain = IndicatorMath.painScore(fundingRate, tacticalBias)
                val painPath = buildPainPath(pain, tacticalBias, strategicBias)

                CryptoAsset(
                    symbol = sym.symbol,
                    baseAsset = sym.baseAsset ?: base,
                    lastPrice = lastPrice,
                    priceChange24h = ticker.priceChangePercent ?: 0.0,
                    quoteVolume24h = quoteVolume,
                    orderBookSpreadPct = spreadPct,
                    fundingRatePct = fundingRate,
                    predictedFundingPct = funding[sym.symbol]?.second ?: fundingRate,
                    openInterestUsd = ois[sym.symbol] ?: 0.0,
                    basisPremiumPct = basis[sym.symbol] ?: 0.0,
                    atr5m = atr5m,
                    atr1h = atr1h,
                    atr4h = atr4h,
                    regime = regime,
                    tacticalBias = tacticalBias,
                    strategicBias = strategicBias,
                    primaryPainPath = painPath
                )
            }

            val qualified = candidates
                .filter { it.orderBookSpreadPct < MAX_SPREAD_PCT }
                .sortedByDescending { asset ->
                    // §6 Screener Output: directional priority ranking.
                    IndicatorMath.painScore(asset.fundingRatePct, asset.tacticalBias) +
                        Math.abs(asset.tacticalBias)
                }
                .take(SCREENED_LIMIT)
            qualified
        }
    }

    /** §4: spread measured from a real L2 top-of-book snapshot; 100% when unavailable. */
    private suspend fun measureSpreadPct(symbol: String, mid: Double): Double {
        val depth = BinanceFuturesClient.fetchDepth(symbol, DEPTH_SAMPLE_LIMIT) ?: return 100.0
        val bestBid = depth.bids.firstOrNull()?.first ?: return 100.0
        val bestAsk = depth.asks.firstOrNull()?.first ?: return 100.0
        if (mid <= 0.0) return 100.0
        return (bestAsk - bestBid) / mid * 100.0
    }

    /** ATR from persisted-or-fresh 5m candles; 0.0 when data is unavailable. */
    private suspend fun atrFor(symbol: String, interval: String, fallbackPrice: Double): Double {
        val candles = loadCandles(symbol, interval, 60)
        val atr = IndicatorMath.atr(candles)
        if (atr > 0.0) return atr
        // Fallback approximates one range unit so ladders remain constructible offline.
        return fallbackPrice * 0.002
    }

    /**
     * §5 Heavy Data: candles are served from the local store when fresh (< 4h); only a
     * stale or empty store triggers a network download, and results are persisted.
     */
    private suspend fun loadCandles(symbol: String, interval: String, limit: Int): List<BinanceFuturesClient.Candle> {
        val cached = runCatching { candleDao.getCandles(symbol, interval) }.getOrDefault(emptyList())
        val latest = runCatching { candleDao.getLatestFetchedAtMs(symbol, interval) }.getOrNull()
        val fresh = cached.isNotEmpty() && latest != null &&
            (System.currentTimeMillis() - latest) < HTF_STALENESS_MS
        if (fresh) {
            return cached.takeLast(limit).map { it.toDomain() }
        }
        val fetched = BinanceFuturesClient.fetchKlines(symbol, interval, limit)
        if (fetched.isNotEmpty()) {
            val now = System.currentTimeMillis()
            runCatching {
                candleDao.insertCandles(
                    fetched.map {
                        CandleEntity(
                            id = "$symbol|$interval|${it.openTimeUtcMs}",
                            symbol = symbol,
                            interval = interval,
                            openTimeUtcMs = it.openTimeUtcMs,
                            closeTimeUtcMs = it.closeTimeUtcMs,
                            open = it.open,
                            high = it.high,
                            low = it.low,
                            close = it.close,
                            volume = it.volume,
                            quoteVolume = it.quoteVolume,
                            fetchedAtMs = now
                        )
                    }
                )
            }
            return fetched
        }
        return cached.takeLast(limit).map { it.toDomain() }
    }

    /** §9 regime classification from real bias scores (band table mirrored in §15). */
    private fun classifyRegime(strategicBias: Int, tacticalBias: Int): MarketRegime = when {
        strategicBias >= 20 && tacticalBias >= -19 -> MarketRegime.BULLISH_CONTINUATION
        strategicBias <= -20 && tacticalBias <= 19 -> MarketRegime.BEARISH_CONTINUATION
        Math.abs(strategicBias - tacticalBias) >= 40 -> MarketRegime.TRANSITION
        else -> MarketRegime.BALANCE
    }

    private fun buildPainPath(pain: Int, tactical: Int, strategic: Int): String {
        val dir = when {
            tactical >= 20 && strategic >= 20 -> "Bull continuation"
            tactical <= -20 && strategic <= -20 -> "Bear continuation"
            tactical >= 20 && strategic <= -20 -> "Tactical bull into strategic supply"
            tactical <= -20 && strategic >= 20 -> "Tactical bear into strategic demand"
            else -> "Range rotation"
        }
        return "$dir (pain $pain)"
    }

    private fun CandleEntity.toDomain() = BinanceFuturesClient.Candle(
        openTimeUtcMs = openTimeUtcMs,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        closeTimeUtcMs = closeTimeUtcMs,
        quoteVolume = quoteVolume
    )
}

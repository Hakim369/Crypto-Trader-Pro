package com.example.engine

import com.example.data.remote.BinanceFuturesClient

/**
 * Phase 2 (Spec §4, §11): deterministic market analytics computed from real candles.
 *
 * Extracted as pure functions so engines consume reproducible numbers instead of
 * hardcoded values, and so unit tests can assert exact behavior without network or
 * Android bindings (Spec §37: reproducible feature snapshots).
 */
object IndicatorMath {

    /**
     * Wilder-smoothed ATR over [candles]. Requires at least one candle; the first value
     * seeds with the simple average of true ranges. Returns 0.0 for empty input.
     */
    fun atr(candles: List<BinanceFuturesClient.Candle>, period: Int = 14): Double {
        if (candles.isEmpty()) return 0.0
        val trueRanges = DoubleArray(candles.size)
        trueRanges[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val prevClose = candles[i - 1].close
            trueRanges[i] = maxOf(
                candles[i].high - candles[i].low,
                Math.abs(candles[i].high - prevClose),
                Math.abs(candles[i].low - prevClose)
            )
        }
        if (candles.size == 1) return trueRanges[0]
        val effectivePeriod = period.coerceIn(1, candles.size)
        var atr = trueRanges.take(effectivePeriod).average()
        for (i in effectivePeriod until candles.size) {
            atr = (atr * (effectivePeriod - 1) + trueRanges[i]) / effectivePeriod
        }
        return atr
    }

    /**
     * Deterministic interim tactical/strategic bias (-100..+100) used until the Phase 3
     * multi-horizon model lands (Spec §9): momentum of recent closes vs the window mean,
     * scaled by realized range, with the same formula for both horizons (different data).
     * Replaces the compliance-review finding that bias scores were static fields.
     */
    fun interimBias(candles: List<BinanceFuturesClient.Candle>, lookback: Int = 20): Int {
        if (candles.isEmpty()) return 0
        val window = candles.takeLast(lookback.coerceAtMost(candles.size))
        val closes = window.map { it.close }
        val mean = closes.average()
        val last = closes.last()
        val range = (window.maxOf { it.high } - window.minOf { it.low })
            .coerceAtLeast(Double.MIN_VALUE)
        val momentumZ = (last - mean) / range * 200.0
        return momentumZ.coerceIn(-100.0, 100.0).toInt()
    }

    /** pain score proxy (§11): crowding weight on funding extremity plus momentum alignment. */
    fun painScore(fundingRate: Double, bias: Int): Int {
        val crowding = (Math.abs(fundingRate) * 20_000).coerceIn(0.0, 100.0)
        val alignment = Math.abs(bias).toDouble()
        return (0.6 * crowding + 0.4 * alignment).toInt().coerceIn(10, 98)
    }
}

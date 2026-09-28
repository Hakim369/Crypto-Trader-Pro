package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BoardRole
import com.example.data.model.CampaignFamily
import com.example.data.model.CampaignState
import com.example.data.model.CryptoAsset
import com.example.data.model.EvidenceFrame
import com.example.data.model.MarketRegime
import com.example.data.model.RiskEnvelope
import com.example.data.model.VenueHealth
import com.example.engine.CampaignEngine
import com.example.engine.FeatureEngine
import com.example.engine.InvalidationAction
import com.example.engine.InvalidationEngine
import com.example.engine.PathEngine
import com.example.engine.RiskEngine
import com.example.engine.SessionSecurityManager
import com.example.engine.StructureEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches ProactiveMS`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ProactiveMS", appName)
    }

    @Test
    fun `structure engine dynamically computes levels and freshness decay`() {
        val structureEngine = StructureEngine()
        val testAsset = CryptoAsset(
            symbol = "SOLUSDT",
            baseAsset = "SOL",
            lastPrice = 150.0,
            priceChange24h = 3.5,
            quoteVolume24h = 500_000_000.0,
            orderBookSpreadPct = 0.02,
            fundingRatePct = -0.015,
            predictedFundingPct = -0.02,
            openInterestUsd = 200_000_000.0,
            basisPremiumPct = 0.05,
            atr5m = 1.0,
            atr1h = 2.5,
            atr4h = 6.0,
            regime = MarketRegime.BULLISH_CONTINUATION,
            tacticalBias = 70,
            strategicBias = 65,
            primaryPainPath = "Ascending Sweep"
        )

        val levelMap = structureEngine.buildLevelMap(testAsset)
        assertNotNull(levelMap)
        assertTrue(levelMap.zones.size >= 5)

        // Test zone freshness decay
        val zone = levelMap.zones.first()
        val decayed = structureEngine.decayZoneFreshness(zone, elapsedMinutes = 60)
        assertTrue(decayed.freshnessScore <= zone.freshnessScore)
    }

    @Test
    fun `campaign engine creates symmetric campaigns and assigns asymmetric roles`() {
        val structureEngine = StructureEngine()
        val featureEngine = FeatureEngine()
        val pathEngine = PathEngine(featureEngine)
        val campaignEngine = CampaignEngine()

        val bullAsset = CryptoAsset(
            symbol = "SOLUSDT",
            baseAsset = "SOL",
            lastPrice = 150.0,
            priceChange24h = 4.0,
            quoteVolume24h = 300_000_000.0,
            orderBookSpreadPct = 0.02,
            fundingRatePct = -0.015,
            predictedFundingPct = -0.02,
            openInterestUsd = 150_000_000.0,
            basisPremiumPct = 0.05,
            atr5m = 1.0,
            atr1h = 2.5,
            atr4h = 6.0,
            regime = MarketRegime.BULLISH_CONTINUATION,
            tacticalBias = 75,
            strategicBias = 80,
            primaryPainPath = "Bullish continuation sweep"
        )

        val map = structureEngine.buildLevelMap(bullAsset)
        val paths = pathEngine.rankCandidatePaths(bullAsset, map)
        val campaigns = campaignEngine.planCampaigns(bullAsset, map, paths)

        // Verify all 6 campaign families are accounted for
        assertEquals(6, campaigns.size)

        val supportLong = campaigns.find { it.family == CampaignFamily.SUPPORT_LONG }
        assertNotNull(supportLong)
        assertEquals(BoardRole.PRIMARY, supportLong!!.role)

        // Counter-trend breakdown short should be disabled or defensive in bullish regime
        val breakdownShort = campaigns.find { it.family == CampaignFamily.BREAKDOWN_SHORT }
        assertNotNull(breakdownShort)
        assertEquals(BoardRole.DISABLED, breakdownShort!!.role)
    }

    @Test
    fun `invalidation engine cuts bad trades early on contradictory evidence`() {
        val invalidationEngine = InvalidationEngine()
        val structureEngine = StructureEngine()

        val asset = CryptoAsset(
            symbol = "SOLUSDT",
            baseAsset = "SOL",
            lastPrice = 145.0, // price fell below support
            priceChange24h = -3.5,
            quoteVolume24h = 200_000_000.0,
            orderBookSpreadPct = 0.09, // spread blown out
            fundingRatePct = 0.03, // funding flipped positive
            predictedFundingPct = 0.04,
            openInterestUsd = 120_000_000.0,
            basisPremiumPct = -0.05,
            atr5m = 1.0,
            atr1h = 2.5,
            atr4h = 6.0,
            regime = MarketRegime.BEARISH_CONTINUATION,
            tacticalBias = -70,
            strategicBias = -65,
            primaryPainPath = "Breakdown"
        )

        val map = structureEngine.buildLevelMap(asset)

        val dummyCampaign = com.example.data.model.Campaign(
            id = "TEST_LONG",
            asset = "SOLUSDT",
            family = CampaignFamily.SUPPORT_LONG,
            regime = MarketRegime.BULLISH_CONTINUATION,
            role = BoardRole.PRIMARY,
            priorityScore = 80.0,
            sizeBudgetUsd = 500.0,
            sizeMultiplier = 1.0,
            entryLadder = listOf(
                com.example.data.model.LadderSlice(1, 150.0, 1.0, 150.0, isFilled = true, isResting = false),
                com.example.data.model.LadderSlice(2, 149.0, 1.0, 149.0, isFilled = false, isResting = true)
            ),
            stopLogic = com.example.data.model.StopLogic(softThreshold = 45, hardThreshold = 70, hardStopPrice = 146.0),
            targets = emptyList(),
            invalidationScore = 10,
            topReasons = emptyList(),
            status = CampaignState.ACTIVE
        )

        // Contradictory evidence: price falling with expanding OI and heavy taker sells
        val adverseEvidence = EvidenceFrame(
            timestamp = System.currentTimeMillis(),
            asset = "SOLUSDT",
            oiDelta1hPct = 2.5,
            fundingRateChangePct = 0.005,
            basisDeltaBps = -10.0,
            takerBuyRatio = 0.15, // 85% taker sell pressure
            acceptanceQuality = 0.1,
            rejectionQuality = 0.1,
            trapProbabilityPct = 85,
            recentImpulseQuality = "Heavy Taker Sell Pressure"
        )

        val result = invalidationEngine.evaluateCampaign(dummyCampaign, asset, adverseEvidence, map)

        // Invalidation score must rise and trigger hard flatten
        assertTrue(result.updatedCampaign.invalidationScore >= 70)
        assertEquals(InvalidationAction.HARD_FLATTEN_COOLDOWN, result.triggerAction)
        assertEquals(CampaignState.CANCELLED, result.updatedCampaign.status)
    }

    @Test
    fun `risk engine detects manual override guardrail breaches`() {
        val riskEngine = RiskEngine()

        val warnings = riskEngine.checkManualOverrideGuardrails(
            engineCalculatedStop = 145.0,
            userStopPrice = 135.0, // far below 3% threshold
            isLong = true,
            engineCalculatedSize = 500.0,
            userSizeBudget = 1500.0 // >150% size budget
        )

        assertEquals(2, warnings.size)
        assertTrue(warnings.any { it.title.contains("Stop Risk") })
        assertTrue(warnings.any { it.title.contains("Position Sizing") })
    }

    @Test
    fun `session security manager performs suspension wipe of API keys in RAM`() {
        val manager = SessionSecurityManager()

        manager.startSession("BINANCE_KEY_12345", "BINANCE_SECRET_ABCDE")
        assertTrue(manager.hasValidActiveSession())

        // Simulate suspension wipe
        manager.terminateSessionAndWipeRam()
        assertFalse(manager.hasValidActiveSession())
    }
}

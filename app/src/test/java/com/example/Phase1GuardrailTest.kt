package com.example

import com.example.data.model.Campaign
import com.example.data.model.CampaignFamily
import com.example.data.model.CampaignState
import com.example.data.model.LadderSlice
import com.example.data.model.OrderType
import com.example.data.model.StopLogic
import com.example.engine.HardwareDiagnosticsProvider
import com.example.engine.OrphanInterceptDecision
import com.example.engine.OrphanInterceptPolicy
import com.example.engine.SessionSecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 verification (Spec §2 + §3): lifecycle-driven orphan intercept, suspension
 * wipe hardening, and feed-staleness propagation from diagnostics.
 */
class OrphanInterceptPolicyTest {

    private fun campaign(
        status: CampaignState,
        restingSlices: Int = 0,
        filledSlices: Int = 0
    ): Campaign = Campaign(
        id = "T_${status.name}",
        asset = "SOLUSDT",
        family = CampaignFamily.SUPPORT_LONG,
        regime = com.example.data.model.MarketRegime.BULLISH_CONTINUATION,
        role = com.example.data.model.BoardRole.PRIMARY,
        priorityScore = 80.0,
        sizeBudgetUsd = 500.0,
        sizeMultiplier = 1.0,
        entryLadder = List(restingSlices) { i ->
            LadderSlice(sliceIndex = i + 1, price = 150.0, qty = 1.0, notionalUsd = 150.0, isFilled = false, isResting = true)
        } + List(filledSlices) { i ->
            LadderSlice(
                sliceIndex = restingSlices + i + 1,
                price = 150.0,
                qty = 1.0,
                notionalUsd = 150.0,
                isFilled = true,
                isResting = false
            )
        },
        stopLogic = StopLogic(softThreshold = 45, hardThreshold = 70, hardStopPrice = 146.0),
        targets = emptyList(),
        invalidationScore = 10,
        topReasons = emptyList(),
        status = status
    )

    @Test
    fun `campaigns in live states count as resting or live orders`() {
        for (state in listOf(
            CampaignState.STAGED,
            CampaignState.PARTIALLY_FILLED,
            CampaignState.ACTIVE,
            CampaignState.REDUCED
        )) {
            assertTrue(
                "Expected $state to count as live",
                OrphanInterceptPolicy.hasRestingOrLiveOrders(listOf(campaign(state)))
            )
        }
    }

    @Test
    fun `completed campaign with a still-resting slice counts as live orders`() {
        val c = campaign(CampaignState.COMPLETED, restingSlices = 1)
        assertTrue(OrphanInterceptPolicy.hasRestingOrLiveOrders(listOf(c)))
    }

    @Test
    fun `terminal campaign with nothing resting does not count as live orders`() {
        val c = campaign(CampaignState.CANCELLED, filledSlices = 1)
        assertFalse(OrphanInterceptPolicy.hasRestingOrLiveOrders(listOf(c)))
    }

    @Test
    fun `no session never triggers intercept`() {
        assertEquals(
            OrphanInterceptDecision.NO_ACTION,
            OrphanInterceptPolicy.decide(hasActiveSession = false, hasRestingOrders = true)
        )
    }

    @Test
    fun `session with resting orders triggers full intercept`() {
        assertEquals(
            OrphanInterceptDecision.SHOW_INTERCEPT,
            OrphanInterceptPolicy.decide(hasActiveSession = true, hasRestingOrders = true)
        )
    }

    @Test
    fun `session without resting orders wipes keys without intercept`() {
        assertEquals(
            OrphanInterceptDecision.WIPE_ONLY,
            OrphanInterceptPolicy.decide(hasActiveSession = true, hasRestingOrders = false)
        )
    }
}

class SessionSecurityHardeningTest {

    @Test
    fun `suspension wipe deactivates session and repeated wipes stay safe`() {
        val manager = SessionSecurityManager()
        manager.startSession("KEY-123456", "SECRET-987654321")
        assertTrue(manager.hasValidActiveSession())

        manager.terminateSessionAndWipeRam()
        assertFalse(manager.hasValidActiveSession())

        // Idempotent: wiping an already-wiped session must not throw or resurrect state.
        manager.terminateSessionAndWipeRam()
        assertFalse(manager.hasValidActiveSession())
    }

    @Test
    fun `starting a new session replaces prior credentials safely`() {
        val manager = SessionSecurityManager()
        manager.startSession("FIRST_KEY", "FIRST_SECRET")
        manager.startSession("SECOND_KEY", "SECOND_SECRET")
        assertTrue(manager.hasValidActiveSession())
        manager.terminateSessionAndWipeRam()
        assertFalse(manager.hasValidActiveSession())
    }

    @Test
    fun `unreachable latency probe marks mandatory feed stale`() {
        val manager = SessionSecurityManager()
        manager.updateHardwareDiagnostics(
            pingBinance = HardwareDiagnosticsProvider.UNREACHABLE_LATENCY_MS,
            pingCoinGlass = 42L,
            isLowPower = false,
            isThrottled = false,
            availableRamMb = 4000
        )
        val health = manager.venueHealth.value
        assertTrue(health.isFeedStale)
        assertFalse(health.isHealthy)
    }

    @Test
    fun `reachable probes keep feed fresh but guardrails still apply`() {
        val manager = SessionSecurityManager()
        manager.updateHardwareDiagnostics(
            pingBinance = 300L, // above the 120ms spike threshold
            pingCoinGlass = 42L,
            isLowPower = false,
            isThrottled = false,
            availableRamMb = 4000
        )
        val health = manager.venueHealth.value
        assertFalse(health.isFeedStale)
        assertTrue(health.isLatencySpike)
        assertFalse(health.isHealthy)
    }

    @Test
    fun `healthy sample yields healthy venue`() {
        val manager = SessionSecurityManager()
        manager.updateHardwareDiagnostics(
            pingBinance = 30L,
            pingCoinGlass = 40L,
            isLowPower = false,
            isThrottled = false,
            availableRamMb = 4000
        )
        assertTrue(manager.venueHealth.value.isHealthy)
    }

    @Test
    fun `blank credentials never start a session`() {
        val manager = SessionSecurityManager()
        manager.startSession("  ", "SECRET")
        manager.startSession("KEY", "")
        assertFalse(manager.hasValidActiveSession())
    }
}

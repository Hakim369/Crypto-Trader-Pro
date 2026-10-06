package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.AssetEntity
import com.example.data.local.CampaignEntity
import com.example.data.local.DatabaseRepository
import com.example.data.model.AuditLogAction
import com.example.data.model.AuditLogEntry
import com.example.data.model.BacktestMetrics
import com.example.data.model.BoardRole
import com.example.data.model.Campaign
import com.example.data.model.CampaignState
import com.example.data.model.CandidatePath
import com.example.data.model.CoinCalibration
import com.example.data.model.CryptoAsset
import com.example.data.model.EvidenceFrame
import com.example.data.model.ExecutionMode
import com.example.data.model.LevelMap
import com.example.data.model.OrderType
import com.example.data.model.RiskEnvelope
import com.example.data.model.VenueHealth
import com.example.data.remote.BinanceSignedClient
import com.example.data.remote.BinanceUserDataClient
import com.example.data.remote.CoinGlassClient
import com.example.engine.CampaignEngine
import com.example.engine.ExecutionEngine
import com.example.engine.ExecutionGuardrails
import com.example.engine.FeatureEngine
import com.example.engine.HardwareDiagnosticsProvider
import com.example.engine.InvalidationAction
import com.example.engine.InvalidationEngine
import com.example.engine.LiveMarketDataProvider
import com.example.engine.MarketDataRepository
import com.example.engine.OrphanInterceptDecision
import com.example.engine.OrphanInterceptPolicy
import com.example.engine.OrderBookSnapshot
import com.example.engine.OverrideWarning
import com.example.engine.PathEngine
import com.example.engine.ReplayEngine
import com.example.engine.ReplayEngine.ReplayResult
import com.example.engine.RiskEngine
import com.example.engine.SessionSecurityManager
import com.example.engine.SimulationHarness
import com.example.engine.SimulationScenario
import com.example.engine.UniverseScreener
import com.example.engine.StructureEngine
import com.example.engine.TakerTrade
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Arrays

data class MainUiState(
    val assets: List<CryptoAsset> = emptyList(),
    val selectedAsset: CryptoAsset? = null,
    val levelMap: LevelMap? = null,
    /** §10 live per-coin calibration for the selected asset (null until candles load). */
    val calibration: CoinCalibration? = null,
    val paths: List<CandidatePath> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    val selectedCampaign: Campaign? = null,
    val liveEvidence: EvidenceFrame? = null,
    val orderBook: OrderBookSnapshot? = null,
    val takerTrades: List<TakerTrade> = emptyList(),
    val riskEnvelope: RiskEnvelope = RiskEnvelope(),
    val venueHealth: VenueHealth = VenueHealth(),
    val executionMode: ExecutionMode = ExecutionMode.PAPER,
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val backtestMetrics: BacktestMetrics = BacktestMetrics(),
    /** Phase 5 (§37): result of the latest historical replay, null until one runs. */
    val lastReplay: ReplayResult? = null,
    /** True while the §37 replay is computing. */
    val isReplayRunning: Boolean = false,
    val selectedScenario: SimulationScenario? = null,
    val isSessionActive: Boolean = false,
    val isOrphanedOrderWarningOpen: Boolean = false,
    val isOverrideDialogOpen: Boolean = false,
    val overrideWarnings: List<OverrideWarning> = emptyList(),
    /** True while a pre-flight hardware/network diagnostics sample is being taken (Spec §2). */
    val isDiagnosticsRunning: Boolean = false,
    /** True once a real diagnostics sample has populated venueHealth (vs. built-in defaults). */
    val hasLiveDiagnostics: Boolean = false,
    /** True when the current universe came from live exchange discovery (vs offline seed). */
    val isLiveUniverse: Boolean = false
 )

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = DatabaseRepository(db)

    private val marketDataRepo = MarketDataRepository()
    private val universeScreener = UniverseScreener(db.candleDao())
    private val liveProvider = LiveMarketDataProvider(universeScreener)
    private val featureEngine = FeatureEngine()
    private val structureEngine = StructureEngine()
    private val pathEngine = PathEngine(featureEngine)
    private val campaignEngine = CampaignEngine()
    private val invalidationEngine = InvalidationEngine()
    private val riskEngine = RiskEngine()
    private val sessionManager = SessionSecurityManager()
    private val simulationHarness = SimulationHarness()
    private val replayEngine = ReplayEngine()

    /** Phase 4 (§36): execution gate + live order routing state. */
    private val executionEngine = ExecutionEngine(riskEngine)
    private val consecutiveRejectsBySymbol = mutableMapOf<String, Int>()
    private var userDataJob: Job? = null
    private var lastPositionCheckMs = 0L
    private val routedClientIds = mutableSetOf<String>()

    /** Phase 1 (Spec §2): real OS diagnostics; no-arg constructor stays test-friendly. */
    private val diagnosticsProvider: HardwareDiagnosticsProvider? =
        runCatching { HardwareDiagnosticsProvider(application) }.getOrNull()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var activeDeltaJob: Job? = null
    private var orderBookJob: Job? = null
    private var takerFlowJob: Job? = null

    init {
        // Optional CoinGlass key from the secrets-managed environment (never persisted).
        runCatching {
            CoinGlassClient.apiKey = BuildConfig.COINGLASS_API_KEY
        }
        loadInitialData()
        observeAuditLogs()
        runPreFlightDiagnostics()
        startDormantUniversePolling()
    }

    private fun loadInitialData() {
        // §5 Heavy Data: pull the real (or cached) universe in the background, but paint
        // the UI immediately from the offline seed so first frame is never blank.
        val initialAssets = marketDataRepo.getInitialUniverse()
        val defaultAsset = initialAssets.first()

        viewModelScope.launch {
            val liveUniverse = runCatching { liveProvider.loadUniverseOnce() }
                .getOrDefault(initialAssets)
            val chosen = liveUniverse.firstOrNull() ?: defaultAsset
            applyUniverseAndRecompute(liveUniverse, chosen)
        }

        val map = structureEngine.buildLevelMap(defaultAsset)
        val candidatePaths = pathEngine.rankCandidatePaths(defaultAsset, map)
        val initialCampaigns = campaignEngine.planCampaigns(defaultAsset, map, candidatePaths)
        val initialEvidence = featureEngine.buildEvidenceFrame(defaultAsset)

        _uiState.value = _uiState.value.copy(
            assets = initialAssets,
            selectedAsset = defaultAsset,
            levelMap = map,
            paths = candidatePaths,
            campaigns = initialCampaigns,
            selectedCampaign = initialCampaigns.firstOrNull(),
            liveEvidence = initialEvidence,
            selectedScenario = simulationHarness.availableScenarios.first()
        )

        // Sync with local Room persistence
        viewModelScope.launch {
            val entities = initialAssets.map {
                AssetEntity(
                    symbol = it.symbol,
                    baseAsset = it.baseAsset,
                    lastPrice = it.lastPrice,
                    priceChange24h = it.priceChange24h,
                    quoteVolume24h = it.quoteVolume24h,
                    orderBookSpreadPct = it.orderBookSpreadPct,
                    fundingRatePct = it.fundingRatePct,
                    openInterestUsd = it.openInterestUsd,
                    basisPremiumPct = it.basisPremiumPct,
                    atr5m = it.atr5m,
                    regime = it.regime.name,
                    tacticalBias = it.tacticalBias,
                    strategicBias = it.strategicBias,
                    primaryPainPath = it.primaryPainPath
                )
            }
            repository.saveAssets(entities)

            // Log startup
            repository.logAction(
                asset = defaultAsset.symbol,
                campaignId = "SYS_BOOT",
                action = AuditLogAction.CAMPAIGN_STAGED,
                reasonCode = "SYSTEM_INITIALIZED",
                message = "Proactive Market Structure Engine initialized in ${_uiState.value.executionMode.displayName} mode"
            )
        }

        startActiveDeltaMode(defaultAsset)
    }

    /** Applies a freshly-screened universe and recomputes analytics for the chosen symbol. */
    private suspend fun applyUniverseAndRecompute(universe: List<CryptoAsset>, chosen: CryptoAsset) {
        // §8: build the level map from real swing structure when candle history exists;
        // fall back to the ATR-offset synthesis only when no candles are available.
        val timeframes = runCatching { universeScreener.loadTimeframes(chosen.symbol) }.getOrNull()
        val map = if (timeframes != null && timeframes.candles1h.isNotEmpty()) {
            structureEngine.buildLevelMap(
                chosen,
                timeframes.candles4h,
                timeframes.candles1h,
                timeframes.candles5m
            )
        } else {
            structureEngine.buildLevelMap(chosen)
        }
        val candidatePaths = pathEngine.rankCandidatePaths(chosen, map)
        // §10: live per-coin calibration from real 1h/5m history.
        val calibration = runCatching { universeScreener.loadCalibration(chosen) }.getOrNull()
        val planned = campaignEngine.planCampaigns(chosen, map, candidatePaths, calibration = calibration)
        val evidence = liveProvider.buildEvidenceFrame(chosen)
        // Phase 4: preserve reconciled fills, then run the staging gate (§22 step 7, §36).
        val merged = mergeFillState(planned, _uiState.value.campaigns)
        val gatedCampaigns = gateAndLog(merged)
        _uiState.value = _uiState.value.copy(
            assets = universe,
            selectedAsset = chosen,
            levelMap = map,
            paths = candidatePaths,
            campaigns = gatedCampaigns,
            selectedCampaign = gatedCampaigns.firstOrNull(),
            liveEvidence = evidence,
            calibration = calibration,
            isLiveUniverse = universe.any { it.quoteVolume24h > 0 && it.orderBookSpreadPct < 100.0 }
        )
        routeLiveBoards(gatedCampaigns)
        startUserDataStreamIfNeeded()
        startActiveDeltaMode(chosen)
    }

    private fun observeAuditLogs() {
        viewModelScope.launch {
            repository.latestAuditLogs.collect { logs ->
                _uiState.value = _uiState.value.copy(auditLogs = logs)
            }
        }
        viewModelScope.launch {
            sessionManager.venueHealth.collect { health ->
                _uiState.value = _uiState.value.copy(venueHealth = health)
            }
        }
        viewModelScope.launch {
            sessionManager.isSessionActive.collect { active ->
                _uiState.value = _uiState.value.copy(isSessionActive = active)
            }
        }
    }

    /**
     * Phase 1 (Spec §2): sample real device state and mandatory-feed latency, then feed
     * the authoritative thresholds in SessionSecurityManager. Safe no-op if the provider
     * is unavailable (e.g. unit-test environments without Android framework bindings).
     */
    fun runPreFlightDiagnostics() {
        val provider = diagnosticsProvider ?: return
        if (_uiState.value.isDiagnosticsRunning) return

        _uiState.value = _uiState.value.copy(isDiagnosticsRunning = true)
        viewModelScope.launch {
            try {
                val sample = provider.sample()
                sessionManager.updateHardwareDiagnostics(
                    pingBinance = sample.binancePingMs,
                    pingCoinGlass = sample.coinglassPingMs,
                    isLowPower = sample.isLowPowerMode,
                    isThrottled = sample.isThermallyThrottled,
                    availableRamMb = sample.availableRamMb
                )
                _uiState.value = _uiState.value.copy(
                    isDiagnosticsRunning = false,
                    hasLiveDiagnostics = true
                )
                repository.logAction(
                    asset = "PORTFOLIO",
                    campaignId = "SYS_DIAGNOSTICS",
                    action = AuditLogAction.CAMPAIGN_STAGED,
                    reasonCode = if (sessionManager.venueHealth.value.isHealthy)
                        "PREFLIGHT_PASSED" else "PREFLIGHT_GUARDRAIL_TRIPPED",
                    message = "Pre-flight diagnostics: Binance ${sample.binancePingMs}ms, CoinGlass ${sample.coinglassPingMs}ms, " +
                        "RAM ${sample.availableRamMb}MB, battery ${sample.batteryLevelPct}%"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDiagnosticsRunning = false)
                repository.logAction(
                    asset = "PORTFOLIO",
                    campaignId = "SYS_DIAGNOSTICS",
                    action = AuditLogAction.CAMPAIGN_STAGED,
                    reasonCode = "PREFLIGHT_ERROR",
                    message = "Pre-flight diagnostics failed: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun selectAsset(symbol: String) {
        val asset = _uiState.value.assets.find { it.symbol == symbol } ?: return
        val map = structureEngine.buildLevelMap(asset)
        val candidatePaths = pathEngine.rankCandidatePaths(asset, map)
        val planned = campaignEngine.planCampaigns(asset, map, candidatePaths)
        val evidence = liveProvider.buildEvidenceFrame(asset)
        val gatedPlanned = gateAndLog(mergeFillState(planned, _uiState.value.campaigns))

        _uiState.value = _uiState.value.copy(
            selectedAsset = asset,
            levelMap = map,
            paths = candidatePaths,
            campaigns = gatedPlanned,
            selectedCampaign = gatedPlanned.firstOrNull(),
            liveEvidence = evidence
        )

        startActiveDeltaMode(asset)

        // Phase 3 (§8/§10): upgrade to the candle-driven level map + coin calibration
        // once loaded; the synchronous paint above keeps first response instant.
        viewModelScope.launch {
            if (_uiState.value.selectedAsset?.symbol == asset.symbol) {
                applyUniverseAndRecompute(_uiState.value.assets, asset)
            }
        }

        viewModelScope.launch {
            repository.logAction(
                asset = asset.symbol,
                campaignId = "ACTIVE_SWITCH",
                action = AuditLogAction.CAMPAIGN_STAGED,
                reasonCode = "SYMBOL_SELECTED",
                message = "Active delta stream routed to ${asset.symbol} (Regime: ${asset.regime.label})"
            )
        }
    }

    /** §5 Dormant Polling: refresh the screener universe on a light REST cadence. */
    private fun startDormantUniversePolling() {
        dormantPollingJob?.cancel()
        dormantPollingJob = viewModelScope.launch {
            liveProvider.dormantUniversePolling().collect { universe ->
                val currentSymbol = _uiState.value.selectedAsset?.symbol
                val chosen = universe.find { it.symbol == currentSymbol } ?: universe.firstOrNull()
                if (chosen != null) {
                    applyUniverseAndRecompute(universe, chosen)
                }
            }
        }
    }

    private var dormantPollingJob: Job? = null

    fun selectCampaign(campaign: Campaign) {
        _uiState.value = _uiState.value.copy(selectedCampaign = campaign)
    }

    fun setExecutionMode(mode: ExecutionMode) {
        _uiState.value = _uiState.value.copy(executionMode = mode)
        viewModelScope.launch {
            repository.logAction(
                asset = _uiState.value.selectedAsset?.symbol ?: "ALL",
                campaignId = "MODE_SWITCH",
                action = AuditLogAction.CAMPAIGN_STAGED,
                reasonCode = "MODE_CHANGED",
                message = "Operational mode changed to ${mode.displayName}"
            )
            // §36: re-gate boards under the new mode and adjust live plumbing.
            val regated = gateAndLog(_uiState.value.campaigns)
            _uiState.value = _uiState.value.copy(campaigns = regated)
            if (mode == ExecutionMode.CAPPED_LIVE || mode == ExecutionMode.SCALED_LIVE) {
                routeLiveBoards(regated)
                startUserDataStreamIfNeeded()
            } else {
                userDataJob?.cancel()
                userDataJob = null
            }
        }
    }

    fun toggleKillSwitch() {
        val current = _uiState.value.riskEnvelope.isKillSwitchEngaged
        val updated = !current
        _uiState.value = _uiState.value.copy(
            riskEnvelope = _uiState.value.riskEnvelope.copy(isKillSwitchEngaged = updated),
            campaigns = if (updated) {
                // Flatten and suppress all active campaigns
                _uiState.value.campaigns.map {
                    it.copy(status = CampaignState.CANCELLED)
                }
            } else _uiState.value.campaigns
        )
        if (updated) {
            // §22/§27: engagement is not just local — pull every venue order too.
            viewModelScope.launch {
                val creds = sessionManager.copyCredentials()
                if (creds != null) {
                    val symbol = _uiState.value.selectedAsset?.symbol
                    if (!symbol.isNullOrEmpty()) {
                        BinanceSignedClient.cancelAllOpenOrders(symbol, creds.first, creds.second)
                    }
                    Arrays.fill(creds.first, '\u0000')
                    Arrays.fill(creds.second, '\u0000')
                }
                userDataJob?.cancel()
                userDataJob = null
                routedClientIds.clear()
            }
        }

        viewModelScope.launch {
            repository.logAction(
                asset = "PORTFOLIO",
                campaignId = "GLOBAL_KILL",
                action = AuditLogAction.EMERGENCY_KILL,
                reasonCode = if (updated) "KILL_SWITCH_ENGAGED" else "KILL_SWITCH_RESET",
                message = if (updated) "EMERGENCY KILL-SWITCH ENGAGED. All resting orders cancelled & staging halted."
                else "Kill-Switch disengaged. Normal supervisor rules restored."
            )
        }
    }

    fun triggerSoftCancel(campaignId: String) {
        val currentCampaigns = _uiState.value.campaigns.toMutableList()
        val index = currentCampaigns.indexOfFirst { it.id == campaignId }
        if (index != -1) {
            val c = currentCampaigns[index]
            val updated = c.copy(
                status = CampaignState.CANCELLED,
                entryLadder = c.entryLadder.map { it.copy(isResting = false) },
                invalidationScore = maxOf(48, c.invalidationScore)
            )
            currentCampaigns[index] = updated
            _uiState.value = _uiState.value.copy(
                campaigns = currentCampaigns,
                selectedCampaign = if (_uiState.value.selectedCampaign?.id == campaignId) updated else _uiState.value.selectedCampaign
            )

            viewModelScope.launch {
                repository.logAction(
                    asset = c.asset,
                    campaignId = c.id,
                    action = AuditLogAction.SOFT_CANCEL,
                    reasonCode = "OPERATOR_SOFT_CANCEL",
                    message = "Unfilled resting slices cancelled for ${c.family.displayName} (Soft threshold hit)"
                )
            }
        }
    }

    fun triggerHardFlatten(campaignId: String) {
        val currentCampaigns = _uiState.value.campaigns.toMutableList()
        val index = currentCampaigns.indexOfFirst { it.id == campaignId }
        if (index != -1) {
            val c = currentCampaigns[index]
            val updated = c.copy(
                status = CampaignState.CANCELLED,
                entryLadder = c.entryLadder.map { it.copy(isResting = false, isFilled = false) },
                invalidationScore = 85,
                cooldownUntilTs = System.currentTimeMillis() + (15 * 60 * 1000)
            )
            currentCampaigns[index] = updated
            _uiState.value = _uiState.value.copy(
                campaigns = currentCampaigns,
                selectedCampaign = if (_uiState.value.selectedCampaign?.id == campaignId) updated else _uiState.value.selectedCampaign
            )

            viewModelScope.launch {
                repository.logAction(
                    asset = c.asset,
                    campaignId = c.id,
                    action = AuditLogAction.HARD_FLATTEN,
                    reasonCode = "OPERATOR_HARD_FLATTEN",
                    message = "Campaign ${c.family.displayName} flattened immediately. Cooldown lockout armed (15m)."
                )
            }
        }
    }

    fun openManualOverrideDialog(campaign: Campaign) {
        _uiState.value = _uiState.value.copy(
            selectedCampaign = campaign,
            isOverrideDialogOpen = true,
            overrideWarnings = emptyList()
        )
    }

    fun closeManualOverrideDialog() {
        _uiState.value = _uiState.value.copy(
            isOverrideDialogOpen = false,
            overrideWarnings = emptyList()
        )
    }

    fun applyManualOverride(campaignId: String, newStopPrice: Double, newSizeBudget: Double) {
        val c = _uiState.value.campaigns.find { it.id == campaignId } ?: return

        // Evaluate guardrails
        val warnings = riskEngine.checkManualOverrideGuardrails(
            engineCalculatedStop = c.stopLogic.hardStopPrice,
            userStopPrice = newStopPrice,
            isLong = c.isLong,
            engineCalculatedSize = c.sizeBudgetUsd,
            userSizeBudget = newSizeBudget
        )

        val updated = c.copy(
            sizeBudgetUsd = newSizeBudget,
            stopLogic = c.stopLogic.copy(hardStopPrice = newStopPrice)
        )

        val updatedCampaigns = _uiState.value.campaigns.map { if (it.id == campaignId) updated else it }

        _uiState.value = _uiState.value.copy(
            campaigns = updatedCampaigns,
            selectedCampaign = updated,
            overrideWarnings = warnings,
            isOverrideDialogOpen = warnings.isNotEmpty() // keep open if persistent warnings need displaying
        )

        viewModelScope.launch {
            repository.logAction(
                asset = c.asset,
                campaignId = c.id,
                action = AuditLogAction.MANUAL_OVERRIDE,
                reasonCode = if (warnings.isNotEmpty()) "GUARDRAIL_OVERRIDE_WARNED" else "OVERRIDE_ACCEPTED",
                message = "Parameters manually overridden for ${c.family.displayName}: Stop=$newStopPrice, Size=$$newSizeBudget. Warnings: ${warnings.size}"
            )
        }
    }

    fun openOrphanedOrderWarning() {
        _uiState.value = _uiState.value.copy(isOrphanedOrderWarningOpen = true)
    }

    fun dismissOrphanedOrderWarning() {
        _uiState.value = _uiState.value.copy(isOrphanedOrderWarningOpen = false)
    }

    fun acknowledgeOrphanedOrderWarning() {
        _uiState.value = _uiState.value.copy(isOrphanedOrderWarningOpen = false)
        // Suspension wipe: immediately wipe API keys from RAM
        sessionManager.terminateSessionAndWipeRam()
        viewModelScope.launch {
            repository.logAction(
                asset = _uiState.value.selectedAsset?.symbol ?: "PORTFOLIO",
                campaignId = "SYS_SECURITY",
                action = AuditLogAction.ORPHANED_ALERT,
                reasonCode = "SUSPENSION_WIPE",
                message = "App lifecycle suspended. API keys securely wiped from RAM per Zero-Persistence mandate."
            )
        }
    }

    /**
     * Phase 1 (Spec §3 Orphaned Order OS Intercept): invoked by MainActivity's lifecycle
     * observer when the app is leaving the foreground with live API credentials.
     *
     * Decision logic is delegated to [OrphanInterceptPolicy] (pure, unit-tested):
     *  - No active session → nothing to orphan; no action.
     *  - Session active but no resting orders → wipe keys immediately (§3 suspension policy).
     *  - Session active with resting orders → full-screen intercept is mandatory.
     */
    fun onAppBackgrounded() {
        when (
            OrphanInterceptPolicy.decide(
                hasActiveSession = sessionManager.hasValidActiveSession(),
                hasRestingOrders = OrphanInterceptPolicy.hasRestingOrLiveOrders(_uiState.value.campaigns)
            )
        ) {
            OrphanInterceptDecision.SHOW_INTERCEPT -> openOrphanedOrderWarning()
            OrphanInterceptDecision.WIPE_ONLY -> {
                sessionManager.terminateSessionAndWipeRam()
                viewModelScope.launch {
                    repository.logAction(
                        asset = "PORTFOLIO",
                        campaignId = "SYS_SECURITY",
                        action = AuditLogAction.ORPHANED_ALERT,
                        reasonCode = "SUSPENSION_WIPE",
                        message = "App backgrounded without resting orders. Session keys wiped from RAM."
                    )
                }
            }
            OrphanInterceptDecision.NO_ACTION -> Unit
        }
    }

    fun startApiSession(key: String, secret: String) {
        sessionManager.startSession(key, secret)
        viewModelScope.launch {
            repository.logAction(
                asset = "PORTFOLIO",
                campaignId = "AUTH_SESSION",
                action = AuditLogAction.CAMPAIGN_STAGED,
                reasonCode = "SESSION_STARTED",
                message = "Encrypted in-memory trade session unlocked. Zero disk persistence confirmed."
            )
        }
    }

    fun terminateApiSession() {
        sessionManager.terminateSessionAndWipeRam()
        viewModelScope.launch {
            repository.logAction(
                asset = "PORTFOLIO",
                campaignId = "AUTH_SESSION",
                action = AuditLogAction.CAMPAIGN_STAGED,
                reasonCode = "SESSION_TERMINATED",
                message = "API credentials expunged from memory."
            )
        }
    }

    fun runSimulationScenario(scenario: SimulationScenario) {
        val metrics = simulationHarness.runScenarioSimulation(scenario.id)
        _uiState.value = _uiState.value.copy(
            selectedScenario = scenario,
            backtestMetrics = metrics
        )

        viewModelScope.launch {
            repository.logAction(
                asset = _uiState.value.selectedAsset?.symbol ?: "SIMULATION",
                campaignId = scenario.id,
                action = AuditLogAction.CAMPAIGN_STAGED,
                reasonCode = "SCENARIO_EXECUTED",
                message = "Backtest scenario executed: ${scenario.name}. Captured move: ${metrics.capturedMoveBeforeConfirmationPct}%"
            )
        }
    }

    private fun startActiveDeltaMode(asset: CryptoAsset) {
        activeDeltaJob?.cancel()
        orderBookJob?.cancel()
        takerFlowJob?.cancel()

        // 1. Live price and micro-delta updates (real WS with simulated fallback)
        activeDeltaJob = viewModelScope.launch {
            liveProvider.activeDeltaStream(asset).collect { updatedAsset ->
                val previousCampaigns = _uiState.value.campaigns
                val currentLevelMap = _uiState.value.levelMap ?: structureEngine.buildLevelMap(updatedAsset)
                val evidence = liveProvider.buildEvidenceFrame(updatedAsset)

                // Run live invalidation evaluations across all staged/active campaigns
                val updatedCampaigns = _uiState.value.campaigns.map { campaign ->
                    val result = invalidationEngine.evaluateCampaign(
                        campaign,
                        updatedAsset,
                        evidence,
                        currentLevelMap
                    )
                    if (result.triggerAction != InvalidationAction.NO_ACTION) {
                        repository.logAction(
                            asset = updatedAsset.symbol,
                            campaignId = campaign.id,
                            action = if (result.triggerAction == InvalidationAction.HARD_FLATTEN_COOLDOWN)
                                AuditLogAction.HARD_FLATTEN else AuditLogAction.SOFT_CANCEL,
                            reasonCode = result.triggerAction.name,
                            message = "${campaign.family.displayName}: ${result.primaryReason}"
                        )
                    }
                    result.updatedCampaign
                }

                // §36 Paper mode: internal fill simulation of resting slices vs live price.
                var working = updatedCampaigns
                if (_uiState.value.executionMode == ExecutionMode.PAPER) {
                    working = working.map { c ->
                        val filled = executionEngine.simulatePaperFills(c, updatedAsset.lastPrice)
                        if (filled != c) {
                            repository.logAction(
                                asset = c.asset,
                                campaignId = c.id,
                                action = AuditLogAction.SLICE_FILLED,
                                reasonCode = "PAPER_FILL",
                                message = "${c.family.displayName}: slice filled internally at ${updatedAsset.lastPrice}"
                            )
                        }
                        filled
                    }
                }

                // §26 Board promotion: a dead primary upgrades healthy secondaries.
                val primaryDied = previousCampaigns.any { prior ->
                    prior.role == BoardRole.PRIMARY &&
                        prior.status != CampaignState.CANCELLED &&
                        working.find { it.id == prior.id }?.status == CampaignState.CANCELLED
                }
                if (primaryDied) {
                    working = working.map { c ->
                        val promoted = invalidationEngine.checkBoardPromotion(c, isPrimaryDead = true)
                        if (promoted != c) {
                            repository.logAction(
                                asset = c.asset,
                                campaignId = c.id,
                                action = AuditLogAction.BOARD_PROMOTED,
                                reasonCode = "SECONDARY_PROMOTED",
                                message = "${c.family.displayName} promoted to Primary after primary board decayed"
                            )
                        }
                        promoted
                    }
                }

                // §26 Rearm: cooldown lapsed and the level still exists -> re-arm.
                val envelopeAllowsRearm = !_uiState.value.riskEnvelope.isKillSwitchEngaged &&
                    !_uiState.value.riskEnvelope.isDailyLossExceeded
                if (envelopeAllowsRearm) {
                    working = working.map { c ->
                        val rearm = invalidationEngine.evaluateRearm(c, currentLevelMap, updatedAsset)
                        if (rearm.triggerAction == InvalidationAction.REARM_CAMPAIGN) {
                            repository.logAction(
                                asset = c.asset,
                                campaignId = c.id,
                                action = AuditLogAction.CAMPAIGN_STAGED,
                                reasonCode = "REARM_CAMPAIGN",
                                message = "${c.family.displayName}: ${rearm.primaryReason}"
                            )
                        }
                        rearm.updatedCampaign
                    }
                }

                // §28 Mirror activation: a hard-invalidated board wakes its suppressed mirror.
                working = working.map { c ->
                    val mirrorId = c.mirrorCampaignId ?: return@map c
                    val mirror = working.find { it.id == mirrorId } ?: return@map c
                    val activated = invalidationEngine.activateMirrorOnTrap(c, mirror) ?: return@map c
                    repository.logAction(
                        asset = activated.asset,
                        campaignId = activated.id,
                        action = AuditLogAction.DEFENSIVE_SCALED,
                        reasonCode = "MIRROR_ACTIVATED",
                        message = "${activated.family.displayName} activated defensively after mirror hard invalidation"
                    )
                    activated
                }

                val worstCaseR = riskEngine.computeWorstCaseStopOutR(
                    working,
                    _uiState.value.riskEnvelope.accountEquityUsd
                )

                _uiState.value = _uiState.value.copy(
                    selectedAsset = updatedAsset,
                    liveEvidence = evidence,
                    campaigns = working,
                    selectedCampaign = working.find { it.id == _uiState.value.selectedCampaign?.id } ?: working.firstOrNull(),
                    riskEnvelope = _uiState.value.riskEnvelope.copy(worstCaseStopOutR = worstCaseR)
                )

                // §22/§33: push cancellations to the venue and reconcile the live position.
                handleLiveExecution(previousCampaigns, updatedAsset)
            }
        }

        // 2. Order book stream (real best bid/ask with synthesized depth ladder)
        orderBookJob = viewModelScope.launch {
            liveProvider.orderBookStream(asset).collect { ob ->
                _uiState.value = _uiState.value.copy(orderBook = ob)
            }
        }

        // 3. Taker trades stream (real aggTrade tape with simulated fallback)
        takerFlowJob = viewModelScope.launch {
            val list = mutableListOf<TakerTrade>()
            liveProvider.takerTradeStream(asset).collect { trade ->
                list.add(0, trade)
                if (list.size > 20) list.removeAt(list.size - 1)
                _uiState.value = _uiState.value.copy(takerTrades = list.toList())
            }
        }
    }

    // ------------------------------------------------------------- Phase 4 execution

    /** Runs the staging gate (risk validation + ExecutionMode + §33 rejects), logging notes. */
    private fun gateAndLog(campaigns: List<Campaign>): List<Campaign> {
        val state = _uiState.value
        val gated = executionEngine.gateStaging(
            campaigns,
            state.executionMode,
            state.riskEnvelope,
            state.venueHealth,
            sessionManager.hasValidActiveSession()
        )
        val feedStale = ExecutionGuardrails.staleFeedAction(state.venueHealth) ==
            ExecutionGuardrails.StaleFeedAction.SUPPRESS_AND_TRIM
        val latencySpike = state.venueHealth.isLatencySpike
        val spreadBlown = state.selectedAsset?.let { ExecutionGuardrails.isSpreadBlowout(it) } == true
        return gated.map { g ->
            var campaign = g.campaign
            var note = g.note
            // §33: order-reject threshold pauses staging for that asset.
            if (ExecutionGuardrails.shouldPauseVenue(consecutiveRejectsBySymbol[campaign.asset] ?: 0)) {
                campaign = campaign.copy(status = CampaignState.SUPPRESSED)
                note = "Venue paused for ${campaign.asset}: order-reject threshold exceeded"
            }
            // §33: stale feed suppresses staging and cancels nonessential resting orders.
            if (feedStale && campaign.status == CampaignState.STAGED) {
                campaign = ExecutionGuardrails.trimNonessentialRestingOrders(listOf(campaign)).first()
                note = "Data feed stale: staging suppressed, nonessential resting orders trimmed"
            }
            // §33: exchange latency spike pauses stop-entry arming (stop ladders stay down).
            if (latencySpike && campaign.status == CampaignState.STAGED &&
                campaign.entryLadder.any { it.orderType != OrderType.PASSIVE_LIMIT }
            ) {
                campaign = campaign.copy(status = CampaignState.SUPPRESSED)
                note = "Exchange latency spike: stop-entry arming paused"
            }
            // §33: spread above the volatility-adjusted limit disables passive staging.
            if (spreadBlown && campaign.status == CampaignState.STAGED &&
                campaign.entryLadder.isNotEmpty() &&
                campaign.entryLadder.all { it.orderType == OrderType.PASSIVE_LIMIT }
            ) {
                campaign = campaign.copy(status = CampaignState.SUPPRESSED)
                note = "Spread exceeds volatility-adjusted limit: passive staging disabled"
            }
            if (note != null) {
                viewModelScope.launch {
                    repository.logAction(
                        asset = campaign.asset,
                        campaignId = campaign.id,
                        action = AuditLogAction.CAMPAIGN_STAGED,
                        reasonCode = "STAGING_GATE_${state.executionMode.name}",
                        message = "${campaign.family.displayName}: $note"
                    )
                }
            }
            campaign
        }
    }

    /** Preserves exchange/paper-reconciled fill state across level-map recomputes (§33). */
    private fun mergeFillState(fresh: List<Campaign>, previous: List<Campaign>): List<Campaign> =
        fresh.map { c ->
            val prior = previous.find { it.id == c.id }
            if (prior == null || prior.entryLadder.none { it.isFilled }) {
                c
            } else {
                val mergedLadder = c.entryLadder.map { slice ->
                    val priorSlice = prior.entryLadder.find { it.sliceIndex == slice.sliceIndex }
                    if (priorSlice != null && priorSlice.isFilled) {
                        slice.copy(isFilled = true, isResting = false)
                    } else slice
                }
                val anyFilled = mergedLadder.any { it.isFilled }
                c.copy(
                    entryLadder = mergedLadder,
                    status = if (anyFilled) executionEngine.statusAfterFills(mergedLadder) else c.status
                )
            }
        }

    /** §22/§36: routes resting slices to the venue for live modes with a valid session. */
    private suspend fun routeLiveBoards(campaigns: List<Campaign>) {
        val mode = _uiState.value.executionMode
        if (mode != ExecutionMode.CAPPED_LIVE && mode != ExecutionMode.SCALED_LIVE) return
        if (!sessionManager.hasValidActiveSession()) return
        val toRoute = campaigns.filter { it.status == CampaignState.STAGED }
        if (toRoute.isEmpty()) return
        val creds = sessionManager.copyCredentials() ?: return
        var rejects = 0
        try {
            for (campaign in toRoute) {
                for (slice in campaign.entryLadder) {
                    if (!slice.isResting || slice.isFilled) continue
                    val clientId = executionEngine.clientOrderId(campaign.id, slice.sliceIndex)
                    if (!routedClientIds.add(clientId)) continue
                    val result = BinanceSignedClient.placeOrder(
                        symbol = campaign.asset,
                        side = if (campaign.isLong) "BUY" else "SELL",
                        orderType = if (slice.orderType == OrderType.PASSIVE_LIMIT) "LIMIT" else "STOP",
                        qty = slice.qty,
                        price = slice.price,
                        stopPrice = if (slice.orderType == OrderType.PASSIVE_LIMIT) null else slice.price,
                        clientOrderId = clientId,
                        apiKey = creds.first,
                        secret = creds.second
                    )
                    if (result.ok) {
                        logLiveNote(
                            campaign.id,
                            "ORDER_PLACED",
                            "${campaign.family.displayName} slice ${slice.sliceIndex} routed at ${slice.price}"
                        )
                    } else {
                        rejects++
                        routedClientIds.remove(clientId)
                        logLiveNote(
                            campaign.id,
                            "ORDER_REJECTED",
                            "${campaign.family.displayName} slice ${slice.sliceIndex} rejected: ${result.message ?: "unknown"}"
                        )
                    }
                }
            }
        } finally {
            Arrays.fill(creds.first, '\u0000')
            Arrays.fill(creds.second, '\u0000')
        }
        consecutiveRejectsBySymbol[campaigns.first().asset] =
            if (rejects > 0) (consecutiveRejectsBySymbol[campaigns.first().asset] ?: 0) + rejects else 0
    }

    /** §25: opens the user-data stream so exchange fills reconcile onto ladder slices. */
    private fun startUserDataStreamIfNeeded() {
        val mode = _uiState.value.executionMode
        if (mode != ExecutionMode.CAPPED_LIVE && mode != ExecutionMode.SCALED_LIVE) return
        if (!sessionManager.hasValidActiveSession()) return
        if (userDataJob != null) return
        userDataJob = viewModelScope.launch {
            val creds = sessionManager.copyCredentials()
            if (creds == null) return@launch
            val listenKey = BinanceSignedClient.createListenKey(creds.first)
            Arrays.fill(creds.first, '\u0000')
            Arrays.fill(creds.second, '\u0000')
            if (listenKey == null) {
                logLiveNote(
                    "PORTFOLIO",
                    "USER_STREAM_FAILED",
                    "Listen key request failed; fills reconcile on next open-order snapshot"
                )
                return@launch
            }
            logLiveNote("PORTFOLIO", "USER_STREAM_OPEN", "User-data stream connected for fill reconciliation")
            BinanceUserDataClient.streamUserEvents(listenKey).collect { event ->
                handleFillEvent(event)
            }
        }
    }

    /** §22/§25: applies one exchange order event onto the matching campaign ladder. */
    private suspend fun handleFillEvent(event: BinanceUserDataClient.FillEvent) {
        val current = _uiState.value.campaigns
        val target = current.find { event.clientOrderId.startsWith(it.id) } ?: return
        val updated = when {
            event.isFill -> executionEngine.reconcileFill(target, event.clientOrderId)
            event.isTerminalCancel -> executionEngine.reconcileCancel(target, event.clientOrderId)
            else -> return
        }
        if (updated == target) return
        _uiState.value = _uiState.value.copy(
            campaigns = current.map { if (it.id == target.id) updated else it },
            selectedCampaign = if (_uiState.value.selectedCampaign?.id == target.id) {
                updated
            } else {
                _uiState.value.selectedCampaign
            }
        )
        if (event.isFill) {
            repository.logAction(
                asset = target.asset,
                campaignId = target.id,
                action = AuditLogAction.SLICE_FILLED,
                reasonCode = "EXCHANGE_FILL",
                message = "${target.family.displayName}: exchange fill ${event.lastFilledQty} @ ${event.lastFilledPrice} (${event.orderStatus})"
            )
        }
    }

    /** §22/§33: venue cancels for newly-dead boards + throttled position reconciliation. */
    private suspend fun handleLiveExecution(previous: List<Campaign>, asset: CryptoAsset) {
        val mode = _uiState.value.executionMode
        if (mode != ExecutionMode.CAPPED_LIVE && mode != ExecutionMode.SCALED_LIVE) return
        if (!sessionManager.hasValidActiveSession()) return

        for (c in _uiState.value.campaigns) {
            val prior = previous.find { it.id == c.id } ?: continue
            val wasLive = prior.status == CampaignState.STAGED ||
                prior.status == CampaignState.PARTIALLY_FILLED ||
                prior.status == CampaignState.ACTIVE
            val nowDead = c.status == CampaignState.CANCELLED || c.status == CampaignState.SUPPRESSED
            if (wasLive && nowDead && prior.entryLadder.any { it.isResting && !it.isFilled }) {
                val creds = sessionManager.copyCredentials() ?: continue
                val cancelled = BinanceSignedClient.cancelAllOpenOrders(c.asset, creds.first, creds.second)
                Arrays.fill(creds.first, '\u0000')
                Arrays.fill(creds.second, '\u0000')
                logLiveNote(
                    c.id,
                    if (cancelled) "VENUE_CANCELLED" else "VENUE_CANCEL_FAILED",
                    "${c.family.displayName}: resting orders ${if (cancelled) "cancelled at venue" else "cancel failed at venue"}"
                )
            }
        }

        // §33: unexpected position mismatch check, throttled to one poll per 30 s.
        val now = System.currentTimeMillis()
        if (now - lastPositionCheckMs > 30_000) {
            lastPositionCheckMs = now
            val creds = sessionManager.copyCredentials()
            if (creds != null) {
                val exchange = BinanceSignedClient.fetchPositionRisk(asset.symbol, creds.first, creds.second)
                Arrays.fill(creds.first, '\u0000')
                Arrays.fill(creds.second, '\u0000')
                if (exchange != null) {
                    val expected = ExecutionGuardrails.expectedPositionQty(_uiState.value.campaigns, asset.lastPrice)
                    if (ExecutionGuardrails.positionMismatchAction(expected, exchange.positionAmt) ==
                        ExecutionGuardrails.PositionMismatchAction.FLATTEN_AND_LOCK
                    ) {
                        flattenAndLock(asset, expected, exchange.positionAmt)
                    }
                }
            }
        }
    }

    /** §33: unexpected position mismatch — flatten symbol, lock until reconciliation. */
    private suspend fun flattenAndLock(asset: CryptoAsset, expectedQty: Double, exchangeQty: Double) {
        val creds = sessionManager.copyCredentials()
        if (creds != null) {
            BinanceSignedClient.cancelAllOpenOrders(asset.symbol, creds.first, creds.second)
            Arrays.fill(creds.first, '\u0000')
            Arrays.fill(creds.second, '\u0000')
        }
        _uiState.value = _uiState.value.copy(
            riskEnvelope = _uiState.value.riskEnvelope.copy(isKillSwitchEngaged = true),
            campaigns = _uiState.value.campaigns.map { campaign ->
                if (campaign.asset == asset.symbol) {
                    campaign.copy(
                        status = CampaignState.CANCELLED,
                        entryLadder = campaign.entryLadder.map { it.copy(isResting = false) }
                    )
                } else campaign
            }
        )
        repository.logAction(
            asset = asset.symbol,
            campaignId = "POSITION_GUARD",
            action = AuditLogAction.EMERGENCY_KILL,
            reasonCode = "POSITION_MISMATCH_LOCK",
            message = "Unexpected position mismatch (expected $expectedQty, exchange $exchangeQty). Symbol flattened and locked until reconciliation."
        )
    }

    /**
     * Phase 5 (§37): deterministic replay of the proactive pipeline over persisted
     * candle history for the selected asset. `baseline = true` reruns it with the
     * asymmetric overlay disabled to quantify the proactive edge.
     */
    fun runReplay(baseline: Boolean = false) {
        val asset = _uiState.value.selectedAsset ?: return
        if (_uiState.value.isReplayRunning) return
        _uiState.value = _uiState.value.copy(isReplayRunning = true)
        viewModelScope.launch {
            try {
                val timeframes = runCatching { universeScreener.loadTimeframes(asset.symbol) }.getOrNull()
                val candles = timeframes?.candles1h.orEmpty()
                if (candles.size < 30) {
                    logLiveNote(
                        "REPLAY",
                        "REPLAY_INSUFFICIENT_DATA",
                        "Replay needs >=30 persisted 1h candles for ${asset.symbol}; found ${candles.size}"
                    )
                    _uiState.value = _uiState.value.copy(isReplayRunning = false)
                    return@launch
                }
                val calibration = runCatching { universeScreener.loadCalibration(asset) }.getOrNull()
                var result = replayEngine.replay(
                    asset = asset,
                    candles = candles,
                    fundingRatePct = asset.fundingRatePct,
                    spreadPct = asset.orderBookSpreadPct,
                    calibration = calibration,
                    baselineMode = baseline
                )
                // §37: quantify the proactive edge against the symmetric-only baseline.
                if (!baseline) {
                    val symmetric = replayEngine.replay(
                        asset = asset,
                        candles = candles,
                        fundingRatePct = asset.fundingRatePct,
                        spreadPct = asset.orderBookSpreadPct,
                        calibration = calibration,
                        baselineMode = true
                    )
                    val base = symmetric.metrics.totalPnlUsd
                    val edge = if (Math.abs(base) > 0.01) {
                        (result.metrics.totalPnlUsd - base) / Math.abs(base) * 100.0
                    } else 0.0
                    result = result.copy(
                        metrics = result.metrics.copy(proactiveEdgeVsBaselinePct = Math.round(edge * 10.0) / 10.0)
                    )
                }
                _uiState.value = _uiState.value.copy(
                    lastReplay = result,
                    backtestMetrics = result.metrics,
                    isReplayRunning = false
                )
                repository.logAction(
                    asset = asset.symbol,
                    campaignId = if (baseline) "REPLAY_BASELINE" else "REPLAY",
                    action = AuditLogAction.CAMPAIGN_STAGED,
                    reasonCode = "REPLAY_EXECUTED",
                    message = "Historical replay over ${candles.size} 1h candles: ${result.metrics.replayedCampaigns} boards, " +
                        "PnL $${result.metrics.totalPnlUsd}, miss rate ${result.metrics.orderMissRatePct}%"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isReplayRunning = false)
                logLiveNote(
                    "REPLAY",
                    "REPLAY_FAILED",
                    "Replay failed: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    private suspend fun logLiveNote(campaignId: String, reasonCode: String, message: String) {
        repository.logAction(
            asset = _uiState.value.selectedAsset?.symbol ?: "PORTFOLIO",
            campaignId = campaignId,
            action = AuditLogAction.CAMPAIGN_STAGED,
            reasonCode = reasonCode,
            message = message
        )
    }

    override fun onCleared() {
        super.onCleared()
        activeDeltaJob?.cancel()
        orderBookJob?.cancel()
        takerFlowJob?.cancel()
        dormantPollingJob?.cancel()
        userDataJob?.cancel()
        sessionManager.terminateSessionAndWipeRam()
    }
}

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
import com.example.data.model.CryptoAsset
import com.example.data.model.EvidenceFrame
import com.example.data.model.ExecutionMode
import com.example.data.model.LevelMap
import com.example.data.model.RiskEnvelope
import com.example.data.model.VenueHealth
import com.example.data.remote.CoinGlassClient
import com.example.engine.CampaignEngine
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

data class MainUiState(
    val assets: List<CryptoAsset> = emptyList(),
    val selectedAsset: CryptoAsset? = null,
    val levelMap: LevelMap? = null,
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
    private fun applyUniverseAndRecompute(universe: List<CryptoAsset>, chosen: CryptoAsset) {
        val map = structureEngine.buildLevelMap(chosen)
        val candidatePaths = pathEngine.rankCandidatePaths(chosen, map)
        val campaigns = campaignEngine.planCampaigns(chosen, map, candidatePaths)
        val evidence = liveProvider.buildEvidenceFrame(chosen)
        _uiState.value = _uiState.value.copy(
            assets = universe,
            selectedAsset = chosen,
            levelMap = map,
            paths = candidatePaths,
            campaigns = campaigns,
            selectedCampaign = campaigns.firstOrNull(),
            liveEvidence = evidence,
            isLiveUniverse = universe.any { it.quoteVolume24h > 0 && it.orderBookSpreadPct < 100.0 }
        )
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

        _uiState.value = _uiState.value.copy(
            selectedAsset = asset,
            levelMap = map,
            paths = candidatePaths,
            campaigns = planned,
            selectedCampaign = planned.firstOrNull(),
            liveEvidence = evidence
        )

        startActiveDeltaMode(asset)

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

                val worstCaseR = riskEngine.computeWorstCaseStopOutR(
                    updatedCampaigns,
                    _uiState.value.riskEnvelope.accountEquityUsd
                )

                _uiState.value = _uiState.value.copy(
                    selectedAsset = updatedAsset,
                    liveEvidence = evidence,
                    campaigns = updatedCampaigns,
                    selectedCampaign = updatedCampaigns.find { it.id == _uiState.value.selectedCampaign?.id } ?: updatedCampaigns.firstOrNull(),
                    riskEnvelope = _uiState.value.riskEnvelope.copy(worstCaseStopOutR = worstCaseR)
                )
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

    override fun onCleared() {
        super.onCleared()
        activeDeltaJob?.cancel()
        orderBookJob?.cancel()
        takerFlowJob?.cancel()
        dormantPollingJob?.cancel()
        sessionManager.terminateSessionAndWipeRam()
    }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppBottomNavigationBar
import com.example.ui.AppDestination
import com.example.ui.MainViewModel
import com.example.ui.components.ManualOverrideDialog
import com.example.ui.components.OrphanedOrderWarningDialog
import com.example.ui.components.TopBar
import com.example.ui.screens.AuditTrailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RiskGuardrailsScreen
import com.example.ui.screens.ScreenerScreen
import com.example.ui.screens.SimulationScreen
import com.example.ui.screens.TelemetryScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ProactiveMsApp()
            }
        }
    }
}

@Composable
fun ProactiveMsApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        topBar = {
            TopBar(
                executionMode = uiState.executionMode,
                onModeChange = { viewModel.setExecutionMode(it) },
                riskEnvelope = uiState.riskEnvelope,
                venueHealth = uiState.venueHealth,
                isSessionActive = uiState.isSessionActive,
                onToggleKillSwitch = { viewModel.toggleKillSwitch() },
                onOpenOrphanWarning = { viewModel.openOrphanedOrderWarning() }
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentDestination = currentDestination,
                onNavigate = { currentDestination = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            when (currentDestination) {
                AppDestination.SCREENER -> {
                    ScreenerScreen(
                        assets = uiState.assets,
                        selectedAsset = uiState.selectedAsset,
                        onSelectAsset = { symbol ->
                            viewModel.selectAsset(symbol)
                            currentDestination = AppDestination.DASHBOARD
                        }
                    )
                }

                AppDestination.DASHBOARD -> {
                    DashboardScreen(
                        asset = uiState.selectedAsset,
                        levelMap = uiState.levelMap,
                        campaigns = uiState.campaigns,
                        selectedCampaign = uiState.selectedCampaign,
                        liveEvidence = uiState.liveEvidence,
                        onSelectCampaign = { viewModel.selectCampaign(it) },
                        onOpenOverride = { viewModel.openManualOverrideDialog(it) },
                        onSoftCancel = { viewModel.triggerSoftCancel(it) },
                        onHardFlatten = { viewModel.triggerHardFlatten(it) }
                    )
                }

                AppDestination.TELEMETRY -> {
                    TelemetryScreen(
                        asset = uiState.selectedAsset,
                        orderBook = uiState.orderBook,
                        takerTrades = uiState.takerTrades,
                        evidence = uiState.liveEvidence
                    )
                }

                AppDestination.RISK -> {
                    RiskGuardrailsScreen(
                        riskEnvelope = uiState.riskEnvelope,
                        venueHealth = uiState.venueHealth,
                        isSessionActive = uiState.isSessionActive,
                        onToggleKillSwitch = { viewModel.toggleKillSwitch() },
                        onStartSession = { k, s -> viewModel.startApiSession(k, s) },
                        onTerminateSession = { viewModel.terminateApiSession() }
                    )
                }

                AppDestination.SIMULATION -> {
                    SimulationScreen(
                        metrics = uiState.backtestMetrics,
                        selectedScenario = uiState.selectedScenario,
                        onRunScenario = { viewModel.runSimulationScenario(it) }
                    )
                }

                AppDestination.AUDIT -> {
                    AuditTrailScreen(
                        auditLogs = uiState.auditLogs
                    )
                }
            }
        }

        // Section 3: Orphaned Order OS Intercept Dialog
        OrphanedOrderWarningDialog(
            isOpen = uiState.isOrphanedOrderWarningOpen,
            onAcknowledgeAndWipe = { viewModel.acknowledgeOrphanedOrderWarning() },
            onDismiss = { viewModel.acknowledgeOrphanedOrderWarning() }
        )

        // Section 24: Manual Override Dialog with Persistent Guardrail Warnings
        ManualOverrideDialog(
            isOpen = uiState.isOverrideDialogOpen,
            campaign = uiState.selectedCampaign,
            warnings = uiState.overrideWarnings,
            onApplyOverride = { newStop, newSize ->
                uiState.selectedCampaign?.let {
                    viewModel.applyManualOverride(it.id, newStop, newSize)
                }
            },
            onDismiss = { viewModel.closeManualOverrideDialog() }
        )
    }
}

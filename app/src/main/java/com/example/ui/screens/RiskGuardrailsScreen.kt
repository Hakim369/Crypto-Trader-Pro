package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RiskEnvelope
import com.example.data.model.VenueHealth
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary

@Composable
fun RiskGuardrailsScreen(
    riskEnvelope: RiskEnvelope,
    venueHealth: VenueHealth,
    isSessionActive: Boolean,
    onToggleKillSwitch: () -> Unit,
    onStartSession: (key: String, secret: String) -> Unit,
    onTerminateSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var apiSecretInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("risk_guardrails_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "RISK ENVELOPE & ZERO-CLOUD GUARDRAILS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Client hardware isolation, RAM-only execution, and strict exposure caps",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 10.sp)
                )
            }
        }

        // Account Portfolio Risk Caps
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "PORTFOLIO RISK ALLOCATION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        fontSize = 10.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Account Equity", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp))
                        Text("$${riskEnvelope.accountEquityUsd.toInt()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = SlateTextPrimary))
                    }
                    Column {
                        Text("Gross Exposure / Cap", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp))
                        Text("$${riskEnvelope.currentGrossExposureUsd.toInt()} / $${riskEnvelope.maxGrossExposureUsd.toInt()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = CyanAccent))
                    }
                    Column {
                        Text("Daily PnL / Limit", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp))
                        Text("+$${riskEnvelope.dailyPnLUsd.toInt()} (2.0R)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = EmeraldBull))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Kill Switch Action Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (riskEnvelope.isKillSwitchEngaged) CrimsonBear.copy(alpha = 0.2f)
                            else Color(0xFF090E1D),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (riskEnvelope.isKillSwitchEngaged) CrimsonBear else CardBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (riskEnvelope.isKillSwitchEngaged) "KILL-SWITCH ENGAGED" else "Emergency Kill-Switch (Standby)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (riskEnvelope.isKillSwitchEngaged) CrimsonBear else SlateTextPrimary
                            )
                        )
                        Text(
                            text = if (riskEnvelope.isKillSwitchEngaged) "All staging halted. All resting orders cancelled." else "One-tap emergency cancellation across all symbols",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp)
                        )
                    }

                    Button(
                        onClick = onToggleKillSwitch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (riskEnvelope.isKillSwitchEngaged) EmeraldBull else CrimsonBear,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("kill_switch_action_button")
                    ) {
                        Text(
                            text = if (riskEnvelope.isKillSwitchEngaged) "Reset" else "Engage",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Pre-Flight Hardware & Connectivity Diagnostics (Section 2)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "PRE-FLIGHT HARDWARE DIAGNOSTICS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        fontSize = 10.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                DiagnosticRow(
                    icon = Icons.Default.Speed,
                    label = "Binance Futures Gateway Latency",
                    statusText = "${venueHealth.binancePingMs} ms",
                    isPass = !venueHealth.isLatencySpike
                )
                DiagnosticRow(
                    icon = Icons.Default.Speed,
                    label = "CoinGlass Derivatives API Latency",
                    statusText = "${venueHealth.coinglassPingMs} ms",
                    isPass = !venueHealth.isLatencySpike
                )
                DiagnosticRow(
                    icon = Icons.Default.Memory,
                    label = "Device Memory (RAM) Allocation",
                    statusText = "${venueHealth.availableRamMb} MB Available",
                    isPass = !venueHealth.isRamConstrained
                )
                DiagnosticRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Thermal State & Low Power Mode",
                    statusText = if (venueHealth.isLowPowerMode || venueHealth.isThermallyThrottled) "Throttled" else "Nominal",
                    isPass = !venueHealth.isLowPowerMode && !venueHealth.isThermallyThrottled
                )
            }
        }

        // Section 3: Session-Based API Key Manager (Zero Persistence)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SESSION API KEY MANAGER (ZERO PERSISTENCE)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            fontSize = 10.sp
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSessionActive) EmeraldBull.copy(alpha = 0.15f) else Color(0xFF22324F))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSessionActive) "SESSION ACTIVE (RAM)" else "LOCKED / NO KEY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSessionActive) EmeraldBull else SlateTextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Mandate: Keys are never saved to disk, database, or cloud. Held in ephemeral memory only during active foreground app usage.",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (!isSessionActive) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Binance API Key") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = CyanAccent) },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiSecretInput,
                        onValueChange = { apiSecretInput = it },
                        label = { Text("Binance API Secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CyanAccent) },
                        modifier = Modifier.fillMaxWidth().testTag("api_secret_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (apiKeyInput.isNotBlank() && apiSecretInput.isNotBlank()) {
                                onStartSession(apiKeyInput, apiSecretInput)
                                apiKeyInput = ""
                                apiSecretInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("start_session_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
                    ) {
                        Text("Unlock In-Memory Trade Session", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Keys active in Trusted Memory. Ready for staging.",
                            style = MaterialTheme.typography.labelSmall.copy(color = EmeraldBull, fontSize = 10.sp)
                        )
                        OutlinedButton(
                            onClick = onTerminateSession,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBear),
                            modifier = Modifier.testTag("terminate_session_button")
                        ) {
                            Text("Wipe Keys & Lock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun DiagnosticRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    statusText: String,
    isPass: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPass) EmeraldBull else CrimsonBear,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(color = SlateTextPrimary, fontSize = 10.sp)
            )
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isPass) EmeraldBull else CrimsonBear,
                fontSize = 10.sp
            )
        )
    }
}

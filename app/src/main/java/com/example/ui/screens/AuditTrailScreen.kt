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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuditLogAction
import com.example.data.model.AuditLogEntry
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.VioletAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuditTrailScreen(
    auditLogs: List<AuditLogEntry>,
    modifier: Modifier = Modifier
) {
    var selectedActionFilter by remember { mutableStateOf<AuditLogAction?>(null) }

    val filteredLogs = remember(auditLogs, selectedActionFilter) {
        if (selectedActionFilter == null) auditLogs
        else auditLogs.filter { it.action == selectedActionFilter }
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("audit_trail_screen")
    ) {
        // Section Header
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
                    text = "AUTOMATION AUDIT TRAIL LOG",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = "${filteredLogs.size} Events",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SlateTextSecondary,
                        fontSize = 10.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Immutable client-side log: Every placement, soft-cancel, flatten, and mirror activation with reason codes",
                style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 10.sp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Filter Tabs
        ScrollableTabRow(
            selectedTabIndex = if (selectedActionFilter == null) 0 else selectedActionFilter!!.ordinal + 1,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = CyanAccent,
            divider = {}
        ) {
            Tab(
                selected = selectedActionFilter == null,
                onClick = { selectedActionFilter = null },
                text = { Text("All (${auditLogs.size})", fontSize = 11.sp) }
            )
            AuditLogAction.values().forEach { action ->
                val count = auditLogs.count { it.action == action }
                Tab(
                    selected = selectedActionFilter == action,
                    onClick = { selectedActionFilter = action },
                    text = { Text("${action.label.split(" ").first()} ($count)", fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Logs List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredLogs, key = { it.id.toString() + "_" + it.timestamp }) { log ->
                val actionColor = when (log.action) {
                    AuditLogAction.CAMPAIGN_STAGED -> CyanAccent
                    AuditLogAction.SLICE_FILLED -> EmeraldBull
                    AuditLogAction.SOFT_CANCEL -> AmberWarning
                    AuditLogAction.HARD_FLATTEN -> CrimsonBear
                    AuditLogAction.BOARD_PROMOTED -> VioletAccent
                    AuditLogAction.DEFENSIVE_SCALED -> Color(0xFF64B5F6)
                    AuditLogAction.EMERGENCY_KILL -> CrimsonBear
                    AuditLogAction.MANUAL_OVERRIDE -> AmberWarning
                    AuditLogAction.ORPHANED_ALERT -> CrimsonBear
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(8.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(actionColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = log.action.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = actionColor,
                                    fontSize = 10.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${log.asset}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateTextSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Text(
                            text = timeFormatter.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SlateTextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SlateTextPrimary,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Reason: ${log.reasonCode} • ID: ${log.campaignId}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SlateTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }
    }
}

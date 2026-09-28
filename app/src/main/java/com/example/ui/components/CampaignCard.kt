package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BoardRole
import com.example.data.model.Campaign
import com.example.data.model.CampaignState
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

@Composable
fun CampaignCard(
    campaign: Campaign,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpenOverride: () -> Unit,
    onSoftCancel: () -> Unit,
    onHardFlatten: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roleColor = when (campaign.role) {
        BoardRole.PRIMARY -> CyanAccent
        BoardRole.SECONDARY -> VioletAccent
        BoardRole.DEFENSIVE -> AmberWarning
        BoardRole.DISABLED -> SlateTextMuted
    }

    val statusColor = when (campaign.status) {
        CampaignState.STAGED -> CyanAccent
        CampaignState.ACTIVE -> EmeraldBull
        CampaignState.PARTIALLY_FILLED -> Color(0xFF00E5FF)
        CampaignState.REDUCED -> AmberWarning
        CampaignState.CANCELLED -> CrimsonBear
        CampaignState.COMPLETED -> EmeraldBull
        CampaignState.SUPPRESSED -> SlateTextMuted
        CampaignState.PLANNED -> Color(0xFF90CAF9)
    }

    val dirColor = if (campaign.isLong) EmeraldBull else CrimsonBear

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBackground)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CyanAccent else CardBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
            .padding(12.dp)
            .testTag("campaign_card_${campaign.family.name}")
    ) {
        // Top row: Family name, Direction, and Board Role badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dirColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = campaign.family.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Board Role Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(roleColor.copy(alpha = 0.15f))
                        .border(1.dp, roleColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = campaign.role.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = roleColor,
                            fontSize = 9.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = campaign.status.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Structural description
        Text(
            text = campaign.family.structuralDescription,
            style = MaterialTheme.typography.labelSmall.copy(
                color = SlateTextMuted,
                fontSize = 10.sp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Priority, Size budget & Invalidation score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SIZE BUDGET",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp)
                )
                Text(
                    text = "$${campaign.sizeBudgetUsd.toInt()}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = SlateTextPrimary
                    )
                )
            }

            Column {
                Text(
                    text = "PRIORITY RANK",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp)
                )
                Text(
                    text = "${campaign.priorityScore.toInt()} pts",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = CyanAccent
                    )
                )
            }

            Column {
                Text(
                    text = "HARD STOP",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp)
                )
                Text(
                    text = "$${campaign.stopLogic.hardStopPrice}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = CrimsonBear
                    )
                )
            }

            Column {
                Text(
                    text = "INVALIDATION",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp)
                )
                Text(
                    text = "${campaign.invalidationScore}/100",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (campaign.isSoftInvalidated) CrimsonBear else EmeraldBull
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Entry Ladder summary slices
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A1020), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            campaign.entryLadder.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (slice.isFilled) EmeraldBull
                                else if (slice.isResting) AmberWarning
                                else SlateTextMuted
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "S${slice.sliceIndex}: $${slice.price}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (slice.isFilled) EmeraldBull else SlateTextSecondary
                        )
                    )
                }
            }
        }

        if (isSelected) {
            Spacer(modifier = Modifier.height(10.dp))
            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onOpenOverride,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                ) {
                    Text("Override", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onSoftCancel,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                ) {
                    Text("Soft Cancel", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onHardFlatten,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBear)
                ) {
                    Text("Flatten", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

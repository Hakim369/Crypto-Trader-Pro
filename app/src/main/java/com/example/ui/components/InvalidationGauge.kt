package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Campaign
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary

@Composable
fun InvalidationGauge(
    campaign: Campaign?,
    modifier: Modifier = Modifier
) {
    if (campaign == null) return

    val score = campaign.invalidationScore
    val softThreshold = campaign.stopLogic.softThreshold // 45
    val hardThreshold = campaign.stopLogic.hardThreshold // 70

    val (badgeText, badgeColor) = when {
        score >= hardThreshold -> Pair("HARD FLATTEN (Score >= $hardThreshold)", CrimsonBear)
        score >= softThreshold -> Pair("SOFT CANCEL (Score >= $softThreshold)", AmberWarning)
        else -> Pair("SURVIVAL ACTIVE (Score < $softThreshold)", EmeraldBull)
    }

    Column(
        modifier = modifier
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
            Column {
                Text(
                    text = "PROACTIVE INVALIDATION ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA),
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = "Cancellation-First Evidence Monitor",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SlateTextMuted,
                        fontSize = 9.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Invalidation Progress Meter with Threshold Gates
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = badgeColor
                )
            )
            Text(
                text = " / 100",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SlateTextMuted,
                    fontSize = 11.sp
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { (score / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = badgeColor,
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0 Safe",
                        style = MaterialTheme.typography.labelSmall.copy(color = EmeraldBull, fontSize = 9.sp)
                    )
                    Text(
                        text = "▲ Soft: $softThreshold",
                        style = MaterialTheme.typography.labelSmall.copy(color = AmberWarning, fontSize = 9.sp)
                    )
                    Text(
                        text = "▲ Hard: $hardThreshold",
                        style = MaterialTheme.typography.labelSmall.copy(color = CrimsonBear, fontSize = 9.sp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Top contributing reasons breakdown
        Text(
            text = "Active Evidence Contributors:",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = SlateTextSecondary
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (campaign.topReasons.isEmpty()) {
            Text(
                text = "• No structural violations detected. Tape aligning with campaign thesis.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SlateTextMuted,
                    fontSize = 11.sp
                )
            )
        } else {
            campaign.topReasons.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(CrimsonBear)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[+${reason.points} pts] ${reason.explanation}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SlateTextPrimary,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

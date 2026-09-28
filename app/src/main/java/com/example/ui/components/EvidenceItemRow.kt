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
import com.example.data.model.EvidenceFrame
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
fun EvidenceItemRow(
    evidence: EvidenceFrame?,
    modifier: Modifier = Modifier
) {
    if (evidence == null) return

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
            Text(
                text = "SIDE-AGNOSTIC LIVE EVIDENCE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = evidence.recentImpulseQuality,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SlateTextSecondary,
                    fontSize = 10.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            EvidenceMetricPill(
                title = "1H OI DELTA",
                value = "${if (evidence.oiDelta1hPct > 0) "+" else ""}${evidence.oiDelta1hPct}%",
                color = if (evidence.oiDelta1hPct > 0.5) EmeraldBull else CrimsonBear
            )
            EvidenceMetricPill(
                title = "FUNDING CHG",
                value = "${if (evidence.fundingRateChangePct > 0) "+" else ""}${(evidence.fundingRateChangePct * 10000).toInt()} bps",
                color = if (evidence.fundingRateChangePct > 0) AmberWarning else CyanAccent
            )
            EvidenceMetricPill(
                title = "BASIS SPREAD",
                value = "${if (evidence.basisDeltaBps > 0) "+" else ""}${evidence.basisDeltaBps} bps",
                color = SlateTextPrimary
            )
            EvidenceMetricPill(
                title = "TAKER RATIO",
                value = "${(evidence.takerBuyRatio * 100).toInt()}% B",
                color = if (evidence.takerBuyRatio > 0.5) EmeraldBull else CrimsonBear
            )
            EvidenceMetricPill(
                title = "TRAP PROB",
                value = "${evidence.trapProbabilityPct}%",
                color = if (evidence.trapProbabilityPct > 50) CrimsonBear else EmeraldBull
            )
        }
    }
}

@Composable
private fun EvidenceMetricPill(title: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .background(Color(0xFF090E1D), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = SlateTextMuted,
                fontSize = 8.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color,
                fontSize = 11.sp
            )
        )
    }
}

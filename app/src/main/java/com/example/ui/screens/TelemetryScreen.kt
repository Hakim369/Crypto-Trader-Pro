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
import com.example.data.model.CryptoAsset
import com.example.data.model.EvidenceFrame
import com.example.engine.OrderBookSnapshot
import com.example.engine.TakerTrade
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary

@Composable
fun TelemetryScreen(
    asset: CryptoAsset?,
    orderBook: OrderBookSnapshot?,
    takerTrades: List<TakerTrade>,
    evidence: EvidenceFrame?,
    modifier: Modifier = Modifier
) {
    if (asset == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select an asset to view live telemetry", color = SlateTextMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("telemetry_screen"),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE ACTIVE DELTA TELEMETRY (${asset.symbol})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "100% Client-Side Invalidation",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = EmeraldBull,
                            fontSize = 9.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Streaming real-time L2 queue shifts, taker aggression, and crowding distortion",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 10.sp)
                )
            }
        }

        // L2 Order Book Queue Component
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "L2 ORDER BOOK DEPTH QUEUE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "Spread: ${orderBook?.spreadPct ?: asset.orderBookSpreadPct}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = CyanAccent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Bids Column (Left)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BIDS (Size / Price)",
                            style = MaterialTheme.typography.labelSmall.copy(color = EmeraldBull, fontSize = 9.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        orderBook?.bids?.take(5)?.forEach { bid ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${bid.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = SlateTextMuted,
                                        fontSize = 9.sp
                                    )
                                )
                                Text(
                                    text = "$${bid.price}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = EmeraldBull,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Asks Column (Right)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ASKS (Price / Size)",
                            style = MaterialTheme.typography.labelSmall.copy(color = CrimsonBear, fontSize = 9.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        orderBook?.asks?.take(5)?.forEach { ask ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$${ask.price}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = CrimsonBear,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                )
                                Text(
                                    text = "${ask.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = SlateTextMuted,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Real-Time Taker Aggression Stream
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "RECENT TAKER FLOW & MICRO-IMPULSES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        fontSize = 10.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                takerTrades.take(8).forEach { trade ->
                    val isTakerBuy = !trade.isBuyerMaker
                    val color = if (isTakerBuy) EmeraldBull else CrimsonBear
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTakerBuy) "TAKER BUY" else "TAKER SELL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                    fontSize = 9.sp
                                )
                            )
                        }

                        Text(
                            text = "$${trade.price}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = SlateTextPrimary,
                                fontSize = 10.sp
                            )
                        )

                        Text(
                            text = "${trade.qty} ${asset.baseAsset}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = SlateTextSecondary,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }
        }

        // Crowding & Elasticity Metrics
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "CROWDING & PAIN ELASTICITY SNAPSHOT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        fontSize = 10.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Predicted 8h Funding", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp))
                        Text("${(asset.predictedFundingPct * 100).let { Math.round(it * 1000.0) / 1000.0 }}%", style = MaterialTheme.typography.labelMedium.copy(color = SlateTextPrimary, fontWeight = FontWeight.Bold))
                    }
                    Column {
                        Text("Basis Premium", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp))
                        Text("${asset.basisPremiumPct}%", style = MaterialTheme.typography.labelMedium.copy(color = SlateTextPrimary, fontWeight = FontWeight.Bold))
                    }
                    Column {
                        Text("ATR (1h / 4h)", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 9.sp))
                        Text("$${asset.atr1h} / $${asset.atr4h}", style = MaterialTheme.typography.labelMedium.copy(color = SlateTextPrimary, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

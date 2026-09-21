package com.chalsmooth.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chalsmooth.app.routing.LagrangianRoutingEngine
import com.chalsmooth.app.ui.theme.*

@Composable
fun NavigationScreen(
    onSwitchToHud: () -> Unit
) {
    var lambda by remember { mutableStateOf(0.5f) }
    var isSimulating by remember { mutableStateOf(false) }

    val comparison = LagrangianRoutingEngine.computeLambdaRoute(lambda)
    val active = comparison.activeCandidate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Turn-by-Turn Instruction Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ElectricCyan,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("↱", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(text = "In 250m", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text(text = "Take Pashan-Sus Resurfaced Bypass", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }

        // Proximity Hazard Alert (Parameter 16)
        if (lambda < 0.6f) {
            Surface(
                color = SevereRed.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SevereRed, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = SevereRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Caution: Severe Crater Ahead!", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("80m in Center Lane • Reduce to ≤20 km/h", color = SevereRed, fontSize = 11.sp)
                    }
                }
            }
        }

        // Bottom Navigation Controller Panel (Parameter 15: Comfort Slider & Trade-off Delta)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Pune Station ➔ Hinjewadi IT Park",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x301E293B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("${active.durationMin} min", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            Text("ETA (${active.distanceKm} km)", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x301E293B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("${active.comfortScore}/100", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SmoothGreen)
                            Text("Ride Comfort", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Trade-off Delta Banner (README spec)
                Surface(
                    color = SmoothGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = SmoothGreen,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (comparison.deltaMin == 0) "FASTEST" else "+${comparison.deltaMin} MIN",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (comparison.deltaMin == 0) "Direct path with 11 pothole impacts" else "${comparison.shockReductionPercent}% fewer road shocks",
                            color = SmoothGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lagrangian Comfort Slider (Parameter 15)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("⚡ Fastest (38m)", fontSize = 11.sp, color = WarningAmber, fontWeight = FontWeight.Bold)
                    Text("λ-sweep trade-off", fontSize = 10.sp, color = TextMuted)
                    Text("🧈 Smoothest (42m)", fontSize = 11.sp, color = SmoothGreen, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = lambda,
                    onValueChange = { lambda = it },
                    colors = SliderDefaults.colors(
                        thumbColor = SmoothGreen,
                        activeTrackColor = SmoothGreen,
                        inactiveTrackColor = WarningAmber
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { isSimulating = !isSimulating },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSimulating) SevereRed else SmoothGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isSimulating) "Stop Navigation" else "Start Comfort Drive", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onSwitchToHud,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("HUD Mode", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

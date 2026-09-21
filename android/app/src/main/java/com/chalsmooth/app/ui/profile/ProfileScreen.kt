package com.chalsmooth.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chalsmooth.app.data.model.Pothole
import com.chalsmooth.app.data.repository.PotholeRepository
import com.chalsmooth.app.ui.theme.*

@Composable
fun ProfileScreen(
    onSelectPothole: (Pothole) -> Unit,
    onOpenReportDialog: () -> Unit
) {
    val potholes by PotholeRepository.potholes.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Parameter 17: User Profile Hero Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(SmoothGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👩‍💻", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Anjali Kulkarni", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Road Guardian 🛡️ • Level 4", fontSize = 12.sp, color = ElectricCyan)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("840 XP", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = WarningAmber)
                            Text("Karma Score", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

        // Achievements / Badges (Parameter 17)
        item {
            Text("🏆 Contributor Badges", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BadgeCard(modifier = Modifier.weight(1f), icon = "🎯", title = "Pothole Hunter", desc = "10+ Validated")
                BadgeCard(modifier = Modifier.weight(1f), icon = "🧈", title = "Smooth Navigator", desc = "50+ Comfort Trips")
                BadgeCard(modifier = Modifier.weight(1f), icon = "📡", title = "Sensor Scout", desc = ">100km IMU")
            }
        }

        // Parameter 18: Report History List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Report History (${potholes.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Button(
                    onClick = onOpenReportDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = SmoothGreen),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("+ New Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(potholes) { ph ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPothole(ph) }
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ph.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("${ph.lane} • Depth: ${ph.depthCm}cm", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(ph.address, fontSize = 11.sp, color = TextMuted)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            color = Color(ph.severity.colorHex).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = ph.severity.label,
                                color = Color(ph.severity.colorHex),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ph.status.label,
                            fontSize = 10.sp,
                            color = if (ph.status.name == "FIXED") SmoothGreen else WarningAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeCard(modifier: Modifier = Modifier, icon: String, title: String, desc: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = modifier.border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(desc, fontSize = 9.sp, color = TextMuted)
        }
    }
}

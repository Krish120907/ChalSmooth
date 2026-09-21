package com.chalsmooth.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chalsmooth.app.data.model.Pothole
import com.chalsmooth.app.data.repository.PotholeRepository
import com.chalsmooth.app.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToRouting: () -> Unit,
    onNavigateToHud: () -> Unit,
    onOpenReportDialog: () -> Unit,
    onSelectPothole: (Pothole) -> Unit
) {
    val potholes by PotholeRepository.potholes.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Section (Parameter 1: Home Dashboard)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Surface(
                        color = SmoothGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.border(1.dp, SmoothGreen.copy(alpha = 0.4f), RoundedCornerShape(50))
                    ) {
                        Text(
                            text = "AI COMFORT NAVIGATION",
                            color = SmoothGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Navigate Smoothly.\nAvoid Craters.",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        lineHeight = 30.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Using 50Hz smartphone IMU sensor vibrations to calculate the smoothest routes in Pune & Mumbai.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToRouting,
                            colors = ButtonDefaults.buttonColors(containerColor = SmoothGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Navigate", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onOpenReportDialog,
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(SevereRed, DangerOrange))),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = SevereRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Report", color = SevereRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Stats Row (Parameter 1)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), title = "Monitored", value = "4,850 km", iconColor = ElectricCyan)
                StatCard(modifier = Modifier.weight(1f), title = "Active Hazards", value = "${potholes.size}", iconColor = SevereRed)
                StatCard(modifier = Modifier.weight(1f), title = "Repaired", value = "128", iconColor = SmoothGreen)
            }
        }

        // Road Condition Corridors (Parameter 10)
        item {
            Text(
                text = "Key Road Condition Corridors",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoadConditionRow(name = "Western Bypass Expressway", score = 96, condition = "Good", color = SmoothGreen)
                RoadConditionRow(name = "FC Road & Shivajinagar", score = 74, condition = "Average", color = WarningAmber)
                RoadConditionRow(name = "Ganeshkhind Univ Circle", score = 42, condition = "Dangerous", color = SevereRed)
            }
        }

        // Nearby Potholes Feed (Parameter 1)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby Pothole Hazards",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "View Map ➔",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.clickable { onNavigateToMap() }
                )
            }
        }

        items(potholes) { pothole ->
            PotholeListItem(pothole = pothole, onClick = { onSelectPothole(pothole) })
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, iconColor: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = modifier.border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = iconColor)
            Text(text = title, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun RoadConditionRow(name: String, score: Int, condition: String, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "$condition ($score/100)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = score / 100f,
                color = color,
                trackColor = Color(0x20FFFFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )
        }
    }
}

@Composable
fun PotholeListItem(pothole: Pothole, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(pothole.severity.colorHex))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pothole.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = pothole.address, fontSize = 12.sp, color = TextMuted)
            }
            Surface(
                color = Color(pothole.severity.colorHex).copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = pothole.severity.label,
                    color = Color(pothole.severity.colorHex),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

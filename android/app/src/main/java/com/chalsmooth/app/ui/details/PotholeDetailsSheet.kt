package com.chalsmooth.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chalsmooth.app.data.model.Pothole
import com.chalsmooth.app.data.model.PotholeStatus
import com.chalsmooth.app.data.repository.PotholeRepository
import com.chalsmooth.app.ui.theme.*

@Composable
fun PotholeDetailsSheet(
    pothole: Pothole,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGlass, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(pothole.severity.colorHex).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = pothole.severity.label,
                        color = Color(pothole.severity.colorHex),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("✕ Close", color = TextSecondary)
                }
            }

            Text(
                text = pothole.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "📍 ${pothole.address}",
                fontSize = 13.sp,
                color = TextSecondary
            )

            // Parameter 14: 4-Step Status Tracker
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x301E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Civic Status Lifecycle", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusStepBadge(title = "Reported", isReached = true)
                        StatusStepBadge(title = "Verified", isReached = pothole.status.stepIndex >= 2)
                        StatusStepBadge(title = "In Progress", isReached = pothole.status.stepIndex >= 3)
                        StatusStepBadge(title = "Fixed", isReached = pothole.status.stepIndex >= 4)
                    }
                }
            }

            // Description
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x301E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Hazard Details", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(pothole.description, fontSize = 13.sp, color = TextSecondary)
                }
            }

            // Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0x301E293B))) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${pothole.depthCm} cm", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Est Depth", fontSize = 10.sp, color = TextMuted)
                    }
                }
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0x301E293B))) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${pothole.sensorPeakJerk} m/s³", fontWeight = FontWeight.Bold, color = SevereRed)
                        Text("Peak Jerk Shock", fontSize = 10.sp, color = TextMuted)
                    }
                }
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0x301E293B))) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${pothole.passesCount}", fontWeight = FontWeight.Bold, color = ElectricCyan)
                        Text("IMU Passes", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }

            // Status Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        PotholeRepository.updateStatus(pothole.id, PotholeStatus.VERIFIED)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Verify", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        PotholeRepository.updateStatus(pothole.id, PotholeStatus.FIXED)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SmoothGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mark Fixed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatusStepBadge(title: String, isReached: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (isReached) SmoothGreen else Color(0x30FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (isReached) {
                Text("✓", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(title, fontSize = 9.sp, color = if (isReached) TextPrimary else TextMuted)
    }
}

package com.chalsmooth.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chalsmooth.app.sensor.ImuSensingService
import com.chalsmooth.app.ui.theme.*

@Composable
fun SensorStudioScreen() {
    val telemetry by ImuSensingService.liveTelemetry.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "📡 50Hz IMU Sensor & ML Studio",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Live Acceleration Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryBox(modifier = Modifier.weight(1f), label = "Accel Z (Vertical)", value = String.format("%.2f m/s²", telemetry.az), color = SmoothGreen)
            TelemetryBox(modifier = Modifier.weight(1f), label = "Accel Y (Forward)", value = String.format("%.2f m/s²", telemetry.ay), color = ElectricCyan)
            TelemetryBox(modifier = Modifier.weight(1f), label = "Accel X (Lateral)", value = String.format("%.2f m/s²", telemetry.ax), color = WarningAmber)
        }

        // Real-Time Shock / Jerk Gauge
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Vertical Jerk Shock Magnitude", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = String.format("%.2f m/s³", telemetry.verticalJerk),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (telemetry.verticalJerk > 15f) SevereRed else SmoothGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = (telemetry.verticalJerk / 50f).coerceIn(0f, 1f),
                    color = if (telemetry.verticalJerk > 15f) SevereRed else SmoothGreen,
                    trackColor = Color(0x20FFFFFF),
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }
        }

        // ML Classification Output
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ML Classifier Output (128-sample Window @ 50Hz)", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (telemetry.detectedAnomaly.contains("Pothole")) SevereRed.copy(alpha = 0.2f) else SmoothGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Class: ${telemetry.detectedAnomaly} (Confidence: 94%)",
                        color = if (telemetry.detectedAnomaly.contains("Pothole")) SevereRed else SmoothGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryBox(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 9.sp, color = TextMuted)
        }
    }
}

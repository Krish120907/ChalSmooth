package com.chalsmooth.app.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chalsmooth.app.ui.theme.*

@Composable
fun HudScreen(
    onExitHud: () -> Unit,
    onQuickMarkPothole: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040711))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top HUD Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🛡️ CHALSMOOTH HUD", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(
                onClick = onExitHud,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x30FFFFFF)),
                shape = RoundedCornerShape(50)
            ) {
                Text("Exit HUD", fontSize = 12.sp)
            }
        }

        // Center Speedometer & Roughness Meter
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "48",
                fontSize = 110.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 110.sp
            )
            Text("KM / H", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 2.sp)

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = SmoothGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.border(1.dp, SmoothGreen.copy(alpha = 0.4f), RoundedCornerShape(50))
            ) {
                Text(
                    text = "🟢 Smooth Asphalt Ahead",
                    color = SmoothGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Upcoming Hazard Banner
        Surface(
            color = SevereRed.copy(alpha = 0.2f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, SevereRed, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = SevereRed, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Caution: Deep Crater Ahead", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("120m ahead in Center Lane • Reduce Speed", color = SevereRed, fontSize = 12.sp)
                }
            }
        }

        // Bottom One-Tap Instant Pothole Mark Button
        Button(
            onClick = onQuickMarkPothole,
            colors = ButtonDefaults.buttonColors(containerColor = SevereRed),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("🔴 ONE-TAP MARK POTHOLE", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

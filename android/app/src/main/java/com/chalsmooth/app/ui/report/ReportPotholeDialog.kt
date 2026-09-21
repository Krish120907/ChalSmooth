package com.chalsmooth.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import com.chalsmooth.app.data.model.Pothole
import com.chalsmooth.app.data.model.PotholeStatus
import com.chalsmooth.app.data.model.Severity
import com.chalsmooth.app.data.repository.PotholeRepository
import com.chalsmooth.app.ui.theme.*
import java.util.UUID

@Composable
fun ReportPotholeDialog(
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    var title by remember { mutableStateOf("Deep crater on main road") }
    var severity by remember { mutableStateOf(Severity.MEDIUM) }
    var lane by remember { mutableStateOf("Center Lane") }
    var depth by remember { mutableStateOf("8.5 cm") }
    var description by remember { mutableStateOf("Severe impact for two-wheelers and sedans.") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "📸 Report Pothole Hazard",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Parameter 8: Severity Selection
                Text("Select Severity", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Severity.values().forEach { sev ->
                        val isSelected = severity == sev
                        Surface(
                            color = if (isSelected) Color(sev.colorHex).copy(alpha = 0.25f) else Color(0x20FFFFFF),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { severity = sev }
                                .border(
                                    1.dp,
                                    if (isSelected) Color(sev.colorHex) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(sev.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(sev.colorHex))
                            }
                        }
                    }
                }

                // Parameter 7: GPS Location
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x301E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("📍 Current Location Tag", fontSize = 11.sp, color = TextMuted)
                        Text("Senapati Bapat Rd, Shivajinagar, Pune", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("GPS: 18.5284, 73.8320 (±3.2m)", fontSize = 10.sp, color = ElectricCyan)
                    }
                }

                // Parameter 9: Details
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Pothole Title / Landmark") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Lane notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newPothole = Pothole(
                                id = "ph-${UUID.randomUUID().toString().take(6)}",
                                title = title,
                                description = description,
                                severity = severity,
                                depthCm = 8.5,
                                widthCm = 60.0,
                                lat = 18.5284,
                                lng = 73.8320,
                                address = "Senapati Bapat Rd, Pune",
                                roadName = "Senapati Bapat Road",
                                lane = lane,
                                status = PotholeStatus.REPORTED,
                                reportedDate = "Today",
                                photoUrl = "https://images.unsplash.com/photo-1515162816999-a0c47dc192f7?auto=format&fit=crop&w=600&q=80"
                            )
                            PotholeRepository.addPothole(newPothole)
                            onSubmitSuccess()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SmoothGreen)
                    ) {
                        Text("Submit (+50 XP)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

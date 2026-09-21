package com.chalsmooth.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
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
import com.chalsmooth.app.data.model.Severity
import com.chalsmooth.app.data.repository.PotholeRepository
import com.chalsmooth.app.ui.theme.*

@Composable
fun MapScreen(
    onSelectPothole: (Pothole) -> Unit,
    onOpenReportDialog: () -> Unit
) {
    val potholes by PotholeRepository.potholes.collectAsState()
    var selectedSeverityFilter by remember { mutableStateOf<Severity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPotholes = potholes.filter {
        (selectedSeverityFilter == null || it.severity == selectedSeverityFilter) &&
        (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.address.contains(searchQuery, ignoreCase = true))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Parameter 11: Floating Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search road or area (e.g. Baner, FC Rd)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard,
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = BorderGlass
                )
            )

            // Parameter 12: Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (selectedSeverityFilter == null) SmoothGreen.copy(alpha = 0.2f) else BgCard,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clickable { selectedSeverityFilter = null }
                        .border(1.dp, if (selectedSeverityFilter == null) SmoothGreen else BorderGlass, RoundedCornerShape(50))
                ) {
                    Text("All", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }

                Severity.values().forEach { sev ->
                    val isSel = selectedSeverityFilter == sev
                    Surface(
                        color = if (isSel) Color(sev.colorHex).copy(alpha = 0.25f) else BgCard,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clickable { selectedSeverityFilter = if (isSel) null else sev }
                            .border(1.dp, if (isSel) Color(sev.colorHex) else BorderGlass, RoundedCornerShape(50))
                    ) {
                        Text(
                            sev.label,
                            color = Color(sev.colorHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Interactive Map Visual & List (Parameter 2, 3, 4)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C1220)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗺️ OpenStreetMap / CartoDB Dark Active", color = TextMuted, fontSize = 12.sp)

                    // Current Location Puck (Parameter 4)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                            .border(2.dp, Color.White, CircleShape)
                    )

                    // Floating GPS Recenter Button
                    IconButton(
                        onClick = { /* Recenter */ },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(BgCard, CircleShape)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = ElectricCyan)
                    }
                }
            }

            // Hazard Pins on Screen
            Text(
                text = "Mapped Pothole Markers (${filteredPotholes.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredPotholes) { ph ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPothole(ph) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(ph.severity.colorHex))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ph.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(ph.address, fontSize = 11.sp, color = TextMuted)
                            }
                            Text(
                                "${ph.depthCm}cm",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(ph.severity.colorHex)
                            )
                        }
                    }
                }
            }
        }
    }
}

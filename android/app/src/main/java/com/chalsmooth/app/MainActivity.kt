package com.chalsmooth.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chalsmooth.app.data.model.Pothole
import com.chalsmooth.app.sensor.ImuSensingService
import com.chalsmooth.app.ui.dashboard.DashboardScreen
import com.chalsmooth.app.ui.details.PotholeDetailsSheet
import com.chalsmooth.app.ui.hud.HudScreen
import com.chalsmooth.app.ui.map.MapScreen
import com.chalsmooth.app.ui.navigation.NavigationScreen
import com.chalsmooth.app.ui.profile.ProfileScreen
import com.chalsmooth.app.ui.report.ReportPotholeDialog
import com.chalsmooth.app.ui.studio.SensorStudioScreen
import com.chalsmooth.app.ui.theme.BgDark
import com.chalsmooth.app.ui.theme.BgCard
import com.chalsmooth.app.ui.theme.ChalSmoothTheme
import com.chalsmooth.app.ui.theme.SmoothGreen

enum class Screen(val title: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Home),
    MAP("Map", Icons.Default.Place),
    NAVIGATION("Routing", Icons.Default.Navigation),
    STUDIO("Sensors", Icons.Default.Sensors),
    HUD("In-Car HUD", Icons.Default.Speed),
    PROFILE("Profile", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start background 50Hz IMU sensing service
        try {
            val serviceIntent = Intent(this, ImuSensingService::class.java)
            startService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            ChalSmoothTheme {
                ChalSmoothApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChalSmoothApp() {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var selectedPothole by remember { mutableStateOf<Pothole?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (currentScreen != Screen.HUD) {
                NavigationBar(
                    containerColor = BgCard,
                    tonalElevation = 8.dp
                ) {
                    Screen.values().filter { it != Screen.HUD }.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SmoothGreen,
                                selectedTextColor = SmoothGreen,
                                indicatorColor = SmoothGreen.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                Screen.DASHBOARD -> DashboardScreen(
                    onNavigateToMap = { currentScreen = Screen.MAP },
                    onNavigateToRouting = { currentScreen = Screen.NAVIGATION },
                    onNavigateToHud = { currentScreen = Screen.HUD },
                    onOpenReportDialog = { showReportDialog = true },
                    onSelectPothole = { selectedPothole = it }
                )
                Screen.MAP -> MapScreen(
                    onSelectPothole = { selectedPothole = it },
                    onOpenReportDialog = { showReportDialog = true }
                )
                Screen.NAVIGATION -> NavigationScreen(
                    onSwitchToHud = { currentScreen = Screen.HUD }
                )
                Screen.STUDIO -> SensorStudioScreen()
                Screen.HUD -> HudScreen(
                    onExitHud = { currentScreen = Screen.DASHBOARD },
                    onQuickMarkPothole = { showReportDialog = true }
                )
                Screen.PROFILE -> ProfileScreen(
                    onSelectPothole = { selectedPothole = it },
                    onOpenReportDialog = { showReportDialog = true }
                )
            }

            // Report Dialog
            if (showReportDialog) {
                ReportPotholeDialog(
                    onDismiss = { showReportDialog = false },
                    onSubmitSuccess = { showReportDialog = false }
                )
            }

            // Pothole Details Sheet
            selectedPothole?.let { pothole ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter
                ) {
                    PotholeDetailsSheet(
                        pothole = pothole,
                        onDismiss = { selectedPothole = null }
                    )
                }
            }
        }
    }
}

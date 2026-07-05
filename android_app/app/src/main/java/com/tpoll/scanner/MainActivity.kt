package com.tpoll.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.tpoll.scanner.ui.screens.DashboardScreen
import com.tpoll.scanner.ui.screens.HealthScreen
import com.tpoll.scanner.ui.screens.HistoryScreen
import com.tpoll.scanner.ui.screens.SettingsScreen
import com.tpoll.scanner.ui.theme.TPollScannerTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkAndStartScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        requestRequiredPermissions()

        setContent {
            TPollScannerTheme {
                MainScreen()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            checkAndStartScan()
        }
    }

    private fun checkAndStartScan() {
        val prefs = getSharedPreferences("scan_settings", MODE_PRIVATE)
        val autoScanEnabled = prefs.getBoolean("auto_scan_enabled", true)

        if (autoScanEnabled) {
            BootReceiver.schedulePeriodicScan(this)
        }
    }
}

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Painel", Icons.Default.Home),
    History("history", "Histórico", Icons.Default.History),
    Health("health", "Saúde", Icons.Default.Favorite),
    Settings("settings", "Configurações", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    val screens = Screen.entries

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            Screen.Dashboard -> "TPoll Scanner"
                            Screen.History -> "Histórico"
                            Screen.Health -> "Saúde do dispositivo"
                            Screen.Settings -> "Configurações"
                        }
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentScreen.route == screen.route,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues)
        ) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    onNavigateToHistory = { currentScreen = Screen.History },
                    onNavigateToHealth = { currentScreen = Screen.Health }
                )
                Screen.History -> HistoryScreen()
                Screen.Health -> HealthScreen()
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}

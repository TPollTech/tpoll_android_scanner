package com.tpoll.scanner.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpoll.scanner.ScanService
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.ui.theme.*

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("scan_results", Context.MODE_PRIVATE)
    val settingsPrefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)

    var isScanning by remember { mutableStateOf(ScanService.isScanRunning()) }
    var lastScanTime by remember { mutableStateOf(prefs.getLong("last_scan_time", 0L)) }
    var lastTotal by remember { mutableStateOf(prefs.getInt("last_scan_total", 0)) }
    var lastHigh by remember { mutableStateOf(prefs.getInt("last_scan_high", 0)) }
    var lastMedium by remember { mutableStateOf(prefs.getInt("last_scan_medium", 0)) }
    var lastRemoved by remember { mutableStateOf(prefs.getInt("last_scan_removed", 0)) }
    var autoScanEnabled by remember { mutableStateOf(settingsPrefs.getBoolean("auto_scan_enabled", true)) }

    val protectionPrefs = context.getSharedPreferences("protection_status", Context.MODE_PRIVATE)
    var shieldActive by remember { mutableStateOf(ShieldService.isRunning()) }
    var shieldThreats by remember { mutableStateOf(protectionPrefs.getInt("threat_count", 0)) }
    var malwareCount by remember { mutableStateOf(protectionPrefs.getInt("malware_count", 0)) }

    LaunchedEffect(shieldActive) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            shieldActive = ShieldService.isRunning()
            shieldThreats = protectionPrefs.getInt("threat_count", 0)
            malwareCount = protectionPrefs.getInt("malware_count", 0)
        }
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            while (isScanning) {
                kotlinx.coroutines.delay(1000)
                isScanning = ScanService.isScanRunning()
                lastScanTime = prefs.getLong("last_scan_time", 0L)
                lastTotal = prefs.getInt("last_scan_total", 0)
                lastHigh = prefs.getInt("last_scan_high", 0)
                lastMedium = prefs.getInt("last_scan_medium", 0)
                lastRemoved = prefs.getInt("last_scan_removed", 0)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TPoll Scanner",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Proteção automática contra apps maliciosos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Escaneando apps...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (shieldActive && shieldThreats == 0)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else if (shieldActive)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (shieldActive && shieldThreats == 0) GreenColor
                    else if (shieldActive && malwareCount > 0) HighRiskColor
                    else if (shieldActive) MediumRiskColor
                    else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (shieldActive) "Proteção em tempo real ativa" else "Proteção desativada",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            malwareCount > 0 -> "$malwareCount malware(s) detectado(s)! Toque nas Configurações"
                            shieldThreats > 0 -> "$shieldThreats app(s) suspeito(s) encontrado(s)"
                            else -> "Nenhuma ameaça encontrada"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Apps,
                label = "Analisados",
                value = lastTotal.toString(),
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                label = "Alto Risco",
                value = lastHigh.toString(),
                color = HighRiskColor
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Error,
                label = "Médio Risco",
                value = lastMedium.toString(),
                color = MediumRiskColor
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Último scan",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (lastScanTime > 0) {
                                java.text.SimpleDateFormat(
                                    "dd/MM/yyyy HH:mm",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(lastScanTime))
                            } else "Nunca",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (lastRemoved > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("$lastRemoved removidos") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Scan automático",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "A cada ${settingsPrefs.getInt("scan_interval_hours", 6)}h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = autoScanEnabled,
                    onCheckedChange = { enabled ->
                        autoScanEnabled = enabled
                        settingsPrefs.edit().putBoolean("auto_scan_enabled", enabled).apply()
                        if (enabled) {
                            com.tpoll.scanner.BootReceiver.schedulePeriodicScan(context)
                        } else {
                            com.tpoll.scanner.BootReceiver.cancelPeriodicScan(context)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                ScanService.startScan(context)
                isScanning = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isScanning,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isScanning)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Sync else Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isScanning) "Escaneando..." else "Iniciar varredura",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

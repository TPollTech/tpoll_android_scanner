package com.tpoll.scanner.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpoll.scanner.ScanService
import com.tpoll.scanner.TPollApp
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.ui.theme.*

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    onNavigateToQuarantine: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {}
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

    var isShieldScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = shieldThreats > 0) { onNavigateToHistory() },
            colors = CardDefaults.cardColors(
                containerColor = when {
                    !shieldActive -> MaterialTheme.colorScheme.surfaceVariant
                    shieldThreats == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    malwareCount > 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (!shieldActive) Color.Gray
                    else if (malwareCount > 0) HighRiskColor
                    else if (shieldThreats > 0) MediumRiskColor
                    else GreenColor,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (shieldActive) "Proteção em tempo real ativa" else "Proteção desativada",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            malwareCount > 0 -> "$malwareCount malware(s) detectado(s)"
                            shieldThreats > 0 -> "$shieldThreats app(s) suspeito(s) - Toque para ver"
                            else -> "Nenhuma ameaça encontrada"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (shieldThreats > 0) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        var quarantineCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            try { quarantineCount = TPollApp.instance.database.quarantineDao().count() } catch (_: Exception) { }
        }

        if (quarantineCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToQuarantine() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = HighRiskColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quarentena",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$quarantineCount app(s) removido(s) - Toque para gerenciar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToPermissions() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permissões dos apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Veja quais apps têm acesso a câmera, microfone, localização e mais",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            val tip = remember { com.tpoll.scanner.DailyTips.getRandomTip(context) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MediumRiskColor,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tip.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tip.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Dica do dia",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToHealth() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saúde do dispositivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toque para analisar bateria, armazenamento, sensores e mais",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
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
                    .padding(12.dp),
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
                .height(48.dp),
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
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isScanning) "Escaneando..." else "Iniciar varredura",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (shieldActive) {
            OutlinedButton(
                onClick = {
                    isShieldScanning = true
                    val intent = Intent(context, ShieldService::class.java).apply {
                        action = ShieldService.ACTION_SCAN_NOW
                    }
                    context.startService(intent)
                    scope.launch {
                        kotlinx.coroutines.delay(5000)
                        isShieldScanning = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isShieldScanning
            ) {
                Icon(
                    imageVector = if (isShieldScanning) Icons.Default.Sync else Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isShieldScanning) "Escaneando ameaças..." else "Escanear ameaças (Shield)",
                    fontSize = 14.sp
                )
            }
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
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
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

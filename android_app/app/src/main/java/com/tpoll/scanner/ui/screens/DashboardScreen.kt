package com.tpoll.scanner.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TPoll Scanner",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Proteção automática contra apps maliciosos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Escaneando apps...",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = shieldThreats > 0) { onNavigateToHistory() },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            !shieldActive -> StatusInactive.copy(alpha = 0.15f)
                            malwareCount > 0 -> ShieldDangerColor.copy(alpha = 0.15f)
                            shieldThreats > 0 -> ShieldWarningColor.copy(alpha = 0.15f)
                            else -> StatusActive.copy(alpha = 0.15f)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (!shieldActive) StatusInactive
                            else if (malwareCount > 0) ShieldDangerColor
                            else if (shieldThreats > 0) ShieldWarningColor
                            else StatusActive,
                            modifier = Modifier.padding(10.dp).size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (shieldActive) "Proteção ativa" else "Proteção desativada",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                malwareCount > 0 -> "$malwareCount malware(s) detectado(s)"
                                shieldThreats > 0 -> "$shieldThreats app(s) suspeito(s)"
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
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = HighRiskColor.copy(alpha = 0.12f)
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                tint = HighRiskColor,
                                modifier = Modifier.padding(10.dp).size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quarentena",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$quarantineCount app(s) removido(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToPermissions() },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Permissões",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Apps com acesso a câmera, localização e mais",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            lineHeight = 16.sp
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToHealth() },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(10.dp).size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Saúde",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bateria, armazenamento, sensores e mais",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                val tip = remember { com.tpoll.scanner.DailyTips.getRandomTip(context) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MediumRiskColor.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MediumRiskColor,
                            modifier = Modifier.padding(10.dp).size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tip.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tip.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Último scan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lastScanTime > 0) {
                                java.text.SimpleDateFormat(
                                    "dd/MM/yyyy HH:mm",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(lastScanTime))
                            } else "Nunca",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (lastRemoved > 0) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = LowRiskColor.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = LowRiskColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$lastRemoved removidos",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LowRiskColor
                                    )
                                }
                            }
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
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    ScanService.startScan(context)
                    isScanning = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isScanning,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
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
                    fontSize = 15.sp,
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isShieldScanning,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (isShieldScanning) Icons.Default.Sync else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isShieldScanning) "Escaneando ameaças..." else "Escanear ameaças (Shield)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

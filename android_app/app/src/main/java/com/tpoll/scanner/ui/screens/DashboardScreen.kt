// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
package com.tpoll.scanner.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpoll.scanner.BootReceiver
import com.tpoll.scanner.DailyTips
import com.tpoll.scanner.ScanService
import com.tpoll.scanner.TPollApp
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.ui.theme.HighRiskColor
import com.tpoll.scanner.ui.theme.LowRiskColor
import com.tpoll.scanner.ui.theme.MediumRiskColor
import com.tpoll.scanner.ui.theme.ShieldDangerColor
import com.tpoll.scanner.ui.theme.ShieldWarningColor
import com.tpoll.scanner.ui.theme.StatusActive
import com.tpoll.scanner.ui.theme.StatusInactive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val protectionPrefs = context.getSharedPreferences("protection_status", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(ScanService.isScanRunning()) }
    var lastScanTime by remember { mutableStateOf(prefs.getLong("last_scan_time", 0L)) }
    var lastTotal by remember { mutableStateOf(prefs.getInt("last_scan_total", 0)) }
    var lastHigh by remember { mutableStateOf(prefs.getInt("last_scan_high", 0)) }
    var lastMedium by remember { mutableStateOf(prefs.getInt("last_scan_medium", 0)) }
    var lastRemoved by remember { mutableStateOf(prefs.getInt("last_scan_removed", 0)) }
    var autoScanEnabled by remember { mutableStateOf(settingsPrefs.getBoolean("auto_scan_enabled", true)) }
    var shieldActive by remember { mutableStateOf(ShieldService.isRunning()) }
    var shieldThreats by remember { mutableStateOf(protectionPrefs.getInt("threat_count", 0)) }
    var malwareCount by remember { mutableStateOf(protectionPrefs.getInt("malware_count", 0)) }
    var isShieldScanning by remember { mutableStateOf(false) }
    var quarantineCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        quarantineCount = runCatching { TPollApp.instance.database.quarantineDao().count() }.getOrDefault(0)
        while (true) {
            delay(5_000)
            shieldActive = ShieldService.isRunning()
            shieldThreats = protectionPrefs.getInt("threat_count", 0)
            malwareCount = protectionPrefs.getInt("malware_count", 0)
        }
    }

    LaunchedEffect(isScanning) {
        while (isScanning) {
            delay(1_000)
            isScanning = ScanService.isScanRunning()
            lastScanTime = prefs.getLong("last_scan_time", 0L)
            lastTotal = prefs.getInt("last_scan_total", 0)
            lastHigh = prefs.getInt("last_scan_high", 0)
            lastMedium = prefs.getInt("last_scan_medium", 0)
            lastRemoved = prefs.getInt("last_scan_removed", 0)
        }
    }

    val statusColor = when {
        !shieldActive -> StatusInactive
        malwareCount > 0 -> ShieldDangerColor
        shieldThreats > 0 || lastHigh > 0 -> ShieldWarningColor
        else -> StatusActive
    }
    val statusTitle = when {
        !shieldActive -> "Proteção desativada"
        malwareCount > 0 -> "Ameaças encontradas"
        shieldThreats > 0 || lastHigh > 0 -> "Revisão recomendada"
        else -> "Celular protegido"
    }
    val statusDescription = when {
        !shieldActive -> "Ative a proteção e faça uma análise completa."
        malwareCount > 0 -> "$malwareCount ameaça(s) precisam de atenção."
        shieldThreats > 0 -> "$shieldThreats app(s) suspeito(s) encontrados."
        lastScanTime == 0L -> "Faça a primeira análise para conferir seus aplicativos."
        else -> "Nenhuma ameaça importante encontrada na última análise."
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = statusColor.copy(alpha = 0.16f)) {
                        Icon(
                            imageVector = if (malwareCount > 0) Icons.Default.Warning else Icons.Default.Shield,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.padding(11.dp).size(30.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(statusTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(statusDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text("Analisando aplicativos...", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    ScanService.startScan(context)
                    isScanning = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isScanning,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(if (isScanning) Icons.Default.Sync else Icons.Default.Security, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Analisando..." else "Analisar agora", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock,
                    title = "Privacidade",
                    subtitle = "Revisar permissões",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToPermissions
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Favorite,
                    title = "Saúde",
                    subtitle = "Bateria e espaço",
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = onNavigateToHealth
                )
            }
        }

        item {
            Text("Resumo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactStat(Modifier.weight(1f), Icons.Default.Apps, "Analisados", lastTotal.toString(), MaterialTheme.colorScheme.primary)
                CompactStat(Modifier.weight(1f), Icons.Default.Warning, "Alto risco", lastHigh.toString(), HighRiskColor)
                CompactStat(Modifier.weight(1f), Icons.Default.Error, "Atenção", lastMedium.toString(), MediumRiskColor)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = shieldThreats > 0) { onNavigateToHistory() },
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = statusColor, modifier = Modifier.size(26.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (shieldActive) "Proteção em tempo real" else "Proteção em pausa", fontWeight = FontWeight.Bold)
                        Text(
                            if (shieldActive) "Monitoramento de novos aplicativos ativo" else "Ative a proteção nas configurações",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (shieldThreats > 0) Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        if (quarantineCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToQuarantine),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = HighRiskColor)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quarentena", fontWeight = FontWeight.Bold)
                            Text("$quarantineCount item(ns) isolado(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Análise automática", fontWeight = FontWeight.Bold)
                        Text(
                            if (lastScanTime > 0) "Última: ${formatScanDate(lastScanTime)}" else "Nenhuma análise concluída",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lastRemoved > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$lastRemoved item(ns) removido(s)", style = MaterialTheme.typography.labelSmall, color = LowRiskColor)
                        }
                    }
                    Switch(
                        checked = autoScanEnabled,
                        onCheckedChange = { enabled ->
                            autoScanEnabled = enabled
                            settingsPrefs.edit().putBoolean("auto_scan_enabled", enabled).apply()
                            if (enabled) BootReceiver.schedulePeriodicScan(context) else BootReceiver.cancelPeriodicScan(context)
                        }
                    )
                }
            }
        }

        if (shieldActive) {
            item {
                OutlinedButton(
                    onClick = {
                        isShieldScanning = true
                        context.startService(Intent(context, ShieldService::class.java).apply { action = ShieldService.ACTION_SCAN_NOW })
                        scope.launch {
                            delay(5_000)
                            isShieldScanning = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isShieldScanning,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(if (isShieldScanning) Icons.Default.Sync else Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isShieldScanning) "Verificando..." else "Verificação rápida")
                }
            }
        }

        item {
            val tip = remember { DailyTips.getRandomTip(context) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MediumRiskColor)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tip.title, fontWeight = FontWeight.Bold)
                        Text(tip.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
            Surface(shape = RoundedCornerShape(11.dp), color = color.copy(alpha = 0.12f)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp).size(22.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactStat(modifier: Modifier, icon: ImageVector, label: String, value: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(5.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatScanDate(time: Long): String {
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(time))
}

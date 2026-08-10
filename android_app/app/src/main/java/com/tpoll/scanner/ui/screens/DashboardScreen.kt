// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
package com.tpoll.scanner.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpoll.scanner.BootReceiver
import com.tpoll.scanner.DailyTips
import com.tpoll.scanner.ScanService
import com.tpoll.scanner.TPollApp
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.ui.theme.HighRiskColor
import com.tpoll.scanner.ui.theme.LocalExtendedColors
import com.tpoll.scanner.ui.theme.LowRiskColor
import com.tpoll.scanner.ui.theme.MediumRiskColor
import com.tpoll.scanner.updater.UpdateChecker
import com.tpoll.scanner.updater.UpdateDialog
import com.tpoll.scanner.updater.UpdatePhase
import com.tpoll.scanner.updater.UpdateResult
import com.tpoll.scanner.updater.UpdateScheduler
import com.tpoll.scanner.updater.UpdateStateStore
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
    val extended = LocalExtendedColors.current
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
    var showUpdateDialog by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val lastSeenVersion = prefs.getInt("last_seen_version_code", 0)
        val storedState = UpdateStateStore.read(context)
        val storedUpdateAvailable = storedState.versionCode > lastSeenVersion &&
            storedState.phase in setOf(
                UpdatePhase.AVAILABLE,
                UpdatePhase.WAITING_FOR_WIFI,
                UpdatePhase.DOWNLOADING,
                UpdatePhase.PERMISSION_REQUIRED,
                UpdatePhase.CONFIRMATION_REQUIRED
            )
        if (storedUpdateAvailable) {
            showUpdateDialog = true
        } else if (
            UpdateScheduler.isAutomaticUpdatesEnabled(context) &&
            UpdateChecker.shouldCheck(context)
        ) {
            val result = UpdateChecker(context.applicationContext).checkForUpdates()
            if (result is UpdateResult.Available && result.info.version_code > lastSeenVersion) {
                UpdateStateStore.write(
                    context = context,
                    phase = UpdatePhase.AVAILABLE,
                    versionCode = result.info.version_code,
                    versionName = result.info.version_name
                )
                showUpdateDialog = true
            }
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
        !shieldActive -> extended.shieldInactive
        malwareCount > 0 -> extended.shieldDanger
        shieldThreats > 0 || lastHigh > 0 -> extended.shieldWarning
        else -> extended.shieldActive
    }
    val statusGradient = when {
        !shieldActive -> Brush.linearGradient(listOf(Color(0xFF37474F), Color(0xFF546E7A)))
        malwareCount > 0 -> extended.gradientDanger
        shieldThreats > 0 || lastHigh > 0 -> Brush.linearGradient(listOf(Color(0xFFFF8F00), Color(0xFFFFAB00)))
        else -> extended.gradientSuccess
    }
    val statusTitle = when {
        !shieldActive -> "Proteção desativada"
        malwareCount > 0 -> "Ameaças detectadas"
        shieldThreats > 0 || lastHigh > 0 -> "Revisão recomendada"
        else -> "Celular protegido"
    }
    val statusDescription = when {
        !shieldActive -> "Ative a proteção para monitorar seu dispositivo."
        malwareCount > 0 -> "$malwareCount ameaça(s) precisa(m) de ação imediata."
        shieldThreats > 0 -> "$shieldThreats app(s) suspeito(s) encontrados."
        lastScanTime == 0L -> "Faça a primeira análise para verificar seus apps."
        else -> "Nenhuma ameaça encontrada na última verificação."
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroStatusCard(
                gradient = statusGradient,
                statusColor = statusColor,
                title = statusTitle,
                description = statusDescription,
                isScanning = isScanning,
                shieldActive = shieldActive
            )
        }

        item {
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ScanProgressIndicator()
                        Column {
                            Text(
                                "Analisando aplicativos...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Verificando permissões e comportamentos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isScanning,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    if (isScanning) Icons.Default.Sync else Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    if (isScanning) "Analisando..." else "Iniciar análise completa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock,
                    title = "Privacidade",
                    subtitle = "Permissões",
                    gradient = extended.gradientPrimary,
                    onClick = onNavigateToPermissions
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Favorite,
                    title = "Saúde",
                    subtitle = "Dispositivo",
                    gradient = Brush.linearGradient(listOf(Color(0xFFFF8F00), Color(0xFFFFAB00))),
                    onClick = onNavigateToHealth
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GradientStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Apps,
                    label = "Analisados",
                    value = lastTotal.toString(),
                    gradient = extended.gradientPrimary
                )
                GradientStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Warning,
                    label = "Alto risco",
                    value = lastHigh.toString(),
                    gradient = extended.gradientDanger
                )
                GradientStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Error,
                    label = "Atenção",
                    value = lastMedium.toString(),
                    gradient = Brush.linearGradient(listOf(Color(0xFFFF8F00), Color(0xFFFFAB00)))
                )
            }
        }

        item {
            Text(
                "Monitoramento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            MonitorCard(
                icon = Icons.Default.VerifiedUser,
                title = "Proteção em tempo real",
                subtitle = if (shieldActive) "Monitoramento ativo" else "Desativado",
                isActive = shieldActive,
                accentColor = if (shieldActive) extended.shieldActive else extended.shieldInactive,
                onClick = if (shieldThreats > 0) onNavigateToHistory else null
            )
        }

        if (quarantineCount > 0) {
            item {
                MonitorCard(
                    icon = Icons.Default.Block,
                    title = "Quarentena",
                    subtitle = "$quarantineCount app(s) isolado(s)",
                    isActive = false,
                    accentColor = HighRiskColor,
                    onClick = onNavigateToQuarantine
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Análise automática",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (lastScanTime > 0) "Última: ${formatScanDate(lastScanTime)}" else "Nenhuma análise ainda",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoScanEnabled,
                            onCheckedChange = { enabled ->
                                autoScanEnabled = enabled
                                settingsPrefs.edit().putBoolean("auto_scan_enabled", enabled).apply()
                                if (enabled) BootReceiver.schedulePeriodicScan(context) else BootReceiver.cancelPeriodicScan(context)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    if (lastRemoved > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = LowRiskColor.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = LowRiskColor
                                )
                                Text(
                                    "$lastRemoved ameaça(s) removida(s) automaticamente",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LowRiskColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isShieldScanning,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        if (isShieldScanning) Icons.Default.Sync else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isShieldScanning) "Verificando..." else "Verificação rápida do Shield")
                }
            }
        }

        item {
            val tip = remember { DailyTips.getRandomTip(context) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MediumRiskColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MediumRiskColor,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tip.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            tip.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showUpdateDialog) {
        UpdateDialog(onDismiss = { seenVersionCode ->
            showUpdateDialog = false
            if (seenVersionCode > 0) {
                context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("last_seen_version_code", seenVersionCode)
                    .apply()
            }
        })
    }
}

@Composable
private fun HeroStatusCard(
    gradient: Brush,
    statusColor: Color,
    title: String,
    description: String,
    isScanning: Boolean,
    shieldActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = Color.White,
                            strokeWidth = 3.dp,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        Icon(
                            imageVector = if (shieldActive) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanProgressIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_progress"
    )

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            strokeWidth = 3.dp,
            strokeCap = StrokeCap.Round
        )
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradient, alpha = 0.08f)
                .padding(16.dp)
        ) {
            Column {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GradientStatCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    gradient: Brush
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradient, alpha = 0.06f)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun MonitorCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(8.dp)
            ) {}
            if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatScanDate(time: Long): String {
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(time))
}

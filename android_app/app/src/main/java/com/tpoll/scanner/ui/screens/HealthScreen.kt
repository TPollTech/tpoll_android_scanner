package com.tpoll.scanner.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpoll.scanner.health.*
import com.tpoll.scanner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HealthScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<DeviceHealthReport?>(null) }
    var isChecking by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isChecking = true
        report = withContext(Dispatchers.Default) {
            DeviceHealthChecker(context).checkAll()
        }
        isChecking = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isChecking) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analisando dispositivos...")
                }
            }
        } else if (report != null) {
            val r = report!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OverallHealthCard(r)
                }

                item {
                    Text(
                        text = "Componentes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item { BatteryCard(r.battery) }
                item { StorageCard("Armazenamento interno", r.internalStorage) }
                if (r.externalStorage != null) {
                    item { StorageCard("Armazenamento externo", r.externalStorage) }
                }
                item { MemoryCard(r.memory) }
                item { CpuCard(r.cpu) }
                item { NetworkCard(r.network) }
                item { BluetoothCard(r.bluetooth) }
                item { ScreenCard(r.screenInfo) }
                item { UptimeCard(r.uptimeDays) }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sensores",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(r.sensors.filter { it.isPresent }) { sensor ->
                    SensorCard(sensor)
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isChecking = true
                                report = withContext(Dispatchers.Default) {
                                    DeviceHealthChecker(context).checkAll()
                                }
                                isChecking = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isChecking
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reanalisar")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun OverallHealthCard(report: DeviceHealthReport) {
    val (statusColor, statusText) = when (report.overallStatus) {
        HealthStatus.GOOD -> GreenColor to "Saudável"
        HealthStatus.WARNING -> MediumRiskColor to "Atenção"
        HealthStatus.CRITICAL -> HighRiskColor to "Crítico"
        HealthStatus.UNKNOWN -> Color.Gray to "Desconhecido"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = when (report.overallStatus) {
                    HealthStatus.GOOD -> Icons.Default.VerifiedUser
                    HealthStatus.WARNING -> Icons.Default.Warning
                    HealthStatus.CRITICAL -> Icons.Default.Error
                    HealthStatus.UNKNOWN -> Icons.Default.Help
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${report.healthyCount} saudável / ${report.warningCount} atenção / ${report.criticalCount} crítico",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun HealthRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    status: HealthStatus
) {
    val statusColor = when (status) {
        HealthStatus.GOOD -> GreenColor
        HealthStatus.WARNING -> MediumRiskColor
        HealthStatus.CRITICAL -> HighRiskColor
        HealthStatus.UNKNOWN -> Color.Gray
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(
                imageVector = when (status) {
                    HealthStatus.GOOD -> Icons.Default.CheckCircle
                    HealthStatus.WARNING -> Icons.Default.Warning
                    HealthStatus.CRITICAL -> Icons.Default.Error
                    HealthStatus.UNKNOWN -> Icons.Default.Help
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun BatteryCard(battery: BatteryHealth) {
    HealthRow(
        icon = { Icon(Icons.Default.BatteryFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "Bateria",
        value = "${battery.level}% - ${battery.temperature}°C - ${if (battery.isCharging) "Carregando" else "Descarregando"} (${battery.health})",
        status = battery.status
    )
}

@Composable
private fun StorageCard(label: String, storage: StorageHealth) {
    HealthRow(
        icon = { Icon(Icons.Default.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = label,
        value = "${storage.usedFormatted} usados de ${storage.totalFormatted} (${storage.usedPercent}%)",
        status = storage.status
    )
}

@Composable
private fun MemoryCard(memory: MemoryHealth) {
    HealthRow(
        icon = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "RAM",
        value = "${memory.usedFormatted} usados de ${memory.totalFormatted} (${memory.usedPercent}%)",
        status = memory.status
    )
}

@Composable
private fun CpuCard(cpu: CpuHealth) {
    val tempText = if (cpu.temperature != null) " - ${cpu.temperature}°C" else ""
    HealthRow(
        icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "CPU",
        value = "Uso: ${cpu.usagePercent}%$tempText",
        status = cpu.status
    )
}

@Composable
private fun NetworkCard(network: NetworkHealth) {
    val detail = buildString {
        append(if (network.isConnected) "Conectado" else "Desconectado")
        if (network.isWifiEnabled) append(" (WiFi)")
        if (network.isMobileDataEnabled) append(" (Móvel)")
    }
    HealthRow(
        icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "Rede",
        value = detail,
        status = network.status
    )
}

@Composable
private fun BluetoothCard(bt: BluetoothHealth) {
    HealthRow(
        icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "Bluetooth",
        value = if (bt.isEnabled) "Ligado" else "Desligado",
        status = bt.status
    )
}

@Composable
private fun ScreenCard(info: String) {
    HealthRow(
        icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "Tela",
        value = info,
        status = HealthStatus.GOOD
    )
}

@Composable
private fun UptimeCard(days: Int) {
    HealthRow(
        icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "Tempo ligado",
        value = "$days dia(s)",
        status = HealthStatus.GOOD
    )
}

@Composable
private fun SensorCard(sensor: SensorHealth) {
    HealthRow(
        icon = { Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = sensor.name,
        value = if (sensor.isPresent) "Detectado" else "Ausente",
        status = if (sensor.isPresent) HealthStatus.GOOD else HealthStatus.WARNING
    )
}

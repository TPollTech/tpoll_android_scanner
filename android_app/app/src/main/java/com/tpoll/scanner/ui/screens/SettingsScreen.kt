// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.tpoll.scanner.BootReceiver
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.ui.theme.ThemeManager
import com.tpoll.scanner.ui.theme.ThemeMode
import com.tpoll.scanner.updater.InstalledAppVersion
import com.tpoll.scanner.updater.UpdateScheduler
import com.tpoll.scanner.updater.UpdateStateStore
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE) }
    val installedVersion = remember(context) { InstalledAppVersion.read(context) }

    var autoRemoveHigh by remember { mutableStateOf(prefs.getBoolean("auto_remove_high", true)) }
    var autoRemoveMedium by remember { mutableStateOf(prefs.getBoolean("auto_remove_medium", false)) }
    var shieldEnabled by remember { mutableStateOf(ShieldService.isEnabled(context)) }
    var automaticUpdatesEnabled by remember {
        mutableStateOf(UpdateScheduler.isAutomaticUpdatesEnabled(context))
    }
    var updateStatus by remember { mutableStateOf(UpdateStateStore.summary(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Remoção automática",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remover apps de ALTO risco")
                        Text(
                            "Score >= 70: removido automaticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = autoRemoveHigh,
                        onCheckedChange = { enabled ->
                            autoRemoveHigh = enabled
                            prefs.edit().putBoolean("auto_remove_high", enabled).apply()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remover apps de MÉDIO risco")
                        Text(
                            "Score 40-69: removido automaticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = autoRemoveMedium,
                        onCheckedChange = { enabled ->
                            autoRemoveMedium = enabled
                            prefs.edit().putBoolean("auto_remove_medium", enabled).apply()
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Proteção Shield",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Proteção em tempo real")
                        Text(
                            "Monitora overlays, acessibilidade, admins e listeners 24/7",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = shieldEnabled,
                        onCheckedChange = { enabled ->
                            shieldEnabled = enabled
                            if (enabled) {
                                ShieldService.start(context)
                            } else {
                                ShieldService.stop(context)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                var autoScan by remember { mutableStateOf(prefs.getBoolean("auto_scan_enabled", true)) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Agendar scan automático", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Executa escaneamento completo automaticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = autoScan,
                        onCheckedChange = { enabled ->
                            autoScan = enabled
                            prefs.edit().putBoolean("auto_scan_enabled", enabled).apply()
                            if (enabled) {
                                BootReceiver.schedulePeriodicScan(context)
                            } else {
                                BootReceiver.cancelPeriodicScan(context)
                            }
                        }
                    )
                }

                if (autoScan) {
                    Spacer(modifier = Modifier.height(8.dp))

                    var useTimeSchedule by remember { mutableStateOf(prefs.getBoolean("use_time_schedule", false)) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Usar horário fixo", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = useTimeSchedule,
                            onCheckedChange = { enabled ->
                                useTimeSchedule = enabled
                                prefs.edit().putBoolean("use_time_schedule", enabled).apply()
                                if (enabled) {
                                    BootReceiver.scheduleAtTime(context)
                                } else {
                                    BootReceiver.schedulePeriodicScan(context)
                                }
                            }
                        )
                    }

                    if (useTimeSchedule) {
                        Spacer(modifier = Modifier.height(8.dp))
                        var hour by remember { mutableIntStateOf(prefs.getInt("scheduled_hour", 3)) }
                        var minute by remember { mutableIntStateOf(prefs.getInt("scheduled_minute", 0)) }
                        var showTimePicker by remember { mutableStateOf(false) }

                        TextButton(onClick = { showTimePicker = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Horário: %02d:%02d".format(hour, minute))
                        }

                        if (showTimePicker) {
                            AlertDialog(
                                onDismissRequest = { showTimePicker = false },
                                title = { Text("Horário do scan") },
                                text = {
                                    val timePickerState = rememberTimePickerState(
                                        initialHour = hour,
                                        initialMinute = minute,
                                        is24Hour = true
                                    )
                                    Column {
                                        Text("Escolha o horário para o scan diário:", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(8.dp))
                                        TimePicker(state = timePickerState)
                                        LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                                            hour = timePickerState.hour
                                            minute = timePickerState.minute
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        prefs.edit()
                                            .putInt("scheduled_hour", hour)
                                            .putInt("scheduled_minute", minute)
                                            .apply()
                                        BootReceiver.scheduleAtTime(context)
                                        showTimePicker = false
                                    }) { Text("OK") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
                                }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        val options = listOf(1 to "1 hora", 2 to "2 horas", 3 to "3 horas", 6 to "6 horas", 12 to "12 horas", 24 to "24 horas")
                        var scanInterval by remember { mutableIntStateOf(prefs.getInt("scan_interval_hours", 6)) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Intervalo", style = MaterialTheme.typography.bodyMedium)
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = "${scanInterval}h",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.menuAnchor().width(120.dp),
                                    singleLine = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    options.forEach { (hours, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                scanInterval = hours
                                                prefs.edit().putInt("scan_interval_hours", hours).apply()
                                                BootReceiver.schedulePeriodicScan(context)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aparência",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                var currentMode by remember { mutableStateOf(ThemeManager.getMode(context)) }
                val modes = listOf(
                    ThemeMode.SYSTEM to "Sistema",
                    ThemeMode.LIGHT to "Claro",
                    ThemeMode.DARK to "Escuro"
                )

                modes.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = {
                                currentMode = mode
                                ThemeManager.setMode(context, mode)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Acessibilidade",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val ttsHelper = com.tpoll.scanner.TPollApp.instance.ttsHelper
                var ttsEnabled by remember { mutableStateOf(ttsHelper.isEnabled()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Assistente de voz", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Anuncia alertas de segurança em voz alta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = ttsEnabled,
                        onCheckedChange = { enabled ->
                            ttsEnabled = enabled
                            ttsHelper.setEnabled(enabled)
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "WebGuard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val webGuard = com.tpoll.scanner.webguard.WebGuard(context)
                var webGuardEnabled by remember { mutableStateOf(webGuard.isEnabled()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monitorar downloads", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Escaneia APKs baixados automaticamente em busca de malware",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = webGuardEnabled,
                        onCheckedChange = { enabled ->
                            webGuardEnabled = enabled
                            webGuard.setEnabled(enabled)
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Atualizações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Atualizar automaticamente")
                        Text(
                            "Baixa em Wi-Fi e instala em segundo plano quando o Android permite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = automaticUpdatesEnabled,
                        onCheckedChange = { enabled ->
                            automaticUpdatesEnabled = enabled
                            UpdateScheduler.setAutomaticUpdatesEnabled(context, enabled)
                            updateStatus = UpdateStateStore.summary(context)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Versão do app")
                        Text(
                            "v${installedVersion.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    FilledTonalButton(
                        onClick = onCheckForUpdates
                    ) {
                        Icon(
                            Icons.Default.Update,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verificar atualizações")
                    }
                }
                updateStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sobre",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "TPoll Scanner v${installedVersion.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "App open-source de segurança para Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "© 2026 TPollTech. Licença MIT.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = "Proibida a redistribuição sem autorização.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "TPoll Scanner - Proteção automática contra apps maliciosos no Android\nhttps://github.com/TPollTech/tpoll_android_scanner")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartilhar")
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/TPollTech/tpoll_android_scanner"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Código")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sair da conta")
                }

                Spacer(modifier = Modifier.height(8.dp))

                var showSignature by remember { mutableStateOf(false) }

                TextButton(onClick = { showSignature = !showSignature }) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showSignature) "Ocultar assinatura" else "Verificar assinatura do APK")
                }

                if (showSignature) {
                    val signature = remember { getAppSignature(context) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Hash SHA-256:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    text = signature,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Compare este hash com o código-fonte em github.com/TPollTech. Se bater, o APK é autêntico.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getAppSignature(context: Context): String {
    return try {
        val pm = context.packageManager
        val packageName = context.packageName
        val flags = if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = pm.getPackageInfo(packageName, flags)

        val signatures = if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }

        if (signatures != null && signatures.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(signatures[0].toByteArray())
            hash.joinToString("") { "%02x".format(it) }.uppercase()
                .chunked(2).joinToString(":")
        } else "Indisponível"
    } catch (e: Exception) {
        "Erro ao ler assinatura"
    }
}

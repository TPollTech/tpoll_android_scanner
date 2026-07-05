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
import com.tpoll.scanner.BootReceiver
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.updater.UpdateChecker
import com.tpoll.scanner.updater.UpdateDialog
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE) }

    var scanInterval by remember { mutableIntStateOf(prefs.getInt("scan_interval_hours", 6)) }
    var autoRemoveHigh by remember { mutableStateOf(prefs.getBoolean("auto_remove_high", true)) }
    var autoRemoveMedium by remember { mutableStateOf(prefs.getBoolean("auto_remove_medium", false)) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var shieldEnabled by remember { mutableStateOf(ShieldService.isRunning()) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    if (showUpdateDialog) {
        UpdateDialog(onDismiss = { showUpdateDialog = false })
    }

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
                    text = "Varredura",
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
                        Text("Scan automático habilitado")
                        Text(
                            "Executa varreduras periódicas em background",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Intervalo de varredura")
                        Text(
                            "A cada $scanInterval horas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    FilledTonalButton(onClick = { showIntervalDialog = true }) {
                        Text("Alterar")
                    }
                }
            }
        }

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
                        Text("Versão do app")
                        Text(
                            "v${com.tpoll.scanner.updater.UpdateInfo.currentVersionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    FilledTonalButton(
                        onClick = { showUpdateDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Update,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verificar")
                    }
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
                    text = "TPoll Scanner v${com.tpoll.scanner.updater.UpdateInfo.currentVersionName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "App open-source de segurança para Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

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

    if (showIntervalDialog) {
        val intervals = listOf(1, 2, 3, 6, 12, 24)
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Intervalo de varredura") },
            text = {
                Column {
                    intervals.forEach { hours ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = scanInterval == hours,
                                onClick = {
                                    scanInterval = hours
                                    prefs.edit().putInt("scan_interval_hours", hours).apply()
                                    BootReceiver.schedulePeriodicScan(context)
                                    showIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("A cada $hours horas")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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

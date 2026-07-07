// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tpoll.scanner.TPollApp
import com.tpoll.scanner.model.QuarantinedApp
import com.tpoll.scanner.ui.theme.HighRiskColor
import com.tpoll.scanner.ui.theme.MediumRiskColor
import com.tpoll.scanner.ui.theme.LowRiskColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarantineScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quarantined by remember { mutableStateOf<List<QuarantinedApp>>(emptyList()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun loadQuarantined() {
        scope.launch {
            try {
                val db = TPollApp.instance.database
                quarantined = db.quarantineDao().getAll()
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) { loadQuarantined() }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Limpar quarentena?") },
            text = { Text("Os registros serão removidos, mas os apps continuarão desinstalados.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { TPollApp.instance.database.quarantineDao().deleteAll() } catch (_: Exception) { }
                        quarantined = emptyList()
                    }
                    showClearConfirm = false
                }) { Text("Limpar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quarentena") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (quarantined.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar tudo")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (quarantined.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = LowRiskColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhum app em quarentena",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Apps removidos automaticamente aparecerão aqui",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Text(
                        text = "${quarantined.size} app(s) em quarentena",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(quarantined, key = { it.id }) { app ->
                    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                    val riskColor = when (app.riskLevel) {
                        "HIGH" -> HighRiskColor
                        "MEDIUM" -> MediumRiskColor
                        else -> LowRiskColor
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName.ifEmpty { app.packageName },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    color = riskColor.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = app.riskLevel,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = riskColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (app.reason.isNotBlank()) {
                                Text(
                                    text = app.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = dateFormat.format(Date(app.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restaurar", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

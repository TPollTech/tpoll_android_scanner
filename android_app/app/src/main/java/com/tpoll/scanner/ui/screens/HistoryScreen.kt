package com.tpoll.scanner.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import com.tpoll.scanner.TPollApp
import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.RiskLevel
import com.tpoll.scanner.ui.theme.HighRiskColor
import com.tpoll.scanner.ui.theme.MediumRiskColor
import com.tpoll.scanner.ui.theme.LowRiskColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var findings by remember { mutableStateOf<List<AppFinding>>(emptyList()) }
    var selectedFinding by remember { mutableStateOf<AppFinding?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadFindings() {
        scope.launch {
            try {
                val db = TPollApp.instance.database
                findings = db.appDao().getThreats()
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) { loadFindings() }

    if (selectedFinding != null) {
        ThreatDetailDialog(
            finding = selectedFinding!!,
            onDismiss = { selectedFinding = null },
            onUninstall = { pkg ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                selectedFinding = null
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (findings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = LowRiskColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhuma ameaça detectada",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "As ameaças encontradas pelo Shield aparecerão aqui",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = { loadFindings() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Atualizar")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${findings.size} ameaça(s) encontrada(s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                TextButton(onClick = {
                    scope.launch {
                        try {
                            TPollApp.instance.database.appDao().deleteAll()
                            findings = emptyList()
                        } catch (_: Exception) { }
                    }
                }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpar")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(findings, key = { it.packageName + it.timestamp }) { finding ->
                    ThreatCard(
                        finding = finding,
                        onClick = { selectedFinding = finding }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreatCard(
    finding: AppFinding,
    onClick: () -> Unit
) {
    val riskColor = when (finding.level) {
        RiskLevel.HIGH -> HighRiskColor
        RiskLevel.MEDIUM -> MediumRiskColor
        RiskLevel.LOW -> LowRiskColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = riskColor.copy(alpha = 0.08f)
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
                imageVector = if (finding.isKnownThreat) Icons.Default.Dangerous else Icons.Default.Warning,
                contentDescription = null,
                tint = riskColor,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = finding.appName.ifEmpty { finding.packageName },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = finding.reasons.firstOrNull() ?: finding.threatType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${finding.score}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = riskColor
                )
                Text(
                    text = when (finding.level) {
                        RiskLevel.HIGH -> "ALTO"
                        RiskLevel.MEDIUM -> "MÉDIO"
                        RiskLevel.LOW -> "BAIXO"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor
                )
            }
        }
    }
}

@Composable
private fun ThreatDetailDialog(
    finding: AppFinding,
    onDismiss: () -> Unit,
    onUninstall: (String) -> Unit
) {
    val riskColor = when (finding.level) {
        RiskLevel.HIGH -> HighRiskColor
        RiskLevel.MEDIUM -> MediumRiskColor
        RiskLevel.LOW -> LowRiskColor
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (finding.isKnownThreat) Icons.Default.Dangerous else Icons.Default.Warning,
                contentDescription = null,
                tint = riskColor,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = finding.appName.ifEmpty { finding.packageName },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                InfoRow("Pacote", finding.packageName)
                InfoRow("Score", "${finding.score}/100")
                InfoRow("Risco", when (finding.level) {
                    RiskLevel.HIGH -> "ALTO"
                    RiskLevel.MEDIUM -> "MÉDIO"
                    RiskLevel.LOW -> "BAIXO"
                })
                InfoRow("Instalador", finding.installer)
                if (finding.threatType.isNotBlank()) {
                    InfoRow("Tipo", finding.threatType)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Motivos:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                finding.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detectado em: ${
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(finding.timestamp))
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUninstall(finding.packageName) },
                colors = ButtonDefaults.buttonColors(containerColor = HighRiskColor)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Desinstalar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

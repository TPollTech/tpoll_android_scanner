// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.ui.screens

import android.content.Intent
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
import com.tpoll.scanner.AppPermissionInfo
import com.tpoll.scanner.PermissionScanner
import com.tpoll.scanner.ui.theme.HighRiskColor
import com.tpoll.scanner.ui.theme.MediumRiskColor
import com.tpoll.scanner.ui.theme.LowRiskColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppPermissionInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedApp by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = withContext(Dispatchers.IO) { PermissionScanner.scanAll(context) }
        apps = result
        isLoading = false
    }

    val filteredApps = when (filter) {
        "high" -> apps.filter { it.totalDangerous >= 10 }
        "medium" -> apps.filter { it.totalDangerous in 5..9 }
        "low" -> apps.filter { it.totalDangerous in 1..4 }
        else -> apps
    }

    val totalDangerousPerms = apps.sumOf { it.totalDangerous }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissões") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Analisando permissões...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${apps.size} apps com ${totalDangerousPerms} permissões perigosas no total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("Todas") })
                                FilterChip(selected = filter == "high", onClick = { filter = "high" }, label = { Text("Alta (10+)") })
                                FilterChip(selected = filter == "medium", onClick = { filter = "medium" }, label = { Text("Média (5-9)") })
                                FilterChip(selected = filter == "low", onClick = { filter = "low" }, label = { Text("Baixa (1-4)") })
                            }
                        }
                    }
                }
                items(filteredApps, key = { it.packageName }) { app ->
                    val isExpanded = expandedApp == app.packageName
                    Card(
                        onClick = { expandedApp = if (isExpanded) null else app.packageName },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                val color = when { app.totalDangerous >= 10 -> HighRiskColor; app.totalDangerous >= 5 -> MediumRiskColor; else -> LowRiskColor }
                                Surface(color = color.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                                    Text("${app.totalDangerous}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isExpanded) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                app.dangerousPermissions.forEach { perm ->
                                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = HighRiskColor)
                                        Spacer(Modifier.width(6.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(perm.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            if (perm.description.isNotBlank()) {
                                                Text(perm.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${app.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Gerenciar", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

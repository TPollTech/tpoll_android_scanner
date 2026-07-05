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
import com.tpoll.scanner.ui.theme.*
import org.json.JSONArray

data class HistoryEntry(
    val timestamp: Long,
    val total: Int,
    val high: Int,
    val medium: Int,
    val removed: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val entries = remember { mutableStateListOf<HistoryEntry>() }

    LaunchedEffect(Unit) {
        entries.clear()
        entries.addAll(loadHistory(context))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Histórico de Varreduras",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Nenhuma varredura registrada",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries.reversed()) { entry ->
                    HistoryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = java.text.SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(entry.timestamp)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (entry.removed > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${entry.removed} removidos") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HistoryStat(
                    label = "Total",
                    value = entry.total.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                HistoryStat(
                    label = "Alto",
                    value = entry.high.toString(),
                    color = HighRiskColor
                )
                HistoryStat(
                    label = "Médio",
                    value = entry.medium.toString(),
                    color = MediumRiskColor
                )
            }
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            )
        }
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

private fun loadHistory(context: Context): List<HistoryEntry> {
    val prefs = context.getSharedPreferences("scan_results", Context.MODE_PRIVATE)
    val json = prefs.getString("scan_history", "[]") ?: "[]"

    return try {
        val array = JSONArray(json)
        val entries = mutableListOf<HistoryEntry>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            entries.add(
                HistoryEntry(
                    timestamp = obj.getLong("timestamp"),
                    total = obj.getInt("total"),
                    high = obj.getInt("high"),
                    medium = obj.getInt("medium"),
                    removed = obj.getInt("removed")
                )
            )
        }

        entries
    } catch (e: Exception) {
        emptyList()
    }
}

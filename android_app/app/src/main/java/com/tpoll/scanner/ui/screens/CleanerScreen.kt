// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPollTech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.

package com.tpoll.scanner.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tpoll.scanner.cleaner.CleanerBucket
import com.tpoll.scanner.cleaner.CleanerCacheItem
import com.tpoll.scanner.cleaner.CleanerFileItem
import com.tpoll.scanner.cleaner.CleanerReport
import com.tpoll.scanner.cleaner.CleanerScanner
import com.tpoll.scanner.cleaner.CleanerTrashManager
import com.tpoll.scanner.cleaner.CleanerTrashResult
import com.tpoll.scanner.cleaner.DuplicateConfidence
import com.tpoll.scanner.cleaner.DuplicateGroup
import com.tpoll.scanner.cleaner.formatCleanerBytes
import com.tpoll.scanner.ui.theme.HighRiskColor
import com.tpoll.scanner.ui.theme.LowRiskColor
import com.tpoll.scanner.ui.theme.MediumRiskColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CleanerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(hasCleanerPermissions(context)) }
    var report by remember { mutableStateOf<CleanerReport?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var pendingTrashItems by remember { mutableStateOf<List<CleanerFileItem>>(emptyList()) }

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            successMessage = "Arquivos enviados para a lixeira do Android. Reanalise para atualizar os resultados."
            scope.launch {
                isScanning = true
                report = runCleanerScan(context) { errorMessage = it }
                isScanning = false
            }
        } else {
            errorMessage = "A limpeza foi cancelada. Nenhum arquivo foi alterado."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.any { it } || hasCleanerPermissions(context)
        if (hasPermission) {
            scope.launch {
                isScanning = true
                errorMessage = null
                report = runCleanerScan(context) { errorMessage = it }
                isScanning = false
            }
        } else {
            errorMessage = "Permita acesso às mídias para encontrar duplicados, vídeos grandes e arquivos antigos."
        }
    }

    fun requestTrash(items: List<CleanerFileItem>) {
        pendingTrashItems = items.distinctBy { it.uri }
    }

    fun performTrash(items: List<CleanerFileItem>) {
        pendingTrashItems = emptyList()
        scope.launch {
            when (val result = CleanerTrashManager(context).prepareTrash(items)) {
                CleanerTrashResult.Empty -> errorMessage = "Nenhum arquivo selecionado."
                is CleanerTrashResult.Failed -> errorMessage = result.message
                is CleanerTrashResult.DeletedImmediately -> {
                    successMessage = "${result.deletedCount} arquivo(s) removido(s), liberando ${formatCleanerBytes(result.sizeBytes)}. Falhas: ${result.failedCount}."
                    isScanning = true
                    report = runCleanerScan(context) { errorMessage = it }
                    isScanning = false
                }
                is CleanerTrashResult.NeedsSystemConfirmation -> {
                    successMessage = "Confirme no Android para mover ${result.itemCount} arquivo(s), ${formatCleanerBytes(result.sizeBytes)}, para a lixeira."
                    trashLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
                }
            }
        }
    }

    if (pendingTrashItems.isNotEmpty()) {
        ConfirmTrashDialog(
            items = pendingTrashItems,
            onDismiss = { pendingTrashItems = emptyList() },
            onConfirm = { performTrash(pendingTrashItems) }
        )
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && report == null && !isScanning) {
            isScanning = true
            errorMessage = null
            report = runCleanerScan(context) { errorMessage = it }
            isScanning = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CleanerHeroCard(
                report = report,
                isScanning = isScanning,
                hasPermission = hasPermission,
                onRequestPermission = { permissionLauncher.launch(cleanerPermissions()) },
                onScanAgain = {
                    scope.launch {
                        isScanning = true
                        errorMessage = null
                        successMessage = null
                        report = runCleanerScan(context) { errorMessage = it }
                        isScanning = false
                    }
                }
            )
        }

        if (!successMessage.isNullOrBlank()) {
            item { InfoCard("Pronto", successMessage.orEmpty(), LowRiskColor) }
        }

        if (!errorMessage.isNullOrBlank()) {
            item { WarningCard("Atenção", errorMessage.orEmpty()) }
        }

        if (isScanning) {
            item { ScanningCard() }
        }

        val currentReport = report
        if (currentReport != null) {
            item { SectionTitle("Resumo da análise") }
            item { SummaryGrid(currentReport) }

            item { SectionTitle("O que dá para revisar") }
            item { CleanerOpportunityList(currentReport) }

            if (currentReport.exactDuplicateGroups.isNotEmpty()) {
                item { SectionTitle("Duplicados confirmados") }
                items(currentReport.exactDuplicateGroups.take(12)) { group ->
                    DuplicateGroupCard(group, onTrashCopies = { requestTrash(group.items.drop(1)) })
                }
            }

            if (currentReport.duplicateGroups.isNotEmpty()) {
                item { SectionTitle("Duplicados prováveis") }
                items(currentReport.duplicateGroups.take(10)) { group ->
                    DuplicateGroupCard(group, onTrashCopies = { requestTrash(group.items.drop(1)) })
                }
            }

            if (currentReport.similarPhotoGroups.isNotEmpty()) {
                item { SectionTitle("Fotos parecidas") }
                items(currentReport.similarPhotoGroups.take(10)) { group ->
                    DuplicateGroupCard(group, onTrashCopies = { requestTrash(group.items.drop(1)) })
                }
            }

            if (currentReport.whatsappBuckets.isNotEmpty()) {
                item { SectionTitle("WhatsApp Cleaner") }
                items(currentReport.whatsappBuckets) { bucket ->
                    BucketCard(bucket, onTrashBucket = { requestTrash(bucket.items) })
                }
            }

            if (currentReport.largeFiles.isNotEmpty()) {
                item { SectionTitle("Arquivos grandes") }
                items(currentReport.largeFiles.take(20)) { file ->
                    LargeFileCard(file, onTrash = { requestTrash(listOf(file)) })
                }
            }

            if (currentReport.apkFiles.isNotEmpty()) {
                item { SectionTitle("APKs baixados") }
                items(currentReport.apkFiles.take(10)) { file ->
                    LargeFileCard(file, onTrash = { requestTrash(listOf(file)) })
                }
            }

            val allCacheItems = currentReport.cacheItems + currentReport.tempItems
            if (allCacheItems.isNotEmpty()) {
                item { SectionTitle("Cache e arquivos temporários") }
                items(allCacheItems) { cacheItem ->
                    CacheItemCard(cacheItem, onClean = {
                        scope.launch {
                            val scanner = CleanerScanner(context)
                            val cleared = scanner.clearAllCache(listOf(cacheItem))
                            if (cleared > 0) {
                                successMessage = "Cache limpo: ${formatCleanerBytes(cacheItem.sizeBytes)} liberados."
                                isScanning = true
                                report = runCleanerScan(context) { errorMessage = it }
                                isScanning = false
                            } else {
                                errorMessage = "Não foi possível limpar este cache."
                            }
                        }
                    })
                }

                item {
                    Button(
                        onClick = {
                            scope.launch {
                                val scanner = CleanerScanner(context)
                                val totalSize = allCacheItems.sumOf { it.sizeBytes }
                                val cleared = scanner.clearAllCache(allCacheItems)
                                if (cleared > 0) {
                                    successMessage = "Todo o cache foi limpo: ${formatCleanerBytes(totalSize)} liberados."
                                    isScanning = true
                                    report = runCleanerScan(context) { errorMessage = it }
                                    isScanning = false
                                } else {
                                    errorMessage = "Não foi possível limpar o cache."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Limpar todo o cache (${formatCleanerBytes(currentReport.totalCacheSize)})")
                    }
                }
            }

            item {
                WarningCard(
                    title = "Limpeza segura",
                    message = "Agora o app já pode enviar arquivos selecionados para a lixeira do Android, mas sempre com confirmação. Revise antes de limpar fotos, documentos e vídeos importantes."
                )
            }
        }
    }
}

private suspend fun runCleanerScan(
    context: Context,
    onError: (String) -> Unit
): CleanerReport? {
    return withContext(Dispatchers.IO) {
        try {
            CleanerScanner(context).scan()
        } catch (e: Exception) {
            onError("Não foi possível analisar os arquivos: ${e.localizedMessage ?: e.javaClass.simpleName}")
            null
        }
    }
}

@Composable
private fun ConfirmTrashDialog(
    items: List<CleanerFileItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar limpeza") },
        text = {
            Text(
                "Você selecionou ${items.size} arquivo(s), somando ${formatCleanerBytes(items.sumOf { it.sizeBytes })}. No Android 11 ou superior, eles serão enviados para a lixeira do sistema quando disponível."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Continuar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CleanerHeroCard(
    report: CleanerReport?,
    isScanning: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScanAgain: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(46.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Limpeza inteligente",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (report != null) {
                    "Você pode revisar até ${formatCleanerBytes(report.recoverableBytesEstimate)} em duplicados, WhatsApp, APKs e arquivos grandes."
                } else {
                    "Encontre duplicados confirmados, fotos parecidas, vídeos grandes, APKs antigos e mídias do WhatsApp."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permitir análise de arquivos")
                }
            } else {
                OutlinedButton(
                    onClick = onScanAgain,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "Analisando..." else "Analisar novamente")
                }
            }
        }
    }
}

@Composable
private fun ScanningCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Column {
                Text("Analisando arquivos...", fontWeight = FontWeight.Bold)
                Text(
                    "Isso pode demorar em celulares com muitas fotos e vídeos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SummaryGrid(report: CleanerReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(Modifier.weight(1f), Icons.Default.AutoAwesome, "Revisável", formatCleanerBytes(report.recoverableBytesEstimate), MediumRiskColor)
            MetricCard(Modifier.weight(1f), Icons.Default.Verified, "Confirmados", report.exactDuplicateGroups.size.toString(), LowRiskColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(Modifier.weight(1f), Icons.Default.ContentCopy, "Duplicados", report.allDuplicateGroups.size.toString(), MaterialTheme.colorScheme.primary)
            MetricCard(Modifier.weight(1f), Icons.Default.CleaningServices, "Cache", formatCleanerBytes(report.totalCacheSize), HighRiskColor)
        }
    }
}

@Composable
private fun CleanerOpportunityList(report: CleanerReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OpportunityRow(Icons.Default.Verified, "Duplicados confirmados", "${report.exactDuplicateGroups.size} grupos por hash", formatCleanerBytes(report.exactDuplicateGroups.sumOf { it.recoverableBytes }), LowRiskColor)
        OpportunityRow(Icons.Default.ContentCopy, "Duplicados prováveis", "${report.duplicateGroups.size} grupos por nome/tamanho", formatCleanerBytes(report.duplicateGroups.sumOf { it.recoverableBytes }), MaterialTheme.colorScheme.primary)
        OpportunityRow(Icons.Default.Image, "Fotos parecidas", "${report.similarPhotoGroups.size} grupos para revisar", formatCleanerBytes(report.similarPhotoGroups.sumOf { it.recoverableBytes }), MediumRiskColor)
        OpportunityRow(Icons.Default.CleaningServices, "Cache do app", "${report.cacheItems.size} pastas de cache", formatCleanerBytes(report.cacheSizeBytes), HighRiskColor)
        OpportunityRow(Icons.Default.InsertDriveFile, "Arquivos temporários", "${report.tempItems.size} pastas temporárias", formatCleanerBytes(report.tempSizeBytes), MediumRiskColor)
        OpportunityRow(Icons.Default.VideoLibrary, "Arquivos grandes", "${report.largeFiles.size} arquivos acima de 100 MB", formatCleanerBytes(report.largeFiles.sumOf { it.sizeBytes }), HighRiskColor)
        OpportunityRow(Icons.Default.PhoneAndroid, "WhatsApp Cleaner", "${report.whatsappCount} mídias/arquivos encontrados", formatCleanerBytes(report.whatsappSizeBytes), LowRiskColor)
        OpportunityRow(Icons.Default.PhotoCamera, "Prints", "${report.screenshotCount} capturas de tela", formatCleanerBytes(report.screenshotSizeBytes), MediumRiskColor)
        OpportunityRow(Icons.Default.Download, "Downloads antigos", "${report.oldDownloadCount} arquivos com mais de 90 dias", formatCleanerBytes(report.oldDownloadSizeBytes), MaterialTheme.colorScheme.tertiary)
        OpportunityRow(Icons.Default.InsertDriveFile, "APKs baixados", "${report.apkCount} instaladores salvos", report.apkCount.toString(), HighRiskColor)
    }
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup, onTrashCopies: () -> Unit) {
    val color = when (group.confidence) {
        DuplicateConfidence.CONFIRMED_HASH -> LowRiskColor
        DuplicateConfidence.PROBABLE_METADATA -> MaterialTheme.colorScheme.primary
        DuplicateConfidence.SIMILAR_PHOTO_NAME -> MediumRiskColor
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.title, fontWeight = FontWeight.Bold)
                    Text(
                        "${group.items.size} arquivos • ${group.confidence.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(formatCleanerBytes(group.recoverableBytes), fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                group.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            group.items.take(3).forEach { item ->
                Text(
                    text = "• ${item.name} — ${formatCleanerBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onTrashCopies, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mover cópias para lixeira")
            }
        }
    }
}

@Composable
private fun BucketCard(bucket: CleanerBucket, onTrashBucket: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = LowRiskColor, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bucket.title, fontWeight = FontWeight.Bold)
                Text(
                    "${bucket.count} arquivo(s) • ${formatCleanerBytes(bucket.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            OutlinedButton(onClick = onTrashBucket) {
                Text("Limpar")
            }
        }
    }
}

@Composable
private fun LargeFileCard(file: CleanerFileItem, onTrash: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when {
                        file.mimeType.startsWith("video") -> Icons.Default.PlayCircle
                        file.mimeType.startsWith("image") -> Icons.Default.Image
                        else -> Icons.Default.Folder
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        file.relativePath.ifBlank { file.category.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
                Text(formatCleanerBytes(file.sizeBytes), fontWeight = FontWeight.Bold, color = HighRiskColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onTrash, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mover para lixeira")
            }
        }
    }
}

@Composable
private fun CacheItemCard(cacheItem: CleanerCacheItem, onClean: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CleaningServices,
                contentDescription = null,
                tint = MediumRiskColor,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(cacheItem.label, fontWeight = FontWeight.Bold)
                Text(
                    "${cacheItem.type.label} • ${formatCleanerBytes(cacheItem.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            OutlinedButton(onClick = onClean) {
                Text("Limpar")
            }
        }
    }
}

@Composable
private fun OpportunityRow(icon: ImageVector, title: String, subtitle: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text(value, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, icon: ImageVector, label: String, value: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun WarningCard(title: String, message: String) {
    InfoCard(title, message, MediumRiskColor)
}

@Composable
private fun InfoCard(title: String, message: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color)
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}

private fun cleanerPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasCleanerPermissions(context: Context): Boolean {
    return cleanerPermissions().any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

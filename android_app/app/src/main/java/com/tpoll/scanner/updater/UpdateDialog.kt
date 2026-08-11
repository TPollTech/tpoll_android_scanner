// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
package com.tpoll.scanner.updater

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpoll.scanner.ui.theme.LowRiskColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdateDialog(
    initialResult: UpdateResult? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val checker = remember(context) { UpdateChecker(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var result by remember(initialResult) { mutableStateOf(initialResult) }
    var isChecking by remember(initialResult) { mutableStateOf(initialResult == null) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(DownloadProgress(0L, 0L)) }
    var preparationFailure by remember { mutableStateOf<ApkPreparationResult.Failed?>(null) }
    var permissionRequired by remember { mutableStateOf(false) }
    var installerLaunched by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }

    fun persistCheckResult(checked: UpdateResult) {
        when (checked) {
            is UpdateResult.Available -> UpdateStateStore.write(
                context = context,
                phase = UpdatePhase.AVAILABLE,
                versionCode = checked.info.versionCode,
                versionName = checked.info.versionName
            )
            is UpdateResult.UpToDate -> UpdateStateStore.write(
                context = context,
                phase = UpdatePhase.IDLE,
                versionCode = checked.installedVersion.code.toInt(),
                versionName = checked.installedVersion.name
            )
            is UpdateResult.Error -> UpdateStateStore.write(
                context = context,
                phase = UpdatePhase.FAILED,
                message = checked.message
            )
        }
    }

    fun checkNow() {
        scope.launch {
            isChecking = true
            preparationFailure = null
            installerLaunched = false
            UpdateStateStore.write(context, UpdatePhase.CHECKING)
            val checked = checker.checkForUpdates()
            result = checked
            persistCheckResult(checked)
            val available = checked as? UpdateResult.Available
            prepared = available?.let { ApkInstaller.isPrepared(context, it.info) } == true
            permissionRequired = prepared && !ApkInstaller.canRequestPackageInstalls(context)
            isChecking = false
        }
    }

    fun launchPreparedInstaller(info: UpdateInfo) {
        scope.launch {
            when (val launchResult = ApkInstaller.launchInstaller(context, info)) {
                ApkInstallLaunchResult.Launched -> {
                    installerLaunched = true
                    permissionRequired = false
                    UpdateStateStore.write(
                        context = context,
                        phase = UpdatePhase.READY_TO_INSTALL,
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        message = "Instalador do Android aberto. Confirme para concluir."
                    )
                }
                ApkInstallLaunchResult.PermissionRequired -> {
                    permissionRequired = true
                    UpdateStateStore.write(
                        context = context,
                        phase = UpdatePhase.PERMISSION_REQUIRED,
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        message = "Autorize esta fonte para continuar a instalação."
                    )
                }
                is ApkInstallLaunchResult.Failed -> {
                    preparationFailure = ApkPreparationResult.Failed(launchResult.message)
                    UpdateStateStore.write(
                        context = context,
                        phase = UpdatePhase.FAILED,
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        message = launchResult.message
                    )
                }
            }
        }
    }

    fun prepareAndInstall(info: UpdateInfo) {
        scope.launch {
            isDownloading = true
            preparationFailure = null
            installerLaunched = false
            UpdateStateStore.write(
                context = context,
                phase = UpdatePhase.DOWNLOADING,
                versionCode = info.versionCode,
                versionName = info.versionName,
                totalBytes = info.sizeBytes
            )
            var lastPersistedPercent = -1
            when (
                val preparation = ApkInstaller.downloadAndValidate(
                    context = context,
                    info = info,
                    onProgress = { current ->
                        withContext(Dispatchers.Main.immediate) { progress = current }
                        val percent = (current.fraction * 100f).toInt().coerceIn(0, 100)
                        if (percent != lastPersistedPercent) {
                            lastPersistedPercent = percent
                            UpdateStateStore.write(
                                context = context,
                                phase = UpdatePhase.DOWNLOADING,
                                versionCode = info.versionCode,
                                versionName = info.versionName,
                                downloadedBytes = current.downloadedBytes,
                                totalBytes = current.totalBytes
                            )
                        }
                    }
                )
            ) {
                is ApkPreparationResult.Ready -> {
                    prepared = true
                    isDownloading = false
                    if (ApkInstaller.canRequestPackageInstalls(context)) {
                        launchPreparedInstaller(info)
                    } else {
                        permissionRequired = true
                        UpdateStateStore.write(
                            context = context,
                            phase = UpdatePhase.PERMISSION_REQUIRED,
                            versionCode = info.versionCode,
                            versionName = info.versionName,
                            message = "Autorize esta fonte para continuar a instalação."
                        )
                    }
                }
                is ApkPreparationResult.Failed -> {
                    isDownloading = false
                    preparationFailure = preparation
                    UpdateStateStore.write(
                        context = context,
                        phase = UpdatePhase.FAILED,
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        message = preparation.message
                    )
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val info = (result as? UpdateResult.Available)?.info
        if (info != null && ApkInstaller.canRequestPackageInstalls(context)) {
            permissionRequired = false
            launchPreparedInstaller(info)
        }
    }

    LaunchedEffect(initialResult) {
        if (initialResult == null) {
            checkNow()
        } else {
            persistCheckResult(initialResult)
            val available = initialResult as? UpdateResult.Available
            prepared = available?.let { ApkInstaller.isPrepared(context, it.info) } == true
            permissionRequired = prepared && !ApkInstaller.canRequestPackageInstalls(context)
        }
    }

    val available = result as? UpdateResult.Available
    val mandatoryUpdate = available?.info?.isMandatoryFor(available.installedVersion.code) == true

    AlertDialog(
        onDismissRequest = { if (!isDownloading && !mandatoryUpdate) onDismiss() },
        icon = {
            Icon(
                Icons.Default.NewReleases,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = when {
                    isDownloading -> "Baixando atualização"
                    installerLaunched -> "Confirme a instalação"
                    permissionRequired -> "Permissão necessária"
                    preparationFailure != null -> "Não foi possível atualizar"
                    isChecking -> "Verificando atualizações"
                    result is UpdateResult.Available -> "Nova versão disponível"
                    result is UpdateResult.UpToDate -> "App atualizado"
                    result is UpdateResult.Error -> "Erro na verificação"
                    else -> "Atualizações"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when {
                isDownloading -> DownloadingContent(progress)
                installerLaunched -> InstallerOpenedContent()
                permissionRequired -> PermissionContent()
                preparationFailure != null -> FailureContent(preparationFailure!!)
                isChecking -> CheckingContent()
                result is UpdateResult.Available -> AvailableContent(
                    (result as UpdateResult.Available).info,
                    (result as UpdateResult.Available).installedVersion
                )
                result is UpdateResult.UpToDate -> Text(
                    "Seu app está na versão mais recente " +
                        "(${(result as UpdateResult.UpToDate).installedVersion.name})."
                )
                result is UpdateResult.Error -> ErrorContent(
                    (result as UpdateResult.Error).message
                )
            }
        },
        confirmButton = {
            when {
                isDownloading -> Unit
                permissionRequired -> Button(onClick = {
                    permissionLauncher.launch(ApkInstaller.unknownSourcesSettingsIntent(context))
                }) {
                    Text("Abrir configuração")
                }
                preparationFailure?.requiresOneTimeReinstall == true -> OutlinedButton(onClick = {
                    val pageUrl = available?.info?.downloadUrl.orEmpty()
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) {
                    Text("Abrir download oficial")
                }
                preparationFailure != null && available != null -> Button(onClick = {
                    preparationFailure = null
                    prepareAndInstall(available.info)
                }) {
                    Text("Tentar novamente")
                }
                result is UpdateResult.Error -> Button(onClick = { checkNow() }) {
                    Text("Tentar novamente")
                }
                available != null && !installerLaunched -> Button(onClick = {
                    if (prepared) launchPreparedInstaller(available.info)
                    else prepareAndInstall(available.info)
                }) {
                    Text(if (prepared) "Instalar agora" else "Atualizar agora")
                }
                !isChecking -> Button(onClick = onDismiss) {
                    Text("Fechar")
                }
            }
        },
        dismissButton = {
            if (!isDownloading && !mandatoryUpdate && available != null && !installerLaunched) {
                TextButton(onClick = onDismiss) {
                    Text("Depois")
                }
            }
        }
    )
}

@Composable
private fun DownloadingContent(progress: DownloadProgress) {
    val percent = (progress.fraction * 100f).toInt().coerceIn(0, 100)
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("$percent% — ${formatBytes(progress.downloadedBytes)} de ${formatBytes(progress.totalBytes)}")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "O APK será validado antes de o instalador do Android abrir.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun InstallerOpenedContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = LowRiskColor,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("APK validado com segurança", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Confirme a atualização na tela oficial do Android. O app será instalado por cima " +
                "da versão atual, preservando seus dados.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PermissionContent() {
    Column {
        Text("Autorize esta fonte", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "O Android precisa permitir que o TPoll Scanner abra o instalador da própria atualização.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Abra a configuração, ative Permitir desta fonte e volte. A instalação continuará " +
                "usando o APK já baixado e validado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FailureContent(failure: ApkPreparationResult.Failed) {
    Column {
        Text(
            failure.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        if (failure.requiresOneTimeReinstall) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Somente instalações antigas assinadas com outra chave precisam dessa migração. " +
                    "Versões oficiais atuais são atualizadas sem desinstalar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CheckingContent() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text("Consultando a versão publicada...")
    }
}

@Composable
private fun AvailableContent(info: UpdateInfo, installedVersion: InstalledAppVersion) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Versão ${info.versionName}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Instalada: ${installedVersion.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (info.isMandatoryFor(installedVersion.code)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Atualização obrigatória",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Novidades", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        info.notesForDisplay().forEach { note ->
            Text("• $note", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(3.dp))
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Column {
        Text("Não foi possível verificar atualizações.")
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
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
import androidx.compose.ui.unit.sp
import com.tpoll.scanner.ui.theme.LowRiskColor
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    onDismiss: (seenVersionCode: Int) -> Unit
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    var isChecking by remember { mutableStateOf(true) }
    var isInstalling by remember { mutableStateOf(false) }
    var installFailure by remember { mutableStateOf<ApkInstallRequestResult.Failed?>(null) }
    var permissionRequired by remember { mutableStateOf(false) }
    var installSubmitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val checker = remember(context) { UpdateChecker(context.applicationContext) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ApkInstaller.canRequestPackageInstalls(context)) {
            permissionRequired = false
        }
    }

    LaunchedEffect(Unit) {
        UpdateStateStore.write(context, UpdatePhase.CHECKING)
        result = checker.checkForUpdates()
        when (val checked = result) {
            is UpdateResult.Available -> UpdateStateStore.write(
                context = context,
                phase = UpdatePhase.AVAILABLE,
                versionCode = checked.info.version_code,
                versionName = checked.info.version_name
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
            null -> Unit
        }
        isChecking = false
    }

    val available = result as? UpdateResult.Available
    val mandatoryUpdate = available?.info?.isMandatoryFor(available.installedVersion.code) == true

    fun dismissWithVersion() {
        val versionCode = (result as? UpdateResult.Available)?.info?.version_code ?: 0
        onDismiss(versionCode)
    }

    AlertDialog(
        onDismissRequest = { if (!isInstalling && !mandatoryUpdate) dismissWithVersion() },
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
                    isInstalling -> "Preparando atualização..."
                    installSubmitted -> "Atualização preparada"
                    permissionRequired -> "Permissão necessária"
                    installFailure != null -> "Não foi possível atualizar"
                    isChecking -> "Verificando..."
                    result is UpdateResult.Available -> "Nova versão disponível!"
                    result is UpdateResult.UpToDate -> "App atualizado"
                    result is UpdateResult.Error -> "Erro na verificação"
                    else -> "Atualizações"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when {
                isInstalling -> DownloadingContent()
                installSubmitted -> SubmittedContent()
                permissionRequired -> PermissionContent()
                installFailure != null -> FailureContent(installFailure!!)
                isChecking -> CheckingContent()
                result is UpdateResult.Available -> AvailableContent(
                    (result as UpdateResult.Available).info
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
                result is UpdateResult.Available &&
                    !isInstalling &&
                    installFailure == null &&
                    !permissionRequired &&
                    !installSubmitted -> {
                    val info = (result as UpdateResult.Available).info
                    Button(onClick = {
                        isInstalling = true
                        UpdateStateStore.write(
                            context = context,
                            phase = UpdatePhase.DOWNLOADING,
                            versionCode = info.version_code,
                            versionName = info.version_name
                        )
                        scope.launch {
                            when (
                                val install = ApkInstaller.downloadAndInstall(
                                    context = context,
                                    apkUrl = info.apk_url,
                                    expectedVersionCode = info.version_code,
                                    expectedSha256 = info.sha256,
                                    expectedSizeBytes = info.size_bytes
                                )
                            ) {
                                is ApkInstallRequestResult.Submitted -> {
                                    installSubmitted = true
                                    UpdateStateStore.write(
                                        context = context,
                                        phase = UpdatePhase.INSTALL_SUBMITTED,
                                        versionCode = info.version_code,
                                        versionName = info.version_name
                                    )
                                }
                                is ApkInstallRequestResult.PermissionRequired -> {
                                    permissionRequired = true
                                    UpdateStateStore.write(
                                        context = context,
                                        phase = UpdatePhase.PERMISSION_REQUIRED,
                                        versionCode = info.version_code,
                                        versionName = info.version_name,
                                        message = "Permissão necessária para instalar a atualização."
                                    )
                                }
                                is ApkInstallRequestResult.Failed -> {
                                    installFailure = install
                                    UpdateStateStore.write(
                                        context = context,
                                        phase = UpdatePhase.FAILED,
                                        versionCode = info.version_code,
                                        versionName = info.version_name,
                                        message = install.message
                                    )
                                }
                            }
                            isInstalling = false
                        }
                    }) {
                        Text("Atualizar agora")
                    }
                }
                permissionRequired -> {
                    Button(onClick = {
                        installPermissionLauncher.launch(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }) {
                        Text("Permitir")
                    }
                }
                installFailure?.requiresOneTimeReinstall == true -> {
                    OutlinedButton(onClick = {
                        val pageUrl = (result as? UpdateResult.Available)
                            ?.info
                            ?.download_url
                            .orEmpty()
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Text("Abrir download oficial")
                    }
                }
                installFailure != null -> {
                    Button(onClick = {
                        installFailure = null
                        permissionRequired = false
                    }) {
                        Text("Tentar novamente")
                    }
                }
                installSubmitted -> {
                    Button(onClick = { dismissWithVersion() }) {
                        Text("OK")
                    }
                }
            }
        },
        dismissButton = {
            if (!isInstalling && !mandatoryUpdate) {
                TextButton(onClick = { dismissWithVersion() }) {
                    Text(
                        if (
                            result is UpdateResult.Available &&
                            installFailure == null &&
                            !permissionRequired &&
                            !installSubmitted
                        ) {
                            "Agora não"
                        } else {
                            "Fechar"
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun DownloadingContent() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text("Baixando e validando a nova versão...")
    }
}

@Composable
private fun SubmittedContent() {
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
        Text("Download concluído com segurança!", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "O Android instalará em segundo plano quando permitido. " +
                "Se precisar de confirmação, você receberá uma notificação.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PermissionContent() {
    Column {
        Text("Autorize esta fonte", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "O Android precisa que você permita ao TPoll Scanner instalar a própria atualização.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Toque em Permitir, ative a opção do sistema e depois tente atualizar novamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FailureContent(failure: ApkInstallRequestResult.Failed) {
    Column {
        Text(failure.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (failure.requiresOneTimeReinstall) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Abra o download oficial no navegador e baixe o APK. Depois, desinstale " +
                    "esta versão antiga e instale o arquivo baixado. As próximas atualizações " +
                    "usarão a assinatura estável.",
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
        Text("Buscando atualizações...")
    }
}

@Composable
private fun AvailableContent(info: UpdateInfo) {
    val context = LocalContext.current
    val installedVersion = remember(context) { InstalledAppVersion.read(context) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Versão ${info.version_name} disponível",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Sua versão: ${installedVersion.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Novidades:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = info.changelog, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
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

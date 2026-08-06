// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.updater

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val checker = remember { UpdateChecker() }

    LaunchedEffect(Unit) {
        result = checker.checkForUpdates()
        isChecking = false
    }

    fun dismissWithVersion() {
        val versionCode = (result as? UpdateResult.Available)?.info?.version_code ?: 0
        onDismiss(versionCode)
    }

    AlertDialog(
        onDismissRequest = { if (!isInstalling) dismissWithVersion() },
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
                    isInstalling -> "Instalando..."
                    installSuccess -> "Instalado!"
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
                isInstalling -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Baixando e instalando nova versão...")
                    }
                }
                installSuccess -> {
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
                        Text("Nova versão instalada com sucesso!", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Reabra o app para usar a versão mais recente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                !installError.isNullOrBlank() -> {
                    Column {
                        Text("Erro na instalação")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = installError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Se o erro persistir, desinstale o app e instale o APK manualmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                isChecking -> {
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
                result is UpdateResult.Available -> {
                    val info = (result as UpdateResult.Available).info
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
                            text = "Sua versão: ${UpdateInfo.currentVersionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Novidades:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
                result is UpdateResult.UpToDate -> {
                    Text("Seu app está na versão mais recente (${UpdateInfo.currentVersionName}).")
                }
                result is UpdateResult.Error -> {
                    Column {
                        Text("Não foi possível verificar atualizações.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (result as UpdateResult.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                result is UpdateResult.Available && !isInstalling && installError.isNullOrBlank() && !installSuccess -> {
                    val info = (result as UpdateResult.Available).info
                    Button(onClick = {
                        isInstalling = true
                        installError = null
                        installSuccess = false
                        scope.launch {
                            ApkInstaller.downloadAndInstall(
                                context = context,
                                apkUrl = info.apk_url.ifEmpty { info.download_url },
                                onStarted = {},
                                onSuccess = {
                                    isInstalling = false
                                    installSuccess = true
                                },
                                onError = { error ->
                                    isInstalling = false
                                    installError = error
                                }
                            )
                        }
                    }) {
                        Text("Atualizar agora")
                    }
                }
                installSuccess -> {
                    Button(onClick = { dismissWithVersion() }) {
                        Text("OK")
                    }
                }
            }
        },
        dismissButton = {
            if (!isInstalling) {
                TextButton(onClick = { dismissWithVersion() }) {
                    Text(
                        when {
                            result is UpdateResult.Available && installError.isNullOrBlank() && !installSuccess -> "Agora não"
                            else -> "Fechar"
                        }
                    )
                }
            }
        }
    )
}

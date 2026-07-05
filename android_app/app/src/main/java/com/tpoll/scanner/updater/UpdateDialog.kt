package com.tpoll.scanner.updater

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun UpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    var isChecking by remember { mutableStateOf(true) }
    val checker = remember { UpdateChecker() }

    LaunchedEffect(Unit) {
        result = checker.checkForUpdates()
        isChecking = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            if (result is UpdateResult.Available) {
                val info = (result as UpdateResult.Available).info
                Button(onClick = {
                    val url = info.apk_url.ifEmpty { info.download_url }
                    checker.openDownloadUrl(context, url)
                }) {
                    Text("Baixar APK")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (result is UpdateResult.Available) "Agora não" else "Fechar")
            }
        }
    )
}

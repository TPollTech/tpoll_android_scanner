// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.remover

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.RiskLevel

data class RemovalResult(
    val success: Boolean,
    val packageName: String,
    val message: String = ""
)

class AppRemover(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun canUninstall(): Boolean {
        return true
    }

    fun removeApp(finding: AppFinding): RemovalResult {
        return try {
            if (finding.level == RiskLevel.LOW && !finding.isKnownThreat) {
                return RemovalResult(
                    success = false,
                    packageName = finding.packageName,
                    message = "App de baixo risco - não removido"
                )
            }

            val success = uninstallForCurrentUser(finding.packageName)

            if (success) {
                RemovalResult(
                    success = true,
                    packageName = finding.packageName,
                    message = "Removido com sucesso"
                )
            } else {
                RemovalResult(
                    success = false,
                    packageName = finding.packageName,
                    message = "Falha na remoção"
                )
            }
        } catch (e: SecurityException) {
            RemovalResult(
                success = false,
                packageName = finding.packageName,
                message = "Sem permissão para remover: ${e.message}"
            )
        } catch (e: Exception) {
            RemovalResult(
                success = false,
                packageName = finding.packageName,
                message = "Erro: ${e.message}"
            )
        }
    }

    private fun uninstallForCurrentUser(packageName: String): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE)
            intent.data = android.net.Uri.parse("package:$packageName")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeApps(findings: List<AppFinding>): List<RemovalResult> {
        val results = mutableListOf<RemovalResult>()

        for (finding in findings) {
            val result = removeApp(finding)
            results.add(result)
        }

        return results
    }

    fun getRemovableApps(findings: List<AppFinding>): List<AppFinding> {
        return findings.filter { finding ->
            finding.level in listOf(RiskLevel.HIGH, RiskLevel.MEDIUM) || finding.isKnownThreat
        }
    }
}

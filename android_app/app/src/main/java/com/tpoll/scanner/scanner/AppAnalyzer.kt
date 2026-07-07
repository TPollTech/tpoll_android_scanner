// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageManager
import android.os.Build
import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.RiskLevel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppAnalyzer(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val gson = Gson()

    data class VirusDbEntry(
        val type: String = "",
        val severity: String = "low",
        val description: String = ""
    )

    data class VirusDb(
        val known_threats: Map<String, VirusDbEntry> = emptyMap(),
        val suspicious_patterns: List<SuspiciousPattern> = emptyList()
    )

    data class SuspiciousPattern(
        val pattern: String = "",
        val reason: String = ""
    )

    fun loadRules(): ScoringRules {
        return try {
            val json = context.assets.open("rules.json").bufferedReader().use { it.readText() }
            val map = gson.fromJson(json, Map::class.java)

            val trustedInstallers = (map["trusted_installers"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
            val suspiciousTerms = (map["suspicious_terms"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            val highRiskPermissions = mutableMapOf<String, Int>()
            @Suppress("UNCHECKED_CAST")
            (map["high_risk_permissions"] as? Map<String, Any>)?.forEach { (k, v) ->
                highRiskPermissions[k] = (v as? Number)?.toInt() ?: 0
            }

            val highRiskAppOps = mutableMapOf<String, Int>()
            @Suppress("UNCHECKED_CAST")
            (map["high_risk_appops"] as? Map<String, Any>)?.forEach { (k, v) ->
                highRiskAppOps[k] = (v as? Number)?.toInt() ?: 0
            }

            val dangerousCombos = mutableListOf<DangerousCombo>()
            @Suppress("UNCHECKED_CAST")
            (map["dangerous_combinations"] as? List<Map<String, Any>>)?.forEach { comboMap ->
                val perms = (comboMap["permissions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val bonus = (comboMap["bonus"] as? Number)?.toInt() ?: 0
                val reason = comboMap["reason"] as? String ?: ""
                if (perms.isNotEmpty()) {
                    dangerousCombos.add(DangerousCombo(perms, bonus, reason))
                }
            }

            ScoringRules(
                trustedInstallers = trustedInstallers,
                suspiciousTerms = suspiciousTerms,
                highRiskPermissions = highRiskPermissions,
                highRiskAppOps = highRiskAppOps,
                dangerousCombinations = dangerousCombos
            )
        } catch (e: Exception) {
            ScoringRules()
        }
    }

    fun loadVirusDb(): VirusDb {
        return try {
            val json = context.assets.open("virus_db.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<VirusDb>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            VirusDb()
        }
    }

    suspend fun analyzeAllPackages(
        thirdPartyOnly: Boolean = true,
        onProgress: (suspend (current: Int, total: Int, packageName: String) -> Unit)? = null
    ): List<AppFinding> {
        val rules = loadRules()
        val virusDb = loadVirusDb()

        val flags = if (thirdPartyOnly) PackageManager.GET_META_DATA else 0
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }

        val filteredPackages = if (thirdPartyOnly) {
            packages.filter { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        } else {
            packages
        }

        val total = filteredPackages.size
        val findings = mutableListOf<AppFinding>()

        for ((index, pkgInfo) in filteredPackages.withIndex()) {
            val packageName = pkgInfo.packageName
            onProgress?.invoke(index + 1, total, packageName)

            val finding = analyzePackage(pkgInfo, virusDb)
            val scored = RiskScorer.scoreApp(finding, rules)
            findings.add(scored)
        }

        return findings.sortedByDescending { it.score }
    }

    private fun analyzePackage(pkgInfo: android.content.pm.PackageInfo, virusDb: VirusDb): AppFinding {
        val packageName = pkgInfo.packageName
        val appName = packageManager.getApplicationLabel(pkgInfo.applicationInfo).toString()
        val apkPath = pkgInfo.applicationInfo.sourceDir ?: ""

        val installer = getInstaller(packageName)

        val permissions = getDeclaredPermissions(pkgInfo)

        val appOps = getAppOps(packageName)

        val knownThreat = virusDb.known_threats.containsKey(packageName)
        val threatType = virusDb.known_threats[packageName]?.type ?: ""

        val suspiciousByPattern = virusDb.suspicious_patterns.any { pattern ->
            try {
                packageName.matches(Regex(pattern.pattern))
            } catch (e: Exception) {
                false
            }
        }

        return AppFinding(
            packageName = packageName,
            appName = appName,
            apkPath = apkPath,
            installer = installer,
            permissions = permissions,
            appOps = appOps,
            level = RiskLevel.LOW,
            isKnownThreat = knownThreat || suspiciousByPattern,
            threatType = threatType
        )
    }

    private fun getInstaller(packageName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val info = packageManager.getInstallSourceInfo(packageName)
                info.installingPackageName ?: info.initiatingPackageName ?: "desconhecido"
            } else {
                @Suppress("DEPRECATION")
                val installer = packageManager.getInstallerPackageName(packageName)
                installer ?: "desconhecido"
            }
        } catch (e: Exception) {
            "desconhecido"
        }
    }

    private fun getDeclaredPermissions(pkgInfo: android.content.pm.PackageInfo): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgInfo.requestedPermissions?.toList() ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.requestedPermissions?.toList() ?: emptyList()
        }
    }

    private fun getAppOps(packageName: String): List<String> {
        val ops = mutableListOf<String>()

        try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE)
            if (appOpsManager != null) {
                val clazz = Class.forName("android.app.AppOpsManager")
                val method = clazz.getMethod(
                    "getPackagesForOps",
                    Array<Int>::class.java
                )
            }
        } catch (e: Exception) {
            // Fallback: retorna lista baseada nas permissões
        }

        return ops
    }

    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE)
            if (appOpsManager != null) {
                val mode = android.app.AppOpsManager::class.java
                    .getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
                    .invoke(
                        appOpsManager,
                        43, // AppOpsManager.OP_GET_USAGE_STATS
                        android.os.Process.myUid(),
                        context.packageName
                    ) as Int
                mode == android.app.AppOpsManager.MODE_ALLOWED
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

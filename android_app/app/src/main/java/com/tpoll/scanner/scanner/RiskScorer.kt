// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.scanner

import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.RiskLevel

data class ScoringRules(
    val trustedInstallers: Set<String> = emptySet(),
    val suspiciousTerms: List<String> = emptyList(),
    val highRiskPermissions: Map<String, Int> = emptyMap(),
    val highRiskAppOps: Map<String, Int> = emptyMap(),
    val dangerousCombinations: List<DangerousCombo> = emptyList()
)

data class DangerousCombo(
    val permissions: List<String>,
    val bonus: Int,
    val reason: String
)

object RiskScorer {

    fun scoreApp(finding: AppFinding, rules: ScoringRules): AppFinding {
        var score = 0
        val reasons = mutableListOf<String>()

        val pkgLower = finding.packageName.lowercase()
        val normalized = pkgLower.replace("[^a-z0-9]".toRegex(), "")

        val matchedTerms = mutableListOf<String>()
        for (term in rules.suspiciousTerms) {
            val termNormalized = term.lowercase().replace("[^a-z0-9]".toRegex(), "")
            if (termNormalized.isNotEmpty() && normalized.contains(termNormalized)) {
                matchedTerms.add(term)
            }
        }
        if (matchedTerms.isNotEmpty()) {
            val add = minOf(40, 12 + matchedTerms.size * 6)
            score += add
            reasons.add("Nome/pacote com termos suspeitos: ${matchedTerms.distinct().take(8).joinToString()}")
        }

        val installer = finding.installer
        if (installer in listOf("desconhecido", "", "null", "None")) {
            score += 12
            reasons.add("Instalador desconhecido")
        } else if (installer !in rules.trustedInstallers) {
            score += 20
            reasons.add("Instalado por origem não confiável: $installer")
        }

        val allOps = finding.appOps.map { it.uppercase() }.toSet()

        for (perm in finding.permissions) {
            val shortPerm = perm.substringAfterLast(".")
            val points = rules.highRiskPermissions[perm]
                ?: rules.highRiskPermissions[shortPerm]
                ?: 0
            if (points > 0) {
                score += points
                reasons.add("Permissão sensível: $shortPerm (+$points)")
            }
        }

        for (op in finding.appOps) {
            val opUpper = op.uppercase()
            val points = rules.highRiskAppOps[opUpper] ?: 0
            if (points > 0) {
                score += points
                reasons.add("AppOp sensível: $opUpper (+$points)")
            }
        }

        val permShortNames = finding.permissions.map { it.substringAfterLast(".") }.toSet()
        val allOpsShort = allOps

        for (combo in rules.dangerousCombinations) {
            val comboShortNames = combo.permissions.map { it.substringAfterLast(".").uppercase() }
            val comboAll = combo.permissions.map { it.uppercase() }

            val matched = comboShortNames.all { it in permShortNames } ||
                    comboAll.all { it in allOpsShort }

            if (matched) {
                score += combo.bonus
                reasons.add("Combinação suspeita: ${combo.reason} (+${combo.bonus})")
            }
        }

        if (finding.isKnownThreat) {
            score = maxOf(score, 80)
            reasons.add("AMEAÇA CONHECIDA: ${finding.threatType}")
        }

        score = minOf(score, 100)

        val level = when {
            score >= 70 -> RiskLevel.HIGH
            score >= 40 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        if (reasons.isEmpty()) {
            reasons.add("Nenhum sinal forte detectado")
        }

        return finding.copy(
            score = score,
            level = level,
            reasons = reasons
        )
    }
}

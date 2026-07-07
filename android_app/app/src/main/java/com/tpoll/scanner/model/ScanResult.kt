// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.model

data class ScanResult(
    val totalScanned: Int = 0,
    val highRiskCount: Int = 0,
    val mediumRiskCount: Int = 0,
    val lowRiskCount: Int = 0,
    val removedCount: Int = 0,
    val keptCount: Int = 0,
    val findings: List<AppFinding> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
) {
    val hasThreats: Boolean get() = highRiskCount > 0 || mediumRiskCount > 0
}

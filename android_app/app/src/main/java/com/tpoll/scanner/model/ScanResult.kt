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

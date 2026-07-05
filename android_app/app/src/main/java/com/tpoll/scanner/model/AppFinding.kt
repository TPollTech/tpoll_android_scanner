package com.tpoll.scanner.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

@Entity(tableName = "scan_results")
data class AppFinding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String = "",
    val apkPath: String = "",
    val installer: String = "",
    val permissions: List<String> = emptyList(),
    val appOps: List<String> = emptyList(),
    val score: Int = 0,
    val level: RiskLevel = RiskLevel.LOW,
    val reasons: List<String> = emptyList(),
    val isKnownThreat: Boolean = false,
    val threatType: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val wasRemoved: Boolean = false,
    val removedAt: Long = 0L
)

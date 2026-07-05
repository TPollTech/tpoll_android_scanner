package com.tpoll.scanner.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quarantined_apps")
data class QuarantinedApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val reason: String,
    val riskLevel: String,
    val score: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val removedBy: String = "auto"
)

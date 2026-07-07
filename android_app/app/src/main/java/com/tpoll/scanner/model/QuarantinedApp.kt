// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
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

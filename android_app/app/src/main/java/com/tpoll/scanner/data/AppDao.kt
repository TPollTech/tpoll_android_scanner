// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tpoll.scanner.model.AppFinding

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(finding: AppFinding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(findings: List<AppFinding>)

    @Query("SELECT * FROM scan_results ORDER BY timestamp DESC")
    suspend fun getAll(): List<AppFinding>

    @Query("SELECT * FROM scan_results WHERE score > 0 ORDER BY score DESC, timestamp DESC")
    suspend fun getThreats(): List<AppFinding>

    @Query("SELECT * FROM scan_results WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT 1")
    suspend fun getByPackageName(pkg: String): AppFinding?

    @Query("DELETE FROM scan_results")
    suspend fun deleteAll()

    @Query("DELETE FROM scan_results WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

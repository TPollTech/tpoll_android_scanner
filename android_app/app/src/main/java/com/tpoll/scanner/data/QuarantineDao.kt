// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tpoll.scanner.model.QuarantinedApp

@Dao
interface QuarantineDao {
    @Insert
    suspend fun insert(app: QuarantinedApp)

    @Query("SELECT * FROM quarantined_apps ORDER BY timestamp DESC")
    suspend fun getAll(): List<QuarantinedApp>

    @Query("SELECT COUNT(*) FROM quarantined_apps")
    suspend fun count(): Int

    @Query("DELETE FROM quarantined_apps WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("DELETE FROM quarantined_apps")
    suspend fun deleteAll()
}

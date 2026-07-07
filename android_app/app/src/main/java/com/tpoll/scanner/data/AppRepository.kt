// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.data

import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.QuarantinedApp

class AppRepository(
    private val dao: AppDao,
    private val quarantineDao: QuarantineDao
) {
    suspend fun saveFindings(findings: List<AppFinding>) {
        dao.deleteAll()
        dao.insertAll(findings)
    }

    suspend fun getAllFindings(): List<AppFinding> = dao.getAll()

    suspend fun getThreats(): List<AppFinding> = dao.getThreats()

    suspend fun getByPackage(pkg: String): AppFinding? = dao.getByPackageName(pkg)

    suspend fun clearAll() = dao.deleteAll()

    suspend fun cleanOld(keepDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L
        dao.deleteOlderThan(cutoff)
    }

    suspend fun quarantineApp(app: QuarantinedApp) = quarantineDao.insert(app)

    suspend fun getAllQuarantined(): List<QuarantinedApp> = quarantineDao.getAll()

    suspend fun getQuarantineCount(): Int = quarantineDao.count()

    suspend fun removeFromQuarantine(pkg: String) = quarantineDao.deleteByPackage(pkg)

    suspend fun clearQuarantine() = quarantineDao.deleteAll()
}

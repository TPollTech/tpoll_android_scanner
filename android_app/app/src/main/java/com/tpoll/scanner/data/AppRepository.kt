package com.tpoll.scanner.data

import com.tpoll.scanner.model.AppFinding

class AppRepository(private val dao: AppDao) {

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
}
